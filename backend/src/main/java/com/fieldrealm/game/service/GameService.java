package com.fieldrealm.game.service;

import com.fieldrealm.game.domain.*;
import com.fieldrealm.game.dto.AttackRequest;
import com.fieldrealm.game.dto.PlayCardRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameService {
    static final int PHASE_DURATION_SECONDS = 30;
    private final CardCatalogService catalog;
    private final SimpMessagingTemplate messaging;
    private final Map<String, GameState> matches = new ConcurrentHashMap<>();
    private final Random random;
    private final AuthService auth;

    @Autowired
    public GameService(CardCatalogService catalog, SimpMessagingTemplate messaging, AuthService auth) {
        this(catalog, messaging, auth, new Random());
    }

    public GameService(CardCatalogService catalog, SimpMessagingTemplate messaging) {
        this(catalog, messaging, null, new Random());
    }

    GameService(CardCatalogService catalog, SimpMessagingTemplate messaging, Random random) {
        this(catalog, messaging, null, random);
    }

    GameService(CardCatalogService catalog, SimpMessagingTemplate messaging, AuthService auth, Random random) {
        this.catalog = catalog;
        this.messaging = messaging;
        this.auth = auth;
        this.random = random;
    }

    public synchronized GameState create(String mode, String playerName) {
        return create(mode, playerName, 3, null, false);
    }

    public synchronized GameState create(String mode, String playerName, int boardSize, String accountId, boolean ranked) {
        GameState game = new GameState();
        game.setId(UUID.randomUUID().toString().substring(0, 8));
        game.setMode((ranked || "PVP".equalsIgnoreCase(mode)) ? "PVP" : "AI");
        game.setBoardSize(normalizeBoardSize(boardSize));
        game.setRanked(ranked);
        game.setWaitingForOpponent("PVP".equals(game.getMode()));

        PlayerState player = new PlayerState("p1", blank(playerName) ? "弈境旅者" : playerName.trim(), "初入弈境", "旅");
        player.setAccountId(accountId);
        initializePlayer(player);
        PlayerState opponent = new PlayerState("p2", game.getMode().equals("AI") ? "雾隐执棋者" : "等待中的对手", "秘境守门人", "雾");
        if (game.getMode().equals("AI")) initializePlayer(opponent);

        game.setPlayers(new ArrayList<>(List.of(player, opponent)));
        game.setSites(createSites(game.getBoardSize()));
        matches.put(game.getId(), game);

        if (game.isWaitingForOpponent()) {
            game.setPhaseEndsAt(null);
            game.setStatusText("房间已创建，等待另一位执棋者加入");
            log(game, player.getName() + " 创建了" + game.getBoardSize() + "×" + game.getBoardSize() + "对局");
        } else {
            player.setEnergy(3);
            startPhase(game, GamePhase.DEPLOY);
            game.setStatusText("先部署至少一张场地；部署阶段限时30秒，超时自动推进");
            log(game, "初始部署开始 · " + player.getName() + " 获得3点灵力");
        }
        return game;
    }

    public synchronized GameState join(String matchId, String playerName, String accountId) {
        GameState game = matches.get(matchId);
        if (game == null) throw new IllegalArgumentException("对局不存在");
        if (!game.isWaitingForOpponent()) throw new IllegalArgumentException("该房间已经开始");
        PlayerState host = game.getPlayers().get(0);
        if (accountId != null && accountId.equals(host.getAccountId())) throw new IllegalArgumentException("不能加入自己创建的房间");
        PlayerState opponent = game.getPlayers().get(1);
        opponent.setName(blank(playerName) ? "远方执棋者" : playerName.trim());
        opponent.setTitle("联机挑战者");
        opponent.setAvatar(opponent.getName().substring(0, 1));
        opponent.setAccountId(accountId);
        initializePlayer(opponent);
        game.setWaitingForOpponent(false);
        game.setActivePlayerIndex(0);
        host.setEnergy(3);
        startPhase(game, GamePhase.DEPLOY);
        game.setStatusText("双方已就位，部署阶段开始");
        log(game, opponent.getName() + " 加入对局 · 双方各有30秒部署时间");
        publish(game);
        return game;
    }

    private void initializePlayer(PlayerState player) {
        player.setDeck(shuffledDeck());
        draw(player, 4);
        ensureOpeningSite(player);
    }

    private int normalizeBoardSize(int size) { return size == 4 || size == 5 ? size : 3; }

    private List<SiteState> createSites(int size) {
        List<SiteState> result = new ArrayList<>();
        if (size == 3) {
            int[][] coordinates = {{0,0},{0,1},{0,2},{1,2},{2,2},{2,1},{2,0},{1,0},{1,1}};
            String[] names = {"西北","北境","东北","东境","东南","南境","西南","西境","天元核心"};
            for (int i = 0; i < 9; i++) result.add(new SiteState(i, names[i], i == 8, coordinates[i][0], coordinates[i][1]));
            return result;
        }
        int total = size * size;
        int coreIndex = (size / 2) * size + (size / 2);
        for (int i = 0; i < total; i++) {
            int row = i / size, column = i % size;
            boolean core = i == coreIndex;
            String position = core ? "天元核心" : "第" + (row + 1) + "行·第" + (column + 1) + "列";
            result.add(new SiteState(i, position, core, row, column));
        }
        return result;
    }
    public synchronized GameState get(String id) {
        GameState game = matches.get(id);
        if (game == null) throw new IllegalArgumentException("\u5bf9\u5c40\u4e0d\u5b58\u5728\u6216\u5df2\u7ed3\u675f");
        advanceExpiredPhase(game, Instant.now());
        return game;
    }

    @Scheduled(fixedRate = 250)
    public synchronized void enforcePhaseDeadlines() {
        Instant now = Instant.now();
        for (GameState game : matches.values()) advanceExpiredPhase(game, now);
    }

    public synchronized GameState playCard(String matchId, PlayCardRequest request) {
        GameState game = get(matchId);
        ensurePlayable(game);
        PlayerState player = requireActive(game, request.playerId());
        CardDefinition card = catalog.require(request.cardId());
        if (player.getHand().size() > 7) throw new IllegalArgumentException("\u624b\u724c\u8d85\u8fc77\u5f20\uff0c\u8bf7\u5148\u5f03\u724c");
        if (!player.getHand().contains(card.id())) throw new IllegalArgumentException("该卡牌不在手牌中");
        if (player.getEnergy() < card.cost()) throw new IllegalArgumentException("灵力不足");
        if (game.getPhase() != GamePhase.DEPLOY && card.type() != CardType.SPELL) {
            throw new IllegalArgumentException("当前阶段只能使用瞬发术式");
        }

        switch (card.type()) {
            case SITE -> deploySite(game, player, card, requiredSite(request.targetSiteIndex()));
            case UNIT -> deployUnit(game, player, card, requiredSite(request.targetSiteIndex()));
            case SPELL -> castSpell(game, player, card, request.targetUnitId());
            case SECRET -> castSecret(game, player, card);
        }
        player.setEnergy(player.getEnergy() - card.cost());
        player.getHand().remove(card.id());
        if (card.type() == CardType.SPELL || card.type() == CardType.SECRET) player.getDiscard().add(card.id());
        game.setUpdatedAt(Instant.now());
        publish(game);
        return game;
    }

    public synchronized GameState enterContest(String matchId, String playerId) {
        GameState game = get(matchId);
        requireActive(game, playerId);
        if (game.getPhase() != GamePhase.DEPLOY) throw new IllegalArgumentException("已经进入争夺阶段");
        if (!bothPlayersHaveSites(game)) throw new IllegalArgumentException("双方都布置至少一张场地后才能开始争夺");
        if (!game.isInitialContestResolved()) throw new IllegalArgumentException("请先完成双方初始部署，再按先手骰结果开始争夺");
        startPhase(game, GamePhase.CONTEST);
        game.setStatusText("选择己方未行动单位，再选择敌方场地发起争夺");
        log(game, game.activePlayer().getName() + " 进入场地争夺阶段");
        publish(game);
        return game;
    }

    public synchronized GameState attack(String matchId, AttackRequest request) {
        GameState game = get(matchId);
        ensurePlayable(game);
        PlayerState player = requireActive(game, request.playerId());
        if (game.getPhase() != GamePhase.CONTEST) throw new IllegalArgumentException("请先进入争夺阶段");
        SiteState source = findUnitSite(game, request.attackerUnitId());
        UnitInstance attacker = source.getUnits().stream()
                .filter(u -> u.getInstanceId().equals(request.attackerUnitId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("进攻单位不存在"));
        if (!player.getId().equals(attacker.getOwnerId())) throw new IllegalArgumentException("只能使用己方单位");
        if (attacker.isSealed()) throw new IllegalArgumentException("该单位正被封印");
        if (attacker.isExhausted()) throw new IllegalArgumentException("该单位本回合已行动");
        SiteState target = site(game, request.targetSiteIndex());
        if (player.getId().equals(target.getOwnerId())) throw new IllegalArgumentException("不能争夺己方场地");
        if (target.getOwnerId() == null) throw new IllegalArgumentException("无主场地需通过部署场地卡占领");
        int distance = siteDistance(game, source.getIndex(), target.getIndex());
        if (distance > attacker.getAttackRange()) {
            throw new IllegalArgumentException("目标距离为" + distance + "，该单位攻击距离只有" + attacker.getAttackRange());
        }

        int attackPower = attacker.getPower();
        if ("FORGE".equals(source.getEffectCode())) attackPower++;
        if ("BEACON".equals(source.getEffectCode())) attacker.setAttackRange(Math.max(attacker.getAttackRange(), attacker.getBaseRange() + 1));
        if ("VANGUARD".equals(attacker.getKeyword()) && target.isCore()) attackPower += 2;
        if ("DUELIST".equals(attacker.getKeyword()) && target.getUnits().isEmpty()) attackPower++;
        int defense = target.totalGuard();
        attacker.setExhausted(true);
        String result;
        if (attackPower > defense) {
            if ("FORTRESS".equals(target.getEffectCode()) && !target.isCore()) {
                if (player.getId().equals(target.getPendingAttackerId())) target.setFortressHits(target.getFortressHits() + 1);
                else { target.setPendingAttackerId(player.getId()); target.setFortressHits(1); }
                if (target.getFortressHits() < 2) {
                    result = "壁垒承受第一次突破，还需下回合再次成功进攻";
                } else {
                    capture(game, target, player);
                    result = "连续突破完成，壁垒归属易手";
                }
            } else {
                capture(game, target, player);
                result = "争夺成功，" + target.getName() + " 归属易手";
            }
            if (attackPower - defense >= 3) {
                player.setScore(player.getScore() + 1);
                result += "；压倒性优势额外获得1分";
            }
        } else {
            result = attackPower == defense ? "战力与守力持平，归属不变" : "守方稳住场地，争夺失败";
            if ("MIRROR".equals(target.getEffectCode()) && target.getOwnerId() != null) {
                playerById(game, target.getOwnerId()).setScore(playerById(game, target.getOwnerId()).getScore() + 1);
                result += "；镜潮回廊反制获得1分";
            }
            target.setPendingAttackerId(null);
            target.setFortressHits(0);
        }
        game.setStatusText("距离 " + distance + " · 战力 " + attackPower + " vs 守力 " + defense + " · " + result);
        log(game, attacker.getName() + " 跨越距离" + distance + "争夺 " + target.getName() + "：" + result);
        game.setUpdatedAt(Instant.now());
        publish(game);
        return game;
    }

    public synchronized GameState endTurn(String matchId, String playerId) {
        GameState game = get(matchId);
        requireActive(game, playerId);
        if (playerHandOverflow(game)) throw new IllegalArgumentException("\u624b\u724c\u8d85\u8fc77\u5f20\uff0c\u8bf7\u5148\u5f03\u724c");
        if (!game.isInitialContestResolved()) return completeInitialDeployment(game);

        settle(game);
        if (game.getPhase() == GamePhase.FINISHED) { publish(game); return game; }
        advanceTurn(game);
        if (game.getMode().equals("AI") && game.getActivePlayerIndex() == 1) {
            runAiTurn(game);
            if (game.getPhase() != GamePhase.FINISHED) advanceTurn(game);
        }
        publish(game);
        return game;
    }

    public synchronized GameState retreatUnit(String matchId, String playerId, String unitId) {
        GameState game = get(matchId);
        ensurePlayable(game);
        PlayerState player = requireActive(game, playerId);
        if (game.getPhase() != GamePhase.DEPLOY) throw new IllegalArgumentException("只能在部署阶段撤离单位");

        SiteState site = findUnitSite(game, unitId);
        UnitInstance unit = site.getUnits().stream()
                .filter(u -> u.getInstanceId().equals(unitId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("单位不存在"));
        if (!player.getId().equals(unit.getOwnerId())) throw new IllegalArgumentException("只能撤离己方单位");

        site.getUnits().remove(unit);
        if ("ARCHITECT".equals(unit.getKeyword())) site.setBaseGuard(Math.max(0, site.getBaseGuard() - 1));
        player.getDiscard().add(unit.getCardId());
        game.setStatusText("「" + unit.getName() + "」已撤离，空出1个驻场位置");
        log(game, player.getName() + " 从" + site.getName() + "撤离「" + unit.getName() + "」");
        game.setUpdatedAt(Instant.now());
        publish(game);
        return game;
    }

    public synchronized GameState leave(String matchId, String playerId, String accountId) {
        GameState game = matches.get(matchId);
        if (game == null) throw new IllegalArgumentException("对局不存在");
        PlayerState leaver = playerById(game, playerId);
        if (leaver.getAccountId() != null && !Objects.equals(leaver.getAccountId(), accountId)) {
            throw new IllegalArgumentException("无权代替其他玩家退出对局");
        }
        if (game.getPhase() == GamePhase.FINISHED) return game;
        if (game.isWaitingForOpponent()) {
            game.setWaitingForOpponent(false);
            finish(game, "DRAW", "房间关闭");
            game.setStatusText(leaver.getName() + " 已关闭房间");
            publish(game);
            return game;
        }
        PlayerState winner = game.getPlayers().stream().filter(player -> !player.getId().equals(leaver.getId())).findFirst().orElseThrow();
        finish(game, winner.getId(), "对手退出");
        game.setStatusText(leaver.getName() + " 退出了对局，" + winner.getName() + " 获得胜利");
        if ("PVP".equals(game.getMode())) {
            log(game, leaver.getName() + " 确认退出·将进入 30 秒匹配冷却");
            if (auth != null && leaver.getAccountId() != null) auth.applyMatchBan(leaver.getAccountId(), 30);
        } else {
            log(game, leaver.getName() + " 结束了人机试炼");
        }
        publish(game);
        return game;
    }

    public synchronized GameState discard(String matchId, String playerId, String cardId) {
        GameState game = get(matchId);
        ensurePlayable(game);
        PlayerState player = requireActive(game, playerId);
        if (game.getPhase() != GamePhase.DEPLOY) throw new IllegalArgumentException("只能在部署阶段弃牌");
        if (!player.getHand().remove(cardId)) throw new IllegalArgumentException("手牌中没有这张卡");
        player.getDiscard().add(cardId);
        game.setStatusText("已主动弃置「" + catalog.require(cardId).name() + "」");
        log(game, player.getName() + " 弃置了1张手牌");
        game.setUpdatedAt(Instant.now());
        publish(game);
        return game;
    }

    private void deploySite(GameState game, PlayerState player, CardDefinition card, int index) {
        SiteState site = site(game, index);
        if (site.getOwnerId() != null && !player.getId().equals(site.getOwnerId())) {
            throw new IllegalArgumentException("不能直接覆盖敌方场地，请先争夺归属");
        }
        site.setOwnerId(player.getId());
        site.setCardId(card.id()); site.setName(card.name());
        int architectBonus = (int) site.getUnits().stream().filter(u -> "ARCHITECT".equals(u.getKeyword())).count();
        site.setBaseGuard(card.guard() + architectBonus);
        site.setBasePoints(card.points()); site.setEffectCode(card.effectCode()); site.setEffect(card.effect());
        site.setPendingAttackerId(null); site.setFortressHits(0);
        if ("ARCHIVE".equals(card.effectCode())) draw(player, 1);
        log(game, player.getName() + " 在" + site.getPosition() + "部署场地「" + card.name() + "」");
        game.setStatusText("场地部署完成，可继续部署或进入争夺");
    }

    private void deployUnit(GameState game, PlayerState player, CardDefinition card, int index) {
        SiteState site = site(game, index);
        if (!player.getId().equals(site.getOwnerId())) throw new IllegalArgumentException("单位只能部署到己方场地");
        if (site.getUnits().size() >= 2) throw new IllegalArgumentException("单个场地最多驻扎2个单位");
        UnitInstance unit = new UnitInstance(UUID.randomUUID().toString().substring(0, 8), card, player.getId());
        if ("SCOUT".equals(card.effectCode()) && site.getUnits().isEmpty()) unit.setPower(unit.getPower() + 1);
        if ("ARCHITECT".equals(card.effectCode())) site.setBaseGuard(site.getBaseGuard() + 1);
        site.getUnits().add(unit);
        if (("CHANNEL".equals(card.effectCode()) && site.isCore()) || "ORACLE".equals(card.effectCode())) draw(player, 1);
        log(game, player.getName() + " 将「" + card.name() + "」部署至" + site.getName());
        game.setStatusText("单位驻场完成");
    }

    private void castSpell(GameState game, PlayerState player, CardDefinition card, String targetUnitId) {
        switch (card.effectCode()) {
            case "DRAW" -> draw(player, 2);
            case "ECHO" -> {
                if (player.getScore() >= game.opponent().getScore()) throw new IllegalArgumentException("仅在积分落后时可发动");
                player.setScore(player.getScore() + 3);
            }
            case "SEAL" -> {
                UnitInstance target = requireUnit(game, targetUnitId);
                if (player.getId().equals(target.getOwnerId())) throw new IllegalArgumentException("请选择敌方单位");
                target.setSealed(true);
            }
            case "SURGE" -> {
                UnitInstance target = requireUnit(game, targetUnitId);
                if (!player.getId().equals(target.getOwnerId())) throw new IllegalArgumentException("请选择己方单位");
                target.setPower(target.getPower() + 2);
            }
            case "REINFORCE" -> {
                UnitInstance target = requireUnit(game, targetUnitId);
                if (!player.getId().equals(target.getOwnerId())) throw new IllegalArgumentException("请选择己方单位");
                target.setGuard(target.getGuard() + 2);
            }
            case "REFRESH" -> {
                UnitInstance target = requireUnit(game, targetUnitId);
                if (!player.getId().equals(target.getOwnerId())) throw new IllegalArgumentException("请选择己方单位");
                target.setExhausted(false);
            }
            case "EXPEDITION" -> {
                draw(player, 1);
                player.setEnergy(player.getEnergy() + 1);
            }
            case "RANGE" -> {
                UnitInstance target = requireUnit(game, targetUnitId);
                if (!player.getId().equals(target.getOwnerId())) throw new IllegalArgumentException("请选择己方单位");
                target.setAttackRange(target.getAttackRange() + 1);
            }
            default -> throw new IllegalArgumentException("术式效果尚未登记");
        }
        log(game, player.getName() + " 发动术式「" + card.name() + "」");
        game.setStatusText("术式「" + card.name() + "」已生效");
    }

    private void castSecret(GameState game, PlayerState player, CardDefinition card) {
        if (player.isSecretUsed()) throw new IllegalArgumentException("本局已经发动过秘策");
        long ownSites = countSites(game, player.getId());
        if ("DOMINION".equals(card.effectCode())) {
            if (ownSites < 5) throw new IllegalArgumentException("需控制至少5个场地才能发动");
            player.setScore(player.getScore() + 8);
        } else if ("OVERGROWTH".equals(card.effectCode())) {
            if (ownSites < 3) throw new IllegalArgumentException("需控制至少3个场地才能发动");
            player.setScoringBonusThisTurn(1);
        } else if ("BALANCE".equals(card.effectCode())) {
            PlayerState rival = game.opponent();
            if (player.getScore() >= rival.getScore() || ownSites >= countSites(game, rival.getId())) {
                throw new IllegalArgumentException("需同时满足积分落后、控制场地更少");
            }
            player.setScore(rival.getScore());
        }
        player.setSecretUsed(true);
        log(game, player.getName() + " 发动不可反制秘策「" + card.name() + "」");
        game.setStatusText("秘策发动，整个弈境为之震动");
    }

    private void settle(GameState game) {
        for (SiteState site : game.getSites()) {
            if (site.getOwnerId() == null || site.getCardId() == null) continue;
            PlayerState owner = playerById(game, site.getOwnerId());
            int gain = site.scoringValue();
            if ("GROWTH".equals(site.getEffectCode()) && site.getUnits().size() >= 2) gain++;
            if ("HARVEST".equals(site.getEffectCode()) && countSites(game, owner.getId()) >= 3) gain++;
            gain += owner.getScoringBonusThisTurn();
            owner.setScore(owner.getScore() + gain);
        }
        int dominationTarget = game.getSites().size() / 2 + 1;
        for (PlayerState p : game.getPlayers()) {
            if (countSites(game, p.getId()) >= dominationTarget) p.setStableTicks(p.getStableTicks() + 1);
            else p.setStableTicks(0);
            p.setScoringBonusThisTurn(0);
        }
        log(game, "回合结算完成 · " + scoreLine(game));

        Optional<PlayerState> domination = game.getPlayers().stream().filter(p -> p.getStableTicks() >= 2).max(Comparator.comparingInt(PlayerState::getScore));
        if (domination.isPresent()) {
            finish(game, domination.get().getId(), "场地绝杀");
            return;
        }
        if (game.getTurnNumber() >= 16) {
            PlayerState a = game.getPlayers().get(0), b = game.getPlayers().get(1);
            if (a.getScore() == b.getScore()) finish(game, "DRAW", "积分平局");
            else finish(game, a.getScore() > b.getScore() ? a.getId() : b.getId(), "八回合积分结算");
        }
    }

    private void advanceTurn(GameState game) {
        game.setTurnNumber(game.getTurnNumber() + 1);
        game.setRound(((game.getTurnNumber() - 1) / 2) + 1);
        game.setActivePlayerIndex(1 - game.getActivePlayerIndex());
        startPhase(game, GamePhase.DEPLOY);
        for (SiteState site : game.getSites()) {
            for (UnitInstance unit : site.getUnits()) {
                unit.setExhausted(false);
                unit.setSealed(false);
                unit.setAttackRange(unit.getBaseRange());
            }
        }
        PlayerState active = game.activePlayer();
        active.setEnergy(3);
        draw(active, 2);
        if (game.getMode().equals("AI") && game.getActivePlayerIndex() == 1) enforceHandLimit(active);
        game.setStatusText(active.getName() + " 的部署阶段");
        log(game, "第" + game.getRound() + "回合 · " + active.getName() + " 获得3点灵力并抽2张牌");
    }

    private void runAiTurn(GameState game) {
        runAiDeployment(game);
        runAiContest(game);
        settle(game);
        publish(game);
    }

    private void runAiDeployment(GameState game) {
        PlayerState ai = game.activePlayer();
        startPhase(game, GamePhase.DEPLOY);
        game.setStatusText(ai.getName() + " 正在观察战场");
        publish(game);
        aiPause(450);

        int safety = 10;
        while (ai.getEnergy() > 0 && safety-- > 0) {
            CardDefinition card = chooseAiCard(game, ai);
            if (card == null) break;

            boolean used = false;
            try {
                if (card.type() == CardType.SITE) {
                    SiteState target = aiSiteTarget(game, ai);
                    if (target != null) {
                        deploySite(game, ai, card, target.getIndex());
                        used = true;
                    }
                } else if (card.type() == CardType.UNIT) {
                    SiteState target = aiUnitTarget(game, ai);
                    if (target != null) {
                        deployUnit(game, ai, card, target.getIndex());
                        used = true;
                    }
                } else if (card.type() == CardType.SPELL) {
                    String targetUnitId = aiSpellTarget(game, ai, card);
                    castSpell(game, ai, card, targetUnitId);
                    used = true;
                }
            } catch (IllegalArgumentException ignored) {
                // The board can change between target selection and action; leave the card in hand.
            }

            if (!used) break;
            ai.setEnergy(ai.getEnergy() - card.cost());
            ai.getHand().remove(card.id());
            if (card.type() == CardType.SPELL) ai.getDiscard().add(card.id());
            publish(game);
            aiPause(700);
        }
    }

    private void runAiContest(GameState game) {
        PlayerState ai = game.activePlayer();
        startPhase(game, GamePhase.CONTEST);
        game.setStatusText(ai.getName() + " 进入争夺阶段");
        log(game, ai.getName() + " 进入场地争夺阶段");
        publish(game);
        aiPause(650);

        List<UnitInstance> attackers = game.getSites().stream().flatMap(site -> site.getUnits().stream())
                .filter(unit -> ai.getId().equals(unit.getOwnerId()) && !unit.isSealed())
                .sorted(Comparator.comparingInt(UnitInstance::getPower).reversed()).toList();
        for (UnitInstance unit : attackers) {
            SiteState source = findUnitSite(game, unit.getInstanceId());
            SiteState target = game.getSites().stream()
                    .filter(site -> site.getOwnerId() != null && !ai.getId().equals(site.getOwnerId()))
                    .filter(site -> siteDistance(game, source.getIndex(), site.getIndex()) <= unit.getAttackRange())
                    .min(Comparator.comparingInt(SiteState::totalGuard)).orElse(null);
            if (target == null) continue;
            game.setStatusText(ai.getName() + " 正在争夺「" + target.getName() + "」");
            publish(game);
            aiPause(400);
            attack(game.getId(), new AttackRequest(ai.getId(), unit.getInstanceId(), target.getIndex()));
            aiPause(950);
        }
    }

    private GameState completeInitialDeployment(GameState game) {
        if (game.getPhase() != GamePhase.DEPLOY) throw new IllegalArgumentException("初始部署尚未完成");
        PlayerState current = game.activePlayer();
        if (countSites(game, current.getId()) == 0) throw new IllegalArgumentException("请先部署至少一张己方场地");

        if (game.getMode().equals("AI") && game.getActivePlayerIndex() == 0) {
            game.setActivePlayerIndex(1);
            resetPhaseDeadline(game);
            PlayerState ai = game.activePlayer();
            ai.setEnergy(3);
            game.setStatusText("你已完成初始部署 · " + ai.getName() + " 正在布置场地");
            log(game, current.getName() + " 完成初始部署，行动交给 " + ai.getName());
            publish(game);
            runAiDeployment(game);
        } else if (!bothPlayersHaveSites(game)) {
            game.setActivePlayerIndex(1 - game.getActivePlayerIndex());
            resetPhaseDeadline(game);
            game.activePlayer().setEnergy(3);
            game.setStatusText(game.activePlayer().getName() + " 的初始部署阶段");
            publish(game);
            return game;
        }

        if (!bothPlayersHaveSites(game)) throw new IllegalArgumentException("双方都布置至少一张场地后才能开始争夺");
        rollInitiative(game);
        game.setInitialContestResolved(true);
        game.setActivePlayerIndex(game.getContestStarterIndex());
        startPhase(game, GamePhase.CONTEST);
        PlayerState starter = game.activePlayer();
        log(game, "双方初始部署完成 · 开始摇先手骰");
        log(game, "先手骰 · " + game.getPlayers().get(0).getName() + " " + game.getPlayerRoll()
                + " : " + game.getOpponentRoll() + " " + game.getPlayers().get(1).getName()
                + " · " + starter.getName() + "先争夺");

        if (game.getMode().equals("AI") && game.getContestStarterIndex() == 1) {
            game.setStatusText(starter.getName() + " 获得先手，开始第一次争夺");
            publish(game);
            runAiContest(game);
            settle(game);
            if (game.getPhase() != GamePhase.FINISHED) advanceTurn(game);
        } else {
            game.setStatusText("双方场地已就位 · 你获得第一次争夺先手");
        }
        publish(game);
        return game;
    }

    private CardDefinition chooseAiCard(GameState game, PlayerState ai) {
        if (aiUnitTarget(game, ai) != null) {
            CardDefinition unit = firstAffordableCard(ai, card -> card.type() == CardType.UNIT);
            if (unit != null) return unit;
        }
        if (aiSiteTarget(game, ai) != null) {
            CardDefinition site = firstAffordableCard(ai, card -> card.type() == CardType.SITE);
            if (site != null) return site;
        }
        return firstAffordableCard(ai, card -> card.type() == CardType.SPELL && canAiUseSpell(game, ai, card));
    }

    private boolean canAiUseSpell(GameState game, PlayerState ai, CardDefinition card) {
        return switch (card.effectCode()) {
            case "DRAW" -> !ai.getDeck().isEmpty();
            case "ECHO" -> ai.getScore() < game.opponent().getScore();
            case "SEAL" -> game.getSites().stream().flatMap(s -> s.getUnits().stream())
                    .anyMatch(u -> !ai.getId().equals(u.getOwnerId()) && !u.isSealed());
            case "SURGE", "REINFORCE", "RANGE" -> game.getSites().stream().flatMap(s -> s.getUnits().stream())
                    .anyMatch(u -> ai.getId().equals(u.getOwnerId()));
            default -> false;
        };
    }

    private String aiSpellTarget(GameState game, PlayerState ai, CardDefinition card) {
        if ("DRAW".equals(card.effectCode()) || "ECHO".equals(card.effectCode())) return null;
        if ("SEAL".equals(card.effectCode())) {
            return game.getSites().stream().flatMap(s -> s.getUnits().stream())
                    .filter(u -> !ai.getId().equals(u.getOwnerId()) && !u.isSealed())
                    .max(Comparator.comparingInt(UnitInstance::getPower)).map(UnitInstance::getInstanceId)
                    .orElseThrow(() -> new IllegalArgumentException("没有可封印的敌方单位"));
        }
        return game.getSites().stream().flatMap(s -> s.getUnits().stream())
                .filter(u -> ai.getId().equals(u.getOwnerId()))
                .max(Comparator.comparingInt(u -> u.getPower() + u.getGuard()))
                .map(UnitInstance::getInstanceId)
                .orElseThrow(() -> new IllegalArgumentException("没有可增幅的己方单位"));
    }

    private CardDefinition firstAffordableCard(PlayerState ai, java.util.function.Predicate<CardDefinition> filter) {
        return ai.getHand().stream().map(catalog::require)
                .filter(card -> card.cost() <= ai.getEnergy())
                .filter(filter)
                .findFirst().orElse(null);
    }

    private SiteState aiSiteTarget(GameState game, PlayerState ai) {
        int total = game.getSites().size();
        Optional<SiteState> frontLine = game.getSites().stream()
                .filter(s -> s.getOwnerId() == null)
                .filter(s -> game.getSites().stream().anyMatch(enemy -> enemy.getOwnerId() != null
                        && !ai.getId().equals(enemy.getOwnerId())
                        && siteDistance(game, s.getIndex(), enemy.getIndex()) == 1))
                .findFirst();
        if (frontLine.isPresent()) return frontLine.get();
        return game.getSites().stream().filter(s -> s.getOwnerId() == null)
                .filter(s -> game.getSites().stream().anyMatch(own -> ai.getId().equals(own.getOwnerId())
                        && siteDistance(game, s.getIndex(), own.getIndex()) == 1))
                .findFirst()
                .orElseGet(() -> game.getSites().stream().filter(s -> s.getOwnerId() == null).findFirst()
                        .orElseGet(() -> game.getSites().stream()
                                .filter(s -> ai.getId().equals(s.getOwnerId())).findFirst().orElse(null)));
    }

    private SiteState aiUnitTarget(GameState game, PlayerState ai) {
        return game.getSites().stream()
                .filter(s -> ai.getId().equals(s.getOwnerId()) && s.getUnits().size() < 2)
                .min(Comparator.comparingInt(s -> s.getUnits().size())).orElse(null);
    }

    private void advanceExpiredPhase(GameState game, Instant now) {
        if (game.isWaitingForOpponent() || game.getPhase() == GamePhase.FINISHED || game.getPhaseEndsAt() == null || game.getPhaseEndsAt().isAfter(now)) return;
        game.setPhaseEndsAt(null);
        if (game.getPhase() == GamePhase.DEPLOY) {
            if (!game.isInitialContestResolved()) {
                autoDeployOpeningSite(game);
                log(game, game.activePlayer().getName() + " \u90e8\u7f72\u65f6\u95f4\u8017\u5c3d\uff0c\u7cfb\u7edf\u81ea\u52a8\u5b8c\u6210\u521d\u59cb\u90e8\u7f72");
                completeInitialDeployment(game);
            } else {
                startPhase(game, GamePhase.CONTEST);
                game.setStatusText("\u90e8\u7f72\u65f6\u95f4\u8017\u5c3d\uff0c\u5df2\u81ea\u52a8\u8fdb\u5165\u4e89\u593a\u9636\u6bb5");
                log(game, game.activePlayer().getName() + " \u90e8\u7f72\u8d85\u65f6 \u00b7 \u81ea\u52a8\u8fdb\u5165\u4e89\u593a");
                publish(game);
            }
            return;
        }
        if (game.getPhase() == GamePhase.CONTEST) {
            PlayerState timedOut = game.activePlayer();
            enforceHandLimit(timedOut);
            log(game, timedOut.getName() + " \u4e89\u593a\u65f6\u95f4\u8017\u5c3d\uff0c\u7cfb\u7edf\u81ea\u52a8\u7ed3\u675f\u56de\u5408");
            settle(game);
            if (game.getPhase() != GamePhase.FINISHED) {
                advanceTurn(game);
                if (game.getMode().equals("AI") && game.getActivePlayerIndex() == 1) {
                    runAiTurn(game);
                    if (game.getPhase() != GamePhase.FINISHED) advanceTurn(game);
                }
            }
            publish(game);
        }
    }

    private void autoDeployOpeningSite(GameState game) {
        PlayerState player = game.activePlayer();
        if (countSites(game, player.getId()) > 0) return;
        String cardId = findSiteCard(player);
        if (cardId == null) throw new IllegalStateException("\u521d\u59cb\u5361\u7ec4\u4e2d\u7f3a\u5c11\u573a\u5730\u5361");
        CardDefinition card = catalog.require(cardId);
        SiteState target = game.getSites().stream().filter(site -> site.getOwnerId() == null).findFirst()
                .orElseThrow(() -> new IllegalStateException("\u6ca1\u6709\u53ef\u7528\u7684\u521d\u59cb\u573a\u5730"));
        player.getHand().remove(cardId);
        player.getDeck().remove(cardId);
        player.getDiscard().remove(cardId);
        deploySite(game, player, card, target.getIndex());
        player.setEnergy(Math.max(0, player.getEnergy() - card.cost()));
    }

    private String findSiteCard(PlayerState player) {
        return java.util.stream.Stream.of(player.getHand(), player.getDeck(), player.getDiscard())
                .flatMap(Collection::stream)
                .filter(id -> catalog.require(id).type() == CardType.SITE)
                .findFirst().orElseGet(() -> catalog.starterDeck().stream()
                        .filter(id -> catalog.require(id).type() == CardType.SITE).findFirst().orElse(null));
    }

    private void startPhase(GameState game, GamePhase phase) {
        game.setPhase(phase);
        game.setPhaseDurationSeconds(PHASE_DURATION_SECONDS);
        game.setPhaseEndsAt(phase == GamePhase.FINISHED ? null : Instant.now().plusSeconds(PHASE_DURATION_SECONDS));
    }

    private void resetPhaseDeadline(GameState game) {
        if (game.getPhase() != GamePhase.FINISHED) {
            game.setPhaseDurationSeconds(PHASE_DURATION_SECONDS);
            game.setPhaseEndsAt(Instant.now().plusSeconds(PHASE_DURATION_SECONDS));
        }
    }

    private void capture(GameState game, SiteState target, PlayerState player) {
        target.setOwnerId(player.getId());
        target.setPendingAttackerId(null); target.setFortressHits(0);
        for (UnitInstance unit : target.getUnits()) unit.setOwnerId(player.getId());
    }

    private void finish(GameState game, String winnerId, String type) {
        game.setWinnerId(winnerId); game.setVictoryType(type); startPhase(game, GamePhase.FINISHED);
        game.setStatusText("DRAW".equals(winnerId) ? "八回合结束，双方平分秋色" : playerById(game, winnerId).getName() + " 以「" + type + "」获胜");
        log(game, "对局结束 · " + game.getStatusText());
        if (game.isRanked() && !game.isRankedResultRecorded() && auth != null && !"DRAW".equals(winnerId)) {
            PlayerState winner = playerById(game, winnerId);
            PlayerState loser = game.getPlayers().stream().filter(p -> !p.getId().equals(winnerId)).findFirst().orElse(null);
            if (loser != null && winner.getAccountId() != null && loser.getAccountId() != null) {
                AuthService.RankedResult result = auth.recordRankedResult(winner.getAccountId(), loser.getAccountId());
                if (result != null) {
                    game.getRatingChanges().put(winner.getId(), result.winnerDelta());
                    game.getRatingChanges().put(loser.getId(), result.loserDelta());
                    game.getRatingsAfter().put(winner.getId(), result.winnerRating());
                    game.getRatingsAfter().put(loser.getId(), result.loserRating());
                    game.setRankedResultRecorded(true);
                }
            }
        }
    }

    private PlayerState requireActive(GameState game, String playerId) {
        if (!game.activePlayer().getId().equals(playerId)) throw new IllegalArgumentException("还没有轮到该玩家行动");
        return game.activePlayer();
    }
    private void ensurePlayable(GameState game) { if (game.isWaitingForOpponent()) throw new IllegalArgumentException("正在等待对手加入"); if (game.getPhase() == GamePhase.FINISHED) throw new IllegalArgumentException("对局已经结束"); }
    private int requiredSite(Integer index) { if (index == null) throw new IllegalArgumentException("请选择目标场地"); return index; }
    private SiteState site(GameState game, int index) { return game.getSites().stream().filter(s -> s.getIndex() == index).findFirst().orElseThrow(() -> new IllegalArgumentException("场地不存在")); }
    private SiteState findUnitSite(GameState game, String id) { return game.getSites().stream().filter(s -> s.getUnits().stream().anyMatch(u -> u.getInstanceId().equals(id))).findFirst().orElseThrow(() -> new IllegalArgumentException("单位不存在")); }
    private UnitInstance requireUnit(GameState game, String id) { if (blank(id)) throw new IllegalArgumentException("请选择目标单位"); return game.getSites().stream().flatMap(s -> s.getUnits().stream()).filter(u -> u.getInstanceId().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("单位不存在")); }
    private PlayerState playerById(GameState game, String id) { return game.getPlayers().stream().filter(p -> p.getId().equals(id)).findFirst().orElseThrow(); }
    private long countSites(GameState game, String playerId) { return game.getSites().stream().filter(s -> playerId.equals(s.getOwnerId())).count(); }
    private boolean bothPlayersHaveSites(GameState game) { return game.getPlayers().stream().allMatch(p -> countSites(game, p.getId()) > 0); }
    private int siteDistance(GameState game, int from, int to) {
        SiteState a = site(game, from), b = site(game, to);
        if (a.isCore() || b.isCore()) return from == to ? 0 : 1;
        return Math.abs(a.getRow() - b.getRow()) + Math.abs(a.getColumn() - b.getColumn());
    }
    private String scoreLine(GameState game) { return game.getPlayers().get(0).getName() + " " + game.getPlayers().get(0).getScore() + " : " + game.getPlayers().get(1).getScore() + " " + game.getPlayers().get(1).getName(); }
    private boolean blank(String value) { return value == null || value.isBlank(); }

    private void rollInitiative(GameState game) {
        int playerRoll;
        int opponentRoll;
        do {
            playerRoll = random.nextInt(6) + 1;
            opponentRoll = random.nextInt(6) + 1;
        } while (playerRoll == opponentRoll);
        game.setPlayerRoll(playerRoll);
        game.setOpponentRoll(opponentRoll);
        game.setContestStarterIndex(playerRoll > opponentRoll ? 0 : 1);
    }
    private List<String> shuffledDeck() { List<String> deck = new ArrayList<>(catalog.starterDeck()); Collections.shuffle(deck, random); return deck; }
    private boolean playerHandOverflow(GameState game) { return game.activePlayer().getHand().size() > 7; }
    private void ensureOpeningSite(PlayerState player) {
        if (player.getHand().stream().anyMatch(id -> catalog.require(id).type() == CardType.SITE)) return;
        for (int i = 0; i < player.getDeck().size(); i++) {
            if (catalog.require(player.getDeck().get(i)).type() == CardType.SITE) {
                String site = player.getDeck().remove(i);
                String replaced = player.getHand().remove(player.getHand().size() - 1);
                player.getHand().add(site);
                player.getDeck().add(replaced);
                return;
            }
        }
    }
    private void draw(PlayerState p, int amount) { for (int i = 0; i < amount && !p.getDeck().isEmpty(); i++) p.getHand().add(p.getDeck().remove(0)); }
    private void enforceHandLimit(PlayerState p) { while (p.getHand().size() > 7) p.getDiscard().add(p.getHand().remove(p.getHand().size() - 1)); }
    private void log(GameState game, String line) { game.getLog().add(0, line); if (game.getLog().size() > 30) game.getLog().remove(game.getLog().size() - 1); }
    private void aiPause(long millis) { /* AI steps are published immediately; the client animates them. */ }
    private void publish(GameState game) { game.setUpdatedAt(Instant.now()); messaging.convertAndSend("/topic/matches/" + game.getId(), game); }
}






package com.fieldrealm.game.service;

import com.fieldrealm.game.domain.*;
import com.fieldrealm.game.dto.AttackRequest;
import com.fieldrealm.game.dto.PlayCardRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameService {
    static final int PHASE_DURATION_SECONDS = 60;
    static final int PHASE_DURATION_PVP = 60;
    static final int PHASE_DURATION_AI = 90;
    static final int MOMENTUM_MAX = 3;

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
        return create(mode, playerName, 3, null, false, "normal", "standard", null, null);
    }

    public synchronized GameState create(String mode, String playerName, int boardSize, String accountId, boolean ranked) {
        return create(mode, playerName, boardSize, accountId, ranked, "normal", "standard", null, null);
    }

    public synchronized GameState create(String mode, String playerName, int boardSize, String accountId, boolean ranked,
                                         String aiDifficulty, String scenario, String deckArchetype, String puzzleId) {
        String scenarioNorm = blank(scenario) ? "standard" : scenario.trim().toLowerCase(Locale.ROOT);
        String difficulty = normalizeDifficulty(aiDifficulty);
        if ("tutorial".equals(scenarioNorm)) {
            return createTutorial(playerName, accountId);
        }
        if ("puzzle".equals(scenarioNorm)) {
            return createPuzzle(playerName, accountId, puzzleId);
        }

        GameState game = new GameState();
        game.setId(UUID.randomUUID().toString().substring(0, 8));
        game.setMode((ranked || "PVP".equalsIgnoreCase(mode)) ? "PVP" : "AI");
        game.setBoardSize(normalizeBoardSize(boardSize));
        game.setRanked(ranked);
        game.setAiDifficulty(difficulty);
        game.setScenario("standard");
        game.setDeckArchetype(blank(deckArchetype) ? "balanced" : deckArchetype.trim().toLowerCase(Locale.ROOT));
        game.setWaitingForOpponent("PVP".equals(game.getMode()));

        PlayerState player = new PlayerState("p1", blank(playerName) ? "弈境旅者" : playerName.trim(), "初入弈境", "旅");
        player.setAccountId(accountId);
        initializePlayer(player, game.getDeckArchetype());
        PlayerState opponent = new PlayerState("p2", game.getMode().equals("AI") ? aiName(difficulty) : "等待中的对手", "秘境守门人", "雾");
        if (game.getMode().equals("AI")) initializePlayer(opponent, "balanced");

        game.setPlayers(new ArrayList<>(List.of(player, opponent)));
        game.setSites(createSites(game.getBoardSize()));
        game.setDominationTarget(game.getSites().size() / 2 + 1);
        matches.put(game.getId(), game);

        if (game.isWaitingForOpponent()) {
            game.setPhaseEndsAt(null);
            game.setStatusText("房间已创建，等待另一位执棋者加入");
            log(game, player.getName() + " 创建了" + game.getBoardSize() + "×" + game.getBoardSize() + "对局");
        } else {
            player.setEnergy(turnEnergy(game));
            startPhase(game, GamePhase.DEPLOY);
            refreshBoardDerived(game);
            game.setStatusText("先部署至少一张场地；部署阶段限时" + phaseSeconds(game) + "秒");
            log(game, "初始部署开始 · " + player.getName() + " 获得" + turnEnergy(game) + "点灵力 · AI难度 " + difficulty);
        }
        return game;
    }

    private GameState createTutorial(String playerName, String accountId) {
        GameState game = new GameState();
        game.setId(UUID.randomUUID().toString().substring(0, 8));
        game.setMode("AI");
        game.setBoardSize(3);
        game.setScenario("tutorial");
        game.setAiDifficulty("easy");
        game.setTutorialStep("deploy_site");
        game.setDeckArchetype("tutorial");

        PlayerState player = new PlayerState("p1", blank(playerName) ? "见习执棋者" : playerName.trim(), "教程旅人", "见");
        player.setAccountId(accountId);
        player.setDeck(new ArrayList<>(List.of(
                "site-verdant", "unit-sentinel", "spell-surge", "site-nexus",
                "unit-scout", "spell-insight", "site-archive", "unit-warden"
        )));
        player.setHand(new ArrayList<>(List.of("site-verdant", "unit-sentinel", "spell-surge", "site-nexus")));
        PlayerState ai = new PlayerState("p2", "雾隐教习", "温柔的对手", "教");
        ai.setDeck(new ArrayList<>(List.of("site-mirror", "unit-warden", "site-bastion", "unit-sentinel")));
        ai.setHand(new ArrayList<>(List.of("site-mirror", "unit-warden", "site-bastion", "unit-sentinel")));

        game.setPlayers(new ArrayList<>(List.of(player, ai)));
        game.setSites(createSites(3));
        game.setDominationTarget(5);
        player.setEnergy(3);
        startPhase(game, GamePhase.DEPLOY);
        refreshBoardDerived(game);
        game.setStatusText("教程：请跟随雾隐教习的说明；先阅读规则，再按提示操作");
        log(game, "详细教程局开始 · 讲解 + 四次实操（场地/单位/完成部署/争夺）");
        matches.put(game.getId(), game);
        return game;
    }

    private GameState createPuzzle(String playerName, String accountId, String puzzleId) {
        String id = blank(puzzleId) ? "core-break" : puzzleId.trim();
        GameState game = new GameState();
        game.setId(UUID.randomUUID().toString().substring(0, 8));
        game.setMode("AI");
        game.setBoardSize(3);
        game.setScenario("puzzle");
        game.setAiDifficulty("easy");
        game.setDeckArchetype("puzzle");
        game.getMeta().put("puzzleId", id);

        PlayerState player = new PlayerState("p1", blank(playerName) ? "残局求解者" : playerName.trim(), "谜题旅人", "谜");
        player.setAccountId(accountId);
        PlayerState ai = new PlayerState("p2", "雾隐残影", "残局守军", "影");
        game.setPlayers(new ArrayList<>(List.of(player, ai)));
        game.setSites(createSites(3));
        game.setDominationTarget(5);

        // Fixed puzzle: 3 energy, take the core this contest phase
        player.setEnergy(3);
        player.setHand(new ArrayList<>(List.of("unit-raider", "spell-surge", "spell-waygate")));
        player.setDeck(new ArrayList<>());
        ai.setHand(new ArrayList<>());
        ai.setDeck(new ArrayList<>());

        SiteState own = game.getSites().get(5); // 南境
        own.setOwnerId("p1");
        own.setCardId("site-forge");
        own.setName("赤焰锻场");
        own.setBaseGuard(1);
        own.setBasePoints(2);
        own.setEffectCode("FORGE");
        own.setEffect("从此场地发起争夺的单位战力+1。");
        UnitInstance raider = new UnitInstance("u-raid", catalog.require("unit-raider"), "p1");
        raider.setMarching(false);
        raider.setExhausted(false);
        own.getUnits().add(raider);

        SiteState core = game.getSites().get(8);
        core.setOwnerId("p2");
        core.setCardId("site-nexus");
        core.setName("灵脉交汇点");
        core.setBaseGuard(1);
        core.setBasePoints(2);
        core.setEffectCode("NEXUS");
        core.setEffect("稳定提供2点基础积分。");
        UnitInstance warden = new UnitInstance("u-ward", catalog.require("unit-warden"), "p2");
        warden.setMarching(false);
        core.getUnits().add(warden);

        SiteState east = game.getSites().get(3);
        east.setOwnerId("p2");
        east.setCardId("site-bastion");
        east.setName("玄岩壁垒");
        east.setBaseGuard(3);
        east.setBasePoints(1);
        east.setEffectCode("FORTRESS");
        east.setEffect("壁垒：需连续两次成功争夺才会易主。");

        game.setInitialContestResolved(true);
        game.setContestStarterIndex(0);
        game.setActivePlayerIndex(0);
        game.setPlayerRoll(6);
        game.setOpponentRoll(1);
        startPhase(game, GamePhase.CONTEST);
        refreshBoardDerived(game);
        game.setStatusText("残局：使用 3 点灵力与手牌，在本阶段夺取天元核心");
        log(game, "残局「核心突破」· 战力5奔袭者已在南境，核心守军玄岩守御者");
        matches.put(game.getId(), game);
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
        initializePlayer(opponent, "balanced");
        game.setWaitingForOpponent(false);
        game.setActivePlayerIndex(0);
        host.setEnergy(turnEnergy(game));
        startPhase(game, GamePhase.DEPLOY);
        refreshBoardDerived(game);
        game.setStatusText("双方已就位，部署阶段开始");
        log(game, opponent.getName() + " 加入对局 · 双方各有" + phaseSeconds(game) + "秒部署时间");
        publish(game);
        return game;
    }

    private void initializePlayer(PlayerState player, String archetype) {
        player.setDeck(shuffledDeck(archetype));
        draw(player, 4);
        ensureOpeningSite(player);
    }

    private int normalizeBoardSize(int size) { return size == 4 || size == 5 ? size : 3; }
    private int turnEnergy(GameState game) { return normalizeBoardSize(game.getBoardSize()) + 1; }
    private int maxRounds(GameState game) { return normalizeBoardSize(game.getBoardSize()) * 3; }
    private String normalizeDifficulty(String raw) {
        if (raw == null) return "normal";
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "easy", "入门" -> "easy";
            case "hard", "困难" -> "hard";
            default -> "normal";
        };
    }
    private String aiName(String difficulty) {
        return switch (difficulty) {
            case "easy" -> "雾隐学徒";
            case "hard" -> "裂隙执棋者";
            default -> "雾隐执棋者";
        };
    }

    private List<SiteState> createSites(int size) {
        List<SiteState> result = new ArrayList<>();
        if (size == 3) {
            int[][] coordinates = {{0,0},{0,1},{0,2},{1,2},{2,2},{2,1},{2,0},{1,0},{1,1}};
            String[] names = {"西北","北境","东北","东境","东南","南境","西南","西境","天元核心"};
            for (int i = 0; i < 9; i++) {
                SiteState site = new SiteState(i, names[i], i == 8, coordinates[i][0], coordinates[i][1]);
                // 四角边陲：部署费-1，积分0
                if (i == 0 || i == 2 || i == 4 || i == 6) site.setFrontier(true);
                result.add(site);
            }
            return result;
        }
        int total = size * size;
        int coreIndex = (size / 2) * size + (size / 2);
        for (int i = 0; i < total; i++) {
            int row = i / size, column = i % size;
            boolean core = i == coreIndex;
            boolean frontier = !core && (row == 0 || row == size - 1) && (column == 0 || column == size - 1);
            String position = core ? "天元核心" : "第" + (row + 1) + "行·第" + (column + 1) + "列";
            SiteState site = new SiteState(i, position, core, row, column);
            site.setFrontier(frontier);
            result.add(site);
        }
        return result;
    }

    public synchronized GameState get(String id) {
        GameState game = matches.get(id);
        if (game == null) throw new IllegalArgumentException("对局不存在或已结束");
        advanceExpiredPhase(game, Instant.now());
        refreshBoardDerived(game);
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
        if (!player.getHand().contains(card.id())) throw new IllegalArgumentException("该卡牌不在手牌中");

        boolean freeSpell = card.type() == CardType.SPELL && card.cost() == 1 && player.getMomentum() >= MOMENTUM_MAX;
        int cost = card.cost();
        if (card.type() == CardType.SITE && request.targetSiteIndex() != null) {
            SiteState targetPreview = site(game, request.targetSiteIndex());
            if (targetPreview.isFrontier() && targetPreview.getOwnerId() == null) cost = Math.max(0, cost - 1);
        }
        if (!freeSpell && player.getEnergy() < cost) throw new IllegalArgumentException("灵力不足：需要" + cost + "点，当前" + player.getEnergy() + "点");
        if (game.getPhase() != GamePhase.DEPLOY && card.type() != CardType.SPELL) {
            throw new IllegalArgumentException("当前阶段只能使用瞬发术式");
        }
        if (game.isFinalRound() && card.type() == CardType.SITE) {
            throw new IllegalArgumentException("终局回合不可新部署场地，只能争夺与术式");
        }

        switch (card.type()) {
            case SITE -> deploySite(game, player, card, requiredSite(request.targetSiteIndex()));
            case UNIT -> deployUnit(game, player, card, requiredSite(request.targetSiteIndex()));
            case SPELL -> castSpell(game, player, card, request.targetUnitId());
            case SECRET -> castSecret(game, player, card);
        }
        if (freeSpell) {
            player.setMomentum(0);
            log(game, player.getName() + " 以满层气势免费发动1费术式");
        } else {
            player.setEnergy(player.getEnergy() - cost);
        }
        player.getHand().remove(card.id());
        if (card.type() == CardType.SPELL || card.type() == CardType.SECRET) player.getDiscard().add(card.id());
        advanceTutorial(game, card);
        refreshBoardDerived(game);
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
        refreshBoardDerived(game);
        game.setStatusText("选择己方未行动单位，再选择敌方场地发起争夺");
        log(game, game.activePlayer().getName() + " 进入场地争夺阶段");
        if ("tutorial".equals(game.getScenario())) game.setTutorialStep("contest");
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
        if (attacker.isSealed()) throw new IllegalArgumentException("该单位正被封印，无法行动");
        if (attacker.isExhausted()) throw new IllegalArgumentException("该单位本回合已行动");
        if (attacker.isShaken()) throw new IllegalArgumentException("该单位处于动摇状态，本回合不可主动进攻");
        SiteState target = site(game, request.targetSiteIndex());
        if (player.getId().equals(target.getOwnerId())) throw new IllegalArgumentException("不能争夺己方场地");
        if (target.getOwnerId() == null) throw new IllegalArgumentException("无主场地需通过部署场地卡占领");
        int distance = siteDistance(game, source.getIndex(), target.getIndex());
        if (distance > attacker.getAttackRange()) {
            throw new IllegalArgumentException("目标距离为" + distance + "，该单位攻击距离只有" + attacker.getAttackRange() + "（超出射程）");
        }

        refreshBoardDerived(game);
        int attackPower = attacker.effectivePower();
        if ("FORGE".equals(source.getEffectCode())) attackPower++;
        if ("BEACON".equals(source.getEffectCode())) {
            attacker.setAttackRange(Math.max(attacker.getAttackRange(), attacker.getBaseRange() + 1 + attacker.getRangeBuff()));
        }
        if ("VANGUARD".equals(attacker.getKeyword()) && target.isCore()) attackPower += 2;
        if ("DUELIST".equals(attacker.getKeyword()) && target.getUnits().isEmpty()) attackPower++;
        // 夹击：目标被 ≥2 个己方邻接场地夹住
        long flanking = game.getSites().stream()
                .filter(s -> player.getId().equals(s.getOwnerId()) && siteDistance(game, s.getIndex(), target.getIndex()) == 1)
                .count();
        if (flanking >= 2) attackPower += 1;

        int defense = target.totalGuard();
        attacker.setExhausted(true);
        String result;
        boolean success = attackPower >= defense;
        if (success) {
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
            player.setMomentum(player.getMomentum() + 1);
            if ("tutorial".equals(game.getScenario())) {
                game.setTutorialStep("done");
                game.setStatusText("教程实操完成：你已成功争夺场地。可继续对局或查看教习总结。");
            }
            if ("puzzle".equals(game.getScenario()) && target.isCore() && player.getId().equals(target.getOwnerId())) {
                finish(game, player.getId(), "残局通关");
                refreshBoardDerived(game);
                publish(game);
                return game;
            }
        } else {
            result = "守方稳住场地，争夺失败";
            if ("MIRROR".equals(target.getEffectCode()) && target.getOwnerId() != null) {
                playerById(game, target.getOwnerId()).setScore(playerById(game, target.getOwnerId()).getScore() + 1);
                result += "；镜潮回廊反制获得1分";
            }
            target.setPendingAttackerId(null);
            target.setFortressHits(0);
            player.setMomentum(Math.max(0, player.getMomentum() - 1));
        }
        game.setStatusText("距离 " + distance + " · 战力 " + attackPower + " vs 守力 " + defense + " · " + result
                + (flanking >= 2 ? " · 夹击+1" : ""));
        log(game, attacker.getName() + " 跨越距离" + distance + "争夺 " + target.getName() + "：" + result);
        refreshBoardDerived(game);
        game.setUpdatedAt(Instant.now());
        publish(game);
        return game;
    }

    public synchronized GameState endTurn(String matchId, String playerId) {
        GameState game = get(matchId);
        requireActive(game, playerId);
        int autoDiscarded = enforceHandLimit(game.activePlayer());
        if (autoDiscarded > 0) {
            game.setStatusText("手牌超过7张，系统自动弃置 " + autoDiscarded + " 张");
            log(game, game.activePlayer().getName() + " 未主动弃牌，系统自动弃置 " + autoDiscarded + " 张");
        }
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
        refreshBoardDerived(game);
        game.setUpdatedAt(Instant.now());
        publish(game);
        return game;
    }

    /** 调防：花 1 灵力把单位移到相邻己方场地 */
    public synchronized GameState moveUnit(String matchId, String playerId, String unitId, int targetSiteIndex) {
        GameState game = get(matchId);
        ensurePlayable(game);
        PlayerState player = requireActive(game, playerId);
        if (game.getPhase() != GamePhase.DEPLOY) throw new IllegalArgumentException("只能在部署阶段调防");
        if (player.getEnergy() < 1) throw new IllegalArgumentException("灵力不足：调防需要1点灵力");

        SiteState from = findUnitSite(game, unitId);
        UnitInstance unit = from.getUnits().stream().filter(u -> u.getInstanceId().equals(unitId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("单位不存在"));
        if (!player.getId().equals(unit.getOwnerId())) throw new IllegalArgumentException("只能调防己方单位");
        SiteState to = site(game, targetSiteIndex);
        if (!player.getId().equals(to.getOwnerId())) throw new IllegalArgumentException("只能调防到己方场地");
        if (from.getIndex() == to.getIndex()) throw new IllegalArgumentException("单位已在该场地");
        if (siteDistance(game, from.getIndex(), to.getIndex()) != 1) throw new IllegalArgumentException("只能调防到相邻场地");
        if (to.getUnits().size() >= 2) throw new IllegalArgumentException("目标场地驻场位已满");

        from.getUnits().remove(unit);
        if ("ARCHITECT".equals(unit.getKeyword())) from.setBaseGuard(Math.max(0, from.getBaseGuard() - 1));
        unit.setRootedTurns(0);
        unit.setMarching(false);
        to.getUnits().add(unit);
        if ("ARCHITECT".equals(unit.getKeyword())) to.setBaseGuard(to.getBaseGuard() + 1);
        player.setEnergy(player.getEnergy() - 1);
        game.setStatusText("「" + unit.getName() + "」调防至" + to.getName());
        log(game, player.getName() + " 花费1灵力将「" + unit.getName() + "」从" + from.getName() + "调防至" + to.getName());
        refreshBoardDerived(game);
        publish(game);
        return game;
    }

    /** 筛牌：弃1抽1，每回合限1次，0灵力 */
    public synchronized GameState cycleCard(String matchId, String playerId, String cardId) {
        GameState game = get(matchId);
        ensurePlayable(game);
        PlayerState player = requireActive(game, playerId);
        if (game.getPhase() != GamePhase.DEPLOY && game.getPhase() != GamePhase.CONTEST) {
            throw new IllegalArgumentException("当前阶段无法筛牌");
        }
        if (player.isCycleUsedThisTurn()) throw new IllegalArgumentException("本回合已使用过筛牌");
        if (!player.getHand().remove(cardId)) throw new IllegalArgumentException("手牌中没有这张卡");
        player.getDiscard().add(cardId);
        draw(player, 1);
        player.setCycleUsedThisTurn(true);
        CardDefinition card = catalog.require(cardId);
        // 主动弃牌有收益
        if (card.type() == CardType.SITE) {
            draw(player, 1);
            game.setStatusText("弃置场地卡，额外抽1张");
        } else if (card.type() == CardType.UNIT) {
            player.getHand().stream().map(catalog::require).filter(c -> c.type() == CardType.UNIT).findFirst();
            game.getSites().stream().flatMap(s -> s.getUnits().stream())
                    .filter(u -> player.getId().equals(u.getOwnerId()) && !u.isExhausted())
                    .findFirst()
                    .ifPresent(u -> {
                        u.setAttackRange(u.getAttackRange() + 1);
                        u.setRangeBuff(u.getRangeBuff() + 1);
                    });
            game.setStatusText("弃置单位卡：一名未行动单位本回合射程+1");
        } else {
            game.setStatusText("已筛牌：弃1抽1");
        }
        log(game, player.getName() + " 筛牌弃置「" + card.name() + "」");
        refreshBoardDerived(game);
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
        if (game.getPhase() != GamePhase.DEPLOY && game.getPhase() != GamePhase.CONTEST) {
            throw new IllegalArgumentException("只能在部署或争夺阶段弃牌");
        }
        if (!player.getHand().remove(cardId)) throw new IllegalArgumentException("手牌中没有这张卡");
        CardDefinition card = catalog.require(cardId);
        if (card.type() == CardType.SITE) {
            draw(player, 1);
            player.getDiscard().add(cardId);
            game.setStatusText("已主动弃置「" + card.name() + "」并抽1张");
        } else if (card.type() == CardType.UNIT) {
            player.getDiscard().add(cardId);
            game.getSites().stream().flatMap(s -> s.getUnits().stream())
                    .filter(u -> player.getId().equals(u.getOwnerId()))
                    .findFirst()
                    .ifPresent(u -> {
                        u.setAttackRange(u.getAttackRange() + 1);
                        u.setRangeBuff(u.getRangeBuff() + 1);
                    });
            game.setStatusText("已主动弃置「" + card.name() + "」：己方单位本回合射程+1");
        } else {
            player.getDiscard().add(cardId);
            game.setStatusText("已主动弃置「" + card.name() + "」");
        }
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
        boolean overwrite = player.getId().equals(site.getOwnerId()) && site.getCardId() != null;
        // 覆盖：保留单位，替换场地效果
        site.setOwnerId(player.getId());
        site.setCardId(card.id());
        site.setName(card.name());
        int architectBonus = (int) site.getUnits().stream().filter(u -> "ARCHITECT".equals(u.getKeyword())).count();
        site.setBaseGuard(card.guard() + architectBonus);
        int points = site.isFrontier() ? 0 : card.points();
        site.setBasePoints(points);
        site.setEffectCode(card.effectCode());
        site.setEffect(card.effect() + (site.isFrontier() ? "（边陲：积分0）" : ""));
        site.setPendingAttackerId(null);
        site.setFortressHits(0);
        if ("ARCHIVE".equals(card.effectCode())) draw(player, 1);
        log(game, player.getName() + (overwrite ? " 覆盖改造" : " 在") + site.getPosition() + (overwrite ? "" : "部署") + "场地「" + card.name() + "」");
        game.setStatusText(overwrite ? "场地覆盖完成，效果已替换" : "场地部署完成，可继续部署或进入争夺");
        if ("tutorial".equals(game.getScenario()) && "deploy_site".equals(game.getTutorialStep())) {
            game.setTutorialStep("deploy_unit");
            game.setStatusText("教程实操：点单位卡「星幕哨卫」，再点你的己方场地驻扎");
        }
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
        log(game, player.getName() + " 将「" + card.name() + "」部署至" + site.getName() + "（行军：当回合不可争夺）");
        game.setStatusText("单位驻场完成（部署当回合处于行军）");
        if ("tutorial".equals(game.getScenario()) && "deploy_unit".equals(game.getTutorialStep())) {
            game.setTutorialStep("ready_contest");
            game.setStatusText("教程实操：点击「完成部署」，让教习也完成布阵并摇先手骰");
        }
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
                target.setPowerBuff(target.getPowerBuff() + 2);
            }
            case "REINFORCE" -> {
                UnitInstance target = requireUnit(game, targetUnitId);
                if (!player.getId().equals(target.getOwnerId())) throw new IllegalArgumentException("请选择己方单位");
                target.setGuard(target.getGuard() + 2);
                target.setGuardBuff(target.getGuardBuff() + 2);
            }
            case "REFRESH" -> {
                UnitInstance target = requireUnit(game, targetUnitId);
                if (!player.getId().equals(target.getOwnerId())) throw new IllegalArgumentException("请选择己方单位");
                target.setExhausted(false);
                target.setShaken(false);
            }
            case "EXPEDITION" -> {
                draw(player, 1);
                player.setEnergy(player.getEnergy() + 1);
            }
            case "RANGE" -> {
                UnitInstance target = requireUnit(game, targetUnitId);
                if (!player.getId().equals(target.getOwnerId())) throw new IllegalArgumentException("请选择己方单位");
                target.setAttackRange(target.getAttackRange() + 1);
                target.setRangeBuff(target.getRangeBuff() + 1);
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
        refreshBoardDerived(game);
        for (SiteState site : game.getSites()) {
            if (site.getOwnerId() == null || site.getCardId() == null) continue;
            PlayerState owner = playerById(game, site.getOwnerId());
            int gain = site.scoringValue();
            if ("GROWTH".equals(site.getEffectCode()) && site.getUnits().size() >= 2) gain++;
            if ("HARVEST".equals(site.getEffectCode()) && countSites(game, owner.getId()) >= 3) gain++;
            // 邻接协同：有相邻己方场时结算 +1
            if (hasAdjacentOwnSite(game, site, owner.getId())) gain += 1;
            gain += owner.getScoringBonusThisTurn();
            owner.setScore(owner.getScore() + gain);
        }
        int dominationTarget = game.getSites().size() / 2 + 1;
        game.setDominationTarget(dominationTarget);
        for (PlayerState p : game.getPlayers()) {
            if (countSites(game, p.getId()) >= dominationTarget) p.setStableTicks(p.getStableTicks() + 1);
            else {
                if (p.getStableTicks() > 0) log(game, p.getName() + " 的绝杀进度被打断，重置为 0/" + 2);
                p.setStableTicks(0);
            }
            p.setScoringBonusThisTurn(0);
        }
        // 分差压迫：落后≥6且场更少时，落后方气势+1
        PlayerState a = game.getPlayers().get(0), b = game.getPlayers().get(1);
        if (Math.abs(a.getScore() - b.getScore()) >= 6) {
            PlayerState behind = a.getScore() < b.getScore() ? a : b;
            PlayerState ahead = a.getScore() < b.getScore() ? b : a;
            if (countSites(game, behind.getId()) < countSites(game, ahead.getId())) {
                behind.setMomentum(behind.getMomentum() + 1);
                log(game, behind.getName() + " 因分差压迫获得1点气势");
            }
        }
        log(game, "回合结算完成 · " + scoreLine(game)
                + " · 绝杀进度 "
                + a.getName() + " " + a.getStableTicks() + "/2 · "
                + b.getName() + " " + b.getStableTicks() + "/2");

        Optional<PlayerState> domination = game.getPlayers().stream()
                .filter(p -> p.getStableTicks() >= 2)
                .max(Comparator.comparingInt(PlayerState::getScore));
        if (domination.isPresent()) {
            finish(game, domination.get().getId(), "场地绝杀");
            return;
        }
        int roundLimit = maxRounds(game);
        if (game.getTurnNumber() >= roundLimit * 2) {
            if (a.getScore() == b.getScore()) finish(game, "DRAW", "积分平局");
            else finish(game, a.getScore() > b.getScore() ? a.getId() : b.getId(), roundLimit + "回合积分结算");
        }
    }

    private void advanceTurn(GameState game) {
        game.setTurnNumber(game.getTurnNumber() + 1);
        game.setRound(((game.getTurnNumber() - 1) / 2) + 1);
        game.setActivePlayerIndex(1 - game.getActivePlayerIndex());
        int roundLimit = maxRounds(game);
        game.setFinalRound(game.getRound() >= roundLimit);
        startPhase(game, GamePhase.DEPLOY);
        for (SiteState site : game.getSites()) {
            for (UnitInstance unit : site.getUnits()) {
                unit.setExhausted(false);
                unit.setSealed(false);
                unit.setMarching(false);
                // 动摇持续到该单位自己的下一回合开始时清除
                if (unit.getOwnerId().equals(game.activePlayer().getId()) && unit.isShaken()) {
                    unit.setShaken(false);
                }
                unit.setAttackRange(unit.getBaseRange() + unit.getRangeBuff());
                // 扎根：同场连续驻守
                if (unit.getOwnerId().equals(game.activePlayer().getId())) {
                    unit.setRootedTurns(unit.getRootedTurns() + 1);
                }
            }
        }
        PlayerState active = game.activePlayer();
        active.setEnergy(turnEnergy(game));
        active.setCycleUsedThisTurn(false);
        draw(active, 2);
        refreshBoardDerived(game);
        String chapter = game.getRound() <= 2 ? "扩张期" : game.isFinalRound() ? "终局" : "冲突期";
        String warn = "";
        for (PlayerState p : game.getPlayers()) {
            if (p.getStableTicks() == 1) warn += " · ⚠ " + p.getName() + " 绝杀进度1/2，再结算一次将触发绝杀";
        }
        game.setStatusText(active.getName() + " 的部署阶段（" + chapter + "）" + (game.isFinalRound() ? " · 终局不可新部署场地" : "") + warn);
        log(game, "第" + game.getRound() + "回合 · " + active.getName() + " 获得" + turnEnergy(game) + "点灵力并抽2张牌" + warn);
    }

    private void runAiTurn(GameState game) {
        if ("puzzle".equals(game.getScenario()) || "tutorial".equals(game.getScenario())) {
            // 教程/残局 AI 极简
            if ("tutorial".equals(game.getScenario()) && !game.isInitialContestResolved()) {
                runAiDeployment(game);
                return;
            }
        }
        runAiDeployment(game);
        runAiContest(game);
        int autoDiscarded = enforceHandLimit(game.activePlayer());
        if (autoDiscarded > 0) {
            log(game, game.activePlayer().getName() + " 未主动弃牌，系统自动弃置 " + autoDiscarded + " 张");
            publish(game);
        }
        game.setStatusText(game.activePlayer().getName() + " 结束了回合");
        log(game, game.activePlayer().getName() + " 结束回合，开始结算");
        settle(game);
        publish(game);
    }

    private void runAiDeployment(GameState game) {
        PlayerState ai = game.activePlayer();
        startPhase(game, GamePhase.DEPLOY);
        game.setStatusText(ai.getName() + " 正在观察战场");
        publish(game);

        int safety = 12;
        while (ai.getEnergy() > 0 && safety-- > 0) {
            CardDefinition card = chooseAiCard(game, ai);
            if (card == null) break;
            boolean used = false;
            try {
                if (card.type() == CardType.SITE && !game.isFinalRound()) {
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
                } else if (card.type() == CardType.SECRET) {
                    castSecret(game, ai, card);
                    used = true;
                }
            } catch (IllegalArgumentException ignored) { }

            if (!used) break;
            int cost = card.cost();
            ai.setEnergy(ai.getEnergy() - cost);
            ai.getHand().remove(card.id());
            if (card.type() == CardType.SPELL || card.type() == CardType.SECRET) ai.getDiscard().add(card.id());
            refreshBoardDerived(game);
            publish(game);
        }
        // hard: try reposition to defend core
        if ("hard".equals(game.getAiDifficulty())) {
            tryAiDefendMove(game, ai);
        }
    }

    private void tryAiDefendMove(GameState game, PlayerState ai) {
        SiteState core = game.getSites().stream().filter(SiteState::isCore).findFirst().orElse(null);
        if (core == null || !ai.getId().equals(core.getOwnerId()) || core.getUnits().size() >= 2 || ai.getEnergy() < 1) return;
        for (SiteState site : game.getSites()) {
            if (!ai.getId().equals(site.getOwnerId()) || site.isCore()) continue;
            if (siteDistance(game, site.getIndex(), core.getIndex()) != 1) continue;
            Optional<UnitInstance> unit = site.getUnits().stream()
                    .filter(u -> u.effectiveGuard() >= 2)
                    .findFirst();
            if (unit.isPresent()) {
                try {
                    moveUnit(game.getId(), ai.getId(), unit.get().getInstanceId(), core.getIndex());
                } catch (Exception ignored) { }
                return;
            }
        }
    }

    private void runAiContest(GameState game) {
        PlayerState ai = game.activePlayer();
        startPhase(game, GamePhase.CONTEST);
        game.setStatusText(ai.getName() + " 进入争夺阶段");
        log(game, ai.getName() + " 进入场地争夺阶段");
        publish(game);

        List<UnitInstance> attackers = game.getSites().stream().flatMap(site -> site.getUnits().stream())
                .filter(unit -> ai.getId().equals(unit.getOwnerId()) && !unit.isSealed() && !unit.isExhausted()
                        && !unit.isMarching() && !unit.isShaken())
                .sorted(Comparator.comparingInt(UnitInstance::effectivePower).reversed()).toList();
        for (UnitInstance unit : attackers) {
            SiteState source = findUnitSite(game, unit.getInstanceId());
            SiteState target = pickAiAttackTarget(game, ai, source, unit);
            if (target == null) continue;
            game.setStatusText(ai.getName() + " 正在争夺「" + target.getName() + "」");
            publish(game);
            try {
                attack(game.getId(), new AttackRequest(ai.getId(), unit.getInstanceId(), target.getIndex()));
            } catch (IllegalArgumentException ignored) { }
        }
    }

    private SiteState pickAiAttackTarget(GameState game, PlayerState ai, SiteState source, UnitInstance unit) {
        String difficulty = game.getAiDifficulty() == null ? "normal" : game.getAiDifficulty();
        List<SiteState> candidates = game.getSites().stream()
                .filter(site -> site.getOwnerId() != null && !ai.getId().equals(site.getOwnerId()))
                .filter(site -> siteDistance(game, source.getIndex(), site.getIndex()) <= unit.getAttackRange())
                .toList();
        if (candidates.isEmpty()) return null;

        if ("easy".equals(difficulty)) {
            return candidates.stream().min(Comparator.comparingInt(SiteState::totalGuard)).orElse(null);
        }

        // normal/hard: score targets
        int dominationTarget = game.getDominationTarget() > 0 ? game.getDominationTarget() : game.getSites().size() / 2 + 1;
        PlayerState human = game.getPlayers().stream().filter(p -> !p.getId().equals(ai.getId())).findFirst().orElse(game.opponent());
        return candidates.stream().max(Comparator.comparingInt(site -> {
            int score = 0;
            int power = unit.effectivePower();
            if ("FORGE".equals(source.getEffectCode())) power++;
            int def = site.totalGuard();
            if (power > def) score += 20 + (power - def);
            else score -= 10;
            if (site.isCore()) score += "hard".equals(difficulty) ? 25 : 12;
            if (human.getStableTicks() >= 1 && human.getId().equals(site.getOwnerId())) score += 30; // break kill threat
            if (countSites(game, ai.getId()) + 1 >= dominationTarget) score += 15;
            if ("FORTRESS".equals(site.getEffectCode()) && site.getFortressHits() == 1
                    && ai.getId().equals(site.getPendingAttackerId())) score += 18;
            score -= def;
            return score;
        })).orElse(null);
    }

    private GameState completeInitialDeployment(GameState game) {
        if (game.getPhase() != GamePhase.DEPLOY) throw new IllegalArgumentException("初始部署尚未完成");
        PlayerState current = game.activePlayer();
        if (countSites(game, current.getId()) == 0) throw new IllegalArgumentException("请先部署至少一张己方场地");

        if (game.getMode().equals("AI") && game.getActivePlayerIndex() == 0) {
            game.setActivePlayerIndex(1);
            resetPhaseDeadline(game);
            PlayerState ai = game.activePlayer();
            ai.setEnergy(turnEnergy(game));
            game.setStatusText("你已完成初始部署 · " + ai.getName() + " 正在布置场地");
            log(game, current.getName() + " 完成初始部署，行动交给 " + ai.getName());
            publish(game);
            runAiDeployment(game);
        } else if (!bothPlayersHaveSites(game)) {
            game.setActivePlayerIndex(1 - game.getActivePlayerIndex());
            resetPhaseDeadline(game);
            game.activePlayer().setEnergy(turnEnergy(game));
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
        // Clear marching for initial contest units of starter? Keep march rules: only newly deployed this turn - initial deploy may allow attack after dice - for fairness clear march on both after init
        for (SiteState site : game.getSites()) {
            for (UnitInstance unit : site.getUnits()) unit.setMarching(false);
        }
        refreshBoardDerived(game);
        game.setStatusText(starter.getName() + " 获得先手，开始第一次争夺");
        if ("tutorial".equals(game.getScenario())) game.setTutorialStep("contest");
        if (game.getMode().equals("AI") && game.getContestStarterIndex() == 1) {
            publish(game);
            runAiContest(game);
            if (game.getPhase() != GamePhase.FINISHED) {
                settle(game);
                if (game.getPhase() != GamePhase.FINISHED) advanceTurn(game);
            }
        }
        publish(game);
        return game;
    }

    private CardDefinition chooseAiCard(GameState game, PlayerState ai) {
        String difficulty = game.getAiDifficulty() == null ? "normal" : game.getAiDifficulty();
        // Prefer seal high threat on hard
        if (!"easy".equals(difficulty)) {
            CardDefinition seal = firstAffordableCard(ai, c -> c.type() == CardType.SPELL && "SEAL".equals(c.effectCode()) && canAiUseSpell(game, ai, c));
            if (seal != null && hasHighThreatEnemy(game, ai)) return seal;
        }
        if (aiUnitTarget(game, ai) != null) {
            CardDefinition unit = firstAffordableCard(ai, card -> card.type() == CardType.UNIT);
            if (unit != null) return unit;
        }
        if (!game.isFinalRound() && aiSiteTarget(game, ai) != null) {
            CardDefinition site = firstAffordableCard(ai, card -> card.type() == CardType.SITE);
            if (site != null) return site;
        }
        return firstAffordableCard(ai, card -> card.type() == CardType.SPELL && canAiUseSpell(game, ai, card));
    }

    private boolean hasHighThreatEnemy(GameState game, PlayerState ai) {
        return game.getSites().stream().flatMap(s -> s.getUnits().stream())
                .anyMatch(u -> !ai.getId().equals(u.getOwnerId()) && u.effectivePower() >= 4 && !u.isSealed());
    }

    private boolean canAiUseSpell(GameState game, PlayerState ai, CardDefinition card) {
        return switch (card.effectCode()) {
            case "DRAW" -> !ai.getDeck().isEmpty();
            case "ECHO" -> ai.getScore() < game.opponent().getScore();
            case "SEAL" -> game.getSites().stream().flatMap(s -> s.getUnits().stream())
                    .anyMatch(u -> !ai.getId().equals(u.getOwnerId()) && !u.isSealed());
            case "SURGE", "REINFORCE", "RANGE", "REFRESH" -> game.getSites().stream().flatMap(s -> s.getUnits().stream())
                    .anyMatch(u -> ai.getId().equals(u.getOwnerId()));
            case "EXPEDITION" -> true;
            default -> false;
        };
    }

    private String aiSpellTarget(GameState game, PlayerState ai, CardDefinition card) {
        if ("DRAW".equals(card.effectCode()) || "ECHO".equals(card.effectCode()) || "EXPEDITION".equals(card.effectCode())) return null;
        if ("SEAL".equals(card.effectCode())) {
            return game.getSites().stream().flatMap(s -> s.getUnits().stream())
                    .filter(u -> !ai.getId().equals(u.getOwnerId()) && !u.isSealed())
                    .max(Comparator.comparingInt(UnitInstance::effectivePower)).map(UnitInstance::getInstanceId)
                    .orElseThrow(() -> new IllegalArgumentException("没有可封印的敌方单位"));
        }
        return game.getSites().stream().flatMap(s -> s.getUnits().stream())
                .filter(u -> ai.getId().equals(u.getOwnerId()))
                .max(Comparator.comparingInt(u -> u.effectivePower() + u.effectiveGuard()))
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
        // Prefer core if empty
        Optional<SiteState> core = game.getSites().stream().filter(SiteState::isCore).filter(s -> s.getOwnerId() == null).findFirst();
        if (core.isPresent() && !"easy".equals(game.getAiDifficulty())) return core.get();
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
        // Prefer reinforce core / low unit sites
        Comparator<SiteState> cmp = Comparator
                .comparing((SiteState s) -> s.isCore() ? 0 : 1)
                .thenComparingInt(s -> s.getUnits().size());
        return game.getSites().stream()
                .filter(s -> ai.getId().equals(s.getOwnerId()) && s.getUnits().size() < 2)
                .min(cmp).orElse(null);
    }

    private void advanceExpiredPhase(GameState game, Instant now) {
        if (game.isWaitingForOpponent() || game.getPhase() == GamePhase.FINISHED || game.getPhaseEndsAt() == null || game.getPhaseEndsAt().isAfter(now)) return;
        game.setPhaseEndsAt(null);
        if (game.getPhase() == GamePhase.DEPLOY) {
            if (!game.isInitialContestResolved()) {
                autoDeployOpeningSite(game);
                log(game, game.activePlayer().getName() + " 部署时间耗尽，系统自动完成初始部署");
                completeInitialDeployment(game);
            } else {
                startPhase(game, GamePhase.CONTEST);
                game.setStatusText("部署时间耗尽，已自动进入争夺阶段");
                log(game, game.activePlayer().getName() + " 部署超时 · 自动进入争夺");
                publish(game);
            }
            return;
        }
        if (game.getPhase() == GamePhase.CONTEST) {
            PlayerState timedOut = game.activePlayer();
            enforceHandLimit(timedOut);
            log(game, timedOut.getName() + " 争夺时间耗尽，系统自动结束回合");
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
        if (cardId == null) throw new IllegalStateException("初始卡组中缺少场地卡");
        CardDefinition card = catalog.require(cardId);
        SiteState target = game.getSites().stream().filter(site -> site.getOwnerId() == null).findFirst()
                .orElseThrow(() -> new IllegalStateException("没有可用的初始场地"));
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

    private int phaseSeconds(GameState game) {
        if ("tutorial".equals(game.getScenario()) || "puzzle".equals(game.getScenario())) return 180;
        if ("AI".equals(game.getMode()) && !game.isRanked()) return PHASE_DURATION_AI;
        return PHASE_DURATION_PVP;
    }

    private void startPhase(GameState game, GamePhase phase) {
        game.setPhase(phase);
        // 进入争夺时解除行军：本回合部署的单位可以参与争夺，但保留「新驻」表现至回合刷新
        if (phase == GamePhase.CONTEST) {
            for (SiteState site : game.getSites()) {
                for (UnitInstance unit : site.getUnits()) {
                    if (game.activePlayer().getId().equals(unit.getOwnerId())) unit.setMarching(false);
                }
            }
        }
        int seconds = phaseSeconds(game);
        game.setPhaseDurationSeconds(seconds);
        game.setPhaseEndsAt(phase == GamePhase.FINISHED ? null : Instant.now().plusSeconds(seconds));
    }

    private void resetPhaseDeadline(GameState game) {
        if (game.getPhase() != GamePhase.FINISHED) {
            int seconds = phaseSeconds(game);
            game.setPhaseDurationSeconds(seconds);
            game.setPhaseEndsAt(Instant.now().plusSeconds(seconds));
        }
    }

    private void capture(GameState game, SiteState target, PlayerState player) {
        String previousOwner = target.getOwnerId();
        target.setOwnerId(player.getId());
        target.setPendingAttackerId(null);
        target.setFortressHits(0);
        for (UnitInstance unit : target.getUnits()) {
            unit.setOwnerId(player.getId());
            unit.setShaken(true);
            unit.setRootedTurns(0);
            unit.setExhausted(true);
        }
        if (previousOwner != null) {
            // 绝杀打断已在 settle 中按场地数量处理
            log(game, target.getName() + " 易主：" + previousOwner + " → " + player.getId());
        }
    }

    private void finish(GameState game, String winnerId, String type) {
        game.setWinnerId(winnerId);
        game.setVictoryType(type);
        startPhase(game, GamePhase.FINISHED);
        game.setStatusText("DRAW".equals(winnerId)
                ? maxRounds(game) + "回合结束，双方平分秋色"
                : playerById(game, winnerId).getName() + " 以「" + type + "」获胜");
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

    private void refreshBoardDerived(GameState game) {
        game.setDominationTarget(game.getSites().size() / 2 + 1);
        int roundLimit = maxRounds(game);
        game.setFinalRound(game.getRound() >= roundLimit && game.isInitialContestResolved());
        // 绝杀预警写入 meta
        Map<String, Object> meta = game.getMeta();
        if (meta == null) {
            meta = new LinkedHashMap<>();
            game.setMeta(meta);
        }
        List<Map<String, Object>> threats = new ArrayList<>();
        for (PlayerState p : game.getPlayers()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("playerId", p.getId());
            row.put("stableTicks", p.getStableTicks());
            row.put("sites", countSites(game, p.getId()));
            row.put("dominating", countSites(game, p.getId()) >= game.getDominationTarget());
            threats.add(row);
        }
        meta.put("domination", threats);
        meta.put("maxRounds", roundLimit);
        meta.put("chapter", game.getRound() <= 2 ? "expansion" : game.isFinalRound() ? "finale" : "conflict");
    }

    private boolean hasAdjacentOwnSite(GameState game, SiteState site, String ownerId) {
        return game.getSites().stream().anyMatch(other ->
                other.getIndex() != site.getIndex()
                        && ownerId.equals(other.getOwnerId())
                        && siteDistance(game, site.getIndex(), other.getIndex()) == 1);
    }

    private void advanceTutorial(GameState game, CardDefinition card) {
        if (!"tutorial".equals(game.getScenario())) return;
        // steps handled in deploy methods
    }

    private PlayerState requireActive(GameState game, String playerId) {
        if (!game.activePlayer().getId().equals(playerId)) throw new IllegalArgumentException("还没有轮到该玩家行动");
        return game.activePlayer();
    }
    private void ensurePlayable(GameState game) {
        if (game.isWaitingForOpponent()) throw new IllegalArgumentException("正在等待对手加入");
        if (game.getPhase() == GamePhase.FINISHED) throw new IllegalArgumentException("对局已经结束");
    }
    private int requiredSite(Integer index) {
        if (index == null) throw new IllegalArgumentException("请选择目标场地");
        return index;
    }
    private SiteState site(GameState game, int index) {
        return game.getSites().stream().filter(s -> s.getIndex() == index).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("场地不存在"));
    }
    private SiteState findUnitSite(GameState game, String id) {
        return game.getSites().stream().filter(s -> s.getUnits().stream().anyMatch(u -> u.getInstanceId().equals(id))).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("单位不存在"));
    }
    private UnitInstance requireUnit(GameState game, String id) {
        if (blank(id)) throw new IllegalArgumentException("请选择目标单位");
        return game.getSites().stream().flatMap(s -> s.getUnits().stream()).filter(u -> u.getInstanceId().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("单位不存在"));
    }
    private PlayerState playerById(GameState game, String id) {
        return game.getPlayers().stream().filter(p -> p.getId().equals(id)).findFirst().orElseThrow();
    }
    private long countSites(GameState game, String playerId) {
        return game.getSites().stream().filter(s -> playerId.equals(s.getOwnerId())).count();
    }
    private boolean bothPlayersHaveSites(GameState game) {
        return game.getPlayers().stream().allMatch(p -> countSites(game, p.getId()) > 0);
    }
    private int siteDistance(GameState game, int from, int to) {
        SiteState a = site(game, from), b = site(game, to);
        if (a.isCore() || b.isCore()) return from == to ? 0 : 1;
        return Math.abs(a.getRow() - b.getRow()) + Math.abs(a.getColumn() - b.getColumn());
    }
    private String scoreLine(GameState game) {
        return game.getPlayers().get(0).getName() + " " + game.getPlayers().get(0).getScore()
                + " : " + game.getPlayers().get(1).getScore() + " " + game.getPlayers().get(1).getName();
    }
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

    private List<String> shuffledDeck(String archetype) {
        List<String> deck = new ArrayList<>(catalog.deckForArchetype(archetype));
        Collections.shuffle(deck, random);
        return deck;
    }

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
    private void draw(PlayerState p, int amount) {
        for (int i = 0; i < amount; i++) {
            if (p.getDeck().isEmpty() && !p.getDiscard().isEmpty()) {
                p.getDeck().addAll(p.getDiscard());
                p.getDiscard().clear();
                Collections.shuffle(p.getDeck(), random);
            }
            if (p.getDeck().isEmpty()) break;
            p.getHand().add(p.getDeck().remove(0));
        }
    }
    private int enforceHandLimit(PlayerState p) {
        int discarded = 0;
        while (p.getHand().size() > 7) {
            p.getDiscard().add(p.getHand().remove(p.getHand().size() - 1));
            discarded++;
        }
        return discarded;
    }
    private void log(GameState game, String line) {
        game.getLog().add(0, line);
        if (game.getLog().size() > 40) game.getLog().remove(game.getLog().size() - 1);
    }
    private void aiPause(long millis) { /* client animates */ }
    private void publish(GameState game) {
        game.setUpdatedAt(Instant.now());
        if (messaging != null) messaging.convertAndSend("/topic/matches/" + game.getId(), game);
    }
}

package com.fieldrealm.game.service;

import com.fieldrealm.game.domain.*;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CardCatalogService {
    private List<CardDefinition> cards;
    private final Map<String, CardDefinition> byId;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final Path storage = Paths.get("data", "cards.json");

    public CardCatalogService() {
        cards = List.of(
            card("site-verdant", "苍翠庭院", CardType.SITE, Rarity.C, 1, 0, 1, 1, "GROWTH", "驻有2个单位时，结算额外获得1分。", "枝叶会记住每一次并肩。", "自然", "稳场"),
            card("site-bastion", "玄岩壁垒", CardType.SITE, Rarity.SR, 2, 0, 3, 1, "FORTRESS", "壁垒：需连续两次成功争夺才会易主。", "山不拒绝风，只拒绝退让。", "壁垒", "防守"),
            card("site-archive", "星尘典藏馆", CardType.SITE, Rarity.R, 1, 0, 1, 1, "ARCHIVE", "部署后立即抽1张牌。", "每一场争夺，都早有星图记载。", "抽牌", "节奏"),
            card("site-forge", "赤焰锻场", CardType.SITE, Rarity.R, 2, 0, 1, 2, "FORGE", "从此场地发起争夺的单位战力+1。", "火焰不制造胜利，只重铸决心。", "进攻", "高分"),
            card("site-mirror", "镜潮回廊", CardType.SITE, Rarity.SR, 2, 0, 2, 1, "MIRROR", "本场守力不低于进攻战力时，额外获得1分。", "潮汐把每个意图原样归还。", "反制", "控场"),
            card("site-nexus", "灵脉交汇点", CardType.SITE, Rarity.C, 1, 0, 1, 2, "NEXUS", "稳定提供2点基础积分。", "所有道路都在此刻汇成棋局。", "积分", "核心"),

            card("unit-scout", "踏界斥候", CardType.UNIT, Rarity.C, 1, 2, 1, 0, "SCOUT", "基础攻击距离2；部署后若本场无其他单位，战力+1。", "先一步抵达，就先一步命名边界。", "灵活", "先锋"),
            card("unit-warden", "玄岩守御者", CardType.UNIT, Rarity.C, 1, 1, 3, 0, "WARDEN", "坚守：提供高额守力。", "他从不追逐战局，战局会撞上他。", "防守", "壁垒"),
            card("unit-channeler", "灵脉引导者", CardType.UNIT, Rarity.R, 2, 3, 2, 0, "CHANNEL", "部署到核心场地时抽1张牌。", "她听见地脉在棋盘下低语。", "抽牌", "核心"),
            card("unit-duelist", "逐风决斗家", CardType.UNIT, Rarity.R, 2, 4, 1, 0, "DUELIST", "争夺仅有场地守力、没有守军的目标时战力+1。", "风替她拔剑，也替她收剑。", "进攻", "突破"),
            card("unit-oracle", "镜潮先知", CardType.UNIT, Rarity.SR, 3, 4, 3, 0, "ORACLE", "部署后查看战局并抽1张牌。", "她从倒影中读到尚未发生的归属。", "抽牌", "全能"),
            card("unit-architect", "场域构筑师", CardType.UNIT, Rarity.SR, 2, 2, 4, 0, "ARCHITECT", "所在场地基础守力+1。", "规则不是墙，是可被重新绘制的线。", "防守", "增幅"),
            card("unit-raider", "裂隙奔袭者", CardType.UNIT, Rarity.R, 2, 5, 0, 0, "RAIDER", "高战力，但不提供守力。", "他只在胜负尚未书写时出现。", "快攻", "高攻"),
            card("unit-sentinel", "星幕哨卫", CardType.UNIT, Rarity.C, 1, 2, 2, 0, "SENTINEL", "驻场时稳定提供攻守。", "星光很远，警戒很近。", "均衡", "基础"),

            card("spell-seal", "静界封印", CardType.SPELL, Rarity.R, 1, 0, 0, 0, "SEAL", "封印敌方1个单位至当前回合结束。", "安静，有时比雷鸣更接近胜利。", "封印", "反制"),
            card("spell-surge", "灵潮骤升", CardType.SPELL, Rarity.C, 1, 0, 0, 0, "SURGE", "己方1个单位本局战力+2。", "潮头只停留一瞬，决策必须更快。", "增幅", "进攻"),
            card("spell-insight", "星图洞见", CardType.SPELL, Rarity.C, 1, 0, 0, 0, "DRAW", "立即抽2张牌。", "看清两步之后，眼前便不再拥挤。", "抽牌", "补给"),
            card("spell-echo", "归属回响", CardType.SPELL, Rarity.SR, 2, 0, 0, 0, "ECHO", "己方积分最低时立即获得3分。", "失去的声音，会从另一侧归来。", "翻盘", "积分"),
            card("spell-reinforce", "临阵固守", CardType.SPELL, Rarity.R, 1, 0, 0, 0, "REINFORCE", "己方1个单位本局守力+2。", "真正的防线，总在最后一刻成形。", "防守", "增幅"),
            card("spell-waygate", "折界通路", CardType.SPELL, Rarity.R, 1, 0, 0, 0, "RANGE", "己方1个单位本回合攻击距离+1。", "路并未变短，只是边界被折叠。", "射程", "机动"),

            card("secret-dominion", "万域归一", CardType.SECRET, Rarity.SSR, 3, 0, 0, 0, "DOMINION", "控制至少5个场地时发动：立即获得8分。每局限1次。", "九域并非疆土，而是同一个答案。", "终结", "秘策"),
            card("secret-reset", "天平重启", CardType.SECRET, Rarity.SSR, 3, 0, 0, 0, "BALANCE", "积分落后且控制场地更少时发动：将积分追平对手。", "当棋局倾斜，天平便开始醒来。", "翻盘", "秘策")
        );
        cards = new ArrayList<>(cards);
        cards.add(card("site-beacon", "\u661f\u706f\u5854", CardType.SITE, Rarity.R, 2, 0, 1, 1, "BEACON", "\u4ece\u6b64\u573a\u5730\u53d1\u8d77\u7684\u4e89\u593a\u653b\u51fb\u8ddd\u79bb+1\u3002", "\u706f\u706b\u662f\u7ed9\u8ff7\u8def\u8005\u7684\u653b\u52bf\u3002", "\u673a\u52a8", "\u8fdb\u653b"));
        cards.add(card("site-sanctum", "\u9759\u8bed\u5723\u6240", CardType.SITE, Rarity.SR, 2, 0, 2, 1, "SANCTUARY", "\u672c\u573a\u5730\u6bcf\u6709\u4e00\u4e2a\u5355\u4f4d\uff0c\u5b88\u529b+1\u3002", "\u6c89\u9ed8\u4e5f\u80fd\u7b51\u6210\u76fe\u724c\u3002", "\u9632\u5b88", "\u9a7b\u573a"));
        cards.add(card("site-harvest", "\u661f\u7eb9\u679c\u56ed", CardType.SITE, Rarity.R, 2, 0, 1, 1, "HARVEST", "\u63a7\u5236\u4e09\u4e2a\u53ca\u4ee5\u4e0a\u573a\u5730\u65f6\u989d\u5916\u83b7\u5f97\u79ef\u5206\u3002", "\u6bcf\u4e00\u679a\u679c\u5b9e\u90fd\u8bb0\u5f55\u7740\u80dc\u5229\u3002", "\u79ef\u5206", "\u8fd0\u8425"));
        cards.add(card("unit-vanguard", "\u661f\u706b\u5148\u950b", CardType.UNIT, Rarity.R, 2, 3, 1, 0, "VANGUARD", "\u653b\u51fb\u4e2d\u5fc3\u573a\u5730\u65f6\u6218\u529b+2\u3002", "\u5148\u950b\u4e0d\u95ee\u9053\u8def\u901a\u5411\u4f55\u65b9\u3002", "\u7a81\u7834", "\u6838\u5fc3"));
        cards.add(card("unit-ranger", "\u591c\u822a\u6e38\u4fa0", CardType.UNIT, Rarity.R, 2, 3, 2, 0, "RANGER", "\u57fa\u7840\u653b\u51fb\u8ddd\u79bb3\u3002", "\u6708\u5149\u4e0b\u6ca1\u6709\u65e0\u6cd5\u5230\u8fbe\u7684\u5730\u65b9\u3002", "\u5c04\u7a0b", "\u673a\u52a8"));
        cards.add(card("unit-weaver", "\u7eaf\u754c\u7ec7\u8005", CardType.UNIT, Rarity.SR, 3, 2, 3, 0, "WEAVER", "\u9a7b\u573a\u65f6\u6240\u5728\u573a\u5730\u989d\u5916\u63d0\u4f9b1\u70b9\u5b88\u529b\u3002", "\u5979\u628a\u7834\u788e\u7684\u8fb9\u754c\u7f1d\u56de\u4e00\u8d77\u3002", "\u9632\u5b88", "\u589e\u5f3a"));
        cards.add(card("spell-muster", "\u56de\u58f0\u96c6\u7ed3", CardType.SPELL, Rarity.R, 1, 0, 0, 0, "REFRESH", "\u6062\u590d\u4e00\u4e2a\u5df1\u65b9\u5355\u4f4d\u7684\u884c\u52a8\u72b6\u6001\u3002", "\u540c\u4e00\u7247\u661f\u7a7a\u4e0b\u518d\u8d77\u4e00\u6b21\u3002", "\u8d44\u6e90", "\u8fde\u7eed"));
        cards.add(card("spell-expedition", "\u8fb9\u754c\u52d8\u63a2", CardType.SPELL, Rarity.C, 1, 0, 0, 0, "EXPEDITION", "\u62bd1\u5f20\u724c\u5e76\u83b7\u5f971\u70b9\u7075\u529b\u3002", "\u672a\u77e5\u7684\u8fb9\u754c\u6c38\u8fdc\u503c\u5f97\u8d70\u4e00\u8d9f\u3002", "\u8865\u7ed9", "\u8282\u594f"));
        cards.add(card("secret-overgrowth", "\u6781\u5883\u751f\u957f", CardType.SECRET, Rarity.SSR, 3, 0, 0, 0, "OVERGROWTH", "\u63a7\u5236\u4e09\u4e2a\u573a\u5730\u65f6\uff0c\u6240\u6709\u5df1\u65b9\u573a\u5730\u672c\u56de\u5408\u989d\u5916\u8ba1\u5206\u3002", "\u751f\u957f\u4e0d\u662f\u9000\u8ba9\uff0c\u800c\u662f\u91cd\u5199\u8fb9\u754c\u3002", "\u7ec8\u7ed3", "\u79d8\u7b56"));
        byId = new LinkedHashMap<>();
        cards.forEach(card -> byId.put(card.id(), card));
        loadOverrides();
    }

    private CardDefinition card(String id, String name, CardType type, Rarity rarity, int cost, int power,
                                int guard, int points, String code, String effect, String flavor, String... tags) {
        return new CardDefinition(id, name, type, rarity, cost, power, guard, points, code, effect, flavor, List.of(tags));
    }

    public synchronized List<CardDefinition> all() { return List.copyOf(byId.values()); }
    public synchronized CardDefinition upsert(CardDefinition card) {
        if (card == null || card.id() == null || card.id().isBlank()) throw new IllegalArgumentException("卡牌ID不能为空");
        if (card.name() == null || card.name().isBlank()) throw new IllegalArgumentException("卡牌名不能为空");
        if (card.type() == null || card.rarity() == null) throw new IllegalArgumentException("卡牌类型和稀有度不能为空");
        if (card.cost() < 0 || card.cost() > 9 || card.power() < 0 || card.guard() < 0 || card.points() < 0) throw new IllegalArgumentException("费用、战力、守力和积分必须在合法范围内");
        if (card.effectCode() == null || card.effectCode().isBlank()) throw new IllegalArgumentException("效果代码不能为空");
        Set<String> allowed = Set.of("GROWTH", "FORTRESS", "ARCHIVE", "FORGE", "MIRROR", "NEXUS", "SCOUT", "WARDEN", "CHANNEL", "DUELIST", "ORACLE", "ARCHITECT", "RAIDER", "SENTINEL", "DRAW", "ECHO", "SEAL", "SURGE", "REINFORCE", "RANGE", "DOMINION", "BALANCE", "BEACON", "SANCTUARY", "HARVEST", "VANGUARD", "RANGER", "WEAVER", "REFRESH", "EXPEDITION", "OVERGROWTH", "CUSTOM");
        if (!allowed.contains(card.effectCode().trim().toUpperCase(Locale.ROOT))) throw new IllegalArgumentException("不支持的效果代码");
        byId.put(card.id().trim(), card); saveOverrides(); return card;
    }
    public synchronized void delete(String id) {
        if (byId.remove(id) == null) throw new IllegalArgumentException("\u5361\u724c\u4e0d\u5b58\u5728");
        saveOverrides();
    }
    public CardDefinition require(String id) {
        CardDefinition card = byId.get(id);
        if (card == null) throw new IllegalArgumentException("未知卡牌：" + id);
        return card;
    }

    public List<String> starterDeck() { return deckForArchetype("balanced"); }

    public List<String> deckForArchetype(String archetype) {
        String key = archetype == null ? "balanced" : archetype.toLowerCase(Locale.ROOT);
        List<String> deck = switch (key) {
            case "bastion", "壁垒龟" -> archetypeDeckBastion();
            case "ranger", "游猎射" -> archetypeDeckRanger();
            case "forge", "锻场突" -> archetypeDeckForge();
            case "draw", "运营抽" -> archetypeDeckDraw();
            case "dominion", "绝杀快控" -> archetypeDeckDominion();
            case "tutorial", "puzzle" -> archetypeDeckBastion();
            default -> archetypeDeckBalanced();
        };
        while (deck.size() < 40) {
            for (String id : byId.keySet()) {
                if (deck.size() >= 40) break;
                deck.add(id);
            }
        }
        return new ArrayList<>(deck.subList(0, 40));
    }

    public List<Map<String, Object>> archetypes() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(archetypeInfo("balanced", "均衡初阵", "综合上手", "标准40张，适合熟悉规则"));
        list.add(archetypeInfo("bastion", "壁垒龟缩", "拖回合拼分", "玄岩壁垒、高守单位、固守术式"));
        list.add(archetypeInfo("ranger", "游猎射程", "跨距偷核心", "斥候、夜航游侠、折界通路、星灯塔"));
        list.add(archetypeInfo("forge", "锻场突击", "压倒性滚雪球", "赤焰锻场、决斗家、奔袭者、灵潮"));
        list.add(archetypeInfo("draw", "运营过牌", "找关键键/秘策", "典藏馆、先知、星图洞见"));
        list.add(archetypeInfo("dominion", "绝杀快控", "中期过半连结算", "低费场、铺量、万域归一"));
        return list;
    }

    public Map<String, Object> deckRules() {
        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("deckSize", 40);
        rules.put("maxCopies", 2);
        rules.put("maxSsr", 1);
        rules.put("minSites", 10);
        rules.put("minUnits", 12);
        rules.put("description", "卡组须满40张；同名最多2张；SSR最多1张；场地至少10张、单位至少12张。");
        return rules;
    }

    public Map<String, List<String>> cardArchetypeTags() {
        Map<String, List<String>> tags = new LinkedHashMap<>();
        tags.put("site-bastion", List.of("bastion"));
        tags.put("unit-warden", List.of("bastion"));
        tags.put("unit-architect", List.of("bastion"));
        tags.put("spell-reinforce", List.of("bastion"));
        tags.put("site-sanctum", List.of("bastion"));
        tags.put("unit-scout", List.of("ranger"));
        tags.put("unit-ranger", List.of("ranger"));
        tags.put("spell-waygate", List.of("ranger"));
        tags.put("site-beacon", List.of("ranger", "forge"));
        tags.put("site-forge", List.of("forge"));
        tags.put("unit-duelist", List.of("forge"));
        tags.put("unit-raider", List.of("forge"));
        tags.put("spell-surge", List.of("forge"));
        tags.put("site-archive", List.of("draw"));
        tags.put("unit-oracle", List.of("draw"));
        tags.put("spell-insight", List.of("draw"));
        tags.put("unit-channeler", List.of("draw", "dominion"));
        tags.put("site-verdant", List.of("dominion", "balanced"));
        tags.put("unit-sentinel", List.of("dominion", "balanced"));
        tags.put("secret-dominion", List.of("dominion"));
        tags.put("secret-overgrowth", List.of("dominion"));
        return tags;
    }

    private Map<String, Object> archetypeInfo(String id, String name, String path, String cards) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id); m.put("name", name); m.put("winPath", path); m.put("focus", cards);
        m.put("cards", deckForArchetype(id));
        return m;
    }

    private List<String> archetypeDeckBalanced() {
        List<String> deck = new ArrayList<>();
        safeAdd(deck, "site-verdant", 2); safeAdd(deck, "site-bastion", 2); safeAdd(deck, "site-archive", 2);
        safeAdd(deck, "site-forge", 1); safeAdd(deck, "site-mirror", 1); safeAdd(deck, "site-nexus", 1);
        safeAdd(deck, "site-beacon", 1); safeAdd(deck, "site-sanctum", 1); safeAdd(deck, "site-harvest", 1);
        safeAdd(deck, "unit-scout", 2); safeAdd(deck, "unit-warden", 2); safeAdd(deck, "unit-channeler", 2);
        safeAdd(deck, "unit-duelist", 2); safeAdd(deck, "unit-oracle", 1); safeAdd(deck, "unit-architect", 1);
        safeAdd(deck, "unit-raider", 1); safeAdd(deck, "unit-sentinel", 1); safeAdd(deck, "unit-vanguard", 1);
        safeAdd(deck, "unit-ranger", 1); safeAdd(deck, "unit-weaver", 1);
        safeAdd(deck, "spell-seal", 2); safeAdd(deck, "spell-surge", 2); safeAdd(deck, "spell-insight", 2);
        safeAdd(deck, "spell-waygate", 1); safeAdd(deck, "spell-muster", 2); safeAdd(deck, "spell-expedition", 2);
        safeAdd(deck, "secret-dominion", 1); safeAdd(deck, "secret-overgrowth", 1);
        return deck;
    }

    private List<String> archetypeDeckBastion() {
        List<String> deck = new ArrayList<>();
        safeAdd(deck, "site-bastion", 2); safeAdd(deck, "site-sanctum", 2); safeAdd(deck, "site-mirror", 2);
        safeAdd(deck, "site-verdant", 2); safeAdd(deck, "site-nexus", 2); safeAdd(deck, "site-harvest", 2);
        safeAdd(deck, "unit-warden", 2); safeAdd(deck, "unit-architect", 2); safeAdd(deck, "unit-weaver", 2);
        safeAdd(deck, "unit-sentinel", 2); safeAdd(deck, "unit-oracle", 1); safeAdd(deck, "unit-channeler", 1);
        safeAdd(deck, "spell-reinforce", 2); safeAdd(deck, "spell-seal", 2); safeAdd(deck, "spell-muster", 2);
        safeAdd(deck, "spell-insight", 2); safeAdd(deck, "spell-expedition", 2);
        safeAdd(deck, "secret-overgrowth", 1); safeAdd(deck, "site-archive", 2); safeAdd(deck, "unit-scout", 1);
        return deck;
    }

    private List<String> archetypeDeckRanger() {
        List<String> deck = new ArrayList<>();
        safeAdd(deck, "site-beacon", 2); safeAdd(deck, "site-archive", 2); safeAdd(deck, "site-verdant", 2);
        safeAdd(deck, "site-forge", 2); safeAdd(deck, "site-nexus", 1); safeAdd(deck, "site-mirror", 1);
        safeAdd(deck, "unit-scout", 2); safeAdd(deck, "unit-ranger", 2); safeAdd(deck, "unit-vanguard", 2);
        safeAdd(deck, "unit-duelist", 2); safeAdd(deck, "unit-raider", 1); safeAdd(deck, "unit-sentinel", 1);
        safeAdd(deck, "spell-waygate", 2); safeAdd(deck, "spell-surge", 2); safeAdd(deck, "spell-insight", 2);
        safeAdd(deck, "spell-expedition", 2); safeAdd(deck, "spell-seal", 1); safeAdd(deck, "spell-muster", 2);
        safeAdd(deck, "secret-dominion", 1); safeAdd(deck, "site-bastion", 1); safeAdd(deck, "unit-channeler", 1);
        return deck;
    }

    private List<String> archetypeDeckForge() {
        List<String> deck = new ArrayList<>();
        safeAdd(deck, "site-forge", 2); safeAdd(deck, "site-beacon", 2); safeAdd(deck, "site-verdant", 2);
        safeAdd(deck, "site-archive", 2); safeAdd(deck, "site-nexus", 1); safeAdd(deck, "site-harvest", 1);
        safeAdd(deck, "unit-duelist", 2); safeAdd(deck, "unit-raider", 2); safeAdd(deck, "unit-vanguard", 2);
        safeAdd(deck, "unit-scout", 2); safeAdd(deck, "unit-sentinel", 1); safeAdd(deck, "unit-channeler", 1);
        safeAdd(deck, "spell-surge", 2); safeAdd(deck, "spell-waygate", 2); safeAdd(deck, "spell-muster", 2);
        safeAdd(deck, "spell-insight", 2); safeAdd(deck, "spell-expedition", 2); safeAdd(deck, "spell-seal", 1);
        safeAdd(deck, "secret-dominion", 1); safeAdd(deck, "site-mirror", 1); safeAdd(deck, "unit-oracle", 1);
        return deck;
    }

    private List<String> archetypeDeckDraw() {
        List<String> deck = new ArrayList<>();
        safeAdd(deck, "site-archive", 2); safeAdd(deck, "site-verdant", 2); safeAdd(deck, "site-nexus", 2);
        safeAdd(deck, "site-mirror", 2); safeAdd(deck, "site-harvest", 2); safeAdd(deck, "site-bastion", 1);
        safeAdd(deck, "unit-oracle", 2); safeAdd(deck, "unit-channeler", 2); safeAdd(deck, "unit-sentinel", 2);
        safeAdd(deck, "unit-architect", 1); safeAdd(deck, "unit-scout", 1); safeAdd(deck, "unit-warden", 1);
        safeAdd(deck, "spell-insight", 2); safeAdd(deck, "spell-expedition", 2); safeAdd(deck, "spell-muster", 2);
        safeAdd(deck, "spell-seal", 2); safeAdd(deck, "spell-reinforce", 2); safeAdd(deck, "spell-surge", 1);
        safeAdd(deck, "secret-overgrowth", 1); safeAdd(deck, "site-sanctum", 1); safeAdd(deck, "unit-weaver", 1);
        safeAdd(deck, "spell-echo", 1);
        return deck;
    }

    private List<String> archetypeDeckDominion() {
        List<String> deck = new ArrayList<>();
        safeAdd(deck, "site-verdant", 2); safeAdd(deck, "site-archive", 2); safeAdd(deck, "site-nexus", 2);
        safeAdd(deck, "site-beacon", 2); safeAdd(deck, "site-forge", 1); safeAdd(deck, "site-harvest", 2);
        safeAdd(deck, "unit-sentinel", 2); safeAdd(deck, "unit-scout", 2); safeAdd(deck, "unit-channeler", 2);
        safeAdd(deck, "unit-duelist", 1); safeAdd(deck, "unit-vanguard", 1); safeAdd(deck, "unit-ranger", 1);
        safeAdd(deck, "spell-insight", 2); safeAdd(deck, "spell-expedition", 2); safeAdd(deck, "spell-waygate", 2);
        safeAdd(deck, "spell-surge", 2); safeAdd(deck, "spell-muster", 2); safeAdd(deck, "spell-seal", 1);
        safeAdd(deck, "secret-dominion", 1); safeAdd(deck, "secret-overgrowth", 1); safeAdd(deck, "site-mirror", 1);
        return deck;
    }

    private void loadOverrides() {
        try {
            if (!Files.exists(storage)) return;
            List<CardDefinition> saved = mapper.readValue(storage.toFile(), new TypeReference<>() {});
            byId.clear(); saved.forEach(card -> byId.put(card.id(), card));
        } catch (Exception ignored) { }
    }
    private void saveOverrides() {
        try {
            Files.createDirectories(storage.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(storage.toFile(), new ArrayList<>(byId.values()));
        } catch (Exception e) { throw new IllegalStateException("Failed to save cards", e); }
    }
    private void safeAdd(List<String> list, String id, int count) { if (byId.containsKey(id)) add(list, id, count); }

    private void add(List<String> list, String id, int count) { for (int i = 0; i < count; i++) list.add(id); }
}



package com.fieldrealm.game.domain;

public class UnitInstance {
    private String instanceId;
    private String cardId;
    private String name;
    private String ownerId;
    private int power;
    private int guard;
    private boolean sealed;
    private boolean exhausted;
    private String keyword;
    private int baseRange = 1;
    private int attackRange = 1;
    /** 动摇：被夺场后守力-1且不可主动攻，1 回合后清除 */
    private boolean shaken;
    /** 同场连续驻守回合数，≥2 时提供扎根守力+1 */
    private int rootedTurns;
    /** 部署当回合行军：不可发起争夺 */
    private boolean marching;
    private int powerBuff;
    private int guardBuff;
    private int rangeBuff;

    public UnitInstance() { }

    public UnitInstance(String instanceId, CardDefinition card, String ownerId) {
        this.instanceId = instanceId;
        this.cardId = card.id();
        this.name = card.name();
        this.ownerId = ownerId;
        this.power = card.power();
        this.guard = card.guard();
        this.keyword = card.effectCode();
        this.baseRange = "RANGER".equals(card.effectCode()) ? 3 : ("SCOUT".equals(card.effectCode()) ? 2 : 1);
        this.attackRange = this.baseRange;
        this.marching = true;
        this.rootedTurns = 0;
    }

    /** 战力含永久增幅；powerBuff 仅用于 UI 图标展示 */
    public int effectivePower() { return power; }
    /** 守力含扎根/动摇；guardBuff 仅用于 UI 图标展示 */
    public int effectiveGuard() {
        int value = guard + (rootedTurns >= 2 ? 1 : 0) - (shaken ? 1 : 0);
        return Math.max(0, value);
    }

    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public int getPower() { return power; }
    public void setPower(int power) { this.power = power; }
    public int getGuard() { return guard; }
    public void setGuard(int guard) { this.guard = guard; }
    public boolean isSealed() { return sealed; }
    public void setSealed(boolean sealed) { this.sealed = sealed; }
    public boolean isExhausted() { return exhausted; }
    public void setExhausted(boolean exhausted) { this.exhausted = exhausted; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public int getBaseRange() { return baseRange; }
    public void setBaseRange(int baseRange) { this.baseRange = baseRange; }
    public int getAttackRange() { return attackRange; }
    public void setAttackRange(int attackRange) { this.attackRange = attackRange; }
    public boolean isShaken() { return shaken; }
    public void setShaken(boolean shaken) { this.shaken = shaken; }
    public int getRootedTurns() { return rootedTurns; }
    public void setRootedTurns(int rootedTurns) { this.rootedTurns = rootedTurns; }
    public boolean isMarching() { return marching; }
    public void setMarching(boolean marching) { this.marching = marching; }
    public int getPowerBuff() { return powerBuff; }
    public void setPowerBuff(int powerBuff) { this.powerBuff = powerBuff; }
    public int getGuardBuff() { return guardBuff; }
    public void setGuardBuff(int guardBuff) { this.guardBuff = guardBuff; }
    public int getRangeBuff() { return rangeBuff; }
    public void setRangeBuff(int rangeBuff) { this.rangeBuff = rangeBuff; }
}

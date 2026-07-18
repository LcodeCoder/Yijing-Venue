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
}


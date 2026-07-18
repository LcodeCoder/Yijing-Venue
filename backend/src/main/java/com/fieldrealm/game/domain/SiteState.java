package com.fieldrealm.game.domain;

import java.util.ArrayList;
import java.util.List;

public class SiteState {
    private int index;
    private int row;
    private int column;
    private String position;
    private boolean core;
    private String ownerId;
    private String cardId;
    private String name;
    private int baseGuard;
    private int basePoints;
    private String effectCode;
    private String effect;
    private String pendingAttackerId;
    private int fortressHits;
    private List<UnitInstance> units = new ArrayList<>();

    public SiteState() { }
    public SiteState(int index, String position, boolean core) { this(index, position, core, 0, 0); }
    public SiteState(int index, String position, boolean core, int row, int column) {
        this.index = index; this.position = position; this.core = core; this.row = row; this.column = column;
        this.name = core ? "无主核心" : "未定之域";
        this.effect = "等待场地卡唤醒";
    }

    public int totalGuard() {
        int unitGuard = units.stream().filter(u -> !u.isSealed()).mapToInt(UnitInstance::getGuard).sum();
        int weaver = (int) units.stream().filter(u -> !u.isSealed() && "WEAVER".equals(u.getKeyword())).count();
        int sanctuary = "SANCTUARY".equals(effectCode) ? (int) units.stream().filter(u -> ownerId != null && ownerId.equals(u.getOwnerId())).count() : 0;
        return baseGuard + unitGuard + weaver + sanctuary;
    }

    public int scoringValue() { return basePoints * (core ? 2 : 1); }

    public int getIndex() { return index; }
    public void setIndex(int index) { this.index = index; }
    public int getRow() { return row; }
    public void setRow(int row) { this.row = row; }
    public int getColumn() { return column; }
    public void setColumn(int column) { this.column = column; }
    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }
    public boolean isCore() { return core; }
    public void setCore(boolean core) { this.core = core; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getBaseGuard() { return baseGuard; }
    public void setBaseGuard(int baseGuard) { this.baseGuard = baseGuard; }
    public int getBasePoints() { return basePoints; }
    public void setBasePoints(int basePoints) { this.basePoints = basePoints; }
    public String getEffectCode() { return effectCode; }
    public void setEffectCode(String effectCode) { this.effectCode = effectCode; }
    public String getEffect() { return effect; }
    public void setEffect(String effect) { this.effect = effect; }
    public String getPendingAttackerId() { return pendingAttackerId; }
    public void setPendingAttackerId(String pendingAttackerId) { this.pendingAttackerId = pendingAttackerId; }
    public int getFortressHits() { return fortressHits; }
    public void setFortressHits(int fortressHits) { this.fortressHits = fortressHits; }
    public List<UnitInstance> getUnits() { return units; }
    public void setUnits(List<UnitInstance> units) { this.units = units; }
}

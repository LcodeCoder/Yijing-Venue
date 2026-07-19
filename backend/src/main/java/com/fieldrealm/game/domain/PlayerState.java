package com.fieldrealm.game.domain;

import java.util.ArrayList;
import java.util.List;

public class PlayerState {
    private String id;
    private String accountId;
    private String name;
    private String title;
    private String avatar;
    private int energy;
    private int score;
    private int stableTicks;
    private int scoringBonusThisTurn;
    private boolean secretUsed;
    /** 气势 0～3，满层可免费发动 1 费术式 */
    private int momentum;
    /** 本回合是否已使用筛牌（弃1抽1） */
    private boolean cycleUsedThisTurn;
    private List<String> deck = new ArrayList<>();
    private List<String> hand = new ArrayList<>();
    private List<String> discard = new ArrayList<>();

    public PlayerState() { }
    public PlayerState(String id, String name, String title, String avatar) {
        this.id = id; this.name = name; this.title = title; this.avatar = avatar;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public int getEnergy() { return energy; }
    public void setEnergy(int energy) { this.energy = energy; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public int getStableTicks() { return stableTicks; }
    public void setStableTicks(int stableTicks) { this.stableTicks = stableTicks; }
    public int getScoringBonusThisTurn() { return scoringBonusThisTurn; }
    public void setScoringBonusThisTurn(int scoringBonusThisTurn) { this.scoringBonusThisTurn = scoringBonusThisTurn; }
    public boolean isSecretUsed() { return secretUsed; }
    public void setSecretUsed(boolean secretUsed) { this.secretUsed = secretUsed; }
    public int getMomentum() { return momentum; }
    public void setMomentum(int momentum) { this.momentum = Math.max(0, Math.min(3, momentum)); }
    public boolean isCycleUsedThisTurn() { return cycleUsedThisTurn; }
    public void setCycleUsedThisTurn(boolean cycleUsedThisTurn) { this.cycleUsedThisTurn = cycleUsedThisTurn; }
    public List<String> getDeck() { return deck; }
    public void setDeck(List<String> deck) { this.deck = deck; }
    public List<String> getHand() { return hand; }
    public void setHand(List<String> hand) { this.hand = hand; }
    public List<String> getDiscard() { return discard; }
    public void setDiscard(List<String> discard) { this.discard = discard; }
}

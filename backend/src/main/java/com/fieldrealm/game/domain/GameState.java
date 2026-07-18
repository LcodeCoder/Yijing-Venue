package com.fieldrealm.game.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class GameState {
    private String id;
    private String mode;
    private int boardSize = 3;
    private boolean ranked;
    private boolean waitingForOpponent;
    private boolean rankedResultRecorded;
    private int round = 1;
    private int turnNumber = 1;
    private int activePlayerIndex;
    private int contestStarterIndex = -1;
    private int playerRoll;
    private int opponentRoll;
    private boolean initialContestResolved;
    private GamePhase phase = GamePhase.DEPLOY;
    private List<PlayerState> players = new ArrayList<>();
    private List<SiteState> sites = new ArrayList<>();
    private String winnerId;
    private String victoryType;
    private String statusText;
    private List<String> log = new ArrayList<>();
    private Instant updatedAt = Instant.now();
    private Instant phaseEndsAt;
    private int phaseDurationSeconds = 15;

    public PlayerState activePlayer() { return players.get(activePlayerIndex); }
    public PlayerState opponent() { return players.get(1 - activePlayerIndex); }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public int getBoardSize() { return boardSize; }
    public void setBoardSize(int boardSize) { this.boardSize = boardSize; }
    public boolean isRanked() { return ranked; }
    public void setRanked(boolean ranked) { this.ranked = ranked; }
    public boolean isWaitingForOpponent() { return waitingForOpponent; }
    public void setWaitingForOpponent(boolean waitingForOpponent) { this.waitingForOpponent = waitingForOpponent; }
    public boolean isRankedResultRecorded() { return rankedResultRecorded; }
    public void setRankedResultRecorded(boolean rankedResultRecorded) { this.rankedResultRecorded = rankedResultRecorded; }
    public int getRound() { return round; }
    public void setRound(int round) { this.round = round; }
    public int getTurnNumber() { return turnNumber; }
    public void setTurnNumber(int turnNumber) { this.turnNumber = turnNumber; }
    public int getActivePlayerIndex() { return activePlayerIndex; }
    public void setActivePlayerIndex(int activePlayerIndex) { this.activePlayerIndex = activePlayerIndex; }
    public int getContestStarterIndex() { return contestStarterIndex; }
    public void setContestStarterIndex(int contestStarterIndex) { this.contestStarterIndex = contestStarterIndex; }
    public int getPlayerRoll() { return playerRoll; }
    public void setPlayerRoll(int playerRoll) { this.playerRoll = playerRoll; }
    public int getOpponentRoll() { return opponentRoll; }
    public void setOpponentRoll(int opponentRoll) { this.opponentRoll = opponentRoll; }
    public boolean isInitialContestResolved() { return initialContestResolved; }
    public void setInitialContestResolved(boolean initialContestResolved) { this.initialContestResolved = initialContestResolved; }
    public GamePhase getPhase() { return phase; }
    public void setPhase(GamePhase phase) { this.phase = phase; }
    public List<PlayerState> getPlayers() { return players; }
    public void setPlayers(List<PlayerState> players) { this.players = players; }
    public List<SiteState> getSites() { return sites; }
    public void setSites(List<SiteState> sites) { this.sites = sites; }
    public String getWinnerId() { return winnerId; }
    public void setWinnerId(String winnerId) { this.winnerId = winnerId; }
    public String getVictoryType() { return victoryType; }
    public void setVictoryType(String victoryType) { this.victoryType = victoryType; }
    public String getStatusText() { return statusText; }
    public void setStatusText(String statusText) { this.statusText = statusText; }
    public List<String> getLog() { return log; }
    public void setLog(List<String> log) { this.log = log; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getPhaseEndsAt() { return phaseEndsAt; }
    public void setPhaseEndsAt(Instant phaseEndsAt) { this.phaseEndsAt = phaseEndsAt; }
    public int getPhaseDurationSeconds() { return phaseDurationSeconds; }
    public void setPhaseDurationSeconds(int phaseDurationSeconds) { this.phaseDurationSeconds = phaseDurationSeconds; }
}

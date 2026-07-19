package com.fieldrealm.game.dto;

public record CreateMatchRequest(
        String mode,
        String playerName,
        Integer boardSize,
        Boolean ranked,
        String aiDifficulty,
        String scenario,
        String deckArchetype,
        String puzzleId
) {
    public CreateMatchRequest(String mode, String playerName, Integer boardSize, Boolean ranked) {
        this(mode, playerName, boardSize, ranked, null, null, null, null);
    }
}

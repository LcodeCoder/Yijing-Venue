package com.fieldrealm.game.dto;

public record CreateMatchRequest(String mode, String playerName, Integer boardSize, Boolean ranked) { }

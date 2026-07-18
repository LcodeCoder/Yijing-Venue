package com.fieldrealm.game.dto;

import jakarta.validation.constraints.NotBlank;

public record PlayerActionRequest(@NotBlank String playerId) { }

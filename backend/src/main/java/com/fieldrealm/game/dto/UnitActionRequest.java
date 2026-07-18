package com.fieldrealm.game.dto;

import jakarta.validation.constraints.NotBlank;

public record UnitActionRequest(
        @NotBlank String playerId,
        @NotBlank String unitId
) { }

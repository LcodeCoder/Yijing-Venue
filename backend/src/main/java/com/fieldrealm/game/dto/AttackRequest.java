package com.fieldrealm.game.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AttackRequest(
        @NotBlank String playerId,
        @NotBlank String attackerUnitId,
        @NotNull Integer targetSiteIndex
) { }

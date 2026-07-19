package com.fieldrealm.game.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MoveUnitRequest(
        @NotBlank String playerId,
        @NotBlank String unitId,
        @NotNull Integer targetSiteIndex
) { }

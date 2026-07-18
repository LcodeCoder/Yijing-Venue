package com.fieldrealm.game.dto;

import jakarta.validation.constraints.NotBlank;

public record PlayCardRequest(
        @NotBlank String playerId,
        @NotBlank String cardId,
        Integer targetSiteIndex,
        String targetUnitId
) { }

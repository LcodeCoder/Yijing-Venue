package com.fieldrealm.game.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @NotBlank @Size(min = 2, max = 20) String displayName,
        @Size(max = 8) String avatar,
        @Size(max = 24) String title
) { }

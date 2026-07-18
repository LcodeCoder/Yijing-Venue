package com.fieldrealm.game.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MailSettingsRequest(
        boolean enabled,
        @NotBlank @Size(max = 120) String host,
        @Min(1) @Max(65535) int port,
        boolean ssl,
        @Size(max = 160) String username,
        @Size(max = 200) String password,
        @NotBlank @Size(max = 40) String fromName
) { }

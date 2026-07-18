package com.fieldrealm.game.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 24) String username,
        @NotBlank @Size(min = 6, max = 72) String password,
        @NotBlank @Size(min = 2, max = 20) String displayName,
        @Email String email,
        @Pattern(regexp = "^$|\\d{6}") String code
) { }

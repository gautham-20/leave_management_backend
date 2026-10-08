package com.leavems.dto;

import com.leavems.entity.Role;
import jakarta.validation.constraints.NotNull;

/** Login request. The role is included so a mismatch is reported as such. */
public record LoginRequest(
        @jakarta.validation.constraints.Email @NotNull String email,
        @NotNull String password,
        @NotNull Role role
) {
}
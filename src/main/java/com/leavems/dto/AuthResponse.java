package com.leavems.dto;

public record AuthResponse(
        String token,
        UserResponse user
) {
}
package com.leavems.dto;

import java.time.Instant;

/**
 * The user shape returned to the client. Deliberately omits the password hash
 * and id-adjacent internals that have no business in a browser payload.
 */
public record UserResponse(
        Long id,
        String name,
        String email,
        String role,
        Instant createdAt
) {
}
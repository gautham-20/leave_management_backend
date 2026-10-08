package com.leavems.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Create/update payload for a policy.
 *
 * <p>{@code active} and {@code sortOrder} are {@link Boolean}/{@code Integer}
 * rather than primitives so that an update can omit them and keep the stored
 * value instead of silently resetting it to a default.
 */
public record LeavePolicyBody(
        @NotBlank
        @Size(max = 120)
        String title,

        @NotBlank
        @Size(max = 1000)
        String description,

        Boolean active,

        @Min(0)
        @Max(10_000)
        Integer sortOrder
) {
    /** Effective display order when the client does not supply one. */
    public int sortOrderOr(int fallback) {
        return sortOrder == null ? fallback : sortOrder;
    }

    /** Effective active flag when the client does not supply one. */
    public boolean activeOr(boolean fallback) {
        return active == null ? fallback : active;
    }
}

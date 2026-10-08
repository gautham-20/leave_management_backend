package com.leavems.dto;

import com.leavems.entity.LeavePolicy;

import java.time.Instant;

/** Wire shape for a policy. Field names match the TypeScript type in PolicyContext. */
public record LeavePolicyResponse(
        Long id,
        String title,
        String description,
        boolean active,
        int sortOrder,
        Instant updatedAt
) {
    public static LeavePolicyResponse from(LeavePolicy policy) {
        return new LeavePolicyResponse(
                policy.getId(),
                policy.getTitle(),
                policy.getDescription(),
                policy.isActive(),
                policy.getSortOrder(),
                policy.getUpdatedAt()
        );
    }
}

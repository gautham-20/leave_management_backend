package com.leavems.dto;

import com.leavems.entity.LeaveStatus;
import jakarta.validation.constraints.NotNull;

public record LeaveStatusUpdate(
        @NotNull LeaveStatus status
) {
}
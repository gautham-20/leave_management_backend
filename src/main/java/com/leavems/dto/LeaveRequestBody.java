package com.leavems.dto;

import com.leavems.entity.LeaveType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record LeaveRequestBody(
        @NotBlank String type,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotBlank @Size(max = 1000) String reason
) {
    public LeaveType resolvedType() {
        return LeaveType.fromWire(type);
    }
}
package com.leavems.dto;

import com.leavems.entity.LeaveRequest;
import com.leavems.entity.User;

import java.time.Instant;
import java.time.LocalDate;

/**
 * The leave shape consumed by the frontend. Field names mirror the old db.json
 * records exactly (including {@code employee} as a plain name string) so the
 * existing pages and the PDF report keep working untouched.
 */
public record LeaveResponse(
        Long id,
        String employee,
        String type,
        LocalDate startDate,
        LocalDate endDate,
        String reason,
        String status,
        Instant createdAt
) {
    public static LeaveResponse from(LeaveRequest leave) {
        User employee = leave.getEmployee();
        return new LeaveResponse(
                leave.getId(),
                employee.getName(),
                leave.getType().toWire(),
                leave.getStartDate(),
                leave.getEndDate(),
                leave.getReason(),
                leave.getStatus().name(),
                leave.getCreatedAt()
        );
    }
}
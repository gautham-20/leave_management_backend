package com.leavems.service;

import com.leavems.dto.LeaveBalanceResponse;
import com.leavems.dto.LeaveRequestBody;
import com.leavems.dto.LeaveResponse;
import com.leavems.entity.LeaveRequest;
import com.leavems.entity.LeaveStatus;
import com.leavems.entity.LeaveType;
import com.leavems.entity.Role;
import com.leavems.entity.User;
import com.leavems.repository.LeaveRequestRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LeaveService {

    /**
     * Annual allowance per leave type. These were previously hardcoded in the
     * employee page's {@code LEAVE_LIMITS} object and enforced only in the
     * browser, so any client could bypass them.
     */
    private static final Map<LeaveType, Integer> LIMITS = Map.of(
            LeaveType.Vacation, 20,
            LeaveType.SickLeave, 10,
            LeaveType.Personal, 5
    );

    private final LeaveRequestRepository leaveRepository;

    public LeaveService(LeaveRequestRepository leaveRepository) {
        this.leaveRepository = leaveRepository;
    }

    @Transactional(readOnly = true)
    public List<LeaveResponse> listVisibleTo(User user) {
        List<LeaveRequest> leaves = user.getRole() == Role.EMPLOYEE
                // Employees only ever see their own requests.
                ? leaveRepository.findByEmployeeIdWithEmployee(user.getId())
                : leaveRepository.findAllWithEmployee();

        return leaves.stream().map(LeaveResponse::from).toList();
    }

    @Transactional
    public LeaveResponse create(User user, LeaveRequestBody body) {
        LeaveType type;
        try {
            type = body.resolvedType();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }

        LocalDate start = body.startDate();
        LocalDate end = body.endDate();

        if (end.isBefore(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date cannot be earlier than start date");
        }
        if (start.isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Leave cannot start in the past");
        }
        if (start.getDayOfWeek() == DayOfWeek.SUNDAY || end.getDayOfWeek() == DayOfWeek.SUNDAY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Sundays are non-working days and cannot be a start or end date");
        }

        int requestedDays = countWorkingDays(start, end);
        int limit = LIMITS.get(type);
        int alreadyUsed = usedDays(user.getId(), type);
        int remaining = limit - alreadyUsed;

        if (requestedDays > remaining) {
            String detail = alreadyUsed > 0
                    ? "You have " + alreadyUsed + " of " + limit + " days already approved"
                    : "Your request of " + requestedDays + " working days exceeds the " + limit + "-day limit";
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Please contact your manager. " + detail + " for " + type.toWire() + ".");
        }

        LeaveRequest saved = leaveRepository.save(new LeaveRequest(user, type, start, end, body.reason().trim()));
        return LeaveResponse.from(saved);
    }

    @Transactional
    public LeaveResponse updateStatus(User approver, Long id, LeaveStatus status) {
        LeaveRequest leave = leaveRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Leave request not found"));

        if (leave.getStatus() != LeaveStatus.Pending) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This request has already been " + leave.getStatus().name().toLowerCase());
        }
        if (approver.getRole() == Role.EMPLOYEE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Employees cannot approve leave requests");
        }
        if (leave.getEmployee().getId().equals(approver.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot approve your own leave request");
        }

        leave.setStatus(status);
        return LeaveResponse.from(leaveRepository.save(leave));
    }

    @Transactional(readOnly = true)
    public LeaveBalanceResponse balanceFor(User user) {
        List<LeaveRequest> approved = leaveRepository.findByEmployeeIdAndStatus(user.getId(), LeaveStatus.Approved);

        Map<String, Integer> limits = new LinkedHashMap<>();
        Map<String, Integer> used = new LinkedHashMap<>();

        // Iteration order follows LIMITS so the UI cards stay in a stable order.
        for (LeaveType type : List.of(LeaveType.Vacation, LeaveType.SickLeave, LeaveType.Personal)) {
            int days = approved.stream()
                    .filter(leave -> leave.getType() == type)
                    .mapToInt(leave -> countWorkingDays(leave.getStartDate(), leave.getEndDate()))
                    .sum();
            limits.put(type.toWire(), LIMITS.get(type));
            used.put(type.toWire(), days);
        }

        return new LeaveBalanceResponse(user.getId(), user.getName(), limits, used);
    }

    /** Counts Mon-Sat days inclusively; Sundays are non-working. */
    static int countWorkingDays(LocalDate start, LocalDate end) {
        int count = 0;
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            if (day.getDayOfWeek() != DayOfWeek.SUNDAY) {
                count++;
            }
        }
        return count;
    }

    private int usedDays(Long userId, LeaveType type) {
        return leaveRepository.findByEmployeeIdAndStatus(userId, LeaveStatus.Approved).stream()
                .filter(leave -> leave.getType() == type)
                .mapToInt(leave -> countWorkingDays(leave.getStartDate(), leave.getEndDate()))
                .sum();
    }
}
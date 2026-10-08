package com.leavems.controller;

import com.leavems.dto.LeaveBalanceResponse;
import com.leavems.dto.LeaveRequestBody;
import com.leavems.dto.LeaveResponse;
import com.leavems.dto.LeaveStatusUpdate;
import com.leavems.entity.User;
import com.leavems.security.CurrentUser;
import com.leavems.service.LeaveService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/leaves")
public class LeaveController {

    private final LeaveService leaveService;
    private final CurrentUser currentUser;

    public LeaveController(LeaveService leaveService, CurrentUser currentUser) {
        this.leaveService = leaveService;
        this.currentUser = currentUser;
    }

    /** Employees receive only their own records; managers and admins receive all. */
    @GetMapping
    public List<LeaveResponse> list() {
        return leaveService.listVisibleTo(currentUser.require());
    }

    @GetMapping("/balance")
    public LeaveBalanceResponse balance() {
        return leaveService.balanceFor(currentUser.require());
    }

    @PostMapping
    public ResponseEntity<LeaveResponse> create(@Valid @RequestBody LeaveRequestBody body) {
        User user = currentUser.require();
        return ResponseEntity.status(HttpStatus.CREATED).body(leaveService.create(user, body));
    }

    @PatchMapping("/{id}")
    public LeaveResponse updateStatus(@PathVariable Long id, @Valid @RequestBody LeaveStatusUpdate update) {
        User approver = currentUser.require();
        return leaveService.updateStatus(approver, id, update.status());
    }
}
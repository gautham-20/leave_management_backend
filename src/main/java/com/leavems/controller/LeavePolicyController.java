package com.leavems.controller;

import com.leavems.dto.LeavePolicyBody;
import com.leavems.dto.LeavePolicyResponse;
import com.leavems.entity.User;
import com.leavems.security.CurrentUser;
import com.leavems.service.LeavePolicyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Company leave policies.
 *
 * <p>{@code GET /api/policies} returns the active policies and is available to
 * every signed-in user, since the manager and employee dashboards display them.
 * {@code GET /api/policies/all} additionally includes retired policies and is
 * admin-only, so the admin editor can show and un-retire them.
 */
@RestController
@RequestMapping("/api/policies")
public class LeavePolicyController {

    private final LeavePolicyService policyService;
    private final CurrentUser currentUser;

    public LeavePolicyController(LeavePolicyService policyService, CurrentUser currentUser) {
        this.policyService = policyService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<LeavePolicyResponse> listActive() {
        return policyService.listActive();
    }

    @GetMapping("/all")
    public List<LeavePolicyResponse> listAll() {
        return policyService.listAll(currentUser.require());
    }

    @PostMapping
    public ResponseEntity<LeavePolicyResponse> create(@Valid @RequestBody LeavePolicyBody body) {
        User admin = currentUser.require();
        return ResponseEntity.status(HttpStatus.CREATED).body(policyService.create(admin, body));
    }

    /** Full replace, kept alongside PATCH so either verb works from the client. */
    @PutMapping("/{id}")
    public LeavePolicyResponse replace(@PathVariable Long id, @Valid @RequestBody LeavePolicyBody body) {
        User admin = currentUser.require();
        return policyService.update(admin, id, body);
    }

    @PatchMapping("/{id}")
    public LeavePolicyResponse update(@PathVariable Long id, @Valid @RequestBody LeavePolicyBody body) {
        User admin = currentUser.require();
        return policyService.update(admin, id, body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        policyService.delete(currentUser.require(), id);
        return ResponseEntity.noContent().build();
    }
}

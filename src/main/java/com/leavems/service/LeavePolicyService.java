package com.leavems.service;

import com.leavems.dto.LeavePolicyBody;
import com.leavems.dto.LeavePolicyResponse;
import com.leavems.entity.LeavePolicy;
import com.leavems.entity.Role;
import com.leavems.entity.User;
import com.leavems.repository.LeavePolicyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Read and edit company leave policies.
 *
 * <p>Every authenticated role may read, because all three dashboards display the
 * policies. Writing is restricted to {@link Role#ADMIN}; the role check lives
 * here rather than only in {@code SecurityConfig} so the rule holds even if a
 * route is ever added without a matching matcher.
 */
@Service
public class LeavePolicyService {

    private final LeavePolicyRepository policyRepository;

    public LeavePolicyService(LeavePolicyRepository policyRepository) {
        this.policyRepository = policyRepository;
    }

    @Transactional(readOnly = true)
    public List<LeavePolicyResponse> listActive() {
        return policyRepository.findByActiveTrueOrderBySortOrderAscIdAsc().stream()
                .map(LeavePolicyResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LeavePolicyResponse> listAll(User user) {
        requireAdmin(user);
        return policyRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(LeavePolicyResponse::from)
                .toList();
    }

    @Transactional
    public LeavePolicyResponse create(User user, LeavePolicyBody body) {
        requireAdmin(user);

        String title = body.title().trim();
        String description = body.description().trim();
        ensureTitleAvailable(title, null);

        int sortOrder = body.sortOrderOr(nextSortOrder());
        LeavePolicy saved = policyRepository.save(new LeavePolicy(title, description, sortOrder));
        if (body.active() != null) {
            saved.setActive(body.active());
            saved = policyRepository.save(saved);
        }

        return LeavePolicyResponse.from(saved);
    }

    @Transactional
    public LeavePolicyResponse update(User user, Long id, LeavePolicyBody body) {
        requireAdmin(user);

        LeavePolicy policy = policyRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy not found"));

        String title = body.title().trim();
        ensureTitleAvailable(title, id);

        policy.setTitle(title);
        policy.setDescription(body.description().trim());
        policy.setSortOrder(body.sortOrderOr(policy.getSortOrder()));
        // Only change active when the client sent it, so a partial edit that
        // omits the flag does not silently retire the policy.
        if (body.active() != null) {
            policy.setActive(body.active());
        }

        return LeavePolicyResponse.from(policyRepository.save(policy));
    }

    @Transactional
    public void delete(User user, Long id) {
        requireAdmin(user);

        if (!policyRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy not found");
        }
        policyRepository.deleteById(id);
    }

    private int nextSortOrder() {
        return policyRepository.findAll().stream()
                .mapToInt(LeavePolicy::getSortOrder)
                .max()
                .orElse(0) + 1;
    }

    /** Rejects a duplicate title before the database constraint does. */
    private void ensureTitleAvailable(String title, Long allowId) {
        policyRepository.findByTitleIgnoreCase(title)
                .filter(existing -> !existing.getId().equals(allowId))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "A policy titled \"" + title + "\" already exists");
                });
    }

    private void requireAdmin(User user) {
        if (user.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only administrators can change company leave policies");
        }
    }
}

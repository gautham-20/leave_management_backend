package com.leavems.dto;

import java.util.List;
import java.util.Map;

/**
 * Per-type leave usage for one employee. The employee dashboard previously
 * recomputed this client-side by string-matching the employee's name against
 * every leave record, which both mis-attributed similarly-named employees and
 * shipped the whole table to the browser. This keeps the calculation on the
 * server, keyed by the caller's real user id.
 */
public record LeaveBalanceResponse(
        Long userId,
        String userName,
        Map<String, Integer> limits,
        Map<String, Integer> used
) {
    public List<String> types() {
        return limits.keySet().stream().toList();
    }
}
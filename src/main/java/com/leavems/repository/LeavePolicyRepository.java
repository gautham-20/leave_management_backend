package com.leavems.repository;

import com.leavems.entity.LeavePolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeavePolicyRepository extends JpaRepository<LeavePolicy, Long> {

    /** Every policy in display order, active or not — used by the admin editor. */
    List<LeavePolicy> findAllByOrderBySortOrderAscIdAsc();

    /** Only the policies every dashboard should show, in display order. */
    List<LeavePolicy> findByActiveTrueOrderBySortOrderAscIdAsc();

    /**
     * Title uniqueness is enforced by a database constraint, but checking it
     * first turns a constraint violation into a clean 400 with a usable message.
     */
    Optional<LeavePolicy> findByTitleIgnoreCase(String title);
}

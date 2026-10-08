package com.leavems.repository;

import com.leavems.entity.LeaveRequest;
import com.leavems.entity.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    /**
     * Fetches leaves eagerly joined with their employee. The dashboards render
     * the employee's name in every row, so a lazy proxy would trigger N+1
     * queries (or fail outright outside a transaction).
     */
    @Query("select l from LeaveRequest l join fetch l.employee order by l.startDate desc, l.id desc")
    List<LeaveRequest> findAllWithEmployee();

    @Query("select l from LeaveRequest l join fetch l.employee where l.employee.id = :employeeId order by l.startDate desc, l.id desc")
    List<LeaveRequest> findByEmployeeIdWithEmployee(@Param("employeeId") Long employeeId);

    List<LeaveRequest> findByEmployeeIdAndStatus(Long employeeId, LeaveStatus status);
}
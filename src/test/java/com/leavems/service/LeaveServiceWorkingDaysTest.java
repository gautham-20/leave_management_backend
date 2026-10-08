package com.leavems.service;

import com.leavems.entity.LeaveStatus;
import com.leavems.entity.LeaveType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The working-day rule is the one piece of leave policy that both the frontend
 * hint text and the backend enforcement depend on, so it is pinned down here.
 */
class LeaveServiceWorkingDaysTest {

    @Test
    void countsSundaysAsNonWorking() {
        // 2026-04-20 is a Monday, 2026-04-26 the following Sunday: 7 days span 6 working days.
        assertThat(LeaveService.countWorkingDays(
                LocalDate.of(2026, 4, 20), LocalDate.of(2026, 4, 26))).isEqualTo(6);
    }

    @Test
    void countsSingleDayRangeAsOne() {
        assertThat(LeaveService.countWorkingDays(
                LocalDate.of(2026, 4, 20), LocalDate.of(2026, 4, 20))).isEqualTo(1);
    }

    @Test
    void excludesOnlySundayFromAFullWeek() {
        assertThat(LeaveService.countWorkingDays(
                LocalDate.of(2026, 4, 20), LocalDate.of(2026, 4, 25))).isEqualTo(6);
    }

    @Test
    void mapsLeaveTypeWireValues() {
        assertThat(LeaveType.fromWire("Sick Leave")).isEqualTo(LeaveType.SickLeave);
        assertThat(LeaveType.fromWire("Vacation")).isEqualTo(LeaveType.Vacation);
        assertThat(LeaveType.fromWire("Personal")).isEqualTo(LeaveType.Personal);
        assertThat(LeaveType.SickLeave.toWire()).isEqualTo("Sick Leave");
    }

    @Test
    void rejectsUnknownLeaveType() {
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> LeaveType.fromWire("Unpaid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void statusNamesMatchTheFrontendContract() {
        assertThat(LeaveStatus.Pending.name()).isEqualTo("Pending");
        assertThat(LeaveStatus.Approved.name()).isEqualTo("Approved");
        assertThat(LeaveStatus.Rejected.name()).isEqualTo("Rejected");
    }
}
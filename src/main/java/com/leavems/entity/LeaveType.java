package com.leavems.entity;

public enum LeaveType {
    Vacation,
    SickLeave,
    Personal;

    /**
     * Converts the wire format used by the frontend ("Sick Leave") into the
     * enum constant. Enum constants cannot contain spaces, so the type carries
     * a separate display name and is mapped explicitly.
     */
    public static LeaveType fromWire(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "Vacation" -> Vacation;
            case "Sick Leave" -> SickLeave;
            case "Personal" -> Personal;
            default -> throw new IllegalArgumentException("Unknown leave type: " + value);
        };
    }

    /** The exact string the frontend expects back. */
    public String toWire() {
        return switch (this) {
            case Vacation -> "Vacation";
            case SickLeave -> "Sick Leave";
            case Personal -> "Personal";
        };
    }
}
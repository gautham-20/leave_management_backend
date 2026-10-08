-- Core schema for the Leave Management System (MySQL 8.0+).
-- Mirrors db/migration/postgresql/V1__init_schema.sql; keep the two in sync.
--
-- MySQL differences from the PostgreSQL version:
--   BIGSERIAL                   -> BIGINT AUTO_INCREMENT
--   TIMESTAMP WITH TIME ZONE    -> DATETIME(6)
--     MySQL has no timezone-aware type; the JDBC connection sets the session
--     time zone to UTC (see application.yml) and the app works in UTC
--     throughout, so the value stored is correct.
--   CHECK constraints            -> supported from MySQL 8.0.16 onward.

CREATE TABLE users (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(120) NOT NULL,
    email       VARCHAR(180) NOT NULL,
    password    VARCHAR(100) NOT NULL,
    role        VARCHAR(20)  NOT NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('EMPLOYEE', 'MANAGER', 'ADMIN'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE leaves (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    employee_id BIGINT        NOT NULL,
    type        VARCHAR(40)   NOT NULL,
    start_date  DATE          NOT NULL,
    end_date    DATE          NOT NULL,
    reason      VARCHAR(1000) NOT NULL,
    status      VARCHAR(20)   NOT NULL DEFAULT 'Pending',
    created_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    -- ON DELETE CASCADE removes an employee's leave requests with the account.
    CONSTRAINT fk_leaves_employee FOREIGN KEY (employee_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_leaves_status CHECK (status IN ('Pending', 'Approved', 'Rejected')),
    -- Values are the Java enum constant names, since the column is persisted
    -- with @Enumerated(EnumType.STRING). Keep these in sync with LeaveType.
    CONSTRAINT ck_leaves_type   CHECK (type IN ('Vacation', 'SickLeave', 'Personal')),
    CONSTRAINT ck_leaves_dates  CHECK (end_date >= start_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Employee dashboards filter by owner + status to compute leave usage.
CREATE INDEX idx_leaves_employee_status ON leaves (employee_id, status);

-- The manager approval inbox lists outstanding requests.
CREATE INDEX idx_leaves_status ON leaves (status);
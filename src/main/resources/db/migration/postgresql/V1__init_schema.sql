-- Core schema for the Leave Management System.
-- Enum values mirror the role/status strings the Next.js frontend already uses.

CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(120)  NOT NULL,
    email       VARCHAR(180)  NOT NULL,
    password    VARCHAR(100)  NOT NULL,
    role        VARCHAR(20)   NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('EMPLOYEE', 'MANAGER', 'ADMIN'))
);

CREATE TABLE leaves (
    id          BIGSERIAL PRIMARY KEY,
    employee_id BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type        VARCHAR(40)   NOT NULL,
    start_date  DATE          NOT NULL,
    end_date    DATE          NOT NULL,
    reason      VARCHAR(1000) NOT NULL,
    status      VARCHAR(20)   NOT NULL DEFAULT 'Pending',
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_leaves_status CHECK (status IN ('Pending', 'Approved', 'Rejected')),
    -- Values are the Java enum constant names, since the column is persisted
    -- with @Enumerated(EnumType.STRING). Keep these in sync with LeaveType.
    CONSTRAINT ck_leaves_type   CHECK (type IN ('Vacation', 'SickLeave', 'Personal')),
    CONSTRAINT ck_leaves_dates  CHECK (end_date >= start_date)
);

-- Employee dashboards filter by owner + status to compute leave usage.
CREATE INDEX idx_leaves_employee_status ON leaves (employee_id, status);

-- The manager approval inbox lists outstanding requests.
CREATE INDEX idx_leaves_status ON leaves (status);
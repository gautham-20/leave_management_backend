-- Company leave policies, editable by an admin and read-only for everyone else.
-- Mirrors db/migration/postgresql/V3__leave_policies.sql; keep the two in sync.
--
-- MySQL differences from the PostgreSQL version:
--   BIGSERIAL                -> BIGINT AUTO_INCREMENT
--   TIMESTAMP WITH TIME ZONE -> DATETIME(6)  (connection session TZ is UTC)
--   BOOLEAN                  -> alias for TINYINT(1), which is what the JPA
--                               boolean mapping validates against.

CREATE TABLE leave_policies (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    title       VARCHAR(120)  NOT NULL,
    description VARCHAR(1000) NOT NULL,
    is_active   BOOLEAN       NOT NULL DEFAULT TRUE,
    sort_order  INT           NOT NULL DEFAULT 0,
    created_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_leave_policies_title UNIQUE (title),
    CONSTRAINT ck_leave_policies_sort_order CHECK (sort_order >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Dashboards list policies in display order.
CREATE INDEX idx_leave_policies_active_sort ON leave_policies (is_active, sort_order);

-- Seeded with the text that was previously hardcoded in the admin page, so an
-- existing deployment shows the same policies immediately after migrating.
INSERT INTO leave_policies (title, description, is_active, sort_order) VALUES
    ('Vacation Policy',    '20 days per annum. Max 5 days carry-over.',                        TRUE, 1),
    ('Medical/Sick Leave', '10 days. Documentation required for more than 3 days.',           TRUE, 2),
    ('Probation Rule',     'New employees can only access Sick Leave for the first 3 months.', TRUE, 3);

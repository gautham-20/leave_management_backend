-- Company leave policies, editable by an admin and read-only for everyone else.
--
-- These rows were previously hardcoded in the admin page's "Active Policies"
-- card, which meant they could be displayed but never changed without a code
-- edit and redeploy. They now live in the database so the admin dashboard is
-- the single place they are maintained and every dashboard reads the same rows.

CREATE TABLE leave_policies (
    id          BIGSERIAL       PRIMARY KEY,
    title       VARCHAR(120)    NOT NULL,
    description VARCHAR(1000)   NOT NULL,
    is_active   BOOLEAN         NOT NULL DEFAULT TRUE,
    sort_order  INTEGER         NOT NULL DEFAULT 0,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_leave_policies_title UNIQUE (title),
    CONSTRAINT ck_leave_policies_sort_order CHECK (sort_order >= 0)
);

-- Dashboards list policies in display order.
CREATE INDEX idx_leave_policies_active_sort ON leave_policies (is_active, sort_order);

-- Seeded with the text that was previously hardcoded in the admin page, so an
-- existing deployment shows the same policies immediately after migrating.
INSERT INTO leave_policies (title, description, is_active, sort_order) VALUES
    ('Vacation Policy',     '20 days per annum. Max 5 days carry-over.',        TRUE, 1),
    ('Medical/Sick Leave',  '10 days. Documentation required for more than 3 days.', TRUE, 2),
    ('Probation Rule',      'New employees can only access Sick Leave for the first 3 months.', TRUE, 3);

-- Seeds the accounts and leave requests from the original db.json so the
-- existing demo login flow keeps working. Passwords are BCrypt hashes; the
-- plaintext values are listed in the README for local sign-in.
--
-- Written with plain INSERT ... SELECT ... WHERE NOT EXISTS rather than
-- PostgreSQL's ON CONFLICT so the same script runs against the H2 database
-- used by the test suite.

INSERT INTO users (name, email, password, role)
SELECT v.name, v.email, v.password, v.role
FROM (VALUES
    ('Admin User',    'admin@test.com', '$2a$10$tI.vQIEiT/BDK.oXV7E7t.3fVefSvc8PEbygVFVVfkyGjoFKMVRve', 'ADMIN'),
    ('Manager Sarah', 'man@gmail.com',  '$2a$10$U3kIH0Tn0xPwNNdwXE8/q.oC8zeFQHvIcfoxupjmhOdKIM4iQ7OK6', 'MANAGER'),
    ('GAUTHAM',       'ab@gmail.com',   '$2a$10$ffOgOvE709eYZaBPRkGFIO8Iv8GMQe8L.qT3kq8HSzCKgwaABfYWm', 'EMPLOYEE'),
    ('Sam',           'bsam@gmail.com', '$2a$10$rHmlrqS8NCtRcjJX9.cy6.uOicu.u7kKzoiV8EQcaRuq1jQYc9ctq', 'EMPLOYEE')
) AS v(name, email, password, role)
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.email = v.email);

-- The two approved requests that were already in db.json, attributed to Sam by
-- email rather than by name so the foreign key resolves deterministically.
INSERT INTO leaves (employee_id, type, start_date, end_date, reason, status)
SELECT u.id, v.type, v.start_date, v.end_date, v.reason, v.status
FROM (VALUES
    ('bsam@gmail.com', 'Vacation', DATE '2026-04-20', DATE '2026-04-25', 'a trip to America', 'Approved'),
    ('bsam@gmail.com', 'Vacation', DATE '2026-05-05', DATE '2026-05-12', 'A trip to Canada',  'Approved')
) AS v(email, type, start_date, end_date, reason, status)
JOIN users u ON u.email = v.email
WHERE NOT EXISTS (
    SELECT 1 FROM leaves l
    WHERE l.employee_id = u.id
      AND l.start_date = v.start_date
      AND l.end_date = v.end_date
);
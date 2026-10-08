-- Seeds the accounts and leave requests from the original db.json so the
-- existing demo login flow keeps working. Passwords are BCrypt hashes; the
-- plaintext values are listed in the README for local sign-in.
--
-- Mirrors db/migration/postgresql/V2__seed_demo_data.sql. Uses UNION ALL
-- rather than the PostgreSQL `VALUES ... AS alias(...)` row constructor, which
-- MySQL does not support in this form.

INSERT INTO users (name, email, password, role)
SELECT 'Admin User', 'admin@test.com', '$2a$10$tI.vQIEiT/BDK.oXV7E7t.3fVefSvc8PEbygVFVVfkyGjoFKMVRve', 'ADMIN'
    FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'admin@test.com')
UNION ALL
SELECT 'Manager Sarah', 'man@gmail.com', '$2a$10$U3kIH0Tn0xPwNNdwXE8/q.oC8zeFQHvIcfoxupjmhOdKIM4iQ7OK6', 'MANAGER'
    FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'man@gmail.com')
UNION ALL
SELECT 'GAUTHAM', 'ab@gmail.com', '$2a$10$ffOgOvE709eYZaBPRkGFIO8Iv8GMQe8L.qT3kq8HSzCKgwaABfYWm', 'EMPLOYEE'
    FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'ab@gmail.com')
UNION ALL
SELECT 'Sam', 'bsam@gmail.com', '$2a$10$rHmlrqS8NCtRcjJX9.cy6.uOicu.u7kKzoiV8EQcaRuq1jQYc9ctq', 'EMPLOYEE'
    FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'bsam@gmail.com');

-- The two approved requests that were already in db.json, attributed to Sam by
-- email rather than by name so the foreign key resolves deterministically.
INSERT INTO leaves (employee_id, type, start_date, end_date, reason, status)
SELECT u.id, 'Vacation', '2026-04-20', '2026-04-25', 'a trip to America', 'Approved'
    FROM users u
    WHERE u.email = 'bsam@gmail.com'
      AND NOT EXISTS (
          SELECT 1 FROM leaves l
          WHERE l.employee_id = u.id
            AND l.start_date = '2026-04-20'
            AND l.end_date = '2026-04-25'
      )
UNION ALL
SELECT u.id, 'Vacation', '2026-05-05', '2026-05-12', 'A trip to Canada', 'Approved'
    FROM users u
    WHERE u.email = 'bsam@gmail.com'
      AND NOT EXISTS (
          SELECT 1 FROM leaves l
          WHERE l.employee_id = u.id
            AND l.start_date = '2026-05-05'
            AND l.end_date = '2026-05-12'
      );
-- Creates the local development database and user for the Leave Management API.
-- Run once as a MySQL administrator:
--   sudo mysql < backend/setup-mysql.sql
CREATE DATABASE IF NOT EXISTS leave_management
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'leaveuser'@'localhost' IDENTIFIED BY 'leavepass';
CREATE USER IF NOT EXISTS 'leaveuser'@'127.0.0.1' IDENTIFIED BY 'leavepass';

-- The account needs DDL rights because Flyway creates the schema and indexes.
GRANT ALL PRIVILEGES ON leave_management.* TO 'leaveuser'@'localhost';
GRANT ALL PRIVILEGES ON leave_management.* TO 'leaveuser'@'127.0.0.1';

FLUSH PRIVILEGES;

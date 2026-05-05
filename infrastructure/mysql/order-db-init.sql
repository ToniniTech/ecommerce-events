-- order-db initialization
-- Spring Boot JPA (ddl-auto: update) creates the tables automatically.
-- This script just ensures the DB and user exist with correct permissions.

CREATE DATABASE IF NOT EXISTS order_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON order_db.* TO 'orderuser'@'%';
FLUSH PRIVILEGES;

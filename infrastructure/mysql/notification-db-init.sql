-- notification-db initialization
CREATE DATABASE IF NOT EXISTS notification_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON notification_db.* TO 'notifuser'@'%';
FLUSH PRIVILEGES;

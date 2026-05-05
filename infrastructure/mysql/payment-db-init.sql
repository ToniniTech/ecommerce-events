-- payment-db initialization
CREATE DATABASE IF NOT EXISTS payment_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON payment_db.* TO 'paymentuser'@'%';
FLUSH PRIVILEGES;

CREATE TABLE IF NOT EXISTS analytics_excluded_ips (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ip_address VARCHAR(45) NOT NULL UNIQUE,
    label VARCHAR(120),
    created_at DATETIME NOT NULL,
    INDEX idx_analytics_excluded_ip (ip_address)
) ENGINE=InnoDB;

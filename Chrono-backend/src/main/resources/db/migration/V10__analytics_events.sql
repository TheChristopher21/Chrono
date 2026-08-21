CREATE TABLE IF NOT EXISTS analytics_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(32) NOT NULL,
    visitor_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64),
    path VARCHAR(512) NOT NULL,
    page_title VARCHAR(256),
    referrer VARCHAR(512),
    referrer_host VARCHAR(255),
    element_label VARCHAR(160),
    element_target VARCHAR(512),
    language VARCHAR(32),
    viewport_width INT,
    viewport_height INT,
    created_at DATETIME NOT NULL,
    INDEX idx_analytics_created_type (created_at, event_type),
    INDEX idx_analytics_path_created (path, created_at),
    INDEX idx_analytics_visitor_created (visitor_id, created_at)
) ENGINE=InnoDB;

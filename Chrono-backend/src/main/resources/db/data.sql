INSERT INTO user (username, password, role) VALUES ('admin', 'admin123', 'ADMIN');
INSERT INTO user (username, password, role) VALUES ('user', 'user123', 'USER');
INSERT INTO role (name) VALUES ('USER');
INSERT INTO customers (name) VALUES ('Default Customer');
INSERT INTO projects (customer_id, name) VALUES (1, 'Standard Project');
UPDATE companies SET customer_tracking_enabled = FALSE WHERE customer_tracking_enabled IS NULL;

ALTER TABLE inv_stock_movements
    ADD COLUMN IF NOT EXISTS lot_number VARCHAR(64);

ALTER TABLE inv_stock_movements
    ADD COLUMN IF NOT EXISTS serial_number VARCHAR(64);

ALTER TABLE inv_stock_movements
    ADD COLUMN IF NOT EXISTS expiration_date DATE;

-- ============================================================
-- 002_usdt.sql — USDT 支付通道（币安 BSC 收款）
-- ============================================================

ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_method VARCHAR(20) DEFAULT 'xunhu';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS usdt_order_id VARCHAR(64);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS usdt_amount NUMERIC(12,2);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS usdt_address VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_orders_usdt_pending ON orders(status) WHERE payment_method = 'usdt';

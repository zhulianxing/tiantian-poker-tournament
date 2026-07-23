-- 代理付费注册：orders 支持 agent_signup 订单类型（无牌桌/门店/赛事，注册费全额归平台）
-- 同时把代理默认抽成从 10% 调整为 20%（新分配比：平台 30% / 门店 30% / 代理 20% / 总代 20%）
-- 执行：psql -U poker -d poker_night -f migrations/005_agent_signup.sql

-- 1. 代理注册单不再关联牌桌/门店
ALTER TABLE orders ALTER COLUMN table_id DROP NOT NULL;
ALTER TABLE orders ALTER COLUMN venue_id DROP NOT NULL;

-- 2. 订单类型：launch（默认，扫码开桌）/ agent_signup（代理注册费）
ALTER TABLE orders ADD COLUMN IF NOT EXISTS order_type VARCHAR(20) NOT NULL DEFAULT 'launch';

-- 3. 代理注册单载荷（支付成功后据此创建 agents 行）与回写的邀请码
ALTER TABLE orders ADD COLUMN IF NOT EXISTS signup_payload JSONB;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS agent_code VARCHAR(16);

-- 4. 代理抽成 10% → 20%（含存量代理）
ALTER TABLE agents ALTER COLUMN rate SET DEFAULT 20;
UPDATE agents SET rate = 20 WHERE rate = 10;

-- 索引
CREATE INDEX IF NOT EXISTS idx_orders_order_type ON orders(order_type);

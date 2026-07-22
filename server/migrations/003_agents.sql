-- 代理制度：总代 → 代理 → 门店（两级）
-- 佣金从门店分成中扣除：venue_income = 门店毛分成 − agent_income − master_agent_income
-- 执行：psql -U poker -d poker_night -f migrations/003_agents.sql

-- ============================================================
-- 1. 代理表（parent_id 为 NULL 的是总代）
-- ============================================================
CREATE TABLE IF NOT EXISTS agents (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code          VARCHAR(16) UNIQUE NOT NULL,          -- 邀请码（绑定门店/下级代通用）
  parent_id     UUID REFERENCES agents(id),            -- NULL = 总代；否则为直属总代
  name          VARCHAR(64) NOT NULL,
  phone         VARCHAR(20) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  rate          INTEGER NOT NULL DEFAULT 10,           -- 每单抽成百分比（按订单金额，从门店分成扣）
  status        VARCHAR(20) DEFAULT 'active',
  created_at    TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- 2. 门店归属代理
-- ============================================================
ALTER TABLE venues ADD COLUMN IF NOT EXISTS agent_id UUID REFERENCES agents(id);

-- ============================================================
-- 3. 订单代理分润（下单时计算并落库，单位：分）
--    venue_income 语义变为"门店实得"（已扣除代理佣金）
-- ============================================================
ALTER TABLE orders ADD COLUMN IF NOT EXISTS agent_id UUID REFERENCES agents(id);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS master_agent_id UUID REFERENCES agents(id);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS agent_income INTEGER NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS master_agent_income INTEGER NOT NULL DEFAULT 0;

-- ============================================================
-- 4. 代理提现申请
-- ============================================================
CREATE TABLE IF NOT EXISTS agent_withdrawals (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  agent_id    UUID NOT NULL REFERENCES agents(id),
  amount      INTEGER NOT NULL,
  status      VARCHAR(20) DEFAULT 'pending',           -- pending / paid / rejected
  note        TEXT,
  created_at  TIMESTAMPTZ DEFAULT NOW(),
  paid_at     TIMESTAMPTZ
);

-- ============================================================
-- 索引
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_venues_agent ON venues(agent_id);
CREATE INDEX IF NOT EXISTS idx_agents_parent ON agents(parent_id);
CREATE INDEX IF NOT EXISTS idx_orders_agent ON orders(agent_id);
CREATE INDEX IF NOT EXISTS idx_orders_master_agent ON orders(master_agent_id);
CREATE INDEX IF NOT EXISTS idx_agent_withdrawals_agent ON agent_withdrawals(agent_id);

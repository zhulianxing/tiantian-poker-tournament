-- Poker Night — 数据库初始化
-- 执行：psql -U postgres -f migrations/001_init.sql

-- ============================================================
-- 1. 场馆表
-- ============================================================
CREATE TABLE IF NOT EXISTS venues (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name        VARCHAR(100) NOT NULL,
  address     TEXT,
  contact     VARCHAR(50),
  phone       VARCHAR(20),
  rate_plan   JSONB DEFAULT '{"platform":30,"venue":70}',
  theme       JSONB DEFAULT '{}',
  status      VARCHAR(20) DEFAULT 'active',
  created_at  TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- 2. 设备绑定表（大屏 SN → 酒吧）
-- ============================================================
CREATE TABLE IF NOT EXISTS device_bindings (
  device_sn   VARCHAR(64) PRIMARY KEY,
  venue_id    UUID NOT NULL REFERENCES venues(id),
  table_label VARCHAR(50),
  bound_at    TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(venue_id, table_label)
);

-- ============================================================
-- 3. 牌桌表
-- ============================================================
CREATE TABLE IF NOT EXISTS tables (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  venue_id      UUID NOT NULL REFERENCES venues(id),
  device_sn     VARCHAR(64) REFERENCES device_bindings(device_sn),
  code          VARCHAR(8) UNIQUE NOT NULL,
  label         VARCHAR(50),
  launch_fee    INTEGER NOT NULL DEFAULT 2500,
  max_players   SMALLINT DEFAULT 6,
  status        VARCHAR(20) DEFAULT 'idle',
  created_at    TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- 4. 赛事表
-- ============================================================
CREATE TABLE IF NOT EXISTS tournaments (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  display_code  VARCHAR(20) UNIQUE NOT NULL,
  table_id      UUID NOT NULL REFERENCES tables(id),
  status        VARCHAR(20) DEFAULT 'registering',
  launch_fee    INTEGER NOT NULL,
  player_count  SMALLINT DEFAULT 0,
  max_players   SMALLINT DEFAULT 6,
  start_chips   INTEGER DEFAULT 1000,
  start_blind   SMALLINT DEFAULT 10,
  blind_interval INTEGER DEFAULT 600,
  wait_countdown INTEGER DEFAULT 300,
  action_timeout INTEGER DEFAULT 30,
  started_at    TIMESTAMPTZ,
  finished_at   TIMESTAMPTZ,
  created_at    TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- 5. 玩家表
-- ============================================================
CREATE TABLE IF NOT EXISTS players (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  nickname      VARCHAR(30) NOT NULL,
  phone         VARCHAR(20) UNIQUE,
  password_hash VARCHAR(255),
  avatar        VARCHAR(10) DEFAULT '🃏',
  total_games   INTEGER DEFAULT 0,
  total_wins    INTEGER DEFAULT 0,
  created_at    TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- 6. 赛事参与者表
-- ============================================================
CREATE TABLE IF NOT EXISTS tournament_players (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tournament_id UUID NOT NULL REFERENCES tournaments(id),
  player_id     UUID NOT NULL REFERENCES players(id),
  seat_index    SMALLINT NOT NULL,
  chip_count    INTEGER DEFAULT 1000,
  status        VARCHAR(20) DEFAULT 'waiting',
  final_rank    SMALLINT,
  joined_at     TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(tournament_id, seat_index),
  UNIQUE(tournament_id, player_id)
);

-- ============================================================
-- 7. 订单表
-- ============================================================
CREATE TABLE IF NOT EXISTS orders (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tournament_id   UUID REFERENCES tournaments(id),
  table_id        UUID NOT NULL REFERENCES tables(id),
  venue_id        UUID NOT NULL REFERENCES venues(id),
  payer_id        UUID REFERENCES players(id),
  payer_identifier VARCHAR(100),
  amount          INTEGER NOT NULL,
  platform_fee    INTEGER NOT NULL,
  venue_income    INTEGER NOT NULL,
  status          VARCHAR(20) DEFAULT 'pending',
  xunhupay_order_id VARCHAR(64),
  paid_at         TIMESTAMPTZ,
  refunded_at     TIMESTAMPTZ,
  refund_reason   TEXT,
  refund_initiated_by VARCHAR(20),
  created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- 8. 结算台账
-- ============================================================
CREATE TABLE IF NOT EXISTS settlements (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  venue_id      UUID NOT NULL REFERENCES venues(id),
  period_start  DATE NOT NULL,
  period_end    DATE NOT NULL,
  total_orders  INTEGER DEFAULT 0,
  total_amount  INTEGER DEFAULT 0,
  venue_share   INTEGER DEFAULT 0,
  platform_share INTEGER DEFAULT 0,
  status        VARCHAR(20) DEFAULT 'pending',
  paid_at       TIMESTAMPTZ,
  transfer_proof TEXT,
  created_at    TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- 9. 对局日志表
-- ============================================================
CREATE TABLE IF NOT EXISTS game_logs (
  id            BIGSERIAL PRIMARY KEY,
  tournament_id UUID NOT NULL REFERENCES tournaments(id),
  hand_number   INTEGER NOT NULL,
  action        JSONB NOT NULL,
  created_at    TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- 索引
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_tables_venue ON tables(venue_id);
CREATE INDEX IF NOT EXISTS idx_tournaments_table ON tournaments(table_id);
CREATE INDEX IF NOT EXISTS idx_tournaments_status ON tournaments(status);
CREATE INDEX IF NOT EXISTS idx_orders_venue ON orders(venue_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_game_logs_tournament ON game_logs(tournament_id);
CREATE INDEX IF NOT EXISTS idx_settlements_venue ON settlements(venue_id);
CREATE INDEX IF NOT EXISTS idx_tournament_players_tournament ON tournament_players(tournament_id);
CREATE INDEX IF NOT EXISTS idx_tournament_players_player ON tournament_players(player_id);

-- ============================================================
-- 初始数据：测试场馆
-- ============================================================
INSERT INTO venues (name, address, contact, phone) 
VALUES ('测试酒吧', '首尔测试地址', '测试员', '010-0000-0000')
ON CONFLICT DO NOTHING;

-- ============================================================
-- 启用 UUID 扩展
-- ============================================================
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

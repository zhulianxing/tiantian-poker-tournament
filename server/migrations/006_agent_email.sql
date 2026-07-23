-- 代理账号迁移到邮箱验证码体系（与玩家端同一套 email_codes，免密码）
-- 手机号/密码列保留（存量数据不丢），仅放开约束；新代理只有 email
-- 执行：psql -U poker -d poker_night -f migrations/006_agent_email.sql

ALTER TABLE agents ADD COLUMN IF NOT EXISTS email VARCHAR(255) UNIQUE;
ALTER TABLE agents ALTER COLUMN phone DROP NOT NULL;
ALTER TABLE agents ALTER COLUMN password_hash DROP NOT NULL;

CREATE INDEX IF NOT EXISTS idx_agents_email ON agents(email);

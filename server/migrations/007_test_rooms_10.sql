-- 007_test_rooms_10.sql — 10 个永久测试房间 + players.is_bot 机器人标记
-- is_bot：陪玩 Bot 判定从昵称前缀迁移到该列（女性昵称不再含 Bot 前缀，前缀判定会失效）
ALTER TABLE players ADD COLUMN IF NOT EXISTS is_bot BOOLEAN DEFAULT false;

-- 存量陪玩 Bot 补标记（历史昵称为 Bot/AutoBot 前缀）
UPDATE players SET is_bot = true
WHERE is_bot IS NOT TRUE AND (nickname LIKE 'Bot%' OR nickname LIKE 'AutoBot%');

-- 10 个永久免费测试房间：is_test=true 由 poker-socket 保活
-- （永远保持一场免费可入座赛事，结束/取消后自动开下一场）
INSERT INTO venues (id, name) VALUES ('00000000-0000-0000-0000-000000000002', '平台默认门店')
ON CONFLICT (id) DO NOTHING;

INSERT INTO tables (venue_id, code, label, launch_fee, max_players, status, is_test)
SELECT '00000000-0000-0000-0000-000000000002', code, code, 0, 6, 'idle', true
FROM (VALUES
  ('TEST01'), ('TEST02'), ('TEST03'), ('TEST04'), ('TEST05'),
  ('TEST06'), ('TEST07'), ('TEST08'), ('TEST09'), ('TEST10')
) AS v(code)
ON CONFLICT (code) DO NOTHING;

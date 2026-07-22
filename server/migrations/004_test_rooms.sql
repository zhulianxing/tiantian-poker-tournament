-- 004_test_rooms.sql — 永久测试房间
-- is_test=true 的牌桌由 poker-socket 保证永远有一场免费可入座的测试赛：
-- 赛事结束/取消后自动开下一场，服务启动自检补齐（ensureTestRoomTournament）。
ALTER TABLE tables ADD COLUMN IF NOT EXISTS is_test BOOLEAN DEFAULT false;

-- SNGT01 设为永久测试房间
UPDATE tables SET is_test = true WHERE code = 'SNGT01';

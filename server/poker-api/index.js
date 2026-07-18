// poker-api/index.js — Poker REST API Server (Port 3000)
'use strict';

const express = require('express');
const cors = require('cors');
const jwt = require('jsonwebtoken');
const { db, query, mailer } = require('@poker-night/shared');
const { TOURNAMENT_STATUS, PLAYER_STATUS, ACTIONS, SNG_DEFAULTS } = require('@poker-night/shared');
const { sendCode } = mailer;

const app = express();
const PORT = process.env.POKER_API_PORT || 3010;
const JWT_SECRET = process.env.JWT_SECRET || 'poker-night-secret-2026';

app.use(cors());
app.use(express.json());

// ============================================================
// 中间件：JWT 鉴权
// ============================================================
function auth(req, res, next) {
  const token = req.headers.authorization?.replace('Bearer ', '');
  if (!token) return res.status(401).json({ error: 'no token' });
  try {
    req.player = jwt.verify(token, JWT_SECRET);
    next();
  } catch {
    res.status(401).json({ error: 'invalid token' });
  }
}

// ============================================================
// 健康检查
// ============================================================
app.get('/health', (req, res) => res.json({ ok: true, service: 'poker-api' }));

// ============================================================
// 认证（邮箱 + 验证码）
// ============================================================

// 发送验证码
app.post('/api/v1/auth/send-code', async (req, res) => {
  const { email, purpose } = req.body;
  if (!email || !purpose) return res.status(400).json({ error: 'missing fields' });
  if (!['login', 'register'].includes(purpose)) return res.status(400).json({ error: 'invalid purpose' });

  // 基本邮箱格式校验
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!emailRegex.test(email)) return res.status(400).json({ error: 'invalid email format' });

  try {
    // 注册：检查邮箱是否已注册
    if (purpose === 'register') {
      const exist = await query('SELECT id FROM players WHERE email = $1', [email]);
      if (exist.rows.length > 0) return res.status(409).json({ error: 'email already registered' });
    }

    // 登录：检查邮箱是否存在
    if (purpose === 'login') {
      const exist = await query('SELECT id FROM players WHERE email = $1', [email]);
      if (exist.rows.length === 0) return res.status(404).json({ error: 'email not found' });
    }

    // 防暴力：同一邮箱60秒内只能发一次
    const recent = await query(
      `SELECT created_at FROM email_codes WHERE email = $1 ORDER BY created_at DESC LIMIT 1`,
      [email]
    );
    if (recent.rows.length > 0) {
      const elapsed = Date.now() - new Date(recent.rows[0].created_at).getTime();
      if (elapsed < 60000) {
        const waitSec = Math.ceil((60000 - elapsed) / 1000);
        return res.status(429).json({ error: `too many requests, try again in ${waitSec}s` });
      }
    }

    // 生成6位验证码
    const code = String(Math.floor(100000 + Math.random() * 900000));

    // 存入数据库
    await query(
      'INSERT INTO email_codes (email, code, purpose) VALUES ($1, $2, $3)',
      [email, code, purpose]
    );

    // 发送邮件（开发模式下输出到控制台）
    await sendCode(email, code, purpose);

    res.json({ success: true });
  } catch (err) {
    console.error('[Auth] send-code error:', err);
    res.status(500).json({ error: 'failed to send code' });
  }
});

// 注册（邮箱 + 验证码 + 昵称）
app.post('/api/v1/auth/register', async (req, res) => {
  const { email, code, nickname } = req.body;
  if (!email || !code || !nickname) return res.status(400).json({ error: 'missing fields' });
  if (nickname.length > 30) return res.status(400).json({ error: 'nickname too long' });

  try {
    // 验证码校验
    const codeResult = await query(
      `SELECT * FROM email_codes 
       WHERE email = $1 AND code = $2 AND purpose = 'register' 
         AND used = FALSE AND expires_at > NOW() 
       ORDER BY created_at DESC LIMIT 1`,
      [email, code]
    );
    if (codeResult.rows.length === 0) return res.status(400).json({ error: 'invalid or expired code' });

    // 再次检查邮箱是否已注册（防止并发）
    const exist = await query('SELECT id FROM players WHERE email = $1', [email]);
    if (exist.rows.length > 0) return res.status(409).json({ error: 'email already registered' });

    // 创建玩家
    const result = await query(
      'INSERT INTO players (nickname, email, avatar) VALUES ($1, $2, $3) RETURNING id, nickname, email, avatar',
      [nickname, email, '🃏']
    );
    const player = result.rows[0];

    // 标记验证码已使用
    await query('UPDATE email_codes SET used = TRUE WHERE id = $1', [codeResult.rows[0].id]);

    const token = jwt.sign({ id: player.id, nickname: player.nickname }, JWT_SECRET, { expiresIn: '7d' });
    res.json({ player, token });
  } catch (err) {
    if (err.code === '23505') return res.status(409).json({ error: 'email already registered' });
    console.error('[Auth] register error:', err);
    res.status(500).json({ error: err.message });
  }
});

// 登录（邮箱 + 验证码）
app.post('/api/v1/auth/login', async (req, res) => {
  const { email, code } = req.body;
  if (!email || !code) return res.status(400).json({ error: 'missing fields' });

  try {
    // 验证码校验
    const codeResult = await query(
      `SELECT * FROM email_codes 
       WHERE email = $1 AND code = $2 AND purpose = 'login' 
         AND used = FALSE AND expires_at > NOW() 
       ORDER BY created_at DESC LIMIT 1`,
      [email, code]
    );
    if (codeResult.rows.length === 0) return res.status(400).json({ error: 'invalid or expired code' });

    // 获取玩家
    const result = await query('SELECT id, nickname, email, avatar FROM players WHERE email = $1', [email]);
    const player = result.rows[0];
    if (!player) return res.status(404).json({ error: 'player not found' });

    // 标记验证码已使用
    await query('UPDATE email_codes SET used = TRUE WHERE id = $1', [codeResult.rows[0].id]);

    const token = jwt.sign({ id: player.id, nickname: player.nickname }, JWT_SECRET, { expiresIn: '7d' });
    res.json({ player, token });
  } catch (err) {
    console.error('[Auth] login error:', err);
    res.status(500).json({ error: err.message });
  }
});

// ============================================================
// 牌桌
// ============================================================

// 获取牌桌信息
app.get('/api/v1/tables/:code', async (req, res) => {
  try {
    const result = await query(
      `SELECT t.*, v.name as venue_name, v.theme as venue_theme
       FROM tables t JOIN venues v ON t.venue_id = v.id
       WHERE t.code = $1`, [req.params.code]
    );
    if (result.rows.length === 0) return res.status(404).json({ error: 'table not found' });
    res.json(result.rows[0]);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// 获取牌桌状态
app.get('/api/v1/tables/:code/status', async (req, res) => {
  try {
    const tableResult = await query('SELECT * FROM tables WHERE code = $1', [req.params.code]);
    if (tableResult.rows.length === 0) return res.status(404).json({ error: 'table not found' });
    const table = tableResult.rows[0];

    // 获取当前赛事
    const tournamentResult = await query(
      `SELECT * FROM tournaments WHERE table_id = $1 AND status IN ('registering','started') ORDER BY created_at DESC LIMIT 1`,
      [table.id]
    );
    const tournament = tournamentResult.rows[0] || null;

    // 获取入座玩家
    let players = [];
    if (tournament) {
      const playerResult = await query(
        `SELECT tp.*, p.nickname, p.avatar
         FROM tournament_players tp JOIN players p ON tp.player_id = p.id
         WHERE tp.tournament_id = $1 ORDER BY tp.seat_index`,
        [tournament.id]
      );
      players = playerResult.rows;
    }

    res.json({ table, tournament, players });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ============================================================
// 赛事
// ============================================================

// 玩家免费入座
app.post('/api/v1/tournaments/:id/join', auth, async (req, res) => {
  const client = await db.getClient();
  try {
    await client.query('BEGIN');

    // 检查赛事状态
    const tResult = await client.query('SELECT * FROM tournaments WHERE id = $1 FOR UPDATE', [req.params.id]);
    const tournament = tResult.rows[0];
    if (!tournament) throw new Error('tournament not found');
    if (tournament.status !== 'registering') throw new Error('tournament not in registering state');
    if (tournament.player_count >= tournament.max_players) throw new Error('table is full');

    // 检查是否已入座
    const existResult = await client.query(
      'SELECT * FROM tournament_players WHERE tournament_id = $1 AND player_id = $2',
      [tournament.id, req.player.id]
    );
    if (existResult.rows.length > 0) throw new Error('already joined');

    // 找空座位
    const seatResult = await client.query(
      'SELECT seat_index FROM tournament_players WHERE tournament_id = $1 ORDER BY seat_index',
      [tournament.id]
    );
    const takenSeats = seatResult.rows.map(r => r.seat_index);
    let seatIndex = 0;
    for (let i = 0; i < tournament.max_players; i++) {
      if (!takenSeats.includes(i)) { seatIndex = i; break; }
    }

    // 入座
    await client.query(
      `INSERT INTO tournament_players (tournament_id, player_id, seat_index, chip_count, status)
       VALUES ($1, $2, $3, $4, 'waiting')`,
      [tournament.id, req.player.id, seatIndex, tournament.start_chips]
    );

    // 更新人数
    await client.query(
      'UPDATE tournaments SET player_count = player_count + 1 WHERE id = $1',
      [tournament.id]
    );

    await client.query('COMMIT');

    // 通过 Socket.IO 广播给牌桌房间
    const tableResult = await query('SELECT code FROM tables WHERE id = $1', [tournament.table_id]);
    const tableCode = tableResult.rows[0]?.code;
    if (tableCode) {
      app.locals.io?.to(`table:${tableCode}`).emit('seat_joined', {
        tournamentId: tournament.id,
        playerId: req.player.id,
        nickname: req.player.nickname,
        seatIndex,
        chipCount: tournament.start_chips,
      });
    }

    res.json({ success: true, seatIndex, chipCount: tournament.start_chips });
  } catch (err) {
    await client.query('ROLLBACK');
    res.status(400).json({ error: err.message });
  } finally {
    client.release();
  }
});

// 玩家离开座位
app.post('/api/v1/tournaments/:id/leave', auth, async (req, res) => {
  const client = await db.getClient();
  try {
    await client.query('BEGIN');

    // 检查赛事状态
    const tResult = await client.query('SELECT * FROM tournaments WHERE id = $1 FOR UPDATE', [req.params.id]);
    const tournament = tResult.rows[0];
    if (!tournament) throw new Error('tournament not found');
    if (!['registering', 'started'].includes(tournament.status)) throw new Error('cannot leave at this stage');

    // 检查玩家是否已入座
    const existResult = await client.query(
      'SELECT * FROM tournament_players WHERE tournament_id = $1 AND player_id = $2',
      [tournament.id, req.player.id]
    );
    if (existResult.rows.length === 0) throw new Error('not joined');

    const playerRecord = existResult.rows[0];

    // 删除入座记录
    await client.query(
      'DELETE FROM tournament_players WHERE tournament_id = $1 AND player_id = $2',
      [tournament.id, req.player.id]
    );

    // 更新人数
    await client.query(
      'UPDATE tournaments SET player_count = GREATEST(player_count - 1, 0) WHERE id = $1',
      [tournament.id]
    );

    await client.query('COMMIT');

    // 通过 Socket.IO 广播给牌桌房间
    const tableResult = await query('SELECT code FROM tables WHERE id = $1', [tournament.table_id]);
    const tableCode = tableResult.rows[0]?.code;
    if (tableCode) {
      app.locals.io?.to(`table:${tableCode}`).emit('seat_left', {
        tournamentId: tournament.id,
        playerId: req.player.id,
        nickname: req.player.nickname,
        seatIndex: playerRecord.seat_index,
      });
    }

    res.json({ success: true });
  } catch (err) {
    await client.query('ROLLBACK');
    res.status(400).json({ error: err.message });
  } finally {
    client.release();
  }
});

// 提交操作
app.post('/api/v1/tournaments/:id/action', auth, async (req, res) => {
  const { action, amount } = req.body;
  // 操作由 Socket.IO 处理（实时性要求），REST 仅做记录
  // 这里返回确认，实际逻辑在 poker-socket 中
  res.json({ success: true, message: 'action received, process via socket' });
});

// 获取赛事结果
app.get('/api/v1/tournaments/:id/result', async (req, res) => {
  try {
    const result = await query(
      `SELECT t.*, tp.seat_index, tp.final_rank, tp.chip_count, p.nickname, p.avatar
       FROM tournaments t
       JOIN tournament_players tp ON tp.tournament_id = t.id
       JOIN players p ON tp.player_id = p.id
       WHERE t.id = $1 AND t.status = 'finished'
       ORDER BY tp.final_rank`, [req.params.id]
    );
    if (result.rows.length === 0) return res.status(404).json({ error: 'result not available' });
    res.json(result.rows);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// 玩家对局历史
app.get('/api/v1/players/me/history', auth, async (req, res) => {
  try {
    const result = await query(
      `SELECT tp.*, t.display_code, t.finished_at, t.max_players, p.nickname
       FROM tournament_players tp
       JOIN tournaments t ON tp.tournament_id = t.id
       JOIN players p ON tp.player_id = p.id
       WHERE tp.player_id = $1
       ORDER BY t.created_at DESC LIMIT 50`,
      [req.player.id]
    );
    res.json(result.rows);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ============================================================
// 启动
// ============================================================
app.listen(PORT, '0.0.0.0', () => {
  console.log(`[Poker API] running on port ${PORT}`);
});

module.exports = app;

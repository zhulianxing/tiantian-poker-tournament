// poker-api/index.js — Poker REST API Server (Port 3000)
'use strict';

const express = require('express');
const cors = require('cors');
const jwt = require('jsonwebtoken');
const bcrypt = require('bcryptjs');
const { db, query } = require('@poker-night/shared');
const { TOURNAMENT_STATUS, PLAYER_STATUS, ACTIONS, SNG_DEFAULTS } = require('@poker-night/shared');

const app = express();
const PORT = process.env.POKER_API_PORT || 3000;
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
// 认证
// ============================================================

// 注册（手机号 + 密码，MVP 不做短信验证码）
app.post('/api/v1/auth/register', async (req, res) => {
  const { phone, nickname, password } = req.body;
  if (!phone || !nickname || !password) {
    return res.status(400).json({ error: 'missing fields' });
  }
  try {
    const hash = bcrypt.hashSync(password, 10);
    const result = await query(
      'INSERT INTO players (nickname, phone, password_hash) VALUES ($1, $2, $3) RETURNING id, nickname, phone, avatar',
      [nickname, phone, hash]
    );
    const player = result.rows[0];
    const token = jwt.sign({ id: player.id, nickname: player.nickname }, JWT_SECRET, { expiresIn: '7d' });
    res.json({ player, token });
  } catch (err) {
    if (err.code === '23505') return res.status(409).json({ error: 'phone already registered' });
    res.status(500).json({ error: err.message });
  }
});

// 登录
app.post('/api/v1/auth/login', async (req, res) => {
  const { phone, password } = req.body;
  if (!phone || !password) return res.status(400).json({ error: 'missing fields' });
  try {
    const result = await query('SELECT * FROM players WHERE phone = $1', [phone]);
    const player = result.rows[0];
    if (!player) return res.status(404).json({ error: 'player not found' });
    if (!bcrypt.compareSync(password, player.password_hash)) {
      return res.status(401).json({ error: 'wrong password' });
    }
    const token = jwt.sign({ id: player.id, nickname: player.nickname }, JWT_SECRET, { expiresIn: '7d' });
    res.json({
      player: { id: player.id, nickname: player.nickname, phone: player.phone, avatar: player.avatar },
      token,
    });
  } catch (err) {
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
  const client = await db.connect();
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

    // 触发 Socket.IO 通知（由外部监听处理）
    app.locals.io?.emit('seat_joined', {
      tournamentId: tournament.id,
      playerId: req.player.id,
      nickname: req.player.nickname,
      seatIndex,
    });

    res.json({ success: true, seatIndex, chipCount: tournament.start_chips });
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

// poker-socket/index.js — Socket.IO 实时服务器 (Port 3001)
'use strict';

const express = require('express');
const http = require('http');
const jwt = require('jsonwebtoken');
const { Server } = require('socket.io');
const { SNGManager } = require('@poker-night/poker-engine');
const { query } = require('@poker-night/shared');
const { TOURNAMENT_STATUS, PLAYER_STATUS, ACTIONS, SNG_DEFAULTS } = require('@poker-night/shared');

const app = express();
const server = http.createServer(app);
const io = new Server(server, { cors: { origin: '*' } });

const PORT = process.env.POKER_SOCKET_PORT || 3001;
const JWT_SECRET = process.env.JWT_SECRET || 'poker-night-secret-2026';

// 活跃赛事管理器（tournamentId → SNGManager）
const activeGames = new Map();
// 倒计时定时器
const countdownTimers = new Map();

// ============================================================
// 中间件：JWT 鉴权
// ============================================================
io.use((socket, next) => {
  const token = socket.handshake.auth?.token;
  if (!token) {
    // 允许未登录连接（大屏可匿名连接）
    socket.isTV = socket.handshake.query.role === 'tv';
    if (socket.isTV) return next();
    return next(new Error('authentication required'));
  }
  try {
    socket.player = jwt.verify(token, JWT_SECRET);
    next();
  } catch {
    next(new Error('invalid token'));
  }
});

// ============================================================
// 连接处理
// ============================================================
io.on('connection', (socket) => {
  console.log(`[Socket] Connected: ${socket.id} role=${socket.isTV ? 'tv' : 'player'}`);

  // 加入牌桌房间
  socket.on('join_table', (tableCode) => {
    socket.join(`table:${tableCode}`);
    console.log(`[Socket] ${socket.id} joined table:${tableCode}`);
  });

  // 玩家操作
  socket.on('player_action', async (data) => {
    const { tournamentId, action, amount } = data;
    const game = activeGames.get(tournamentId);
    if (!game) {
      socket.emit('error', { message: 'game not found' });
      return;
    }

    const result = game.handleAction(socket.player.id, action, amount);
    if (!result.success) {
      socket.emit('action_rejected', { error: result.error, action });
    }
  });

  // 断线处理
  socket.on('disconnect', () => {
    console.log(`[Socket] Disconnected: ${socket.id}`);
  });
});

// ============================================================
// 赛事激活（由支付回调触发）
// ============================================================
async function activateTournament(tournamentId) {
  try {
    // 获取赛事信息
    const tResult = await query(
      `SELECT t.*, tbl.code as table_code FROM tournaments t
       JOIN tables tbl ON t.table_id = tbl.id WHERE t.id = $1`,
      [tournamentId]
    );
    const tournament = tResult.rows[0];
    if (!tournament) throw new Error('tournament not found');

    // 更新状态为 registering
    await query('UPDATE tournaments SET status = $1 WHERE id = $2',
      [TOURNAMENT_STATUS.REGISTERING, tournamentId]);

    // 通知大屏进入等待阶段
    io.to(`table:${tournament.table_code}`).emit('tournament_activated', {
      tournamentId,
      displayCode: tournament.display_code,
      maxPlayers: tournament.max_players,
      launchFee: tournament.launch_fee,
      waitCountdown: tournament.wait_countdown,
    });

    // 启动倒计时
    startWaitCountdown(tournamentId, tournament.wait_countdown, tournament.table_code);

    console.log(`[Socket] Tournament ${tournament.display_code} activated`);
  } catch (err) {
    console.error('[Socket] Activate error:', err.message);
  }
}

// ============================================================
// 倒计时
// ============================================================
function startWaitCountdown(tournamentId, duration, tableCode) {
  let remaining = duration;
  const timer = setInterval(async () => {
    remaining--;
    io.to(`table:${tableCode}`).emit('countdown_tick', { remaining });

    if (remaining <= 0) {
      clearInterval(timer);
      countdownTimers.delete(tournamentId);

      // 检查入座人数
      const result = await query(
        'SELECT player_count, max_players FROM tournaments WHERE id = $1', [tournamentId]
      );
      const t = result.rows[0];
      if (!t) return;

      if (t.player_count < SNG_DEFAULTS.MIN_PLAYERS) {
        // 取消赛事，触发退款
        await cancelTournament(tournamentId, 'insufficient players');
      } else if (t.player_count >= t.max_players) {
        // 已满人，直接开赛
        await startTournament(tournamentId);
      } else {
        // 倒计时结束，≥2 人开赛
        await startTournament(tournamentId);
      }
    }
  }, 1000);

  countdownTimers.set(tournamentId, timer);
}

// ============================================================
// 取消赛事
// ============================================================
async function cancelTournament(tournamentId, reason) {
  await query('UPDATE tournaments SET status = $1 WHERE id = $2',
    [TOURNAMENT_STATUS.CANCELLED, tournamentId]);

  // 通知退款（由 payment-svc 处理）
  io.emit('tournament_cancelled', { tournamentId, reason });
  console.log(`[Socket] Tournament ${tournamentId} cancelled: ${reason}`);
}

// ============================================================
// 开始赛事
// ============================================================
async function startTournament(tournamentId) {
  try {
    // 获取赛事和玩家
    const tResult = await query('SELECT * FROM tournaments WHERE id = $1', [tournamentId]);
    const tournament = tResult.rows[0];

    const pResult = await query(
      `SELECT tp.*, p.nickname FROM tournament_players tp
       JOIN players p ON tp.player_id = p.id
       WHERE tp.tournament_id = $1 ORDER BY tp.seat_index`,
      [tournamentId]
    );

    const players = pResult.rows.map(p => ({
      id: p.player_id,
      seatIndex: p.seat_index,
      chipCount: p.chip_count,
      status: p.status,
    }));

    // 创建 SNG 管理器
    const game = new SNGManager(tournament, players);

    // 绑定事件 → 广播到牌桌房间
    const tableResult = await query('SELECT code FROM tables WHERE id = $1', [tournament.table_id]);
    const tableCode = tableResult.rows[0].code;
    const room = `table:${tableCode}`;

    game.emit = (event, data) => {
      io.to(room).emit(event, data);
      // 底牌只发给对应玩家
      if (event === 'hole_cards') {
        const playerSocket = [...io.sockets.sockets.values()]
          .find(s => s.player?.id === data.playerId);
        if (playerSocket) playerSocket.emit('hole_cards', data);
      }
    };

    activeGames.set(tournamentId, game);

    // 更新状态
    await query('UPDATE tournaments SET status = $1, started_at = NOW() WHERE id = $2',
      [TOURNAMENT_STATUS.STARTED, tournamentId]);

    // 开始
    game.start();

    io.to(room).emit('tournament_started', {
      tournamentId,
      displayCode: tournament.display_code,
      players: players.map(p => ({ id: p.id, seatIndex: p.seat_index, chips: p.chipCount })),
    });

    console.log(`[Socket] Tournament ${tournament.display_code} started`);
  } catch (err) {
    console.error('[Socket] Start error:', err.message);
  }
}

// 导出供外部调用
module.exports = { io, server, activateTournament, startTournament, cancelTournament };

server.listen(PORT, '0.0.0.0', () => {
  console.log(`[Poker Socket] running on port ${PORT}`);
});

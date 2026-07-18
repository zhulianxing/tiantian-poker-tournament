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
app.use(express.json());
const server = http.createServer(app);
const io = new Server(server, { cors: { origin: '*' } });

const PORT = process.env.POKER_SOCKET_PORT || 3011;
const JWT_SECRET = process.env.JWT_SECRET || 'poker-night-secret-2026';

// 活跃赛事管理器（tournamentId → SNGManager）
const activeGames = new Map();
// 倒计时定时器
const countdownTimers = new Map();
// 断线玩家追踪（playerId → { tournamentId, disconnectedAt, missedHands }）
const disconnectedPlayers = new Map();

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
  socket.on('join_table', async (data) => {
    const tableCode = typeof data === 'string' ? data : data?.tableCode;
    if (!tableCode) return;
    socket.join(`table:${tableCode}`);
    console.log(`[Socket] ${socket.id} joined table:${tableCode}`);

    // 发送当前桌状态给新连接的客户端（TV 或玩家）
    try {
      const tableResult = await query('SELECT * FROM tables WHERE code = $1', [tableCode]);
      if (tableResult.rows.length > 0) {
        const table = tableResult.rows[0];
        const tournamentResult = await query(
          `SELECT * FROM tournaments WHERE table_id = $1 AND status IN ('idle','registering','started') ORDER BY created_at DESC LIMIT 1`,
          [table.id]
        );
        const tournament = tournamentResult.rows[0] || null;
        let players = [];
        if (tournament) {
          const playerResult = await query(
            `SELECT tp.seat_index, tp.chip_count, tp.status, p.nickname, p.avatar, p.id as player_id
             FROM tournament_players tp JOIN players p ON tp.player_id = p.id
             WHERE tp.tournament_id = $1 ORDER BY tp.seat_index`,
            [tournament.id]
          );
          players = playerResult.rows;
        }
        // 构造客户端期望的 table_state 格式
        const phase = tournament ? (tournament.status === 'idle' ? 'idle' : tournament.status === 'registering' ? 'registering' : 'started') : 'idle';
        const seats = [];
        const maxPlayers = table.max_players || 6;
        for (let i = 0; i < maxPlayers; i++) {
          const p = players.find(pl => pl.seat_index === i);
          if (p) {
            seats.push({
              seatIndex: i,
              playerId: p.player_id,
              nickname: p.nickname,
              avatar: p.avatar || '🃏',
              status: p.status,
              chipCount: p.chip_count,
              currentBet: 0,
              isActing: false,
              lastAction: '',
            });
          } else {
            seats.push({ seatIndex: i, status: 'empty' });
          }
        }
        socket.emit('table_state', {
          phase,
          seats,
          displayCode: tournament?.display_code || '',
          sb: tournament?.start_blind || 10,
          bb: (tournament?.start_blind || 10) * 2,
          blindLevel: 1,
          pot: 0,
          communityCards: [],
          actingIndex: -1,
          dealerIndex: 0,
          handNumber: 0,
          stage: '',
        });
        console.log(`[Socket] Sent table_state to ${socket.id} for ${tableCode} (phase=${phase}, ${players.length} players)`);
      }
    } catch (err) {
      console.error(`[Socket] Error sending table_state: ${err.message}`);
    }

    // 检查是否是重连玩家
    if (socket.player) {
      for (const [tid, game] of activeGames) {
        const player = game.players.find(p => p.id === socket.player.id);
        if (player) {
          handleReconnect(socket, tid);
          break;
        }
      }
    }
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
    handleDisconnect(socket);
  });
});

// ============================================================
// 断线处理
// ============================================================
async function handleDisconnect(socket) {
  if (!socket.player) return;

  const playerId = socket.player.id;

  // 查找玩家参与的活跃赛事
  let tournamentId = null;
  for (const [tid, game] of activeGames) {
    const player = game.players.find(p => p.id === playerId);
    if (player && player.status !== 'eliminated') {
      tournamentId = tid;
      // 标记为 sitout
      if (player.status === 'playing') {
        player.status = 'sitout';
        console.log(`[Socket] Player ${playerId} marked sitout in tournament ${tid}`);
      }
      break;
    }
  }

  if (!tournamentId) return;

  // 记录断线信息
  disconnectedPlayers.set(playerId, {
    tournamentId,
    disconnectedAt: Date.now(),
    missedHands: 0,
  });

  // 通知牌桌
  const tResult = await query(
    `SELECT t.code FROM tournaments t JOIN tables tbl ON t.table_id = tbl.id WHERE t.id = $1`,
    [tournamentId]
  );
  const tableCode = tResult.rows[0]?.code;
  if (tableCode) {
    io.to(`table:${tableCode}`).emit('player_disconnected', { playerId, nickname: socket.player.nickname });
  }
}

// ============================================================
// 玩家重连处理
// ============================================================
function handleReconnect(socket, tournamentId) {
  const playerId = socket.player.id;
  const discInfo = disconnectedPlayers.get(playerId);
  if (!discInfo) return;

  // 检查是否超过 3 局未回归
  if (discInfo.missedHands >= 3) {
    // 自动淘汰
    const game = activeGames.get(discInfo.tournamentId);
    if (game) {
      const player = game.players.find(p => p.id === playerId);
      if (player) {
        player.status = 'eliminated';
        game.emit('player_eliminated', { playerId, reason: 'disconnected 3 hands' });
      }
    }
    disconnectedPlayers.delete(playerId);
    return;
  }

  // 恢复玩家状态
  const game = activeGames.get(discInfo.tournamentId);
  if (game) {
    const player = game.players.find(p => p.id === playerId);
    if (player && player.status === 'sitout') {
      player.status = 'playing';
      console.log(`[Socket] Player ${playerId} reconnected`);
    }
  }
  disconnectedPlayers.delete(playerId);
}

// ============================================================
// 内部 API（供 payment-svc 调用）
// ============================================================
app.post('/internal/broadcast', (req, res) => {
  try {
    const { event, tableCode, data } = req.body;
    if (!event || !tableCode) return res.status(400).json({ error: 'missing event or tableCode' });
    io.to(`table:${tableCode}`).emit(event, data || {});
    console.log(`[Socket] Broadcast ${event} to table:${tableCode}`);
    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.post('/internal/activate', async (req, res) => {
  try {
    const { tournamentId } = req.body;
    if (!tournamentId) return res.status(400).json({ error: 'missing tournamentId' });
    await activateTournament(tournamentId);
    res.json({ success: true });
  } catch (e) {
    console.error('[Socket] Activate error:', e.message);
    res.status(500).json({ error: e.message });
  }
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

    // 满员立即开赛（每 tick 检查）
    if (remaining % 5 === 0) {
      try {
        const checkResult = await query('SELECT player_count, max_players FROM tournaments WHERE id = $1', [tournamentId]);
        const ct = checkResult.rows[0];
        if (ct && ct.player_count >= ct.max_players) {
          clearInterval(timer);
          countdownTimers.delete(tournamentId);
          io.to(`table:${tableCode}`).emit('countdown_tick', { remaining: 0, reason: 'table_full' });
          await startTournament(tournamentId);
          return;
        }
      } catch (e) { /* ignore tick check errors */ }
    }

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
module.exports = { io, server, activateTournament, startTournament, cancelTournament, handleReconnect };

// 每手结束后检查断线玩家
setInterval(() => {
  for (const [playerId, info] of disconnectedPlayers) {
    info.missedHands++;
    if (info.missedHands >= 3) {
      // 自动淘汰
      const game = activeGames.get(info.tournamentId);
      if (game) {
        const player = game.players.find(p => p.id === playerId);
        if (player && player.status !== 'eliminated') {
          player.status = 'eliminated';
          game.emit('player_eliminated', { playerId, reason: 'disconnected 3 hands' });
          console.log(`[Socket] Player ${playerId} eliminated after 3 missed hands`);
        }
      }
      disconnectedPlayers.delete(playerId);
    }
  }
}, 60000); // 每分钟检查一次

server.listen(PORT, '0.0.0.0', () => {
  console.log(`[Poker Socket] running on port ${PORT}`);
});

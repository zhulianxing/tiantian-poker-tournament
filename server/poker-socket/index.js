// poker-socket/index.js — Socket.IO 实时服务器 (Port 3001)
'use strict';

const express = require('express');
const http = require('http');
const jwt = require('jsonwebtoken');
const { Server } = require('socket.io');
const { SNGManager } = require('@poker-night/poker-engine');
const { query } = require('@poker-night/shared');
const { TOURNAMENT_STATUS, PLAYER_STATUS, ACTIONS, SNG_DEFAULTS, BOT_COMPANION, genBotIdentity } = require('@poker-night/shared');

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
// 激活/开赛幂等守卫（AGENT-04 事故：API 满员自动激活 + 手动 /internal/activate 并发导致双开引擎）
const activatingTournaments = new Set();
const startingTournaments = new Set();
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
        // 检查是否有活跃的 SNG 游戏
        const activeGame = [...activeGames.values()].find(g =>
          g.tournament && g.tournament.table_id === table.id
        );
        const gameSeats = activeGame ? activeGame.getSeatsSnapshot() : null;
        const gameHand = activeGame ? activeGame.handNumber : 0;
        const gameBlind = activeGame ? activeGame.blindLevel : 1;
        const gamePot = activeGame?.currentHand ? activeGame.currentHand.pot : 0;
        const gameActing = activeGame?.currentHand ? activeGame.currentHand.actingIndex : -1;
        const gameStage = activeGame?.currentHand ? activeGame.currentHand.stage : '';
        const gameDealer = activeGame ? activeGame.dealerIndex : 0;

        // 如果有活跃游戏，用游戏状态覆盖座位信息
        const finalSeats = gameSeats || seats;

        socket.emit('table_state', {
          phase,
          tournamentId: tournament?.id || '',
          seats: finalSeats,
          displayCode: tournament?.display_code || '',
          sb: tournament ? Math.min(Math.floor(tournament.start_blind * Math.pow(2, gameBlind - 1)), 100000000) : 10,
          bb: tournament ? Math.min(Math.floor(tournament.start_blind * 2 * Math.pow(2, gameBlind - 1)), 200000000) : 20,
          blindLevel: gameBlind,
          pot: gamePot,
          communityCards: activeGame?.currentHand?.revealedCommunity || [],
          actingIndex: gameActing,
          dealerIndex: gameDealer,
          handNumber: gameHand,
          stage: gameStage,
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

  // 该玩家还有其他在线连接（agent 客户端与主人浏览器同号双开）时，单个 socket 断开不算断线
  const stillOnline = [...io.sockets.sockets.values()]
    .some(s => s.id !== socket.id && s.player?.id === playerId);
  if (stillOnline) {
    console.log(`[Socket] Socket ${socket.id} closed, player ${playerId} still online via other connection`);
    return;
  }

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
    `SELECT tbl.code FROM tournaments t JOIN tables tbl ON t.table_id = tbl.id WHERE t.id = $1`,
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
  // 幂等：已激活（倒计时在跑）/ 已开赛 / 正在激活或开赛中的重复触发一律忽略
  if (activatingTournaments.has(tournamentId) || countdownTimers.has(tournamentId)
      || activeGames.has(tournamentId) || startingTournaments.has(tournamentId)) {
    console.log(`[Socket] Tournament ${tournamentId} already activated, skip duplicate activation`);
    return;
  }
  activatingTournaments.add(tournamentId);
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
    activatingTournaments.delete(tournamentId);
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

      // 陪玩机制：按真人数决定——0 真人取消赛事（触发退款）；
      // ≥1 真人即开赛，startTournament 内部会用陪玩 Bot 补足 max_players
      const result = await query(
        `SELECT COUNT(*) AS humans FROM tournament_players tp
         JOIN players p ON p.id = tp.player_id
         WHERE tp.tournament_id = $1
           AND p.is_bot IS NOT TRUE`,
        [tournamentId]
      );
      const humans = Number(result.rows[0] ? result.rows[0].humans : 0);
      if (humans === 0) {
        await cancelTournament(tournamentId, 'no human players');
      } else {
        await startTournament(tournamentId);
      }
    }
  }, 1000);

  countdownTimers.set(tournamentId, timer);
}

// ============================================================
// 永久测试房间：is_test 牌桌永远保持一场免费可入座的测试赛
// （结束/取消后自动开下一场；服务启动自检补齐）
// ============================================================
async function ensureTestRoomTournament(tableCode) {
  try {
    const tb = await query('SELECT * FROM tables WHERE code = $1 AND is_test = true', [tableCode]);
    const table = tb.rows[0];
    if (!table) return;
    // 清理僵死赛事：未支付 pending 超过 15 分钟的视为废弃（否则永远挡住测试房间）
    const stale = await query(
      `UPDATE tournaments SET status = 'cancelled'
       WHERE table_id = $1 AND status = 'pending' AND created_at < NOW() - INTERVAL '15 minutes'
       RETURNING id`,
      [table.id]
    );
    if (stale.rows.length) {
      await query(
        `UPDATE orders SET status = 'cancelled' WHERE status = 'pending' AND tournament_id = ANY($1)`,
        [stale.rows.map(r => r.id)]
      );
      console.log(`[Socket] Test room ${tableCode}: cleaned ${stale.rows.length} stale pending tournament(s)`);
    }
    // 已有 pending/registering/started 赛事则不动
    const act = await query(
      `SELECT id FROM tournaments WHERE table_id = $1 AND status IN ('pending','registering','started') LIMIT 1`,
      [table.id]
    );
    if (act.rows.length) return;
    const displayCode = 'T' + Math.random().toString(36).slice(2, 7).toUpperCase();
    const ins = await query(
      `INSERT INTO tournaments (display_code, table_id, launch_fee, max_players, start_chips,
        start_blind, blind_interval, wait_countdown, action_timeout, status)
       VALUES ($1, $2, 0, $3, 1000, 10, 300, $4, 30, 'pending') RETURNING id`,
      [displayCode, table.id, table.max_players || 6, SNG_DEFAULTS.WAIT_COUNTDOWN]
    );
    const tid = ins.rows[0].id;
    console.log(`[Socket] Test room ${tableCode}: new free tournament ${displayCode} (${tid})`);
    await activateTournament(tid);
  } catch (e) {
    console.error(`[Socket] Test room ${tableCode} ensure error:`, e.message);
  }
}

async function ensureAllTestRooms() {
  try {
    const r = await query('SELECT code FROM tables WHERE is_test = true', []);
    for (const row of r.rows) await ensureTestRoomTournament(row.code);
  } catch (e) {
    console.error('[Socket] Test room startup check error:', e.message);
  }
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

  // 永久测试房间：取消后自动开下一场
  try {
    const tr = await query(
      'SELECT tbl.code FROM tournaments t JOIN tables tbl ON t.table_id = tbl.id WHERE t.id = $1',
      [tournamentId]
    );
    if (tr.rows[0]) setTimeout(() => ensureTestRoomTournament(tr.rows[0].code), 5000);
  } catch (e) { /* 测试房间重开失败不影响主流程 */ }
}

// ============================================================
// 陪玩 Bot 补人：开赛前往空座位补 Bot，凑满 max_players
// 仅在已有真人（已付费/已入座）时触发；纯 Bot 局不开
// ============================================================
async function fillBotsToFull(tournamentId, tournament) {
  if (!BOT_COMPANION.ENABLED) return;
  const maxPlayers = tournament.max_players || SNG_DEFAULTS.MAX_PLAYERS;
  const pRes = await query(
    `SELECT tp.seat_index, p.nickname, p.is_bot FROM tournament_players tp
     JOIN players p ON p.id = tp.player_id WHERE tp.tournament_id = $1`,
    [tournamentId]
  );
  const humans = pRes.rows.filter(r => !r.is_bot);
  if (humans.length === 0) return; // 无真人不补（倒计时分支会取消赛事）

  const occupied = new Set(pRes.rows.map(r => r.seat_index));
  const need = maxPlayers - pRes.rows.length;
  if (need <= 0) return;

  const startChips = tournament.start_chips || SNG_DEFAULTS.START_CHIPS;
  // 同场昵称去重（含真人昵称，避免 bot 与真人撞名）
  const usedNicks = new Set(pRes.rows.map(r => r.nickname).filter(Boolean));
  let filled = 0;
  for (let seat = 0; seat < maxPlayers && filled < need; seat++) {
    if (occupied.has(seat)) continue;
    const bot = genBotIdentity(usedNicks);
    const pIns = await query(
      'INSERT INTO players (nickname, avatar, is_bot) VALUES ($1, $2, true) RETURNING id',
      [bot.nickname, bot.avatar]
    );
    await query(
      `INSERT INTO tournament_players (tournament_id, player_id, seat_index, chip_count, status)
       VALUES ($1, $2, $3, $4, 'registered')`,
      [tournamentId, pIns.rows[0].id, seat, startChips]
    );
    filled++;
  }
  if (filled > 0) {
    await query('UPDATE tournaments SET player_count = player_count + $1 WHERE id = $2', [filled, tournamentId]);
    console.log(`[Socket] Companion bots filled: +${filled} bots for tournament ${tournamentId} (${humans.length} human)`);
  }
}

// ============================================================
// 开始赛事
// ============================================================
async function startTournament(tournamentId) {
  // 幂等：引擎已在跑或正在开赛中，拒绝双开
  if (activeGames.has(tournamentId) || startingTournaments.has(tournamentId)) {
    console.log(`[Socket] Tournament ${tournamentId} already started, skip duplicate start`);
    return;
  }
  startingTournaments.add(tournamentId);
  activatingTournaments.delete(tournamentId);
  try {
    // 获取赛事和玩家
    const tResult = await query('SELECT * FROM tournaments WHERE id = $1', [tournamentId]);
    const tournament = tResult.rows[0];

    // 陪玩补人：不足 max_players 时用 Bot 补满（需在读取玩家列表之前）
    await fillBotsToFull(tournamentId, tournament);

    const pResult = await query(
      `SELECT tp.*, p.nickname, p.is_bot FROM tournament_players tp
       JOIN players p ON tp.player_id = p.id
       WHERE tp.tournament_id = $1 ORDER BY tp.seat_index`,
      [tournamentId]
    );

    const players = pResult.rows.map(p => ({
      id: p.player_id,
      seatIndex: p.seat_index,
      chipCount: p.chip_count,
      status: p.status,
      nickname: p.nickname,
      isBot: p.is_bot === true,
    }));

    // 创建 SNG 管理器
    const game = new SNGManager(tournament, players);

    // 绑定事件 → 广播到牌桌房间
    const tableResult = await query('SELECT code FROM tables WHERE id = $1', [tournament.table_id]);
    const tableCode = tableResult.rows[0].code;
    const room = `table:${tableCode}`;

    game.emit = async (event, data) => {
      // hole_cards 发给对应玩家的所有连接（agent 客户端与主人浏览器同号双开都要收到），不广播给全房间
      if (event === 'hole_cards') {
        const playerSockets = [...io.sockets.sockets.values()]
          .filter(s => s.player?.id === data.playerId);
        playerSockets.forEach(s => s.emit('hole_cards', data));
      } else {
        io.to(room).emit(event, data);
      }
      // 赛事结束时写回数据库
      if (event === 'tournament_finished') {
        try {
          const rankings = data.rankings || [];
          for (const r of rankings) {
            // rank 1 记为 winner，其余记为 eliminated（此前只更新筹码和名次，status 永远停在 waiting）
            const finalStatus = r.rank === 1 ? 'winner' : 'eliminated';
            await query('UPDATE tournament_players SET chip_count = $1, final_rank = $2, status = $3 WHERE tournament_id = $4 AND player_id = $5',
              [r.chips || 0, r.rank, finalStatus, tournamentId, r.playerId]);
          }
          await query('UPDATE tournaments SET status = $1, finished_at = NOW() WHERE id = $2',
            [TOURNAMENT_STATUS.FINISHED, tournamentId]);
          console.log(`[Socket] Tournament ${tournament.display_code} finished, results saved`);
          activeGames.delete(tournamentId);
          // 永久测试房间：结束后 10 秒自动开下一场
          setTimeout(() => ensureTestRoomTournament(tableCode), 10000);
        } catch (e) {
          console.error('[Socket] Failed to save tournament results:', e.message);
        }
      }
    };

    activeGames.set(tournamentId, game);
    startingTournaments.delete(tournamentId);

    // 更新状态
    await query('UPDATE tournaments SET status = $1, started_at = NOW() WHERE id = $2',
      [TOURNAMENT_STATUS.STARTED, tournamentId]);

    // 开始
    game.start();

    io.to(room).emit('tournament_started', {
      tournamentId,
      displayCode: tournament.display_code,
      seats: game.getSeatsSnapshot(),
      players: players.map(p => ({ id: p.id, seatIndex: p.seat_index, chips: p.chipCount, nickname: p.nickname })),
    });

    console.log(`[Socket] Tournament ${tournament.display_code} started`);
  } catch (err) {
    startingTournaments.delete(tournamentId);
    console.error('[Socket] Start error:', err.message, err.stack);
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
  // 永久测试房间自检（等 DB 就绪）
  setTimeout(ensureAllTestRooms, 3000);
});

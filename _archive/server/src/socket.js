const { Server } = require('socket.io');
const PokerEngine = require('./game');
const db = require('./db');

const tables = new Map(); // code -> { engine, io room }

function setupSocket(io) {
  io.on('connection', (socket) => {
    console.log(`[WS] ${socket.id} connected`);

    // --- 加入牌桌 ---
    socket.on('join_table', async ({ tableCode, playerId, nickname }) => {
      let tableData = tables.get(tableCode);
      if (!tableData) {
        // 创建新牌桌
        const table = db.getTableByCode(tableCode);
        if (!table) {
          socket.emit('error', { message: '牌桌不存在' });
          return;
        }
        const engine = new PokerEngine(tableCode, {
          maxPlayers: table.max_players,
          startingChips: table.starting_chips,
          smallBlind: table.small_blind,
          bigBlind: table.big_blind,
          blindInterval: table.blind_interval,
        });
        tableData = { table, engine };
        tables.set(tableCode, tableData);
        
        // 监听引擎事件
        setupEngineEvents(engine, io, tableCode);
      }

      const { engine } = tableData;
      const result = engine.addPlayer(playerId, nickname);
      if (!result.ok) {
        socket.emit('error', { message: result.message });
        return;
      }

      // 加入 Socket.io 房间
      socket.join(`table:${tableCode}`);
      socket.data.tableCode = tableCode;
      socket.data.playerId = playerId;

      // 设置玩家类型
      socket.data.role = socket.handshake.query.role || 'player';

      // 发送当前牌桌状态
      socket.emit('table_joined', {
        tableCode,
        seatIndex: result.seatIndex,
        tableState: engine.getTableState(),
        yourHand: engine.getPlayer(playerId)?.hand.map(c => c.code) || [],
      });

      // 如果人数 >= 2 且游戏未开始，自动开局
      if (engine.getActivePlayers().length >= 2 && engine.status === 'waiting') {
        setTimeout(() => engine.startNewHand(), 2000);
      }
    });

    // --- 玩家行动 ---
    socket.on('player_action', ({ action, amount }) => {
      const { tableCode, playerId } = socket.data;
      if (!tableCode || !playerId) return;

      const tableData = tables.get(tableCode);
      if (!tableData) return;

      const result = tableData.engine.playerAction(playerId, action, amount);
      if (!result.ok) {
        socket.emit('action_result', { ok: false, message: result.message });
      }
    });

    // --- 断线处理 ---
    socket.on('disconnect', () => {
      const { tableCode, playerId } = socket.data;
      if (tableCode && playerId) {
        const tableData = tables.get(tableCode);
        if (tableData) {
          // 标记为暂时离开，不立即移除
          // 保持筹码但自动弃牌
          console.log(`[WS] ${playerId} disconnected from ${tableCode}`);
        }
      }
    });
  });
}

function setupEngineEvents(engine, io, tableCode) {
  const room = `table:${tableCode}`;

  engine.on('tableState', (state) => {
    io.to(room).emit('table_state', state);
  });

  engine.on('playersChanged', (players) => {
    io.to(room).emit('players_changed', players);
  });

  engine.on('handStarted', (data) => {
    io.to(room).emit('hand_started', data);
    
    // 给每个玩家发底牌（私有）
    for (const player of engine.players) {
      const socketId = findSocketId(io, room, player.id);
      if (socketId) {
        io.to(socketId).emit('your_hand', { cards: player.holeCards.map(c => c.code) });
      }
    }
  });

  engine.on('flop', (cards) => {
    io.to(room).emit('community_cards', { cards: cards.map(c => c.code), stage: 'flop' });
  });

  engine.on('turn', (cards) => {
    io.to(room).emit('community_cards', { cards: cards.map(c => c.code), stage: 'turn' });
  });

  engine.on('river', (cards) => {
    io.to(room).emit('community_cards', { cards: cards.map(c => c.code), stage: 'river' });
  });

  engine.on('action', (record) => {
    io.to(room).emit('player_action', record);
  });

  engine.on('showdown', (data) => {
    io.to(room).emit('showdown', data);
  });

  engine.on('handWon', (data) => {
    io.to(room).emit('hand_won', data);
  });

  engine.on('gameOver', (data) => {
    io.to(room).emit('game_over', data);
  });
}

function findSocketId(io, room, playerId) {
  const sockets = io.sockets.adapter.rooms.get(room);
  if (!sockets) return null;
  
  for (const socketId of sockets) {
    const socket = io.sockets.sockets.get(socketId);
    if (socket && socket.data.playerId === playerId) {
      return socketId;
    }
  }
  return null;
}

module.exports = { setupSocket };

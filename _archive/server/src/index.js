require('dotenv').config();
const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const cors = require('cors');
const { v4: uuid } = require('uuid');
const path = require('path');

const db = require('./db');
const auth = require('./auth');
const { setupSocket } = require('./socket');

const PORT = process.env.PORT || 3000;

// 初始化数据库
db.initDB();

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
  cors: { origin: '*', methods: ['GET', 'POST'] },
});

app.use(cors());
app.use(express.json());

// 静态文件（前端页面）
app.use(express.static(path.join(__dirname, '..', 'public')));

// --- REST API ---

// 发送验证码
app.post('/api/auth/send-code', async (req, res) => {
  const { email } = req.body;
  if (!email || !email.includes('@')) {
    return res.json({ ok: false, message: '请输入有效邮箱' });
  }
  const result = await auth.sendVerificationEmail(email);
  res.json(result);
});

// 验证码登录/注册
app.post('/api/auth/verify', async (req, res) => {
  const { email, code, nickname } = req.body;
  if (!email || !code) {
    return res.json({ ok: false, message: '缺少参数' });
  }
  const result = await auth.verifyAndLogin(email, code, nickname);
  res.json(result);
});

// 创建牌桌（酒吧管理员）
app.post('/api/table/create', (req, res) => {
  const { name, maxPlayers, smallBlind, bigBlind, startingChips } = req.body;
  const code = Math.random().toString(36).substring(2, 8).toUpperCase();
  
  const table = db.createTable(code, name || `桌${code}`, {
    maxPlayers: maxPlayers || 6,
    smallBlind: smallBlind || 10,
    bigBlind: bigBlind || 20,
    startingChips: startingChips || 2000,
  });

  res.json({
    ok: true,
    table: {
      code: table.code,
      name: table.name,
      maxPlayers: table.max_players,
      smallBlind: table.small_blind,
      bigBlind: table.big_blind,
    }
  });
});

// 扫码获取牌桌信息
app.get('/api/table/:code', (req, res) => {
  const table = db.getTableByCode(req.params.code);
  if (!table) return res.status(404).json({ ok: false, message: '牌桌不存在' });
  
  res.json({
    ok: true,
    table: {
      code: table.code,
      name: table.name,
      status: table.status,
      maxPlayers: table.max_players,
      playerCount: 0, // 可通过引擎获取
    }
  });
});

// 玩家信息
app.get('/api/player/:id', (req, res) => {
  const player = db.getPlayer(req.params.id);
  if (!player) return res.status(404).json({ ok: false, message: '玩家不存在' });
  res.json({ ok: true, player });
});

// 游戏页面
app.get('/play/:code', (req, res) => {
  res.sendFile(path.join(__dirname, '..', 'public', 'player.html'));
});

app.get('/tv/:code', (req, res) => {
  res.sendFile(path.join(__dirname, '..', 'public', 'tv.html'));
});

// --- WebSocket ---
setupSocket(io);

server.listen(PORT, () => {
  console.log(`🃏 Poker Night Server running on port ${PORT}`);
  console.log(`   http://localhost:${PORT}`);
});

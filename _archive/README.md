# Poker Night 🃏

酒吧德州扑克系统 — 扫码入桌，电视大屏显示

## 场景

酒吧单桌锦标赛，电视（Android TV/Pad 投屏）显示公共牌和玩家牌桌信息，玩家手机扫码进入牌桌。

## 架构

```
📱 Player App ──WebSocket──▶ 🎰 Seoul Server ──WebSocket──▶ 📺 TV Display
(Android Phone)              (Node.js + WS)                (Android TV/Pad)
- 邮箱验证码登录              - 扑克引擎                   - 公共牌 5张
- 底牌显示                   - 游戏房间                   - 底池/筹码
- 下注/弃牌/加注              - SQLite 数据库              - 行动高亮
- 扫码入桌                   - 邮件服务                   - 座次表
```

## 技术栈

| 层级 | 技术 |
|------|------|
| 服务端 | Node.js + Express + Socket.IO |
| 数据库 | SQLite (better-sqlite3) |
| 游戏引擎 | 纯服务端 Poker Engine |
| Web端 | 原生 HTML/CSS/JS (PWA 兼容) |
| 手机端 | 浏览器扫码即用 / Android App |
| 电视端 | 浏览器 / Android TV App |
| 认证 | 邮箱验证码 (SMTP) |

## 游戏规则

- 6人桌，无限注德州扑克
- 盲注每10分钟递增
- 起始筹码 2000
- 自动发牌，超时弃牌（30秒）

## 快速开始

### 运行 Web 版（已部署）

```
服务端: http://43.164.130.145/poker/
电视端: http://43.164.130.145/poker/tv/{桌号}
玩家端: http://43.164.130.145/poker/player/{桌号}
```

### 本地开发

```bash
cd server
npm install
# 配置 .env
echo "EMAIL_USER=your@email.com" >> .env
echo "EMAIL_PASS=your_password" >> .env
npm start
```

### 创建牌桌

```bash
curl -X POST http://localhost:3000/api/table/create \
  -H "Content-Type: application/json" \
  -d '{"name":"酒吧之夜","maxPlayers":6}'
```

## API 文档

### REST API
| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/auth/send-code` | POST | 发送邮箱验证码 |
| `/api/auth/verify` | POST | 验证登录/注册 |
| `/api/table/create` | POST | 创建牌桌 |
| `/api/table/:code` | GET | 获取牌桌信息 |

### WebSocket 事件

| 事件 | 方向 | 说明 |
|------|------|------|
| `join_table` | Client→Server | 加入牌桌 |
| `player_action` | Client→Server | fold/check/call/raise/allin |
| `your_hand` | Server→Player | 玩家底牌 |
| `table_state` | Server→All | 牌桌状态 |
| `community_cards` | Server→All | 公共牌 |
| `showdown` | Server→All | 摊牌 |
| `hand_won` | Server→All | 赢家 |

## 部署

- 服务器: 首尔 (43.164.130.145, 56ms ping)
- 端口: 3000 (Node), 80 (Nginx 反代 /poker/)
- 进程: PM2
- Nginx: WebSocket 支持

## 项目结构

```
poker-night/
├── server/
│   ├── src/
│   │   ├── index.js        # 入口 + HTTP
│   │   ├── socket.js       # WebSocket
│   │   ├── game.js         # 扑克引擎
│   │   ├── auth.js         # 邮箱认证
│   │   └── db.js           # SQLite
│   └── public/             # Web 前端
│       ├── index.html      # 官网首页
│       ├── tv.html         # TV 大屏
│       └── player.html     # 玩家端
├── player-app/             # Android 玩家端
├── tv-display/             # Android TV 端
├── h5-client/              # H5 备选
└── web-demo/               # 演示
```

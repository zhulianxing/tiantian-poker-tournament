# Poker Night — 酒吧德州扑克系统架构

## 场景
酒吧单桌锦标赛，电视大屏显示公共牌，玩家扫码手机入桌

## 系统架构

```
┌──────────────────────┐      WebSocket        ┌──────────────────────┐
│  📱 Player App       │◄──────────────────────►│  🎰 Seoul Server     │
│  (Android Phone)     │                        │  (Node.js + WS)     │
│  - 邮箱注册登录       │                        │  - Poker Engine      │
│  - 底牌显示          │                        │  - WebSocket Room    │
│  - 下注/弃牌/加注     │                        │  - Email Auth        │
│  - 扫码入桌          │                        │  - SQLite            │
└──────────────────────┘                        └──────┬───────────────┘
                                                       │ WebSocket
┌──────────────────────┐                        ┌──────▼───────────────┐
│  📺 TV Display       │◄──────────────────────►│  📱 投屏设备         │
│  (Android TV/Pad)    │                        │  (Android TV/Pad)    │
│  - 公共牌 5张        │                        │  - 六人座次表        │
│  - 底池/玩家筹码      │                        │  - 筹码动画          │
│  - 庄家/行动提示      │                        │  - 行动高亮          │
└──────────────────────┘                        └──────────────────────┘
```

## 数据流

```
[玩家操作] → WebSocket → [Server Game Engine] → 广播 → [所有客户端]
                                      │
                                      ├─ 📺 TV: 公共牌 + 底池 + 下注
                                      └─ 📱 各玩家: 自己的底牌 + 需要行动时提醒
```

## 角色分配 (v2 工作流)

```
1️⃣ 架构设计 → pool-deepseek-v4-pro (当前)
2️⃣ 服务端开发 → pool-kimi-k2.7-code-highspeed (编码专用)
3️⃣ Android App → pool-deepseek-v4-pro + Kimi K3 CLI
4️⃣ UI/UX 设计 → pool-kimi-k2.6 (多模态)
5️⃣ 测试验收 → MiMo + QClaw sub-agent
6️⃣ 部署上线 → QClaw SSH + Seoul
7️⃣ 市场推广 → pool-glm-5.2 (中文文案)
```

## 技术栈

| 层级 | 技术 | 说明 |
|------|------|------|
| Server | Node.js + Express + Socket.IO | 首尔 56ms |
| 数据库 | SQLite (better-sqlite3) | 零配置 |
| 游戏引擎 | 纯 JS (server-side) | 杜绝客户端作弊 |
| Player App | Android Kotlin + WebSocket | 扫码+底牌+操作 |
| TV App | Android Kotlin + WebSocket | 公共牌+大屏 |
| 认证 | 邮箱验证码 (SMTP) | 139邮箱 SMTP |
| 通信 | Socket.IO (双向) | 实时性要求高 |
| 部署 | PM2 + Nginx | 首尔 Docker |

## 游戏规则 (单桌锦标赛)

- 6人桌，无限注德州扑克
- 盲注递增 (每10分钟上涨)
- 起始筹码 2000
- 当只剩 1 人时比赛结束

## API 设计

### REST API
```
POST /api/auth/send-code  发送验证码
POST /api/auth/verify     验证邮箱登录
GET  /api/table/:code     获取牌桌信息(扫码后)
```

### WebSocket Events
```
Client → Server:
  join_table { tableCode, playerId }
  player_action { action: fold/check/call/raise/bet/allin, amount }
  
Server → All:
  table_state { players, communityCards, pot, currentTurn }
  
Server → TV only:
  tv_state { seats, communityCards, pot, chipCounts, timer }
  
Server → Player only:
  your_hand { cards: [card1, card2] }
  your_turn { action, minRaise, maxRaise, pot, callAmount }
```

## 项目结构

```
poker-night/
├── server/               # Node.js 服务端
│   ├── package.json
│   ├── src/
│   │   ├── index.js     # 入口 + HTTP
│   │   ├── socket.js    # WebSocket 事件
│   │   ├── game.js      # 扑克引擎
│   │   ├── auth.js      # 邮箱认证
│   │   └── db.js        # SQLite
│   └── .env
├── player-app/           # Android 玩家端
│   ├── app/src/main/java/
│   └── build.gradle
├── tv-display/           # Android TV 端
│   ├── app/src/main/java/
│   └── build.gradle
└── web-demo/             # 用于快速验证的 Web 版
    ├── server.html
    └── client.html
```

## 部署
- 服务器: 首尔 43.164.130.145 (56ms)
- 端口: Web 3000, WebSocket 3000 (同端口)
- Nginx: 反向代理 + SSL
- 进程: PM2

# Poker Night v2 完整规划

> 基于当前单桌 SNG（Node.js + Socket.IO + SQLite）向可运营酒吧德州扑克演进。Web 优先、服务端权威。

---

## 1. 目标与范围

### 1.1 解决当前 7 大缺陷

| # | 缺陷 | v2 目标 |
|---|---|---|
| 1 | 无 seat 管理 | 6 座位生命周期：占座/离座/候补/断线保护/Sit-out |
| 2 | 无起赛条件 | 明确 buy-in、最小开局人数、倒计时、自动开局 |
| 3 | 淘汰处理不完善 | 边池(side pot)、all-in 处理、淘汰广播、名次记录 |
| 4 | 无奖励分配 | SNG 奖金结构（名次百分比），支持自定义奖池 |
| 5 | 无观战模式 | 非玩家以 spectator 角色进入，仅见公共信息 |
| 6 | 邮件只打印 | 接入真实 SMTP（保留 fallback 调试开关） |
| 7 | 无持久化 | 手牌历史、玩家行为、牌桌状态、 tournament 结果落库 |

### 1.2 设计原则

- **服务端权威**：所有牌局逻辑、计时、状态转换在 server 执行，客户端只渲染。
- **Web 优先**：player.html / tv.html / admin.html 优先完成，Android App 复用同一套 Socket 事件。
- **最小可用 → 逐步增强**：先让单桌 SNG 可稳定玩，再扩展多桌/ tournament。
- **向下兼容**：保留现有 `/poker/` 路径与 API，新能力加在 `/api/v2/*` 或新事件中。

---

## 2. 模块架构图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                               客户端层 (Web 优先)                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌─────────────────┐  │
│  │ player.html  │  │  tv.html     │  │ admin.html   │  │ Android Apps    │  │
│  │ 玩家手机/TV  │  │ 大屏公共牌   │  │ 酒吧后台     │  │ (后续复用事件)  │  │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  └────────┬────────┘  │
└─────────┼─────────────────┼─────────────────┼───────────────────┼───────────┘
          │                 │                 │                   │
          └─────────────────┴────────┬────────┴───────────────────┘
                                     │ HTTPS / WebSocket (Nginx → Node)
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              接入层 (server/src)                            │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │ index.js    │  │ socket.js   │  │ middleware  │  │ REST API /v1 + /v2  │ │
│  │ Express入口  │  │ Socket.IO   │  │ 认证/限流   │  │ 牌桌/玩家/历史      │ │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └─────────────────────┘ │
└─────────┼────────────────┼────────────────┼──────────────────────────────────┘
          │                │                │
          └────────────────┴───────┬────────┘
                                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              领域层 (server/src/engine)                       │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────┐  │
│  │ SeatManager     │  │ PokerEngine     │  │ TournamentEngine (SNG)      │  │
│  │ 座位/候补/SitOut │  │ 单圈游戏逻辑     │  │ 生命周期/淘汰/奖金/盲注调度   │  │
│  └────────┬────────┘  └────────┬────────┘  └─────────────┬───────────────┘  │
│           │                    │                         │                  │
│  ┌────────▼────────────────────▼─────────────────────────▼──────────────┐   │
│  │                        GameStateMachine                               │   │
│  │  waiting → starting → playing → showdown → between_hands → finished  │   │
│  └─────────────────────────────────┬───────────────────────────────────┘   │
└────────────────────────────────────┼────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              持久化/基础设施层                                │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │ db.js       │  │ migrations  │  │ auth.js     │  │ mailer.js           │ │
│  │ SQLite连接   │  │ 版本化Schema │  │ 邮箱验证码   │  │ SMTP + 日志 fallback │ │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.1 数据流

```
玩家操作 ──► socket.js 校验 ──► engine 处理 ──► 写 DB ──► 广播 state
                              │
                              ├─► 私密：your_hand / your_turn（单播）
                              ├─► 公共：table_state / community_cards / showdown（房间广播）
                              └─► 观战：table_state 过滤掉 holeCards
```

---

## 3. 阶段划分（4 阶段）

### 阶段一：基础底座（Foundation）
目标：座位管理、真实邮件、持久化、管理后台，让游戏可开始可结束。

### 阶段二：牌局正确性（Game Integrity）
目标：边池、all-in、淘汰、盲注结构、超时策略、断线重连。

### 阶段三：观战与体验（Spectator & UX）
目标：观战模式、TV 增强、手牌历史、玩家统计、动画与提示。

### 阶段四：运营与部署（Production）
目标：奖金分配、 tournament 管理、监控、备份、Docker/自动化部署。

---

## 4. 每阶段文件列表

### 阶段一：基础底座

#### 服务端文件
```
server/
├── src/
│   ├── index.js                  # 修改：挂载 /api/v2，静态文件路径
│   ├── config.js                 # 新增：环境配置集中（盲注、超时、SMTP）
│   ├── socket.js                 # 重写：加入 seat/spectator 分发
│   ├── middleware/
│   │   ├── authMiddleware.js     # 新增：JWT/Token 校验中间件
│   │   └── errorHandler.js       # 新增：统一错误响应
│   ├── engine/
│   │   ├── SeatManager.js        # 新增：6 座位生命周期
│   │   ├── PokerEngine.js        # 从 game.js 迁移 + 修复
│   │   └── GameStateMachine.js   # 新增：waiting/starting/playing 状态机
│   ├── services/
│   │   ├── mailer.js             # 新增：SMTP 真实发送 + fallback 日志
│   │   └── tournamentService.js  # 新增：SNG 元数据服务
│   ├── db/
│   │   ├── index.js              # 新增：从 db.js 迁移并扩展
│   │   ├── migrations/
│   │   │   ├── 001_initial.sql   # 新增：当前 schema 迁移
│   │   │   └── 002_seats_persistence.sql  # 新增：seats, hands, actions 表
│   │   └── repositories/
│   │       ├── playerRepo.js     # 新增
│   │       ├── tableRepo.js      # 新增
│   │       └── handRepo.js       # 新增
│   └── auth.js                   # 修改：接入 mailer.js，生成 token
├── tests/
│   ├── unit/
│   │   ├── SeatManager.test.js
│   │   └── PokerEngine.test.js
│   └── integration/
│       └── socket.join.test.js
└── public/
    ├── admin.html                # 新增：创建牌桌、启动/重置、查看状态
    ├── admin.js                  # 新增
    └── admin.css                 # 新增
```

#### 数据库变更（`002_seats_persistence.sql`）
```sql
CREATE TABLE seats (
  id TEXT PRIMARY KEY,
  table_id TEXT NOT NULL,
  seat_index INTEGER NOT NULL,
  player_id TEXT,
  status TEXT DEFAULT 'empty', -- empty, occupied, reserved, sitout, disconnected
  buy_in INTEGER DEFAULT 0,
  created_at TEXT DEFAULT (datetime('now')),
  updated_at TEXT DEFAULT (datetime('now')),
  UNIQUE(table_id, seat_index)
);

CREATE TABLE tournaments (
  id TEXT PRIMARY KEY,
  table_id TEXT NOT NULL,
  status TEXT DEFAULT 'registering', -- registering, running, paused, finished
  started_at TEXT,
  finished_at TEXT,
  prize_pool INTEGER DEFAULT 0,
  payouts TEXT -- JSON {1: 50, 2: 30, 3: 20}
);

CREATE TABLE hands (
  id TEXT PRIMARY KEY,
  tournament_id TEXT,
  table_id TEXT NOT NULL,
  hand_number INTEGER NOT NULL,
  button_seat INTEGER,
  sb_seat INTEGER,
  bb_seat INTEGER,
  community_cards TEXT, -- JSON
  pot INTEGER DEFAULT 0,
  started_at TEXT,
  finished_at TEXT
);

CREATE TABLE hand_actions (
  id TEXT PRIMARY KEY,
  hand_id TEXT NOT NULL,
  player_id TEXT,
  seat_index INTEGER,
  action TEXT,
  amount INTEGER,
  street TEXT, -- preflop, flop, turn, river
  created_at TEXT DEFAULT (datetime('now'))
);

CREATE TABLE hand_players (
  id TEXT PRIMARY KEY,
  hand_id TEXT NOT NULL,
  player_id TEXT NOT NULL,
  seat_index INTEGER,
  hole_cards TEXT, -- JSON, 加密或仅服务端可见
  starting_chips INTEGER,
  ending_chips INTEGER
);
```

### 阶段二：牌局正确性

#### 服务端文件
```
server/src/engine/
├── PokerEngine.js                # 修改：加入 side pot, all-in 逻辑
├── SidePotCalculator.js          # 新增：边池计算
├── BlindScheduler.js             # 新增：盲注级别表与计时
├── ActionTimer.js                # 新增：超时管理、time bank
└── ReconnectPolicy.js            # 新增：断线保护策略

server/src/services/
├── eliminationService.js         # 新增：淘汰、名次、复活（可选）
└── handRecorder.js               # 新增：记录每手到 DB

server/tests/unit/
├── SidePotCalculator.test.js
├── BlindScheduler.test.js
└── ActionTimer.test.js
```

#### 关键修复清单
- `PokerEngine.showdown` 改为按边池分别比牌。
- `advanceTurn` 跳过 all-in 玩家，但当需要开牌时正确触发。
- 新 street 时清除 `currentBet` 但保留 all-in 玩家的 `bet` 用于边池计算。
- 支持玩家买码重入？SNG 模式下不支持，MTT 后续再说。

### 阶段三：观战与体验

#### 服务端文件
```
server/src/
├── socket.js                     # 修改：区分 player/spectator/admin 角色
├── engine/
│   └── SpectatorStateFilter.js   # 新增：过滤公共/私有字段
└── services/
    └── statsService.js           # 新增：VPIP、PFR、M-ratio 等统计

server/public/
├── tv.html                       # 重写：6 座位布局、盲注计时、动画
├── tv.js                         # 新增
├── tv.css                        # 新增
├── player.html                   # 修改：操作 UX、time bank 显示
├── player.js                     # 新增：从 html 分离
├── spectator.html                # 新增
├── spectator.js                  # 新增
└── components/
    ├── Seat.js                   # 新增：Web Component 或模板
    ├── Card.js                   # 新增
    └── ChipStack.js              # 新增
```

### 阶段四：运营与部署

#### 服务端文件
```
server/src/
├── services/
│   ├── payoutService.js          # 新增：奖金计算与确认
│   ├── auditService.js           # 新增：关键操作日志
│   └── healthService.js          # 新增：/health 端点
├── routes/
│   ├── api.js                    # 新增：REST 路由汇总
│   └── adminApi.js               # 新增：管理员接口
├── jobs/
│   └── backupJob.js              # 新增：SQLite 定时备份
└── index.js                      # 修改：挂载 /health、/metrics

server/
├── ecosystem.config.js           # 新增：PM2 配置
├── Dockerfile                    # 新增
├── docker-compose.yml            # 新增
├── .dockerignore                 # 新增
└── scripts/
    ├── deploy.sh                 # 新增
    └── backup.sh                 # 新增

nginx/
├── poker-night.conf              # 新增：Nginx 反代 + WebSocket + SSL
└── ssl/                          # 新增：证书目录（手动放置）

.github/
└── workflows/
    └── ci.yml                    # 新增：测试 + 构建（可选）
```

---

## 5. 关键设计决策

### 5.1 座位策略（Seat Management）

#### 核心规则

| 场景 | 策略 |
|---|---|
| 座位总数 | 固定 6 座（索引 0-5），与电视大屏 6 人布局一一对应。 |
| 入座方式 | 玩家通过扫码 `/play/:code` → 选择空座 → 确认 buy-in → 占座。 |
| 满员处理 | 提供候补队列（waitlist），有人离座时按顺序递补。 |
| 离座 | SNG 进行中：不可真正离座，只能标记为 `sitout`；下一手自动弃牌。 |
| 断线 | 保留座位 5 分钟，状态 `disconnected`；当前手超时弃牌；重连后恢复 `occupied`。 |
| 淘汰 | 筹码为 0 时状态 `busted`，释放座位，记录名次。 |
| 庄家位 | 每手按座位顺序轮转，跳过 `busted`/`sitout`/`disconnected` 座位。 |

#### 状态机

```
empty ──入座+买入──► occupied
occupied ──主动 Sit-out──► sitout
occupied ──断线──► disconnected ──重连──► occupied
occupied ──筹码归零──► busted
sitout ──恢复──► occupied
sitout ──断线超过 5min──► busted （可配置）
```

#### 座次与 TV 布局

```
          TV 顶部（庄家位建议放 0）
    ┌─────┐     ┌─────┐     ┌─────┐
    │  0  │     │  1  │     │  2  │
    └─────┘     └─────┘     └─────┘
    ┌─────┐     ┌─────┐     ┌─────┐
    │  5  │     │  4  │     │  3  │
    └─────┘     └─────┘     └─────┘
          TV 底部（公共牌/底池居中）
```

- 前端按 `seatIndex` 渲染，不依赖数组顺序。
- 庄家按钮（Dealer Button）显示在 `dealerIndex` 座位。

### 5.2 盲注结构（Blind Structure）

#### 标准 SNG 盲注表（10 分钟/级别，起始 2000 筹码）

| Level | Small Blind | Big Blind | Ante | Duration |
|---|---|---|---|---|
| 1 | 10 | 20 | 0 | 10 min |
| 2 | 15 | 30 | 0 | 10 min |
| 3 | 25 | 50 | 0 | 10 min |
| 4 | 50 | 100 | 0 | 10 min |
| 5 | 75 | 150 | 0 | 10 min |
| 6 | 100 | 200 | 25 | 10 min |
| 7 | 150 | 300 | 25 | 10 min |
| 8 | 200 | 400 | 50 | 10 min |
| 9 | 300 | 600 | 75 | 10 min |
| 10 | 400 | 800 | 100 | 10 min |
| 11 | 500 | 1000 | 100 | 10 min |
| 12 | 600 | 1200 | 200 | 10 min |
| 13 | 800 | 1600 | 200 | 10 min |
| 14 | 1000 | 2000 | 300 | 10 min |
| 15 | 1500 | 3000 | 500 | 10 min |

#### 行为规则

- 盲注表在 `BlindScheduler` 中定义，创建牌桌时可覆盖。
- 级别在 tournament 开始后计时，**不在每手之间重置**。
- 升级时广播 `blind_level_changed`。
- 当大盲注超过平均筹码的 1/10 时，可在 admin 面板开启 "加速盲注"，缩短到 5 分钟。
- Ante 从第 6 级开始，由每个玩家每手前置投入。

#### 短桌调整

- 当人数 ≤ 3 时，可选自动降低级别增速（保持 10 min）或维持不变。
- 本方案选择**维持不变**，保持 SNG 快速结束的节奏。

### 5.3 超时策略（Action Timer & Time Bank）

#### 默认行动时间

- 常规行动：**30 秒**。
- 摊牌后下一手开始前有 **5 秒** 间隙。
- 升级/淘汰等事件可暂停计时器（admin 控制）。

#### 超时处理流程

| 次数 | 行为 |
|---|---|
| 第 1 次 | 自动弃牌（fold），前端提示 "超时弃牌"。 |
| 连续 2 次 | 自动弃牌 + 标记 `sitout`，下一手仍然自动弃牌直到手动恢复。 |
| 断线 | 立即触发 fold（若当前需行动），并进入 5 分钟 `disconnected` 保护。 |
| 全押后 | 该玩家不再需要行动，不触发超时。 |

#### Time Bank（可选，阶段三实现）

- 每位玩家初始拥有 **60 秒** time bank。
- 每次行动最多可额外使用 **30 秒**。
- 用完即止，不补充（或每级别补充 10 秒，可配置）。

#### 实现要点

- 服务端维护 `actionDeadline` 时间戳，广播给前端用于同步倒计时。
- 使用 `setTimeout` 做服务端触发；前端倒计时只用于显示，不用于判定。
- 玩家行动、新 street、摊牌都清除并重置计时器。

### 5.4 观战方案（Spectator Mode）

#### 观战入口

- URL：`/spectator/:code`
- 无需 buy-in，扫码或直接进入即可。
- 也可从 player.html 的 "仅观战" 按钮进入。

#### 观战权限

| 可见 | 不可见 |
|---|---|
| 座位/玩家昵称/筹码 | 任何玩家的底牌 |
| 公共牌、底池、当前下注 | 对手手牌范围/建议 |
| 当前行动玩家与倒计时 | 私密聊天 |
| 摊牌时的公开手牌 | 未摊牌玩家的手牌 |
| 盲注级别与历史牌谱 | 管理操作 |

#### 技术实现

- `socket.handshake.query.role === 'spectator'` 时加入房间，但不占座。
- `SpectatorStateFilter` 将 `table_state` 中的 `holeCards` 字段过滤掉。
- 事件与玩家共用同一 room，但通过角色过滤私有事件。
- spectator 数量无硬性上限（建议 ≤ 50，防止广播压力）。

---

## 6. 测试策略

### 6.1 测试金字塔

```
        ┌─────────────┐
        │  E2E 手动   │  手机扫码 + TV 大屏 + admin 面板联调
        │  (少量)     │
        ├─────────────┤
        │ Socket 集成 │  join_table, player_action, 断线重连
        │  (中等)     │
        ├─────────────┤
        │  单元测试   │  SeatManager, PokerEngine, SidePotCalculator,
        │  (大量)     │  BlindScheduler, ActionTimer, 手牌评估
        └─────────────┘
```

### 6.2 单元测试重点

| 模块 | 测试用例 |
|---|---|
| `SeatManager` | 6 座占满拒绝、候补队列、断线恢复、淘汰释放 |
| `PokerEngine` | 正常一圈下注、all-in 流程、单挑、多人全押 |
| `SidePotCalculator` | 2 人 all-in 不同额度、主池/边池分配、多人边池 |
| `BlindScheduler` | 级别递增、ante 生效、自定义表覆盖 |
| `evaluateHand` | 同花顺、四条、葫芦、同花、顺子、三条、两对、一对、高牌 |
| `ActionTimer` | 超时触发 fold、time bank 扣减、手动取消 |

### 6.3 集成测试重点

- 两人以上加入后自动开局。
- 一人 all-in，另一人跟注，正确产生边池。
- 断线后重连，状态同步正确。
- 盲注升级时前后端显示一致。
- 观战者看不到底牌。

### 6.4 手动验收清单

- [ ] 创建牌桌 → 扫码 → 占座 → 买入 → 开局。
- [ ] 完整玩完一局 SNG（6 人 → 1 人），奖金分配正确。
- [ ] TV 大屏正确显示 6 座位、公共牌、底池、倒计时。
- [ ] 手机端弃牌/跟注/加注/All-in 正常。
- [ ] 观战模式看不到任何底牌。
- [ ] 断线 30 秒后重连，筹码和座位保留。
- [ ] 邮件验证码 5 分钟内真实送达。

---

## 7. 部署步骤

### 7.1 环境准备（首尔 43.164.130.145）

```bash
# 1. 服务器依赖
ssh root@43.164.130.145
apt update
apt install -y nodejs npm nginx sqlite3 git
npm install -g pm2

# 2. 项目目录
mkdir -p /opt/poker-night
cd /opt/poker-night
git clone <repo> .   # 或 scp 上传

# 3. 安装依赖
cd server
npm ci --production

# 4. 环境变量
cp .env.example .env
# 编辑 .env：EMAIL_USER, EMAIL_PASS, DB_PATH, JWT_SECRET, ADMIN_KEY
```

### 7.2 Nginx 配置

文件：`/etc/nginx/sites-available/poker-night`

```nginx
server {
    listen 80;
    server_name 43.164.130.145;

    location /poker/ {
        proxy_pass http://127.0.0.1:3000/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_cache_bypass $http_upgrade;
    }
}
```

启用：
```bash
ln -s /etc/nginx/sites-available/poker-night /etc/nginx/sites-enabled/
nginx -t
systemctl restart nginx
```

### 7.3 PM2 配置

文件：`server/ecosystem.config.js`

```js
module.exports = {
  apps: [{
    name: 'poker-night',
    script: './src/index.js',
    cwd: '/opt/poker-night/server',
    instances: 1,
    exec_mode: 'fork',
    watch: false,
    env: {
      NODE_ENV: 'production',
      PORT: 3000,
      DB_PATH: '/opt/poker-night/server/data/poker.db',
    },
    log_file: '/var/log/poker-night/combined.log',
    error_file: '/var/log/poker-night/error.log',
    out_file: '/var/log/poker-night/out.log',
    time: true,
    max_memory_restart: '512M',
  }],
};
```

启动：
```bash
mkdir -p /var/log/poker-night
pm2 start ecosystem.config.js
pm2 save
pm2 startup systemd
```

### 7.4 数据库初始化与备份

```bash
# 初始化（应用迁移）
cd /opt/poker-night/server
node scripts/migrate.js up

# 备份脚本写入 crontab
echo "0 4 * * * /opt/poker-night/server/scripts/backup.sh" | crontab -
```

备份脚本 `scripts/backup.sh`：
```bash
#!/bin/bash
DB=/opt/poker-night/server/data/poker.db
BACKUP_DIR=/opt/poker-night/backups
mkdir -p $BACKUP_DIR
sqlite3 $DB ".backup '$BACKUP_DIR/poker-$(date +%Y%m%d-%H%M%S).db'"
find $BACKUP_DIR -name 'poker-*.db' -mtime +7 -delete
```

### 7.5 更新流程

```bash
ssh root@43.164.130.145
cd /opt/poker-night
git pull origin main
cd server
npm ci --production
node scripts/migrate.js up
pm2 reload poker-night
```

### 7.6 健康检查

```bash
curl http://43.164.130.145/poker/health
# 期望：{"ok":true,"uptime":...,"db":"ok"}
```

---

## 8. 风险与回滚

| 风险 | 缓解措施 |
|---|---|
| Socket 状态丢失（单进程） | 阶段四前用单进程 + PM2 reload 零停机；后续如需多进程再引入 Redis Adapter。 |
| SQLite 并发写 | better-sqlite3 同步写，单 Node 进程即可满足单桌 SNG；多桌时迁移到 PostgreSQL。 |
| 邮件 SMTP 被封 | 保留 fallback 日志 + admin 面板可查看最近验证码。 |
| 盲注升级导致游戏卡顿 | 计时器独立，升级事件异步广播。 |
| 玩家恶意刷新占座 | 同一邮箱/同一 IP 限流；admin 可强制踢人。 |
| 新代码破坏旧牌桌 | 使用 `/api/v2` 与新事件命名空间，保留旧前端兼容。 |

---

## 9. 验收标准（Definition of Done）

- [ ] 阶段一：管理后台可创建/重置牌桌；玩家扫码占座买入；邮件真实发送；手牌数据落库。
- [ ] 阶段二：完整支持 all-in、边池、淘汰；盲注按表递增；超时自动 fold；断线 5 分钟内可重连。
- [ ] 阶段三：存在 spectator 角色与独立页面；TV 大屏正确渲染 6 座；手机端操作流畅。
- [ ] 阶段四：SNG 结束后按名次自动分配奖金；部署脚本一键更新；每日自动备份；health 接口正常。

---

*文档版本：v2.0*
*规划日期：2026-07-17*

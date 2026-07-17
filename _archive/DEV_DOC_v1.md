# 🃏 德州扑克之夜 — 正式开发文档 v1.0

**文档编号：** PN-DEV-001  
**日期：** 2026-07-18  
**状态：** 定稿（待审批后进入开发）  
**依据：** 用户业务规范七章 v1.0 + ai_tools_ranking_v2 模型角色分配

---

## 第一部分：业务规范确认（定稿）

### 1.1 核心权责：首尔服务器为唯一全量中枢

- 首尔服务器（43.164.130.145）承载 **100% 核心业务逻辑**
- 边缘节点仅做 CDN + WebSocket 转发，零业务计算
- 分账数据仅存于服务端 + 商户后台 + 运营后台，**大屏和玩家 APP 不展示财务**

### 1.2 付费发起规则

- 唯一判定：**付费扫码下单成功** = 赛事激活
- 不区分发起者身份（酒吧工作人员 or 普通玩家）
- 分账比例永久固定：**酒吧 70% / 平台 30%**
- 发起方唯一权益：解锁本场赛事、开放玩家免费入座通道
- 发起方无对局特权、无座位优先选择权

### 1.3 电视大屏（荷官）四阶段

| 阶段 | 状态 | 展示内容 |
|------|------|---------|
| 1 | 空闲待机 | 品牌视觉 + 赛事规则轮播 + 下场预告 + 双二维码（APP下载 + 赛事发起付费） |
| 2 | 赛前等待 | 赛事编号 + 参数 + 已入座玩家 + 剩余空位 + 开赛倒计时 + 双二维码保留 |
| 3 | 对局进行 | 公共牌 + 底池 + 盲注 + 当前行动玩家 + 操作倒计时 + 全座位状态 |
| 4 | 赛事结束 | 最终排行榜 + 个人战绩 → 自动切回阶段1 |

### 1.4 手机 APP（玩家端）边界

- **全程零付费**：无余额、无充值、无支付模块
- 赛前：扫码下载 + 扫码免费锁定席位
- 对局中：接收底牌 + 查看公开信息 + 提交操作（过/跟/加/弃/全下）
- 赛后：个人排名 + 对局历史
- 账户：仅注册/登录/战绩存档

### 1.5 支付分账流程

```
扫码付费 → 虎皮椒订单 → 支付成功 → 回调首尔校验
  → 自动拆分记账（70%酒吧 / 30%平台）
  → 赛事房间激活
  → 开放免费入座
```

- 退款：酒吧商户后台发起全额退款，原路退回
- 结算周期：T+7，平台对公转账 70% 收益

### 1.6 数据流向

```
首尔服务端 ← 唯一权威源头
  ↓ 推送（只读）
电视大屏 ← 纯展示，零运算
  ↓ 推送（只读+指令上传）
手机APP ← 查看+提交，零判定
```

### 1.7 设备绑定

- 大屏设备 SN 永久绑定单一酒吧
- 从根源杜绝分账归属错乱

---

## 第二部分：技术规范

### 2.1 系统架构

```
┌──────────────────────────────────────────────────────────────┐
│                      首尔服务器 (43.164.130.145)              │
│  ┌────────────┐  ┌────────────┐  ┌────────────────────────┐ │
│  │  Poker API │  │  Payment   │  │  Merchant API          │ │
│  │  (Express)  │  │  Gateway   │  │  (Express)             │ │
│  │  Port 3000  │  │  (虎皮椒)  │  │  Port 3002             │ │
│  └──────┬─────┘  └─────┬──────┘  └──────────┬─────────────┘ │
│         │              │                    │                │
│  ┌──────▼──────────────▼────────────────────▼─────────────┐ │
│  │              PostgreSQL (Port 5432)                     │ │
│  │  venues / tables / tournaments / players / orders /    │ │
│  │  settlements / game_logs / device_bindings             │ │
│  └────────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────────┐ │
│  │              Socket.IO (Port 3001)                     │ │
│  │  牌局实时推送 / 大屏状态同步 / 玩家操作通道            │ │
│  └────────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────────┐ │
│  │              PM2 进程管理                               │ │
│  │  poker-api / poker-socket / merchant-api / payment-svc │ │
│  └────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
        ↑                    ↑                      ↑
        │WebSocket           │REST+WS               │REST
        │                    │                      │
  ┌─────┴──────┐    ┌───────┴────────┐    ┌───────┴───────┐
  │  TV Display │    │  Player APP    │    │  运营/商户后台 │
  │  (Android)  │    │  (Android)     │    │  (Web)        │
  │  首尔直连    │    │  首尔直连       │    │  首尔直连     │
  └────────────┘    └────────────────┘    └───────────────┘
```

### 2.2 数据库设计（PostgreSQL）

```sql
-- 场馆表
CREATE TABLE venues (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name        VARCHAR(100) NOT NULL,          -- 酒吧名称
  address     TEXT,
  contact     VARCHAR(50),                    -- 联系人
  phone       VARCHAR(20),
  rate_plan   JSONB DEFAULT '{"platform":30,"venue":70}',
  theme       JSONB DEFAULT '{}',             -- {logo, primary_color, ...}
  status      VARCHAR(20) DEFAULT 'active',
  created_at  TIMESTAMPTZ DEFAULT NOW()
);

-- 设备绑定表（大屏 SN → 酒吧）
CREATE TABLE device_bindings (
  device_sn   VARCHAR(64) PRIMARY KEY,        -- 设备唯一标识
  venue_id    UUID NOT NULL REFERENCES venues(id),
  table_label VARCHAR(50),                    -- "吧台1号"
  bound_at    TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(venue_id, table_label)
);

-- 牌桌表
CREATE TABLE tables (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  venue_id    UUID NOT NULL REFERENCES venues(id),
  device_sn   VARCHAR(64) REFERENCES device_bindings(device_sn),
  code        VARCHAR(8) UNIQUE NOT NULL,     -- 展示用桌号 C8BBGS
  label       VARCHAR(50),                    -- "吧台1号"
  buy_in      INTEGER NOT NULL DEFAULT 25,    -- 发起费(¥)
  max_players SMALLINT DEFAULT 6,
  status      VARCHAR(20) DEFAULT 'idle',     -- idle/waiting/playing/finished
  created_at  TIMESTAMPTZ DEFAULT NOW()
);

-- 赛事表
CREATE TABLE tournaments (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  table_id    UUID NOT NULL REFERENCES tables(id),
  status      VARCHAR(20) DEFAULT 'registering', -- registering/started/finished/cancelled
  buy_in      INTEGER NOT NULL,
  prize_pool  INTEGER NOT NULL DEFAULT 0,     -- 总奖池(发起费，非抽成后)
  player_count SMALLINT DEFAULT 0,
  max_players SMALLINT DEFAULT 6,
  start_blind SMALLINT DEFAULT 10,            -- 起始小盲
  blind_interval INTEGER DEFAULT 600,         -- 盲注升级间隔(秒)
  started_at  TIMESTAMPTZ,
  finished_at TIMESTAMPTZ,
  created_at  TIMESTAMPTZ DEFAULT NOW()
);

-- 玩家表
CREATE TABLE players (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  nickname    VARCHAR(30) NOT NULL,
  phone       VARCHAR(20) UNIQUE,            -- 手机号登录
  password_hash VARCHAR(255),
  avatar      VARCHAR(10) DEFAULT '🃏',
  total_games INTEGER DEFAULT 0,
  total_wins  INTEGER DEFAULT 0,
  created_at  TIMESTAMPTZ DEFAULT NOW()
);

-- 赛事参与者表
CREATE TABLE tournament_players (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tournament_id UUID NOT NULL REFERENCES tournaments(id),
  player_id    UUID NOT NULL REFERENCES players(id),
  seat_index   SMALLINT NOT NULL,
  chip_count   INTEGER DEFAULT 1000,         -- 初始筹码
  final_rank   SMALLINT,
  joined_at    TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(tournament_id, seat_index),
  UNIQUE(tournament_id, player_id)
);

-- 订单表
CREATE TABLE orders (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tournament_id UUID REFERENCES tournaments(id),
  table_id     UUID NOT NULL REFERENCES tables(id),
  venue_id     UUID NOT NULL REFERENCES venues(id),
  player_id    UUID NOT NULL REFERENCES players(id), -- 发起方
  amount       INTEGER NOT NULL,             -- 实付金额(分)
  platform_fee INTEGER NOT NULL,             -- 平台30%(分)
  venue_income INTEGER NOT NULL,             -- 酒吧70%(分)
  status       VARCHAR(20) DEFAULT 'pending',-- pending/paid/refunded
  xunhupay_order_id VARCHAR(64),             -- 虎皮椒订单号
  paid_at      TIMESTAMPTZ,
  refunded_at  TIMESTAMPTZ,
  refund_reason TEXT,
  created_at   TIMESTAMPTZ DEFAULT NOW()
);

-- 结算台账
CREATE TABLE settlements (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  venue_id     UUID NOT NULL REFERENCES venues(id),
  period_start DATE NOT NULL,
  period_end   DATE NOT NULL,
  total_orders INTEGER DEFAULT 0,
  total_amount INTEGER DEFAULT 0,            -- 总流水(分)
  venue_share  INTEGER DEFAULT 0,            -- 酒吧应得(分)
  platform_share INTEGER DEFAULT 0,          -- 平台应得(分)
  status       VARCHAR(20) DEFAULT 'pending',-- pending/confirmed/paid
  paid_at      TIMESTAMPTZ,
  transfer_proof TEXT,                       -- 转账凭证
  created_at   TIMESTAMPTZ DEFAULT NOW()
);

-- 对局日志表
CREATE TABLE game_logs (
  id           BIGSERIAL PRIMARY KEY,
  tournament_id UUID NOT NULL REFERENCES tournaments(id),
  hand_number  INTEGER NOT NULL,
  action       JSONB NOT NULL,               -- 完整操作记录
  created_at   TIMESTAMPTZ DEFAULT NOW()
);

-- 索引
CREATE INDEX idx_tables_venue ON tables(venue_id);
CREATE INDEX idx_tournaments_table ON tournaments(table_id);
CREATE INDEX idx_tournaments_status ON tournaments(status);
CREATE INDEX idx_orders_venue ON orders(venue_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_game_logs_tournament ON game_logs(tournament_id);
CREATE INDEX idx_settlements_venue ON settlements(venue_id);
```

### 2.3 API 规范

#### Poker API (Port 3000)

| 方法 | 路径 | 说明 | 角色 |
|------|------|------|------|
| GET | `/api/v1/tables/:code` | 获取牌桌信息 | 公开 |
| GET | `/api/v1/tables/:code/status` | 获取牌桌当前状态 | 公开 |
| POST | `/api/v1/tournaments/:id/join` | 玩家免费入座 | 玩家 |
| POST | `/api/v1/tournaments/:id/action` | 提交操作（过/跟/加/弃/全下） | 玩家 |
| GET | `/api/v1/tournaments/:id/result` | 获取赛事结果 | 公开 |
| GET | `/api/v1/players/:id/history` | 玩家对局历史 | 玩家 |

#### Payment API (Port 3003)

| 方法 | 路径 | 说明 | 角色 |
|------|------|------|------|
| POST | `/api/v1/payment/create` | 创建虎皮椒订单 | 玩家(发起方) |
| POST | `/api/v1/payment/callback` | 虎皮椒异步回调 | 虎皮椒 |
| GET | `/api/v1/payment/orders/:id` | 查询订单状态 | 玩家 |
| POST | `/api/v1/payment/refund` | 发起退款 | 酒吧商户 |

#### Merchant API (Port 3002)

| 方法 | 路径 | 说明 | 角色 |
|------|------|------|------|
| POST | `/api/v1/merchant/login` | 商户登录 | 酒吧 |
| GET | `/api/v1/merchant/dashboard` | 数据看板 | 酒吧 |
| GET | `/api/v1/merchant/orders` | 订单明细 | 酒吧 |
| GET | `/api/v1/merchant/settlements` | 结算记录 | 酒吧 |
| POST | `/api/v1/merchant/settlements/:id/withdraw` | 申请提现 | 酒吧 |

#### Socket.IO 事件

| 事件名 | 方向 | 数据 | 说明 |
|--------|------|------|------|
| `table_state` | S→TV/APP | TableState JSON | 牌桌完整状态推送 |
| `hand_result` | S→TV/APP | HandResult JSON | 每手牌结果 |
| `player_hole_cards` | S→APP | Card[] | 玩家私有底牌（点对点） |
| `player_action` | APP→S | {action, amount} | 玩家操作提交 |
| `tournament_started` | S→TV/APP | Tournament JSON | 赛事开始通知 |
| `tournament_finished` | S→TV/APP | Result JSON | 赛事结束通知 |
| `blind_level_up` | S→TV/APP | {level, sb, bb} | 盲注升级通知 |

### 2.4 单桌 SNG 赛制规则

```
初始筹码：1000
起始盲注：10/20
盲注升级：每 10 分钟翻倍
  Level 1: 10/20
  Level 2: 20/40
  Level 3: 40/80
  Level 4: 80/160
  Level 5: 160/320
  ...
开赛条件：满 2-6 人（由发起方预设）+ 倒计时结束
操作时限：每手 30 秒，超时自动弃牌
淘汰条件：筹码归零
结束条件：只剩 1 人
奖励：冠军独享奖池（= 总买入 - 抽水）
```

---

## 第三部分：项目分工与角色分配

### 3.1 模型角色分配（严格执行 ai_tools_ranking_v2）

| 工作阶段 | 负责模型/工具 | 具体职责 |
|---------|--------------|---------|
| **架构设计** | Kimi Code K3 (plan mode) | 技术方案、ERD 设计、API 规范 |
| **架构审查** | Gemini 2.5 Pro (2M ctx) | 全局架构审查、安全性分析 |
| **后端核心编码** | Kimi Code K3 | 牌局引擎、支付对接、分账逻辑 |
| **后端辅助编码** | pool-deepseek-v4-pro (QClaw sub-agent) | API 端点、CRUD、工具脚本 |
| **TV Display 编码** | Kimi Code K3 → QClaw 补完编译 | Android TV APP |
| **Player APP 编码** | Kimi Code K3 → QClaw 补完编译 | Android 玩家 APP |
| **商户/运营后台** | pool-deepseek-v4-pro (QClaw) | Web 后台 |
| **UI 设计审查** | pool-kimi-k2.6 (text+image) | 设计稿分析、截图评审 |
| **测试** | MiMo Code CLI + QClaw sub-agent | 单元测试、集成测试、验收 |
| **部署** | QClaw (SSH + PM2) | 首尔服务器部署、Nginx 配置 |
| **文档** | pool-glm-5.2 (中文最佳) + QClaw | 开发文档、API 文档、用户手册 |
| **市场/SEO** | QClaw + pool-glm-5.2 | 产品页、SEO、推广文案 |

### 3.2 人工角色

| 角色 | 职责 | 决策权 |
|------|------|--------|
| **产品负责人（用户）** | 需求审批、验收标准、商业决策 | 最终审批 |
| **项目经理（QClaw）** | 任务编排、进度跟踪、子代理调度 | 执行 |
| **开发执行（Kimi K3）** | 核心代码编写、架构落地 | 技术方案 |
| **测试执行（MiMo + QClaw）** | 测试用例、执行、报告 | 质量把关 |

---

## 第四部分：开发阶段与任务分解

### Phase 0：项目初始化（0.5 天）

| # | 任务 | 负责模型 | 交付物 |
|---|------|---------|--------|
| 0.1 | 废弃旧代码，清理项目目录 | QClaw | clean workspace |
| 0.2 | 首尔服务器环境准备（PostgreSQL + Node.js + PM2） | QClaw (SSH) | 服务就绪 |
| 0.3 | 数据库初始化（建库 + 表结构 + 索引） | QClaw | DB schema 落地 |
| 0.4 | 项目目录结构创建 | QClaw | monorepo 骨架 |

**目录结构：**
```
poker-night/
├── server/                 # 首尔服务器后端
│   ├── poker-api/          # 牌局 API (Port 3000)
│   ├── poker-socket/       # Socket.IO (Port 3001)
│   ├── merchant-api/       # 商户后台 API (Port 3002)
│   ├── payment-svc/        # 支付服务 (Port 3003)
│   ├── shared/             # 共享模块（DB、模型、工具）
│   └── migrations/         # 数据库迁移
├── tv-display/             # Android TV APP
├── player-app/             # Android 玩家 APP
├── merchant-dashboard/     # 商户后台 Web
├── admin-dashboard/        # 运营后台 Web
├── docs/                   # 开发文档
└── deploy/                 # 部署脚本
```

### Phase 1：后端核心（3 天）

| # | 任务 | 负责模型 | 交付物 | 验收标准 |
|---|------|---------|--------|---------|
| 1.1 | PostgreSQL 数据库 schema 落地 | QClaw | migration SQL | 所有表创建成功 |
| 1.2 | 共享模块（DB连接池、模型定义、工具函数） | QClaw | shared/ 代码 | 单元测试通过 |
| 1.3 | 牌局引擎核心（Deck、Hand、Evaluate） | Kimi K3 | poker-engine/ | 52张牌洗牌、5张牌型判定 |
| 1.4 | SNG 赛事管理（创建、报名、开赛、淘汰、结算） | Kimi K3 | tournament service | 完整 SNG 流程闭环 |
| 1.5 | Socket.IO 实时推送（牌桌状态、底牌、事件） | Kimi K3 | socket service | TV/APP 收到实时数据 |
| 1.6 | 虎皮椒支付对接（创建订单、回调校验） | QClaw | payment-svc/ | 沙箱支付全流程通过 |
| 1.7 | 分账记账（70/30 自动拆分） | QClaw | settlement service | 订单→分账→台账 |
| 1.8 | 设备绑定 API（大屏 SN → 酒吧） | QClaw | device API | SN 绑定+验证 |

### Phase 2：TV Display APP（2 天）

| # | 任务 | 负责模型 | 交付物 | 验收标准 |
|---|------|---------|--------|---------|
| 2.1 | 四阶段页面框架（空闲/等待/对局/结算） | Kimi K3 | Compose UI | 四阶段切换正确 |
| 2.2 | 双二维码生成（APP下载 + 赛事发起付费） | QClaw | QR 组件 | 扫码可下载/支付 |
| 2.3 | 赛事规则轮播动画 | QClaw | 轮播组件 | 规则展示完整 |
| 2.4 | 对局展示（6座位 + 公共牌 + 底池 + 盲注） | Kimi K3 | Table UI | 牌局实时同步 |
| 2.5 | 设备 SN 注册 + 自动连接首尔 | QClaw | boot logic | 开机自动连表 |
| 2.6 | 编译 APK + 部署到服务器 | QClaw | .apk 文件 | TV 上安装运行 |

### Phase 3：Player APP（3 天）

| # | 任务 | 负责模型 | 交付物 | 验收标准 |
|---|------|---------|--------|---------|
| 3.1 | 注册/登录（手机号 + 验证码） | Kimi K3 | auth 模块 | 新用户注册成功 |
| 3.2 | 扫码入座（扫大屏二维码 → 锁定席位） | Kimi K3 | scan 模块 | 扫码后入座成功 |
| 3.3 | 底牌显示 + 操作面板（过/跟/加/弃/全下） | Kimi K3 | game UI | 操作提交到服务端 |
| 3.4 | 公开信息同步（公共牌、底池、对手状态） | QClaw | sync 模块 | 实时同步 |
| 3.5 | 赛后排名 + 历史记录 | QClaw | result 页 | 查看历史 |
| 3.6 | 编译 APK + 部署下载链接 | QClaw | .apk 文件 | 手机安装运行 |

### Phase 4：商户 + 运营后台（2 天）

| # | 任务 | 负责模型 | 交付物 | 验收标准 |
|---|------|---------|--------|---------|
| 4.1 | 商户登录 + 数据看板 | QClaw | dashboard 页 | 数据展示正确 |
| 4.2 | 订单明细 + 导出 | QClaw | orders 页 | 支持账单导出 |
| 4.3 | 结算管理 + 提现申请 | QClaw | settlement 页 | T+7 流程 |
| 4.4 | 运营后台（全局场馆/设备/订单） | QClaw | admin 页 | 全局管理 |
| 4.5 | 退款流程 | QClaw | refund 页 | 退款→冲销→原路退回 |

### Phase 5：测试验收（2 天）

| # | 任务 | 负责模型 | 交付物 | 验收标准 |
|---|------|---------|--------|---------|
| 5.1 | 后端单元测试（牌局引擎、支付、分账） | MiMo CLI | 测试报告 | 覆盖率 > 80% |
| 5.2 | 端到端集成测试（付费→开赛→对局→结算） | QClaw sub-agent | E2E 报告 | 全流程通过 |
| 5.3 | TV Display 验收（四阶段切换） | QClaw sub-agent | 验收报告 | 四阶段正确 |
| 5.4 | Player APP 验收（扫码→入座→打牌→结算） | QClaw sub-agent | 验收报告 | 全流程通过 |
| 5.5 | 安全审计（支付校验、数据隔离、权限） | Gemini 2.5 Pro | 审计报告 | 无高危漏洞 |

### Phase 6：部署上线（1 天）

| # | 任务 | 负责模型 | 交付物 | 验收标准 |
|---|------|---------|--------|---------|
| 6.1 | 首尔服务器正式部署 | QClaw (SSH) | PM2 进程 | 全部 online |
| 6.2 | Nginx 配置（HTTPS + WebSocket 代理） | QClaw | nginx.conf | HTTPS 可访问 |
| 6.3 | APK 下载页部署 | QClaw | 下载页 | 扫码可下载 |
| 6.4 | 数据库备份 cron | QClaw | crontab | 每日自动备份 |

**总工期：约 13 天（含测试和部署）**

---

## 第五部分：验收标准

### 5.1 功能验收清单

- [ ] 任意用户扫码付费 → 赛事房间激活
- [ ] 玩家免费扫码入座 → 大屏实时刷新
- [ ] 凑齐人数 → SNG 自动开赛
- [ ] 牌局过程：发牌 → 操作 → 判定 → 结算
- [ ] 大屏四阶段正确切换
- [ ] 玩家 APP 底牌私密接收
- [ ] 分账：70% 酒吧 / 30% 平台
- [ ] 商户后台可查看流水和结算
- [ ] 退款流程：发起 → 冲销 → 原路退回
- [ ] 设备 SN 绑定验证

### 5.2 安全验收

- [ ] 支付回调签名校验
- [ ] 玩家操作服务端校验（客户端不可伪造）
- [ ] 大屏纯只读，无操作接口
- [ ] 商户后台鉴权隔离
- [ ] 分账数据不暴露给前端

### 5.3 性能验收

- [ ] Socket.IO 延迟 < 500ms（中国境内 → 首尔）
- [ ] 单桌 6 人并发稳定
- [ ] 支付回调响应 < 3s
- [ ] 大屏状态切换 < 1s

---

## 第六部分：执行纪律

### 6.1 模型使用纪律

1. **Kimi Code K3** 独立运行，负责架构设计和核心编码，通过 `kimi` CLI 调用
2. **QClaw** 负责编排、辅助编码、部署、测试、文档，通过 sub-agent 并行
3. **MiMo Code** 负责测试执行，通过 `mimo` CLI 调用
4. **Gemini 2.5 Pro** 负责架构审查和安全审计，通过 QClaw provider 调用
5. 严禁跨角色使用（如用 MiMo 做架构设计，或用 QClaw 做核心牌局引擎）

### 6.2 代码管理纪律

1. 每个模块完成后立即 `git commit`
2. 分支策略：`main`（稳定）→ `dev`（开发）→ `feature/xxx`（功能分支）
3. 代码审查：核心模块由 Gemini 2.5 Pro 审查后才可合并
4. 所有 API 变更需更新文档

### 6.3 文档纪律

1. 每个 Phase 完成后产出验收报告
2. API 文档实时更新
3. 数据库变更需有 migration 文件
4. 部署操作记录写入 memory

---

## 附录：服务器资源

| 资源 | 地址 | 用途 |
|------|------|------|
| 首尔服务器 | 43.164.130.145 | 主服（API + DB + Socket） |
| 美国服务器 | 43.166.240.93 | APK 下载页 + 备份 |
| 虎皮椒 | xunhupay.com | 支付通道 |
| GitHub | zhulianxing/poker-night | 代码仓库 |

---

*文档版本 v1.0 — 2026-07-18 — 待用户审批后进入 Phase 0*

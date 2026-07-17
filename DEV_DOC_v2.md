# 🃏 德州扑克之夜 — 正式开发文档 v2.0

**文档编号：** PN-DEV-002  
**日期：** 2026-07-18  
**状态：** 定稿（待审批后进入开发）  
**依据：** 用户业务规范七章 v1.0 + ai_tools_ranking_v2 模型角色分配  
**变更：** v2.0 修复 v1.0 中 12 处逻辑缺陷

---

## v1.0 → v2.0 修订记录

| # | 问题 | 修复 |
|---|------|------|
| 1 | **「发起方」概念残留** — 业务规范明确"取消酒吧/玩家身份区分，只认付费行为"，但 DB 仍有 `player_id` 字段标注"发起方"，API 仍有"玩家(发起方)"角色 | 移除"发起方"概念，订单只记录"付费方"（ payer_id），不赋任何特权标签 |
| 2 | **玩家 APP 内嵌支付** — 1.5 写"扫码付费→APP唤起支付"，但 1.4 明确"全程零付费"，APP 无支付模块 | 付费通过微信/支付宝原生扫码完成（大屏二维码直跳支付），不经过 APP；APP 只有扫码入座功能 |
| 3 | **SNG 奖池定义矛盾** — 2.4 写"冠军独享奖池（=总买入-抽水）"，但业务规范里买入费是"赛事启动服务费"，不是每人单独付费 | 奖池=服务费金额本身（用户扫码付的就是启动费），不涉及"每人买入扣抽水"；玩家免费参赛，筹码是虚拟的 |
| 4 | **`buy_in` 字段含义错误** — tables 表 `buy_in` 标注"发起费"，但赛事是单次付费启动，不是每人付 | 改为 `launch_fee`（启动费），由酒吧/平台预设；tournaments 表记录实际收取的启动费 |
| 5 | **`prize_pool` 无意义** — 筹码是虚拟的，玩家免费参赛，不存在真实奖池 | 移除 `prize_pool`；赛事奖励规则由酒吧线下自行约定（如赠送酒水），系统不涉及真实奖金分配 |
| 6 | **退款规则不完整** — 只说"酒吧商户后台发起退款"，但付费方可能是普通玩家，他无法自己退 | 退款发起权归属：酒吧商户后台（酒吧管理员）+ 平台运营后台（平台管理员），普通玩家无退款权限 |
| 7 | **缺少赛事编号生成规则** — tournaments 表有 id(UUID) 但没给展示用编号 | 新增 `display_code`（如 `PN-20260718-001`），按日期+序号生成 |
| 8 | **缺少开赛倒计时规则** — 1.3 提到"开赛倒计时"但未定义时长和触发 | 定义：付费激活后进入等待阶段，倒计时 300 秒（5 分钟），满人即刻开赛，未满人倒计时结束也开赛（≥2人） |
| 9 | **缺少玩家断线处理** — 对局中玩家断线怎么办 | 定义：30 秒操作超时自动弃牌；断线 60 秒自动变为 sit-out（持续盲注消耗），3 局未回归自动淘汰 |
| 10 | **Socket.IO 事件缺 `seat_joined`** — 玩家入座后大屏需实时刷新 | 新增 `seat_joined` / `seat_left` 事件 |
| 11 | **缺少二维码内容规范** — 双二维码扫码后跳转到哪 | 明确：APP 下载码→`https://poker-night.clawclaw.tech/download`；付费码→`https://pay.poker-night.clawclaw.tech/launch?table=CODE&device=SN` |
| 12 | **商户后台缺少设备管理** — 酒吧需要查看自己绑了哪些设备 | Merchant API 新增设备列表/解绑接口 |

---

## 第一部分：业务规范（定稿）

### 1.1 核心权责：首尔服务器为唯一全量中枢

- 首尔服务器（43.164.130.145）承载 **100% 核心业务逻辑**
- 边缘节点仅做 CDN + WebSocket 转发，零业务计算
- 分账数据仅存于服务端 + 商户后台 + 运营后台，**大屏和玩家 APP 不展示财务**

### 1.2 付费发起规则（取消身份区分，只认付费行为）

- 赛事激活的唯一条件：**扫码支付赛事启动费成功**
- 不区分付费者身份（酒吧工作人员 or 普通玩家 or 任何人）
- 分账比例永久固定：**酒吧 70% / 平台 30%**
- 付费者唯一权益：解锁本场赛事、开放玩家免费入座通道
- 付费者无对局特权、无座位优先选择权、无特殊标识
- 付费者本人如要参赛，仍需通过 APP 扫码免费入座，与其他玩家平等

### 1.3 电视大屏（荷官）四阶段展示规范

#### 阶段 1：空闲待机（无赛事）
1. 开机欢迎、场馆品牌视觉
2. 自动轮播完整赛事说明：单桌限血赛规则、初始筹码、盲注递增、开赛触发条件、入座与行为规范
3. 下一场赛事预告：预设盲注、人数上限、预计开放时段、可预约空位
4. **双二维码固定常驻**：
   - ① APP 下载二维码（新人安装）
   - ② 赛事发起付费二维码（设备唯一绑定对应酒吧，任何人扫码即可付费发起赛事）

#### 阶段 2：赛前付费入座等待（赛事已激活）
1. 展示本场赛事编号、完整赛事参数
2. 实时公示已免费入座玩家昵称/头像、剩余空位、开赛倒计时进度条
3. 状态提示：赛事已激活，玩家扫码免费入座，满人/倒计时结束自动开赛
4. 保留双二维码，支持随时付费预约下一场赛事

**开赛触发条件**（满足任一即开赛）：
- 满人（达到 `max_players`）
- 倒计时结束且 ≥ 2 人入座

**倒计时规则**：
- 付费激活后立即开始，默认 300 秒（5 分钟）
- 倒计时结束且 < 2 人：赛事取消，自动退款给付费者

#### 阶段 3：对局进行中（荷官播报）
仅展示公开对局信息：
- 公共牌、底池（虚拟筹码数）、盲注等级
- 当前行动玩家、操作倒计时（30 秒）
- 全部座位玩家状态（待行动/弃牌/全下/淘汰）
- 数据均由首尔服务端统一推送

#### 阶段 4：赛事结束结算
1. 大屏展示本场赛事最终玩家排行榜、个人对局战绩
2. **隐藏所有订单金额、分成、平台服务费等财务数据**
3. 自动切回空闲待机（阶段 1），开启新一轮赛事发起循环
4. 首尔服务端归档：订单、支付流水、入座记录、对局日志、分账台账，数据存储 ≥ 180 天

### 1.4 手机 APP（玩家端）功能边界（全程零付费）

1. **赛前**：扫码下载 APP → 扫码免费锁定参赛席位 → 查看入座结果
2. **对局中**：接收个人私有底牌（点对点下发）→ 查看全局公开信息 → 提交操作（过/跟/加/弃/全下）→ 所有指令仅上传首尔服务端，以服务端审核结果为准
3. **赛后**：查看个人排名与对局历史记录
4. **账户体系**：仅注册、登录、个人战绩存档，**无余额、无充值、无支付模块**
5. **付费发起**：不通过 APP 完成。大屏付费二维码直接调起微信/支付宝原生支付，支付成功后服务端激活赛事

### 1.5 支付、分账、商户结算全流程

#### 付费下单流程
```
任意用户扫大屏付费二维码
  → 微信/支付宝原生支付（不经过 APP）
  → 虎皮椒生成订单（绑定设备 SN + 酒吧 ID）
  → 支付成功
  → 虎皮椒异步回调首尔服务端校验接口
  → 校验通过：自动拆分记账
    → 70% 计入酒吧商户待结算账户
    → 30% 计入平台账户
  → 赛事房间激活，开放免费入座
```

#### 退款规则
- **发起权限**：酒吧商户后台 + 平台运营后台（普通玩家/付费者无退款权限）
- **触发条件**：设备故障、服务端异常、长时间未开赛（倒计时结束 < 2 人入座）
- **执行**：订单作废 → 预存分成同步冲销 → 资金原路退回付费者账户

#### 商户结算
- **数据看板**：订单总量、流水总额、待结算冻结金额、T+7 可提现余额、客流统计、赛事数据
- **订单明细**：每笔订单支付时间、付费账号、实付金额、70% 分成金额、赛事编号、设备 SN
- **结算周期**：T+7 自动结算，平台核对账单后对公转账 70% 收益
- **提现**：商户可手动申请提现，转账凭证永久存档

### 1.6 数据流向与风控体系

```
首尔服务端 ← 唯一规则、支付、分账、数据权威源头
  ↓ 推送（只读）
电视大屏 ← 纯展示终端，零运算、零修改权限
  ↓ 推送（只读）+ ↑ 指令上传（仅操作）
手机APP ← 查看 + 提交，零本地判定权限
```

### 1.7 设备强绑定

- 大屏设备 SN 永久绑定单一酒吧
- 从根源杜绝分账归属错乱

### 1.8 玩家断线处理规则

| 场景 | 处理 |
|------|------|
| 操作超时（30 秒） | 自动弃牌（fold） |
| 网络断线 | 30 秒内每手自动弃牌；持续盲注消耗 |
| 断线 > 60 秒 | 标记为 sit-out，继续扣盲注 |
| 断线 > 3 局（未回归） | 自动淘汰（筹码归零处理） |

---

## 第二部分：技术规范

### 2.1 系统架构

```
┌──────────────────────────────────────────────────────────────┐
│                    首尔服务器 (43.164.130.145)                 │
│                                                               │
│  ┌────────────┐ ┌───────────┐ ┌──────────┐ ┌──────────────┐ │
│  │ Poker API  │ │ Socket.IO │ │ Payment  │ │ Merchant API │ │
│  │ (Express)  │ │  Server   │ │  Service │ │  (Express)  │ │
│  │ Port 3000  │ │ Port 3001 │ │ Port 3003│ │ Port 3002   │ │
│  └─────┬──────┘ └─────┬─────┘ └────┬─────┘ └──────┬───────┘ │
│        │              │            │               │         │
│  ┌─────▼──────────────▼────────────▼───────────────▼───────┐ │
│  │                   PostgreSQL (Port 5432)                 │ │
│  │  venues / device_bindings / tables / tournaments /       │ │
│  │  players / tournament_players / orders / settlements /   │ │
│  │  game_logs                                               │ │
│  └─────────────────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │                   PM2 进程管理                            │ │
│  │  poker-api / poker-socket / payment-svc / merchant-api   │ │
│  └─────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
         ↑                ↑                    ↑
         │ WebSocket      │ REST + WS          │ REST
         │                │                    │
   ┌─────┴──────┐  ┌──────┴────────┐  ┌───────┴───────┐
   │ TV Display  │  │ Player APP    │  │ 商户/运营后台  │
   │ (Android)   │  │ (Android)     │  │ (Web)         │
   │ 只读展示    │  │ 查看+指令提交  │  │ 管理结算      │
   └────────────┘  └───────────────┘  └───────────────┘

         扫码支付（不经过 APP）
   ┌──────────────────────────────────────┐
   │ 大屏付费二维码 → 微信/支付宝原生支付  │
   │ → 虎皮椒回调首尔 → 激活赛事          │
   └──────────────────────────────────────┘
```

### 2.2 数据库设计（PostgreSQL）

```sql
-- 场馆表
CREATE TABLE venues (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name        VARCHAR(100) NOT NULL,
  address     TEXT,
  contact     VARCHAR(50),
  phone       VARCHAR(20),
  rate_plan   JSONB DEFAULT '{"platform":30,"venue":70}',
  theme       JSONB DEFAULT '{}',
  status      VARCHAR(20) DEFAULT 'active',
  created_at  TIMESTAMPTZ DEFAULT NOW()
);

-- 设备绑定表（大屏 SN → 酒吧）
CREATE TABLE device_bindings (
  device_sn   VARCHAR(64) PRIMARY KEY,
  venue_id    UUID NOT NULL REFERENCES venues(id),
  table_label VARCHAR(50),
  bound_at    TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(venue_id, table_label)
);

-- 牌桌表
CREATE TABLE tables (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  venue_id      UUID NOT NULL REFERENCES venues(id),
  device_sn     VARCHAR(64) REFERENCES device_bindings(device_sn),
  code          VARCHAR(8) UNIQUE NOT NULL,    -- 展示用桌号 C8BBGS
  label         VARCHAR(50),                   -- "吧台1号"
  launch_fee    INTEGER NOT NULL DEFAULT 2500, -- 赛事启动费（分）
  max_players   SMALLINT DEFAULT 6,
  status        VARCHAR(20) DEFAULT 'idle',    -- idle/waiting/playing/finished
  created_at    TIMESTAMPTZ DEFAULT NOW()
);

-- 赛事表
CREATE TABLE tournaments (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  display_code  VARCHAR(20) UNIQUE NOT NULL,   -- PN-20260718-001
  table_id      UUID NOT NULL REFERENCES tables(id),
  status        VARCHAR(20) DEFAULT 'registering', -- registering/starting/started/finished/cancelled
  launch_fee    INTEGER NOT NULL,              -- 实际收取启动费（分）
  player_count  SMALLINT DEFAULT 0,
  max_players   SMALLINT DEFAULT 6,
  start_chips   INTEGER DEFAULT 1000,          -- 初始虚拟筹码
  start_blind   SMALLINT DEFAULT 10,
  blind_interval INTEGER DEFAULT 600,          -- 盲注升级间隔（秒）
  wait_countdown INTEGER DEFAULT 300,          -- 赛前等待倒计时（秒）
  action_timeout INTEGER DEFAULT 30,           -- 操作时限（秒）
  started_at    TIMESTAMPTZ,
  finished_at   TIMESTAMPTZ,
  created_at    TIMESTAMPTZ DEFAULT NOW()
);

-- 玩家表
CREATE TABLE players (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  nickname      VARCHAR(30) NOT NULL,
  phone         VARCHAR(20) UNIQUE,
  password_hash VARCHAR(255),
  avatar        VARCHAR(10) DEFAULT '🃏',
  total_games   INTEGER DEFAULT 0,
  total_wins    INTEGER DEFAULT 0,
  created_at    TIMESTAMPTZ DEFAULT NOW()
);

-- 赛事参与者表
CREATE TABLE tournament_players (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tournament_id UUID NOT NULL REFERENCES tournaments(id),
  player_id     UUID NOT NULL REFERENCES players(id),
  seat_index    SMALLINT NOT NULL,
  chip_count    INTEGER DEFAULT 1000,
  status        VARCHAR(20) DEFAULT 'waiting', -- waiting/playing/folded/allin/eliminated/sitout
  final_rank    SMALLINT,
  joined_at     TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(tournament_id, seat_index),
  UNIQUE(tournament_id, player_id)
);

-- 订单表
CREATE TABLE orders (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tournament_id   UUID REFERENCES tournaments(id),
  table_id        UUID NOT NULL REFERENCES tables(id),
  venue_id        UUID NOT NULL REFERENCES venues(id),
  payer_id        UUID REFERENCES players(id), -- 付费者（可能未注册玩家，可空）
  payer_identifier VARCHAR(100),               -- 微信/支付宝用户标识（openid/uid）
  amount          INTEGER NOT NULL,            -- 实付金额（分）
  platform_fee    INTEGER NOT NULL,            -- 平台 30%（分）
  venue_income    INTEGER NOT NULL,            -- 酒吧 70%（分）
  status          VARCHAR(20) DEFAULT 'pending', -- pending/paid/refunded/cancelled
  xunhupay_order_id VARCHAR(64),
  paid_at         TIMESTAMPTZ,
  refunded_at     TIMESTAMPTZ,
  refund_reason   TEXT,
  refund_initiated_by VARCHAR(20),             -- merchant/admin/auto
  created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- 结算台账
CREATE TABLE settlements (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  venue_id      UUID NOT NULL REFERENCES venues(id),
  period_start  DATE NOT NULL,
  period_end    DATE NOT NULL,
  total_orders  INTEGER DEFAULT 0,
  total_amount  INTEGER DEFAULT 0,
  venue_share   INTEGER DEFAULT 0,
  platform_share INTEGER DEFAULT 0,
  status        VARCHAR(20) DEFAULT 'pending', -- pending/confirmed/paid
  paid_at       TIMESTAMPTZ,
  transfer_proof TEXT,
  created_at    TIMESTAMPTZ DEFAULT NOW()
);

-- 对局日志表
CREATE TABLE game_logs (
  id            BIGSERIAL PRIMARY KEY,
  tournament_id UUID NOT NULL REFERENCES tournaments(id),
  hand_number   INTEGER NOT NULL,
  action        JSONB NOT NULL,
  created_at    TIMESTAMPTZ DEFAULT NOW()
);

-- 索引
CREATE INDEX idx_tables_venue ON tables(venue_id);
CREATE INDEX idx_tournaments_table ON tournaments(table_id);
CREATE INDEX idx_tournaments_status ON tournaments(status);
CREATE INDEX idx_orders_venue ON orders(venue_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_game_logs_tournament ON game_logs(tournament_id);
CREATE INDEX idx_settlements_venue ON settlements(venue_id);
CREATE INDEX idx_tournament_players_tournament ON tournament_players(tournament_id);
CREATE INDEX idx_tournament_players_player ON tournament_players(player_id);
```

### 2.3 二维码内容规范

| 二维码 | 内容 | 扫码后行为 |
|--------|------|-----------|
| APP 下载码 | `https://poker-night.clawclaw.tech/download` | 浏览器打开下载页，安装 Player APK |
| 赛事发起付费码 | `https://pay.poker-night.clawclaw.tech/launch?table=CODE&device=SN` | 微信/支付宝原生支付界面，金额=启动费 |

### 2.4 API 规范

#### Poker API (Port 3000)

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| GET | `/api/v1/tables/:code` | 获取牌桌信息 | 公开 |
| GET | `/api/v1/tables/:code/status` | 获取牌桌当前状态 | 公开 |
| POST | `/api/v1/auth/register` | 玩家注册（手机号+验证码） | 公开 |
| POST | `/api/v1/auth/login` | 玩家登录 | 公开 |
| POST | `/api/v1/tournaments/:id/join` | 玩家免费入座 | 玩家 token |
| POST | `/api/v1/tournaments/:id/action` | 提交操作 | 玩家 token |
| GET | `/api/v1/tournaments/:id/result` | 获取赛事结果 | 公开 |
| GET | `/api/v1/players/me/history` | 我的对局历史 | 玩家 token |

#### Payment Service (Port 3003)

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | `/api/v1/payment/create` | 创建虎皮椒订单（由扫码触发） | 设备 SN 验证 |
| POST | `/api/v1/payment/callback` | 虎皮椒异步回调 | 签名校验 |
| GET | `/api/v1/payment/status/:order_id` | 查询订单状态 | 公开 |
| POST | `/api/v1/payment/refund` | 发起退款 | 商户/管理员 token |

#### Merchant API (Port 3002)

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | `/api/v1/merchant/login` | 商户登录 | 公开 |
| GET | `/api/v1/merchant/dashboard` | 数据看板 | 商户 token |
| GET | `/api/v1/merchant/orders` | 订单明细 | 商户 token |
| GET | `/api/v1/merchant/devices` | 设备列表 | 商户 token |
| GET | `/api/v1/merchant/settlements` | 结算记录 | 商户 token |
| POST | `/api/v1/merchant/settlements/:id/withdraw` | 申请提现 | 商户 token |
| POST | `/api/v1/merchant/refund` | 发起退款 | 商户 token |

#### Admin API (Port 3002, 路由前缀 /admin)

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | `/api/v1/admin/login` | 管理员登录 | 公开 |
| GET | `/api/v1/admin/venues` | 场馆列表 | 管理员 token |
| POST | `/api/v1/admin/venues` | 创建场馆 | 管理员 token |
| POST | `/api/v1/admin/devices/bind` | 绑定设备到场馆 | 管理员 token |
| GET | `/api/v1/admin/orders` | 全局订单 | 管理员 token |
| POST | `/api/v1/admin/refund` | 平台发起退款 | 管理员 token |

#### Socket.IO 事件

| 事件名 | 方向 | 数据 | 说明 |
|--------|------|------|------|
| `table_state` | S→TV/APP | TableState JSON | 牌桌完整状态推送 |
| `seat_joined` | S→TV/APP | {player, seatIndex} | 玩家入座通知 |
| `seat_left` | S→TV/APP | {playerId, seatIndex} | 玩家离座通知 |
| `hole_cards` | S→APP(点对点) | {cards: [...]} | 玩家私有底牌 |
| `player_action` | APP→S | {action, amount} | 玩家操作提交 |
| `action_result` | S→TV/APP | {playerId, action, amount} | 操作结果广播 |
| `hand_result` | S→TV/APP | HandResult JSON | 每手牌结果 |
| `tournament_started` | S→TV/APP | Tournament JSON | 赛事开始 |
| `tournament_finished` | S→TV/APP | Result JSON | 赛事结束 |
| `blind_level_up` | S→TV/APP | {level, sb, bb} | 盲注升级 |
| `countdown_tick` | S→TV | {remaining} | 倒计时每秒推送 |

### 2.5 单桌 SNG 赛制规则

```
初始筹码：1000（虚拟，免费）
起始盲注：10/20
盲注升级：每 10 分钟翻倍
  Level 1:  10/20
  Level 2:  20/40
  Level 3:  40/80
  Level 4:  80/160
  Level 5:  160/320
  ...

开赛条件（满足任一）：
  - 入座人数 = max_players → 立即开赛
  - 倒计时结束 且 入座人数 ≥ 2 → 开赛
  - 倒计时结束 且 入座人数 < 2 → 赛事取消，自动退款

操作时限：30 秒/手，超时自动弃牌
淘汰条件：筹码归零
结束条件：只剩 1 人
排名：按淘汰顺序倒序

奖励：系统不涉及真实奖金分配
     酒吧可线下自行约定奖励（如赠送酒水/免单等）
     系统仅记录虚拟排名
```

### 2.6 赛事编号生成规则

```
格式：PN-YYYYMMDD-NNN
示例：PN-20260718-001
规则：每日从 001 开始递增，按首尔服务器时区 (UTC+9) 日期
```

---

## 第三部分：项目分工与角色分配

### 3.1 模型角色分配（严格执行 ai_tools_ranking_v2）

| 工作阶段 | 负责模型/工具 | 具体职责 |
|---------|--------------|---------|
| **架构设计** | Kimi Code K3 (plan mode) | 技术方案、ERD 设计、API 规范 |
| **架构审查** | Gemini 2.5 Pro (2M ctx) | 全局架构审查、安全性分析 |
| **后端核心编码** | Kimi Code K3 | 牌局引擎、SNG 赛制、Socket.IO |
| **后端辅助编码** | pool-deepseek-v4-pro (QClaw sub-agent) | API 端点、CRUD、支付对接、分账 |
| **TV Display 编码** | Kimi Code K3 → QClaw 补完编译 | Android TV APP |
| **Player APP 编码** | Kimi Code K3 → QClaw 补完编译 | Android 玩家 APP |
| **商户/运营后台** | pool-deepseek-v4-pro (QClaw) | Web 后台 |
| **UI 设计审查** | pool-kimi-k2.6 (text+image) | 设计稿分析、截图评审 |
| **测试** | MiMo Code CLI + QClaw sub-agent | 单元测试、集成测试、验收 |
| **部署** | QClaw (SSH + PM2) | 首尔服务器部署、Nginx 配置 |
| **文档** | pool-glm-5.2 + QClaw | 开发文档、API 文档、用户手册 |

### 3.2 执行纪律

1. Kimi Code K3 独立运行，负责架构设计和核心编码
2. QClaw 负责编排、辅助编码、部署、测试、文档
3. MiMo Code 负责测试执行
4. Gemini 2.5 Pro 负责架构审查和安全审计
5. **严禁跨角色使用**

---

## 第四部分：开发阶段与任务分解

### Phase 0：项目初始化（0.5 天）

| # | 任务 | 负责模型 | 交付物 |
|---|------|---------|--------|
| 0.1 | 清理旧代码，创建 monorepo 骨架 | QClaw | 目录结构 |
| 0.2 | 首尔服务器装 PostgreSQL + 环境检查 | QClaw (SSH) | 服务就绪 |
| 0.3 | 数据库建库建表 + 索引 | QClaw | DB schema |
| 0.4 | Git 仓库初始化 | QClaw | repo |

### Phase 1：后端核心（3 天）

| # | 任务 | 负责模型 | 交付物 | 验收标准 |
|---|------|---------|--------|---------|
| 1.1 | 共享模块（DB 连接池、模型定义、工具函数） | QClaw | shared/ | DB 连接成功 |
| 1.2 | 牌局引擎（Deck、HandEvaluator、筹码计算） | **Kimi K3** | poker-engine/ | 52 张牌洗牌+5 张牌型判定正确 |
| 1.3 | SNG 赛事管理（创建/报名/开赛/淘汰/结算/取消） | **Kimi K3** | tournament service | 完整 SNG 流程闭环 |
| 1.4 | Socket.IO 实时推送（11 个事件） | **Kimi K3** | socket service | TV/APP 收到实时数据 |
| 1.5 | 虎皮椒支付对接（创建订单/回调校验/退款） | QClaw | payment-svc/ | 沙箱支付全流程通过 |
| 1.6 | 分账记账（70/30 自动拆分 + 结算台账） | QClaw | settlement service | 订单→分账→台账 |
| 1.7 | 设备绑定 API + 赛事编号生成 | QClaw | device + code gen | SN 绑定+编号正确 |
| 1.8 | 玩家认证（注册/登录/token） | QClaw | auth service | 注册登录可用 |
| 1.9 | **架构审查** | **Gemini 2.5 Pro** | 审查报告 | 无高危问题 |

### Phase 2：TV Display APP（2 天）

| # | 任务 | 负责模型 | 交付物 | 验收标准 |
|---|------|---------|--------|---------|
| 2.1 | 四阶段页面框架 + 状态机 | **Kimi K3** | Compose UI | 四阶段切换正确 |
| 2.2 | 双二维码生成 + 动态内容 | QClaw | QR 组件 | 扫码可下载/支付 |
| 2.3 | 赛事规则轮播 + 待机动画 | QClaw | 轮播组件 | 规则展示完整 |
| 2.4 | 对局展示（6 座位 + 公共牌 + 底池 + 盲注） | **Kimi K3** | Table UI | 牌局实时同步 |
| 2.5 | 设备 SN 注册 + 自动连接首尔 | QClaw | boot logic | 开机自动连表 |
| 2.6 | 编译 APK + 部署 | QClaw | .apk | TV 安装运行 |

### Phase 3：Player APP（3 天）

| # | 任务 | 负责模型 | 交付物 | 验收标准 |
|---|------|---------|--------|---------|
| 3.1 | 注册/登录（手机号 + 验证码） | **Kimi K3** | auth 模块 | 注册登录成功 |
| 3.2 | 扫码入座（扫大屏二维码 → 锁定席位） | **Kimi K3** | scan 模块 | 入座成功 |
| 3.3 | 底牌显示 + 操作面板（过/跟/加/弃/全下） | **Kimi K3** | game UI | 操作提交服务端 |
| 3.4 | 公开信息同步 | QClaw | sync 模块 | 实时同步 |
| 3.5 | 赛后排名 + 历史记录 | QClaw | result 页 | 查看历史 |
| 3.6 | 编译 APK + 部署下载链接 | QClaw | .apk | 手机安装运行 |

### Phase 4：商户 + 运营后台（2 天）

| # | 任务 | 负责模型 | 交付物 | 验收标准 |
|---|------|---------|--------|---------|
| 4.1 | 商户登录 + 数据看板 | QClaw | dashboard | 数据正确 |
| 4.2 | 订单明细 + 导出 | QClaw | orders 页 | 可导出 |
| 4.3 | 设备管理（列表/绑定/解绑） | QClaw | devices 页 | 设备 CRUD |
| 4.4 | 结算管理 + 提现申请 | QClaw | settlement 页 | T+7 流程 |
| 4.5 | 退款流程 | QClaw | refund 页 | 退款→冲销→原路退回 |
| 4.6 | 运营后台（全局管理） | QClaw | admin 页 | 全局可管理 |

### Phase 5：测试验收（2 天）

| # | 任务 | 负责模型 | 交付物 | 验收标准 |
|---|------|---------|--------|---------|
| 5.1 | 后端单元测试 | **MiMo CLI** | 测试报告 | 覆盖率 > 80% |
| 5.2 | E2E 集成测试 | QClaw sub-agent | E2E 报告 | 全流程通过 |
| 5.3 | TV Display 验收 | QClaw sub-agent | 验收报告 | 四阶段正确 |
| 5.4 | Player APP 验收 | QClaw sub-agent | 验收报告 | 全流程通过 |
| 5.5 | **安全审计** | **Gemini 2.5 Pro** | 审计报告 | 无高危 |

### Phase 6：部署上线（1 天）

| # | 任务 | 负责模型 | 交付物 | 验收标准 |
|---|------|---------|--------|---------|
| 6.1 | 首尔服务器正式部署 | QClaw (SSH) | PM2 进程 | 全部 online |
| 6.2 | Nginx 配置（HTTPS + WS 代理） | QClaw | nginx.conf | HTTPS 可访问 |
| 6.3 | APK 下载页部署 | QClaw | 下载页 | 扫码可下载 |
| 6.4 | 数据库备份 cron | QClaw | crontab | 每日备份 |

**总工期：约 13 天**

---

## 第五部分：验收标准

### 5.1 功能验收

- [ ] 任何人扫大屏付费码 → 微信/支付宝支付 → 赛事激活
- [ ] 玩家 APP 扫码免费入座 → 大屏实时刷新
- [ ] 满人或倒计时结束 → SNG 自动开赛
- [ ] 倒计时结束 < 2 人 → 赛事取消 + 自动退款
- [ ] 牌局全流程：发牌 → 操作 → 判定 → 淘汰 → 结算
- [ ] 大屏四阶段正确切换
- [ ] 玩家 APP 底牌私密接收（点对点）
- [ ] 玩家断线处理（30s 弃牌 / 60s sitout / 3 局淘汰）
- [ ] 分账：70% 酒吧 / 30% 平台（服务端私密执行）
- [ ] 商户后台：看板 + 订单 + 设备 + 结算 + 退款
- [ ] 运营后台：全局场馆/设备/订单/退款
- [ ] 设备 SN 绑定验证

### 5.2 安全验收

- [ ] 虎皮椒回调签名校验
- [ ] 玩家操作服务端校验（客户端不可伪造）
- [ ] 大屏纯只读，无操作接口
- [ ] 商户/运营后台鉴权隔离
- [ ] 分账数据不暴露给 TV/APP
- [ ] 玩家 token 鉴权 + 过期机制

### 5.3 性能验收

- [ ] Socket.IO 延迟 < 500ms（中国 → 首尔）
- [ ] 单桌 6 人并发稳定
- [ ] 支付回调响应 < 3s
- [ ] 大屏状态切换 < 1s

---

## 附录

### 服务器资源

| 资源 | 地址 | 用途 |
|------|------|------|
| 首尔服务器 | 43.164.130.145 | 主服（API + DB + Socket） |
| 美国服务器 | 43.166.240.93 | APK 下载页 + 备份 |
| 虎皮椒 | xunhupay.com | 支付通道 |
| GitHub | zhulianxing/poker-night | 代码仓库 |

### 目录结构

```
poker-night/
├── server/
│   ├── shared/             # DB 连接、模型、工具
│   ├── poker-api/          # 牌局 API (3000)
│   ├── poker-socket/       # Socket.IO (3001)
│   ├── merchant-api/       # 商户+运营 API (3002)
│   ├── payment-svc/        # 支付服务 (3003)
│   ├── poker-engine/       # 牌局引擎（核心）
│   └── migrations/         # DB 迁移
├── tv-display/             # Android TV APP
├── player-app/             # Android 玩家 APP
├── merchant-dashboard/     # 商户后台 Web
├── admin-dashboard/        # 运营后台 Web
├── docs/                   # 文档
└── deploy/                 # 部署脚本
```

---

*文档版本 v2.0 — 2026-07-18 — 修复 12 处逻辑缺陷 — 待用户审批*

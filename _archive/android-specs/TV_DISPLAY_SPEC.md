# Poker Night — Android TV Display App 功能说明

> 酒吧/包间大屏电视端，实时显示牌桌状态。玩家将手机作为操作端，电视作为公共信息展示。

---

## 1. 概述

| 项目 | 值 |
|------|-----|
| 包名 | `com.pokernight.tvdisplay` |
| 最低 SDK | 24 (Android 7.0) |
| 目标 SDK | 34 (Android 14) |
| 推荐设备 | Android TV 盒子（Mi Box、NVIDIA Shield）、Fire TV、各品牌智能电视 |
| 方向 | 横屏 16:9（强制 landscape） |
| 网络 | Wi-Fi / 以太网，Socket.IO 实时通信 |
| 服务端 | `43.164.130.145:80/poker/`（首尔） |

### 1.1 使用流程

```
用户打开 App
  → 输入桌号 or 扫描二维码（摄像头扫码 / 图片导入）
  → 连接 WebSocket（/poker?room={tableCode}&role=spectator）
  → 进入大屏显示模式
  → 持续渲染牌桌状态
  → 断线自动重连
```

### 1.2 角色说明

电视端以 **spectator（观众）** 角色接入牌局。服务端发送的事件经 `SpectatorStateFilter` 过滤：**永远不发送底牌（hole cards）**，仅包含公共牌、底池、玩家状态摘要。

---

## 2. 屏幕列表

### 2.0 启动屏 / 加载（SplashScreen）

- 显示 Poker Night logo + 品牌色（深色背景，金色/酒红点缀）
- 自动检测网络状态
- 检测到已保存桌号 → 自动尝试重连
- 加载耗时 > 3s 显示「正在连接服务器...」

### 2.1 连接屏（ConnectScreen）

**功能：**
- 12 键虚拟数字键盘（0-9, 退格, 确认）—— 便于遥控器输入
- 二维码扫描按钮（调用相机扫描桌边二维码）
- 最近连接记录（最多 5 条，带时间戳）
- 已连接桌号显示 + 断开按钮

**UI 布局：**
```
┌──────────────────────────────┐
│           🃏 Poker Night         │
│                                │
│     ┌──────────────────┐      │
│     │   输入桌号:      │      │
│     │   C8BBGS         │      │
│     │   [连接] [扫码]   │      │
│     └──────────────────┘      │
│                                │
│     1  2  3                    │
│     4  5  6                    │
│     7  8  9                    │
│     ←  0  確認                │
│                                │
│  最近: F8R0GP (5分钟前)       │
└──────────────────────────────┘
```

### 2.2 牌桌主屏（TableScreen）— 核心画面

这是 App 的主界面，持续运行。

**UI 布局（横向 16:9）：**

```
┌────────────────────────────────────────────┐
│ [桌号:C8BBGS]  [盲注:10/20]  [级别:1/10]   │ ← 顶栏
│ [奖池:¥150]    [玩家:5/6]    [倒计时:8:32] │
├────────────────────────────────────────────┤
│                                            │
│   [P1]           [P2]           [P3]       │ ← 上排座位(3)
│   昵称1         昵称2         昵称3        │
│   筹码1500     筹码1200     筹码2000      │
│   状态:坐       状态:坐      状态:弃牌     │
│   ──────                                │
│        🔥 公共牌 🔥                      │
│        [A♠][K♥][Q♦][7♣][3♠]               │ ← 五张公共牌
│        ──────    底池: 320             │
│                                            │
│   [P6]           [P5]           [P4]       │ ← 下排座位(3)
│   昵称6         昵称5         昵称4        │
│   筹码900      筹码1800      筹码300      │
│   状态:All-in   状态:坐      状态:断线     │
│                                            │
├────────────────────────────────────────────┤
│  手牌历史 ▶  |  设置  |  断开             │ ← 底栏
└────────────────────────────────────────────┘
```

**各区域详细说明：**

| 区域 | 内容 | 交互 |
|------|------|------|
| **顶栏** | 桌号、盲注级别、级别序号（1/N）、奖池总奖金、在线/总座位、盲注倒计时 | 无（纯展示） |
| **上排玩家 (P1-P3)** | 头像（首字母圆图）、昵称、筹码、状态标签 | 点击可查看该玩家本局统计 |
| **公共牌区** | 5 张牌（面朝上），无牌时显示 5 个牌背占位 | 无 |
| **底池** | 当前底池筹码总数 | ALL-IN 时显示主池/边池分拆 |
| **下排玩家 (P4-P6)** | 同上排 | 同上 |
| **底栏** | 手牌历史入口、设置、断开 | 导航 |

**玩家状态标签：**
```
🟢 坐席中（waiting）
💬 行动中（acting）— 闪烁+高亮边框
✋ 弃牌（folded）
🟡 All-In
⚪ 未入座（empty）
🔴 断线（disconnected）
🏆 已淘汰（busted）
👑 当前庄家（dealer button）
```

### 2.3 手牌历史弹窗（HandHistoryOverlay）

**触发：** 点击底栏"手牌历史 ▶"

**内容：**
- 最近 20 手牌记录，倒序（最新在上）
- 每条记录：手牌编号 → 公共牌 → 各玩家行动摘要 → 胜者 + 赢额

**UI 布局：**
```
┌────────────────────────────────────┐
│  ← 手牌历史                    [关闭] │
├────────────────────────────────────┤
│ #48 | 级别3 10/20               │
│ 公共: [A♠][K♥][Q♦][7♣][3♠]       │
│ 张三: 跟注→弃牌  李四: 加注(50)→胜 │
│ 胜者: 李四 (+320)               │
├────────────────────────────────────┤
│ #47 | 级别2 15/30               │
│ 公共: [2♦][7♥][9♠][J♥][T♠]       │
│ 胜者: 王五 (+480)               │
├────────────────────────────────────┤
│ ...                              │
└────────────────────────────────────┘
```

---

## 3. 核心数据模型

### 3.1 TableState（牌桌状态）

```typescript
interface TableState {
  tableCode: string;          // e.g. "C8BBGS"
  phase: 'idle' | 'waiting' | 'playing' | 'showdown';
  level: number;              // 当前盲注级别
  maxLevel: number;           // 总级别数
  smallBlind: number;
  bigBlind: number;
  ante: number;               // 第6级起 > 0
  blindCountdown: number;     // 升级剩余秒数
  seats: PlayerSeat[];        // 6个座位
  communityCards: Card[];     // 0-5张公共牌
  mainPot: number;
  sidePots: SidePot[];
  currentPlayer: string;      // 当前行动玩家ID
  actionDeadline: number;     // 当前行动截止时间戳
  dealerSeat: number;         // 庄家座位索引
  lastHandResult: HandResult | null;
}
```

### 3.2 PlayerSeat（玩家座位）

```typescript
interface PlayerSeat {
  seatIndex: number;          // 0-5
  playerId: string | null;    // null = 空座
  nickname: string;
  chipCount: number;
  currentBet: number;         // 本轮下注额
  status: 'empty' | 'waiting' | 'acting' | 'folded' | 'allIn' | 'disconnected' | 'busted' | 'sitout';
  isDealer: boolean;
  isCurrentActor: boolean;
  lastAction?: string;        // e.g. "加注100"
  avatarColor: string;        // 座位固定色，无需头像图片
}
```

### 3.3 Card（扑克牌）

```typescript
interface Card {
  suit: 'spades' | 'hearts' | 'diamonds' | 'clubs';
  rank: '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9' | '10' | 'J' | 'Q' | 'K' | 'A';
  faceUp: boolean;
}
```

### 3.4 HandResult（手牌结果，仅 TV 端可接收——来自过滤后的 spectator 事件）

```typescript
interface HandResult {
  handNumber: number;
  winnerId: string;
  winnerNickname: string;
  winAmount: number;
  winningHand: string;        // e.g. "两对" / "顺子"
  // 注意：TV端不包含 holeCards，SpectatorStateFilter 已过滤
}
```

### 3.5 SidePot（边池）

```typescript
interface SidePot {
  amount: number;
  eligiblePlayerIds: string[];
}
```

---

## 4. Socket.IO 事件协议

### 4.1 客户端 → 服务端

| 事件 | Payload | 说明 |
|------|---------|------|
| `join_table` | `{ tableCode, role: 'spectator' }` | 以观众身份加入牌桌 |
| `leave_table` | `{}` | 断开牌桌 |
| `hand_history` | `{ tableCode, page, pageSize }` | 请求手牌历史 |
| `table_info` | `{ tableCode }` | 请求牌桌基础信息 |

### 4.2 服务端 → 客户端

| 事件 | Payload | 说明 |
|------|---------|------|
| `table_state` | `TableState` | 全量牌桌状态（不含底牌） |
| `community_flop` | `{ cards: Card[], pot: number }` | 翻牌 |
| `community_turn` | `{ card: Card, pot: number }` | 转牌 |
| `community_river` | `{ card: Card, pot: number }` | 河牌 |
| `player_action` | `{ seatIndex, action, amount, chipCount }` | 玩家行动通知 |
| `hand_result` | `HandResult` | 手牌结果 |
| `showdown` | `{ winners, pot, sidePots }` | 摊牌结果 |
| `table_message` | `{ type, text }` | 系统消息（e.g. "张三淘汰"） |

---

## 5. UI 设计规范

### 5.1 牌面渲染

- 花色使用 Unicode 符号：♠♣♥♦
- 红心/方块=红色，黑桃/梅花=黑色
- 公共牌尺寸 ≥ 80×116dp（保证远距离可看清）
- 牌间间距 8dp
- 牌背面显示复杂花纹（Poker Night logo 水印）

### 5.2 字体要求

- 牌面数字：monospace 加粗，≥ 36sp
- 筹码数字：monospace 加粗，≥ 24sp
- 昵称：sans-serif medium，≥ 20sp
- 底栏：≥ 16sp

### 5.3 色彩方案（深色主题）

| 用途 | 色值 | 说明 |
|------|------|------|
| 背景 | `#1A1A2E` | 深蓝黑 |
| 牌桌绿 | `#0D5C32` | 牌桌中心区域 |
| 卡牌白 | `#F5F5F0` | 牌面底色 |
| 金色强调 | `#FFD700` | 庄家按钮、底池数字 |
| 红色行动 | `#E53935` | 当前行动者高亮 |
| 筹码绿 | `#4CAF50` | 筹码数字 |
| 提示白 | `#FFFFFF` (87%) | 主要文字 |

---

## 6. 错误 & 边界情况

| 场景 | 表现 |
|------|------|
| **Wi-Fi 断开** | 显示「已断线，正在重连...」横幅；自动重连（指数退避 1s→2s→4s→8s...最大 30s） |
| **服务器重启** | Socket.IO 触发 `disconnect` → 同断线重连逻辑；重连后请求 `table_state` 恢复画面 |
| **桌号无效** | 显示「未找到该牌桌」toast，自动回到连接屏 |
| **牌桌已结束** | 显示「该牌局已结束」，可回到连接屏 |
| **All-in 边池** | 底池区自动分拆显示「主池: 320 | 边池1: 150」，玩家座椅底部显示 eligible |
| **长时间无操作** | 30 分钟无交互 → 屏幕保护模式（自动降低亮度，保留牌桌显示但隐藏昵称等隐私信息），点击恢复 |
| **遥控器输入桌号** | 支持 D-pad 导航，虚拟键盘可方向键操作 |
| **多 App 实例** | 同一桌号允许多台电视同时观看（酒吧多个屏幕） |

---

## 7. 技术栈推荐

| 组件 | 推荐 | 说明 |
|------|------|------|
| UI 框架 | Jetpack Compose for TV (BETA) 或 Leanback + Fragment | Compose for TV 更现代 |
| WebSocket | `io.socket:socket.io-client:2.x` | 与服务端 Socket.IO 兼容 |
| 二维码扫描 | ML Kit Barcode Scanning (Google Play Services) | 离线扫码，快速 |
| 网络检查 | `ConnectivityManager` + `NetworkCallback` | 实时网络状态监听 |
| 图片加载 | Coil (Compose) 或 Glide | 头像等 |
| 状态管理 | ViewModel + StateFlow | 单向数据流 |
| DI | Hilt 或 Koin | 依赖注入 |

---

## 8. 性能目标

| 指标 | 目标 |
|------|------|
| 冷启动时间 | ≤ 3s |
| 牌桌渲染帧率 | ≥ 30fps |
| 事件到渲染延迟 | ≤ 500ms |
| 内存使用 | ≤ 150MB（持续运行） |
| 网络重连恢复时间 | ≤ 5s（稳定网络） |
| APK 大小 | ≤ 8MB（不含架构专有 lib） |

---

## 9. 开发阶段建议

1. **P0（MVP）**：连接屏 + 牌桌主屏（座位/公共牌/底池/盲注）→ 可使用
2. **P1**：手牌历史弹窗 + All-in 边池展示 + 断线重连
3. **P2**：二维码扫码 + 屏幕保护 + 多语言 + 遥控器优化
4. **P3**：自定义牌桌背景（酒品牌logo） + 多桌切换 + 牌桌聊天

---

## 10. 与现有 Web 版 tv.html 的关系

- **tv.html** 是快速原型/测试用途
- **Android TV App** 是正式产品
- 两者使用相同的 Socket.IO 事件协议
- Android TV App 在 UX/性能/遥控器适配上全面优于浏览器版本
- 建议 tv.html 保留作为桌面/调试备用

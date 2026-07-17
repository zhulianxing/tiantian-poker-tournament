# Poker Night 服务端完善 — 任务总结

## 日期
2026-07-18

## 目标
完善 Poker Night 服务端，涵盖玩家入座/离座、操作超时自动弃牌、断线处理、退款/订单 API、设备管理 API 和 Nginx 配置。

## 修改的文件清单

### 1. `server/poker-api/index.js`
- **端口更新**：3000 → 3010
- **入座广播增强**（`POST /api/v1/tournaments/:id/join`）：广播 `seat_joined` 事件现在通过 Socket.IO 房间（`table:${code}`）定向发送，而非全局 emit
- **新增离座 API**（`POST /api/v1/tournaments/:id/leave`）：
  - JWT 鉴权
  - 事务删除 `tournament_players` 记录
  - `player_count - 1`（用 `GREATEST` 防 0 下溢）
  - 仅允许 `registering`/`started` 状态离座
  - 广播 `seat_left` 事件到牌桌房间

### 2. `server/poker-engine/sng-manager.js`
- **操作超时定时器**（任务 2）：
  - 新增 `this.actionTimer` 和 `this.actionTimeout` 属性
  - `startActionTimer(seatIndex)`：30 秒倒计时，超时自动调用 `handleAction(playerId, 'fold')`
  - `clearActionTimer()`：清除定时器
  - 在 `startNewHand` 末尾启动计时器（设置 `actingIndex` 后）
  - 在 `advanceTurn` 设置新 `actingIndex` 时重置计时器
  - 在 `advanceStage` 设置新 `actingIndex` 时重置计时器
  - 在 `handleAction` 开头清除计时器（玩家已操作）
  - 在 `finishHand` 开头清除计时器
  - 在 `finish` 方法中清除计时器
  - 发射 `action_timer_started` 事件通知前端

### 3. `server/poker-socket/index.js`
- **端口更新**：3001 → 3011
- **断线处理**（任务 3）：
  - 新增 `disconnectedPlayers` Map 追踪断线玩家
  - `handleDisconnect(socket)`：标记玩家为 `sitout`，记录断线时间
  - `handleReconnect(socket, tournamentId)`：恢复玩家为 `playing`，或淘汰超过 3 局未回归的玩家
  - `join_table` 事件增加重连检测
  - 每分钟检查断线玩家 `missedHands`，≥3 则自动淘汰
  - 导出 `handleReconnect` 供外部调用

### 4. `server/payment-svc/index.js`
- **端口更新**：3002 → 3012
- **新增 JWT 鉴权中间件**：`auth()` 函数
- **增强退款 API**（`POST /api/v1/refund`，任务 4）：
  - JWT 鉴权（商户或管理员）
  - 参数：`orderId` + `reason`（必填）
  - 验证订单状态为 `paid`
  - 调用虎皮椒退款接口
  - 事务更新：订单状态 → `refunded`，冲销 `platform_fee`/`venue_income` 为 0
  - 取消关联赛事（如未结束）
  - 通知 Socket.IO 相关方
- **新增订单列表 API**（`GET /api/v1/orders`，任务 4）：
  - JWT 鉴权
  - 商户只能看自己的订单
  - 支持分页（`page`/`limit`）
  - 支持日期筛选（`startDate`/`endDate`）
  - 支持状态筛选（`status`）
  - 返回订单总数和分页信息

### 5. `server/merchant-api/index.js`
- **端口更新**：3003 → 3013
- **退款调用路径更新**：指向新的 `/api/v1/refund` 端点，传递 `reason` 参数
- **新增设备管理 API**（任务 5，统一路径 `/api/v1/devices`）：
  - `GET /api/v1/devices`：查看场馆绑定的设备列表
  - `POST /api/v1/devices/bind`：绑定设备 SN 到场馆（含跨场馆冲突检查，自动创建牌桌）
  - `DELETE /api/v1/devices/:sn`：解绑设备（检查归属权，清除牌桌设备关联）

### 6. `deploy/nginx-poker.conf`（新建）
- Nginx 反向代理配置
- 域名：`poker.clawclaw.tech`
- API（`/api/`）→ 3010
- Socket.IO（`/socket.io/`）→ 3011（含 WebSocket upgrade）
- 商户后台（`/merchant/`）→ 3013
- 支付回调（`/pay/`）→ 3012
- 静态文件（`/download/`）→ `/opt/poker-night/public/`

## 约束遵守
- ✅ 使用 `query` 函数从 `@poker-night/shared` 导入
- ✅ 常量从 `@poker-night/shared` 的 `constants.js` 导入
- ✅ `JWT_SECRET = process.env.JWT_SECRET || 'poker-night-secret-2026'`
- ✅ 端口：poker-api=3010, poker-socket=3011, payment-svc=3012, merchant-api=3013
- ✅ 未修改现有数据库表结构
- ✅ 所有文件通过 `node --check` 语法验证

## 语法验证结果
```
poker-api/index.js:       OK
poker-socket/index.js:    OK
payment-svc/index.js:     OK
merchant-api/index.js:    OK
poker-engine/sng-manager.js: OK
```

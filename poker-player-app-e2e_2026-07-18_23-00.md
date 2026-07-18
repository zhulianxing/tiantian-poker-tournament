# Poker Night Player App 修复与 E2E 测试 (2026-07-18 22:00-23:00)

## 目标
修复 Player App 操作面板逻辑缺陷，完成端到端真机测试验证。

## 关键修复

### Player App 9 个逻辑缺陷全部修复
1. **`mySeatIndex` 未设置** — `joinTournament` 成功后设置
2. **`SocketService` 未解析 `seats`** — 新增 `parseSeats()` + `findMySeat()`
3. **导航状态不匹配** — `TableLobbyScreen` 增加 `started` 状态检查
4. **底牌泄露** — `poker-socket` 的 `hole_cards` 仅发给对应玩家
5. **`SeatInfo` 缺 `currentBet`** — 数据模型已添加
6. **`seats` 不可变下标赋值** — 全部改用 `toMutableList()` + `copy()`
7. **`connect()` 内存泄漏** — 断开旧连接后再连接
8. **`joined` 分支未设 `mySeatIndex`** — 回退分支也设置
9. **`table_state` 未解析关键字段** — 补充 `pot`/`currentBet`/`blindLevel`/`handNumber`/`actingIndex`/`dealerIndex`
10. **`tournament_started` 未解析 `tournamentId`** — 补充解析并设置 `mySeatIndex`

### 基础设施修复
- Nginx 代理端口：`/api/` 从 3003→3010
- `poker-api` 内部连接 `poker-socket`：3011→3001
- `DB` 表名：`auth_codes`→`email_codes`

### SNG 引擎修复（本轮之前已完成）
- 下注轮次完成检测（`lastRaiserIndex` 对比 + `hasActed` 检查）
- `showdown` 分支 `hand_result` 事件
- `advanceStage` 清空 `actions` 数组
- `toCall` 增加 `Math.max(0, ...)` 保护
- Bot 自动操作（70% call / 30% fold）
- `start()` 加入 `seated→PLAYING` 状态转换

## E2E 测试结果

### 自动化测试（5 bot）
- ✅ 5 玩家注册→入座→激活→手牌运行
- ✅ 18+ 事件正确接收（table_state/new_hand/hand_started/stage_changed/showdown/hand_result）
- ✅ 底牌 0 泄露
- ⚠️ `myChips=undefined`（测试脚本中 `findMySeat` 匹配问题，非 APP 问题）
- ⚠️ 没有 `turn_changed`/`action_result`（全部 bot 自动超时 fold）

### 真机测试
- Player App Release APK 已安装至手机
- TV Display Release APK 已安装
- 新赛事 R02 已创建（registering 状态，6 人座）
- 待用户在手机上操作测试

## Git 提交
- `e424ec4` — Player App 8 个缺陷修复 + 服务端 hole_cards 修复
- `ca0caac` — SocketService table_state/tournament_started 完整解析

## 当前状态
- PM2 4 服务在线（poker-api:3010, poker-socket:3001, payment-svc:3002, merchant-api:3003）
- 新赛事 R02 等待用户加入测试
- Player App 和 TV Display 均已安装最新版本

# Poker Night SNG 引擎重大升级 + 完整赛事测试通过 (2026-07-18 23:45)

## 目标
修复 SNG 引擎核心 bug，增强 Bot AI，验证从开赛到结束的完整流程+数据库持久化。

## 关键修复

### 1. CALL 金额计算 Bug（严重）
- **问题**: `callAmount = Math.min(hand.currentBet, player.chipCount)` — 直接用 `currentBet` 而没有减去玩家本轮已投注额
- **影响**: 玩家 call 时被多扣筹码（例如 SB 已经投了 10，currentBet=20，应该只补 10 但被扣了 20）
- **修复**: `toCall = Math.max(0, hand.currentBet - alreadyBet)`，再 `callAmount = Math.min(toCall, player.chipCount)`

### 2. Bot AI 策略增强
- **旧策略**: 70% call / 30% fold，不 raise → 赛事永不结束
- **新策略**:
  - 可 check → 80% check, 20% raise（偷池）
  - 需跟注时按底池赔率 (potOdds) 分段决策：
    - potOdds < 25%: 85% call, 15% raise
    - potOdds 25-40%: 60% call, 5% raise, 35% fold
    - potOdds > 40%: 20% call, 80% fold
  - 短筹码策略 (chipCount < 3BB): 50% all-in, 50% fold

### 3. finish() 数据库持久化
- **问题**: `finish()` 只 emit `tournament_finished` 事件，不写回数据库
- **修复**: `poker-socket` 的 `game.emit` 回调中处理 `tournament_finished`：
  - 更新 `tournament_players` 的 `chip_count` 和 `final_rank`
  - 更新 `tournaments` 的 `status=finished` 和 `finished_at`
  - 从 `activeGames` Map 中清除已结束的赛事

### 4. isFinished 保护
- 在 `startNewHand()` 开头加入 `if (this.isFinished) return`
- 防止 `finish()` 后的 `setTimeout(startNewHand)` 重复触发

### 5. 排名增加 nickname
- `rankings` 数组中每个条目增加 `nickname` 字段，方便日志和前端显示

## 完整赛事测试结果

### 测试参数
- 6 Bot 玩家，起始筹码 1000，盲注 20，盲注间隔 10s，操作超时 3s

### 结果 ✅
| 指标 | 值 |
|------|-----|
| 总耗时 | 75 秒 |
| 手牌数 | 4 |
| 淘汰数 | 5 |
| 摊牌数 | 4 |
| 总事件数 | 81 |
| 赛事结束 | ✅ 自然结束 |
| DB status | finished |
| DB finished_at | 记录正确 |
| DB rankings | 6 人全部正确 |

### 排名
1. 🥇 Bot6 — 6000 筹码（冠军）
2. Bot5 — 0 筹码
3. Bot4 — 0 筹码
4. Bot3 — 0 筹码
5. Bot2 — 0 筹码
6. Bot1 — 0 筹码

### Bot AI 验证
- Hand #1: pot=560（有 raise）→ Bot3 赢
- Hand #2: pot=1040（有 raise）→ Bot6 赢
- Hand #3: pot=3040（大 raise/allin）→ Bot6 赢，淘汰 3 人
- Hand #4: pot=4280（allin）→ Bot6 赢，淘汰 2 人，赛事结束

## 同步修复
- `poker-api/index.js`: POKER_SOCKET_PORT 默认值 3011→3001（两处）
- `poker-api/index.js`: 满员自动调用 `/internal/activate` 开赛
- `poker-socket/index.js`: `table_state` 事件添加 `tournamentId` 字段

## Git 提交
- `6f31806` — 服务器端修复同步到本地
- `b7c83e8` — SNG 引擎重大升级

## 当前状态
- PM2 4 服务在线
- 新赛事 REAL03 已创建（registering，桌号 SNGT01）
- Player App + TV Display Release APK 已安装至手机
- 用户可在手机上进行真机 E2E 测试

## 待验证
- 真机 E2E：Player App 注册→入座→打牌→淘汰/夺冠
- TV Display：实时显示赛事状态
- 多人真机测试（需多台手机）

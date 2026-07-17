# Poker Night Phase 0 完成记录

**日期**：2026-07-18
**服务器**：首尔 43.164.130.145

## 完成内容

### 1. 本地 Monorepo 骨架
```
poker-night/
├── server/
│   ├── shared/          # 共享模块（DB连接池、常量）
│   ├── poker-engine/    # 牌局引擎（洗牌、牌型判定、SNG管理器）
│   ├── poker-api/       # REST API（认证、牌桌、赛事、玩家）
│   ├── poker-socket/    # Socket.IO 实时服务（赛事激活、倒计时、操作广播）
│   ├── payment-svc/     # 支付服务（虎皮椒集成、回调、退款）
│   ├── merchant-api/    # 商户后台API（设备管理、订单、收入统计）
│   └── migrations/      # PostgreSQL 建表脚本
├── ecosystem.config.js  # PM2 配置
├── package.json
└── .env.example
```

### 2. 数据库（PostgreSQL 15）
- 9 张表：venues, device_bindings, tables, tournaments, players, tournament_players, orders, settlements, game_logs
- 9 个索引
- pgcrypto 扩展（UUID 生成）
- 测试场馆已插入

### 3. 牌局引擎
- `deck.js` — 52 张牌、Fisher-Yates 洗牌、发牌
- `hand-evaluator.js` — 10 级牌型判定、7选5最佳组合、A-2-3-4-5 轮子顺子
- `sng-manager.js` — SNG 赛事状态机：盲注升级、发牌、下注轮、摊牌、淘汰、结算

### 4. 单元测试
- 7/7 全通过

### 5. 服务器部署
| 服务 | 端口 | PM2 名称 | 状态 |
|------|------|----------|------|
| poker-api | 3010 | poker-api | ✅ online |
| poker-socket | 3011 | poker-socket | ✅ online |
| payment-svc | 3012 | payment-svc | ✅ online |
| merchant-api | 3013 | merchant-api | ✅ online |

### 6. 服务器环境
- OS: OpenCloudOS 9.4
- Node.js: v20.20.2 (via nvm)
- PM2: 已安装
- PostgreSQL: 15.18
- 磁盘: 84% (6.6G 可用)
- 内存: 1.9G total, ~400M available

## 待办（Phase 1+）
- [ ] Nginx 反代配置（3010→80/api, 3011→80/socket, 3013→80/merchant）
- [ ] 虎皮椒支付配置（AppID/Secret/通道URL）
- [ ] TV Display App 改造（适配新 API）
- [ ] Player App 开发
- [ ] 商户后台前端开发
- [ ] 端到端集成测试

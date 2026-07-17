# Poker Night 部署状态报告

## 时间
2026-07-18 01:15 GMT+8

## 已完成

### 服务端部署 ✅
- **服务器**: 首尔 43.164.130.145 (56ms ping)
- **端口**: 3000 (Node.js) → 80 (Nginx 反代 /poker/)
- **进程管理**: PM2 (`poker-night`), 开机自启已配置
- **Nginx**: WebSocket 升级握手支持已配置
- **数据库**: SQLite (`/opt/server/data/poker.db`)

### 公网访问 ✅
| 页面 | URL | 状态 |
|------|-----|------|
| 主页 | http://43.164.130.145/poker/ | 200 ✅ |
| 电视端 | http://43.164.130.145/poker/tv/{桌号} | 200 ✅ |
| 玩家端 | http://43.164.130.145/poker/player/{桌号} | 200 ✅ |
| API | http://43.164.130.145/poker/api/table/{桌号} | 200 ✅ |

### 功能实现
- **扑克引擎**: 完整 6 人无限注德州扑克 (game.js, 16KB)
- **Socket.IO 实时**: 底牌分发、牌桌状态、摊牌、行动广播
- **邮箱认证**: 验证码发送/校验 (开发模式打印到控制台)
- **牌桌管理**: 创建/查询/状态管理
- **Web 前端**:
  - TV 端: 大屏显示（公共牌5张、玩家筹码、底池、盲注级别）
  - 玩家端: 底牌、下注/弃牌/过牌/加注/ALL-IN、筹码显示
  - 首页: 功能展示、演示链接

### 牌桌创建
```bash
# 现有牌桌
C8BBGS - 酒吧之夜 (waiting)
F8R0GP - 吧台1号 (waiting)

# 新建牌桌
curl -X POST http://43.164.130.145/poker/api/table/create \
  -H "Content-Type: application/json" \
  -d '{"name":"桌名","maxPlayers":6}'
```

## 待处理
- **GitHub 推送**: 本地 SSH key 可认证但仓库未创建；新加坡节点有 SSH key 但未添加到 GitHub 账号
- **Android 客户端**: Player App 和 TV Display App 尚未开发
- **Docker 部署**: 尚未容器化
- **邮件服务**: 开发模式仅打印验证码，需集成真实 SMTP
- **游戏持久化**: SQLite schema 已定义但牌局历史/手牌记录未写入
- **手机适配**: 玩家端 Web 页面已适配移动端，但 Android App 可提供更好的体验

## 项目结构
```
/Users/mac/Documents/Codex/poker-night/
├── README.md          # 项目文档
├── ARCHITECTURE.md    # 架构设计
├── server/
│   ├── src/
│   │   ├── index.js   # Express 入口
│   │   ├── socket.js  # WebSocket 处理
│   │   ├── game.js    # 扑克引擎
│   │   ├── auth.js    # 邮箱认证
│   │   └── db.js      # SQLite
│   └── public/
│       ├── index.html # 主页
│       ├── tv.html    # TV 大屏
│       └── player.html # 玩家端
├── player-app/        # Android 玩家端 (TBD)
├── tv-display/        # Android TV 端 (TBD)
├── h5-client/         # H5 备选 (TBD)
└── web-demo/          # 演示 (TBD)
```

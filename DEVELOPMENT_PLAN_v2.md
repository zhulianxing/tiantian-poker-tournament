# 天天扑克锦标赛 — 完整二次开发计划 v2.0

**版本**：v2.0  
**日期**：2026-07-19  
**项目**：tiantian-poker-tournament  
**策略**：二次开发（基于现有 75% 完成度扩展）

---

## 一、项目现状基线

### 已完成资产

| 模块 | 完成度 | 核心资产 | 状态 |
|------|--------|----------|------|
| 数据库 | 100% | 9 张表 + 索引 + pgcrypto | ✅ 生产可用 |
| SNG 引擎 | 100% | sng-manager.js / hand-evaluator.js / deck.js | ✅ 测试通过 |
| 后端 API | 90% | poker-api / poker-socket / payment-svc / merchant-api | ✅ 首尔已部署 |
| Player App | 85% | Jetpack Compose / E2E 通过 / Release APK | ⚠️ UI 待美化 |
| TV Display | 85% | Compose for TV / 四阶段展示 / Release APK | ⚠️ UI 待美化 |
| 商户后台 | 60% | 基础 HTML/JS / 订单/设备/统计 | ⚠️ 功能不全 |
| 支付 | 70% | 虎皮椒代码完成 / 参数已配置 | ⚠️ 待生产测试 |
| Admin 后台 | 20% | 空目录 | ❌ 未开发 |
| 官网 | 0% | 无 | ❌ 未开始 |
| 多语言 | 0% | 无 | ❌ 未开始 |

### 服务器资源

| 地区 | IP | 角色 | 状态 |
|------|-----|------|------|
| 🇰🇷 首尔 | 43.164.130.145 | 主服（API + DB + Socket + 支付） | ✅ 运行中 |
| 🇸🇬 新加坡 | 43.160.252.162 | 东南亚边缘节点 | ⏳ 待启用 |
| 🇯🇵 东京 | 43.165.191.194 | 日韩边缘节点 | ⏳ 待启用 |
| 🇺🇸 美国 | 43.166.240.93 | 官网 + APK 下载 + DB 备份 + 欧美接入 | ⏳ 待启用 |

### 支付渠道

| 渠道 | 类型 | 状态 |
|------|------|------|
| 虎皮椒 | 微信/支付宝 | ✅ 参数已配置 |
| 币安支付 | USDT 加密货币 | ⏳ 待集成 |

---

## 二、总体开发策略

**核心原则**：不重写、渐进式、每日可交付

- **Week 1**：产品化补全（支付/部署/UI 美化）
- **Week 2**：国际化 + 多服务器（多语言/边缘节点/官网）
- **Week 3**：运营准备（Admin/监控/压测/上线）

---

## 三、Phase 1：产品化补全（Day 1-3）

### Day 1：支付闭环 + 域名 HTTPS

**目标**：用户可真实扫码支付，赛事自动激活

| 任务 | 负责 | 交付物 | 验收标准 |
|------|------|--------|----------|
| 部署最新代码到首尔 | DevOps | PM2 重启 | 4 服务 online |
| 配置 Nginx HTTPS | DevOps | nginx.conf | https://poker.clawclaw.tech 可访问 |
| SSL 证书申请 | DevOps | Let's Encrypt / 腾讯云 | 浏览器不报警告 |
| 虎皮椒生产测试 | Backend | 测试订单 | 支付 0.01 元成功激活赛事 |
| 支付回调验证 | Backend | 回调日志 | 订单状态自动变 paid |

**代码变更**：
- `ecosystem.config.js` — 已更新（含 AppID/AppKey）
- `deploy/nginx-poker.conf` — 更新 HTTPS 配置

**风险**：虎皮椒回调需要公网可访问，确保 Nginx 配置正确

---

### Day 2：美国服务器 + APK 下载

**目标**：用户可扫码下载 APP

| 任务 | 负责 | 交付物 | 验收标准 |
|------|------|--------|----------|
| 美国服务器初始化 | DevOps | Node.js + Nginx + PM2 | 服务器可 SSH |
| 部署 APK 下载页 | DevOps | download.html | 扫码可下载 APK |
| 配置 DB 备份脚本 | DevOps | backup.sh cron | 每日自动备份到美国 |
| 官网静态资源托管 | DevOps | Nginx 配置 | 静态文件可访问 |

**文件准备**：
- `player-app/app/build/outputs/apk/release/app-release.apk`
- `tv-display/app/build/outputs/apk/release/app-release.apk`
- 下载页 HTML（带二维码 + 简介）

---

### Day 3：商户后台补全

**目标**：商户可完整管理设备/订单/结算

| 任务 | 负责 | 交付物 | 验收标准 |
|------|------|--------|----------|
| 设备管理（绑定/解绑/列表） | Frontend | devices 页面 | 可增删改查 |
| 订单导出（CSV/Excel） | Frontend | export 功能 | 可下载订单报表 |
| 结算记录 + 提现申请 | Fullstack | settlements 页面 | T+7 流程完整 |
| 数据看板图表 | Frontend | ECharts 图表 | 收入/订单趋势图 |

**API 补全**：
- `GET /api/v1/merchant/stats` — 统计数据
- `GET /api/v1/merchant/orders/export` — 导出
- `POST /api/v1/merchant/settlements/:id/withdraw` — 提现

---

## 四、Phase 2：APP UI 重设计（Day 4-6）

### Day 4：设计系统 + Player App 大厅/登录

**目标**：统一视觉语言，Player App 核心页面焕新

**设计规范**：
- **主色**：深绿 `#0D5C32` + 金色 `#FFD700` + 暗黑 `#1A1A2E`
- **字体**：中文思源黑体 / 英文 Inter
- **圆角**：12dp 卡片 / 8dp 按钮
- **动效**：300ms ease-out 过渡

**Player App 重设计页面**：
1. SplashScreen — 品牌动画 + Logo 渐显
2. LoginScreen — 深色背景 + 金色输入框 + 验证码倒计时
3. TableListScreen — 卡片式牌桌列表 + 扫码按钮浮动
4. RegisterScreen — 与登录页统一风格

**技术实现**：
- 新建 `ui/theme/TiantianTheme.kt`
- 替换所有 `BgDark` / `Gold` 为设计系统变量
- 添加 Lottie 动画（启动页）

---

### Day 5：Player App 牌桌 + TV Display 四阶段

**Player App 牌桌重设计**：
1. TableGameScreen — 圆形牌桌布局 + 座位环绕
2. PokerCard — 3D 翻牌动画 + 花色渐变
3. ActionPanel — 底部弹出式操作面板 + 加注滑块
4. ResultOverlay — 胜利/淘汰全屏动效

**TV Display 重设计**：
1. IdleScreen — 品牌轮播 + 双 QR 码美化
2. WaitingScreen — 玩家头像环绕 + 倒计时进度条
3. TableScreen — 6 座位环绕 + 公共牌 3D 翻转
4. FinishedScreen — 排行榜动画 + 冠军特效

**技术实现**：
- Canvas 绘制牌桌背景
- Animatable 实现筹码飞行动画
- 座位状态脉冲动画（行动中红色呼吸灯）

---

### Day 6：商户后台现代化

**目标**：从「能用」到「好用」

**重设计内容**：
1. 登录页 — 品牌背景 + 卡片式登录框
2. Dashboard — 统计卡片 + ECharts 趋势图
3. 订单管理 — 表格 + 筛选 + 分页 + 导出
4. 设备管理 — 卡片式设备列表 + 绑定弹窗
5. 结算管理 — 时间轴式结算记录 + 提现按钮

**技术栈**：原生 HTML/CSS/JS + ECharts 5

---

## 五、Phase 3：国际化 + 多服务器（Day 7-9）

### Day 7：多语言架构

**Android i18n**：
```
player-app/app/src/main/res/
├── values/strings.xml          # 中文（默认）
├── values-en/strings.xml       # 英文
├── values-ko/strings.xml       # 韩文
└── values-ja/strings.xml       # 日文
```

**TV Display i18n**：同上

**后端 i18n**：
- `venues` 表加 `locale` 字段（默认 zh-CN）
- `players` 表加 `preferred_language` 字段
- API 错误码返回 i18n key，前端按语言渲染

**语言优先级**：
- P0：中文（简体）+ 英文
- P1：韩文 + 日文
- P2：繁体中文

---

### Day 8：多服务器部署

**新加坡节点（43.160.252.162）**：
- 安装 Nginx
- 配置反向代理到首尔 poker-api / poker-socket
- WebSocket 转发（WSS）
- 东南亚玩家接入

**东京节点（43.165.191.194）**：
- 同上，日韩玩家接入

**美国节点（43.166.240.93）**：
- 官网托管（Next.js 静态导出）
- APK 下载服务
- DB 备份接收
- 欧美玩家接入

**Nginx 配置模板**：
```nginx
# 边缘节点反向代理
location /api/ {
    proxy_pass http://43.164.130.145:3010;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
}

location /socket.io/ {
    proxy_pass http://43.164.130.145:3011;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
}
```

---

### Day 9：官网上线

**官网技术栈**：
- Next.js 14（SSG 静态导出）
- Tailwind CSS
- Framer Motion（动画）
- next-i18next（多语言）

**官网结构**：
```
tiantianpoker.com
├── /zh — 中文首页
├── /en — English
├── /ko — 한국어
├── /ja — 日本語
├── /download — APP 下载
├── /merchant — 商户合作
├── /rules — 赛事规则
└── /about — 关于我们
```

**核心页面**：
1. **首页 Hero**：3D 牌桌动画 + 「立即下载」CTA
2. **产品特色**：SNG 赛制 / 实时对战 / 酒吧场景 / 70/30 分账
3. **下载页**：Player App + TV Display 二维码 + 截图
4. **商户合作**：分账说明 + 入驻表单
5. **规则说明**：德州扑克规则 + 赛事规则 + FAQ

**部署**：美国服务器 Nginx 静态托管 + Cloudflare CDN

---

## 六、Phase 4：运营准备（Day 10-12）

### Day 10：Admin 后台开发

**Admin 功能**：
1. 全局场馆管理（增删改查）
2. 设备绑定管理
3. 全局订单监控
4. 退款审批
5. 用户管理（玩家/商户）
6. 数据报表（全平台收入/订单趋势）

**技术栈**：React + Ant Design / 或复用商户后台技术栈

**API 补全**：
- `GET /api/v1/admin/venues` — 场馆列表
- `POST /api/v1/admin/venues` — 创建场馆
- `POST /api/v1/admin/devices/bind` — 绑定设备
- `GET /api/v1/admin/orders` — 全局订单
- `POST /api/v1/admin/refund` — 平台退款

---

### Day 11：监控 + 备份 + 安全

**监控**：
- PM2 监控（进程状态/内存/CPU）
- 自定义告警（服务离线/支付失败/DB 连接失败）
- Telegram / 邮件告警

**备份**：
- 每日 02:00 首尔 DB → 美国服务器
- 保留 30 天备份
- 备份验证（每周恢复测试）

**安全**：
- JWT 过期机制（7 天 → 24 小时）
- API 限流（防刷）
- SQL 注入防护（参数化查询已做）
- XSS 防护（前端转义）

---

### Day 12：压测 + 上线准备

**压测目标**：
- 单桌 6 人并发：CPU < 50%，内存 < 500MB
- Socket.IO 延迟 < 500ms（亚洲）
- 支付回调响应 < 3s
- 同时 3 桌并发稳定

**压测工具**：
- Artillery / k6
- 模拟 6 玩家同时操作

**上线检查清单**：
- [ ] 支付真实测试通过
- [ ] HTTPS 全站启用
- [ ] 多语言切换正常
- [ ] 4 台服务器全部 online
- [ ] 备份脚本运行正常
- [ ] 监控告警测试通过
- [ ] APP Release APK 最新
- [ ] 官网可访问
- [ ] 商户后台功能完整
- [ ] Admin 后台可用

---

## 七、详细任务分解表

### 后端开发任务

| # | 任务 | 文件 | 优先级 | 工时 |
|---|------|------|--------|------|
| B1 | 虎皮椒生产测试 | payment-svc/index.js | P0 | 0.5d |
| B2 | 支付回调日志增强 | payment-svc/index.js | P0 | 0.5d |
| B3 | 商户统计 API | merchant-api/index.js | P0 | 1d |
| B4 | 订单导出 API | merchant-api/index.js | P1 | 0.5d |
| B5 | 结算/提现 API | merchant-api/index.js | P1 | 1d |
| B6 | Admin 全局 API | merchant-api/index.js | P1 | 2d |
| B7 | 多语言字段 | migrations/003_i18n.sql | P1 | 0.5d |
| B8 | API 限流中间件 | shared/rate-limit.js | P2 | 0.5d |
| B9 | 币安支付集成 | payment-svc/binance-pay.js | P2 | 2d |

### 前端开发任务

| # | 任务 | 文件 | 优先级 | 工时 |
|---|------|------|--------|------|
| F1 | 设计系统 Theme | ui/theme/TiantianTheme.kt | P0 | 0.5d |
| F2 | Player App 登录/注册 | ui/screens/LoginScreen.kt | P0 | 0.5d |
| F3 | Player App 大厅 | ui/screens/TableListScreen.kt | P0 | 0.5d |
| F4 | Player App 牌桌 | ui/screens/TableGameScreen.kt | P0 | 1d |
| F5 | TV Display 四阶段 | ui/screens/*.kt | P0 | 1d |
| F6 | 商户后台重设计 | merchant-dashboard/* | P1 | 1d |
| F7 | Admin 后台 | admin-dashboard/* | P1 | 2d |
| F8 | 官网首页 | website/pages/index.tsx | P1 | 1d |
| F9 | 官网下载页 | website/pages/download.tsx | P1 | 0.5d |
| F10 | 官网商户页 | website/pages/merchant.tsx | P2 | 0.5d |
| F11 | Android i18n | values-*/strings.xml | P1 | 1d |
| F12 | 官网 i18n | website/locales/* | P1 | 0.5d |

### DevOps 任务

| # | 任务 | 服务器 | 优先级 | 工时 |
|---|------|--------|--------|------|
| D1 | Nginx HTTPS 配置 | 首尔 | P0 | 0.5d |
| D2 | SSL 证书申请 | 首尔 | P0 | 0.5d |
| D3 | 美国服务器初始化 | 美国 | P0 | 0.5d |
| D4 | APK 下载页部署 | 美国 | P0 | 0.5d |
| D5 | 新加坡节点部署 | 新加坡 | P1 | 0.5d |
| D6 | 东京节点部署 | 东京 | P1 | 0.5d |
| D7 | DB 备份脚本 | 首尔→美国 | P1 | 0.5d |
| D8 | 监控告警配置 | 首尔 | P2 | 1d |
| D9 | Cloudflare CDN | 美国 | P2 | 0.5d |

---

## 八、里程碑与交付物

### Milestone 1：支付闭环（Day 1 结束）
- ✅ 可真实扫码支付
- ✅ 赛事自动激活
- ✅ HTTPS 可用

### Milestone 2：产品可下载（Day 2 结束）
- ✅ APK 可扫码下载
- ✅ 美国服务器运行

### Milestone 3：UI 专业化（Day 6 结束）
- ✅ Player App 新 UI
- ✅ TV Display 新 UI
- ✅ 商户后台现代化

### Milestone 4：国际化（Day 9 结束）
- ✅ 多语言支持
- ✅ 4 台服务器全部运行
- ✅ 官网上线

### Milestone 5：运营就绪（Day 12 结束）
- ✅ Admin 后台可用
- ✅ 监控告警就绪
- ✅ 压测通过
- ✅ 正式上线

---

## 九、风险与应对

| 风险 | 概率 | 影响 | 应对 |
|------|------|------|------|
| 虎皮椒回调失败 | 中 | 支付无法激活赛事 | 增加回调日志 + 手动激活兜底 |
| SSL 证书申请失败 | 低 | HTTPS 无法启用 | 使用腾讯云免费证书或自签名过渡 |
| 多服务器网络延迟 | 中 | 边缘节点体验差 | 只读转发 + 首尔权威架构 |
| APP UI 重设计延期 | 中 | 整体进度滞后 | 先出核心页面，次要页面后续迭代 |
| 官网设计不满意 | 低 | 品牌形象受损 | 准备 2-3 版设计稿备选 |
| 并发性能不足 | 低 | 6 人桌卡顿 | 提前压测 + Redis 缓存优化 |

---

## 十、每日工作节奏

**每日站会（10 分钟）**：
- 昨天完成了什么
- 今天计划做什么
- 有什么阻塞

**每日交付**：
- 代码 commit + push
- 更新 WORK_PROGRESS.md
- 部署到测试环境

**版本管理**：
- Day 3：v1.0.0（支付闭环 + 产品化）
- Day 6：v1.1.0（UI 重设计）
- Day 9：v1.2.0（国际化 + 多服务器）
- Day 12：v2.0.0（正式上线）

---

## 十一、资源需求

| 资源 | 数量 | 说明 |
|------|------|------|
| 后端开发 | 1 人 | Node.js / PostgreSQL |
| Android 开发 | 1 人 | Kotlin / Jetpack Compose |
| 前端开发 | 1 人 | React / Next.js / HTML |
| UI 设计 | 1 人 | Figma / 品牌设计 |
| DevOps | 0.5 人 | Nginx / Linux / 监控 |

---

## 十二、下一步行动

请确认以下事项，我立即开始执行：

1. **是否批准本计划？**
2. **从哪个 Phase 开始？**（建议 Phase 1 Day 1：支付+HTTPS）
3. **是否需要我先写 Nginx HTTPS 配置？**
4. **是否需要我先写商户后台 API？**
5. **官网域名确定了吗？**（tiantianpoker.com / 或其他）

等待你的开发指令。

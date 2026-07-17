# Poker Night Phase 1 完成记录

**日期**: 2026-07-18
**时间**: 07:10 GMT+8

## 完成内容

### TV Display App (com.pokernight.tvdisplay)
- **文件数**: 31 个 Kotlin/资源文件
- **APK**: 17MB (debug)
- **路径**: `/Users/mac/Documents/Codex/poker-night/tv-display/`
- **技术栈**: Jetpack Compose for TV + Material 3, Socket.IO 2.1.0
- **功能**:
  - ConnectScreen: 输入桌号连接, QR码下载 Player App
  - TableScreen: 6座位环绕布局, 公共牌区, 顶栏信息, 底栏操作
  - 14个 Socket.IO 事件处理
  - 座位状态颜色: 行动中红色边框, 弃牌半透明, All-In金色, 淘汰灰色
  - TV端不显示底牌
- **编译修复**:
  - @file:OptIn(ExperimentalTvMaterial3Api::class) 注解
  - WebSocket 类名大小写 (Websocket → WebSocket)
  - KeyboardCapitalization 引用删除
  - TableViewModel 类型安全

### Player App (com.pokernight.player)
- **文件数**: 33 个 Kotlin/资源文件
- **APK**: 9.3MB (debug)
- **路径**: `/Users/mac/Documents/Codex/poker-night/player-app/`
- **技术栈**: Jetpack Compose Material 3 + Navigation Compose, Socket.IO 2.1.0, Retrofit + OkHttp
- **功能**:
  - SplashScreen: 检查 token
  - LoginScreen/RegisterScreen: 手机号+密码认证
  - TableListScreen: 可用牌桌列表
  - TableGameScreen: 核心游戏画面 (5对手+底牌+公共牌+行动面板)
  - ActionPanel: 弃牌/过牌/跟注/加注/全下 + 加注滑块
  - Snackbar 二次确认 (弃牌/全下)
  - JWT Bearer token 认证
- **编译修复**:
  - SocketService EVENT_ERROR 残留 lambda 清理
  - TableGameScreen toast 引用修复 (collectAsState)
  - NetworkProvider auth 改为 mapOf

### 服务端 (已部署)
- **服务器**: 首尔 43.164.130.145
- **PM2 进程**: poker-api(3010), poker-socket(3011), payment-svc(3012), merchant-api(3013)
- **数据库**: PostgreSQL 15, 9表+索引
- **测试牌桌**: PNDEMO (venue: 测试酒吧, launch_fee: 2500)

## 编译参数
- Gradle 8.10.2 (本地缓存)
- AGP 8.5.2
- Kotlin 1.9.22 + composeOptions kotlinCompilerExtensionVersion=1.5.9
- compileSdk 34, minSdk 24, targetSdk 34

## 子代理使用
1. tv-display-dev: 创建 TV Display 项目 (10m37s)
2. player-app-dev: 创建 Player App 项目 (11m27s)
3. fix-tv-display: 修复 TV Display 编译错误 (11m48s)
4. fix-player-app: 修复 Player App 编译错误 (1m59s)

## 未完成
- GitHub 推送未成功 (网络受限, github.com 不可达)
- 需要在真机测试两个 APP
- Phase 2: SNG 赛制完整实现 (当前只有骨架)
- 商户后台前端开发
- Nginx 配置完善 (poker 路由)

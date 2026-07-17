# Poker Night — TV Display APK 完整补完 (2026-07-18)

## 背景
Kimi Code 生成了 android/tv-display 项目 Gradle 骨架，但 gradle-wrapper.jar 下载失败导致无法编译。人工接手补全。

## 完成工作

### 1. 文件补齐
Kimi 已写好的：
- `gradle/libs.versions.toml`（缺 3 个引用，已修复）
- `settings.gradle.kts`
- `build.gradle.kts`（顶层，缺 kotlinCompose plugin 引用，已修复）
- `app/build.gradle.kts`（含 composeOptions 替代 kotlinCompose plugin）
- `gradle.properties`
- `gradle/wrapper/gradle-wrapper.properties`

人工补充的 32 个文件：
**数据模型（7 个）** — 通过子代理写入
- `data/model/Card.kt` — 扑克牌 + fromCode() 解析器
- `data/model/PlayerSeat.kt` — 座位数据 + SeatStatus 枚举（8 状态）
- `data/model/TableState.kt` — 牌桌状态 + TablePhase 枚举
- `data/model/SidePot.kt` — 边池
- `data/model/HandResult.kt` — 手牌结果
- `data/model/ConnectionState.kt` — 连接状态密封类
- `data/model/ServerEvent.kt` — 服务端 DTO + toTableState()/toHandResult() 映射

**网络层（3 个）**
- `network/SocketService.kt` — Socket.IO 客户端（连接/状态/牌桌事件/错误）
- `network/RestApi.kt` — REST API（HTTP 牌桌状态拉取）
- `network/TableViewModel.kt` — ViewModel（桌号输入/连接/断开/历史）

**UI 组件（4 个）**
- `ui/components/PlayerSeatView.kt` — 6 座位显示（头像/昵称/筹码/状态/下注）
- `ui/components/PokerCardView.kt` — 扑克牌渲染（正面/背面/公共牌行）
- `ui/components/TopBar.kt` — 顶栏（桌号/盲注/级别/倒计时/底池）
- `ui/components/NumericKeypad.kt` — 数字键盘 + 桌号输入

**屏幕（2 个）**
- `ui/screens/ConnectScreen.kt` — 连接页（桌号输入/数字键盘/最近连接）
- `ui/screens/TableScreen.kt` — 牌桌页（6 座位环绕/公共牌/底池/相态标签/断开按钮）

**入口（1 个）**
- `MainActivity.kt` — Compose 入口，根据连接状态切换连接/牌桌页面

**主题（2 个）**
- `ui/theme/Color.kt` — 暗色主题色板（背景/桌面绿/金/红/Chip/座位）
- `ui/theme/Theme.kt` — Material3 darkColorScheme

**资源（5 个）**
- `AndroidManifest.xml` — TV Leanback 清单
- `res/values/strings.xml`
- `res/values/colors.xml`
- `res/values/themes.xml`
- `res/drawable/ic_launcher_foreground.xml`
- `res/drawable/ic_launcher_background.xml`
- `res/mipmap-anydpi-v26/ic_launcher.xml`
- `res/drawable/tv_banner.xml`
- `proguard-rules.pro`

### 2. 编译修复
- Gradle wrapper jar 从已有项目复制
- `gradle-wrapper.properties`: 8.4→8.10.2（有缓存）
- `build.gradle.kts` 顶层: 去掉 kotlinCompose plugin（1.9.22 不支持）
- `app/build.gradle.kts`: 用 composeOptions + kotlinCompilerExtensionVersion=1.5.9 替代 kotlinCompose plugin
- AGP 8.2.2→8.5.2（aapt2 有缓存）
- `PokerCardView.kt`: 补全 `import androidx.compose.ui.graphics.Color`

### 3. 编译结果
- **BUILD SUCCESSFUL**
- APK: `app/build/outputs/apk/debug/app-debug.apk` (9.6MB)
- 35 tasks, 9 executed, 26 cached

### 4. 架构特色
- **服务端**：首尔 43.164.130.145:80/poker（Express + Socket.IO + SQLite）
- **双通道**：Socket.IO 实时牌桌状态推送 + REST API 拉取
- **语言**：全 Jetpack Compose for TV（tv-foundation + tv-material）
- **6 座位椭圆布局**：顶部 3 座 + 底部 3 座环绕公共牌
- **连接方式**：8 字符桌号（含确认/删除/历史记忆）
- **桌号**：现有 C8BBGS、F8R0GP 两桌

### 待完成
- Player App (Android TV 玩家端) — 项目架子未创建
- 同一首尔服务器部署 TV Display APK 下载
- GitHub 推送（凭据缺失）

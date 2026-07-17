# Poker Night 项目废弃与重启记录 (2026-07-18)

## 废弃内容

以下旧工作全部废弃，不再使用：

### 旧 TV Display 项目
- 路径：`/Users/mac/Documents/Codex/poker-night/android/tv-display/`
- 内容：37 个文件，已编译成功（app-debug.apk 9.6MB）
- 废弃原因：基于旧的单桌自由玩法，不符合新的投币机式付费 SNG 业务规范
- 保留：仅保留 Gradle 构建配置和 Compose 主题色板供参考

### 旧后端代码
- 路径：首尔服务器 `/www/wwwroot/poker-night/`
- 内容：Express + Socket.IO + SQLite 单桌牌局
- 废弃原因：无 PostgreSQL、无支付、无分账、无商户体系、无 SNG 赛制

### 旧文档
- `PRD_v1_pay_to_play.md` — 已被 `DEV_DOC_v1.md` 完全替代
- `android-specs/TV_DISPLAY_SPEC.md` — 已被 `DEV_DOC_v1.md` Phase 2 替代
- `android-specs/PLAYER_APP_SPEC.md` — 已被 `DEV_DOC_v1.md` Phase 3 替代
- `PLAN_v2_Roadmap.md` — 已被 `DEV_DOC_v1.md` 完全替代

## 新项目起点

- **文档**：`DEV_DOC_v1.md`（正式开发文档 v1.0）
- **路径**：`/Users/mac/Documents/Codex/poker-night/`（清空后重建）
- **服务器**：首尔 43.164.130.145（从 SQLite 升级为 PostgreSQL）
- **模型分工**：严格执行 `ai_tools_ranking_v2_20260717.md`

## 下一步

等待用户审批 `DEV_DOC_v1.md` → 进入 Phase 0（项目初始化）

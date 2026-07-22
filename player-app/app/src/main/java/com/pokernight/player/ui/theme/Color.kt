package com.pokernight.player.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// 天天扑克锦标赛 — 深色高级感色板（对齐网页版 player.html :root 令牌）
// --gold:#FFD700  --gold2:#c9a227  --bg:#07070c  --panel:rgba(255,255,255,.035)
// --panel2:rgba(0,0,0,.45)  --line:rgba(201,162,39,.22)  --txt:#e8e8ec
// --dim:#9c9ca8  --red:#E5484D  --green:#46A758  --blue:#4A90D9
// ============================================================

// 背景层次（由深到浅）
val BgDark = Color(0xFF07070C)          // 主背景：网页 --bg #07070c
val BgElevated = Color(0xFF12121A)      // 一级抬升面
val SurfaceCard = Color(0xFF1A1A24)     // 卡片面
val SurfaceBorder = Color(0xFF2A2A38)   // 卡片描边
val MaskBg = Color(0xFF101018)          // 浮层卡片底：网页 .mask-box #101018

// 毛玻璃面板与描边（网页令牌）
val PanelGlass = Color(0x09FFFFFF)      // --panel rgba(255,255,255,.035)
val PanelDark = Color(0x73000000)       // --panel2 rgba(0,0,0,.45)
val HairlineWhite = Color(0x12FFFFFF)   // rgba(255,255,255,.07) 面板细边
val SeatBg = Color(0xB807070C)          // 座位卡底 rgba(7,7,12,.72)
val GhostBg = Color(0x14FFFFFF)         // rgba(255,255,255,.08) ghost 按钮底

// 牌桌毡面（网页 .felt：绿色微光渐变 + 金线）
val FeltGreen = Color(0x4D0D5A32)       // rgba(13,90,50,.30)
val FeltSheen = Color(0x05FFFFFF)       // rgba(255,255,255,.02)
val TableGreen = Color(0xFF0E5A32)      // 毡面中心（旧椭圆毡面，备用）
val TableGreenMid = Color(0xFF0A4426)   // 毡面过渡
val TableGreenDark = Color(0xFF062B17)  // 毡面边缘
val TableRim = Color(0xFF3A2E18)        // 桌沿木色

// 金色体系
val Gold = Color(0xFFFFD700)            // 主金 --gold
val GoldBright = Color(0xFFFFE887)      // 高光金
val GoldDark = Color(0xFFC9A227)        // 暗影金 --gold2
val GoldSoft = Color(0x33FFD700)        // 20% 金（光晕）
val GoldLine = Color(0x38C9A227)        // --line rgba(201,162,39,.22) 金线边框
val GoldPillEdge = Color(0x73FFD700)    // rgba(255,215,0,.45) 底池胶囊边

// 功能色
val ActionGreen = Color(0xFF46A758)     // --green
val ActionRed = Color(0xFFE5484D)       // --red
val ActionBlue = Color(0xFF4A90D9)      // --blue
val ActionOrange = Color(0xFFF0A020)    // 加注（旧实心，备用）
val ActionFoldGray = Color(0xFF8A94A6)  // 弃牌（旧实心，备用）
val ActionAllInRed = Color(0xFFD4382C)  // 全下（旧实心，备用）

// 按钮渐变（网页 button.act 配色）
val CallGreenA = Color(0xFF3D9950)      // 过牌/跟注渐变起
val CallGreenB = Color(0xFF2C7A3C)      // 过牌/跟注渐变止
val RaiseA = Color(0xFFE8A33D)          // 加注渐变起
val RaiseB = Color(0xFFB5761F)          // 加注渐变止
val FoldTintBg = Color(0x33E5484D)      // 弃牌底 rgba(229,72,77,.2)
val AllInTintBg = Color(0x26FFD700)     // 全下底 rgba(255,215,0,.15)

// 中性色
val DisabledGray = Color(0xFF55555F)
val White = Color(0xFFFFFFFF)
val Black = Color(0xFF000000)
val LightGray = Color(0xFF9C9CA8)
val TextPrimary = Color(0xFFE8E8EC)     // --txt
val TextDim = Color(0xFF9C9CA8)         // --dim
val TextSecondary = Color(0xB3FFFFFF)   // 70% 白
val TextTertiary = Color(0x80FFFFFF)    // 50% 白

// 扑克牌（网页 .pcard / .pcard.back）
val CardWhite = Color(0xFFFAFAF5)
val CardBorder = Color(0xFF999999)
val SuitRed = Color(0xFFE5484D)         // 网页 .pcard.red = --red
val SuitBlack = Color(0xFF1A1A1A)       // 网页 .pcard.black
val CardBackA = Color(0xFF0F2440)       // 牌背渐变起
val CardBackB = Color(0xFF1E3A5F)       // 牌背渐变止
val CardBackLine = Color(0xFF2A5080)    // 牌背描边

// 兼容旧引用
val DarkSurface = SurfaceCard
val DarkCard = BgElevated

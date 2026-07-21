@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.pokernight.tvdisplay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.pokernight.tvdisplay.data.model.TableState
import com.pokernight.tvdisplay.ui.theme.*

/**
 * Top bar showing table info: table code, blind level, pot, players, countdown.
 */
@Composable
fun TopBar(
    state: TableState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SeatBg)
            .border(1.dp, SeatBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left: 桌号 + 阶段
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InfoChip(
                label = "桌号",
                value = state.tableCode.ifEmpty { "---" },
                valueColor = GoldAccent,
            )
            InfoChip(
                label = "阶段",
                value = if (state.stage.isNotEmpty()) stageLabel(state.stage) else phaseLabel(state.phase),
                valueColor = TextPrimary,
            )
        }

        // Center: 盲注级别 + 手牌号
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InfoChip(
                label = "盲注",
                value = "L${state.blindLevel}",
                valueColor = GoldAccent,
            )
            InfoChip(
                label = "小盲/大盲",
                value = "${state.sb}/${state.bb}",
                valueColor = ChipGreen,
            )
            InfoChip(
                label = "手牌",
                value = "#${state.handNumber}",
                valueColor = TextPrimary,
            )
        }

        // Right: 玩家数 + 倒计时 + 底池
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InfoChip(
                label = "玩家",
                value = "${state.activePlayerCount}/${state.totalPlayerCount}",
                valueColor = ChipGreen,
            )
            if (state.countdown > 0) {
                InfoChip(
                    label = "开赛",
                    value = "${state.countdown}s",
                    valueColor = RedAction,
                )
            }
            if (state.pot > 0) {
                InfoChip(
                    label = "底池",
                    value = formatPot(state.pot),
                    valueColor = GoldAccent,
                )
            }
        }
    }
}

@Composable
private fun InfoChip(
    label: String,
    value: String,
    valueColor: Color = TextPrimary,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = TextTertiary,
            fontSize = 11.sp,
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun formatPot(pot: Int): String {
    return when {
        pot >= 1_000_000 -> "${pot / 1_000_000}M"
        pot >= 1_000 -> "${pot / 1_000}K"
        else -> pot.toString()
    }
}

/** 牌局阶段英文码 → 中文标签 */
internal fun stageLabel(stage: String): String = when (stage.lowercase()) {
    "preflop", "pre-flop", "pre_flop" -> "翻牌前"
    "flop" -> "翻牌圈"
    "turn" -> "转牌圈"
    "river" -> "河牌圈"
    "showdown" -> "摊牌"
    else -> stage.uppercase()
}

/** 赛事阶段英文码 → 中文标签 */
internal fun phaseLabel(phase: String): String = when (phase.lowercase()) {
    "registering" -> "报名中"
    "started", "playing" -> "进行中"
    "finished" -> "已结束"
    else -> phase.uppercase()
}

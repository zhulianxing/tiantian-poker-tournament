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
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SeatBg)
            .border(1.dp, SeatBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left: Table code + stage
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InfoChip(
                label = "Table",
                value = state.tableCode.ifEmpty { "---" },
                valueColor = GoldAccent,
            )
            InfoChip(
                label = "Stage",
                value = state.stage.uppercase().ifEmpty { state.phase.uppercase() },
                valueColor = TextPrimary,
            )
        }

        // Center: Blind level + Pot
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InfoChip(
                label = "Blind Lv",
                value = "${state.blindLevel}",
                valueColor = GoldAccent,
            )
            InfoChip(
                label = "SB/BB",
                value = "${state.sb}/${state.bb}",
                valueColor = ChipGreen,
            )
            InfoChip(
                label = "Hand",
                value = "#${state.handNumber}",
                valueColor = TextPrimary,
            )
        }

        // Right: Players + Countdown
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InfoChip(
                label = "Players",
                value = "${state.activePlayerCount}/${state.totalPlayerCount}",
                valueColor = ChipGreen,
            )
            if (state.countdown > 0) {
                InfoChip(
                    label = "Starts in",
                    value = "${state.countdown}s",
                    valueColor = RedAction,
                )
            }
            if (state.pot > 0) {
                InfoChip(
                    label = "Pot",
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

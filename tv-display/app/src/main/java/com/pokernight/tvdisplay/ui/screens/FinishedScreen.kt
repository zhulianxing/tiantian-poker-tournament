@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.pokernight.tvdisplay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.pokernight.tvdisplay.data.model.TableState
import com.pokernight.tvdisplay.data.network.TableViewModel
import com.pokernight.tvdisplay.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Finished screen — shown when phase == "finished".
 *
 * Displays:
 * - Final rankings (🥇🥈🥉)
 * - Auto-returns to IdleScreen after 5 seconds
 */
@Composable
fun FinishedScreen(
    state: TableState,
    viewModel: TableViewModel,
    modifier: Modifier = Modifier,
) {
    // Auto-return to idle after 5 seconds
    LaunchedEffect(Unit) {
        delay(5000)
        // Reset to idle phase locally — server will also push a TABLE_STATE event
        viewModel.resetToIdle()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(BgDark, TableGreenDark.copy(alpha = 0.5f))
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(48.dp),
        ) {
            // Title
            Text(
                text = "🏆 赛事结束 🏆",
                color = GoldAccent,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Rankings
            state.rankings.forEachIndexed { index, ranking ->
                val name = ranking["nickname"] as? String ?: "Player ${index + 1}"
                val chips = (ranking["finalChips"] as? Number)?.toInt() ?: 0
                val medal = when (index) {
                    0 -> "🥇"
                    1 -> "🥈"
                    2 -> "🥉"
                    else -> "${index + 1}."
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardBg)
                        .border(
                            width = if (index == 0) 2.dp else 1.dp,
                            color = if (index == 0) GoldAccent else SeatBorder,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "$medal $name",
                        color = if (index == 0) GoldAccent else TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = formatChips(chips),
                        color = ChipGreen,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Auto-return hint
            Text(
                text = "5 秒后自动返回待机画面…",
                color = TextTertiary,
                fontSize = 14.sp,
            )
        }
    }
}

private fun formatChips(chips: Int): String {
    return when {
        chips >= 1_000_000 -> "${chips / 1_000_000}M"
        chips >= 1_000 -> "${chips / 1_000}K"
        else -> chips.toString()
    }
}

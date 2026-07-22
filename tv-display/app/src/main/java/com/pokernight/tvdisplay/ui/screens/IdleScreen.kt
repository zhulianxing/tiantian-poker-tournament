@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.pokernight.tvdisplay.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.pokernight.tvdisplay.R
import com.pokernight.tvdisplay.data.model.TableState
import com.pokernight.tvdisplay.data.network.TableViewModel
import com.pokernight.tvdisplay.ui.components.QrCodeCard
import com.pokernight.tvdisplay.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Idle screen — shown when phase == "idle".
 *
 * Displays:
 * - Venue branding + welcome
 * - Dual QR codes (app download + tournament pay)
 * - Auto-rotating tournament info carousel (5s per page)
 * - Next tournament preview
 */
@Composable
fun IdleScreen(
    state: TableState,
    viewModel: TableViewModel,
    modifier: Modifier = Modifier,
) {
    val tableCode = state.tableCode
    val payUrl = "https://poker.clawclaw.tech/pay/pay.html?table=$tableCode"
    val downloadUrl = "https://poker.clawclaw.tech/download"

    // Carousel state
    var carouselPage by remember { mutableStateOf(0) }
    val carouselPages = remember {
        listOf(
            CarouselPage(
                title = "单桌限血赛规则",
                content = "初始筹码 1,000\n6人桌 · 单桌赛制\n最后存活者获胜",
            ),
            CarouselPage(
                title = "盲注递增",
                content = "起始盲注 10/20\n每 10 分钟翻倍\n合理分配筹码是关键",
            ),
            CarouselPage(
                title = "开赛条件",
                content = "满 6 人立即开赛\n或倒计时 5 分钟结束自动开赛\n至少 2 人方可激活赛事",
            ),
            CarouselPage(
                title = "入座与行为规范",
                content = "扫码免费入座\n每手操作时限 30 秒\n超时自动弃牌\n保持网络畅通",
            ),
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            carouselPage = (carouselPage + 1) % carouselPages.size
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BgDark, TableGreenDark.copy(alpha = 0.4f))
                )
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // === Header ===
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_logo),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "天天扑克锦标赛",
                    color = GoldAccent,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "欢迎光临 · 欢迎入座",
                color = TextSecondary,
                fontSize = 18.sp,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // === Main content row ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left: App download QR
                QrCodeCard(
                    url = downloadUrl,
                    title = "下载选手 APP",
                    description = "扫码下载天天扑克锦标赛选手端 APP",
                    qrSize = 180,
                )

                // Center: Info carousel
                Box(
                    modifier = Modifier
                        .width(420.dp)
                        .height(280.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardBg)
                        .border(1.dp, SeatBorder, RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(
                        targetState = carouselPage,
                        transitionSpec = {
                            (slideInHorizontally(animationSpec = tween(500)) { it } + fadeIn(tween(500)))
                                .togetherWith(
                                    slideOutHorizontally(animationSpec = tween(500)) { -it } + fadeOut(tween(500))
                                )
                                .using(SizeTransform(clip = false))
                        },
                        label = "carousel",
                    ) { page ->
                        val carouselData = carouselPages[page]
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // Page indicator dots
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                carouselPages.indices.forEach { index ->
                                    Box(
                                        modifier = Modifier
                                            .size(if (index == page) 8.dp else 6.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(
                                                if (index == page) GoldAccent
                                                else GoldAccent.copy(alpha = 0.3f)
                                            )
                                    )
                                }
                            }

                            Text(
                                text = carouselData.title,
                                color = GoldAccent,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                            )

                            Text(
                                text = carouselData.content,
                                color = TextPrimary,
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                            )
                        }
                    }
                }

                // Right: Pay QR
                QrCodeCard(
                    url = payUrl,
                    title = "发起赛事付费",
                    description = "扫码付费激活本场赛事",
                    qrSize = 180,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // === Next tournament preview ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardBg)
                    .border(1.dp, SeatBorder, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NextTournamentInfo(label = "下一场盲注", value = "${state.sb}/${state.bb}")
                NextTournamentInfo(label = "人数上限", value = "6人")
                NextTournamentInfo(label = "当前状态", value = "等待激活")
            }
        }
    }
}

@Composable
private fun NextTournamentInfo(
    label: String,
    value: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = TextTertiary,
            fontSize = 12.sp,
        )
        Text(
            text = value,
            color = GoldAccent,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private data class CarouselPage(
    val title: String,
    val content: String,
)

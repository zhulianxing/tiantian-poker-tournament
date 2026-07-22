package com.pokernight.player.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokernight.player.R
import com.pokernight.player.data.GameViewModel
import com.pokernight.player.ui.theme.BgDark
import com.pokernight.player.ui.theme.Gold
import com.pokernight.player.ui.theme.GoldDark
import com.pokernight.player.ui.theme.TableGreenDark
import com.pokernight.player.ui.theme.TextTertiary
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    viewModel: GameViewModel,
    onNavigate: (String) -> Unit,
) {
    LaunchedEffect(Unit) {
        delay(1500)
        val isLoggedIn = viewModel.uiState.value.isLoggedIn
        onNavigate(if (isLoggedIn) "table_list" else "login")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(TableGreenDark, BgDark),
                    radius = 1400f,
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 主 Logo（与网站一致）
            Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                modifier = Modifier.size(96.dp),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = "天天扑克锦标赛",
                fontSize = 32.sp,
                color = Gold,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "TIANTIAN POKER TOURNAMENT",
                fontSize = 11.sp,
                color = GoldDark,
                letterSpacing = 3.sp,
            )
            Spacer(Modifier.height(40.dp))
            CircularProgressIndicator(
                color = Gold,
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.5.dp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "正在进入牌桌…",
                fontSize = 12.sp,
                color = TextTertiary,
            )
        }
    }
}

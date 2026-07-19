package com.pokernight.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokernight.player.data.GameViewModel
import com.pokernight.player.ui.theme.BgDark
import com.pokernight.player.ui.theme.Gold
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
            .background(BgDark),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "♠♣♥♦",
                fontSize = 48.sp,
                color = Gold,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "天天扑克锦标赛",
                fontSize = 36.sp,
                color = Gold,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Player",
                fontSize = 16.sp,
                color = Gold.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(32.dp))
            CircularProgressIndicator(color = Gold, modifier = Modifier.size(32.dp))
        }
    }
}

package com.pokernight.player.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokernight.player.R
import com.pokernight.player.data.GameViewModel
import com.pokernight.player.ui.theme.ActionRed
import com.pokernight.player.ui.theme.BgDark
import com.pokernight.player.ui.theme.Gold
import com.pokernight.player.ui.theme.SurfaceBorder
import com.pokernight.player.ui.theme.SurfaceCard
import com.pokernight.player.ui.theme.TableGreenDark
import com.pokernight.player.ui.theme.TextSecondary
import com.pokernight.player.ui.theme.TextTertiary

@Composable
fun TableListScreen(
    viewModel: GameViewModel,
    onTableClick: (String) -> Unit,
    onScanClick: () -> Unit,
    onLogout: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    var tableCode by remember { mutableStateOf("") }
    // 进入本屏时读取一次：若有未结束的牌局则显示"返回牌局"
    val activeTable = remember { viewModel.getActiveTable() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(TableGreenDark, BgDark, BgDark)
                )
            )
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_logo),
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "天天扑克锦标赛",
                            fontSize = 22.sp,
                            color = Gold,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                    Text(
                        text = "欢迎, ${uiState.nickname ?: "玩家"}",
                        fontSize = 13.sp,
                        color = TextSecondary,
                    )
                }
                TextButton(onClick = onLogout) {
                    Text("退出", color = Gold.copy(alpha = 0.8f))
                }
            }

            Spacer(Modifier.height(28.dp))

            // 扫码入座：主操作大卡
            Button(
                onClick = onScanClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Gold,
                    contentColor = BgDark,
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
            ) {
                Text("📱  扫码入座", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            }

            // 返回牌局：有未结束赛事时显示
            if (activeTable != null) {
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { onTableClick(activeTable) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SurfaceCard,
                        contentColor = Gold,
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Gold.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("♠  返回牌局 · $activeTable", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(28.dp))

            // Divider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Divider(
                    modifier = Modifier.weight(1f),
                    color = SurfaceBorder,
                )
                Text(
                    text = "或手动输入桌号",
                    color = TextTertiary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
                androidx.compose.material3.Divider(
                    modifier = Modifier.weight(1f),
                    color = SurfaceBorder,
                )
            }

            Spacer(Modifier.height(20.dp))

            // 手动输入卡片
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceCard, RoundedCornerShape(16.dp))
                    .border(1.dp, SurfaceBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = tableCode,
                    onValueChange = { tableCode = it.uppercase().take(6) },
                    label = { Text("桌号", color = TextSecondary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.weight(1f),
                    colors = darkFieldColors(),
                    textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = {
                        if (tableCode.isNotBlank()) {
                            onTableClick(tableCode)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Gold,
                        contentColor = BgDark,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(56.dp),
                ) {
                    Text("入座", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            uiState.error?.let { err ->
                Text(err, color = ActionRed, fontSize = 14.sp)
            }
        }
    }
}

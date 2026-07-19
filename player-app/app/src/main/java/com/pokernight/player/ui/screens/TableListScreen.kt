package com.pokernight.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokernight.player.data.GameViewModel
import com.pokernight.player.ui.theme.BgDark
import com.pokernight.player.ui.theme.Gold
import com.pokernight.player.ui.theme.White

@Composable
fun TableListScreen(
    viewModel: GameViewModel,
    onTableClick: (String) -> Unit,
    onScanClick: () -> Unit,
    onLogout: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    var tableCode by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
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
                Text(
                    text = "♠ 天天扑克锦标赛",
                    fontSize = 24.sp,
                    color = Gold,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onLogout) {
                    Text("退出", color = Gold.copy(alpha = 0.8f))
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "欢迎, ${uiState.nickname ?: "玩家"}",
                fontSize = 14.sp,
                color = White.copy(alpha = 0.7f),
            )

            Spacer(Modifier.height(24.dp))

            // Scan button
            Button(
                onClick = onScanClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Gold,
                    contentColor = BgDark,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("📱 扫码入座", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(24.dp))

            // Divider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Divider(
                    modifier = Modifier.weight(1f),
                    color = White.copy(alpha = 0.2f),
                )
                Text(
                    text = "或手动输入桌号",
                    color = White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                androidx.compose.material3.Divider(
                    modifier = Modifier.weight(1f),
                    color = White.copy(alpha = 0.2f),
                )
            }

            Spacer(Modifier.height(16.dp))

            // Enter table code
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = tableCode,
                    onValueChange = { tableCode = it.uppercase().take(6) },
                    label = { Text("桌号", color = White.copy(alpha = 0.6f)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = White,
                        unfocusedTextColor = White,
                        focusedBorderColor = Gold,
                        unfocusedBorderColor = White.copy(alpha = 0.3f),
                        cursorColor = Gold,
                    ),
                    textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.width(8.dp))
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
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(56.dp),
                ) {
                    Text("入座", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))

            uiState.error?.let { err ->
                Text(err, color = androidx.compose.ui.graphics.Color.Red, fontSize = 14.sp)
            }
        }
    }
}

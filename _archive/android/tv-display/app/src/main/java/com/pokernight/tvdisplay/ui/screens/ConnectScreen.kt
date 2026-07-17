package com.pokernight.tvdisplay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokernight.tvdisplay.ui.components.NumericKeypad
import com.pokernight.tvdisplay.ui.components.TableCodeInput
import com.pokernight.tvdisplay.ui.theme.*

@Composable
fun ConnectScreen(
    tableCode: String,
    recentTables: List<String>,
    onAppendCode: (Char) -> Unit,
    onDeleteCode: () -> Unit,
    onClearCode: () -> Unit,
    onConnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo area
        Text(
            text = "🃏",
            fontSize = 48.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Poker Night",
            color = Gold,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "酒吧德州扑克",
            color = TextSecondary,
            fontSize = 16.sp
        )

        Spacer(Modifier.height(40.dp))

        // Table code input
        TableCodeInput(code = tableCode)

        Spacer(Modifier.height(24.dp))

        // Numeric keypad
        NumericKeypad(
            onDigit = onAppendCode,
            onDelete = onDeleteCode,
            onConfirm = onConnect
        )

        Spacer(Modifier.height(24.dp))

        // Recent tables
        if (recentTables.isNotEmpty()) {
            Text(
                text = "最近连接",
                color = TextSecondary,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                recentTables.forEach { code ->
                    Box(
                        modifier = Modifier
                            .background(Surface, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = code,
                            color = Gold,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // QR placeholder
        Text(
            text = "[ 扫码功能即将上线 ]",
            color = TextMuted,
            fontSize = 14.sp
        )
    }
}

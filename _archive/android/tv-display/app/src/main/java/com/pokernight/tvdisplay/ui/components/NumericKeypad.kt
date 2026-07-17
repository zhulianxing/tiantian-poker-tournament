package com.pokernight.tvdisplay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokernight.tvdisplay.ui.theme.*

@Composable
fun NumericKeypad(
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Row 1
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyButton("1") { onDigit('1') }
            KeyButton("2") { onDigit('2') }
            KeyButton("3") { onDigit('3') }
        }
        // Row 2
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyButton("4") { onDigit('4') }
            KeyButton("5") { onDigit('5') }
            KeyButton("6") { onDigit('6') }
        }
        // Row 3
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyButton("7") { onDigit('7') }
            KeyButton("8") { onDigit('8') }
            KeyButton("9") { onDigit('9') }
        }
        // Row 4
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Delete
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .height(56.dp)
                    .background(RedAccent.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .border(1.dp, RedAccent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Text("←", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            // 0
            KeyButton("0") { onDigit('0') }
            // Confirm
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .height(56.dp)
                    .background(ChipGreen.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .border(1.dp, ChipGreen, RoundedCornerShape(12.dp))
                    .clickable { onConfirm() },
                contentAlignment = Alignment.Center
            ) {
                Text("确认", color = ChipGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(72.dp)
            .height(56.dp)
            .background(Surface, RoundedCornerShape(12.dp))
            .border(1.dp, SeatBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun TableCodeInput(
    code: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("桌号:", color = TextSecondary, fontSize = 18.sp)
        Box(
            modifier = Modifier
                .background(Surface, RoundedCornerShape(8.dp))
                .border(1.dp, Gold, RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = code.ifEmpty { "输入桌号..." },
                color = if (code.isEmpty()) TextMuted else Gold,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )
        }
    }
}

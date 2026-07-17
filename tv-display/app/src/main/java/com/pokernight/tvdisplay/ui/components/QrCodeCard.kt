@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.pokernight.tvdisplay.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.pokernight.tvdisplay.ui.theme.*
import com.pokernight.tvdisplay.ui.util.generateQrCode

/**
 * A reusable QR code card with title and description.
 * Used in IdleScreen and WaitingScreen.
 */
@Composable
fun QrCodeCard(
    url: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    qrSize: Int = 160,
) {
    var qrBitmap by remember(url) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(url) {
        qrBitmap = generateQrCode(url)
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .border(1.dp, SeatBorder, RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (qrBitmap != null) {
            Image(
                bitmap = qrBitmap!!.asImageBitmap(),
                contentDescription = title,
                modifier = Modifier
                    .size(qrSize.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(2.dp, GoldAccent.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(qrSize.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SeatBg)
                    .border(2.dp, SeatBorder, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Loading…",
                    color = TextTertiary,
                    fontSize = 12.sp,
                )
            }
        }

        Text(
            text = title,
            color = GoldAccent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = description,
            color = TextSecondary,
            fontSize = 11.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.widthIn(max = 180.dp),
        )
    }
}

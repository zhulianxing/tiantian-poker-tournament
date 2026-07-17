@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.pokernight.tvdisplay.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.pokernight.tvdisplay.ui.theme.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Connect screen — enter table code and connect.
 * Also shows QR code download hint for Player App.
 */
@Composable
fun ConnectScreen(
    onConnect: (String) -> Unit,
    isConnecting: Boolean = false,
    error: String? = null,
) {
    var tableCode by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Generate QR code for Player App download
    LaunchedEffect(Unit) {
        qrBitmap = generateQrCode("http://43.164.130.145/download")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BgDark, TableGreenDark.copy(alpha = 0.3f))
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(48.dp),
        ) {
            // Title
            Text(
                text = "♠ Poker Night ♠",
                color = GoldAccent,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = "Connect your table display",
                color = TextSecondary,
                fontSize = 20.sp,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Table code input — using Material3 OutlinedTextField (works with TV)
            OutlinedTextField(
                value = tableCode,
                onValueChange = { newValue ->
                    tableCode = newValue
                        .filter { it.isLetterOrDigit() }
                        .take(6)
                        .uppercase()
                },
                label = { Text("Table Code") },
                placeholder = { Text("6-character code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    autoCorrect = false,
                ),
                isError = error != null,
                supportingText = {
                    if (error != null) {
                        Text(
                            text = error,
                            color = RedAction,
                        )
                    } else {
                        Text(
                            text = "${tableCode.length}/6 characters",
                            color = TextTertiary,
                        )
                    }
                },
                modifier = Modifier.width(360.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldAccent,
                    unfocusedBorderColor = SeatBorder,
                    focusedLabelColor = GoldAccent,
                    unfocusedLabelColor = TextSecondary,
                    cursorColor = GoldAccent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                ),
            )

            // Connect button
            Button(
                onClick = { onConnect(tableCode) },
                enabled = tableCode.length == 6 && !isConnecting,
                modifier = Modifier
                    .width(360.dp)
                    .height(56.dp),
                colors = ButtonDefaults.colors(
                    containerColor = GoldAccent,
                    contentColor = Color.Black,
                    disabledContainerColor = GoldAccent.copy(alpha = 0.3f),
                    disabledContentColor = Color.Black.copy(alpha = 0.3f),
                ),
            ) {
                Text(
                    text = if (isConnecting) "Connecting…" else "Connect",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // QR code section
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // QR code
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap!!.asImageBitmap(),
                        contentDescription = "QR code for Player App download",
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(2.dp, GoldAccent.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
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

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.width(200.dp),
                ) {
                    Text(
                        text = "Download Player App",
                        color = GoldAccent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Scan the QR code with your phone to download the Poker Night Player App and join the table.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

/**
 * Generates a QR code bitmap from text using ZXing.
 */
private fun generateQrCode(text: String): Bitmap? {
    return try {
        val hints = hashMapOf<EncodeHintType, Any>()
        hints[EncodeHintType.MARGIN] = 1
        val bitMatrix = QRCodeWriter().encode(
            text,
            BarcodeFormat.QR_CODE,
            300,
            300,
            hints,
        )
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}


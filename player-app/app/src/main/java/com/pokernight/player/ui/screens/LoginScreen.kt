package com.pokernight.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokernight.player.data.GameViewModel
import com.pokernight.player.ui.theme.ActionRed
import com.pokernight.player.ui.theme.BgDark
import com.pokernight.player.ui.theme.Gold
import com.pokernight.player.ui.theme.GoldDark
import com.pokernight.player.ui.theme.SurfaceBorder
import com.pokernight.player.ui.theme.SurfaceCard
import com.pokernight.player.ui.theme.TableGreenDark
import com.pokernight.player.ui.theme.TextSecondary
import com.pokernight.player.ui.theme.White

@Composable
fun LoginScreen(
    viewModel: GameViewModel,
    onLoginSuccess: () -> Unit,
    onRegisterClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val codeCountdown by viewModel.codeCountdown.collectAsState()
    val isCodeSending by viewModel.isCodeSending.collectAsState()
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) onLoginSuccess()
    }

    val isEmailValid = email.contains("@") && email.contains(".")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(TableGreenDark, BgDark, BgDark)
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // 品牌区
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(Color(0xFF1A1A24), RoundedCornerShape(18.dp))
                    .border(1.5.dp, GoldDark, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("♠", fontSize = 36.sp, color = Gold)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "天天扑克锦标赛",
                fontSize = 26.sp,
                color = Gold,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "邮箱验证码登录",
                fontSize = 14.sp,
                color = TextSecondary,
            )
            Spacer(Modifier.height(28.dp))

            // 登录卡片
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceCard, RoundedCornerShape(20.dp))
                    .border(1.dp, SurfaceBorder, RoundedCornerShape(20.dp))
                    .padding(20.dp),
            ) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("邮箱", color = TextSecondary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    colors = darkFieldColors(),
                    textStyle = TextStyle(fontSize = 16.sp),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { if (it.length <= 6) code = it.filter { c -> c.isDigit() } },
                        label = { Text("验证码", color = TextSecondary) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.weight(1f),
                        colors = darkFieldColors(),
                        textStyle = TextStyle(fontSize = 16.sp),
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.sendCode(email, "login") },
                        enabled = isEmailValid && !isCodeSending,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Gold,
                            contentColor = BgDark,
                            disabledContainerColor = Gold.copy(alpha = 0.25f),
                            disabledContentColor = BgDark.copy(alpha = 0.5f),
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(56.dp),
                    ) {
                        Text(
                            text = if (codeCountdown > 0) "${codeCountdown}s" else "发送验证码",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                uiState.error?.let { err ->
                    Text(
                        text = err,
                        color = ActionRed,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Gold, modifier = Modifier.size(36.dp))
                    }
                } else {
                    Button(
                        onClick = { viewModel.login(email, code) },
                        enabled = isEmailValid && code.length == 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Gold,
                            contentColor = BgDark,
                            disabledContainerColor = Gold.copy(alpha = 0.25f),
                            disabledContentColor = BgDark.copy(alpha = 0.5f),
                        ),
                        shape = RoundedCornerShape(12.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                    ) {
                        Text("登 录", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            TextButton(onClick = {
                viewModel.clearError()
                onRegisterClick()
            }) {
                Text("没有账号？去注册", color = Gold.copy(alpha = 0.8f), fontSize = 14.sp)
            }
        }
    }
}

@Composable
internal fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = White,
    unfocusedTextColor = White,
    focusedBorderColor = Gold,
    unfocusedBorderColor = SurfaceBorder,
    cursorColor = Gold,
    focusedContainerColor = Color(0xFF12121A),
    unfocusedContainerColor = Color(0xFF12121A),
)

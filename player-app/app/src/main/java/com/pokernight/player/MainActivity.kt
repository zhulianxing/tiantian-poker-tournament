package com.pokernight.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pokernight.player.ui.navigation.PokerNavGraph
import com.pokernight.player.ui.theme.PokerNightTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PokerNightTheme {
                PokerNavGraph()
            }
        }
    }
}

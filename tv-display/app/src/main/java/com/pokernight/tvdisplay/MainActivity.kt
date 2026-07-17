package com.pokernight.tvdisplay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pokernight.tvdisplay.data.network.TableViewModel
import com.pokernight.tvdisplay.ui.screens.ConnectScreen
import com.pokernight.tvdisplay.ui.screens.TableScreen
import com.pokernight.tvdisplay.ui.theme.TVDisplayTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TVDisplayTheme {
                val viewModel: TableViewModel = viewModel()
                val state by viewModel.uiState.collectAsState()
                val isConnecting by viewModel.isConnecting.collectAsState()
                val connectionError by viewModel.connectionError.collectAsState()

                if (state.connected) {
                    TableScreen(
                        state = state,
                        onDisconnect = { viewModel.disconnect() },
                    )
                } else {
                    ConnectScreen(
                        onConnect = { tableCode ->
                            viewModel.connect(tableCode)
                        },
                        isConnecting = isConnecting,
                        error = connectionError,
                    )
                }
            }
        }
    }
}

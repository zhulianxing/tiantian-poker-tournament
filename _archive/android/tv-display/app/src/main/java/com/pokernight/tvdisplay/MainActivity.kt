package com.pokernight.tvdisplay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pokernight.tvdisplay.network.TableViewModel
import com.pokernight.tvdisplay.ui.screens.ConnectScreen
import com.pokernight.tvdisplay.ui.screens.TableScreen
import com.pokernight.tvdisplay.ui.theme.PokerNightTvTheme
import com.pokernight.tvdisplay.data.model.ConnectionState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PokerNightTvTheme {
                val viewModel: TableViewModel = viewModel()
                val connectionState by viewModel.connectionState.collectAsState()
                val tableState by viewModel.tableState.collectAsState()
                val tableCode by viewModel.tableCode.collectAsState()
                val recentTables by viewModel.recentTables.collectAsState()

                when (connectionState) {
                    is ConnectionState.Disconnected,
                    is ConnectionState.Error -> {
                        ConnectScreen(
                            tableCode = tableCode,
                            recentTables = recentTables,
                            onAppendCode = viewModel::appendCodeChar,
                            onDeleteCode = viewModel::deleteCodeChar,
                            onClearCode = viewModel::clearCode,
                            onConnect = viewModel::connectToTable
                        )
                    }
                    is ConnectionState.Connected,
                    is ConnectionState.Connecting -> {
                        TableScreen(
                            tableState = tableState,
                            connectionState = connectionState,
                            onDisconnect = viewModel::disconnect
                        )
                    }
                }
            }
        }
    }
}

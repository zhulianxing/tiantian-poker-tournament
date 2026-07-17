package com.pokernight.tvdisplay.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pokernight.tvdisplay.data.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TableViewModel : ViewModel() {
    private val socketService = SocketService()
    private val restApi = RestApi()

    // Connection
    val connectionState: StateFlow<ConnectionState> = socketService.connectionState

    // Table state
    val tableState: StateFlow<TableState?> = socketService.tableState

    // Hand results
    val handResult = socketService.handResult

    // Messages
    val messageEvents = socketService.messageEvents

    // Error events
    val errorEvents = socketService.errorEvents

    // UI state
    private val _tableCode = MutableStateFlow("")
    val tableCode: StateFlow<String> = _tableCode.asStateFlow()

    private val _recentTables = MutableStateFlow<List<String>>(emptyList())
    val recentTables: StateFlow<List<String>> = _recentTables.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    fun appendCodeChar(c: Char) {
        if (_tableCode.value.length < 8) {
            _tableCode.value += c.uppercase()
        }
    }

    fun deleteCodeChar() {
        if (_tableCode.value.isNotEmpty()) {
            _tableCode.value = _tableCode.value.dropLast(1)
        }
    }

    fun clearCode() {
        _tableCode.value = ""
    }

    fun connectToTable() {
        val code = _tableCode.value
        if (code.isBlank()) return

        _isConnecting.value = true
        socketService.connect(code)

        // Save to recent
        val recent = _recentTables.value.toMutableList()
        recent.remove(code)
        recent.add(0, code)
        if (recent.size > 5) recent.removeAt(recent.lastIndex)
        _recentTables.value = recent

        // Reset connecting after timeout
        viewModelScope.launch {
            kotlinx.coroutines.delay(5000)
            _isConnecting.value = false
        }
    }

    fun disconnect() {
        socketService.disconnect()
        _isConnecting.value = false
    }

    override fun onCleared() {
        socketService.disconnect()
        super.onCleared()
    }
}

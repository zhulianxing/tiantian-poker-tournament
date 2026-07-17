package com.pokernight.tvdisplay.network

import com.google.gson.Gson
import com.pokernight.tvdisplay.data.model.*
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.json.JSONObject

class SocketService(
    private val serverUrl: String = "http://43.164.130.145:80/poker"
) {
    private var socket: Socket? = null
    private val gson = Gson()

    private val _tableState = MutableStateFlow<TableState?>(null)
    val tableState: StateFlow<TableState?> = _tableState.asStateFlow()

    private val _handResult = MutableSharedFlow<HandResult>(extraBufferCapacity = 5)
    val handResult: SharedFlow<HandResult> = _handResult.asSharedFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _errorEvents = Channel<String>(Channel.BUFFERED)
    val errorEvents: Flow<String> = _errorEvents.receiveAsFlow()

    private val _messageEvents = Channel<String>(Channel.BUFFERED)
    val messageEvents: Flow<String> = _messageEvents.receiveAsFlow()

    fun connect(tableCode: String) {
        _connectionState.value = ConnectionState.Connecting(serverUrl)

        try {
            val options = IO.Options().apply {
                forceNew = true
                reconnection = true
                reconnectionAttempts = Int.MAX_VALUE
                reconnectionDelay = 1000
                reconnectionDelayMax = 30000
                timeout = 10000
                transports = arrayOf("websocket", "polling")
            }

            socket = IO.socket(serverUrl, options)

            socket?.on(Socket.EVENT_CONNECT) {
                _connectionState.value = ConnectionState.Connected(tableCode)
                socket?.emit("join_table", JSONObject().apply {
                    put("tableCode", tableCode)
                    put("role", "spectator")
                })
            }

            socket?.on("table_state") { args ->
                if (args.isNotEmpty()) {
                    try {
                        val data = args[0] as? JSONObject
                        val dto = gson.fromJson(data.toString(), ServerTableState::class.java)
                        _tableState.value = dto.toTableState()
                    } catch (e: Exception) {
                        _errorEvents.trySend("解析牌桌状态失败: ${e.message}")
                    }
                }
            }

            socket?.on("hand_result") { args ->
                if (args.isNotEmpty()) {
                    try {
                        val data = args[0] as? JSONObject
                        val dto = gson.fromJson(data.toString(), ServerHandResult::class.java)
                        _handResult.tryEmit(dto.toHandResult())
                    } catch (e: Exception) {
                        _errorEvents.trySend("解析手牌结果失败: ${e.message}")
                    }
                }
            }

            socket?.on("table_message") { args ->
                if (args.isNotEmpty()) {
                    try {
                        val data = args[0] as? JSONObject
                        val text = data?.optString("text", "") ?: ""
                        _messageEvents.trySend(text)
                    } catch (_: Exception) {}
                }
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                _connectionState.value = ConnectionState.Disconnected
            }

            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                val msg = args.firstOrNull()?.toString() ?: "未知错误"
                _connectionState.value = ConnectionState.Error(msg)
                _errorEvents.trySend("连接错误: $msg")
            }

            socket?.connect()
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error(e.message ?: "连接异常")
            _errorEvents.trySend("连接异常: ${e.message}")
        }
    }

    fun disconnect() {
        socket?.off()
        socket?.disconnect()
        socket = null
        _connectionState.value = ConnectionState.Disconnected
        _tableState.value = null
    }

    fun isConnected(): Boolean = socket?.connected() == true
}

package com.pokernight.tvdisplay.data.network

import android.util.Log
import com.pokernight.tvdisplay.data.model.ClientEvent
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import kotlin.concurrent.thread

class SocketService {

    companion object {
        private const val TAG = "SocketService"
        private const val WS_URL = "wss://poker.clawclaw.tech/socket.io/?EIO=4&transport=websocket&role=tv"
        private const val EIO_OPEN = '0'
        private const val EIO_CLOSE = '1'
        private const val EIO_PING = '2'
        private const val EIO_MESSAGE = '4'
        private const val SIO_CONNECT = '0'
        private const val SIO_DISCONNECT = '1'
        private const val SIO_EVENT = '2'
        private const val SIO_ERROR = '4'
    }

    private var wsClient: WebSocketClient? = null
    private var connected = false
    private var shouldReconnect = false
    private var reconnectThread: Thread? = null

    private var savedTableCode: String? = null
    private var savedOnEvent: ((String, JSONObject) -> Unit)? = null
    private var savedOnConnect: (() -> Unit)? = null
    private var savedOnDisconnect: (() -> Unit)? = null
    private var savedOnError: ((String) -> Unit)? = null

    fun connect(
        tableCode: String,
        onEvent: (String, JSONObject) -> Unit,
        onConnect: () -> Unit,
        onDisconnect: () -> Unit,
        onError: (String) -> Unit,
    ) {
        savedTableCode = tableCode
        savedOnEvent = onEvent
        savedOnConnect = onConnect
        savedOnDisconnect = onDisconnect
        savedOnError = onError
        shouldReconnect = true
        doConnect()
    }

    private fun doConnect() {
        try {
            wsClient?.close()
            wsClient = null
            connected = false

            Log.i(TAG, "Connecting to WebSocket: $WS_URL")

            wsClient = object : WebSocketClient(URI(WS_URL)) {
                override fun onOpen(handshake: ServerHandshake?) {
                    Log.i(TAG, "onOpen — waiting for Engine.IO open")
                }

                override fun onMessage(msg: String?) {
                    if (msg.isNullOrEmpty()) return
                    Log.i(TAG, "RAW[${"$msg".take(200)}]") 
                    handleEngineIOMessage(msg)
                }

                override fun onClose(code: Int, reason: String?, remote: Boolean) {
                    Log.i(TAG, "onClose: $code $reason remote=$remote")
                    handleDisconnect()
                }

                override fun onError(ex: Exception?) {
                    Log.e(TAG, "onError: ${ex?.message}", ex)
                }
            }

            wsClient?.connectionLostTimeout = 0
            wsClient?.connect()

        } catch (e: Exception) {
            Log.e(TAG, "Connection error", e)
            savedOnError?.invoke(e.message ?: "Connection failed")
            handleDisconnect()
        }
    }

    private fun handleEngineIOMessage(text: String) {
        if (text.isEmpty()) return
        when (text[0]) {
            EIO_OPEN -> {
                try {
                    val sid = JSONObject(text.substring(1)).optString("sid", "?")
                    Log.i(TAG, "Engine.IO open sid=$sid")
                    wsClient?.send("40")
                    Log.i(TAG, "Sent 40")
                } catch (e: Exception) {
                    Log.e(TAG, "Bad open: $text", e)
                }
            }
            EIO_CLOSE -> {
                Log.i(TAG, "Engine.IO close")
                wsClient?.close()
                handleDisconnect()
            }
            EIO_PING -> wsClient?.send("3")
            EIO_MESSAGE -> handleSocketIOMessage(text)
            else -> Log.w(TAG, "Unknown EIO: '$text[0]'")
        }
    }

    private fun handleSocketIOMessage(text: String) {
        if (text.length < 2) return
        when (text[1]) {
            SIO_CONNECT -> {
                Log.i(TAG, "SIO CONNECT (40)")
                if (!connected) {
                    connected = true
                    savedTableCode?.let { code ->
                        // Server join_table handler expects plain string, not object
                        wsClient?.send("42[\"join_table\",\"$code\"]")
                        Log.i(TAG, "Emitted join_table $code")
                    }
                    savedOnConnect?.invoke()
                }
            }
            SIO_EVENT -> {
                try {
                    val arr = JSONArray(text.substring(2))
                    val name = arr.optString(0, "")
                    if (name.isEmpty()) return
                    val data = if (arr.length() > 1 && arr.opt(1) is JSONObject) arr.getJSONObject(1) else JSONObject()
                    Log.d(TAG, "Event: $name")
                    savedOnEvent?.invoke(name, data)
                } catch (e: Exception) {
                    Log.e(TAG, "Bad event: ${text.substring(2)}", e)
                }
            }
            SIO_DISCONNECT -> {
                Log.i(TAG, "SIO DISCONNECT")
                handleDisconnect()
            }
            SIO_ERROR -> Log.e(TAG, "SIO ERROR: ${text.substring(2)}")
            else -> Log.d(TAG, "SIO type '${text[1]}': ${text.substring(2)}")
        }
    }

    fun emit(event: String, data: JSONObject) {
        val arr = JSONArray().apply { put(event); put(data) }
        wsClient?.send("42$arr")
        Log.d(TAG, "Emitted: $event")
    }

    fun disconnect() {
        Log.i(TAG, "Disconnecting")
        shouldReconnect = false
        reconnectThread?.interrupt()
        reconnectThread = null
        wsClient?.close()
        wsClient = null
        connected = false
    }

    fun isConnected(): Boolean = connected && wsClient?.isOpen == true

    private fun handleDisconnect() {
        val wasConnected = connected
        connected = false
        wsClient = null
        if (wasConnected) savedOnDisconnect?.invoke()
        if (shouldReconnect) scheduleReconnect()
    }

    private fun scheduleReconnect() {
        reconnectThread?.interrupt()
        reconnectThread = thread {
            try {
                val delay = 1000L + (Math.random() * 4000).toLong()
                Log.i(TAG, "Reconnect in ${delay}ms")
                Thread.sleep(delay)
                if (shouldReconnect) doConnect()
            } catch (_: InterruptedException) {}
        }
    }
}

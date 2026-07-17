package com.pokernight.tvdisplay.data.network

import android.util.Log
import com.pokernight.tvdisplay.data.model.ClientEvent
import com.pokernight.tvdisplay.data.model.ServerEvent
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.engineio.client.transports.WebSocket
import io.socket.engineio.client.transports.Polling
import org.json.JSONObject
import java.net.URISyntaxException

/**
 * Socket.IO client wrapper for TV Display.
 * Connects to the poker server as a spectator.
 */
class SocketService {

    companion object {
        private const val TAG = "SocketService"
        private const val SERVER_URL = "http://43.164.130.145"
    }

    private var socket: Socket? = null

    /**
     * Connect to the server and join a table as spectator.
     * @param tableCode 6-character table code
     * @param onEvent callback for server events: (eventName, data)
     * @param onConnect called when connection is established
     * @param onDisconnect called when disconnected
     * @param onError called on connection error
     */
    fun connect(
        tableCode: String,
        onEvent: (String, JSONObject) -> Unit,
        onConnect: () -> Unit,
        onDisconnect: () -> Unit,
        onError: (String) -> Unit,
    ) {
        try {
            val options = IO.Options().apply {
                transports = arrayOf(Polling.NAME, WebSocket.NAME)
                reconnection = true
                reconnectionAttempts = 5
                reconnectionDelay = 2000
                timeout = 10000
            }

            socket = IO.socket(SERVER_URL, options)

            socket?.apply {
                on(Socket.EVENT_CONNECT) {
                    Log.i(TAG, "Socket connected")
                    // Join as spectator
                    val joinData = JSONObject().apply {
                        put("tableCode", tableCode)
                        put("role", "spectator")
                    }
                    emit(ClientEvent.JOIN_TABLE, joinData)
                    onConnect()
                }

                on(Socket.EVENT_DISCONNECT) {
                    Log.i(TAG, "Socket disconnected")
                    onDisconnect()
                }

                on(Socket.EVENT_CONNECT_ERROR) { args ->
                    val msg = args?.firstOrNull()?.toString() ?: "Connection error"
                    Log.e(TAG, "Connect error: $msg")
                    onError(msg)
                }

                // Register all server→client events
                val events = listOf(
                    ServerEvent.TOURNAMENT_ACTIVATED,
                    ServerEvent.COUNTDOWN_TICK,
                    ServerEvent.TOURNAMENT_STARTED,
                    ServerEvent.NEW_HAND,
                    ServerEvent.HOLE_CARDS, // TV ignores but must handle
                    ServerEvent.HAND_STARTED,
                    ServerEvent.STAGE_CHANGED,
                    ServerEvent.TURN_CHANGED,
                    ServerEvent.ACTION_RESULT,
                    ServerEvent.SHOWDOWN,
                    ServerEvent.HAND_RESULT,
                    ServerEvent.PLAYER_ELIMINATED,
                    ServerEvent.TOURNAMENT_FINISHED,
                    ServerEvent.BLIND_LEVEL_UP,
                    ServerEvent.TABLE_STATE,
                )

                events.forEach { event ->
                    on(event) { args ->
                        val data = args?.firstOrNull()
                        val json = if (data is JSONObject) data
                        else if (data != null) JSONObject(data.toString())
                        else JSONObject()
                        Log.d(TAG, "Event received: $event -> $json")
                        onEvent(event, json)
                    }
                }
            }

            socket?.connect()
        } catch (e: URISyntaxException) {
            Log.e(TAG, "URI Syntax error", e)
            onError(e.message ?: "Invalid server URL")
        } catch (e: Exception) {
            Log.e(TAG, "Connection error", e)
            onError(e.message ?: "Connection failed")
        }
    }

    fun disconnect() {
        socket?.apply {
            disconnect()
            off()
        }
        socket = null
        Log.i(TAG, "Socket disconnected and cleaned up")
    }

    fun isConnected(): Boolean = socket?.connected() == true
}

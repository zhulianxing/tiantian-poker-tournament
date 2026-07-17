package com.pokernight.tvdisplay.data.model

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    data class Connecting(val serverUrl: String) : ConnectionState()
    data class Connected(val tableCode: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

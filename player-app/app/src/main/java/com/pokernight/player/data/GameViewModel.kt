package com.pokernight.player.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pokernight.player.data.model.AuthRequest
import com.pokernight.player.data.model.AuthResponse
import com.pokernight.player.data.model.GameState
import com.pokernight.player.data.model.GameHistory
import com.pokernight.player.data.model.TableStatus
import com.pokernight.player.data.network.AuthManager
import com.pokernight.player.data.network.NetworkProvider
import com.pokernight.player.data.network.SocketService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class GameViewModel : ViewModel() {

    private val api = NetworkProvider.api
    private var socketService: SocketService? = null

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _tableStatus = MutableStateFlow<TableStatus?>(null)
    val tableStatus: StateFlow<TableStatus?> = _tableStatus.asStateFlow()

    private val _history = MutableStateFlow<List<GameHistory>>(emptyList())
    val history: StateFlow<List<GameHistory>> = _history.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    data class UiState(
        val isLoading: Boolean = false,
        val error: String? = null,
        val isLoggedIn: Boolean = false,
        val nickname: String? = null,
        val socketConnected: Boolean = false,
        val lastEvent: String = "",
        val lastEventData: JSONObject? = null,
    )

    init {
        _uiState.value = _uiState.value.copy(
            isLoggedIn = AuthManager.isLoggedIn(),
            nickname = AuthManager.getNickname(),
        )
    }

    fun login(phone: String, password: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val resp: AuthResponse = api.login(AuthRequest(phone = phone, password = password))
                AuthManager.saveToken(resp.token)
                AuthManager.savePlayer(
                    resp.player.id,
                    resp.player.nickname,
                    resp.player.phone,
                    resp.player.avatar,
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoggedIn = true,
                    nickname = resp.player.nickname,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "登录失败")
            }
        }
    }

    fun register(phone: String, nickname: String, password: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val resp: AuthResponse = api.register(
                    AuthRequest(phone = phone, password = password, nickname = nickname)
                )
                AuthManager.saveToken(resp.token)
                AuthManager.savePlayer(
                    resp.player.id,
                    resp.player.nickname,
                    resp.player.phone,
                    resp.player.avatar,
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoggedIn = true,
                    nickname = resp.player.nickname,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "注册失败")
            }
        }
    }

    fun logout() {
        socketService?.disconnect()
        socketService = null
        AuthManager.logout()
        _uiState.value = UiState()
        _gameState.value = GameState()
        _tableStatus.value = null
    }

    fun fetchTableStatus(code: String) {
        viewModelScope.launch {
            try {
                val status = api.getTableStatus(code)
                _tableStatus.value = status
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "获取牌桌状态失败")
            }
        }
    }

    fun loadHistory() {
        val token = AuthManager.getToken() ?: return
        viewModelScope.launch {
            try {
                _history.value = api.getMyHistory("Bearer $token")
            } catch (e: Exception) {
                _history.value = emptyList()
            }
        }
    }

    fun connectSocket(tableCode: String) {
        if (socketService != null) return
        val token = AuthManager.getToken()
        socketService = SocketService(
            onStateUpdate = { state -> _gameState.value = state },
            onEvent = { event, data ->
                _uiState.value = _uiState.value.copy(lastEvent = event, lastEventData = data)
                if (event == SocketService.EVT_TOURNAMENT_ACTIVATED) {
                    val tid = data.optString("tournamentId", "")
                    _gameState.value = _gameState.value.copy(tournamentId = tid)
                }
            },
        )
        socketService?.connect(token)
        socketService?.joinTable(tableCode)
        _uiState.value = _uiState.value.copy(socketConnected = true)
    }

    fun disconnectSocket() {
        socketService?.disconnect()
        socketService = null
        _uiState.value = _uiState.value.copy(socketConnected = false)
        _gameState.value = GameState()
    }

    fun performAction(action: String, amount: Int = 0) {
        val gs = _gameState.value
        socketService?.playerAction(gs.tournamentId, action, amount)
    }

    fun showToast(msg: String?) {
        _toast.value = msg
    }

    fun consumeToast() {
        _toast.value = null
    }

    override fun onCleared() {
        super.onCleared()
        socketService?.disconnect()
    }
}

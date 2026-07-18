package com.pokernight.player.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pokernight.player.data.model.AuthResponse
import com.pokernight.player.data.model.GameState
import com.pokernight.player.data.model.GameHistory
import com.pokernight.player.data.model.JoinResponse
import com.pokernight.player.data.model.LoginRequest
import com.pokernight.player.data.model.RegisterRequest
import com.pokernight.player.data.model.SendCodeRequest
import com.pokernight.player.data.model.TableStatus
import com.pokernight.player.data.network.AuthManager
import com.pokernight.player.data.network.NetworkProvider
import com.pokernight.player.data.network.SocketService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    // Code countdown state
    private val _codeCountdown = MutableStateFlow(0)
    val codeCountdown: StateFlow<Int> = _codeCountdown.asStateFlow()

    private val _isCodeSending = MutableStateFlow(false)
    val isCodeSending: StateFlow<Boolean> = _isCodeSending.asStateFlow()

    // Join result state
    private val _joinResult = MutableStateFlow<JoinResponse?>(null)
    val joinResult: StateFlow<JoinResponse?> = _joinResult.asStateFlow()

    private var countdownJob: Job? = null

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

    // ─── Auth: Email + Code ───

    fun sendCode(email: String, purpose: String) {
        if (_isCodeSending.value) return
        _isCodeSending.value = true
        _uiState.value = _uiState.value.copy(error = null)
        viewModelScope.launch {
            try {
                val resp = api.sendCode(SendCodeRequest(email = email, purpose = purpose))
                if (resp.success) {
                    startCountdown()
                    _toast.value = "验证码已发送至 $email"
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = resp.message ?: "验证码发送失败"
                    )
                    _isCodeSending.value = false
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "验证码发送失败")
                _isCodeSending.value = false
            }
        }
    }

    private fun startCountdown() {
        _codeCountdown.value = 60
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (i in 60 downTo 1) {
                _codeCountdown.value = i
                delay(1000)
            }
            _codeCountdown.value = 0
            _isCodeSending.value = false
        }
    }

    fun login(email: String, code: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val resp: AuthResponse = api.login(LoginRequest(email = email, code = code))
                AuthManager.saveToken(resp.token)
                AuthManager.savePlayer(
                    resp.player.id,
                    resp.player.nickname,
                    resp.player.email,
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

    fun register(email: String, code: String, nickname: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val resp: AuthResponse = api.register(
                    RegisterRequest(email = email, code = code, nickname = nickname)
                )
                AuthManager.saveToken(resp.token)
                AuthManager.savePlayer(
                    resp.player.id,
                    resp.player.nickname,
                    resp.player.email,
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
        _joinResult.value = null
    }

    // ─── Table & Tournament ───

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

    fun joinTournament(tournamentId: String) {
        val token = AuthManager.getToken() ?: return
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val result = api.joinTournament(tournamentId, "Bearer $token")
                _joinResult.value = result.copy(tournamentId = tournamentId)
                // Set mySeatIndex in gameState so the game screen knows which seat is mine
                _gameState.value = _gameState.value.copy(
                    tournamentId = tournamentId,
                    mySeatIndex = result.seatIndex,
                    myChips = if (result.chipCount > 0) result.chipCount else result.startChips,
                )
                _uiState.value = _uiState.value.copy(isLoading = false)
                _toast.value = "入座成功！座位号: ${result.seatIndex}"
            } catch (e: retrofit2.HttpException) {
                val errorBody = try {
                    e.response()?.errorBody()?.string() ?: ""
                } catch (_: Exception) { "" }
                android.util.Log.d("GameVM", "joinTournament HttpException: code=${e.code()} body=$errorBody")
                if (errorBody.contains("already joined") || errorBody.contains("already")) {
                    // Already joined — navigate to lobby anyway
                    val mySeat = 0
                    _joinResult.value = JoinResponse(
                        success = true,
                        seatIndex = mySeat,
                        tournamentId = tournamentId,
                        chipCount = 1000,
                        startChips = 1000,
                    )
                    _gameState.value = _gameState.value.copy(
                        tournamentId = tournamentId,
                        mySeatIndex = mySeat,
                        myChips = 1000,
                    )
                    _uiState.value = _uiState.value.copy(isLoading = false, error = null)
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = errorBody.ifEmpty { e.message() ?: "入座失败" })
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "入座失败")
            }
        }
    }

    fun clearJoinResult() {
        _joinResult.value = null
    }

    fun leaveTournament(tournamentId: String, onSuccess: () -> Unit) {
        val token = AuthManager.getToken() ?: return
        viewModelScope.launch {
            try {
                api.leaveTournament(tournamentId, "Bearer $token")
                _joinResult.value = null
                _toast.value = "已离开座位"
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "离开失败")
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

    // ─── Socket ───

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

    // ─── Toast ───

    fun showToast(msg: String?) {
        _toast.value = msg
    }

    fun consumeToast() {
        _toast.value = null
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        socketService?.disconnect()
    }
}

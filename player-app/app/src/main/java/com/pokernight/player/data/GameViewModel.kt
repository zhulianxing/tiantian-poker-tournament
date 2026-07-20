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

    fun getPlayerId(): String? = AuthManager.getPlayerId()

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

    private var tableStatusCode: String? = null

    fun fetchTableStatus(code: String) {
        // 换了桌号才清空旧数据：避免残留的上场赛事状态（如 started）触发大厅自动跳牌局；
        // 同桌号轮询时保留旧数据，防止界面每 5 秒闪加载
        if (tableStatusCode != code) {
            tableStatusCode = code
            _tableStatus.value = null
        }
        viewModelScope.launch {
            try {
                val status = api.getTableStatus(code)
                _tableStatus.value = status
                // 赛事已结束 → 清除"返回牌局"入口
                if (status.tournament?.status == "finished" && AuthManager.getActiveTable() == code) {
                    AuthManager.clearActiveTable()
                }
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
                AuthManager.clearActiveTable()
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
        // If socketService exists but is disconnected, recreate it
        if (socketService != null) {
            if (socketService?.isConnected() == true) {
                android.util.Log.i("GameViewModel", "connectSocket: socket already connected, skipping")
                return
            }
            android.util.Log.i("GameViewModel", "connectSocket: socketService exists but disconnected, recreating")
            socketService?.disconnect()
            socketService = null
        }
        val token = AuthManager.getToken()
        android.util.Log.i("GameViewModel", "connectSocket: token=${if (token != null) token.take(20) + "..." else "null"}, tableCode=$tableCode")
        // 新 socket = 新牌局上下文：清掉上一场残留的牌局状态（旧底牌/底池/阶段），
        // 防止大厅/牌局页被过期数据误导（服务器 table_state 会立刻重新同步）
        _gameState.value = GameState(tableCode = tableCode)
        socketService = SocketService(
            onStateUpdate = { state -> _gameState.value = state },
            onEvent = { event, data ->
                _uiState.value = _uiState.value.copy(lastEvent = event, lastEventData = data)
                if (event == SocketService.EVT_TOURNAMENT_ACTIVATED) {
                    val tid = data.optString("tournamentId", "")
                    _gameState.value = _gameState.value.copy(tournamentId = tid)
                }
                if (event == SocketService.EVT_TOURNAMENT_FINISHED) {
                    AuthManager.clearActiveTable()
                }
            },
        )
        socketService?.connect(token)
        // Note: joinTable is called here but Socket may not be connected yet.
        // SocketService will re-emit join_table on EVENT_CONNECT.
        socketService?.joinTable(tableCode)
        AuthManager.saveActiveTable(tableCode)
        _uiState.value = _uiState.value.copy(socketConnected = true)
    }

    /** App 回到前台时调用：socket 断开后手动触发重连（库内重连在 Doze 下不可靠）。 */
    fun reconnectSocketIfNeeded() {
        val ss = socketService ?: return
        if (!ss.isConnected()) {
            android.util.Log.i("GameViewModel", "reconnectSocketIfNeeded: reconnecting")
            ss.connect(AuthManager.getToken())
        }
    }

    /** 当前是否有可返回的活跃牌桌。 */
    fun getActiveTable(): String? = AuthManager.getActiveTable()

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

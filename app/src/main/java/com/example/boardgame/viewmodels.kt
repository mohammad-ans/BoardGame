package com.example.boardgame

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

sealed class OnlineUiState {
    data object Idle : OnlineUiState()
    data object Connecting : OnlineUiState()
    data object Waiting : OnlineUiState()
    data class RoomCreated(val roomCode: String) : OnlineUiState()
    data class Matched(val roomCode: String, val turn: Boolean) : OnlineUiState()
    data class Error(val message: String) : OnlineUiState()
}

data class GameBoardState(
    val player1Pos: Int = 0,
    val player2Pos: Int = 0,
    val player1Turn: Int = 1,
    val diceVal: Int = 1,
    val diceEnabled: Boolean = true,
    val fireworksRunning: Boolean = false
)

sealed class GameEvent{
    data class RemoteMove(val diceVal: Int): GameEvent()
    data class RejoinData(val json: JSONObject): GameEvent()
    data class LoadingTextChanged(val text: String): GameEvent()
    data object StartLoading : GameEvent()
    data object StopLoading : GameEvent()
    data object SelfTimedOut: GameEvent()
    data object OpponentTimedOut: GameEvent()
}

class GameSessionViewModel: ViewModel() {
    var connection: GameConnection? = null
    var onlineConnection: OnlineGameConnection? = null
    var isHost: Boolean = false
    var connectionType: String = "pvp"

    var localPlayerName: String = ""
    var opponentName: String = ""
    var uniqueUid: String = ""
    private val _onlineState = MutableStateFlow<OnlineUiState>(OnlineUiState.Idle)
    val onlineState: StateFlow<OnlineUiState> = _onlineState.asStateFlow()

    private val _navigateToGame = MutableStateFlow(false)
    val navigateToGame: StateFlow<Boolean> = _navigateToGame.asStateFlow()

    fun createRoom() {
        _onlineState.value = OnlineUiState.Connecting
        onlineConnection?.createRoom(
            onCreated = { code -> _onlineState.value = OnlineUiState.RoomCreated(code) },
            onFailed = { _onlineState.value = OnlineUiState.Error("Server error") },
            onMatched = { code, turn -> onOnlineMatched(code, turn) }
        )
    }

    fun joinRoom(code: String) {
        _onlineState.value = OnlineUiState.Connecting
        onlineConnection?.joinRoom(
            roomCode = code,
            onJoined = { roomCode, turn -> onOnlineMatched(roomCode, turn) },
            onFailed = { message ->
                _onlineState.value = OnlineUiState.Error(message)
            }
        )
    }

    fun findRandomMatch() {
        _onlineState.value = OnlineUiState.Connecting
        onlineConnection?.findRandomMatch(
            onWaiting = { _onlineState.value = OnlineUiState.Waiting },
            onMatched = { code, turn -> onOnlineMatched(code, turn) },
            onFailed = {
                _onlineState.value = OnlineUiState.Error("Server error")
            }
        )
    }

    private fun onOnlineMatched(code: String, turn: Boolean) {
        isHost = turn
        connection = onlineConnection
        connectionType = "online"
        resetForNewGame()
        _onlineState.value = OnlineUiState.Matched(code, turn)
        _navigateToGame.value = true
    }

    fun consumeNavigation() {
        _navigateToGame.value = false
    }

    private fun resetForNewGame() {
        cancelTimer()
        turnTimeout = 0L
        _remainingSeconds.value = 0L
        boardInitialized = false
        _boardState.value = GameBoardState()
        callbacksBound = false
    }

    fun resetOnlineState() {
        _onlineState.value = OnlineUiState.Idle
    }

    private val _boardState = MutableStateFlow(GameBoardState())
    val boardState: StateFlow<GameBoardState> = _boardState.asStateFlow()

    var boardInitialized: Boolean = false
        private set

    fun initBoard(defaultTurn: Int) {
        if (boardInitialized) return
        _boardState.value = GameBoardState(player1Turn = defaultTurn, diceEnabled = defaultTurn == 1)
        boardInitialized = true
    }

    fun updateBoard(transform: (GameBoardState) -> GameBoardState) {
        _boardState.value = transform(_boardState.value)
    }

    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()

    private fun emit(e: GameEvent) {
        _events.tryEmit(e)
    }

    private var callbacksBound = false

    fun bindConnectionCallbacks() {
        if (callbacksBound) return
        val conn = connection ?: return
        callbacksBound = true

        conn.setOnStartLoading(
            {
                cancelTimer()
                emit(GameEvent.StartLoading)
            },
            { text -> emit(GameEvent.LoadingTextChanged(text)) }
        )
        conn.setOnStopLoading { emit(GameEvent.StopLoading) }

        conn.setOnReceiveData { json ->
            val newTurn = json.getInt("turn")
            val newP1 = json.getInt("player2")
            val newP2 = json.getInt("player1")
            updateBoard {
                it.copy(
                    player1Turn = newTurn,
                    player1Pos = newP1,
                    player2Pos = newP2,
                    diceEnabled = newTurn == 1
                )
            }
            turnTimeout = json.getLong("seconds")
            runTimerLoop()
            emit(GameEvent.RejoinData(json))
        }

        conn.onMoveReceived { move ->
            emit(GameEvent.RemoteMove(move.diceVal))
        }
    }

    var turnTimeout: Long = 0L

    private val _remainingSeconds = MutableStateFlow(0L)
    val remainingSeconds: StateFlow<Long> = _remainingSeconds.asStateFlow()

    private var timerJob: Job? = null
    private var grPeriodJob: Job? = null

    fun startTurnTimer(durationMs: Long = 30_000L) {
        turnTimeout = System.currentTimeMillis() + durationMs
        runTimerLoop()
    }

    fun scheduleTimerFromCurrentTimeout() {
        runTimerLoop()
    }

    fun cancelTimer() {
        timerJob?.cancel()
        grPeriodJob?.cancel()
    }

    private fun runTimerLoop() {
        timerJob?.cancel()
        grPeriodJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                val remaining = turnTimeout - System.currentTimeMillis()
                if (remaining <= 0L) {
                    _remainingSeconds.value = 0L
                    handleTimerExpired()
                    break
                }
                _remainingSeconds.value = remaining / 1000
                delay(1000)
            }
        }
    }

    private fun handleTimerExpired() {
        if (_boardState.value.player1Turn == 1) {
            forfeit()
            emit(GameEvent.SelfTimedOut)
        } else {
            grPeriodJob?.cancel()
            grPeriodJob = viewModelScope.launch {
                delay(10_000)
                if (turnTimeout - System.currentTimeMillis() <= 0L) {
                    connection?.send(JSONObject().apply { put("type", "max_wait_leave") }, true)
                    connection?.send(
                        JSONObject().apply {
                            put("type", "game_over")
                            put("winner", uniqueUid)
                        }, true
                    )
                    emit(GameEvent.OpponentTimedOut)
                }
            }
        }
    }

    fun forfeit() {
        connection?.sendMove(GameMove(localPlayerName, -1))
    }

    fun resendStateAfterOpponentReconnect() {
        turnTimeout += 4000L
        val b = _boardState.value
        connection?.send(
            JSONObject().apply {
                put("type", "rejoin_data")
                put("player1", b.player1Pos)
                put("player2", b.player2Pos)
                put("turn", if (b.player1Turn == 1) 2 else 1)
                put("seconds", turnTimeout - 2000L)
            }, true
        )
        runTimerLoop()
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        grPeriodJob?.cancel()
        connection?.disconnect()
    }
}
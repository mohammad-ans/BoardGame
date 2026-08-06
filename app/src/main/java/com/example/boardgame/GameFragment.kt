package com.example.boardgame

import androidx.core.graphics.toColorInt
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.example.boardgame.databinding.GamefragmentBinding
import kotlinx.coroutines.Job
import org.json.JSONObject

class GameFragment : Fragment(R.layout.gamefragment) {
    private val sessionViewModel: GameSessionViewModel by navGraphViewModels(R.id.nav_graph)
    private lateinit var gameConnection: GameConnection
    lateinit var tiles: ArrayList<TextView>
    lateinit var player2Name: String
    lateinit var player1Name: String
    var sizeTile: Int = 0
    var heightTile: Int = 0
    private var _binding: GamefragmentBinding? = null
    private val binding get() = _binding!!
    lateinit var animationLaunch: Job
    lateinit var uniqueUid: String
    var winningPoints = 50
    var isMuted: Boolean = true
    var prefs: SharedPreferences? = null
    var timeOutFunc: Job? = null

    val snakes = mapOf(
        49 to 38, 47 to 36, 42 to 33, 44 to 33, 40 to 29,
        35 to 24, 30 to 19, 23 to 12, 15 to 4
    )
    val ladders = mapOf(
        34 to 46, 32 to 41, 16 to 27, 11 to 28, 3 to 14
    )

    private var player1Pos = 0
    private var player2Pos = 0
    private var player1Turn = 1
    private var diceVal = 1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = GamefragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        prefs = requireContext().getSharedPreferences("game_prefs", Context.MODE_PRIVATE)
        gameConnection = sessionViewModel.connection
            ?: throw IllegalStateException("Game Fragment reached with no active connection")

        gameConnection.startVoiceChat()
        if (sessionViewModel.connectionType in listOf("bot", "pvp"))
            binding.muteButton.visibility = View.GONE

        isMuted = true
        gameConnection.setMuted(isMuted)
        binding.muteButton.setBackgroundResource(if (isMuted) R.drawable.ic_mic_stop else R.drawable.ic_mic)
        binding.muteButton.setOnClickListener {
            isMuted = !isMuted
            gameConnection.setMuted(isMuted)
            binding.muteButton.setBackgroundResource(if (isMuted) R.drawable.ic_mic_stop else R.drawable.ic_mic)
        }

        val isHost = sessionViewModel.isHost
        player1Name = getCurrentName()
        player2Name = prefs?.getString("username2", "Player 2")!!
        uniqueUid = prefs?.getString("uuid", null)!!

        sessionViewModel.localPlayerName = player1Name
        sessionViewModel.opponentName = player2Name
        sessionViewModel.uniqueUid = uniqueUid

        val defaultTurn = if (isHost) 1 else 2
        sessionViewModel.initBoard(defaultTurn)
        sessionViewModel.bindConnectionCallbacks()

        binding.resetGameOverlay.setOnClickListener {
            gameConnection.sendMove(GameMove(player1Name, -2))
            resetGame(false)
        }

        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            sizeTile = resources.displayMetrics.heightPixels / 9
            heightTile = (sizeTile * 3) / 4
        } else {
            sizeTile = resources.displayMetrics.widthPixels / 6
            heightTile = (sizeTile * 3) / 4
        }
        tiles = ArrayList()
        var i = 50
        var j: Int
        while (i > 0) {
            j = i - 5
            while (i > j) { addTile(i); i-- }
            i -= 4
            while (i <= j) { addTile(i); i++ }
            i -= 6
        }

        binding.resetGame.setOnClickListener { resetGameCheck() }
        binding.diceImage.setOnClickListener {
            diceRoll()
        }
        if (!sessionViewModel.timerStarted)
            sessionViewModel.startTurnTimer()
        else
            sessionViewModel.ensureTimer()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sessionViewModel.boardState.collect { state ->
                    boardCreate(state, animate = false)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sessionViewModel.remainingSeconds.collect { secs ->
                    binding.timeout?.text = getString(R.string.timeout, secs)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sessionViewModel.events.collect { event -> handleEvent(event) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sessionViewModel.onlineState.collect { state -> render(state) }
            }
        }

    }
    private fun render(state: OnlineUiState) {
        if(state is  OnlineUiState.Error){
            findNavController().popBackStack()
        }
    }
    private fun boardCreate(state: GameBoardState, animate: Boolean) {
        player1Pos = state.player1Pos
        player2Pos = state.player2Pos
        player1Turn = state.player1Turn
        diceVal = state.diceVal

        binding.player1.text = getString(R.string.player_1_dynamic, player1Name, player1Pos)
        binding.player2.text = getString(R.string.player_2_dynamic, player2Name, player2Pos)
        binding.playerTurn.text = getString(
            R.string.player_turn_dynamic,
            if (player1Turn == 1) player1Name else player2Name
        )
        binding.diceImage.isEnabled = state.diceEnabled
        updateDiceImg(diceVal)

        if (state.fireworksRunning && !binding.fireworks.isRunning()) {
            binding.fireworks.visibility = View.VISIBLE
            binding.fireworks.start()
            binding.overlayGameOver.visibility = View.VISIBLE
            val name = if (player1Pos >= winningPoints) player1Name else player2Name
            binding.playerWins.text = getString(R.string.player_wins, name)
            binding.diceImage.isEnabled = false
        }

        tiles.getOrNull(0)?.doOnLayout {
            if (player2Pos != 0) changePosition(binding.player2icon, 0, min(player2Pos - 1, 49))
            if (player1Pos != 0) changePosition(binding.player1icon, 0, min(player1Pos - 1, 49))
        }
    }

    private fun handleEvent(event: GameEvent) {
        when (event) {
            is GameEvent.RemoteMove -> onRemoteMove(event.diceVal)
            is GameEvent.RejoinData -> rejoinDataHandle(event.json)
            is GameEvent.LoadingTextChanged -> binding.loadingMsg.text = event.text
            is GameEvent.StartLoading -> {
                binding.overlayWait.visibility = View.VISIBLE
                binding.overlayWait.bringToFront()
                binding.loadingMsg.text = getString(R.string.own_wait)
            }
            is GameEvent.StopLoading -> {
                binding.overlayWait.visibility = View.GONE
            }
            is GameEvent.SelfTimedOut -> {
                goBack()
                winner(player2Name)
            }
            is GameEvent.OpponentTimedOut -> {
                goBack()
                binding.overlayWait.visibility = View.GONE
                if (!binding.fireworks.isRunning()) winner(player1Name)
            }
        }
    }
    private fun onRemoteMove(diceVal: Int) {
        Log.e("Move receive", "$diceVal")
        when (diceVal) {
            -1 -> {
                binding.overlayWait.visibility = View.GONE
                goBack()
                winner(player1Name)
                gameConnection.send(
                    JSONObject().apply {
                        put("type", "game_over")
                        put("winner", uniqueUid)
                    }, true
                )
            }
            -2 -> resetGame(false)
            0 -> {
                binding.overlayWait.visibility = View.GONE
                goBack()
                if (!binding.fireworks.isRunning()) {
                    winner(player1Name)
                    gameConnection.send(
                        JSONObject().apply {
                            put("type", "game_over")
                            put("winner", uniqueUid)
                        }, true
                    )
                }
            }
            -3 -> {
                sessionViewModel.cancelTimer()
                binding.overlayWait.visibility = View.VISIBLE
                binding.overlayWait.bringToFront()
                binding.loadingMsg.text = getString(R.string.opponent_wait)
            }
            -4 -> {
                binding.overlayWait.visibility = View.GONE
                goBack()
                if (!binding.fireworks.isRunning()) winner(player2Name)
            }
            -5 -> sessionViewModel.resendStateAfterOpponentReconnect()
            else -> diceHandler(diceVal)
        }
    }

    private fun rejoinDataHandle(json: JSONObject) {
        binding.overlayWait.visibility = View.GONE
        if (player1Pos > 49) {
            if (!binding.fireworks.isRunning())
                gameConnection.send(
                    JSONObject().apply {
                        put("type", "game_over")
                        put("winner", uniqueUid)
                    }, true
                )
            winner(player1Name)
        } else if (player2Pos > 49) {
            winner(player2Name)
        }
    }

    fun enableDice() {
        binding.diceImage.isEnabled = true
    }


    fun addTile(i: Int) {
        val temp = TextView(requireContext())
        temp.text = "$i"

        val params = GridLayout.LayoutParams()
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        params.height = (sizeTile * 3) / 4

        temp.gravity = Gravity.CENTER
        temp.textSize = 14f
        temp.setTypeface(temp.typeface, Typeface.BOLD)

        if (((i - 1) % 2) == 0)
            temp.setBackgroundColor("#dbffcd".toColorInt())
        else
            temp.setBackgroundColor("#669ca4".toColorInt())
        temp.layoutParams = params

        binding.grid.addView(temp)
        val tempVar = i % 10
        if (tempVar in 6..9 || tempVar == 0)
            tiles.add(0, temp)
        else
            tiles.add(tempVar - 1, temp)
    }

    fun diceRoll() {
        diceHandler((1..6).random())
    }

    fun winner(playerName: String) {
        timeOutFunc?.cancel()
        binding.playerWins.text = getString(R.string.player_wins, playerName)
        binding.overlayGameOver.visibility = View.VISIBLE
        binding.playerTurn.text = getString(R.string.game_over)
        binding.diceImage.isEnabled = false
        binding.fireworks.visibility = View.VISIBLE
        binding.fireworks.bringToFront()
        binding.fireworks.start()

        sessionViewModel.updateBoard { it.copy(fireworksRunning = true, diceEnabled = false) }
    }

    fun diceHandler(diceVal: Int) {
        binding.diceImage.isEnabled = false

        binding.diceImage.animate().rotationBy(360f).duration = 400

        lifecycleScope.launch {
            delay(300)
            applyMove(diceVal)
        }
    }


    suspend fun applyMove(diceVal: Int) {

        updateDiceImg(diceVal)
        if(timeOutFunc != null)
            timeOutFunc?.cancel()

        if (player1Turn == 1) {
            sessionViewModel.cancelTimer()
            player1Turn = 2
            val start = player1Pos - 1
            player1Pos += diceVal
            gameConnection.sendMove(GameMove(player1Name, diceVal))

            mainAnimation(start, if (player1Pos <= 50) (player1Pos - 1) else 49) {
                setColor(if ((player1Pos - 1) < 50) (player1Pos - 1) else 49)
            }


            if (snakes.containsKey(player1Pos)) {
                animationLaunch.join()
                val temp = player1Pos - 1
                player1Pos = snakes.getValue(player1Pos)
                animationLaunch = reverseAnimation(temp, player1Pos - 1) {
                    setColor(player1Pos - 1)
                }
            }
            if (ladders.containsKey(player1Pos)) {
                animationLaunch.join()
                val temp = player1Pos - 1
                player1Pos = ladders.getValue(player1Pos)
                stairAnimation(temp, player1Pos - 1) {
                    setColor(player1Pos - 1)
                }

            }
            binding.player1.text = getString(R.string.player_1_dynamic, player1Name, player1Pos)
            if (player1Pos >= winningPoints) {
                animationLaunch.join()
                changePosition(binding.player1icon, start, winningPoints - 1)
                resetTiles()
                tiles[49].setBackgroundColor(Color.BLUE)
                winner(player1Name)
                gameConnection.send(
                    JSONObject().apply {
                        put("type", "game_over")
                        put("winner", uniqueUid)
                    }, true
                )
                return
            }
            animationLaunch.join()
            changePosition(binding.player1icon, start, player1Pos - 1)
            if (sessionViewModel.connectionType == "bot") {
                lifecycleScope.launch {
                    delay(1000)
                    diceRoll()
                }
            }
        } else {
            sessionViewModel.cancelTimer()
            player1Turn = 1
            val start = player2Pos - 1
            player2Pos += diceVal

            mainAnimation(start, if (player2Pos <= 50) player2Pos - 1 else 49) {
                setColor(if (player2Pos <= 50) (player2Pos - 1) else 49)
            }

            if (snakes.containsKey(player2Pos)) {
                animationLaunch.join()
                val temp = player2Pos - 1
                player2Pos = snakes.getValue(player2Pos)
                animationLaunch = reverseAnimation(temp, player2Pos - 1) {
                    setColor(player2Pos - 1)
                }

            }
            if (ladders.containsKey(player2Pos)) {
                animationLaunch.join()
                val temp = player2Pos - 1
                player2Pos = ladders.getValue(player2Pos)
                stairAnimation(temp, player2Pos - 1) {
                    setColor(player2Pos - 1)
                }

            }
            binding.player2.text = getString(R.string.player_2_dynamic, player2Name, player2Pos)
            if (player2Pos >= winningPoints) {
                animationLaunch.join()
                changePosition(binding.player2icon, start, winningPoints - 1)
                resetTiles()
                tiles[49].setBackgroundColor(Color.RED)
                syncBoard()
                winner(player2Name)
                return
            }
            animationLaunch.join()
            changePosition(binding.player2icon, start, player2Pos - 1)
            enableDice()
        }
        if (sessionViewModel.connectionType == "pvp") {
            enableDice()
        }
        binding.playerTurn.text = getString(R.string.player_turn_dynamic, if (player1Turn == 1) player1Name else player2Name)
        syncBoard()
        sessionViewModel.startTurnTimer()
    }
    private fun syncBoard() {
        sessionViewModel.updateBoard {
            it.copy(
                player1Pos = player1Pos,
                player2Pos = player2Pos,
                player1Turn = player1Turn,
                diceVal = diceVal,
                diceEnabled = binding.diceImage.isEnabled
            )
        }
    }

    fun resetGame(midGameReset: Boolean = false) {
        if (midGameReset)
            winner(player2Name)
        binding.fireworks.stop()
        binding.fireworks.visibility = View.GONE
        binding.overlayGameOver.visibility = View.GONE
        player1Pos = 0
        player2Pos = 0
        if(sessionViewModel.connectionType == "pvp" || sessionViewModel.connectionType == "bot")
            player1Turn = 1

        binding.player1.text = getString(R.string.player_1_dynamic, player1Name, 0)
        binding.player2.text = getString(R.string.player_2_dynamic, player2Name, 0)
        binding.playerTurn.text = getString(R.string.player_turn_dynamic, if (player1Turn == 1) player1Name else player2Name)
        binding.diceImage.setImageResource(R.drawable.dice_one)
        enableDice()
        changePosition(binding.player2icon, player2Pos - 1, 0)
        changePosition(binding.player1icon, player1Pos - 1, 0)
        resetTiles()
        sessionViewModel.updateBoard {
            GameBoardState(
                player1Pos = 0, player2Pos = 0, player1Turn = player1Turn, diceVal = 1, diceEnabled = true, fireworksRunning = false
            )
        }
        sessionViewModel.startTurnTimer()
    }

    fun resetGameCheck() {
        if (gameEnd()) {
            resetGame(false)
        } else if (player1Pos == 0 && player2Pos == 0) {
            Toast.makeText(
                requireContext(),
                "Game is already at starting point.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            if (sessionViewModel.connectionType == "pvp" || sessionViewModel.connectionType == "bot")
                showResetConfirmation()
            else
                showResetConfirmationOnline()
        }
    }

    fun showResetConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Reset Game?")
            .setMessage("Current game in progress. Are you sure you want to reset?")
            .setPositiveButton("Reset", { _, _ -> resetGame(false) })
            .setNegativeButton("Nope", null)
            .setCancelable(true)
            .show()
    }

    fun showResetConfirmationOnline() {
        AlertDialog.Builder(requireContext())
            .setTitle("Reset Game?")
            .setMessage("Current game is in progress. Do you want to forfeit?")
            .setPositiveButton("Reset", { _, _ -> sendForfeitSignal() })
            .setNegativeButton("Nope", null)
            .setCancelable(true)
            .show()
    }

    fun sendForfeitSignal() {
        gameConnection.sendMove(GameMove(player1Name, -1))
        goBack()
        winner(player2Name)
    }


    fun animation1(pos: Int) {
        resetTiles()
        if (pos > -1)
            tiles[pos].setBackgroundColor(Color.YELLOW)
    }

    fun animation2(pos: Int) {
        resetTiles()
        if (pos > -1)
            tiles[pos].setBackgroundColor(Color.GREEN)
    }

    fun mainAnimation(start: Int, end: Int, func: () -> Unit) {

        animationLaunch = lifecycleScope.launch {
            for (pos in start..end) {
                animation1(pos)
                delay(100)
            }
            delay(150)
            func()
        }
    }

    fun stairAnimation(start: Int, end: Int, func: () -> Unit) {

        animationLaunch = lifecycleScope.launch {
            for (pos in start..end) {
                animation2(pos)
                delay(100)
            }
            delay(150)
            func()
        }
    }

    fun gameEnd(): Boolean {
        return (player1Pos >= 50 || player2Pos >= 50)
    }

    fun reverseAnimation(start: Int, end: Int, func: () -> Unit): Job {
        return lifecycleScope.launch {
            for (pos in start downTo end) {
                resetTiles()
                tiles[pos].setBackgroundColor(Color.RED)
                delay(100)
            }
            delay(150)
            func()
        }
    }

    fun setColor(i: Int) {
        if ((i % 2) == 0)
            tiles[i].setBackgroundColor("#dbffcd".toColorInt())
        else
            tiles[i].setBackgroundColor("#669ca4".toColorInt())
    }

    fun resetTiles() {
        for ((index, tile) in tiles.withIndex()) {
            setColor(index)
        }
    }

    fun changePosition(player: ImageView, start: Int, end: Int) {
        val rowCheck = end / 5
        val p = player.layoutParams as ConstraintLayout.LayoutParams
        val bottom = heightTile * rowCheck
        var left : Int
        if ((rowCheck % 2) == 0) {
            left = (end % 5) * tiles[0].width
        } else {
            left = abs((end % 5) - 4) * tiles[0].width
        }
        p.setMargins(left, p.topMargin, p.rightMargin, bottom)
        player.layoutParams = p
    }

    override fun onDestroyView() {
        super.onDestroyView()
        gameConnection.stopVoiceChat()
        if (isRemoving) {
            sessionViewModel.cancelTimer()
            gameConnection.disconnect()
        }
        _binding = null
    }

    fun getCurrentName(): String {
        val prefs = requireContext().getSharedPreferences("game_prefs", Context.MODE_PRIVATE)
        return prefs.getString("username", "Player 1")!!
    }
    private fun goBack() {
        binding.resetGameOverlay.text = getString(R.string.go_back)
        binding.resetGameOverlay.setOnClickListener {
            findNavController().popBackStack()
        }
    }
    fun updateDiceImg(diceVal: Int) {
        val imgSrc = when (diceVal) {
            1 -> R.drawable.dice_one
            2 -> R.drawable.dice_two
            3 -> R.drawable.dice_three
            4 -> R.drawable.dice_four
            5 -> R.drawable.dice_five
            else -> R.drawable.dice_six
        }
        binding.diceImage.setImageResource(imgSrc)
    }

    override fun onStop() {
        super.onStop()
        _binding.let {
            if(it?.fireworks?.isRunning() ?: false)
                it.fireworks.stop()
        }
    }
}
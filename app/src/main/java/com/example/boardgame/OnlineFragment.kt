package com.example.boardgame

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import kotlinx.coroutines.launch

class OnlineFragment : Fragment(R.layout.online_setup_fragment) {
    private val sessionViewModel: GameSessionViewModel by navGraphViewModels(R.id.nav_graph)

    private val serverUrl = "wss://yappyyap.xyz:443/ws"
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var codeArea: EditText
    private lateinit var statusJoin: TextView
    private lateinit var roomBtn: Button
    private lateinit var randomMatch: Button
    private lateinit var goToJoin: Button
    private lateinit var hostBtn: Button
    private lateinit var joinBtn: Button
    private lateinit var statusHost: TextView
    private lateinit var progressHost: ProgressBar
    private lateinit var roomCodeT: TextView
    private lateinit var mainLayout: LinearLayout
    private lateinit var roomLayout: LinearLayout
    private lateinit var joinLayout: LinearLayout
    private lateinit var hostLayout: LinearLayout
    private lateinit var currentLayout: LinearLayout
    private var backStack = mutableListOf<LinearLayout>()
    private lateinit var backBtn: Button
    private var prefs: SharedPreferences? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = requireContext().getSharedPreferences("game_prefs", Context.MODE_PRIVATE)

        if (sessionViewModel.onlineConnection == null) {
            val username = prefs?.getString("username", "Player 2")
            sessionViewModel.onlineConnection =
                OnlineGameConnection(requireContext(), username!!, serverUrl)
        }

        mainLayout = view.findViewById(R.id.online_main)
        currentLayout = mainLayout
        roomLayout = view.findViewById(R.id.online_room)
        joinLayout = view.findViewById(R.id.online_room_join)
        hostLayout = view.findViewById(R.id.online_room_host)

        backBtn = view.findViewById(R.id.backBtnMain)
        status = view.findViewById(R.id.overall_status)
        progress = view.findViewById(R.id.onlineProgressSearching)
        randomMatch = view.findViewById(R.id.onlineBtnRandom)
        roomBtn = view.findViewById(R.id.onlineRoom)

        goToJoin = view.findViewById(R.id.onlineBtnJoin)
        joinBtn = view.findViewById(R.id.onlineRoomJoin)
        hostBtn = view.findViewById(R.id.onlineBtnHost)

        codeArea = view.findViewById(R.id.room_code_input)
        statusJoin = view.findViewById(R.id.join_friendly_room_status)
        statusHost = view.findViewById(R.id.status_host)
        progressHost = view.findViewById(R.id.onlineProgressHost)
        roomCodeT = view.findViewById(R.id.room_code)

        joinBtn.setOnClickListener { onJoinLis() }
        hostBtn.setOnClickListener { onCreateRoomLis() }
        roomBtn.setOnClickListener {
            roomLayout.visibility = View.VISIBLE
            backStack.add(mainLayout)
            currentLayout = roomLayout
            mainLayout.visibility = View.GONE
        }
        randomMatch.setOnClickListener { onRandomMatchLis() }
        goToJoin.setOnClickListener {
            joinLayout.visibility = View.VISIBLE
            backStack.add(roomLayout)
            currentLayout = joinLayout
            roomLayout.visibility = View.GONE
        }
        backBtn.setOnClickListener {
            sessionViewModel.resetOnlineState()
            if (backStack.isEmpty()) {
                findNavController().popBackStack()
            } else {
                val tempLayout = backStack.removeAt(backStack.lastIndex)
                tempLayout.visibility = View.VISIBLE
                currentLayout.visibility = View.GONE
                currentLayout = tempLayout
                goToJoin.isEnabled = true
                roomBtn.isEnabled = true
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sessionViewModel.onlineState.collect { state -> render(state) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sessionViewModel.navigateToGame.collect { shouldNavigate ->
                    if (shouldNavigate) {
                        findNavController().navigate(R.id.online_to_game)
                        sessionViewModel.consumeNavigation()
                    }
                }
            }
        }
    }

    private fun render(state: OnlineUiState) {
        when (state) {
            is OnlineUiState.Idle -> {
                progress.visibility = View.GONE
                progressHost.visibility = View.GONE
                status.text = ""
                statusHost.text = ""
                statusJoin.text = ""
                roomCodeT.text = ""
            }
            is OnlineUiState.Connecting -> {
                status.setTextColor(android.graphics.Color.WHITE)
                status.text = getString(R.string.finding_match)
                progress.visibility = View.VISIBLE

                statusHost.setTextColor(android.graphics.Color.WHITE)
                statusHost.text = getString(R.string.finding_match)
                progressHost.visibility = View.VISIBLE

                statusJoin.setTextColor(android.graphics.Color.WHITE)
                statusJoin.text = getString(R.string.joining)
            }
            is OnlineUiState.Waiting -> {
                status.setTextColor(android.graphics.Color.WHITE)
                status.text = getString(R.string.join_wait)
                progress.visibility = View.VISIBLE
            }
            is OnlineUiState.RoomCreated -> {
                progressHost.visibility = View.VISIBLE
                roomCodeT.text = getString(R.string.room_code, state.roomCode)
                statusHost.setTextColor(android.graphics.Color.WHITE)
                statusHost.text = getString(R.string.join_wait)
            }
            is OnlineUiState.Matched -> {}
            is OnlineUiState.Error -> {
                if (findNavController().currentDestination?.id != R.id.online_fragment) {
                    findNavController().popBackStack()
                }
                status.setTextColor(android.graphics.Color.RED)
                status.text = state.message
                progress.visibility = View.GONE
                statusHost.setTextColor(android.graphics.Color.RED)
                statusHost.text = state.message
                statusJoin.setTextColor(android.graphics.Color.RED)
                statusJoin.text = state.message
                progressHost.visibility = View.GONE
                roomBtn.isEnabled = true
                randomMatch.isEnabled = true
                goToJoin.isEnabled = true
            }
        }
    }

    private fun onCreateRoomLis() {
        goToJoin.isEnabled = false
        roomLayout.visibility = View.GONE
        hostLayout.visibility = View.VISIBLE
        backStack.add(roomLayout)
        currentLayout = hostLayout
        sessionViewModel.createRoom()
    }

    private fun onJoinLis() {
        val code = codeArea.text.toString().trim().uppercase()
        if (code.isEmpty()) {
            statusJoin.setTextColor(android.graphics.Color.RED)
            statusJoin.text = getString(R.string.empty_code)
            return
        }
        statusJoin.setTextColor(android.graphics.Color.WHITE)
        statusJoin.text = getString(R.string.joining)
        sessionViewModel.joinRoom(code)
    }

    private fun onRandomMatchLis() {
        status.setTextColor(android.graphics.Color.WHITE)
        status.text = getString(R.string.finding_match)
        progress.visibility = View.VISIBLE
        roomBtn.isEnabled = false
        randomMatch.isEnabled = false
        sessionViewModel.findRandomMatch()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        sessionViewModel.cancelTimer()
    }
}
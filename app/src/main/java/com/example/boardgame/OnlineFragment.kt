package com.example.boardgame

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.compose.ui.graphics.Color
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels

class OnlineFragment: Fragment(R.layout.online_setup_fragment) {
    private val sessionViewModel: GameSessionViewModel by navGraphViewModels(R.id.nav_graph)
    private lateinit var connection: OnlineGameConnection

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
        val username = prefs?.getString("username", "Player 2")
        connection = OnlineGameConnection(requireContext(), username!!, serverUrl)

        mainLayout = view.findViewById<LinearLayout>(R.id.online_main)
        currentLayout = mainLayout
        roomLayout = view.findViewById<LinearLayout>(R.id.online_room)
        joinLayout = view.findViewById<LinearLayout>(R.id.online_room_join)
        hostLayout = view.findViewById<LinearLayout>(R.id.online_room_host)

        backBtn = view.findViewById<Button>(R.id.backBtnMain)
        status = view.findViewById<TextView>(R.id.overall_status)
        progress = view.findViewById<ProgressBar>(R.id.onlineProgressSearching)
        randomMatch = view.findViewById<Button>(R.id.onlineBtnRandom)
        roomBtn = view.findViewById<Button>(R.id.onlineRoom)

        goToJoin = view.findViewById<Button>(R.id.onlineBtnJoin)
        joinBtn = view.findViewById<Button>(R.id.onlineRoomJoin)
        hostBtn = view.findViewById<Button>(R.id.onlineBtnHost)

        codeArea = view.findViewById<EditText>(R.id.room_code_input)
        statusJoin = view.findViewById<TextView>(R.id.join_friendly_room_status)
        statusHost = view.findViewById<TextView>(R.id.status_host)
        progressHost = view.findViewById<ProgressBar>(R.id.onlineProgressHost)
        roomCodeT = view.findViewById<TextView>(R.id.room_code)

        joinBtn.setOnClickListener { onJoinLis() }

        hostBtn.setOnClickListener {
            onCreateRoomLis()
        }
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
            if(backStack.isEmpty()){
                findNavController().popBackStack()
            }
            else{
                val tempLayout = backStack.removeAt(backStack.lastIndex)
                tempLayout.visibility = View.VISIBLE
                currentLayout.visibility = View.GONE
                currentLayout = tempLayout
                goToJoin.isEnabled = true
                roomBtn.isEnabled = true
            }

        }
    }
    private fun setBusy() {
        status.setTextColor(android.graphics.Color.WHITE)
        status.text = getString(R.string.finding_match)
        progress.visibility = View.VISIBLE
    }
    private fun onCreateRoomLis() {
        goToJoin.isEnabled = false
        roomLayout.visibility = View.GONE
        hostLayout.visibility = View.VISIBLE
        backStack.add(roomLayout)
        currentLayout = hostLayout
        progressHost.visibility = View.VISIBLE
        connection.createRoom(
            onCreated = {roomCode->
                requireActivity().runOnUiThread {
                    roomCodeT.text = getString(R.string.room_code, roomCode)
                    statusHost.setTextColor(android.graphics.Color.WHITE)
                    statusHost.text = getString(R.string.join_wait)
                }
            },
            onFailed = {
                requireActivity().runOnUiThread {
                    if(findNavController().currentDestination?.id != R.id.online_fragment)
                        findNavController().popBackStack()
                    statusHost.setTextColor(android.graphics.Color.RED)
                    statusHost.text = getString(R.string.server_error)
                    goToJoin.isEnabled = true
                    progressHost.visibility = View.GONE
                }
                       },
            onMatched = { _, turn ->
                onConnected(turn)
            }
        )

    }
    private fun onJoinLis() {
        val code = codeArea.text.toString().trim().uppercase()
        if (code.isEmpty()){
            statusJoin.setTextColor(android.graphics.Color.RED)
            statusJoin.text = getString(R.string.empty_code)
            return
        }
        statusJoin.setTextColor(android.graphics.Color.WHITE)
        statusJoin.text = getString(R.string.joining)
        connection.joinRoom(
            roomCode = code,
            onJoined = {_, turn ->
                onConnected(turn)},
            onFailed = {message ->
                requireActivity().runOnUiThread {
                    if (findNavController().currentDestination?.id != R.id.online_fragment)
                        findNavController().popBackStack()
                    statusJoin.setTextColor(android.graphics.Color.RED)
                    statusJoin.text = message
                }
            }
        )
    }
    private fun onRandomMatchLis() {
        setBusy()
        roomBtn.isEnabled = false
        randomMatch.isEnabled = false
        connection.findRandomMatch(
            onWaiting = {requireActivity().runOnUiThread {
                status.setTextColor(android.graphics.Color.WHITE)
                status.text = getString(R.string.join_wait)
            }},
            onMatched = {_, turn ->
                onConnected(turn)},
            onFailed = {
                requireActivity().runOnUiThread {
                    if(findNavController().currentDestination?.id != R.id.online_fragment)
                        findNavController().popBackStack()
                    status.text = getString(R.string.server_error)
                    status.setTextColor(android.graphics.Color.RED)
                    progress.visibility = View.GONE
                    roomBtn.isEnabled = true
                    randomMatch.isEnabled = true
                }
            }

        )
    }
    private fun onConnected(turn: Boolean) {
        sessionViewModel.isHost = turn
        sessionViewModel.connection = connection
        sessionViewModel.connectionType = "online"
        requireActivity().runOnUiThread {
            findNavController().navigate(R.id.online_to_game)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if(isRemoving && sessionViewModel.connection == null)
            connection.disconnect()
    }

}
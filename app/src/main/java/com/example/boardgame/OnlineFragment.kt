package com.example.boardgame

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels

class OnlineFragment: Fragment(R.layout.online_setup_fragment) {
    private val sessionViewModel: GameSessionViewModel by navGraphViewModels(R.id.nav_graph)
    private lateinit var connection: OnlineGameConnection

    private val serverUrl = ""
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var codeArea: EditText
    private lateinit var joinRoom: Button
    private lateinit var createRoom: Button
    private lateinit var randomMatch: Button
    private var prefs: SharedPreferences? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        status = view.findViewById<TextView>(R.id.online_status)
        progress = view.findViewById<ProgressBar>(R.id.progress_online)
        codeArea = view.findViewById<EditText>(R.id.room_code)
        joinRoom = view.findViewById<Button>(R.id.join_friendly_room)
        randomMatch = view.findViewById<Button>(R.id.randomMatchBtn)
        createRoom = view.findViewById<Button>(R.id.create_room)
        connection = OnlineGameConnection(requireContext(), "ws://localhost:8000", serverUrl)
        prefs = requireContext().getSharedPreferences("game_prefs", Context.MODE_PRIVATE)


        joinRoom.setOnClickListener { onJoinLis() }
        createRoom.setOnClickListener { onCreateRoomLis() }
        randomMatch.setOnClickListener { onRandomMatchLis() }
    }
    private fun setBusy(message : String) {
        status.text = message
        progress.visibility = View.VISIBLE
        listOf(createRoom, joinRoom, randomMatch).forEach { it.isEnabled = false }
    }
    private fun onCreateRoomLis() {
        setBusy("Creating Room...")
        connection.createRoom(
            onCreated = {roomCode->
                requireActivity().runOnUiThread {
                    status.text = "Room coode: $roomCode\nWaiting for opponent to join..."
                }
            },
            onFailed = {showFailure("Could not reach server")},
            onMatched = {onConnected()}
        )

    }
    private fun onJoinLis() {
        val code = codeArea.text.toString().trim().uppercase()
        if (code.isEmpty()){
            status.text = "Enter a room code first"
            return
        }
        setBusy("Joining Room $code...")
        connection.joinRoom(
            roomCode = code,
            onJoined = {onConnected()},
            onFailed = {message -> showFailure(message)}
        )
    }
    private fun onRandomMatchLis() {
        setBusy("Finding a match")
        connection.findRandomMatch(
            onWaiting = {requireActivity().runOnUiThread { status.text = "Waiting for another player to join" }},
            onMatched = {onConnected()},
            onFailed = {showFailure("Could not reach server")}

        )
    }
    private fun onConnected() {
        sessionViewModel.connection = connection
        prefs?.edit()?.putString("username2", "Opponent")?.apply()
        requireActivity().runOnUiThread {
            findNavController().navigate(R.id.online_to_game)
        }
    }
    private fun showFailure(message : String) {
        requireActivity().runOnUiThread {
            status.text = message
            progress.visibility = View.GONE
            listOf(createRoom, joinRoom, randomMatch).forEach { it.isEnabled = true }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if(isRemoving && sessionViewModel.connection == null)
            connection.disconnect()
    }

}
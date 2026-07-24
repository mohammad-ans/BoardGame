package com.example.boardgame

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels

class OfflineFragment: Fragment(R.layout.offline_fragment) {
    private lateinit var btnBot : Button
    private lateinit var btnPvp : Button
    private val viewModel: GameSessionViewModel by navGraphViewModels(R.id.nav_graph)

    var prefs : SharedPreferences? = null
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        prefs = requireContext().getSharedPreferences("game_prefs", Context.MODE_PRIVATE)
        btnBot = view.findViewById<Button>(R.id.offline_bot)
        btnPvp = view.findViewById<Button>(R.id.offline_pvp)
        viewModel.connection = OfflineGameConnection(requireContext(), "Player 1")
        viewModel.isHost = true

        btnBot.setOnClickListener(::botHandler)
        btnPvp.setOnClickListener(::pvpHandler)
        view.findViewById<Button>(R.id.backBtnOffline).setOnClickListener {
            findNavController().popBackStack()
        }
    }
    private fun botHandler(view: View){
        findNavController().navigate(R.id.offline_to_game)
        viewModel.connectionType = "bot"
        setUsername("Bot")
    }
    private fun pvpHandler(view: View){
        findNavController().navigate(R.id.offline_to_game)
        viewModel.connectionType = "pvp"
        setUsername("Player 2")
    }
    private fun setUsername(value : String){
        prefs?.edit()?.putString("username2", value)?.apply()
    }

}
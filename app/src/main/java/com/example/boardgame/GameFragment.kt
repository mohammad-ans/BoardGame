package com.example.boardgame

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment

class GameFragment : Fragment() {
    private val sessionViewModel : GameSessionViewModel? = null
    private lateinit var gameConnection: GameConnection

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        gameConnection = sessionViewModel?.connection?: throw IllegalStateException("Game Fragment reached with no active connection")
        connection.onMoveReceived { move ->
            requireActivity().runOnUiThread{
                applyMove()
            }
        }
    }
    private fun applyMove() {}

}
package com.example.boardgame

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

class MenuFragment : Fragment(R.layout.menu) {
    override fun onViewCreated(view : View, savedInstanceState : Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<Button>(R.id.offline_mode).setOnClickListener {
            findNavController().navigate(R.id.mode_to_offline_loading)
        }
        view.findViewById<Button>(R.id.online_mode).setOnClickListener {
            findNavController().navigate(R.id.mode_to_online_loading)
        }
        view.findViewById<Button>(R.id.nearby_mode).setOnClickListener {
            findNavController().navigate(R.id.mode_to_friendly_loading)
        }
    }
}
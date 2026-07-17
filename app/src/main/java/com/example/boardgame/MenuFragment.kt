package com.example.boardgame

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

class MenuFragment : Fragment(R.layout.menu) {
    private lateinit var usernameInput: EditText
    private lateinit var usernameConfirm : Button
    private lateinit var usernameOverlay : ConstraintLayout
    override fun onViewCreated(view : View, savedInstanceState : Bundle?) {
        usernameInput = view.findViewById<EditText>(R.id.username_input)
        usernameConfirm = view.findViewById<Button>(R.id.username_input_confirm)
        usernameOverlay = view.findViewById<ConstraintLayout>(R.id.username_input_overlay)
        if(!checkUsername()){
            usernameOverlay.visibility = View.VISIBLE
        }
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
    private fun checkUsername() : Boolean{
        if(true)
            return true
        return false
    }
}
package com.example.boardgame

import android.content.Context
import android.content.SharedPreferences
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
    private lateinit var profileButton : Button
    var prefs : SharedPreferences? = null

    override fun onViewCreated(view : View, savedInstanceState : Bundle?) {
        usernameInput = view.findViewById<EditText>(R.id.username_input)
        usernameConfirm = view.findViewById<Button>(R.id.username_input_confirm)
        usernameOverlay = view.findViewById<ConstraintLayout>(R.id.username_input_overlay)
        profileButton = view.findViewById<Button>(R.id.profile_menu_icon)
        prefs = requireContext().getSharedPreferences("game_prefs", Context.MODE_PRIVATE)
        val username = getUsername()
        if(username == null){
            usernameOverlay.visibility = View.VISIBLE
        }
        usernameConfirm.setOnClickListener{
            prefs?.edit()?.putString("username", usernameInput.text.toString())?.apply()
            usernameOverlay.visibility = View.GONE
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
        profileButton.setOnClickListener {
            findNavController().navigate(R.id.mode_to_profile)
        }
    }
    private fun getUsername() : String?{
        val username = prefs?.getString("username", null)
        return username
    }
}
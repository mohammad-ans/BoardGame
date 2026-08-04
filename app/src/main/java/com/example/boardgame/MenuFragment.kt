package com.example.boardgame

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class MenuFragment : Fragment(R.layout.menu) {
    private lateinit var usernameInput: EditText
    private lateinit var usernameConfirm : Button
    private lateinit var usernameInputArea : ConstraintLayout
    private lateinit var usernameCancel : Button
    private lateinit var editUsernameButton : Button
    private lateinit var usernameValue : TextView
    private lateinit var usernameInitial : TextView
    private lateinit var goProfile: TextView
    private lateinit var leaderboard: TextView
    var prefs : SharedPreferences? = null

    override fun onViewCreated(view : View, savedInstanceState : Bundle?) {
        usernameInput = view.findViewById<EditText>(R.id.username_input)
        usernameConfirm = view.findViewById<Button>(R.id.username_input_confirm)
        usernameInputArea = view.findViewById<ConstraintLayout>(R.id.username_input_area)
        usernameCancel = view.findViewById<Button>(R.id.username_cancel_btn)
        editUsernameButton = view.findViewById<Button>(R.id.edit_username_btn)
        goProfile = view.findViewById<TextView>(R.id.go_profile)
        prefs = requireContext().getSharedPreferences("game_prefs", Context.MODE_PRIVATE)
        ensureUniqueId()
        usernameValue = view.findViewById<TextView>(R.id.username_value)
        usernameInitial = view.findViewById<TextView>(R.id.username_initial)
        leaderboard = view.findViewById<TextView>(R.id.leaderboard)

        val username = getUsername()
        usernameInitial.text = getString(R.string.username_initial, username[0])
        usernameValue.text = getString(R.string.username_val, username)

        editUsernameButton.setOnClickListener {
            usernameInputArea.visibility = View.VISIBLE
        }
        goProfile.setOnClickListener {
            findNavController().navigate(R.id.mode_to_profile)
        }
        usernameConfirm.setOnClickListener{
            val username = usernameInput.text.toString()
            if(username.isEmpty()){
                return@setOnClickListener
            }
            prefs?.edit()?.putString("username", username)?.apply()
            usernameInitial.text = getString(R.string.username_initial, username[0])
            usernameValue.text = getString(R.string.username_val, username)
            usernameInputArea.visibility = View.GONE
        }
        usernameCancel.setOnClickListener {
            usernameInputArea.visibility = View.GONE
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
        leaderboard.setOnClickListener {
            findNavController().navigate(R.id.mode_to_leaderboard)
        }
    }
    private fun getUsername() : String{
        val username = prefs?.getString("username", null)
        if (username == null || username.isEmpty()) {
            prefs?.edit()?.putString("username", "Username")
            return "Username"
        }
        return username
    }
    private fun ensureUniqueId() {
        val username = prefs?.getString("uuid", null)
        if(username == null){
            @OptIn(ExperimentalUuidApi::class)
            val temp = Uuid.random().toString()
            prefs?.edit()?.putString("uuid", temp)?.apply()
        }
    }
}
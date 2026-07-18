package com.example.boardgame

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment

class ProfileFragment : Fragment(R.layout.profile_fragment) {
    private lateinit var username: EditText
    private lateinit var saveBtn: Button
    private lateinit var prefs : SharedPreferences
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        username = view.findViewById<EditText>(R.id.profile_edit_username)
        saveBtn = view.findViewById<Button>(R.id.profile_save_username)
        prefs = requireContext().getSharedPreferences("game_prefs", Context.MODE_PRIVATE)

        saveBtn.setOnClickListener {
            if(username.text.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "Username cannot be empty", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            prefs.edit().putString("usernameq", username.text.toString()).apply()
            Toast.makeText(requireContext(), "Username updated", Toast.LENGTH_LONG).show()
        }
    }
}
package com.example.boardgame

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.boardgame.databinding.ProfileBinding
import okhttp3.Call


class ProfileFragment: Fragment(R.layout.profile) {
    var binding: ProfileBinding? = null
    var request: Call? = null
    private lateinit var prefs: SharedPreferences
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = ProfileBinding.inflate(inflater, container, false)
        return binding?.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        prefs = requireContext().getSharedPreferences("game_prefs", Context.MODE_PRIVATE)
        val api = Api("https://yappyyap.xyz:443")
        val uuid = prefs.getString("uuid", null)!!
        request = api.fetchProfile(username = uuid, ::f){
            if(!isAdded || binding == null)
                return@fetchProfile
            val wins = prefs.getInt("wins", 0)
            val losses = prefs.getInt("losses", 0)
            binding?.wins?.text = getString(R.string.wins, wins)
            binding?.losses?.text = getString(R.string.loss, losses)
            api.postResult {
                Toast.makeText(requireContext(), "Loaded old stats. $it", Toast.LENGTH_LONG).show()
            }
        }

        val username = prefs.getString("username", null)
        binding?.profileHeading?.text = username

        binding?.backBtn?.setOnClickListener {
            findNavController().popBackStack()
        }
    }
    fun f(profile: ProfileResult?) {
        if(!isAdded || binding == null)
            return
        binding?.wins?.text = getString(R.string.wins, profile?.wins)
        binding?.losses?.text = getString(R.string.loss, profile?.losses)
        prefs.edit().apply {
            putInt("wins", profile?.wins ?: 0)
            putInt("losses", profile?.losses ?: 0)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
        request?.cancel()
    }
}
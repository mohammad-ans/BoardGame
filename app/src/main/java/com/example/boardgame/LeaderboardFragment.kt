package com.example.boardgame

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.boardgame.databinding.LeaderboardBinding

class LeaderboardFragment: Fragment(R.layout.leaderboard) {
    private lateinit var binding: LeaderboardBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = LeaderboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val api = Api("https://yappyyap.xyz:443")
        api.leaderboard(::f){
            requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), "Leaderboard load error $it", Toast.LENGTH_LONG).show()
            }
        }
        binding.backBtn.setOnClickListener {
            findNavController().popBackStack()
        }
    }
    private fun f( lst: List<ProfileResult>) {
        requireActivity().runOnUiThread {
            binding.leaderboardLst.adapter = LeaderboardAdapter(requireContext(), lst)
        }
    }
}
class LeaderboardAdapter(context: Context, private val entries: List<ProfileResult>) : ArrayAdapter<ProfileResult>(context, 0, entries) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_leaderboard, parent, false)
        val entry = entries[position]
        view.findViewById<TextView>(R.id.rank).text = (position + 1).toString()
        view.findViewById<TextView>(R.id.player_name).text = entry.localName
        view.findViewById<TextView>(R.id.wins).text = "${entry.wins}"
        return view
    }
}
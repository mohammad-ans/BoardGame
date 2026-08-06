package com.example.boardgame

import android.content.Context
import android.os.Bundle
import okhttp3.Call
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.boardgame.databinding.LeaderboardBinding

class LeaderboardFragment: Fragment(R.layout.leaderboard) {
    var binding: LeaderboardBinding? = null
    var request: Call? = null
    private lateinit var api: Api

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = LeaderboardBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        api = Api("https://yappyyap.xyz:443")
        request = api.leaderboard(::f){
            if(!isAdded || binding == null)
                return@leaderboard

            api.postResult {
                Toast.makeText(
                    requireContext(),
                    "Leaderboard load error $it",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        binding?.backBtn?.setOnClickListener {
            findNavController().popBackStack()
        }
    }
    private fun f( lst: List<ProfileResult>) {
        if(!isAdded || binding == null)
            return
        api.postResult{
            binding?.leaderboardLst?.layoutManager = LinearLayoutManager(requireContext())
            binding?.leaderboardLst?.adapter = LeaderboardAdapter(lst)
        }


    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
        request?.cancel()
    }
}
class LeaderboardAdapter(
    private val entries: List<ProfileResult>
) : RecyclerView.Adapter<LeaderboardAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val rank: TextView = view.findViewById(R.id.rank)
        val playerName: TextView = view.findViewById(R.id.player_name)
        val wins: TextView = view.findViewById(R.id.wins)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_leaderboard, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]

        holder.rank.text = (position + 1).toString()
        holder.playerName.text = entry.localName
        holder.wins.text = entry.wins.toString()
    }

    override fun getItemCount(): Int = entries.size
}
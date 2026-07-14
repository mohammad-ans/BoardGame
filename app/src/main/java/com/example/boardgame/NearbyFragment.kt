package com.example.boardgame

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels

class NearbyFragment : Fragment(R.layout.nearbysetup) {
    private val sessionViewModel: GameSessionViewModel by navGraphViewModels(R.id.nav_graph)
    private lateinit var connection: NearbyGameConnection
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var live: ListView
    private lateinit var btnHost: Button
    private lateinit var btnJoin: Button

    private var discoveredPlayers: List<DiscoveredPlayer> = emptyList()
    private lateinit var playersAdapter: ArrayAdapter<String>

    override fun onViewCreated(view : View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        status = view.findViewById<TextView>(R.id.nearbyStatus)
        live = view.findViewById<ListView>(R.id.nearbyLive)
        progress = view.findViewById<ProgressBar>(R.id.nearbyProgressSearching)
        btnJoin = view.findViewById<Button>(R.id.nearbyBtnJoin)
        btnHost = view.findViewById<Button>(R.id.nearbyBtnHost)

        connection = NearbyGameConnection(requireContext(), localPlayerName = playerName())
        playersAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, mutableListOf())
        live.adapter = playersAdapter

        btnHost.setOnClickListener{startHostFlow()}
        btnJoin.setOnClickListener { startJoinFlow() }
        live.setOnItemClickListener{_, _, position, _ ->
            val player = discoveredPlayers[position]
            status.text = "Requesting to join ${player.playerName}"
            connection.connectToEndpoint(
                endpointId = player.endpointId,
                onOpponentConnected = { onConnected(isHost = false)},
                onRejected = {
                    requireActivity().runOnUiThread {
                        status.text = "Request declined. Pick another player or try again"
                    }
                }
            )

        }

    }
    private fun startHostFlow() {
        btnHost.isEnabled = false
        btnJoin.isEnabled = false
        progress.visibility = View.VISIBLE
        Toast.makeText(requireContext(), "hihi", Toast.LENGTH_LONG).show()
        status.text = "Waiting for players to join"
        Toast.makeText(requireContext(), "hihi2", Toast.LENGTH_LONG).show()
        live.visibility = View.GONE
        Toast.makeText(requireContext(), "hihi", Toast.LENGTH_LONG).show()
        connection.startHosting(
            onIncomingRequest = {request -> showAcceptDeclineDialog(request)},
            onOpponentConnected = {onConnected(isHost = true)},
            onOpponentDisconnected = {
                requireActivity().runOnUiThread { status.text="Opponent disconnected" }
            }
        )
    }
    private fun startJoinFlow() {
        btnHost.isEnabled = false
        btnJoin.isEnabled = false
        progress.visibility = View.VISIBLE
        status.text = "Searching for nearby players..."
        live.visibility = View.VISIBLE

        connection.startDiscovery { updatedList ->
            requireActivity().runOnUiThread {
                discoveredPlayers = updatedList
                playersAdapter.clear()
                playersAdapter.addAll(updatedList.map {it.playerName})
                playersAdapter.notifyDataSetChanged()
                status.text = if (updatedList.isEmpty()) "Searching for nearby games..." else "Tap a player to join"
            }

        }
    }
    private fun showAcceptDeclineDialog(request: IncomingRequest) {
        requireActivity().runOnUiThread {
            AlertDialog.Builder(requireContext())
                .setTitle("${request.playerName} wants to join")
                .setCancelable(false)
                .setPositiveButton("Accept") {_,_ ->
                    connection.respondToRequest(request.endpointId, accept = true)
                }
                .setNegativeButton("Decline") {_, _ ->
                    connection.respondToRequest(request.endpointId, accept = false)
                    status.text = "Declined. Still waiting for players..."
                }
                .show()
        }
    }

    private fun onConnected(isHost: Boolean) {
        sessionViewModel.connection = connection
        sessionViewModel.isHost = isHost
        requireActivity().runOnUiThread {
//            move to game
        }

    }

    fun playerName() : String {
        return "Player ${(1..99).random()}"
    }

    override fun onDestroyView() {
        super.onDestroyView()

        if (isRemoving && sessionViewModel.connection == null){
            connection.disconnect()
        }
    }


}
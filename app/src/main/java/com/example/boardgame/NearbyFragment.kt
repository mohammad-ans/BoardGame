package com.example.boardgame

import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.registerForActivityResult
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.location.LocationManagerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NearbyFragment : Fragment(R.layout.nearbysetup) {

    private val requiredPermissions = arrayOf(
        android.Manifest.permission.BLUETOOTH_ADVERTISE,
        android.Manifest.permission.BLUETOOTH_SCAN,
        android.Manifest.permission.BLUETOOTH_CONNECT,
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.NEARBY_WIFI_DEVICES
    )
    private val sessionViewModel: GameSessionViewModel by navGraphViewModels(R.id.nav_graph)
    private lateinit var connection: NearbyGameConnection
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var live: ListView
    private lateinit var btnHost: Button
    private lateinit var btnJoin: Button
    private var check = 0
    private var discoveredPlayers: List<DiscoveredPlayer> = emptyList()
    private lateinit var playersAdapter: ArrayAdapter<String>
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private var pendingAction: (() -> Unit)? = null
    private val enableBluetoothLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){result ->
        if(result.resultCode == Activity.RESULT_OK)
            pendingAction?.invoke()
        else {
            Toast.makeText(
                requireContext(),
                "Bluetooth is required to connect to nearby device.",
                Toast.LENGTH_LONG
            ).show()
        }
        pendingAction = null
    }
    private val prefs by lazy { requireContext().getSharedPreferences("permission_prefs", Context.MODE_PRIVATE) }
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
        result -> if (result.values.all { it }){
            setup()
        }
        else {
            val permanentlyDenied = requiredPermissions.any{permission ->
                !ContextCompat.checkSelfPermission(requireContext(), permission).let {
                    it == PackageManager.PERMISSION_GRANTED
                } && !shouldShowRequestPermissionRationale(permission)
            }
            if(permanentlyDenied)
                showSettings()
            else
                goBack("Nearby playing requires BLUETOOTH, WIFI and LOCATION Permissions to work")

        }

    }
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
        if(!hasAllPermissions()) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    private fun setup(){
        btnHost.setOnClickListener{ checkLocation {  checkBluetooth {startHostFlow() }}}
        btnJoin.setOnClickListener { checkLocation {  checkBluetooth {startJoinFlow()} }}
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
    private fun checkBluetooth(f : () -> Unit){
        if(bluetoothAdapter?.isEnabled == true)
            f()
        else {
            pendingAction = f
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }
    private fun checkLocation(f: () -> Unit){
        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (LocationManagerCompat.isLocationEnabled(locationManager))
            f()
        else{
            AlertDialog.Builder(requireContext())
                .setTitle("Location Required")
                .setMessage("Nearby play needs location service ON")
                .setCancelable(false)
                .setPositiveButton("Open Settings"){_,_, ->
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    private fun startHostFlow() {
        btnHost.isEnabled = false
        btnJoin.isEnabled = false
        progress.visibility = View.VISIBLE
        status.text = "Waiting for players to join"
        live.visibility = View.GONE

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
                Toast.makeText(requireContext(), updatedList.get(0).playerName, Toast.LENGTH_LONG).show()
                Toast.makeText(requireContext(), "${updatedList.get(0).endpointId}", Toast.LENGTH_LONG).show()
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
        sessionViewModel.connectionType = "nearby"
        requireActivity().runOnUiThread {
            findNavController().navigate(R.id.friendly_to_game)
        }

    }

    fun playerName() : String {
        return "Player ${(1..99).random()}"
    }

    private fun hasAllPermissions(): Boolean{
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }
    }
    private fun showSettings(){
        AlertDialog.Builder(requireContext())
            .setTitle("Permissions Required")
            .setMessage("Nearby play needs Nearby and location permissions. Enable them in Settings")
            .setCancelable(false)
            .setPositiveButton("Open Settings"){_, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", requireContext().packageName, null)
                }
                startActivity(intent)
                check = 1
            }.setNegativeButton("Cancel"){_,_, ->
                goBack("No way to play without permissions")
            }.show()
    }
    private fun goBack(text : String){
        findNavController().popBackStack()
        Toast.makeText(requireContext(), text, Toast.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        if(hasAllPermissions())
            setup()
        else if(check == 1){
            goBack("Grant permissions to play")
        }

    }
    override fun onDestroyView() {
        super.onDestroyView()

        if (isRemoving && sessionViewModel.connection == null){
            connection.disconnect()
        }
    }


}
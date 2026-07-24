package com.example.boardgame

import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import kotlin.text.get



class PlayerAdapter(context: Context, private val players: List<DiscoveredPlayer>, private val onBtnClick: (player : DiscoveredPlayer) -> Unit) : ArrayAdapter<DiscoveredPlayer>(context, 0, players) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_list, parent, false)
        val player = players[position]
        view.findViewById<TextView>(R.id.player_join_name).text = player.playerName
        view.findViewById<Button>(R.id.player_join).setOnClickListener {
            onBtnClick(player)
        }
        return view
    }

}
class NearbyFragment : Fragment(R.layout.nearbysetup) {

    private val requiredPermissions = arrayOf(
        android.Manifest.permission.BLUETOOTH_ADVERTISE,
        android.Manifest.permission.BLUETOOTH_SCAN,
        android.Manifest.permission.BLUETOOTH_CONNECT,
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.NEARBY_WIFI_DEVICES,
        android.Manifest.permission.RECORD_AUDIO
    )
    private val sessionViewModel: GameSessionViewModel by navGraphViewModels(R.id.nav_graph)
    private lateinit var connection: NearbyGameConnection
    private lateinit var statusJoin: TextView
    private lateinit var live: ListView
    private lateinit var btnHost: Button
    private lateinit var btnJoin: Button
    private lateinit var hostScreen: LinearLayout
    private lateinit var mainScreen : LinearLayout
    private lateinit var joinScreen: LinearLayout
    private lateinit var backMain: Button
    private lateinit var progressJoin: ProgressBar
    private lateinit var progressHost: ProgressBar
    private lateinit var currentLayout: LinearLayout
    private var backStack = mutableListOf<LinearLayout>()
    private var check = 0
    private var discoveredPlayers: List<DiscoveredPlayer> = emptyList()
    private lateinit var playersAdapter: PlayerAdapter
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
    private var usernameTwo = "Player"

    var prefs : SharedPreferences? = null
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

        prefs = requireContext().getSharedPreferences("game_prefs", Context.MODE_PRIVATE)
        statusJoin = view.findViewById<TextView>(R.id.joining_nearby_text)
        live = view.findViewById<ListView>(R.id.nearbyLive)
        btnJoin = view.findViewById<Button>(R.id.nearbyBtnJoin)
        btnHost = view.findViewById<Button>(R.id.nearbyBtnHost)
        mainScreen = view.findViewById<LinearLayout>(R.id.nearby_main)
        currentLayout = mainScreen
        hostScreen = view.findViewById<LinearLayout>(R.id.nearby_host)
        joinScreen = view.findViewById<LinearLayout>(R.id.nearby_join)

        backMain = view.findViewById<Button>(R.id.backBtnNearby)
        progressHost = view.findViewById<ProgressBar>(R.id.nearbyProgressSearching)
        progressJoin = view.findViewById<ProgressBar>(R.id.nearbyProgressJoin)

        connection = NearbyGameConnection(requireContext(), localPlayerName = playerName())
        playersAdapter = PlayerAdapter(requireContext(), mutableListOf<DiscoveredPlayer>(), ::playerJoin)
        live.adapter = playersAdapter
        if(!hasAllPermissions()) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    private fun playerJoin(player: DiscoveredPlayer) {
        statusJoin.text = getString(R.string.outgoing_request, player.playerName)
        usernameTwo = player.playerName
        connection.connectToEndpoint(
            endpointId = player.endpointId,
            onOpponentConnected = { onConnected(isHost = false)},
            onRejected = {
                requireActivity().runOnUiThread {
                    statusJoin.text = getString(R.string.req_declined)
                }
            }
        )
    }
    private fun setup(){
        btnHost.setOnClickListener{ checkLocation {  checkBluetooth {startHostFlow() }}}
        btnJoin.setOnClickListener { checkLocation {  checkBluetooth {startJoinFlow()} }}

        backMain.setOnClickListener {
            if(backStack.isEmpty()){
                findNavController().popBackStack()
            }
            else {
                val tempLayout = backStack.removeAt(backStack.lastIndex)
                tempLayout.visibility = View.VISIBLE
                currentLayout.visibility = View.GONE
                currentLayout = tempLayout
            }
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
                .setPositiveButton("Open Settings"){_,_ ->
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    private fun startHostFlow() {
        btnHost.isEnabled = false
        hostScreen.visibility = View.VISIBLE
        currentLayout = hostScreen
        mainScreen.visibility = View.GONE
        backStack.add(mainScreen)

        connection.startHosting(
            onIncomingRequest = {request -> showAcceptDeclineDialog(request)},
            onOpponentConnected = {onConnected(isHost = true)},
            onOpponentDisconnected = {
                requireActivity().runOnUiThread { Toast.makeText(requireContext(), "Opponent Disconnected", Toast.LENGTH_LONG).show() }
            }
        )
    }
    private fun startJoinFlow() {
        btnJoin.isEnabled = false
        joinScreen.visibility = View.VISIBLE
        currentLayout = joinScreen
        mainScreen.visibility = View.GONE
        backStack.add(mainScreen)
        statusJoin.text = getString(R.string.searching_nearby)
        progressJoin.visibility = View.VISIBLE

        connection.startDiscovery { updatedList ->
            requireActivity().runOnUiThread {
                if(updatedList.isNotEmpty()){
                    progressJoin.visibility = View.GONE
                    statusJoin.text = getString(R.string.tap_players)
                }
                else {
                    progressJoin.visibility = View.VISIBLE
                    statusJoin.text = getString(R.string.searching_nearby)
                }
                discoveredPlayers = updatedList
                playersAdapter.clear()
                playersAdapter.addAll(updatedList)
                playersAdapter.notifyDataSetChanged()
            }

        }
    }
    private fun showAcceptDeclineDialog(request: IncomingRequest) {
        val view = layoutInflater.inflate(R.layout.incoming_request, null)
        view.findViewById<TextView>(R.id.hosting_nearby_text).text = getString(R.string.incoming_request, request.playerName)
        view.findViewById<Button>(R.id.accept).setOnClickListener {
            connection.respondToRequest(request.endpointId, accept = true)
            usernameTwo = request.playerName
        }
        view.findViewById<Button>(R.id.decline).setOnClickListener {
            connection.respondToRequest(request.endpointId, accept = false)
        }
        requireActivity().runOnUiThread {
            AlertDialog.Builder(requireContext())
                .setView(view)
                .create()
                .show()
        }
    }

    private fun onConnected(isHost: Boolean) {
        sessionViewModel.connection = connection
        sessionViewModel.isHost = isHost
        sessionViewModel.connectionType = "online"
        prefs?.edit()?.putString("username2", usernameTwo)?.apply()
        requireActivity().runOnUiThread {
            findNavController().navigate(R.id.friendly_to_game)
        }

    }

    fun playerName() : String {
        val prefs = requireContext().getSharedPreferences("game_prefs", Context.MODE_PRIVATE)
        return prefs.getString("username", "Player")!!
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
            }.setNegativeButton("Cancel"){_,_ ->
                goBack("You cannot play this mode without permissions")
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
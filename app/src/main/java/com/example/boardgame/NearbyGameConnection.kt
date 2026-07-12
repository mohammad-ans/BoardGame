package com.example.boardgame

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class NearbyGameConnection(private val context: Context, private val localPlayerName: String) : GameConnection {
    private val serviceID = "com.example.boardgame"
    private val connectionsClient = Nearby.getConnectionsClient(context)
    private var connectedEndPointId: String? = null
    private var moveCallback: ((GameMove) -> Unit)? = null

    private val discoveredPlayers = mutableMapOf<String, DiscoveredPlayer>()
    private var endpointsUpdatedCallback: ((List<DiscoveredPlayer>) -> Unit)? = null

    private var onIncomingRequest: ((IncomingRequest) -> Unit)? = null
    private var onOpponentConnected: (() -> Unit)? = null
    private var onOpponentDisconnected: (() -> Unit)? = null
    private var onJoinRejected: (() -> Unit)? = null
    private val payloadCallback = object :  PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if(payload.type == Payload.Type.BYTES) {
                val json = String(payload.asBytes()!!, Charsets.UTF_8)
                val move = Json.decodeFromString<GameMove>(json)
                moveCallback?.invoke(move)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {

        }
    }
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback(){
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            onIncomingRequest?.invoke(IncomingRequest(endpointId, info.endpointName)) ?: connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK ->{
                    connectedEndPointId = endpointId
                    connectionsClient.stopAdvertising()
                    connectionsClient.stopDiscovery()
                    onOpponentConnected?.invoke()
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    onJoinRejected?.invoke()
                }
                else -> {
                    //Nothing yet
                }
            }
        }

        override fun onDisconnected(ednpointId: String) {
            connectedEndPointId = null
            onOpponentDisconnected?.invoke()
        }
    }

    override fun startHosting(onIncomingRequest: (IncomingRequest) -> Unit, onOpponentConnected: () -> Unit, onOpponentDisconnected: () -> Unit) {
        this.onIncomingRequest = onIncomingRequest
        this.onOpponentConnected = onOpponentConnected
        this.onOpponentDisconnected = onOpponentDisconnected

        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()

        connectionsClient.startAdvertising(
            localPlayerName, serviceID, connectionLifecycleCallback, options
        )
    }

    override fun respondToRequest(endpointId: String, accept: Boolean){
        if(accept)
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        else
            connectionsClient.rejectConnection(endpointId)
    }

    override fun startDiscovery(onEndpointsUpdated: (List<DiscoveredPlayer>) -> Unit) {
        endpointsUpdatedCallback = onEndpointsUpdated
        discoveredPlayers.clear()
        val discoveryCallback = object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                discoveredPlayers[endpointId] = DiscoveredPlayer(endpointId, info.endpointName)
                endpointsUpdatedCallback?.invoke(discoveredPlayers.values.toList())
            }
            override fun onEndpointLost(endpointId: String) {
                discoveredPlayers.remove(endpointId)
                endpointsUpdatedCallback?.invoke((discoveredPlayers.values.toList()))
            }
        }
        val options = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        connectionsClient.startDiscovery(serviceID, discoveryCallback, options)
    }
    override fun connectToEndpoint(endpointId : String, onOpponentConnected: () -> Unit, onRejected: () -> Unit) {
        this.onOpponentConnected = onOpponentConnected
        this.onJoinRejected = onRejected

        connectionsClient.requestConnection(
            localPlayerName, endpointId, connectionLifecycleCallback
        )
    }
    override fun sendMove(move: GameMove) {
        val endpointId = connectedEndPointId ?: return
        val json = Json.encodeToString(move)
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(json.toByteArray()))
    }

    override fun onMoveReceived(callback: (GameMove) -> Unit) {
        moveCallback = callback
    }
    override fun disconnect(){
        connectedEndPointId?.let {connectionsClient.disconnectFromEndpoint(it)}
        connectionsClient.stopAllEndpoints()
    }
}


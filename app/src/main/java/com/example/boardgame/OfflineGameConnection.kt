package com.example.boardgame

import android.content.Context

class OfflineGameConnection(private val context : Context, val playerName : String) : GameConnection {
    private var moveCallback : ((GameMove) -> Unit)? = null
    override fun disconnect() {
    }

    override fun sendMove(move: GameMove) {

    }

    override fun onMoveReceived(callback: (GameMove) -> Unit) {
        moveCallback = callback
    }

    override fun respondToRequest(endpointId: String, accept: Boolean) {

    }

    override fun startDiscovery(onEndpointsUpdated: (List<DiscoveredPlayer>) -> Unit) {

    }

    override fun connectToEndpoint(
        endpointId: String,
        onOpponentConnected: () -> Unit,
        onRejected: () -> Unit
    ) {

    }

    override fun startHosting(
        onIncomingRequest: (IncomingRequest) -> Unit,
        onOpponentConnected: () -> Unit,
        onOpponentDisconnected: () -> Unit
    ) {

    }
}
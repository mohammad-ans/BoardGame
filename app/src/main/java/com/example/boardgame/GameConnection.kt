package com.example.boardgame

import kotlinx.serialization.Serializable

interface GameConnection {
    fun startHosting(
        onIncomingRequest: (IncomingRequest) -> Unit,
        onOpponentConnected: () -> Unit,
        onOpponentDisconnected: () -> Unit
    )

    fun respondToRequest(endpointId: String, accept: Boolean)
    fun startDiscovery(onEndpointsUpdated: (List<DiscoveredPlayer>) -> Unit)
    fun connectToEndpoint(endpointId : String, onOpponentConnected: () -> Unit, onRejected: () -> Unit)
    fun sendMove(move : GameMove)
    fun onMoveReceived(callback: (GameMove) -> Unit)
    fun disconnect()
    fun startVoiceChat() {}
    fun stopVoiceChat() {}
    fun setMuted(muted: Boolean) {}
}

data class DiscoveredPlayer(
    val endpointId : String,
    val playerName : String
)

@Serializable
data class GameMove(
    val playerId: String,
    val diceVal: Int
)
data class IncomingRequest(val endpointId: String, val playerName: String)

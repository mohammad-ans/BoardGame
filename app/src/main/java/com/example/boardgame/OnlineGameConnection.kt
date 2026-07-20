package com.example.boardgame

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class OnlineGameConnection(private val context: Context, private val playerName: String, private val serverUrl : String) : GameConnection {
    private var moveCallback: ((GameMove) -> Unit)? = null
    private  val client = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build()
    private var webSocket : WebSocket? = null
    private var currentRoomCode: String? = null
    private var onRoomCreated: ((String) -> Unit)? = null
    private var onMatched: ((String) -> Unit)? = null
    private var onPlayerJoined : ((String) -> Unit)? = null
    private var onWaitingForMatch: (() -> Unit)? = null
    private var onOpponentsDisconnected: (() -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private var onConnectionFailed : (() -> Unit)? = null
    @OptIn(ExperimentalUuidApi::class)
    private val username = Uuid.random().toString()
    override fun onMoveReceived(callback: (GameMove) -> Unit) {
        moveCallback = callback
    }
    private fun ensureConnected(onOpen: ()->Unit) {
        if (webSocket != null) {
            onOpen()
            Log.e("A", "Alraedy")
            return
        }
        val request = Request.Builder().url("ws://10.0.2.2:8000/ws").build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                super.onOpen(webSocket, response)
                send(JSONObject().put("username", username))
                onOpen()
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = JSONObject(text)
                handleServerMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("C", "Alraedy ${response?.message}")
                Log.e("C", "Alraedy ${response?.request}")
                Log.e("C", "Alraedy ${t.message}")
                super.onFailure(webSocket, t, response)
                this@OnlineGameConnection.webSocket = null
                onError?.invoke("Websocket closed ${t.message}")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.e("D", "Alraedy")
                super.onClosed(webSocket, code, reason)
                this@OnlineGameConnection.webSocket = null
                onError?.invoke("Websocket closed $reason")
            }
        })
    }

    private fun handleServerMessage(text : String) {
        val json = try{
            JSONObject(text)
        } catch (e: Exception) {
            Log.e("Online Mode", text)
            return
        }
        Log.e("Ab", "$json")
        when(json.optString("type")) {
            "room_created" -> {
                currentRoomCode = json.getString("room_code")
                onRoomCreated?.invoke(currentRoomCode!!)
            }
            "waiting_for_match" -> onWaitingForMatch?.invoke()
            "matched" -> {
                currentRoomCode = json.getString("room_code")
                onMatched?.invoke(currentRoomCode!!)
            }
            "player_joined" -> {
                onPlayerJoined?.invoke(currentRoomCode!!)
            }
            "join_room" -> {
                val status = json.getString("status")
                if (status == "success") {
                    Log.e("C", "$currentRoomCode")
                    onMatched?.invoke(currentRoomCode!!)

                }
                else
                    onError?.invoke(json.getString("status"))
            }
            "move" -> {
                moveCallback?.invoke(GameMove(playerId = "Player", diceVal = json.getInt("diceVal")))
            }
            "opponent_disconnected" -> onOpponentsDisconnected?.invoke()
            "error" -> onError?.invoke(json.optString("message", "Server Error"))
        }
    }

    private fun send(json : JSONObject) {
        webSocket?.send(json.toString())
    }
    fun createRoom(onCreated : (String) -> Unit, onFailed : () -> Unit, onMatched: (String) -> Unit)  {
        this.onRoomCreated = onCreated
        this.onConnectionFailed = onFailed
        this.onPlayerJoined = onMatched
        ensureConnected {
            send( JSONObject().put("type", "create_room"))
        }
    }
    fun joinRoom(roomCode : String, onJoined: (String) -> Unit, onFailed: (String) -> Unit) {
        currentRoomCode = roomCode
        this.onMatched = onJoined
        this.onError = onFailed
        this.onConnectionFailed = { onError?.invoke("Could not reach server") }
        ensureConnected { send(JSONObject().put("type", "join_room").put("room_code", roomCode)) }
    }
    fun findRandomMatch(onWaiting: () -> Unit, onMatched: (String) -> Unit, onFailed: () -> Unit) {
        this.onWaitingForMatch = onWaiting
        this.onMatched = onMatched
        this.onConnectionFailed = onFailed
        ensureConnected {
            send(JSONObject().put("type", "find_random_match"))
        }
    }
    fun setOnOpponentDisconnected(callback: () -> Unit) {
        this.onOpponentsDisconnected = callback
    }

    override fun sendMove(move: GameMove) {
        send(JSONObject().put("type", "move").put("diceVal", move.diceVal))
    }

    override fun disconnect() {
        send(JSONObject().put("type", "leave"))
        webSocket?.close(1000, "Player left")
        webSocket = null
        currentRoomCode = null
    }

    override fun connectToEndpoint(
        endpointId: String,
        onOpponentConnected: () -> Unit,
        onRejected: () -> Unit
    ) {

    }

    override fun respondToRequest(endpointId: String, accept: Boolean) {
        TODO("Not yet implemented")
    }

    override fun startDiscovery(onEndpointsUpdated: (List<DiscoveredPlayer>) -> Unit) {
        TODO("Not yet implemented")
    }

    override fun startHosting(
        onIncomingRequest: (IncomingRequest) -> Unit,
        onOpponentConnected: () -> Unit,
        onOpponentDisconnected: () -> Unit
    ) {
        TODO("Not yet implemented")
    }
}
package com.example.boardgame

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.provider.Settings.Global.getString
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.DataChannel
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.util.concurrent.TimeUnit
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class OnlineGameConnection(private val context: Context, private val playerName: String, private val serverUrl : String) : GameConnection {
    private var moveCallback: ((GameMove) -> Unit)? = null
    private  val client = OkHttpClient.Builder().pingInterval(2, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS).build()
    private var webSocket : WebSocket? = null
    private var isMuted = true
    private var currentRoomCode: String? = null
    private var onRoomCreated: ((String) -> Unit)? = null
    private var onMatched: ((String, Boolean) -> Unit)? = null
    private var onWaitingForMatch: (() -> Unit)? = null
    private var onOpponentsDisconnected: (() -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private var onConnectionFailed : (() -> Unit)? = null
    private val prefs = context.getSharedPreferences("game_prefs", Context.MODE_PRIVATE)
    val username = prefs.getString("uuid", null)!!
    private var isInitiator = false
    private val eglBase = EglBase.create()
    private var startLoading : (() -> Unit)? = null
    private var stopLoading : (() -> Unit)? = null
    private var receiveData : ((JSONObject) -> Unit)? = null
    private var loadingFunc : ((String) -> Unit)? = null
    private val peerConnectionFactory: PeerConnectionFactory by lazy {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
        )
        PeerConnectionFactory.builder().createPeerConnectionFactory()
    }
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: org.webrtc.AudioTrack? = null
    private var audioSource: AudioSource? = null

    private val iceServers = listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com").createIceServer() )
    private val pendingIceCandidates = mutableListOf<IceCandidate>()
    private var remoteDescriptionSet = false
    override fun onMoveReceived(callback: (GameMove) -> Unit) {
        moveCallback = callback
    }
    private var reconnectAttempts = 0
    private val maxRecon = 5
    private var iceRestartAttempts = 0
    private val maxIceRestarts = 3
    private var isRestartingVoice = false
    private var voiceConnectionLost: (() -> Unit)? = null
    private val voiceHandler = Handler(Looper.getMainLooper())
    private fun ensureConnected(recon : Boolean, onOpen: ()->Unit) {
        if (webSocket != null) {
            onOpen()
            return
        }
        val request = Request.Builder().url(serverUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                super.onOpen(webSocket, response)
                Log.e("Open", "open")
                send(JSONObject().put("username", username).put("local", playerName))
                reconnectAttempts = 0
                iceRestartAttempts = 0
                onOpen()
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("C", " ${t.message}")
                super.onFailure(webSocket, t, response)
                this@OnlineGameConnection.webSocket = null
                reconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosed(webSocket, code, reason)
                this@OnlineGameConnection.webSocket = null
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
        Log.e("JSON", "${json}")
        when(json.optString("type")) {
            "room_created" -> {
                currentRoomCode = json.getString("room_code")
                onRoomCreated?.invoke(currentRoomCode!!)
            }
            "waiting_for_match" -> onWaitingForMatch?.invoke()
            "matched" -> {
                val turn = json.getInt("turn") == 1
                currentRoomCode = json.getString("room_code")
                isInitiator = json.getBoolean("is_initiator")
                Log.e("Test", "$turn $isInitiator")
                onMatched?.invoke(currentRoomCode!!, turn)
            }
            "player_joined" -> {
                onMatched?.invoke(currentRoomCode!!, true)
                isInitiator = true
            }
            "join_room" -> {
                val status = json.getString("status")
                if (status == "success") {
                    onMatched?.invoke(currentRoomCode!!, false)
                    isInitiator = false
                }
                else
                    onError?.invoke(json.getString("status"))
            }
            "move" -> {
                moveCallback?.invoke(GameMove(playerId = "Player", diceVal = json.getInt("diceVal")))
            }
            "voice_offer" -> {
                val sdp = SessionDescription(SessionDescription.Type.OFFER, json.getString("sdp"))
                peerConnection?.setRemoteDescription(object : SimpleSdpObserver(){
                    override fun onSetSuccess(){
                        remoteDescriptionSet = true
                        flushPendingIceCandidates()
                        createAndSendAnswer()
                    }
                }, sdp)

            }
            "voice_answer" -> {
                val sdp = SessionDescription(SessionDescription.Type.ANSWER, json.getString("sdp"))
                peerConnection?.setRemoteDescription(object : SimpleSdpObserver(){
                    override fun onSetSuccess() {
                        remoteDescriptionSet = true
                        flushPendingIceCandidates()
                    }
                }, sdp)
            }
            "voice_ice_candidate" -> {
                val candidate = IceCandidate(
                    json.getString("sdpMid"),
                    json.getInt("sdpMLineIndex"),
                    json.getString("candidate")
                )
                if (remoteDescriptionSet)
                    peerConnection?.addIceCandidate(candidate)
                else
                    pendingIceCandidates.add(candidate)
            }
            "username" -> prefs.edit().putString("username2", json.getString("username")).apply()
            "opponent_disconnected" -> onOpponentsDisconnected?.invoke()
            "error" -> onError?.invoke(json.optString("message", "Server Error"))
            "rejoined" -> {
                loadingFunc?.invoke("Reconnected, awaiting for data from the opponent")
            }
            "rejoined_opponent" -> {
                loadingFunc?.invoke("Opponent reconnected, sending data")
                moveCallback?.invoke(GameMove("", -5))
            }
            "rejoin_data" -> {
                receiveData?.invoke(json)
                stopLoading?.invoke()
            }
            "stop_loading" -> {
                stopLoading?.invoke()
            }
        }
    }


    override fun setMuted(muted: Boolean) {
        isMuted = muted
        localAudioTrack?.setEnabled(!isMuted)
    }
    override fun startVoiceChat() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        val speaker = audioManager.availableCommunicationDevices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        }
        speaker?.let {
            audioManager.setCommunicationDevice(it)
        }
        val constraints = MediaConstraints()
        audioSource = peerConnectionFactory.createAudioSource(constraints)
        localAudioTrack = peerConnectionFactory.createAudioTrack("audio_track", audioSource)
        localAudioTrack?.setEnabled(!isMuted)
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                send(JSONObject().apply {
                    put("type", "voice_ice_candidate")
                    put("candidate", candidate?.sdp)
                    put("sdpMid", candidate?.sdpMid)
                    put("sdpMLineIndex", candidate?.sdpMLineIndex)
                }, true)
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d("WebRTC", "Ice State: $state")
            }

            override fun onAddStream(p0: MediaStream?) {

            }

            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {

            }

            override fun onIceConnectionReceivingChange(p0: Boolean) {

            }

            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {

            }

            override fun onRemoveStream(p0: MediaStream?) {

            }

            override fun onDataChannel(p0: DataChannel?) {

            }

            override fun onAddTrack(
                receiver: RtpReceiver?,
                mediaStreams: Array<out MediaStream?>?
            ) {
            }

            override fun onRenegotiationNeeded() {
            }

            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate?>?) {

            }
        })
        peerConnection?.addTrack(localAudioTrack, listOf("stream1"))
        if (isInitiator)
            createAndSendOffer()
    }
    private fun createAndSendOffer(){
        Log.e("WebRtc", "sent")
        val constraints = MediaConstraints()
        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                peerConnection?.setLocalDescription(SimpleSdpObserver(), sdp)
                send(JSONObject().apply {
                    put("type", "voice_offer")
                    put("sdp", sdp?.description)
                }, true)
            }
        }, constraints)
    }
    private fun createAndSendAnswer() {
        val constraints = MediaConstraints()
        peerConnection?.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                peerConnection?.setLocalDescription(SimpleSdpObserver(), sdp)
                send(JSONObject().apply {
                    put("type", "voice_answer")
                    put("sdp", sdp?.description)
                }, true)
            }
        }, constraints)
    }
    override fun send(json : JSONObject, put: Boolean) {
        Log.e("Move", "$json  $webSocket")
        if(put){
            if(currentRoomCode == null)
                return
            json.put("room_code", currentRoomCode)
        }
        webSocket?.send(json.toString())
    }
    fun createRoom(onCreated : (String) -> Unit, onFailed : () -> Unit, onMatched: (String, Boolean) -> Unit)  {
        this.onRoomCreated = onCreated
        this.onConnectionFailed = onFailed
        this.onMatched = onMatched
        this.onError = {onFailed()}
        ensureConnected(false) {
            send( JSONObject().put("type", "create_room"))

        }
    }
    fun joinRoom(roomCode : String, onJoined: (String, Boolean) -> Unit, onFailed: (String) -> Unit) {
        currentRoomCode = roomCode
        this.onMatched = onJoined
        this.onError = onFailed
        this.onConnectionFailed = { onError?.invoke("Could not reach server") }
        ensureConnected(false) { send(JSONObject().put("type", "join_room"), true) }
    }
    fun findRandomMatch(onWaiting: () -> Unit, onMatched: (String, Boolean) -> Unit, onFailed: () -> Unit) {
        this.onWaitingForMatch = onWaiting
        this.onMatched = onMatched
        this.onConnectionFailed = onFailed
        this.onError = {onFailed()}
        ensureConnected(false) {
            send(JSONObject().put("type", "find_random_match"))
        }
    }
    fun setOnOpponentDisconnected(callback: () -> Unit) {
        this.onOpponentsDisconnected = callback
    }

    override fun sendMove(move: GameMove) {
        send(JSONObject().put("type", "move").put("dice_val", move.diceVal), true)
    }

    override fun disconnect() {
        send(JSONObject().put("type", "leave"), true)
        webSocket?.close(1000, "Player left")
        webSocket = null
        currentRoomCode = null
    }

    override fun connectToEndpoint(
        endpointId: String,
        onOpponentConnected: () -> Unit,
        onRejected: () -> Unit
    ) {}

    override fun respondToRequest(endpointId: String, accept: Boolean) {}

    override fun startDiscovery(onEndpointsUpdated: (List<DiscoveredPlayer>) -> Unit) {}

    override fun startHosting(
        onIncomingRequest: (IncomingRequest) -> Unit,
        onOpponentConnected: () -> Unit,
        onOpponentDisconnected: () -> Unit
    ) {}

    override fun stopVoiceChat() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_NORMAL
        val earpiece = audioManager.availableCommunicationDevices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
        }
        earpiece?.let {
            audioManager.setCommunicationDevice(it)
        }
        peerConnection?.close()
        peerConnection = null
        localAudioTrack = null
        audioSource = null
    }
    private fun reconnect(){
        Log.e("Reconn", "$reconnectAttempts")

        startLoading?.invoke()
        if (reconnectAttempts >= maxRecon) {
            onConnectionFailed?.invoke()
            return
        }
        val delay = 1500L + (500L * reconnectAttempts)
        reconnectAttempts++
        Handler(Looper.getMainLooper()).postDelayed({
            ensureConnected(true) {
                if(currentRoomCode != null) {
                    send(JSONObject().apply {
                        put("type", "rejoin")
                        put("room_code", currentRoomCode)
                        put("username", username)
                    }, true)
                    reconnectVoiceIfNeeded()
                }
                else{
                    reconnectAttempts = maxRecon
                    onConnectionFailed?.invoke()
                }
            }
        }, delay)

    }
    private fun reconnectVoiceIfNeeded() {
        if (peerConnection == null)
            return
        rebuildVoiceConnection()
    }
    private fun rebuildVoiceConnection() {
        stopVoiceChat()
        startVoiceChat()
        isRestartingVoice = false
    }

    private fun attemptVoiceRecovery(restart: Boolean){
        if (peerConnection == null)
            return
        if(isRestartingVoice)
            return
        if (iceRestartAttempts >= maxIceRestarts){
            voiceConnectionLost?.invoke()
            stopVoiceChat()
            return
        }
        isRestartingVoice = true
        iceRestartAttempts++;
        if(restart)
            restartVoiceConnection()
        else
            rebuildVoiceConnection()
    }

    override fun setOnVoiceConnection(f: () -> Unit) {
        voiceConnectionLost = f
    }

    private fun flushPendingIceCandidates() {
        pendingIceCandidates.forEach { peerConnection?.addIceCandidate(it) }
        pendingIceCandidates.clear()
    }
    open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {
            Log.e("WebRTC", "Create failure: $error")
        }

        override fun onSetFailure(error: String?) {
            Log.e("WebRTC", "Set failure: $error")
        }
    }
    override fun setOnStopLoading(f: (() -> Unit)) {
        stopLoading = f
    }
    override fun setOnStartLoading(f :(() -> Unit), f2 : ((String) -> Unit)) {
        loadingFunc = f2
        startLoading = f
    }

    override fun setOnReceiveData(f : ((JSONObject) -> Unit)){
        receiveData = f
    }
    private fun restartVoiceConnection() {
        if (peerConnection == null){
            isRestartingVoice = false
            return
        }
        remoteDescriptionSet = false
        pendingIceCandidates.clear()
        if(!isInitiator){
            isRestartingVoice = false
            return
        }
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
        }
        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                peerConnection?.setLocalDescription(SimpleSdpObserver(), sdp)
                send(JSONObject().apply {
                    put("type", "voice_offer")
                    put("room_code", currentRoomCode)
                    put("sdp", sdp?.description)
                }, true)
                isRestartingVoice = false
            }

            override fun onCreateFailure(error: String?) {
                super.onCreateFailure(error)
                isRestartingVoice = false
            }
        }, constraints)
    }
}
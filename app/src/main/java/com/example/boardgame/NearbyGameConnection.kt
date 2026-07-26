package com.example.boardgame

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.TextView
import android.widget.Toast
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject

@Serializable
data class Username(val username : String)

class NearbyGameConnection(private val context: Context, private val localPlayerName: String) : GameConnection {
    private val serviceID = "com.example.boardgame"
    private val connectionsClient = Nearby.getConnectionsClient(context)
    private var connectedEndPointId: String? = null
    var moveCallback: ((GameMove) -> Unit)? = null

    private val discoveredPlayers = mutableMapOf<String, DiscoveredPlayer>()
    private var endpointsUpdatedCallback: ((List<DiscoveredPlayer>) -> Unit)? = null

    private var onIncomingRequest: ((IncomingRequest) -> Unit)? = null
    private var onOpponentConnected: (() -> Unit)? = null
    private var onOpponentDisconnected: (() -> Unit)? = null
    private var onJoinRejected: (() -> Unit)? = null
    private var onUsernameReceived: ((String) -> Unit)? = null
    private val sampleRate = 16000
    private val channelConfigIn = AudioFormat.CHANNEL_IN_MONO
    private val channelConfigOut = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioFormat)
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var captureThread: Thread? = null
    private var isCapturing: Boolean = false
    private var isMuted: Boolean = true
    private var pipeWriteSide: ParcelFileDescriptor? = null

    @SuppressLint("MissingPermission")
    override fun startVoiceChat() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        val speaker = audioManager.availableCommunicationDevices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        }
        speaker?.let {
            audioManager.setCommunicationDevice(it)
        }
        val endpointId = connectedEndPointId ?: return
        if (isCapturing)
            return
        val pipe = ParcelFileDescriptor.createPipe()
        val readSide = pipe[0]
        pipeWriteSide = pipe[1]
        val payload = Payload.fromStream(ParcelFileDescriptor.AutoCloseInputStream(readSide))
        connectionsClient.sendPayload(endpointId, payload)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRate, channelConfigIn, audioFormat, minBufferSize
        )
        isCapturing = true
        audioRecord?.startRecording()
        captureThread = Thread {
            val buffer = ByteArray(minBufferSize)
            val outputStream = ParcelFileDescriptor.AutoCloseOutputStream(pipeWriteSide)
            try{
                while(isCapturing) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (bytesRead > 0 && !isMuted) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                }
            }
            catch(e : Exception) {
                Log.e("NearbyVoice", "Capture Error: ${e.message}", e)
            }
            finally {
                outputStream.close()
            }
        }
        captureThread?.start()
    }

    override fun setMuted(muted: Boolean) {
        isMuted = muted
    }
    override fun stopVoiceChat() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        val earpiece = audioManager.availableCommunicationDevices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
        }
        earpiece?.let {
            audioManager.setCommunicationDevice(it)
        }
        isCapturing = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        captureThread = null
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    private fun handleIncomingVoiceStream(payload: Payload) {
        val inputStream = payload.asStream()?.asInputStream() ?: return

        val minTrackBuffer = AudioTrack.getMinBufferSize(sampleRate, channelConfigOut, audioFormat)

        audioTrack = AudioTrack.Builder().setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(
                AudioAttributes.CONTENT_TYPE_SPEECH).build()
        ).setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(audioFormat).setChannelMask(channelConfigOut).build())
            .setBufferSizeInBytes(minTrackBuffer).setTransferMode(AudioTrack.MODE_STREAM).build()

        audioTrack?.play()

        Thread {
            val buffer = ByteArray(minTrackBuffer)

            try {
                while (true) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1)
                        break
                    audioTrack?.write(buffer, 0, bytesRead)
                }
            }
            catch(e : Exception) {
                Log.e("NearbyVoice", "PlayBack Error ${e.message}", e)
            }
        }.start()
    }
    fun onUsernameReceivedSet(f : (String) -> Unit){
        onUsernameReceived = f
    }
    private val payloadCallback = object :  PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when(payload.type){
                Payload.Type.BYTES -> {
                    val json = String(payload.asBytes()!!, Charsets.UTF_8)
                    val envelope = JSONObject(json)
                    when(envelope.getString("kind")){
                        "move" -> {
                            val move = Json.decodeFromString<GameMove>(envelope.getString("data"))
                            moveCallback?.invoke(move)
                        }
                        "username" -> {
                            val username = envelope.getString("username")
                            onUsernameReceived?.invoke(username)
                        }
                    }
                }
                Payload.Type.STREAM -> {
                    handleIncomingVoiceStream(payload)
                }
                else -> {
                    Log.e("Nearby Unknown Stream", "Type: ${payload.type} $payload")
                }
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
        ).addOnSuccessListener {
            Toast.makeText(context, "Advertising was successful", Toast.LENGTH_LONG).show()
        }
            .addOnFailureListener { e ->
                Toast.makeText(context, "${e.message}", Toast.LENGTH_LONG).show()
                Toast.makeText(context, "${e.cause}", Toast.LENGTH_LONG).show()
                Toast.makeText(context, "${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
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
            .addOnSuccessListener {
            Toast.makeText(context, "Discovery was successful", Toast.LENGTH_LONG).show()
        }
            .addOnFailureListener { e ->
                Toast.makeText(context, "${e.message}", Toast.LENGTH_LONG).show()
                Toast.makeText(context, "${e.cause}", Toast.LENGTH_LONG).show()
                Toast.makeText(context, "${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
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
        val payload = Payload.fromBytes(
            JSONObject().put("kind", "move").put("data", json).toString().toByteArray()
        )
        connectionsClient.sendPayload(endpointId, payload)
    }
    fun sendUsername() {
        val endpointId = connectedEndPointId ?: return
        val payload = Payload.fromBytes(
            JSONObject().put("kind", "username").put("username", localPlayerName).toString().toByteArray()
        )
        connectionsClient.sendPayload(endpointId, payload)
    }

    override fun onMoveReceived(callback: (GameMove) -> Unit) {
        moveCallback = callback
    }
    override fun disconnect(){
        connectedEndPointId?.let {connectionsClient.disconnectFromEndpoint(it)}
        connectionsClient.stopAllEndpoints()
    }
}


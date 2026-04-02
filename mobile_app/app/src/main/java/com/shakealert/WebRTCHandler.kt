package com.shakealert

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.webrtc.*

class WebRTCHandler(
    private val context: Context,
    private val onOfferReady: (String) -> Unit,
    private val onIceCandidateReady: (JSONObject) -> Unit
) {
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null

    companion object {
        private const val TAG = "WebRTCHandler"
    }

    init {
        initWebRTC()
    }

    private fun initWebRTC() {
        Log.i(TAG, "Initializing WebRTC Component...")
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )

        val options = PeerConnectionFactory.Options()
        val audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setAudioDeviceModule(audioDeviceModule)
            .createPeerConnectionFactory()

        audioDeviceModule.release()

        createLocalAudioTrack()
    }

    private fun createLocalAudioTrack() {
        val factory = peerConnectionFactory ?: return
        val audioSource = factory.createAudioSource(MediaConstraints())
        localAudioTrack = factory.createAudioTrack("ARDAMSa0", audioSource)
        localAudioTrack?.setEnabled(true)
        Log.i(TAG, "Local Audio Microphone Track Captured.")
    }

    fun startCall() {
        Log.i(TAG, "Initiating WebRTC Audio Pipeline (Creating Offer)...")
        if (peerConnection != null) {
            peerConnection?.close()
            peerConnection = null
        }

        val rtcConfig = PeerConnection.RTCConfiguration(
            listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
            )
        )

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate != null) {
                    val json = JSONObject()
                    json.put("sdpMLineIndex", candidate.sdpMLineIndex)
                    json.put("sdpMid", candidate.sdpMid)
                    json.put("candidate", candidate.sdp)
                    onIceCandidateReady(json)
                }
            }
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
        })

        localAudioTrack?.let {
            val mediaStreamIds = listOf("ARDAMS")
            peerConnection?.addTrack(it, mediaStreamIds)
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                sessionDescription?.let { sdp ->
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.i(TAG, "SDP Request Generated. Ready to send.")
                            onOfferReady(sdp.description)
                        }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(p0: String?) {}
                    }, sdp)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, MediaConstraints().apply {
             mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
             mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        })
    }

    fun handleRemoteAnswer(sdpDescription: String) {
        val sdp = SessionDescription(SessionDescription.Type.ANSWER, sdpDescription)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.i(TAG, "Remote Rescuer Answer received! Audio Bridged Successfully.")
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, sdp)
    }

    fun handleRemoteIceCandidate(json: JSONObject) {
        val candidate = IceCandidate(
            json.getString("sdpMid"),
            json.getInt("sdpMLineIndex"),
            json.getString("candidate")
        )
        peerConnection?.addIceCandidate(candidate)
    }

    fun destroy() {
        localAudioTrack?.dispose()
        peerConnection?.close()
        peerConnectionFactory?.dispose()
    }
}

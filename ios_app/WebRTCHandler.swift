import Foundation
import WebRTC

class WebRTCHandler: NSObject {
    private var factory: RTCPeerConnectionFactory
    private var peerConnection: RTCPeerConnection?
    private var localAudioTrack: RTCAudioTrack?

    var onOfferReady: ((String) -> Void)?
    var onIceCandidateReady: (([String: Any]) -> Void)?

    override init() {
        RTCInitializeSSL()
        let videoEncoderFactory = RTCDefaultVideoEncoderFactory()
        let videoDecoderFactory = RTCDefaultVideoDecoderFactory()
        self.factory = RTCPeerConnectionFactory(encoderFactory: videoEncoderFactory, decoderFactory: videoDecoderFactory)
        super.init()
    }

    func startCall() {
        print("Initiating WebRTC Audio Pipeline (Creating Offer)...")
        if peerConnection != nil {
            stopCall()
        }

        let config = RTCConfiguration()
        config.iceServers = [RTCIceServer(urlStrings: ["stun:stun.l.google.com:19302"])]

        let constraints = RTCMediaConstraints(mandatoryConstraints: nil, optionalConstraints: nil)
        self.peerConnection = factory.peerConnection(with: config, constraints: constraints, delegate: self)

        createLocalAudioTrack()

        if let audioTrack = localAudioTrack {
            peerConnection?.add(audioTrack, streamIds: ["ARDAMS"])
        }

        let sdpConstraints = RTCMediaConstraints(mandatoryConstraints: [
            "OfferToReceiveAudio": "true",
            "OfferToReceiveVideo": "false"
        ], optionalConstraints: nil)

        peerConnection?.offer(for: sdpConstraints) { (sdp, error) in
            guard let sdp = sdp else { return }
            self.peerConnection?.setLocalDescription(sdp, completionHandler: { (error) in
                if error == nil {
                    print("SDP Request Generated. Ready to send.")
                    self.onOfferReady?(sdp.sdpDescription)
                }
            })
        }
    }

    private func createLocalAudioTrack() {
        let audioSource = factory.audioSource(with: nil)
        localAudioTrack = factory.audioTrack(with: audioSource, trackId: "ARDAMSa0")
        localAudioTrack?.isEnabled = true
        print("Local Audio Microphone Track Captured.")
    }

    func handleRemoteAnswer(sdpDescription: String) {
        let sdp = RTCSessionDescription(type: .answer, sdp: sdpDescription)
        peerConnection?.setRemoteDescription(sdp, completionHandler: { error in
            if error == nil {
                print("Remote Rescuer Answer received! Audio Bridged Successfully.")
            }
        })
    }

    func handleRemoteIceCandidate(json: [String: Any]) {
        guard let candidateStr = json["candidate"] as? String else { return }

        var sdpIndex: Int32 = 0
        if let idx = json["sdpMLineIndex"] as? Int32 {
            sdpIndex = idx
        } else if let idx = json["sdpMLineIndex"] as? Int {
            sdpIndex = Int32(idx)
        }

        let sdpMid = json["sdpMid"] as? String
        let candidate = RTCIceCandidate(sdp: candidateStr, sdpMLineIndex: sdpIndex, sdpMid: sdpMid)
        peerConnection?.add(candidate)
    }

    func stopCall() {
        print("WebRTC Terminating Command Received. Safely shutting down tunnel...")
        peerConnection?.close()
        peerConnection = nil
        localAudioTrack = nil
    }
}

extension WebRTCHandler: RTCPeerConnectionDelegate {
    func peerConnection(_ peerConnection: RTCPeerConnection, didGenerate candidate: RTCIceCandidate) {
        let json: [String: Any] = [
            "sdpMLineIndex": candidate.sdpMLineIndex,
            "sdpMid": candidate.sdpMid ?? "",
            "candidate": candidate.sdp
        ]
        onIceCandidateReady?(json)
    }

    func peerConnection(_ peerConnection: RTCPeerConnection, didChange stateChanged: RTCSignalingState) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didAdd stream: RTCMediaStream) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didRemove stream: RTCMediaStream) {}
    func peerConnectionShouldRenegotiate(_ peerConnection: RTCPeerConnection) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceConnectionState) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceGatheringState) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didRemove candidates: [RTCIceCandidate]) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didOpen dataChannel: RTCDataChannel) {}
}

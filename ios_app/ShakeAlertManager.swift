import Foundation
import CoreLocation
import SocketIO
import AVFoundation

class ShakeAlertManager: NSObject, ObservableObject {
    static let shared = ShakeAlertManager()

    @Published var logs: [String] = []
    @Published var mainStatus: String = "🔴 System Offline"
    @Published var subStatus: String = "Monitoring disabled"
    @Published var isServiceRunning: Bool = false

    private var socketManager: SocketManager?
    private var socket: SocketIOClient?
    private var locationManager = CLLocationManager()
    private var shakeDetector = ShakeDetector()
    private var webRTCHandler = WebRTCHandler()
    private var audioPlayer: AVAudioPlayer?

    let SOCKET_URL = "http://10.9.32.45:3000"

    override init() {
        super.init()
        setupShakeDetector()
        setupWebRTC()
        setupLocation()
    }

    func startService() {
        isServiceRunning = true
        mainStatus = "🟢 System Active"
        subStatus = "Background monitoring enabled"
        broadcastLog("> ShakeAlert Manager Started")

        initSocket()
        shakeDetector.start()
    }

    func stopService() {
        isServiceRunning = false
        mainStatus = "🔴 System Offline"
        subStatus = "Monitoring disabled"
        broadcastLog("> ShakeAlert Manager Stopped")

        socket?.disconnect()
        shakeDetector.stop()
        webRTCHandler.stopCall()
        stopSosAlarm()
    }

    private func setupShakeDetector() {
        shakeDetector.delegate = self
    }

    private func setupWebRTC() {
        webRTCHandler.onOfferReady = { [weak self] sdp in
            self?.broadcastLog("📤 Transmitting Live Audio Offer to Rescuers...")
            let payload: [String: Any] = ["type": "offer", "sdp": sdp]
            self?.socket?.emit("signal", payload)
        }

        webRTCHandler.onIceCandidateReady = { [weak self] candidateJson in
            let payload: [String: Any] = ["type": "candidate", "candidate": candidateJson]
            self?.socket?.emit("signal", payload)
        }
    }

    private func setupLocation() {
        locationManager.delegate = self
        locationManager.requestAlwaysAuthorization()
        locationManager.allowsBackgroundLocationUpdates = true
        locationManager.pausesLocationUpdatesAutomatically = false
    }

    private func initSocket() {
        guard let url = URL(string: SOCKET_URL) else { return }

        socketManager = SocketManager(socketURL: url, config: [.log(false), .compress, .extraHeaders(["Bypass-Tunnel-Reminder": "true"])])
        socket = socketManager?.defaultSocket

        socket?.on(clientEvent: .connect) { [weak self] data, ack in
            self?.broadcastLog("✅ Connected to Server.")
            self?.socket?.emit("register", "victim")
        }

        socket?.on(clientEvent: .error) { [weak self] data, ack in
            self?.broadcastLog("❌ Connection Error: \(data)")
        }

        socket?.on("signal") { [weak self] data, ack in
            guard let dict = data[0] as? [String: Any], let type = dict["type"] as? String else { return }

            switch type {
            case "answer":
                self?.broadcastLog("📞 Rescuer accepted audio! Establishing bridge...")
                if let sdp = dict["sdp"] as? String {
                    self?.webRTCHandler.handleRemoteAnswer(sdpDescription: sdp)
                }
            case "candidate":
                if let candidate = dict["candidate"] as? [String: Any] {
                    self?.webRTCHandler.handleRemoteIceCandidate(json: candidate)
                }
            case "poke_audio":
                self?.broadcastLog("🎙️ Dashboard requested Audio Test. Activating microphone...")
                self?.webRTCHandler.startCall()
            case "disconnect_audio":
                self?.broadcastLog("🛑 Dashboard disconnected call. Releasing microphone.")
                self?.webRTCHandler.stopCall()
            case "trigger_sos":
                self?.broadcastLog("🔔 Dashboard triggered SOS ALARM!")
                self?.playSosAlarm()
            case "stop_sos":
                self?.broadcastLog("🔕 Dashboard stopped SOS ALARM!")
                self?.stopSosAlarm()
            default:
                break
            }
        }

        socket?.connect()
    }

    func manualTrigger() {
        if isServiceRunning {
            broadcastLog("> Sending Manual Alert Override...")
            sendAlertToServer(severity: "manual-test", force: 0.0)
        } else {
            broadcastLog("❌ Service is offline. Enable background monitoring first.")
        }
    }

    private func sendAlertToServer(severity: String, force: Float) {
        guard let location = locationManager.location else {
            broadcastLog("❌ Location missing. Could not acquire GPS.")
            return
        }

        let lat = location.coordinate.latitude
        let lng = location.coordinate.longitude

        broadcastLog("📤 Sending Alert: \(severity) at [\(lat), \(lng)]")

        let payload: [String: Any] = [
            "lat": lat,
            "lng": lng,
            "severity": severity,
            "rawSensor": Double(force)
        ]

        socket?.emit("shake_alert", payload)
    }

    private func broadcastLog(_ msg: String) {
        print(msg)
        DispatchQueue.main.async {
            self.logs.append(msg)
        }
    }

    private func playSosAlarm() {
        guard let url = Bundle.main.url(forResource: "alarm", withExtension: "mp3") else { return }
        do {
            try AVAudioSession.sharedInstance().setCategory(.playback, mode: .default, options: [])
            try AVAudioSession.sharedInstance().setActive(true)
            audioPlayer = try AVAudioPlayer(contentsOf: url)
            audioPlayer?.numberOfLoops = -1
            audioPlayer?.play()
        } catch {
            broadcastLog("❌ Failed to play SOS alarm")
        }
    }

    private func stopSosAlarm() {
        audioPlayer?.stop()
    }
}

extension ShakeAlertManager: ShakeDetectorDelegate {
    func onShake(severity: String, force: Float) {
        broadcastLog("🚨 EARTHQUAKE VERIFIED! (\(severity))")
        sendAlertToServer(severity: severity, force: force)
        if severity == "severe" || severity == "manual-test" {
            broadcastLog("🎙️ Activating emergency microphone...")
            webRTCHandler.startCall()
        }
    }

    func onFalseAlarmDropped() {
        broadcastLog("⚠️ False Alarm Detected (Phone Drop). Ignoring.")
    }
}

extension ShakeAlertManager: CLLocationManagerDelegate {
    func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        if status == .authorizedAlways || status == .authorizedWhenInUse {
            broadcastLog("✅ Location permissions granted.")
        } else {
            broadcastLog("❌ Location permissions denied.")
        }
    }
}

import Foundation
import CoreMotion

protocol ShakeDetectorDelegate: AnyObject {
    func onShake(severity: String, force: Float)
    func onFalseAlarmDropped()
}

class ShakeDetector {
    private let motionManager = CMMotionManager()
    weak var delegate: ShakeDetectorDelegate?

    private let TRIGGER_THRESHOLD_GRAVITY: Float = 1.3
    private let SPIKE_THRESHOLD_GRAVITY: Float = 1.8
    private let ANALYSIS_WINDOW_MS: TimeInterval = 2.5
    private let REQUIRED_SPIKES = 4

    private var isAnalyzing = false
    private var analysisStartTime: Date?
    private var spikeCount = 0
    private var maxForceDetected: Float = 0
    private var lastSpikeTime: Date?

    func start() {
        guard motionManager.isAccelerometerAvailable else {
            print("Accelerometer not available")
            return
        }

        motionManager.accelerometerUpdateInterval = 0.1
        motionManager.startAccelerometerUpdates(to: .main) { [weak self] (data, error) in
            guard let self = self, let data = data else { return }
            self.processAcceleration(data.acceleration)
        }
        print("📡 Sensors active and listening.")
    }

    func stop() {
        motionManager.stopAccelerometerUpdates()
    }

    private func processAcceleration(_ acceleration: CMAcceleration) {
        // CMAcceleration is in units of g
        let gForce = Float(sqrt(pow(acceleration.x, 2) + pow(acceleration.y, 2) + pow(acceleration.z, 2)))
        let now = Date()

        if !isAnalyzing {
            if gForce > TRIGGER_THRESHOLD_GRAVITY {
                isAnalyzing = true
                analysisStartTime = now
                spikeCount = 1
                maxForceDetected = gForce
                lastSpikeTime = now
                print("Trigger hit (\(gForce) g)! Initiating 2.5s signal analysis window.")
            }
        } else {
            if gForce > maxForceDetected {
                maxForceDetected = gForce
            }

            if gForce > SPIKE_THRESHOLD_GRAVITY {
                if let lastSpike = lastSpikeTime, now.timeIntervalSince(lastSpike) > 0.2 {
                    spikeCount += 1
                    lastSpikeTime = now
                }
            }

            if let start = analysisStartTime, now.timeIntervalSince(start) > ANALYSIS_WINDOW_MS {
                endAnalysis()
            }
        }
    }

    private func endAnalysis() {
        print("Analysis complete. Spikes: \(spikeCount), MaxForce: \(maxForceDetected)")

        if spikeCount >= REQUIRED_SPIKES {
            let severity: String
            if maxForceDetected > 4.5 {
                severity = "severe"
            } else if maxForceDetected > 2.5 {
                severity = "moderate"
            } else {
                severity = "mild"
            }
            delegate?.onShake(severity: severity, force: maxForceDetected)
        } else {
            print("Discarded! Only \(spikeCount) sustained peaks. Classified as Phone Drop.")
            delegate?.onFalseAlarmDropped()
        }

        isAnalyzing = false
        spikeCount = 0
        maxForceDetected = 0
    }
}

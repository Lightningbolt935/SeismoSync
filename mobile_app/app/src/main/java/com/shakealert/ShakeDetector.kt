package com.shakealert

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt
import android.util.Log

class ShakeDetector(private val listener: OnShakeListener) : SensorEventListener {

    interface OnShakeListener {
        fun onShake(severity: String, force: Float)
        fun onFalseAlarmDropped()
    }

    private val TRIGGER_THRESHOLD_GRAVITY = 1.3f   // Min force to kick off analysis
    private val SPIKE_THRESHOLD_GRAVITY = 1.8f     // Force needed to count as a major spike
    private val ANALYSIS_WINDOW_MS = 2500L         // Capture window duration length (2.5 seconds)
    private val REQUIRED_SPIKES = 4                // Must cross spike threshold 4 distinct times to prove rhythmic frequency

    private var isAnalyzing = false
    private var analysisStartTime: Long = 0
    private var spikeCount = 0
    private var maxForceDetected = 0f
    private var lastSpikeTime: Long = 0

    companion object {
        private const val TAG = "ShakeDetector"
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val gX = event.values[0] / SensorManager.GRAVITY_EARTH
            val gY = event.values[1] / SensorManager.GRAVITY_EARTH
            val gZ = event.values[2] / SensorManager.GRAVITY_EARTH

            val gForce = sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat()
            val now = System.currentTimeMillis()

            if (!isAnalyzing) {
                // IDLE STATE: Wait for initial impact
                if (gForce > TRIGGER_THRESHOLD_GRAVITY) {
                    isAnalyzing = true
                    analysisStartTime = now
                    spikeCount = 1 // Count the trigger itself as the first spike
                    maxForceDetected = gForce
                    lastSpikeTime = now
                    Log.i(TAG, "Trigger hit ($gForce g)! Initiating 2.5s signal analysis window.")
                }
            } else {
                // ANALYZING STATE: Collect data over 2500ms sliding window
                if (gForce > maxForceDetected) {
                    maxForceDetected = gForce
                }

                // Count distinct spikes (Require 200ms gap between spikes so a single long wave isn't double-counted)
                if (gForce > SPIKE_THRESHOLD_GRAVITY && (now - lastSpikeTime > 200)) {
                    spikeCount++
                    lastSpikeTime = now
                }

                // Check if the 2.5 second window has expired
                if (now - analysisStartTime > ANALYSIS_WINDOW_MS) {
                    endAnalysis()
                }
            }
        }
    }

    private fun endAnalysis() {
        Log.i(TAG, "Analysis complete. Spikes: $spikeCount, MaxForce: $maxForceDetected")

        if (spikeCount >= REQUIRED_SPIKES) {
            // CONFIRMED EARTHQUAKE - Prolonged algorithmic rhythmic shaking detected
            val severity = when {
                maxForceDetected > 4.5 -> "severe"
                maxForceDetected > 2.5 -> "moderate"
                else -> "mild"
            }
            listener.onShake(severity, maxForceDetected)
        } else {
            // FALSE ALARM - Most likely a phone drop or passing bump
            Log.i(TAG, "Discarded! Only $spikeCount sustained peaks. Classified as Phone Drop.")
            listener.onFalseAlarmDropped()
        }

        // Reset variables for next completely separate event
        isAnalyzing = false
        spikeCount = 0
        maxForceDetected = 0f
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }
}

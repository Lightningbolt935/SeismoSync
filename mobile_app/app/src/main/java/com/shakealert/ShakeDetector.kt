package com.shakealert

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class ShakeDetector(private val listener: OnShakeListener) : SensorEventListener {

    interface OnShakeListener {
        fun onShake(severity: String, force: Float)
    }

    private var lastTime: Long = 0
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    private var lastZ: Float = 0f
    
    // Placeholder ML threshold logic
    // In the future, collect this data array and pass it to a TFLite model instead
    private val SHAKE_THRESHOLD_GRAVITY = 2.7f 
    private val SHAKE_SLOP_TIME_MS = 1000

    private var lastShakeTime: Long = 0

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val gX = x / SensorManager.GRAVITY_EARTH
            val gY = y / SensorManager.GRAVITY_EARTH
            val gZ = z / SensorManager.GRAVITY_EARTH

            // gForce will be close to 1 when there is no movement.
            val gForce = sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat()

            if (gForce > SHAKE_THRESHOLD_GRAVITY) {
                val now = System.currentTimeMillis()
                
                // ignore shake events too close to each other (1000ms)
                if (lastShakeTime + SHAKE_SLOP_TIME_MS > now) {
                    return
                }

                // Determine severity based on gForce (Placeholder)
                val severity = when {
                    gForce > 5.0 -> "severe"
                    gForce > 3.5 -> "moderate"
                    else -> "mild"
                }

                lastShakeTime = now
                listener.onShake(severity, gForce)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }
}

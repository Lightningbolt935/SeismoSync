package com.shakealert

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var shakeDetector: ShakeDetector
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var mSocket: Socket? = null

    private lateinit var statusText: TextView

    companion object {
        private const val TAG = "ShakeAlert"
        const val PERMISSION_REQUEST_LOCATION = 100
        // Restored to direct local IP (VPN disconnected)
        const val SOCKET_URL = "http://10.3.195.53:3000" 
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(40, 40, 40, 40)
        }

        statusText = TextView(this).apply {
            text = "Initializing Shake Alert System...\n"
            textSize = 18f
        }
        layout.addView(statusText)

        val testButton = Button(this).apply {
            text = "Trigger Manual Alert"
            setOnClickListener {
                statusText.append("\n[Manual Trigger] Detecting...")
                sendAlertToServer("manual-test", 0.0f)
            }
        }
        layout.addView(testButton)

        scrollView.addView(layout)
        setContentView(scrollView)

        Log.i(TAG, "Activity Created.")
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        initSocket()
        initSensors()
        requestPermissions()
    }

    private fun initSocket() {
        try {
            statusText.append("Connecting to: $SOCKET_URL\n")
            
            val opts = IO.Options()
            opts.reconnection = true
            opts.transports = arrayOf(io.socket.engineio.client.transports.WebSocket.NAME)
            // Bypass localtunnel reminder page
            opts.extraHeaders = mapOf("Bypass-Tunnel-Reminder" to listOf("true"))
            
            mSocket = IO.socket(SOCKET_URL, opts)
            
            mSocket?.on(Socket.EVENT_CONNECT) {
                Log.i(TAG, "Socket Connected!")
                runOnUiThread {
                    statusText.append("✅ Connected to Server.\n")
                }
                mSocket?.emit("register", "victim")
            }

            mSocket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                val err = if (args.isNotEmpty()) args[0].toString() else "Unknown Error"
                Log.e(TAG, "Connection Error: $err")
                runOnUiThread {
                    statusText.append("❌ Connection Error: $err\n")
                }
            }

            mSocket?.on(Socket.EVENT_DISCONNECT) {
                runOnUiThread { statusText.append("⚠️ Disconnected from server.\n") }
            }

            mSocket?.connect()
        } catch (e: Exception) {
            Log.e(TAG, "Socket Error", e)
            statusText.append("❌ Socket Setup Failed: ${e.message}\n")
        }
    }

    private fun initSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        if (accelerometer == null) {
            statusText.append("❌ No Accelerometer found!\n")
            return
        }

        shakeDetector = ShakeDetector(object : ShakeDetector.OnShakeListener {
            override fun onShake(severity: String, force: Float) {
                runOnUiThread {
                    statusText.append("🚨 SHAKE DETECTED! ($severity)\n")
                }
                sendAlertToServer(severity, force)
            }
        })
        statusText.append("📡 Sensors active.\n")
    }

    private fun requestPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            statusText.append("⌛ Requesting Location permissions...\n")
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), PERMISSION_REQUEST_LOCATION)
        } else {
            statusText.append("📍 Location permission granted.\n")
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendAlertToServer(severity: String, force: Float) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                val lat = location?.latitude ?: 0.0
                val lng = location?.longitude ?: 0.0
                
                runOnUiThread {
                    statusText.append("📤 Sending Alert: $severity at [$lat, $lng]\n")
                }

                val payload = JSONObject().apply {
                    put("lat", lat)
                    put("lng", lng)
                    put("severity", severity)
                    put("rawSensor", force.toDouble())
                }

                mSocket?.emit("shake_alert", payload)
            }
        } else {
            Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.also { accel ->
            sensorManager.registerListener(shakeDetector, accel, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(shakeDetector)
    }

    override fun onDestroy() {
        super.onDestroy()
        mSocket?.disconnect()
    }
}

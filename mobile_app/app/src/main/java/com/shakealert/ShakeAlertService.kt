package com.shakealert

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

class ShakeAlertService : Service() {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var shakeDetector: ShakeDetector
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var mSocket: Socket? = null
    private var webRTCHandler: WebRTCHandler? = null

    companion object {
        private const val TAG = "ShakeAlertService"
        const val SOCKET_URL = "http://10.3.195.53:3000"
        
        const val ACTION_LOG = "com.shakealert.LOG"
        const val ACTION_STATUS = "com.shakealert.STATUS"
        const val EXTRA_LOG_MSG = "log_msg"
        const val EXTRA_STATUS_MSG = "status_msg"
        const val EXTRA_SUB_STATUS = "sub_status"
        const val NOTIFICATION_ID = 101
        
        var isServiceRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        broadcastLog("> ShakeAlert Service Created in Background")
        initWebRTC()
        initSocket()
        initSensors()
    }

    private fun initWebRTC() {
        webRTCHandler = WebRTCHandler(this,
            onOfferReady = { sdp ->
                broadcastLog("📤 Transmitting Live Audio Offer to Rescuers...")
                val payload = JSONObject().apply {
                    put("type", "offer")
                    put("sdp", sdp)
                }
                mSocket?.emit("signal", payload)
            },
            onIceCandidateReady = { candidateJson ->
                val payload = JSONObject().apply {
                    put("type", "candidate")
                    put("candidate", candidateJson)
                }
                mSocket?.emit("signal", payload)
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "DISABLE_FOREGROUND") {
            stopForeground(true)
            broadcastStatus("🟢 System Active", "Monitoring (App Open Only)")
            // Removing stopSelf() so the socket and sensors stay alive while app is visible
        } else if (action == "MANUAL_TRIGGER") {
            sendAlertToServer("manual-test", 0.0f)
        } else if (action == "ENABLE_FOREGROUND") {
            createNotificationChannel()
            val notification = NotificationCompat.Builder(this, "shake_alert_channel")
                .setContentTitle("Shake Alert Monitoring")
                .setContentText("Actively monitoring for seismic events.")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            startForeground(NOTIFICATION_ID, notification)
            broadcastStatus("🟢 System Active", "Background monitoring enabled")
        }
        return START_STICKY
    }

    private fun initSocket() {
        try {
            val opts = IO.Options()
            opts.reconnection = true
            opts.transports = arrayOf(io.socket.engineio.client.transports.WebSocket.NAME)
            opts.extraHeaders = mapOf("Bypass-Tunnel-Reminder" to listOf("true"))

            mSocket = IO.socket(SOCKET_URL, opts)
            
            mSocket?.on(Socket.EVENT_CONNECT) {
                broadcastLog("✅ Connected to Server.")
                mSocket?.emit("register", "victim")
            }

            mSocket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                val err = if (args.isNotEmpty()) args[0].toString() else "Unknown Error"
                broadcastLog("❌ Connection Error: $err")
            }

            mSocket?.on("signal") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    if (data.has("type")) {
                        when (data.getString("type")) {
                            "answer" -> {
                                broadcastLog("📞 Rescuer accepted audio! Establishing bridge...")
                                webRTCHandler?.handleRemoteAnswer(data.getString("sdp"))
                            }
                            "candidate" -> {
                                webRTCHandler?.handleRemoteIceCandidate(data.getJSONObject("candidate"))
                            }
                            "poke_audio" -> {
                                broadcastLog("🎙️ Dashboard requested Audio Test. Activating microphone...")
                                webRTCHandler?.startCall()
                            }
                        }
                    }
                }
            }

            mSocket?.on(Socket.EVENT_DISCONNECT) {
                broadcastLog("⚠️ Disconnected from server.")
            }
            mSocket?.connect()
        } catch (e: Exception) {
            broadcastLog("❌ Socket Setup Failed: ${e.message}")
        }
    }

    private fun initSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        if (accelerometer == null) {
            broadcastLog("❌ No Accelerometer found!")
            return
        }

        shakeDetector = ShakeDetector(object : ShakeDetector.OnShakeListener {
            override fun onShake(severity: String, force: Float) {
                broadcastLog("🚨 SHAKE DETECTED! ($severity)")
                sendAlertToServer(severity, force)
                if (severity == "severe" || severity == "manual-test") {
                    broadcastLog("🎙️ Activating emergency microphone...")
                    webRTCHandler?.startCall()
                }
            }
        })
        
        sensorManager.registerListener(shakeDetector, accelerometer, SensorManager.SENSOR_DELAY_UI)
        broadcastLog("📡 Sensors active and listening.")
    }

    @SuppressLint("MissingPermission")
    private fun sendAlertToServer(severity: String, force: Float) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                val lat = location?.latitude ?: 0.0
                val lng = location?.longitude ?: 0.0
                
                broadcastLog("📤 Sending Alert: $severity at [$lat, $lng]")

                val payload = JSONObject().apply {
                    put("lat", lat)
                    put("lng", lng)
                    put("severity", severity)
                    put("rawSensor", force.toDouble())
                }

                mSocket?.emit("shake_alert", payload)
            }
        } else {
            broadcastLog("❌ Location permission missing. Could not acquire GPS.")
        }
    }

    private fun broadcastLog(msg: String) {
        Log.i(TAG, msg)
        val intent = Intent(ACTION_LOG).apply {
            putExtra(EXTRA_LOG_MSG, msg)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun broadcastStatus(status: String, subStatus: String) {
        val intent = Intent(ACTION_STATUS).apply {
            putExtra(EXTRA_STATUS_MSG, status)
            putExtra(EXTRA_SUB_STATUS, subStatus)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "shake_alert_channel",
                "Shake Alert Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Keeps the app listening for shakes in the background" }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        sensorManager.unregisterListener(shakeDetector)
        mSocket?.disconnect()
        broadcastLog("> Service Destroyed.\nMonitoring Stopped.")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

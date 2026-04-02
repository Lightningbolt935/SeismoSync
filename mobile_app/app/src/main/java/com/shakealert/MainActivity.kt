package com.shakealert

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class MainActivity : AppCompatActivity() {

    private lateinit var tvMainStatus: TextView
    private lateinit var tvSubStatus: TextView
    private lateinit var tvTerminal: TextView
    private lateinit var scrollViewTerminal: ScrollView
    private lateinit var switchBackground: Switch
    private lateinit var btnManualTrigger: Button

    companion object {
        const val PERMISSION_REQUEST_CODE = 200
    }

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val msg = intent?.getStringExtra(ShakeAlertService.EXTRA_LOG_MSG) ?: return
            appendLog(msg)
        }
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra(ShakeAlertService.EXTRA_STATUS_MSG)
            val subStatus = intent?.getStringExtra(ShakeAlertService.EXTRA_SUB_STATUS)
            if (status != null) {
                tvMainStatus.text = status
                tvMainStatus.setTextColor(Color.parseColor("#34C759")) // Green success color
            }
            if (subStatus != null) tvSubStatus.text = subStatus
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvMainStatus = findViewById(R.id.tvMainStatus)
        tvSubStatus = findViewById(R.id.tvSubStatus)
        tvTerminal = findViewById(R.id.tvTerminal)
        scrollViewTerminal = findViewById(R.id.scrollViewTerminal)
        switchBackground = findViewById(R.id.switchBackground)
        btnManualTrigger = findViewById(R.id.btnManualTrigger)

        // Setup manual trigger
        btnManualTrigger.setOnClickListener {
            if (ShakeAlertService.isServiceRunning) {
                appendLog("> Sending Manual Alert Override...")
                val intent = Intent(this, ShakeAlertService::class.java).apply {
                    action = "MANUAL_TRIGGER"
                }
                startService(intent)
            } else {
                appendLog("❌ Service is offline. Enable background monitoring first.")
            }
        }

        // Setup switch listener
        switchBackground.isChecked = ShakeAlertService.isServiceRunning
        if (ShakeAlertService.isServiceRunning) {
            tvMainStatus.text = "🟢 System Active"
            tvMainStatus.setTextColor(Color.parseColor("#34C759"))
            tvSubStatus.text = "Background monitoring enabled"
        }

        switchBackground.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startShakeAppService()
            } else {
                stopShakeAppService()
            }
        }

        requestPermissions()
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        val missingPermissions = permissions.filter { 
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED 
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
            appendLog("> Requesting required permissions...")
        } else {
            // Auto start base service (Connects to Server)
            startService(Intent(this, ShakeAlertService::class.java))
            // Enable foreground persistence if requested
            if (switchBackground.isChecked) {
                startShakeAppService()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startService(Intent(this, ShakeAlertService::class.java))
                if (switchBackground.isChecked) {
                    startShakeAppService()
                }
            } else {
                appendLog("❌ Permissions denied. Cannot start background service.")
                switchBackground.isChecked = false
            }
        }
    }

    private fun startShakeAppService() {
        appendLog("> Enabling Background Persistence...")
        val serviceIntent = Intent(this, ShakeAlertService::class.java).apply {
            action = "ENABLE_FOREGROUND"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun stopShakeAppService() {
        appendLog("> Disabling Background Persistence...")
        val serviceIntent = Intent(this, ShakeAlertService::class.java).apply {
            action = "DISABLE_FOREGROUND"
        }
        startService(serviceIntent)
    }

    private fun appendLog(msg: String) {
        runOnUiThread {
            tvTerminal.append("\n$msg")
            scrollViewTerminal.post {
                scrollViewTerminal.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(logReceiver, IntentFilter(ShakeAlertService.ACTION_LOG))
        LocalBroadcastManager.getInstance(this).registerReceiver(statusReceiver, IntentFilter(ShakeAlertService.ACTION_STATUS))
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(logReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(statusReceiver)
    }
}

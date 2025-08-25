package com.example.smartxhelmet

import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MenuItem
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class DashboardActivity : AppCompatActivity() {

    private lateinit var helmetStatusTextView: TextView
    private lateinit var helmetNameTextView: TextView
    private lateinit var tvLiveSpeed: TextView
    private lateinit var shrinkAnim: Animation

    private var isButtonAnimating = false
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var espDevice: BluetoothDevice? = null
    private var isConnected = false

    private var helmetId: String = "Unknown" // will be fetched from ESP32

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        helmetStatusTextView = findViewById(R.id.tvConnectionStatus)
        helmetNameTextView = findViewById(R.id.tvHelmetName)
        tvLiveSpeed = findViewById(R.id.tvLiveSpeed)
        shrinkAnim = AnimationUtils.loadAnimation(this, R.anim.bounce)

        val btnAppInfo: Button = findViewById(R.id.btnAppInfo)
        val btnAddContact: Button = findViewById(R.id.btnAddContact)
        val locationBtn: Button = findViewById(R.id.locationBtn)
        val btnSearchDevice: Button = findViewById(R.id.btnSearchDevice)
        val btnRefreshStatus: Button = findViewById(R.id.btnRefreshStatus)
        val btnStartSpeedStream: Button = findViewById(R.id.btnStartSpeedStream)
        val btnSettings: Button = findViewById(R.id.btnSettings)

        // Load saved Helmet ID (if available)
        val prefs = getSharedPreferences("SmartHelmetPrefs", MODE_PRIVATE)
        helmetId = prefs.getString("helmet_id", "Unknown") ?: "Unknown"

        // Navigation buttons
        setSmoothNavigate(btnStartSpeedStream, SpeedometerActivity::class.java)
        setSmoothNavigate(btnAppInfo, SafetyInstructionsActivity::class.java)
        setSmoothNavigate(btnAddContact, AddContactActivity::class.java)
        setSmoothNavigate(locationBtn, LocationActivity::class.java)
        setSmoothNavigate(btnSearchDevice, BluetoothScanActivity::class.java)

        btnRefreshStatus.setOnClickListener {
            smoothBounceOnly(it as Button)
            checkBluetoothStatus()
        }

        // ⚙️ Settings button -> PopupMenu
        btnSettings.setOnClickListener {
            val popup = PopupMenu(this, btnSettings)
            popup.menu.add("Your ID: $helmetId")

            popup.setOnMenuItemClickListener { _: MenuItem ->
                Toast.makeText(this, "Your ID: $helmetId", Toast.LENGTH_LONG).show()
                true
            }
            popup.show()
        }

        requestBluetoothPermissionIfNeeded()
        checkBluetoothStatus()
    }

    private fun setSmoothNavigate(button: Button, targetActivity: Class<*>) {
        button.setOnClickListener {
            if (isButtonAnimating) return@setOnClickListener
            isButtonAnimating = true
            button.startAnimation(shrinkAnim)

            shrinkAnim.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) { button.isClickable = false }
                override fun onAnimationEnd(animation: Animation?) {
                    button.clearAnimation()
                    button.isClickable = true
                    isButtonAnimating = false
                    startActivity(Intent(this@DashboardActivity, targetActivity))
                }
                override fun onAnimationRepeat(animation: Animation?) {}
            })
        }
    }

    private fun smoothBounceOnly(button: Button) {
        if (isButtonAnimating) return
        isButtonAnimating = true
        button.startAnimation(shrinkAnim)

        shrinkAnim.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) { button.isClickable = false }
            override fun onAnimationEnd(animation: Animation?) {
                button.clearAnimation()
                button.isClickable = true
                isButtonAnimating = false
            }
            override fun onAnimationRepeat(animation: Animation?) {}
        })
    }

    private fun checkBluetoothStatus() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            helmetStatusTextView.text = "🪖 Helmet Status: Permission Denied"
            helmetNameTextView.text = "🆔 Helmet Name: Permission Denied"
            return
        }

        val bondedDevices: Set<BluetoothDevice> = bluetoothAdapter.bondedDevices
        espDevice = bondedDevices.firstOrNull { it.name == "SmartHelmetESP32" }

        if (espDevice != null) {
            if (!isConnected) {
                connectToDevice(espDevice!!)
            } else {
                helmetStatusTextView.text = "🪖 Helmet Status: Already Connected"
                helmetNameTextView.text = "🆔 Helmet Name: ${espDevice!!.name}"
            }
        } else {
            isConnected = false
            helmetStatusTextView.text = "🪖 Helmet Status: Not Connected"
            helmetNameTextView.text = "🆔 Helmet Name: Not Found"
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        Thread {
            try {
                val uuid = device.uuids?.get(0)?.uuid
                    ?: UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
                bluetoothSocket?.connect()

                inputStream = bluetoothSocket?.inputStream
                outputStream = bluetoothSocket?.outputStream
                isConnected = true

                runOnUiThread {
                    helmetStatusTextView.text = "🪖 Helmet Status: Connected"
                    helmetNameTextView.text = "🆔 Helmet Name: ${device.name}"
                }

                // Request Helmet ID from ESP32
                requestHelmetId()

                startReadingSpeed()
            } catch (e: Exception) {
                e.printStackTrace()
                isConnected = false
                runOnUiThread {
                    helmetStatusTextView.text = "🪖 Helmet Status: Not Connected"
                    helmetNameTextView.text = "🆔 Helmet Name: Not Available"
                    Toast.makeText(this, "❌ Failed to connect to Helmet", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun requestHelmetId() {
        try {
            outputStream?.write("GET_ID\n".toByteArray())
            outputStream?.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startReadingSpeed() {
        Thread {
            val buffer = ByteArray(1024)
            while (isConnected) {
                try {
                    val bytes = inputStream?.read(buffer)
                    if (bytes != null && bytes > 0) {
                        val data = String(buffer, 0, bytes).trim()

                        if (data.startsWith("ID,")) {
                            // Extract Helmet ID
                            helmetId = data.substringAfter("ID,")
                            val prefs = getSharedPreferences("SmartHelmetPrefs", MODE_PRIVATE)
                            prefs.edit().putString("helmet_id", helmetId).apply()

                            runOnUiThread {
                                Toast.makeText(this, "✅ Helmet ID: $helmetId", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            // Otherwise, assume it's speed data
                            runOnUiThread { tvLiveSpeed.text = "$data km/h" }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    isConnected = false
                    runOnUiThread {
                        helmetStatusTextView.text = "🪖 Helmet Status: Disconnected"
                        tvLiveSpeed.text = "-- km/h"
                    }
                    break
                }
            }
        }.start()
    }

    private fun requestBluetoothPermissionIfNeeded() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                1001
            )
        }
    }
}

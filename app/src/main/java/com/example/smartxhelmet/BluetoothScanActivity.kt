package com.example.smartxhelmet

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.view.Gravity
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.view.animation.AlphaAnimation
import android.content.Intent

class BluetoothScanActivity : AppCompatActivity() {

    private lateinit var radarImage: ImageView
    private lateinit var radarStatus: TextView
    private lateinit var container: LinearLayout
    private lateinit var listView: ListView
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val devicesList = ArrayList<BluetoothDevice>()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            background = ContextCompat.getDrawable(this@BluetoothScanActivity, R.drawable.background) // ✅ SET BACKGROUND IMAGE
            gravity = Gravity.CENTER
        }

        radarImage = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(600, 600).apply {
                gravity = Gravity.CENTER
            }
            setImageResource(R.drawable.radar_circle)
        }

        radarStatus = TextView(this).apply {
            text = "Scanning for devices..."
            textSize = 18f
            setTextColor(0xFF7CFC00.toInt())
            gravity = Gravity.CENTER
        }

        listView = ListView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                600
            ).apply {
                topMargin = 30
            }
            dividerHeight = 2
        }

        container.addView(radarImage)
        container.addView(radarStatus)
        container.addView(listView)
        setContentView(container)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList())
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val device = devicesList[position]
            try {
                device.createBond()
                Toast.makeText(this, "Pairing with ${device.name}", Toast.LENGTH_SHORT).show()

                // ✅ Delay and show "Connected" after pairing attempt
                Handler(Looper.getMainLooper()).postDelayed({
                    showConnectedAnimation(device.name ?: "Device")
                }, 3000)

            } catch (e: SecurityException) {
                Toast.makeText(this, "Permission error pairing device!", Toast.LENGTH_SHORT).show()
            }
        }

        startRadarAnimation()
        checkBluetoothPermissions()
    }

    private fun showConnectedAnimation(deviceName: String) {
        // Clear UI
        container.removeAllViews()

        // ✅ Fade-in TextView
        val connectedText = TextView(this).apply {
            text = "Connected to $deviceName"
            textSize = 24f
            setTextColor(0xFF4CAF50.toInt()) // Green
            gravity = Gravity.CENTER
        }

        val fadeIn = AlphaAnimation(0f, 1f).apply {
            duration = 1000
            fillAfter = true
        }

        connectedText.startAnimation(fadeIn)
        container.addView(connectedText)

        // ✅ Delay and go to Dashboard
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, DashboardActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 3000)
    }

    private fun checkBluetoothPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 101)
        } else {
            startBluetoothScan()
        }
    }

    private fun startBluetoothScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Enable Bluetooth First", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        registerReceiver(receiver, filter)


        bluetoothAdapter.startDiscovery()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothDevice.ACTION_FOUND) {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                device?.let {
                    if (!devicesList.contains(it)) {
                        devicesList.add(it)
                        val name = it.name ?: "Unknown"
                        adapter.add("$name\n${it.address}")
                        adapter.notifyDataSetChanged()
                        radarStatus.text = "Found: $name"
                    }
                }
            }
        }
    }

    private fun startRadarAnimation() {
        val rotate = RotateAnimation(
            0f, 360f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 2000
            repeatCount = Animation.INFINITE
            interpolator = LinearInterpolator()
        }
        radarImage.startAnimation(rotate)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(receiver)
            bluetoothAdapter?.cancelDiscovery()
        } catch (_: Exception) {}
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startBluetoothScan()
        } else {
            Toast.makeText(this, "Bluetooth permission denied!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}

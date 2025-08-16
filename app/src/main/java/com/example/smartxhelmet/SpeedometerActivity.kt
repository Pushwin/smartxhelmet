package com.example.smartxhelmet

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.github.anastr.speedviewlib.SpeedView
import java.io.InputStream
import java.util.*
import kotlin.concurrent.thread

class SpeedometerActivity : AppCompatActivity() {

    private lateinit var speedometerView: SpeedView
    private lateinit var backButton: Button

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_speedometer)

        speedometerView = findViewById(R.id.speedometerView)
        backButton = findViewById(R.id.btnBackToDashboard)

        backButton.setOnClickListener {
            finish()
        }

        connectBluetoothAndReadSpeed()
    }

    private fun connectBluetoothAndReadSpeed() {
        val deviceName = "SmartHelmetESP32"  // Must match ESP32 name
        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices

        val device = pairedDevices?.find { it.name == deviceName }

        device?.let {
            thread {
                try {
                    val uuid = it.uuids[0].uuid
                    bluetoothSocket = it.createRfcommSocketToServiceRecord(uuid)
                    bluetoothSocket?.connect()

                    val inputStream: InputStream = bluetoothSocket!!.inputStream
                    val buffer = ByteArray(1024)
                    var bytes: Int

                    while (true) {
                        bytes = inputStream.read(buffer)
                        val incoming = String(buffer, 0, bytes)

                        if (incoming.contains("Speed:")) {
                            val speedValue = incoming.replace("Speed:", "").trim().toFloatOrNull()

                            speedValue?.let { speed ->
                                handler.post {
                                    speedometerView.speedTo(speed)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothSocket?.close()
    }
}

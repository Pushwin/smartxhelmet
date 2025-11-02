package com.example.smartxhelmet

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.anastr.speedviewlib.SpeedView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import java.io.IOException
import java.util.*

class LocationActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private var helmetMarker: Marker? = null
    private lateinit var tvLiveSpeed: TextView
    private lateinit var speedometerView: SpeedView

    private val btAdapter = BluetoothAdapter.getDefaultAdapter()
    private var btSocket: BluetoothSocket? = null
    private val btDeviceName = "SmartHelmetESP32"
    private val btUUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // SPP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location)

        tvLiveSpeed = findViewById(R.id.tvLiveSpeed)
        speedometerView = findViewById(R.id.speedometerView)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapContainer) as SupportMapFragment
        mapFragment.getMapAsync(this)

        connectBluetooth()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        val startPos = LatLng(28.6139, 77.2090)
        helmetMarker = mMap.addMarker(MarkerOptions().position(startPos).title("Helmet Location"))
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(startPos, 15f))
    }

    private fun connectBluetooth() {
        val pairedDevices: Set<BluetoothDevice>? = btAdapter?.bondedDevices
        val device = pairedDevices?.firstOrNull { it.name == btDeviceName }
        device?.let {
            Thread {
                try {
                    btSocket = it.createRfcommSocketToServiceRecord(btUUID)
                    btSocket?.connect()
                    readBluetooth()
                } catch (e: IOException) { e.printStackTrace() }
            }.start()
        }
    }

    private fun readBluetooth() {
        val input = btSocket?.inputStream?.bufferedReader()
        try {
            while (true) {
                val line = input?.readLine() ?: break
                runOnUiThread { handleBluetoothLine(line) }
            }
        } catch (e: IOException) { e.printStackTrace() }
    }

    private fun handleBluetoothLine(line: String) {
        when {
            line.startsWith("SPEED,") -> {
                val speed = line.split(",")[1].toFloatOrNull() ?: 0f
                tvLiveSpeed.text = "${speed} km/h"
                speedometerView.speedTo(speed)
            }
            line.startsWith("GPS,") -> {
                val parts = line.split(",")
                if (parts.size >= 3) {
                    val lat = parts[1].toDoubleOrNull() ?: return
                    val lng = parts[2].toDoubleOrNull() ?: return
                    val pos = LatLng(lat, lng)
                    if (helmetMarker == null) {
                        helmetMarker = mMap.addMarker(MarkerOptions().position(pos).title("Helmet"))
                    } else {
                        helmetMarker?.position = pos
                    }
                    mMap.animateCamera(CameraUpdateFactory.newLatLng(pos))
                }
            }
        }
    }
}

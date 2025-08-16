package com.example.smartxhelmet
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class AddContactActivity : AppCompatActivity() {

    private lateinit var etContactName: EditText
    private lateinit var etContactPhone: EditText
    private lateinit var etContactEmail: EditText
    private lateinit var btnSave: Button
    private lateinit var btnReloadContact: Button
    private lateinit var tvCurrentContact: TextView

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val deviceName = "SmartHelmetESP32"
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_contact)

        etContactName = findViewById(R.id.etContactName)
        etContactPhone = findViewById(R.id.etContactPhone)
        etContactEmail = findViewById(R.id.etContactEmail)
        btnSave = findViewById(R.id.btnSaveContact)
        btnReloadContact = findViewById(R.id.btnReloadContact)
        tvCurrentContact = findViewById(R.id.tvCurrentContact)

        btnSave.setOnClickListener {
            val name = etContactName.text.toString().trim()
            val phone = etContactPhone.text.toString().trim()
            val email = etContactEmail.text.toString().trim()

            if (phone.isEmpty()) {
                Toast.makeText(this, "Please enter phone number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val contactData = "SAVE,$name,$phone,$email\n"
            sendToBluetooth(contactData)
        }

        btnReloadContact.setOnClickListener {
            fetchSavedContactFromESP32()
        }

        // Auto fetch on screen open
        fetchSavedContactFromESP32()
    }

    private fun sendToBluetooth(data: String) {
        val device = getPairedESP32Device() ?: return

        Thread {
            try {
                val socket = device.createRfcommSocketToServiceRecord(uuid)
                socket.connect()
                socket.outputStream.write(data.toByteArray())
                runOnUiThread {
                    Toast.makeText(this, "Contact sent to ESP32", Toast.LENGTH_SHORT).show()
                }
                socket.close()
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Send Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun fetchSavedContactFromESP32() {
        val device = getPairedESP32Device() ?: return

        Thread {
            try {
                val socket = device.createRfcommSocketToServiceRecord(uuid)
                socket.connect()

                val outputStream = socket.outputStream
                val inputStream = socket.inputStream

                outputStream.write("GET_CONTACT\n".toByteArray())

                val buffer = ByteArray(1024)
                val bytes = inputStream.read(buffer)
                val received = String(buffer, 0, bytes)

                runOnUiThread {
                    if (received.startsWith("SAVED,") && !received.contains("None")) {
                        val parts = received.removePrefix("SAVED,").split(",")
                        if (parts.size >= 3) {
                            val contactInfo = "👤 ${parts[0]}\n📞 ${parts[1]}\n📧 ${parts[2]}"
                            tvCurrentContact.text = contactInfo
                        }
                    } else {
                        tvCurrentContact.text = "No saved contact."
                    }
                }

                socket.close()
            } catch (e: Exception) {
                runOnUiThread {
                    tvCurrentContact.text = "❌ Unable to fetch contact"
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun getPairedESP32Device(): BluetoothDevice? {
        val pairedDevices = bluetoothAdapter?.bondedDevices
        val device = pairedDevices?.firstOrNull { it.name == deviceName }
        if (device == null) {
            Toast.makeText(this, "❌ ESP32 not paired!", Toast.LENGTH_SHORT).show()
        }
        return device
    }
}

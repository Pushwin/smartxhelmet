package com.example.smartxhelmet

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LocationActivity : AppCompatActivity() {

    private lateinit var etUserId: EditText
    private lateinit var btnFetch: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location)

        etUserId = findViewById(R.id.etUserId)
        btnFetch = findViewById(R.id.btnFetch)

        btnFetch.setOnClickListener {
            val enteredId = etUserId.text.toString().trim()
            if (enteredId.isNotEmpty()) {
                Toast.makeText(this, "Fetching location for ID: $enteredId", Toast.LENGTH_SHORT).show()
                // 🔥 Later: Fetch location from Firebase/Server here
            } else {
                Toast.makeText(this, "Please enter a valid ID", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

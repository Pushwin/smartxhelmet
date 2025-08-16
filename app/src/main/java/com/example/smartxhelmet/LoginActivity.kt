package com.example.smartxhelmet

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var videoView: VideoView
    private var videoUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        videoView = findViewById(R.id.videoView)
        videoUri = Uri.parse("android.resource://$packageName/${R.raw.videoo}")
        setupVideoView()

        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvCreateAccount = findViewById<TextView>(R.id.tvCreateAccount)
        val btnCallAmbulance = findViewById<Button>(R.id.btnCallAmbulance)

        btnLogin.setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
        }

        tvCreateAccount.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        btnCallAmbulance.setOnClickListener {
            Toast.makeText(this, "Calling ambulance...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupVideoView() {
        videoUri?.let {
            videoView.setVideoURI(it)
            videoView.setOnPreparedListener { mp ->
                mp.isLooping = true
                mp.setVolume(0f, 0f) // Mute video
            }
            videoView.start()
        }
    }

    override fun onResume() {
        super.onResume()
        videoView.resume()
        videoView.start()
    }

    override fun onPause() {
        super.onPause()
        videoView.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        videoView.stopPlayback()
    }
}

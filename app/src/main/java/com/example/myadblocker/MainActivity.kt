package com.example.myadblocker

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {

    private lateinit var statusText: TextView
    private lateinit var counterText: TextView

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_main
        )

        statusText =
            findViewById(
                R.id.statusText
            )

        counterText =
            findViewById(
                R.id.counterText
            )

        findViewById<Button>(
            R.id.startButton
        ).setOnClickListener {

            requestVpnPermission()
        }

        findViewById<Button>(
            R.id.stopButton
        ).setOnClickListener {

            stopVpn()
        }

        updateUi()
    }

    private fun requestVpnPermission() {

        val intent =
            VpnService.prepare(this)

        if (intent != null) {

            startActivityForResult(
                intent,
                VPN_REQUEST
            )

        } else {

            startVpn()
        }
    }

    private fun startVpn() {

        val intent =
            Intent(
                this,
                AdBlockVpnService::class.java
            )

        startService(intent)

        updateUi()
    }

    private fun stopVpn() {

        val intent =
            Intent(
                this,
                AdBlockVpnService::class.java
            )

        stopService(intent)

        updateUi()
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {

        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (
            requestCode == VPN_REQUEST &&
            resultCode == RESULT_OK
        ) {

            startVpn()
        }
    }

    private fun updateUi() {

        statusText.text =
            if (
                AdBlockVpnService.running
            ) {
                "Protection ON"
            } else {
                "Protection OFF"
            }

        counterText.text =
            "Blocked: ${
                AdBlockVpnService
                    .blockedCount
                    .get()
            }"
    }

    companion object {

        private const val VPN_REQUEST = 100
    }
}


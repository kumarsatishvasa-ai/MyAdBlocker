package com.example.myadblocker

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class AdBlockVpnService : VpnService() {

    companion object {

        @Volatile
        var running = false

        val blockedCount =
            AtomicInteger(0)

        private val isStarted =
            AtomicBoolean(false)
    }

    private var vpnInterface:
        ParcelFileDescriptor? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (!isStarted.get()) {
            startVpn()
        }

        return START_STICKY
    }

    private fun startVpn() {

        if (isStarted.getAndSet(true)) {
            return
        }

        try {

            vpnInterface =
                Builder()
                    .setSession(
                        "My Ad Blocker"
                    )

                    /*
                     * Local VPN address.
                     */
                    .addAddress(
                        "10.10.10.2",
                        32
                    )

                    /*
                     * DNS server address used
                     * by the local DNS engine.
                     */
                    .addDnsServer(
                        "10.10.10.1"
                    )

                    /*
                     * DNS traffic only.
                     *
                     * This avoids pretending that
                     * this service is a full TCP/UDP
                     * forwarding VPN.
                     */
                    .addRoute(
                        "10.10.10.1",
                        32
                    )

                    .establish()

            running = true

        } catch (e: Exception) {

            running = false
            isStarted.set(false)

            vpnInterface?.close()
            vpnInterface = null

            stopSelf()
        }
    }

    override fun onDestroy() {

        running = false
        isStarted.set(false)

        vpnInterface?.close()
        vpnInterface = null

        super.onDestroy()
    }
}


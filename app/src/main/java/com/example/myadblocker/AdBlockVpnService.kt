package com.example.myadblocker

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class AdBlockVpnService : VpnService() {

    companion object {
        @Volatile
        var running = false

        val blockedCount = AtomicInteger(0)

        private const val VPN_ADDRESS = "10.8.0.2"
        private const val DNS_ADDRESS = "10.8.0.1"

        private const val UPSTREAM_DNS = "1.1.1.1"
        private const val DNS_PORT = 53
    }

    private var vpnInterface: ParcelFileDescriptor? = null

    private var job: Job? = null

    private val started =
        AtomicBoolean(false)

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (started.compareAndSet(false, true)) {
            startVpn()
        }

        return START_STICKY
    }

    private fun startVpn() {

        try {

            vpnInterface =
                Builder()
                    .setSession("My Ad Blocker")

                    .addAddress(
                        VPN_ADDRESS,
                        32
                    )

                    /*
                     * Only DNS traffic is routed
                     * through the VPN.
                     */
                    .addRoute(
                        DNS_ADDRESS,
                        32
                    )

                    .addDnsServer(
                        DNS_ADDRESS
                    )

                    .setBlocking(false)

                    .establish()

            if (vpnInterface == null) {
                stopSelf()
                return
            }

            running = true

            job =
                CoroutineScope(
                    Dispatchers.IO +
                    SupervisorJob()
                ).launch {

                    packetLoop()
                }

        } catch (e: Exception) {

            e.printStackTrace()

            running = false
            started.set(false)

            stopSelf()
        }
    }

    private suspend fun packetLoop() {

        val vpn =
            vpnInterface ?: return

        val input =
            FileInputStream(
                vpn.fileDescriptor
            )

        val output =
            FileOutputStream(
                vpn.fileDescriptor
            )

        val buffer =
            ByteArray(32767)

        while (
            isActive &&
            running
        ) {

            val length =
                withContext(
                    Dispatchers.IO
                ) {
                    input.read(buffer)
                }

            if (length <= 0) {
                continue
            }

            val packet =
                buffer.copyOf(length)

            val dns =
                DnsPacket.parse(packet)

            if (dns == null) {
                continue
            }

            if (
                BlockList.isBlocked(
                    dns.hostname
                )
            ) {

                blockedCount.incrementAndGet()

                val response =
                    DnsPacket.blockedResponse(
                        dns
                    )

                output.write(response)

            } else {

                val response =
                    forwardDns(
                        dns.query
                    )

                if (response != null) {

                    /*
                     * The returned DNS message
                     * must be placed into a proper
                     * IP/UDP response packet.
                     */
                    val reply =
                        DnsPacket.createUdpIpv4Response(
                            packet,
                            response
                        )

                    output.write(reply)
                }
            }
        }
    }

    private fun forwardDns(
        query: ByteArray
    ): ByteArray? {

        return try {

            DatagramSocket().use { socket ->

                protect(socket)

                val address =
                    InetAddress.getByName(
                        UPSTREAM_DNS
                    )

                val request =
                    DatagramPacket(
                        query,
                        query.size,
                        address,
                        DNS_PORT
                    )

                socket.soTimeout = 5000

                socket.send(request)

                val responseBuffer =
                    ByteArray(4096)

                val response =
                    DatagramPacket(
                        responseBuffer,
                        responseBuffer.size
                    )

                socket.receive(response)

                responseBuffer.copyOf(
                    response.length
                )
            }

        } catch (e: Exception) {

            null
        }
    }

    override fun onDestroy() {

        running = false
        started.set(false)

        job?.cancel()

        job = null

        vpnInterface?.close()

        vpnInterface = null

        super.onDestroy()
    }
}

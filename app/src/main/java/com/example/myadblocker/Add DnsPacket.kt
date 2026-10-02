package com.example.myadblocker

import java.nio.ByteBuffer

data class DnsQuery(
    val hostname: String,
    val query: ByteArray,
    val transactionId: ByteArray,
    val sourcePacket: ByteArray
)

object DnsPacket {

    fun parse(
        packet: ByteArray
    ): DnsQuery? {

        /*
         * IPv4 header
         */

        if (packet.size < 28) {
            return null
        }

        val version =
            (packet[0].toInt() shr 4) and 0xF

        if (version != 4) {
            return null
        }

        val ihl =
            (packet[0].toInt() and 0xF) * 4

        if (packet.size < ihl + 8) {
            return null
        }

        val protocol =
            packet[9].toInt() and 0xFF

        /*
         * UDP
         */

        if (protocol != 17) {
            return null
        }

        val udpStart = ihl

        val destinationPort =
            unsignedShort(
                packet,
                udpStart + 2
            )

        if (destinationPort != 53) {
            return null
        }

        val dnsStart =
            udpStart + 8

        if (
            packet.size <
            dnsStart + 12
        ) {
            return null
        }

        val transactionId =
            packet.copyOfRange(
                dnsStart,
                dnsStart + 2
            )

        val questionCount =
            unsignedShort(
                packet,
                dnsStart + 4
            )

        if (questionCount < 1) {
            return null
        }

        var position =
            dnsStart + 12

        val labels =
            mutableListOf<String>()

        while (
            position < packet.size
        ) {

            val length =
                packet[position].toInt() and 0xFF

            position++

            if (length == 0) {
                break
            }

            if (
                length > 63 ||
                position + length >
                packet.size
            ) {
                return null
            }

            labels.add(
                String(
                    packet,
                    position,
                    length,
                    Charsets.US_ASCII
                )
            )

            position += length
        }

        if (labels.isEmpty()) {
            return null
        }

        val hostname =
            labels.joinToString(".")
                .lowercase()

        return DnsQuery(
            hostname = hostname,
            query = packet.copyOfRange(
                dnsStart,
                packet.size
            ),
            transactionId = transactionId,
            sourcePacket = packet
        )
    }

    fun blockedResponse(
        query: DnsQuery
    ): ByteArray {

        val dns =
            query.query.clone()

        /*
         * DNS flags:
         *
         * QR = response
         * RA = recursion available
         *
         * RCODE = NXDOMAIN
         */

        val flags =
            0x8183

        dns[2] =
            (flags shr 8).toByte()

        dns[3] =
            (flags and 0xFF).toByte()

        /*
         * Answer count = 0
         */

        dns[6] = 0
        dns[7] = 0

        /*
         * Authority count = 0
         */

        dns[8] = 0
        dns[9] = 0

        /*
         * Additional count = 0
         */

        dns[10] = 0
        dns[11] = 0

        return createUdpIpv4Response(
            query.sourcePacket,
            dns
        )
    }

    fun createUdpIpv4Response(
        originalPacket: ByteArray,
        dnsResponse: ByteArray
    ): ByteArray {

        val ihl =
            (originalPacket[0]
                .toInt() and 0xF) * 4

        val udpLength =
            8 + dnsResponse.size

        val totalLength =
            ihl + udpLength

        val result =
            ByteArray(totalLength)

        /*
         * Copy IPv4 header.
         */

        originalPacket.copyInto(
            result,
            0,
            0,
            ihl
        )

        /*
         * Swap source/destination IP.
         */

        for (i in 0 until 4) {

            result[i + 12] =
                originalPacket[i + 16]

            result[i + 16] =
                originalPacket[i + 12]
        }

        /*
         * Protocol = UDP.
         */

        result[9] = 17

        /*
         * Total length.
         */

        result[2] =
            (totalLength shr 8).toByte()

        result[3] =
            (totalLength and 0xFF).toByte()

        /*
         * Reset IP checksum.
         */

        result[10] = 0
        result[11] = 0

        val ipChecksum =
            checksum(
                result,
                0,
                ihl
            )

        result[10] =
            (ipChecksum shr 8).toByte()

        result[11] =
            (ipChecksum and 0xFF).toByte()

        /*
         * UDP header.
         */

        val udpStart = ihl

        result[udpStart] =
            originalPacket[
                udpStart + 2
            ]

        result[udpStart + 1] =
            originalPacket[
                udpStart + 3
            ]

        result[udpStart + 2] =
            originalPacket[
                udpStart
            ]

        result[udpStart + 3] =
            originalPacket[
                udpStart + 1
            ]

        result[udpStart + 4] =
            (udpLength shr 8).toByte()

        result[udpStart + 5] =
            (udpLength and 0xFF).toByte()

        /*
         * UDP checksum = 0 for IPv4.
         */

        result[udpStart + 6] = 0
        result[udpStart + 7] = 0

        dnsResponse.copyInto(
            result,
            udpStart + 8
        )

        return result
    }

    private fun unsignedShort(
        data: ByteArray,
        offset: Int
    ): Int {

        return (
            ((data[offset].toInt() and 0xFF) shl 8) or
            (data[offset + 1].toInt() and 0xFF)
        )
    }

    private fun checksum(
        data: ByteArray,
        offset: Int,
        length: Int
    ): Int {

        var sum = 0

        var i = offset

        while (i < offset + length - 1) {

            sum +=
                ((data[i].toInt() and 0xFF) shl 8) or
                (data[i + 1].toInt() and 0xFF)

            sum =
                (sum and 0xFFFF) +
                (sum shr 16)

            i += 2
        }

        if (
            i ==
            offset + length - 1
        ) {

            sum +=
                (data[i].toInt() and 0xFF) shl 8
        }

        while (
            (sum shr 16) != 0
        ) {

            sum =
                (sum and 0xFFFF) +
                (sum shr 16)
        }

        return sum.inv() and 0xFFFF
    }
}

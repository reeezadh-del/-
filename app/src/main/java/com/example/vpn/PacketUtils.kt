package com.example.vpn

import java.nio.ByteBuffer

data class UdpDnsPacket(
    val srcIp: ByteArray,
    val dstIp: ByteArray,
    val srcPort: Int,
    val dstPort: Int,
    val dnsPayload: ByteArray
)

object PacketUtils {

    fun parseUdpDnsPacket(raw: ByteArray, length: Int): UdpDnsPacket? {
        if (length < 28) return null
        val version = (raw[0].toInt() ushr 4) and 0x0F
        if (version != 4) return null

        val ihl = (raw[0].toInt() and 0x0F) * 4
        if (ihl < 20 || length < ihl + 8) return null

        val protocol = raw[9].toInt() and 0xFF
        if (protocol != 17) return null // UDP only

        val srcIp = raw.copyOfRange(12, 16)
        val dstIp = raw.copyOfRange(16, 20)

        val srcPort = ((raw[ihl].toInt() and 0xFF) shl 8) or (raw[ihl + 1].toInt() and 0xFF)
        val dstPort = ((raw[ihl + 2].toInt() and 0xFF) shl 8) or (raw[ihl + 3].toInt() and 0xFF)
        if (dstPort != 53) return null

        val udpLength = ((raw[ihl + 4].toInt() and 0xFF) shl 8) or (raw[ihl + 5].toInt() and 0xFF)
        val payloadLen = (udpLength - 8).coerceAtMost(length - (ihl + 8))
        if (payloadLen <= 0) return null

        val dnsPayload = raw.copyOfRange(ihl + 8, ihl + 8 + payloadLen)
        return UdpDnsPacket(
            srcIp = srcIp,
            dstIp = dstIp,
            srcPort = srcPort,
            dstPort = dstPort,
            dnsPayload = dnsPayload
        )
    }

    fun buildUdpDnsResponse(request: UdpDnsPacket, dnsResponsePayload: ByteArray): ByteArray {
        val ipHeaderLen = 20
        val udpHeaderLen = 8
        val totalLen = ipHeaderLen + udpHeaderLen + dnsResponsePayload.size
        val udpLen = udpHeaderLen + dnsResponsePayload.size

        val buffer = ByteBuffer.allocate(totalLen)

        // IPv4 Header
        buffer.put(0x45.toByte()) // Version 4, IHL 5
        buffer.put(0x00.toByte()) // TOS
        buffer.putShort(totalLen.toShort())
        buffer.putShort(0x0000.toShort()) // Identification
        buffer.putShort(0x4000.toShort()) // Flags: Don't Fragment
        buffer.put(64.toByte()) // TTL
        buffer.put(17.toByte()) // Protocol: UDP
        buffer.putShort(0) // Checksum placeholder
        buffer.put(request.dstIp) // Source IP = original dest
        buffer.put(request.srcIp) // Dest IP = original src

        // UDP Header
        buffer.putShort(request.dstPort.toShort()) // Source Port = 53
        buffer.putShort(request.srcPort.toShort()) // Dest Port = original srcPort
        buffer.putShort(udpLen.toShort())
        buffer.putShort(0) // UDP Checksum placeholder
        buffer.put(dnsResponsePayload)

        val packet = buffer.array()

        // Compute IPv4 header checksum
        val ipChecksum = computeChecksum(packet, 0, ipHeaderLen)
        packet[10] = ((ipChecksum ushr 8) and 0xFF).toByte()
        packet[11] = (ipChecksum and 0xFF).toByte()

        // Compute UDP checksum with IPv4 pseudo-header
        val udpChecksum = computeUdpChecksum(
            srcIp = request.dstIp,
            dstIp = request.srcIp,
            udpSegment = packet,
            udpOffset = ipHeaderLen,
            udpLength = udpLen
        )
        packet[ipHeaderLen + 6] = ((udpChecksum ushr 8) and 0xFF).toByte()
        packet[ipHeaderLen + 7] = (udpChecksum and 0xFF).toByte()

        return packet
    }

    fun extractDnsQueryName(dnsPayload: ByteArray): String {
        if (dnsPayload.size < 13) return "unknown"
        return try {
            val sb = StringBuilder()
            var index = 12
            while (index < dnsPayload.size) {
                val len = dnsPayload[index].toInt() and 0xFF
                if (len == 0) break
                if (index + 1 + len > dnsPayload.size) break
                if (sb.isNotEmpty()) sb.append('.')
                sb.append(String(dnsPayload, index + 1, len, Charsets.US_ASCII))
                index += len + 1
            }
            sb.toString().ifBlank { "unknown" }
        } catch (_: Exception) {
            "unknown"
        }
    }

    private fun computeChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        val end = offset + length
        while (i < end - 1) {
            val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < end) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }
        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return (sum.inv() and 0xFFFF).toInt()
    }

    private fun computeUdpChecksum(
        srcIp: ByteArray,
        dstIp: ByteArray,
        udpSegment: ByteArray,
        udpOffset: Int,
        udpLength: Int
    ): Int {
        var sum = 0L
        for (i in 0 until 4 step 2) {
            sum += ((srcIp[i].toInt() and 0xFF) shl 8) or (srcIp[i + 1].toInt() and 0xFF)
            sum += ((dstIp[i].toInt() and 0xFF) shl 8) or (dstIp[i + 1].toInt() and 0xFF)
        }
        sum += 17L // UDP protocol
        sum += udpLength.toLong()

        var i = udpOffset
        val end = udpOffset + udpLength
        while (i < end - 1) {
            val word = ((udpSegment[i].toInt() and 0xFF) shl 8) or (udpSegment[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < end) {
            sum += (udpSegment[i].toInt() and 0xFF) shl 8
        }
        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        val checksum = (sum.inv() and 0xFFFF).toInt()
        return if (checksum == 0) 0xFFFF else checksum
    }
}

package com.example.vpn

import android.net.VpnService
import com.example.data.VpnRuntimeState
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

class DohResolver(
    private val vpnService: VpnService,
    private val dohEndpointUrl: String,
    private val bootstrapIp: String
) {
    private data class CachedHost(val addresses: List<InetAddress>, val expiresAtMs: Long)

    private val hostCache = ConcurrentHashMap<String, CachedHost>()

    private val protectedSocketFactory = object : SocketFactory() {
        private val delegate = getDefault()

        override fun createSocket(): Socket =
            delegate.createSocket().also { vpnService.protect(it) }

        override fun createSocket(host: String?, port: Int): Socket =
            delegate.createSocket(host, port).also { vpnService.protect(it) }

        override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
            delegate.createSocket(host, port, localHost, localPort).also { vpnService.protect(it) }

        override fun createSocket(host: InetAddress?, port: Int): Socket =
            delegate.createSocket(host, port).also { vpnService.protect(it) }

        override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
            delegate.createSocket(address, port, localAddress, localPort).also { vpnService.protect(it) }
    }

    private val bootstrapDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return when (hostname.lowercase()) {
                "cloudflare-dns.com", "one.one.one.one" -> listOf(
                    InetAddress.getByName("1.1.1.1"),
                    InetAddress.getByName("1.0.0.1")
                )
                "dns.google" -> listOf(
                    InetAddress.getByName("8.8.8.8"),
                    InetAddress.getByName("8.8.4.4")
                )
                "dns.quad9.net" -> listOf(InetAddress.getByName("9.9.9.9"))
                "dns.adguard-dns.com" -> listOf(InetAddress.getByName("94.140.14.14"))
                "free.shecan.ir" -> listOf(InetAddress.getByName("178.22.122.100"))
                else -> Dns.SYSTEM.lookup(hostname)
            }
        }
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .socketFactory(protectedSocketFactory)
        .dns(bootstrapDns)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    fun resolveWireDns(dnsWireQuery: ByteArray): ByteArray? {
        val mediaType = "application/dns-message".toMediaType()
        val request = Request.Builder()
            .url(dohEndpointUrl)
            .header("Accept", "application/dns-message")
            .post(dnsWireQuery.toRequestBody(mediaType))
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null && bytes.size >= 12) {
                        return bytes
                    }
                }
            }
        } catch (e: Exception) {
            VpnRuntimeState.appendLog("DoH", "خطای موقت DoH (${e.message})، تلاش از مسیر پشتیبان", isError = false)
        }

        return fallbackProtectedUdpQuery(dnsWireQuery)
    }

    fun resolveHost(host: String): InetAddress? {
        val cleanHost = host.trim()
        if (cleanHost.isEmpty()) return null

        // Direct IP check
        if (cleanHost.matches(Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$"))) {
            return InetAddress.getByName(cleanHost)
        }

        val now = System.currentTimeMillis()
        val cached = hostCache[cleanHost]
        if (cached != null && cached.expiresAtMs > now && cached.addresses.isNotEmpty()) {
            return cached.addresses.first()
        }

        val wireQuery = buildARecordQuery(cleanHost)
        val wireResp = resolveWireDns(wireQuery)
        if (wireResp != null) {
            val parsed = parseARecordsFromDnsResponse(wireResp)
            if (parsed.isNotEmpty()) {
                hostCache[cleanHost] = CachedHost(parsed, now + 60_000L)
                return parsed.first()
            }
        }

        return try {
            InetAddress.getByName(cleanHost)
        } catch (_: Exception) {
            null
        }
    }

    private fun fallbackProtectedUdpQuery(dnsWireQuery: ByteArray): ByteArray? {
        return try {
            DatagramSocket().use { udpSocket ->
                vpnService.protect(udpSocket)
                udpSocket.soTimeout = 3500
                val dest = InetAddress.getByName(bootstrapIp)
                udpSocket.send(DatagramPacket(dnsWireQuery, dnsWireQuery.size, dest, 53))
                val respBuf = ByteArray(4096)
                val respPacket = DatagramPacket(respBuf, respBuf.size)
                udpSocket.receive(respPacket)
                respBuf.copyOf(respPacket.length)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildARecordQuery(hostname: String): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeShort(0x1337) // Transaction ID
        dos.writeShort(0x0100) // Standard query, recursion desired
        dos.writeShort(1) // QDCOUNT
        dos.writeShort(0) // ANCOUNT
        dos.writeShort(0) // NSCOUNT
        dos.writeShort(0) // ARCOUNT
        for (label in hostname.split('.')) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            dos.writeByte(bytes.size)
            dos.write(bytes)
        }
        dos.writeByte(0)
        dos.writeShort(1) // Type A
        dos.writeShort(1) // Class IN
        dos.flush()
        return baos.toByteArray()
    }

    private fun parseARecordsFromDnsResponse(resp: ByteArray): List<InetAddress> {
        if (resp.size < 12) return emptyList()
        val qdCount = ((resp[4].toInt() and 0xFF) shl 8) or (resp[5].toInt() and 0xFF)
        val anCount = ((resp[6].toInt() and 0xFF) shl 8) or (resp[7].toInt() and 0xFF)
        if (anCount == 0) return emptyList()

        var offset = 12
        repeat(qdCount) {
            offset = skipDnsName(resp, offset)
            if (offset < 0 || offset + 4 > resp.size) return emptyList()
            offset += 4
        }

        val results = mutableListOf<InetAddress>()
        repeat(anCount) {
            if (offset >= resp.size) return results
            offset = skipDnsName(resp, offset)
            if (offset < 0 || offset + 10 > resp.size) return results
            val type = ((resp[offset].toInt() and 0xFF) shl 8) or (resp[offset + 1].toInt() and 0xFF)
            val rdLength = ((resp[offset + 8].toInt() and 0xFF) shl 8) or (resp[offset + 9].toInt() and 0xFF)
            offset += 10
            if (offset + rdLength > resp.size) return results
            if (type == 1 && rdLength == 4) {
                val ipBytes = resp.copyOfRange(offset, offset + 4)
                results.add(InetAddress.getByAddress(ipBytes))
            }
            offset += rdLength
        }
        return results
    }

    private fun skipDnsName(data: ByteArray, startOffset: Int): Int {
        var idx = startOffset
        while (idx < data.size) {
            val len = data[idx].toInt() and 0xFF
            if (len == 0) return idx + 1
            if ((len and 0xC0) == 0xC0) return idx + 2
            idx += len + 1
        }
        return -1
    }
}

package com.example

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.example.data.ConfigUriParser
import com.example.model.ProxyProtocol
import com.example.vpn.PacketUtils
import com.example.vpn.UdpDnsPacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("سپر نت", appName)
    }

    @Test
    fun `parse vless trojan socks and base64 subscription`() {
        val vmessJson = """{"add":"de1.example.com","port":443,"id":"11111111-1111-1111-1111-111111111111","net":"ws","tls":"tls","sni":"cdn.example.com","ps":"GermanyNode"}"""
        val vmessUri = "vmess://" + Base64.encodeToString(vmessJson.toByteArray(), Base64.NO_WRAP)
        val rawList = """
            vless://22222222-2222-2222-2222-222222222222@nl1.example.com:443?security=tls&sni=sub.example.com&type=ws#Netherlands
            $vmessUri
            trojan://secretpass@tr1.example.com:443?sni=tr1.example.com#Turkey
            socks5://user:pass@198.51.100.10:1080#MySocks
        """.trimIndent()

        val encodedSub = Base64.encodeToString(rawList.toByteArray(), Base64.NO_WRAP)
        val profiles = ConfigUriParser.parseBulk(encodedSub)

        assertEquals(4, profiles.size)
        assertEquals(ProxyProtocol.VLESS, profiles[0].protocol)
        assertEquals("Netherlands", profiles[0].name)
        assertEquals("sub.example.com", profiles[0].sni)

        assertEquals(ProxyProtocol.VMESS, profiles[1].protocol)
        assertEquals("GermanyNode", profiles[1].name)
        assertEquals(443, profiles[1].port)

        assertEquals(ProxyProtocol.TROJAN, profiles[2].protocol)
        assertEquals("Turkey", profiles[2].name)

        assertEquals(ProxyProtocol.SOCKS5, profiles[3].protocol)
        assertTrue(profiles[3].protocol.supportsDirectTunnel)
        assertEquals("user", profiles[3].username)
        assertEquals("pass", profiles[3].passwordOrUuid)
    }

    @Test
    fun `packet utils round trips ipv4 udp dns response`() {
        val dnsPayload = byteArrayOf(
            0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x07, 'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte(),
            'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
            0x03, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(), 0x00,
            0x00, 0x01, 0x00, 0x01
        )
        val req = UdpDnsPacket(
            srcIp = byteArrayOf(10, 120, 0, 2),
            dstIp = byteArrayOf(10, 120, 0, 1),
            srcPort = 54321,
            dstPort = 53,
            dnsPayload = dnsPayload
        )
        val respPacket = PacketUtils.buildUdpDnsResponse(req, dnsPayload)
        assertTrue(respPacket.size == 28 + dnsPayload.size)

        // Verify swapped IPv4 addresses and ports
        assertEquals(10.toByte(), respPacket[12])
        assertEquals(1.toByte(), respPacket[15])
        assertEquals(2.toByte(), respPacket[19])
        assertEquals("example.com", PacketUtils.extractDnsQueryName(dnsPayload))

        // Re-parse checking header structure (dst port in response is 54321, src port is 53)
        val srcPort = ((respPacket[20].toInt() and 0xFF) shl 8) or (respPacket[21].toInt() and 0xFF)
        val dstPort = ((respPacket[22].toInt() and 0xFF) shl 8) or (respPacket[23].toInt() and 0xFF)
        assertEquals(53, srcPort)
        assertEquals(54321, dstPort)
        assertNotNull(respPacket)
    }
}

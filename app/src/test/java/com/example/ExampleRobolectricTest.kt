package com.example

import android.content.Context
import android.util.Base64
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.ConfigUriParser
import com.example.model.ConnectionState
import com.example.model.ProxyProtocol
import com.example.model.VpnTelemetry
import com.example.ui.MainUiState
import com.example.ui.screens.ConnectScreen
import com.example.ui.theme.SeparNetTheme
import com.example.vpn.PacketUtils
import com.example.vpn.UdpDnsPacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

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

    @Test
    fun `singbox config builder generates valid vless reality and anti-dpi configs`() {
        val vlessRealityUri =
            "vless://33333333-3333-3333-3333-333333333333@de.example.com:443?security=reality&sni=www.google.com&fp=chrome&pbk=abc123pbk&sid=01ab&flow=xtls-rprx-vision#GermanyReality"
        val profile = ConfigUriParser.parseSingle(vlessRealityUri)
        assertNotNull(profile)

        val proxySettings = com.example.data.UserSettings(
            vpnMode = com.example.model.VpnMode.PROXY_CHAIN
        )
        val proxyJson = com.example.vpn.SingBoxConfigBuilder.buildConfig(
            settings = proxySettings,
            profile = profile,
            localSocksAntiDpiPort = 0
        )
        val parsedProxy = org.json.JSONObject(proxyJson)
        assertEquals("proxy", parsedProxy.getJSONObject("route").getString("final"))
        assertEquals("sniff", parsedProxy.getJSONObject("route").getJSONArray("rules").getJSONObject(0).getString("action"))
        assertEquals(2, parsedProxy.getJSONArray("inbounds").getJSONObject(0).getJSONArray("address").length())
        val dnsServers = parsedProxy.getJSONObject("dns").getJSONArray("servers")
        assertEquals("https://1.1.1.1/dns-query", dnsServers.getJSONObject(0).getString("address"))
        assertEquals("local", dnsServers.getJSONObject(1).getString("address"))
        val outbounds = parsedProxy.getJSONArray("outbounds")
        val vlessOutbound = outbounds.getJSONObject(0)
        assertEquals("vless", vlessOutbound.getString("type"))
        assertEquals("xtls-rprx-vision", vlessOutbound.getString("flow"))
        assertTrue(vlessOutbound.getJSONObject("tls").getJSONObject("reality").getBoolean("enabled"))
        assertEquals("abc123pbk", vlessOutbound.getJSONObject("tls").getJSONObject("reality").getString("public_key"))

        val dpiSettings = com.example.data.UserSettings(
            vpnMode = com.example.model.VpnMode.DPI_FRAGMENT_DOH
        )
        val dpiJson = com.example.vpn.SingBoxConfigBuilder.buildConfig(
            settings = dpiSettings,
            profile = null,
            localSocksAntiDpiPort = 19080
        )
        val dpiParsed = org.json.JSONObject(dpiJson)
        assertEquals("anti-dpi-socks", dpiParsed.getJSONObject("route").getString("final"))
        assertEquals(
            "anti-dpi-socks",
            dpiParsed.getJSONObject("dns").getJSONArray("servers").getJSONObject(0).getString("detour")
        )

        val vlessTlsWs = ConfigUriParser.parseSingle(
            "vless://44444444-4444-4444-4444-444444444444@cdn.example.com:443?security=tls&sni=cdn.example.com&type=ws&path=/ws#CloudflareTls"
        )
        assertNotNull(vlessTlsWs)
        val tlsFragJson = com.example.vpn.SingBoxConfigBuilder.buildConfig(
            settings = com.example.data.UserSettings(
                vpnMode = com.example.model.VpnMode.PROXY_CHAIN,
                tlsFragmentEnabled = true,
                fragmentDelayMs = 200
            ),
            profile = vlessTlsWs,
            localSocksAntiDpiPort = 0
        )
        val tlsFragObj = org.json.JSONObject(tlsFragJson).getJSONArray("outbounds").getJSONObject(0).getJSONObject("tls")
        assertTrue(tlsFragObj.getBoolean("fragment"))
        assertEquals("200ms", tlsFragObj.getString("fragment_fallback_delay"))
    }

    @Test
    fun `parse new circumvention protocols and wireguard conf`() {
        val hy2 = ConfigUriParser.parseSingle(
            "hy2://secret123@hy.example.com:8443?sni=edge.example.com&obfs=salamander&obfs-password=obfspass&upmbps=50&downmbps=100&insecure=1#FastHY2"
        )
        assertNotNull(hy2)
        assertEquals(ProxyProtocol.HYSTERIA2, hy2!!.protocol)
        assertEquals("FastHY2", hy2.name)
        assertEquals("salamander", hy2.obfsType)
        assertEquals("obfspass", hy2.obfsPassword)

        val tuic = ConfigUriParser.parseSingle(
            "tuic://11111111-1111-1111-1111-111111111111:tuicpass@tuic.example.com:443?sni=cdn.example.com&congestion_control=bbr&udp_relay_mode=quic#MyTUIC"
        )
        assertNotNull(tuic)
        assertEquals(ProxyProtocol.TUIC, tuic!!.protocol)
        assertEquals("11111111-1111-1111-1111-111111111111", tuic.username)
        assertEquals("tuicpass", tuic.passwordOrUuid)

        val wgUri = ConfigUriParser.parseSingle(
            "wireguard://privKeyBase64@162.159.192.1:2408?publickey=pubKeyBase64&address=172.16.0.2/32&mtu=1280&reserved=1,2,3#WarpNode"
        )
        assertNotNull(wgUri)
        assertEquals(ProxyProtocol.WIREGUARD, wgUri!!.protocol)
        assertEquals("privKeyBase64", wgUri.passwordOrUuid)
        assertEquals("pubKeyBase64", wgUri.realityPublicKey)

        val wgConfText = """
            [Interface]
            PrivateKey = confPrivKey
            Address = 10.0.0.2/32
            MTU = 1320

            [Peer]
            PublicKey = confPubKey
            PresharedKey = confPsk
            Endpoint = 198.51.100.50:51820
        """.trimIndent()
        val wgConfProfiles = ConfigUriParser.parseBulk(wgConfText)
        assertEquals(1, wgConfProfiles.size)
        assertEquals(ProxyProtocol.WIREGUARD, wgConfProfiles[0].protocol)
        assertEquals("198.51.100.50", wgConfProfiles[0].host)
        assertEquals(51820, wgConfProfiles[0].port)
        assertEquals("confPsk", wgConfProfiles[0].obfsPassword)

        val ssh = ConfigUriParser.parseSingle("ssh://ubuntu:sshpass@203.0.113.5:2222#MySSH")
        assertNotNull(ssh)
        assertEquals(ProxyProtocol.SSH, ssh!!.protocol)
        assertEquals("ubuntu", ssh.username)
        assertEquals("sshpass", ssh.passwordOrUuid)
        assertEquals(2222, ssh.port)

        val anytls = ConfigUriParser.parseSingle("anytls://anypass@any.example.com:443?sni=www.cloudflare.com&fp=chrome#MyAnyTLS")
        assertNotNull(anytls)
        assertEquals(ProxyProtocol.ANYTLS, anytls!!.protocol)
        assertEquals("anypass", anytls.passwordOrUuid)
        assertEquals("www.cloudflare.com", anytls.sni)
    }

    @Test
    fun `singbox config builder generates hy2 tuic wireguard ssh anytls urltest and h2mux`() {
        val baseSettings = com.example.data.UserSettings(
            vpnMode = com.example.model.VpnMode.PROXY_CHAIN,
            multiplexEnabled = true
        )

        // 1. Hysteria2
        val hy2 = ConfigUriParser.parseSingle(
            "hy2://secret123@hy.example.com:8443?sni=edge.example.com&obfs=salamander&obfs-password=obfspass&upmbps=40&downmbps=80#FastHY2"
        )!!
        val hy2Obj = org.json.JSONObject(
            com.example.vpn.SingBoxConfigBuilder.buildConfig(baseSettings, hy2, 0)
        ).getJSONArray("outbounds").getJSONObject(0)
        assertEquals("hysteria2", hy2Obj.getString("type"))
        assertEquals(40, hy2Obj.getInt("up_mbps"))
        assertEquals("salamander", hy2Obj.getJSONObject("obfs").getString("type"))

        // 2. TUIC
        val tuic = ConfigUriParser.parseSingle(
            "tuic://11111111-1111-1111-1111-111111111111:tuicpass@tuic.example.com:443?sni=cdn.example.com#MyTUIC"
        )!!
        val tuicObj = org.json.JSONObject(
            com.example.vpn.SingBoxConfigBuilder.buildConfig(baseSettings, tuic, 0)
        ).getJSONArray("outbounds").getJSONObject(0)
        assertEquals("tuic", tuicObj.getString("type"))
        assertTrue(tuicObj.getBoolean("zero_rtt_handshake"))

        // 3. WireGuard endpoint
        val wg = ConfigUriParser.parseSingle(
            "wireguard://privKey@162.159.192.1:2408?publickey=pubKey&reserved=10,20,30#Warp"
        )!!
        val wgRoot = org.json.JSONObject(
            com.example.vpn.SingBoxConfigBuilder.buildConfig(baseSettings, wg, 0)
        )
        val wgEp = wgRoot.getJSONArray("endpoints").getJSONObject(0)
        assertEquals("wireguard", wgEp.getString("type"))
        assertEquals("proxy", wgEp.getString("tag"))
        assertEquals(3, wgEp.getJSONArray("peers").getJSONObject(0).getJSONArray("reserved").length())

        // 4. SSH
        val ssh = ConfigUriParser.parseSingle("ssh://root:pw@203.0.113.5:22#SshTunnel")!!
        val sshObj = org.json.JSONObject(
            com.example.vpn.SingBoxConfigBuilder.buildConfig(baseSettings, ssh, 0)
        ).getJSONArray("outbounds").getJSONObject(0)
        assertEquals("ssh", sshObj.getString("type"))
        assertEquals("root", sshObj.getString("user"))

        // 5. AnyTLS + H2Mux on VLESS WS
        val anytls = ConfigUriParser.parseSingle("anytls://anypass@any.example.com:443?sni=any.example.com#Any")!!
        val anyObj = org.json.JSONObject(
            com.example.vpn.SingBoxConfigBuilder.buildConfig(baseSettings, anytls, 0)
        ).getJSONArray("outbounds").getJSONObject(0)
        assertEquals("anytls", anyObj.getString("type"))
        assertEquals(5, anyObj.getInt("min_idle_session"))

        val vlessWs = ConfigUriParser.parseSingle(
            "vless://44444444-4444-4444-4444-444444444444@cdn.example.com:443?security=tls&sni=cdn.example.com&type=ws#MuxVless"
        )!!
        val muxObj = org.json.JSONObject(
            com.example.vpn.SingBoxConfigBuilder.buildConfig(baseSettings, vlessWs, 0)
        ).getJSONArray("outbounds").getJSONObject(0).getJSONObject("multiplex")
        assertTrue(muxObj.getBoolean("enabled"))
        assertEquals("h2mux", muxObj.getString("protocol"))
        assertTrue(muxObj.getBoolean("padding"))

        // 6. AUTO_BEST_SERVER (urltest pool)
        val autoSettings = baseSettings.copy(vpnMode = com.example.model.VpnMode.AUTO_BEST_SERVER)
        val autoRoot = org.json.JSONObject(
            com.example.vpn.SingBoxConfigBuilder.buildConfig(
                settings = autoSettings,
                profile = null,
                localSocksAntiDpiPort = 0,
                allProfiles = listOf(hy2, tuic, ssh)
            )
        )
        assertEquals("proxy", autoRoot.getJSONObject("route").getString("final"))
        val autoOutbounds = autoRoot.getJSONArray("outbounds")
        val urltestObj = (0 until autoOutbounds.length())
            .map { autoOutbounds.getJSONObject(it) }
            .first { it.getString("type") == "urltest" }
        assertEquals("proxy", urltestObj.getString("tag"))
        assertEquals(3, urltestObj.getJSONArray("outbounds").length())
        assertEquals("https://www.instagram.com/", urltestObj.getString("url"))
    }

    @Test
    fun `warp provisioner matches RFC 7748 x25519 vector and parses registration json`() {
        // RFC 7748 Section 6.1 Alice private key & expected X25519(a, 9) public key
        val alicePrivHex = "77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a"
        val expectedPubHex = "8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a"
        val alicePriv = alicePrivHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val basePoint = ByteArray(32).apply { this[0] = 9 }
        val actualPub = com.example.data.WarpProvisioner.x25519(alicePriv, basePoint)
        val actualPubHex = actualPub.joinToString("") { "%02x".format(it) }
        assertEquals(expectedPubHex, actualPubHex)

        val keyPair = com.example.data.WarpProvisioner.generateWireGuardKeyPair()
        assertEquals(32, Base64.decode(keyPair.privateKeyBase64, Base64.DEFAULT).size)
        assertEquals(32, Base64.decode(keyPair.publicKeyBase64, Base64.DEFAULT).size)

        // client_id base64 for bytes [10, 20, 30] -> "ChQe"
        val clientIdB64 = Base64.encodeToString(byteArrayOf(10, 20, 30), Base64.NO_WRAP)
        val sampleJson = """
            {
              "config": {
                "client_id": "$clientIdB64",
                "interface": { "addresses": { "v4": "172.16.0.2", "v6": "2606:4700:110::2" } },
                "peers": [{ "public_key": "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=" }]
              }
            }
        """.trimIndent()
        val warpProfile = com.example.data.WarpProvisioner.parseWarpRegistrationJson(
            jsonText = sampleJson,
            privateKeyBase64 = keyPair.privateKeyBase64,
            endpointHost = "162.159.192.1",
            endpointPort = 2408
        )
        assertNotNull(warpProfile)
        assertEquals(ProxyProtocol.WIREGUARD, warpProfile!!.protocol)
        assertTrue(warpProfile.extraParam.contains("reserved=10,20,30"))
        assertTrue(warpProfile.extraParam.contains("address=172.16.0.2/32"))
    }

    @Test
    fun `parse and build hysteria1 shadowtls naive mport and double warp chain`() {
        val hy1 = ConfigUriParser.parseSingle(
            "hysteria://hy1.example.com:443?auth=hy1pass&upmbps=30&downmbps=60&obfs=xplus&obfsParam=obfskey&peer=cdn.example.com#HY1"
        )
        assertNotNull(hy1)
        assertEquals(ProxyProtocol.HYSTERIA, hy1!!.protocol)

        val ssShadowTls = ConfigUriParser.parseSingle(
            "ss://YWVzLTI1Ni1nY206c3NwYXNz@stls.example.com:443?plugin=shadow-tls;host=www.bing.com;passwd=stlssecret#SS_STLS"
        )
        assertNotNull(ssShadowTls)
        assertEquals("shadowtls", ssShadowTls!!.obfsType)
        assertEquals("stlssecret", ssShadowTls.obfsPassword)

        val naive = ConfigUriParser.parseSingle(
            "naive+quic://nuser:npass@naive.example.com:443?sni=naive.example.com#NaiveQuic"
        )
        assertNotNull(naive)
        assertEquals(ProxyProtocol.NAIVE, naive!!.protocol)

        val hy2Mport = ConfigUriParser.parseSingle(
            "hy2://p@hy2.example.com:443?mport=20000-30000&sni=hy2.example.com#HoppingHY2"
        )
        assertNotNull(hy2Mport)

        val settings = com.example.data.UserSettings(vpnMode = com.example.model.VpnMode.PROXY_CHAIN)

        val hy1Obj = org.json.JSONObject(
            com.example.vpn.SingBoxConfigBuilder.buildConfig(settings, hy1, 0)
        ).getJSONArray("outbounds").getJSONObject(0)
        assertEquals("hysteria", hy1Obj.getString("type"))
        assertEquals("hy1pass", hy1Obj.getString("auth_str"))

        val ssRoot = org.json.JSONObject(
            com.example.vpn.SingBoxConfigBuilder.buildConfig(settings, ssShadowTls, 0)
        )
        val ssOutbounds = ssRoot.getJSONArray("outbounds")
        val ssObj = (0 until ssOutbounds.length()).map { ssOutbounds.getJSONObject(it) }
            .first { it.getString("type") == "shadowsocks" }
        val stlsObj = (0 until ssOutbounds.length()).map { ssOutbounds.getJSONObject(it) }
            .first { it.getString("type") == "shadowtls" }
        assertEquals(stlsObj.getString("tag"), ssObj.getString("detour"))
        assertEquals(3, stlsObj.getInt("version"))

        val naiveObj = org.json.JSONObject(
            com.example.vpn.SingBoxConfigBuilder.buildConfig(settings, naive, 0)
        ).getJSONArray("outbounds").getJSONObject(0)
        assertEquals("naive", naiveObj.getString("type"))
        assertTrue(naiveObj.getBoolean("quic"))

        val mportObj = org.json.JSONObject(
            com.example.vpn.SingBoxConfigBuilder.buildConfig(settings, hy2Mport, 0)
        ).getJSONArray("outbounds").getJSONObject(0)
        assertEquals("20000:30000", mportObj.getJSONArray("server_ports").getString(0))
        assertEquals("30s", mportObj.getString("hop_interval"))

        // Double WARP chain: relay server + WARP endpoint detour
        val warp = ConfigUriParser.parseSingle(
            "wireguard://privKey@162.159.192.1:2408?publickey=pubKey&reserved=1,2,3#WARP"
        )!!
        val doubleWarpSettings = settings.copy(vpnMode = com.example.model.VpnMode.DOUBLE_WARP_CHAIN)
        val hy2MportNonNull = hy2Mport!!
        val doubleRoot = org.json.JSONObject(
            com.example.vpn.SingBoxConfigBuilder.buildConfig(
                settings = doubleWarpSettings,
                profile = hy2MportNonNull,
                localSocksAntiDpiPort = 0,
                allProfiles = listOf(hy2MportNonNull, warp)
            )
        )
        val warpEp = doubleRoot.getJSONArray("endpoints").getJSONObject(0)
        assertEquals("proxy", warpEp.getString("tag"))
        assertEquals("relay-server", warpEp.getString("detour"))
        val relayOutbound = doubleRoot.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("relay-server", relayOutbound.getString("tag"))
        assertEquals("hysteria2", relayOutbound.getString("type"))
    }

    @Test
    fun `ai core engine personas portals and auto discovery defaults are valid`() {
        val defaults = com.example.data.UserSettings()
        assertEquals(com.example.model.VpnMode.AI_AUTO_DISCOVERY, defaults.vpnMode)

        assertTrue(com.example.ai.AiExpertPersona.entries.size >= 4)
        assertTrue(com.example.ai.AiCoreEngine.GLOBAL_AI_PORTALS.size >= 6)
        assertTrue(
            com.example.ai.AiCoreEngine.GLOBAL_AI_PORTALS.all {
                it.url.startsWith("https://") && it.name.isNotBlank()
            }
        )
    }

    @Test
    fun `github vpn manager workflow yaml and protocols are valid`() {
        val yaml = com.example.data.GitHubVpnManager.WORKFLOW_YAML
        assertTrue(yaml.contains("workflow_dispatch"))
        assertTrue(yaml.contains("sing-box"))
        assertTrue(yaml.contains("cloudflared"))
        assertTrue(yaml.contains("vpn-live"))
        assertEquals(3, com.example.data.GitHubVpnManager.GitHubServerProtocol.entries.size)
    }

    @Test
    fun `ai core engine formats firebase and app check errors cleanly in persian`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.ai.AiCoreEngine.ensureFirebaseInitialized(context)
        val initMsg = com.example.ai.AiCoreEngine.formatUserError(
            IllegalStateException("Default FirebaseApp is not initialized in this process com.aistudio.separeazad.rkmxqv.")
        )
        assertTrue(initMsg.contains("google-services.json"))
        val appCheckMsg = com.example.ai.AiCoreEngine.formatUserError(
            IllegalStateException("HTTP 403 App Check token is invalid")
        )
        assertTrue(appCheckMsg.contains("debug.keystore.base64"))
    }

    @Test
    fun `connect screen displays material3 connect disconnect switch and triggers toggle`() {
        var toggleCount = 0
        composeTestRule.setContent {
            SeparNetTheme {
                ConnectScreen(
                    uiState = MainUiState(
                        telemetry = VpnTelemetry(state = ConnectionState.Disconnected)
                    ),
                    onToggleConnection = { toggleCount++ },
                    onSelectMode = {},
                    onOpenProfiles = {},
                    onOpenTools = {},
                    onCheckExitIp = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("vpn_connect_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("vpn_switch_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.onNodeWithTag("vpn_master_switch")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()
        assertEquals(2, toggleCount)
    }
}



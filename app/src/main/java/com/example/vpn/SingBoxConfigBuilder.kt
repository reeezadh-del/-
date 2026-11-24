package com.example.vpn

import com.example.data.UserSettings
import com.example.model.ProxyProfile
import com.example.model.ProxyProtocol
import com.example.model.VpnMode
import org.json.JSONArray
import org.json.JSONObject

object SingBoxConfigBuilder {

    fun buildConfig(
        settings: UserSettings,
        profile: ProxyProfile?,
        localSocksAntiDpiPort: Int,
        allProfiles: List<ProxyProfile> = emptyList()
    ): String {
        val validPool = allProfiles.filter { it.protocol.supportsDirectTunnel }.take(25)
        val useAutoBest = settings.vpnMode == VpnMode.AUTO_BEST_SERVER && validPool.isNotEmpty()
        val useDoubleWarp = settings.vpnMode == VpnMode.DOUBLE_WARP_CHAIN && allProfiles.isNotEmpty()
        val useRemoteProxy = (settings.vpnMode == VpnMode.PROXY_CHAIN && profile != null) ||
            useAutoBest ||
            useDoubleWarp
        val useLocalAntiDpiSocks = settings.vpnMode == VpnMode.DPI_FRAGMENT_DOH && localSocksAntiDpiPort > 0
        val mainOutboundTag = when {
            useRemoteProxy -> "proxy"
            useLocalAntiDpiSocks -> "anti-dpi-socks"
            else -> "direct"
        }

        val root = JSONObject()

        // 1. Log
        root.put("log", JSONObject().apply {
            put("disabled", false)
            put("level", "info")
            put("timestamp", true)
        })

        // 2. DNS
        val dohUrl = settings.resolveActiveDnsUrl()
        val dnsDetour = when {
            useRemoteProxy -> "proxy"
            useLocalAntiDpiSocks -> "anti-dpi-socks"
            else -> "direct"
        }
        val dnsServers = JSONArray().apply {
            put(JSONObject().apply {
                put("tag", "dns-remote")
                put("address", dohUrl)
                put("address_resolver", "dns-local")
                put("detour", dnsDetour)
            })
            // Local Android physical network DNS (avoids Iran plain UDP 53 1.1.1.1 hijacking)
            put(JSONObject().apply {
                put("tag", "dns-local")
                put("address", "local")
                put("detour", "direct")
            })
        }
        val dnsRules = JSONArray().apply {
            put(JSONObject().apply {
                put("outbound", JSONArray().put("any"))
                put("server", "dns-local")
            })
            put(JSONObject().apply {
                put("domain_suffix", JSONArray().put(".ir"))
                put("server", "dns-local")
            })
        }
        root.put("dns", JSONObject().apply {
            put("servers", dnsServers)
            put("rules", dnsRules)
            put("final", "dns-remote")
            put("strategy", "ipv4_only")
        })

        // 3. Inbounds (Full-device dual-stack TUN + internal mixed proxy for Instagram/exit-IP verification)
        val inbounds = JSONArray().apply {
            put(JSONObject().apply {
                put("type", "tun")
                put("tag", "tun-in")
                put("interface_name", "tun0")
                put("address", JSONArray().apply {
                    put("172.19.0.1/30")
                    put("fdfe:dcba:9876::1/126")
                })
                put("mtu", 1500)
                put("auto_route", true)
                put("strict_route", true)
                put("stack", "gvisor")
            })
            put(JSONObject().apply {
                put("type", "mixed")
                put("tag", "mixed-in")
                put("listen", "127.0.0.1")
                put("listen_port", 10808)
            })
        }
        root.put("inbounds", inbounds)

        // 4. Outbounds & Endpoints
        val outbounds = JSONArray()
        val endpoints = JSONArray()

        if (useAutoBest) {
            val poolTags = JSONArray()
            validPool.forEachIndexed { idx, p ->
                val tag = "node-$idx"
                poolTags.put(tag)
                outbounds.put(buildProxyOutbound(p, settings, tag = tag, extraOutbounds = outbounds))
            }
            outbounds.put(JSONObject().apply {
                put("type", "urltest")
                put("tag", "proxy")
                put("outbounds", poolTags)
                put("url", "https://www.instagram.com/")
                put("interval", "3m")
                put("tolerance", 80)
                put("interrupt_exist_connections", false)
            })
        } else if (useDoubleWarp) {
            val warpProfile = allProfiles.firstOrNull { it.protocol == ProxyProtocol.WIREGUARD }
            val relayProfile = (profile?.takeIf { it.protocol != ProxyProtocol.WIREGUARD })
                ?: allProfiles.firstOrNull { it.protocol != ProxyProtocol.WIREGUARD }
                ?: allProfiles.lastOrNull { it != warpProfile }

            if (warpProfile != null && relayProfile != null && warpProfile != relayProfile) {
                if (relayProfile.protocol == ProxyProtocol.WIREGUARD) {
                    endpoints.put(buildWireGuardEndpoint(relayProfile, tag = "relay-server"))
                } else {
                    outbounds.put(buildProxyOutbound(relayProfile, settings, tag = "relay-server", extraOutbounds = outbounds))
                }
                endpoints.put(buildWireGuardEndpoint(warpProfile, tag = "proxy", detourTag = "relay-server"))
            } else if (warpProfile != null) {
                endpoints.put(buildWireGuardEndpoint(warpProfile, tag = "proxy"))
            } else if (relayProfile != null) {
                outbounds.put(buildProxyOutbound(relayProfile, settings, tag = "proxy", extraOutbounds = outbounds))
            }
        } else if (useRemoteProxy && profile != null) {
            if (profile.protocol == ProxyProtocol.WIREGUARD) {
                endpoints.put(buildWireGuardEndpoint(profile, tag = "proxy"))
            } else {
                outbounds.put(buildProxyOutbound(profile, settings, tag = "proxy", extraOutbounds = outbounds))
            }
        }

        if (useLocalAntiDpiSocks) {
            outbounds.put(JSONObject().apply {
                put("type", "socks")
                put("tag", "anti-dpi-socks")
                put("server", "127.0.0.1")
                put("server_port", localSocksAntiDpiPort)
                put("version", "5")
            })
        }
        outbounds.put(JSONObject().apply {
            put("type", "direct")
            put("tag", "direct")
        })
        outbounds.put(JSONObject().apply {
            put("type", "block")
            put("tag", "block")
        })

        if (endpoints.length() > 0) {
            root.put("endpoints", endpoints)
        }
        root.put("outbounds", outbounds)

        // 5. Route (sing-box 1.12 rule actions)
        val routeRules = JSONArray().apply {
            put(JSONObject().apply {
                put("action", "sniff")
            })
            put(JSONObject().apply {
                put("protocol", "dns")
                put("action", "hijack-dns")
            })
            put(JSONObject().apply {
                put("ip_is_private", true)
                put("outbound", "direct")
            })
            if (useLocalAntiDpiSocks) {
                // Force browsers off QUIC/HTTP3 (UDP 443) so they fall back to TCP TLS where fragmentation works
                put(JSONObject().apply {
                    put("protocol", "quic")
                    put("action", "reject")
                })
                // Local anti-DPI SOCKS only handles TCP; route non-DNS UDP directly
                put(JSONObject().apply {
                    put("network", "udp")
                    put("outbound", "direct")
                })
            }
            if (useRemoteProxy) {
                put(JSONObject().apply {
                    put("domain_suffix", JSONArray().put(".ir"))
                    put("outbound", "direct")
                })
            }
        }
        root.put("route", JSONObject().apply {
            put("rules", routeRules)
            put("final", mainOutboundTag)
            put("auto_detect_interface", true)
        })

        // 6. Experimental Clash API tracker (enables StatusMessage uplink/downlink traffic telemetry in libbox)
        root.put("experimental", JSONObject().apply {
            put("clash_api", JSONObject())
        })

        return root.toString()
    }

    private fun buildWireGuardEndpoint(
        profile: ProxyProfile,
        tag: String,
        detourTag: String? = null
    ): JSONObject {
        val extras = parseExtraParams(profile.extraParam)
        val localAddrs = (extras["address"] ?: "172.16.0.2/32")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val mtu = extras["mtu"]?.toIntOrNull() ?: 1280
        val reservedArr = extras["reserved"]
            ?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.takeIf { it.size == 3 }

        val peerObj = JSONObject().apply {
            put("address", profile.host)
            put("port", profile.port)
            put("public_key", profile.realityPublicKey) // Peer public key
            if (profile.obfsPassword.isNotBlank()) {
                put("pre_shared_key", profile.obfsPassword)
            }
            put("allowed_ips", JSONArray().apply {
                put("0.0.0.0/0")
                put("::/0")
            })
            if (reservedArr != null) {
                put("reserved", JSONArray(reservedArr))
            }
        }

        return JSONObject().apply {
            put("type", "wireguard")
            put("tag", tag)
            put("address", JSONArray(localAddrs))
            put("private_key", profile.passwordOrUuid)
            put("mtu", mtu)
            put("peers", JSONArray().put(peerObj))
            if (!detourTag.isNullOrBlank()) {
                put("detour", detourTag)
            }
        }
    }

    private fun buildProxyOutbound(
        profile: ProxyProfile,
        settings: UserSettings,
        tag: String = "proxy",
        extraOutbounds: JSONArray? = null
    ): JSONObject {
        val obj = JSONObject()
        obj.put("tag", tag)
        obj.put("server", profile.host)
        obj.put("server_port", profile.port)

        when (profile.protocol) {
            ProxyProtocol.VLESS -> {
                obj.put("type", "vless")
                obj.put("uuid", profile.passwordOrUuid)
                if (profile.flow.isNotBlank()) {
                    obj.put("flow", profile.flow)
                } else {
                    obj.put("packet_encoding", "xudp")
                }
                applyTlsAndTransport(obj, profile, settings)
                if (settings.multiplexEnabled && profile.flow.isBlank()) {
                    applyMultiplex(obj)
                }
            }
            ProxyProtocol.VMESS -> {
                obj.put("type", "vmess")
                obj.put("uuid", profile.passwordOrUuid)
                obj.put("security", "auto")
                obj.put("alter_id", profile.username.toIntOrNull() ?: 0)
                applyTlsAndTransport(obj, profile, settings)
                if (settings.multiplexEnabled) {
                    applyMultiplex(obj)
                }
            }
            ProxyProtocol.TROJAN -> {
                obj.put("type", "trojan")
                obj.put("password", profile.passwordOrUuid)
                applyTlsAndTransport(obj, profile, settings)
                if (settings.multiplexEnabled) {
                    applyMultiplex(obj)
                }
            }
            ProxyProtocol.SHADOWSOCKS -> {
                obj.put("type", "shadowsocks")
                val creds = profile.passwordOrUuid
                val method = creds.substringBefore(":", "aes-256-gcm")
                val pass = if (creds.contains(":")) creds.substringAfter(":") else creds
                obj.put("method", method)
                obj.put("password", pass)
                if (profile.obfsType == "shadowtls" && profile.obfsPassword.isNotBlank() && extraOutbounds != null) {
                    val detourTag = "shadowtls-detour-$tag"
                    extraOutbounds.put(JSONObject().apply {
                        put("type", "shadowtls")
                        put("tag", detourTag)
                        put("server", profile.host)
                        put("server_port", profile.port)
                        put("version", 3)
                        put("password", profile.obfsPassword)
                        put("tls", buildSimpleTls(profile, defaultAlpn = emptyList(), allowFragment = false))
                    })
                    obj.put("detour", detourTag)
                }
                if (settings.multiplexEnabled) {
                    applyMultiplex(obj)
                }
            }
            ProxyProtocol.HYSTERIA2 -> {
                obj.put("type", "hysteria2")
                obj.put("password", profile.passwordOrUuid)
                val extras = parseExtraParams(profile.extraParam)
                extras["up"]?.toIntOrNull()?.let { obj.put("up_mbps", it) }
                extras["down"]?.toIntOrNull()?.let { obj.put("down_mbps", it) }
                val mport = extras["mport"]
                if (!mport.isNullOrBlank()) {
                    val ranges = mport.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    if (ranges.isNotEmpty()) {
                        obj.remove("server_port")
                        obj.put("server_ports", JSONArray(ranges))
                        obj.put("hop_interval", "30s")
                    }
                }
                if (profile.obfsType.isNotBlank() && profile.obfsPassword.isNotBlank()) {
                    obj.put("obfs", JSONObject().apply {
                        put("type", profile.obfsType)
                        put("password", profile.obfsPassword)
                    })
                }
                obj.put("tls", buildSimpleTls(profile, defaultAlpn = listOf("h3"), allowFragment = false))
            }
            ProxyProtocol.HYSTERIA -> {
                obj.put("type", "hysteria")
                val extras = parseExtraParams(profile.extraParam)
                obj.put("up_mbps", extras["up"]?.toIntOrNull() ?: 50)
                obj.put("down_mbps", extras["down"]?.toIntOrNull() ?: 100)
                if (profile.passwordOrUuid.isNotBlank()) {
                    obj.put("auth_str", profile.passwordOrUuid)
                }
                if (profile.obfsPassword.isNotBlank()) {
                    obj.put("obfs", profile.obfsPassword)
                }
                obj.put("disable_mtu_discovery", true)
                obj.put("tls", buildSimpleTls(profile, defaultAlpn = listOf("h3"), allowFragment = false))
            }
            ProxyProtocol.TUIC -> {
                obj.put("type", "tuic")
                obj.put("uuid", profile.username)
                obj.put("password", profile.passwordOrUuid)
                val extras = parseExtraParams(profile.extraParam)
                obj.put("congestion_control", extras["cc"]?.ifBlank { "bbr" } ?: "bbr")
                obj.put("udp_relay_mode", extras["udp_mode"]?.ifBlank { "native" } ?: "native")
                obj.put("zero_rtt_handshake", true)
                obj.put("tls", buildSimpleTls(profile, defaultAlpn = listOf("h3"), allowFragment = false))
            }
            ProxyProtocol.WIREGUARD -> {
                val extras = parseExtraParams(profile.extraParam)
                val localAddrs = (extras["address"] ?: "172.16.0.2/32")
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                obj.put("type", "wireguard")
                obj.put("local_address", JSONArray(localAddrs))
                obj.put("private_key", profile.passwordOrUuid)
                obj.put("peer_public_key", profile.realityPublicKey)
                if (profile.obfsPassword.isNotBlank()) {
                    obj.put("pre_shared_key", profile.obfsPassword)
                }
                extras["mtu"]?.toIntOrNull()?.let { obj.put("mtu", it) }
                extras["reserved"]
                    ?.split(",")
                    ?.mapNotNull { it.trim().toIntOrNull() }
                    ?.takeIf { it.size == 3 }
                    ?.let { obj.put("reserved", JSONArray(it)) }
            }
            ProxyProtocol.SHADOWTLS -> {
                val extras = parseExtraParams(profile.extraParam)
                obj.put("type", "shadowtls")
                obj.put("version", extras["version"]?.toIntOrNull() ?: 3)
                if (profile.passwordOrUuid.isNotBlank()) {
                    obj.put("password", profile.passwordOrUuid)
                }
                obj.put("tls", buildSimpleTls(profile, defaultAlpn = emptyList(), allowFragment = false).apply {
                    put("utls", JSONObject().apply {
                        put("enabled", true)
                        put("fingerprint", profile.fingerprint.ifBlank { "chrome" })
                    })
                })
            }
            ProxyProtocol.NAIVE -> {
                val extras = parseExtraParams(profile.extraParam)
                val isQuic = extras["quic"] == "true"
                obj.put("type", "naive")
                if (profile.username.isNotBlank()) obj.put("username", profile.username)
                if (profile.passwordOrUuid.isNotBlank()) obj.put("password", profile.passwordOrUuid)
                if (isQuic) {
                    obj.put("quic", true)
                    obj.put("quic_congestion_control", "bbr")
                }
                obj.put("tls", buildSimpleTls(profile, defaultAlpn = emptyList(), allowFragment = false))
            }
            ProxyProtocol.SSH -> {
                obj.put("type", "ssh")
                obj.put("user", profile.username.ifBlank { "root" })
                if (profile.passwordOrUuid.trim().startsWith("-----BEGIN")) {
                    obj.put("private_key", profile.passwordOrUuid.trim())
                } else {
                    obj.put("password", profile.passwordOrUuid)
                }
            }
            ProxyProtocol.ANYTLS -> {
                obj.put("type", "anytls")
                obj.put("password", profile.passwordOrUuid)
                obj.put("idle_session_check_interval", "30s")
                obj.put("idle_session_timeout", "30s")
                obj.put("min_idle_session", 5)
                obj.put("tls", buildSimpleTls(profile, defaultAlpn = emptyList(), allowFragment = settings.tlsFragmentEnabled))
            }
            ProxyProtocol.SOCKS5 -> {
                obj.put("type", "socks")
                obj.put("version", "5")
                if (profile.username.isNotBlank()) obj.put("username", profile.username)
                if (profile.passwordOrUuid.isNotBlank()) obj.put("password", profile.passwordOrUuid)
            }
            ProxyProtocol.HTTP -> {
                obj.put("type", "http")
                if (profile.username.isNotBlank()) obj.put("username", profile.username)
                if (profile.passwordOrUuid.isNotBlank()) obj.put("password", profile.passwordOrUuid)
            }
        }
        return obj
    }

    private fun applyMultiplex(obj: JSONObject) {
        obj.put("multiplex", JSONObject().apply {
            put("enabled", true)
            put("protocol", "h2mux")
            put("max_connections", 4)
            put("min_streams", 4)
            put("padding", true)
        })
    }

    private fun buildSimpleTls(
        profile: ProxyProfile,
        defaultAlpn: List<String>,
        allowFragment: Boolean
    ): JSONObject = JSONObject().apply {
        put("enabled", true)
        val serverName = profile.sni.ifBlank { profile.host }
        if (serverName.isNotBlank()) put("server_name", serverName)
        if (profile.allowInsecure) put("insecure", true)
        val alpnItems = if (profile.alpn.isNotBlank()) {
            profile.alpn.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            defaultAlpn
        }
        if (alpnItems.isNotEmpty()) {
            put("alpn", JSONArray(alpnItems))
        }
        if (allowFragment) {
            put("fragment", true)
            put("fragment_fallback_delay", "250ms")
        }
    }

    private fun parseExtraParams(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.split(";")
            .mapNotNull { pair ->
                val idx = pair.indexOf('=')
                if (idx > 0) pair.substring(0, idx).trim() to pair.substring(idx + 1).trim() else null
            }
            .toMap()
    }

    private fun applyTlsAndTransport(obj: JSONObject, profile: ProxyProfile, settings: UserSettings) {
        val sec = profile.security.lowercase()
        if (sec == "tls" || sec == "reality") {
            val tls = JSONObject().apply {
                put("enabled", true)
                val serverName = profile.sni.ifBlank { profile.host }
                if (serverName.isNotBlank()) put("server_name", serverName)
                if (profile.allowInsecure) put("insecure", true)
                if (profile.alpn.isNotBlank()) {
                    val alpnArr = JSONArray()
                    profile.alpn.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                        alpnArr.put(it)
                    }
                    if (alpnArr.length() > 0) put("alpn", alpnArr)
                }
                val fp = profile.fingerprint.ifBlank { "chrome" }
                put("utls", JSONObject().apply {
                    put("enabled", true)
                    put("fingerprint", fp)
                })
                if (sec == "reality" && profile.realityPublicKey.isNotBlank()) {
                    put("reality", JSONObject().apply {
                        put("enabled", true)
                        put("public_key", profile.realityPublicKey)
                        if (profile.realityShortId.isNotBlank()) {
                            put("short_id", profile.realityShortId)
                        }
                    })
                }
                if (settings.tlsFragmentEnabled && sec == "tls") {
                    put("fragment", true)
                    put("fragment_fallback_delay", "${settings.fragmentDelayMs.coerceAtLeast(100)}ms")
                }
            }
            obj.put("tls", tls)
        }

        val net = profile.transport.lowercase()
        when (net) {
            "ws" -> {
                obj.put("transport", JSONObject().apply {
                    put("type", "ws")
                    put("path", profile.path.ifBlank { "/" })
                    val hostHeader = profile.hostHeader.ifBlank { profile.sni }
                    if (hostHeader.isNotBlank()) {
                        put("headers", JSONObject().put("Host", hostHeader))
                    }
                })
            }
            "grpc" -> {
                obj.put("transport", JSONObject().apply {
                    put("type", "grpc")
                    put("service_name", profile.path.ifBlank { "GunService" })
                })
            }
            "httpupgrade" -> {
                obj.put("transport", JSONObject().apply {
                    put("type", "httpupgrade")
                    put("path", profile.path.ifBlank { "/" })
                    val hostHeader = profile.hostHeader.ifBlank { profile.sni }
                    if (hostHeader.isNotBlank()) put("host", hostHeader)
                })
            }
            "http", "h2" -> {
                obj.put("transport", JSONObject().apply {
                    put("type", "http")
                    put("path", profile.path.ifBlank { "/" })
                    val hostHeader = profile.hostHeader.ifBlank { profile.sni }
                    if (hostHeader.isNotBlank()) {
                        put("host", JSONArray().put(hostHeader))
                    }
                })
            }
        }
    }
}

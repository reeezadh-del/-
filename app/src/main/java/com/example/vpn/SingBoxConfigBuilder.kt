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
        localSocksAntiDpiPort: Int
    ): String {
        val useRemoteProxy = settings.vpnMode == VpnMode.PROXY_CHAIN && profile != null
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
        val bootstrapIp = settings.resolveBootstrapIp()
        val dnsServers = JSONArray().apply {
            // Remote encrypted DoH routed through the main outbound (proxy or direct)
            put(JSONObject().apply {
                put("tag", "dns-remote")
                put("address", dohUrl)
                put("address_resolver", "dns-bootstrap")
                put("detour", if (useRemoteProxy) "proxy" else "direct")
            })
            // Bootstrap/direct DNS for resolving proxy server hostname & .ir domains
            put(JSONObject().apply {
                put("tag", "dns-bootstrap")
                put("address", bootstrapIp)
                put("detour", "direct")
            })
        }
        val dnsRules = JSONArray().apply {
            put(JSONObject().apply {
                put("outbound", JSONArray().put("any"))
                put("server", "dns-bootstrap")
            })
            put(JSONObject().apply {
                put("domain_suffix", JSONArray().put(".ir"))
                put("server", "dns-bootstrap")
            })
        }
        root.put("dns", JSONObject().apply {
            put("servers", dnsServers)
            put("rules", dnsRules)
            put("final", "dns-remote")
            put("strategy", "ipv4_only")
        })

        // 3. Inbounds (Full-device TUN)
        val inbounds = JSONArray().apply {
            put(JSONObject().apply {
                put("type", "tun")
                put("tag", "tun-in")
                put("interface_name", "tun0")
                put("address", JSONArray().put("172.19.0.1/30"))
                put("mtu", 1500)
                put("auto_route", true)
                put("strict_route", true)
                put("stack", "gvisor")
                put("sniff", true)
                put("sniff_override_destination", true)
            })
            put(JSONObject().apply {
                put("type", "mixed")
                put("tag", "mixed-in")
                put("listen", "127.0.0.1")
                put("listen_port", 10808)
            })
        }
        root.put("inbounds", inbounds)

        // 4. Outbounds
        val outbounds = JSONArray()
        if (useRemoteProxy && profile != null) {
            outbounds.put(buildProxyOutbound(profile, settings))
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
        root.put("outbounds", outbounds)

        // 5. Route
        val routeRules = JSONArray().apply {
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
                // Keep .ir domains direct when using remote proxy unless user wants full proxy
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

        return root.toString()
    }

    private fun buildProxyOutbound(profile: ProxyProfile, settings: UserSettings): JSONObject {
        val obj = JSONObject()
        obj.put("tag", "proxy")
        obj.put("server", profile.host)
        obj.put("server_port", profile.port)

        when (profile.protocol) {
            ProxyProtocol.VLESS -> {
                obj.put("type", "vless")
                obj.put("uuid", profile.passwordOrUuid)
                if (profile.flow.isNotBlank()) {
                    obj.put("flow", profile.flow)
                }
                applyTlsAndTransport(obj, profile, settings)
            }
            ProxyProtocol.VMESS -> {
                obj.put("type", "vmess")
                obj.put("uuid", profile.passwordOrUuid)
                obj.put("security", "auto")
                obj.put("alter_id", profile.username.toIntOrNull() ?: 0)
                applyTlsAndTransport(obj, profile, settings)
            }
            ProxyProtocol.TROJAN -> {
                obj.put("type", "trojan")
                obj.put("password", profile.passwordOrUuid)
                applyTlsAndTransport(obj, profile, settings)
            }
            ProxyProtocol.SHADOWSOCKS -> {
                obj.put("type", "shadowsocks")
                val creds = profile.passwordOrUuid
                val method = creds.substringBefore(":", "aes-256-gcm")
                val pass = if (creds.contains(":")) creds.substringAfter(":") else creds
                obj.put("method", method)
                obj.put("password", pass)
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
                if (profile.fingerprint.isNotBlank()) {
                    put("utls", JSONObject().apply {
                        put("enabled", true)
                        put("fingerprint", profile.fingerprint)
                    })
                }
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
        }
    }
}

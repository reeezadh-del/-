package com.example.data

import android.net.Uri
import android.util.Base64
import com.example.model.ProxyProfile
import com.example.model.ProxyProtocol
import org.json.JSONObject
import java.net.URLDecoder

object ConfigUriParser {

    fun parseBulk(rawText: String, subscriptionSource: String = ""): List<ProxyProfile> {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return emptyList()

        // Check for WireGuard INI-style .conf block ([Interface] / [Peer])
        if (trimmed.contains("[Interface]", ignoreCase = true) &&
            trimmed.contains("[Peer]", ignoreCase = true)
        ) {
            parseWireGuardConf(trimmed, subscriptionSource)?.let { return listOf(it) }
        }

        val expandedText = if (!trimmed.contains("://")) {
            decodeBase64Safe(trimmed) ?: trimmed
        } else {
            trimmed
        }

        return expandedText
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line -> parseSingle(line, subscriptionSource) }
            .toList()
    }

    fun parseSingle(rawUri: String, subscriptionSource: String = ""): ProxyProfile? {
        val line = rawUri.trim()
        return try {
            when {
                line.startsWith("vless://", ignoreCase = true) -> parseVless(line, subscriptionSource)
                line.startsWith("vmess://", ignoreCase = true) -> parseVmess(line, subscriptionSource)
                line.startsWith("trojan://", ignoreCase = true) -> parseTrojan(line, subscriptionSource)
                line.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(line, subscriptionSource)
                line.startsWith("hysteria2://", ignoreCase = true) ||
                    line.startsWith("hy2://", ignoreCase = true) -> parseHysteria2(line, subscriptionSource)
                line.startsWith("hysteria://", ignoreCase = true) -> parseHysteria1(line, subscriptionSource)
                line.startsWith("tuic://", ignoreCase = true) -> parseTuic(line, subscriptionSource)
                line.startsWith("wireguard://", ignoreCase = true) ||
                    line.startsWith("wg://", ignoreCase = true) -> parseWireGuardUri(line, subscriptionSource)
                line.startsWith("shadowtls://", ignoreCase = true) -> parseShadowTls(line, subscriptionSource)
                line.startsWith("naive+https://", ignoreCase = true) ||
                    line.startsWith("naive+quic://", ignoreCase = true) ||
                    line.startsWith("naive://", ignoreCase = true) -> parseNaive(line, subscriptionSource)
                line.startsWith("ssh://", ignoreCase = true) -> parseSsh(line, subscriptionSource)
                line.startsWith("anytls://", ignoreCase = true) -> parseAnyTls(line, subscriptionSource)
                line.startsWith("socks5://", ignoreCase = true) ||
                    line.startsWith("socks://", ignoreCase = true) -> parseSocksOrHttp(line, ProxyProtocol.SOCKS5, subscriptionSource)
                line.startsWith("http://", ignoreCase = true) -> parseSocksOrHttp(line, ProxyProtocol.HTTP, subscriptionSource)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseVless(rawUri: String, sub: String): ProxyProfile? {
        val uri = Uri.parse(rawUri)
        val host = uri.host ?: return null
        val port = uri.port.takeIf { it > 0 } ?: 443
        val uuid = uri.userInfo.orEmpty()
        val sni = uri.getQueryParameter("sni").orEmpty()
        val security = uri.getQueryParameter("security").orEmpty().ifBlank { "none" }
        val transport = uri.getQueryParameter("type").orEmpty().ifBlank { "tcp" }
        val path = uri.getQueryParameter("path").orEmpty()
            .ifBlank { uri.getQueryParameter("serviceName").orEmpty() }
        val hostHeader = uri.getQueryParameter("host").orEmpty()
        val flow = uri.getQueryParameter("flow").orEmpty()
        val fp = uri.getQueryParameter("fp").orEmpty().ifBlank { "chrome" }
        val pbk = uri.getQueryParameter("pbk").orEmpty()
        val sid = uri.getQueryParameter("sid").orEmpty()
        val alpn = uri.getQueryParameter("alpn").orEmpty()
        val allowInsecure = uri.getQueryParameter("allowInsecure") == "1" ||
            uri.getQueryParameter("insecure") == "1"
        val remark = decodeFragment(uri.fragment).ifBlank { "$host:$port" }

        return ProxyProfile(
            name = remark,
            protocol = ProxyProtocol.VLESS,
            host = host,
            port = port,
            passwordOrUuid = uuid,
            sni = sni,
            transport = transport,
            security = security,
            path = path,
            hostHeader = hostHeader,
            flow = flow,
            fingerprint = fp,
            realityPublicKey = pbk,
            realityShortId = sid,
            alpn = alpn,
            allowInsecure = allowInsecure,
            rawUri = rawUri,
            subscriptionSource = sub
        )
    }

    private fun parseVmess(rawUri: String, sub: String): ProxyProfile? {
        val encoded = rawUri.substringAfter("://").trim()
        val decodedJson = decodeBase64Safe(encoded) ?: return null
        val json = JSONObject(decodedJson)
        val host = json.optString("add").trim()
        val port = json.optInt("port", json.optString("port").toIntOrNull() ?: 0)
        if (host.isEmpty() || port <= 0) return null

        val ps = json.optString("ps").trim().ifBlank { "$host:$port" }
        val uuid = json.optString("id").trim()
        val net = json.optString("net").trim().ifBlank { "tcp" }
        val tls = json.optString("tls").trim().ifBlank { "none" }
        val hostHeader = json.optString("host").trim()
        val sni = json.optString("sni").trim().ifBlank { hostHeader }
        val path = json.optString("path").trim()
        val fp = json.optString("fp").trim().ifBlank { "chrome" }
        val alpn = json.optString("alpn").trim()
        val alterId = json.optString("aid").trim().ifBlank { json.optInt("aid", 0).toString() }

        return ProxyProfile(
            name = ps,
            protocol = ProxyProtocol.VMESS,
            host = host,
            port = port,
            username = alterId, // store alterId for VMess
            passwordOrUuid = uuid,
            sni = sni,
            transport = net,
            security = tls,
            path = path,
            hostHeader = hostHeader,
            fingerprint = fp,
            alpn = alpn,
            rawUri = rawUri,
            subscriptionSource = sub
        )
    }

    private fun parseTrojan(rawUri: String, sub: String): ProxyProfile? {
        val uri = Uri.parse(rawUri)
        val host = uri.host ?: return null
        val port = uri.port.takeIf { it > 0 } ?: 443
        val password = uri.userInfo.orEmpty()
        val sni = uri.getQueryParameter("sni").orEmpty().ifBlank { host }
        val security = uri.getQueryParameter("security").orEmpty().ifBlank { "tls" }
        val transport = uri.getQueryParameter("type").orEmpty().ifBlank { "tcp" }
        val path = uri.getQueryParameter("path").orEmpty()
            .ifBlank { uri.getQueryParameter("serviceName").orEmpty() }
        val hostHeader = uri.getQueryParameter("host").orEmpty()
        val fp = uri.getQueryParameter("fp").orEmpty().ifBlank { "chrome" }
        val alpn = uri.getQueryParameter("alpn").orEmpty()
        val allowInsecure = uri.getQueryParameter("allowInsecure") == "1" ||
            uri.getQueryParameter("insecure") == "1"
        val remark = decodeFragment(uri.fragment).ifBlank { "$host:$port" }

        return ProxyProfile(
            name = remark,
            protocol = ProxyProtocol.TROJAN,
            host = host,
            port = port,
            passwordOrUuid = password,
            sni = sni,
            transport = transport,
            security = security,
            path = path,
            hostHeader = hostHeader,
            fingerprint = fp,
            alpn = alpn,
            allowInsecure = allowInsecure,
            rawUri = rawUri,
            subscriptionSource = sub
        )
    }

    private fun parseShadowsocks(rawUri: String, sub: String): ProxyProfile? {
        val withoutScheme = rawUri.substringAfter("ss://")
        val fragmentPart = withoutScheme.substringAfter("#", "")
        val remark = decodeFragment(fragmentPart)
        val mainPart = withoutScheme.substringBefore("#").substringBefore("?")

        val hostPort: String
        val userPass: String
        if (mainPart.contains("@")) {
            val encodedCreds = mainPart.substringBefore("@")
            userPass = decodeBase64Safe(encodedCreds) ?: encodedCreds
            hostPort = mainPart.substringAfter("@")
        } else {
            val decoded = decodeBase64Safe(mainPart) ?: return null
            if (!decoded.contains("@")) return null
            userPass = decoded.substringBefore("@")
            hostPort = decoded.substringAfter("@")
        }

        val lastColon = hostPort.lastIndexOf(':')
        if (lastColon <= 0) return null
        val host = hostPort.substring(0, lastColon).trim('[', ']')
        val port = hostPort.substring(lastColon + 1).toIntOrNull() ?: return null

        val uri = runCatching { Uri.parse(rawUri) }.getOrNull()
        val shadowTlsParam = uri?.getQueryParameter("shadow-tls").orEmpty()
        var stlsPass = ""
        var stlsSni = ""
        if (shadowTlsParam.isNotBlank()) {
            val decodedStls = decodeBase64Safe(shadowTlsParam) ?: shadowTlsParam
            runCatching {
                val stlsJson = JSONObject(decodedStls)
                stlsPass = stlsJson.optString("password").trim()
                stlsSni = stlsJson.optString("host").trim()
            }
        } else {
            val plugin = uri?.getQueryParameter("plugin").orEmpty()
            if (plugin.contains("shadow-tls", ignoreCase = true)) {
                val parts = plugin.split(";")
                parts.forEach { p ->
                    val eq = p.indexOf('=')
                    if (eq > 0) {
                        val k = p.substring(0, eq).trim().lowercase()
                        val v = p.substring(eq + 1).trim()
                        if (k == "passwd" || k == "password") stlsPass = v
                        if (k == "host" || k == "sni") stlsSni = v
                    }
                }
            }
        }

        return ProxyProfile(
            name = remark.ifBlank { "$host:$port" },
            protocol = ProxyProtocol.SHADOWSOCKS,
            host = host,
            port = port,
            passwordOrUuid = userPass,
            sni = stlsSni,
            obfsType = if (stlsPass.isNotBlank()) "shadowtls" else "",
            obfsPassword = stlsPass,
            rawUri = rawUri,
            subscriptionSource = sub
        )
    }

    private fun parseSocksOrHttp(rawUri: String, protocol: ProxyProtocol, sub: String): ProxyProfile? {
        val uri = Uri.parse(rawUri)
        val host = uri.host ?: return null
        val port = uri.port.takeIf { it > 0 } ?: if (protocol == ProxyProtocol.SOCKS5) 1080 else 8080
        val userInfo = uri.userInfo.orEmpty()
        val username = userInfo.substringBefore(":", "")
        val password = if (userInfo.contains(":")) userInfo.substringAfter(":") else ""
        val remark = decodeFragment(uri.fragment).ifBlank { "$host:$port" }

        return ProxyProfile(
            name = remark,
            protocol = protocol,
            host = host,
            port = port,
            username = username,
            passwordOrUuid = password,
            rawUri = rawUri,
            subscriptionSource = sub
        )
    }

    private fun parseHysteria2(rawUri: String, sub: String): ProxyProfile? {
        val uri = Uri.parse(rawUri)
        val host = uri.host ?: return null
        val port = uri.port.takeIf { it > 0 } ?: 443
        val password = uri.userInfo.orEmpty()
        val sni = uri.getQueryParameter("sni").orEmpty().ifBlank { host }
        val obfs = uri.getQueryParameter("obfs").orEmpty()
        val obfsPass = uri.getQueryParameter("obfs-password").orEmpty()
        val insecure = uri.getQueryParameter("insecure") == "1" ||
            uri.getQueryParameter("allowInsecure") == "1"
        val alpn = uri.getQueryParameter("alpn").orEmpty()
        val upMbps = uri.getQueryParameter("upmbps").orEmpty()
        val downMbps = uri.getQueryParameter("downmbps").orEmpty()
        val mportRaw = uri.getQueryParameter("mport").orEmpty().trim()
        val normalizedMport = if (mportRaw.isNotBlank()) {
            mportRaw.split(",").joinToString(",") { it.trim().replace("-", ":") }
        } else ""
        val extra = buildList {
            if (upMbps.isNotBlank()) add("up=$upMbps")
            if (downMbps.isNotBlank()) add("down=$downMbps")
            if (normalizedMport.isNotBlank()) add("mport=$normalizedMport")
        }.joinToString(";")
        val remark = decodeFragment(uri.fragment).ifBlank { "HY2 $host:$port" }

        return ProxyProfile(
            name = remark,
            protocol = ProxyProtocol.HYSTERIA2,
            host = host,
            port = port,
            passwordOrUuid = password,
            sni = sni,
            security = "tls",
            alpn = alpn,
            allowInsecure = insecure,
            obfsType = obfs,
            obfsPassword = obfsPass,
            extraParam = extra,
            rawUri = rawUri,
            subscriptionSource = sub
        )
    }

    private fun parseTuic(rawUri: String, sub: String): ProxyProfile? {
        val uri = Uri.parse(rawUri)
        val host = uri.host ?: return null
        val port = uri.port.takeIf { it > 0 } ?: 443
        val userInfo = uri.userInfo.orEmpty()
        val uuid = userInfo.substringBefore(":", "")
        val password = if (userInfo.contains(":")) userInfo.substringAfter(":") else ""
        val sni = uri.getQueryParameter("sni").orEmpty().ifBlank { host }
        val cc = uri.getQueryParameter("congestion_control").orEmpty().ifBlank { "bbr" }
        val udpMode = uri.getQueryParameter("udp_relay_mode").orEmpty().ifBlank { "native" }
        val alpn = uri.getQueryParameter("alpn").orEmpty().ifBlank { "h3" }
        val insecure = uri.getQueryParameter("allow_insecure") == "1" ||
            uri.getQueryParameter("insecure") == "1"
        val remark = decodeFragment(uri.fragment).ifBlank { "TUIC $host:$port" }

        return ProxyProfile(
            name = remark,
            protocol = ProxyProtocol.TUIC,
            host = host,
            port = port,
            username = uuid,
            passwordOrUuid = password,
            sni = sni,
            security = "tls",
            alpn = alpn,
            allowInsecure = insecure,
            extraParam = "cc=$cc;udp_mode=$udpMode",
            rawUri = rawUri,
            subscriptionSource = sub
        )
    }

    private fun parseWireGuardUri(rawUri: String, sub: String): ProxyProfile? {
        val uri = Uri.parse(rawUri)
        val host = uri.host ?: return null
        val port = uri.port.takeIf { it > 0 } ?: 51820
        val privateKey = uri.userInfo.orEmpty()
        val publicKey = uri.getQueryParameter("publickey").orEmpty()
            .ifBlank { uri.getQueryParameter("peer_public_key").orEmpty() }
        if (privateKey.isBlank() || publicKey.isBlank()) return null
        val psk = uri.getQueryParameter("presharedkey").orEmpty()
        val address = uri.getQueryParameter("address").orEmpty().ifBlank { "172.16.0.2/32" }
        val mtu = uri.getQueryParameter("mtu").orEmpty().ifBlank { "1280" }
        val reserved = uri.getQueryParameter("reserved").orEmpty()
        val extra = buildList {
            add("address=$address")
            add("mtu=$mtu")
            if (reserved.isNotBlank()) add("reserved=$reserved")
        }.joinToString(";")
        val remark = decodeFragment(uri.fragment).ifBlank { "WG $host:$port" }

        return ProxyProfile(
            name = remark,
            protocol = ProxyProtocol.WIREGUARD,
            host = host,
            port = port,
            passwordOrUuid = privateKey,
            realityPublicKey = publicKey,
            obfsPassword = psk,
            extraParam = extra,
            rawUri = rawUri,
            subscriptionSource = sub
        )
    }

    private fun parseWireGuardConf(conf: String, sub: String): ProxyProfile? {
        var privateKey = ""
        var address = "172.16.0.2/32"
        var mtu = "1280"
        var publicKey = ""
        var psk = ""
        var endpoint = ""
        for (rawLine in conf.lineSequence()) {
            val line = rawLine.substringBefore("#").trim()
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val key = line.substring(0, eq).trim().lowercase()
            val value = line.substring(eq + 1).trim()
            when (key) {
                "privatekey" -> privateKey = value
                "address" -> address = value
                "mtu" -> mtu = value
                "publickey" -> publicKey = value
                "presharedkey" -> psk = value
                "endpoint" -> endpoint = value
            }
        }
        val colonIdx = endpoint.lastIndexOf(':')
        if (privateKey.isBlank() || publicKey.isBlank() || colonIdx <= 0) return null
        val host = endpoint.substring(0, colonIdx).trim('[', ']')
        val port = endpoint.substring(colonIdx + 1).toIntOrNull() ?: return null

        return ProxyProfile(
            name = "WireGuard $host",
            protocol = ProxyProtocol.WIREGUARD,
            host = host,
            port = port,
            passwordOrUuid = privateKey,
            realityPublicKey = publicKey,
            obfsPassword = psk,
            extraParam = "address=$address;mtu=$mtu",
            rawUri = conf.trim(),
            subscriptionSource = sub
        )
    }

    private fun parseSsh(rawUri: String, sub: String): ProxyProfile? {
        val uri = Uri.parse(rawUri)
        val host = uri.host ?: return null
        val port = uri.port.takeIf { it > 0 } ?: 22
        val userInfo = uri.userInfo.orEmpty()
        val username = userInfo.substringBefore(":", "root").ifBlank { "root" }
        val password = if (userInfo.contains(":")) userInfo.substringAfter(":") else ""
        val remark = decodeFragment(uri.fragment).ifBlank { "SSH $host:$port" }

        return ProxyProfile(
            name = remark,
            protocol = ProxyProtocol.SSH,
            host = host,
            port = port,
            username = username,
            passwordOrUuid = password,
            rawUri = rawUri,
            subscriptionSource = sub
        )
    }

    private fun parseAnyTls(rawUri: String, sub: String): ProxyProfile? {
        val uri = Uri.parse(rawUri)
        val host = uri.host ?: return null
        val port = uri.port.takeIf { it > 0 } ?: 443
        val password = uri.userInfo.orEmpty()
        val sni = uri.getQueryParameter("sni").orEmpty().ifBlank { host }
        val fp = uri.getQueryParameter("fp").orEmpty().ifBlank { "chrome" }
        val insecure = uri.getQueryParameter("insecure") == "1" ||
            uri.getQueryParameter("allowInsecure") == "1"
        val alpn = uri.getQueryParameter("alpn").orEmpty()
        val remark = decodeFragment(uri.fragment).ifBlank { "AnyTLS $host:$port" }

        return ProxyProfile(
            name = remark,
            protocol = ProxyProtocol.ANYTLS,
            host = host,
            port = port,
            passwordOrUuid = password,
            sni = sni,
            security = "tls",
            fingerprint = fp,
            alpn = alpn,
            allowInsecure = insecure,
            rawUri = rawUri,
            subscriptionSource = sub
        )
    }

    private fun parseHysteria1(rawUri: String, sub: String): ProxyProfile? {
        val uri = Uri.parse(rawUri)
        val host = uri.host ?: return null
        val port = uri.port.takeIf { it > 0 } ?: 443
        val auth = uri.getQueryParameter("auth").orEmpty().ifBlank { uri.userInfo.orEmpty() }
        val sni = uri.getQueryParameter("peer").orEmpty()
            .ifBlank { uri.getQueryParameter("sni").orEmpty() }
            .ifBlank { host }
        val obfs = uri.getQueryParameter("obfs").orEmpty()
        val obfsParam = uri.getQueryParameter("obfsParam").orEmpty()
        val insecure = uri.getQueryParameter("insecure") == "1" ||
            uri.getQueryParameter("allowInsecure") == "1"
        val alpn = uri.getQueryParameter("alpn").orEmpty()
        val upMbps = uri.getQueryParameter("upmbps").orEmpty().ifBlank { "50" }
        val downMbps = uri.getQueryParameter("downmbps").orEmpty().ifBlank { "100" }
        val remark = decodeFragment(uri.fragment).ifBlank { "HY1 $host:$port" }

        return ProxyProfile(
            name = remark,
            protocol = ProxyProtocol.HYSTERIA,
            host = host,
            port = port,
            passwordOrUuid = auth,
            sni = sni,
            security = "tls",
            alpn = alpn,
            allowInsecure = insecure,
            obfsType = obfs,
            obfsPassword = obfsParam,
            extraParam = "up=$upMbps;down=$downMbps",
            rawUri = rawUri,
            subscriptionSource = sub
        )
    }

    private fun parseShadowTls(rawUri: String, sub: String): ProxyProfile? {
        val uri = Uri.parse(rawUri)
        val remark = decodeFragment(uri.fragment)
        if (uri.host != null) {
            val host = uri.host ?: return null
            val port = uri.port.takeIf { it > 0 } ?: 443
            val pass = uri.userInfo.orEmpty()
            val sni = uri.getQueryParameter("sni").orEmpty().ifBlank { "www.bing.com" }
            val version = uri.getQueryParameter("version").orEmpty().ifBlank { "3" }
            return ProxyProfile(
                name = remark.ifBlank { "ShadowTLS $host:$port" },
                protocol = ProxyProtocol.SHADOWTLS,
                host = host,
                port = port,
                passwordOrUuid = pass,
                sni = sni,
                security = "tls",
                extraParam = "version=$version",
                rawUri = rawUri,
                subscriptionSource = sub
            )
        }

        // Base64-encoded body variant: shadowtls://base64(password@host:port)?sni=...
        val afterScheme = rawUri.substringAfter("://").substringBefore("#")
        val mainPart = afterScheme.substringBefore("?")
        val decoded = decodeBase64Safe(mainPart) ?: return null
        val reconstructed = "shadowtls://$decoded" + if (afterScheme.contains("?")) "?${afterScheme.substringAfter("?")}" else ""
        val parsed = Uri.parse(reconstructed)
        val host = parsed.host ?: return null
        val port = parsed.port.takeIf { it > 0 } ?: 443
        val sni = parsed.getQueryParameter("sni").orEmpty().ifBlank { "www.bing.com" }
        val version = parsed.getQueryParameter("version").orEmpty().ifBlank { "3" }
        return ProxyProfile(
            name = remark.ifBlank { "ShadowTLS $host:$port" },
            protocol = ProxyProtocol.SHADOWTLS,
            host = host,
            port = port,
            passwordOrUuid = parsed.userInfo.orEmpty(),
            sni = sni,
            security = "tls",
            extraParam = "version=$version",
            rawUri = rawUri,
            subscriptionSource = sub
        )
    }

    private fun parseNaive(rawUri: String, sub: String): ProxyProfile? {
        val isQuic = rawUri.startsWith("naive+quic://", ignoreCase = true)
        val normalized = "https://" + rawUri.substringAfter("://")
        val uri = Uri.parse(normalized)
        val host = uri.host ?: return null
        val port = uri.port.takeIf { it > 0 } ?: 443
        val userInfo = uri.userInfo.orEmpty()
        val username = userInfo.substringBefore(":", "")
        val password = if (userInfo.contains(":")) userInfo.substringAfter(":") else ""
        val sni = uri.getQueryParameter("sni").orEmpty().ifBlank { host }
        val insecure = uri.getQueryParameter("insecure") == "1" ||
            uri.getQueryParameter("allowInsecure") == "1"
        val remark = decodeFragment(uri.fragment).ifBlank { "Naive $host:$port" }

        return ProxyProfile(
            name = remark,
            protocol = ProxyProtocol.NAIVE,
            host = host,
            port = port,
            username = username,
            passwordOrUuid = password,
            sni = sni,
            security = "tls",
            allowInsecure = insecure,
            extraParam = if (isQuic) "quic=true" else "quic=false",
            rawUri = rawUri,
            subscriptionSource = sub
        )
    }

    private fun decodeFragment(fragment: String?): String {
        if (fragment.isNullOrBlank()) return ""
        return try {
            URLDecoder.decode(fragment, "UTF-8")
        } catch (_: Exception) {
            fragment
        }
    }

    private fun decodeBase64Safe(input: String): String? {
        val cleaned = input.trim()
            .replace('-', '+')
            .replace('_', '/')
        val padded = when (cleaned.length % 4) {
            2 -> "$cleaned=="
            3 -> "$cleaned="
            else -> cleaned
        }
        return try {
            val bytes = Base64.decode(padded, Base64.DEFAULT)
            String(bytes, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }
}

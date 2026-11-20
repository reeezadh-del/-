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
        val sni = json.optString("sni").trim().ifBlank { json.optString("host").trim() }
        val path = json.optString("path").trim()

        return ProxyProfile(
            name = ps,
            protocol = ProxyProtocol.VMESS,
            host = host,
            port = port,
            passwordOrUuid = uuid,
            sni = sni,
            transport = net,
            security = tls,
            path = path,
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

        return ProxyProfile(
            name = remark.ifBlank { "$host:$port" },
            protocol = ProxyProtocol.SHADOWSOCKS,
            host = host,
            port = port,
            passwordOrUuid = userPass,
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

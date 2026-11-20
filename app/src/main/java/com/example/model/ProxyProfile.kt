package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ProxyProtocol(val displayName: String, val supportsDirectTunnel: Boolean) {
    SOCKS5("SOCKS5", true),
    HTTP("HTTP", true),
    VLESS("VLESS", true),
    VMESS("VMess", true),
    TROJAN("Trojan", true),
    SHADOWSOCKS("SS", true)
}

@Entity(tableName = "proxy_profiles")
data class ProxyProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val protocol: ProxyProtocol,
    val host: String,
    val port: Int,
    val username: String = "",
    val passwordOrUuid: String = "",
    val sni: String = "",
    val transport: String = "tcp",
    val security: String = "none",
    val path: String = "",
    val hostHeader: String = "",
    val flow: String = "",
    val fingerprint: String = "chrome",
    val realityPublicKey: String = "",
    val realityShortId: String = "",
    val alpn: String = "",
    val allowInsecure: Boolean = false,
    val rawUri: String,
    val subscriptionSource: String = "",
    val latencyMs: Long? = null,
    val lastTestedAt: Long = 0L,
    val isSelected: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

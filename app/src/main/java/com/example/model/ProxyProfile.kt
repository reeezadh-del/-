package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ProxyProtocol(val displayName: String, val supportsDirectTunnel: Boolean) {
    SOCKS5("SOCKS5", true),
    HTTP("HTTP", true),
    VLESS("VLESS", false),
    VMESS("VMess", false),
    TROJAN("Trojan", false),
    SHADOWSOCKS("SS", false)
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
    val rawUri: String,
    val subscriptionSource: String = "",
    val latencyMs: Long? = null,
    val lastTestedAt: Long = 0L,
    val isSelected: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

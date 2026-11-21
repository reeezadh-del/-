package com.example.model

enum class VpnMode(val titleFa: String, val subtitleFa: String) {
    AI_AUTO_DISCOVERY(
        titleFa = "هوش مصنوعی خودارتقاگر (کشف تمام روش‌ها)",
        subtitleFa = "تست خودکار تمام پروتکل‌ها، WARP تمیز، مخازن آزاد و تکه‌تکه‌سازی TLS تا برقراری اتصال"
    ),
    PROXY_CHAIN(
        titleFa = "تونل سرور انتخاب‌شده + DoH",
        subtitleFa = "اتصال از طریق سرور انتخابی (VLESS، Hysteria2، TUIC، WireGuard، SSH و…)"
    ),
    AUTO_BEST_SERVER(
        titleFa = "اتصال هوشمند خودکار (Auto Failover)",
        subtitleFa = "تست هم‌زمان همه سرورهای ذخیره‌شده و سوییچ خودکار روی سریع‌ترین سرور سالم"
    ),
    DOUBLE_WARP_CHAIN(
        titleFa = "تونل ترکیبی (سرور شما + Cloudflare WARP)",
        subtitleFa = "عبور از فیلترینگ با سرور انتخابی و تغییر IP خروج به Cloudflare WARP تمیز"
    ),
    DPI_FRAGMENT_DOH(
        titleFa = "ضد فیلتر هوشمند (TLS Fragment + DoH)",
        subtitleFa = "عبور مستقیم از فیلترینگ SNI و DNS بدون سرور خارجی (مناسب سایت‌های دارای محدودیت SNI)"
    ),
    DOH_ONLY(
        titleFa = "فقط DNS رمزنگاری‌شده (ضد مسمومیت DNS)",
        subtitleFa = "جلوگیری از تغییر مسیر DNS اپراتورها با کمترین مصرف باتری"
    )
}

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data object Disconnecting : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

data class VpnTelemetry(
    val state: ConnectionState = ConnectionState.Disconnected,
    val activeMode: VpnMode = VpnMode.AI_AUTO_DISCOVERY,
    val connectedSinceMs: Long = 0L,
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L,
    val rxSpeedBps: Long = 0L,
    val txSpeedBps: Long = 0L,
    val dnsQueriesResolved: Int = 0,
    val fragmentedConnections: Int = 0,
    val activeDnsLabel: String = "Cloudflare DoH",
    val localProxyPort: Int = 0,
    val activeServerLabel: String? = null
)

data class DnsPreset(
    val id: String,
    val nameFa: String,
    val dohUrl: String,
    val bootstrapIp: String,
    val descriptionFa: String
) {
    companion object {
        val ALL = listOf(
            DnsPreset(
                id = "cloudflare",
                nameFa = "Cloudflare (کلادفلر)",
                dohUrl = "https://1.1.1.1/dns-query",
                bootstrapIp = "1.1.1.1",
                descriptionFa = "سریع، بدون نیاز به DNS اولیه و مقاوم در برابر مسمومیت"
            ),
            DnsPreset(
                id = "google",
                nameFa = "Google DoH (گوگل)",
                dohUrl = "https://8.8.8.8/dns-query",
                bootstrapIp = "8.8.8.8",
                descriptionFa = "پایداری بالا روی اغلب اپراتورهای ایران"
            ),
            DnsPreset(
                id = "quad9",
                nameFa = "Quad9 Security",
                dohUrl = "https://9.9.9.9/dns-query",
                bootstrapIp = "9.9.9.9",
                descriptionFa = "مسدودسازی دامنه‌های مخرب و فیشینگ"
            ),
            DnsPreset(
                id = "adguard",
                nameFa = "AdGuard DNS",
                dohUrl = "https://94.140.14.14/dns-query",
                bootstrapIp = "94.140.14.14",
                descriptionFa = "حذف تبلیغات و ردیاب‌ها به صورت رمزنگاری‌شده"
            ),
            DnsPreset(
                id = "shecan",
                nameFa = "Shecan (ضد تحریم)",
                dohUrl = "https://free.shecan.ir/dns-query",
                bootstrapIp = "178.22.122.100",
                descriptionFa = "مناسب عبور از تحریم سرویس‌های برنامه‌نویسی و گیمینگ"
            )
        )

        fun findById(id: String): DnsPreset = ALL.firstOrNull { it.id == id } ?: ALL.first()
    }
}

data class InstalledAppInfo(
    val appName: String,
    val packageName: String,
    val isIranianSuggested: Boolean,
    val isBypassed: Boolean
)

data class ScanEndpointResult(
    val label: String,
    val host: String,
    val port: Int,
    val category: String,
    val tcpLatencyMs: Long? = null,
    val tlsLatencyMs: Long? = null,
    val isReachable: Boolean? = null,
    val isTesting: Boolean = false
)

data class TunnelLogEntry(
    val timestampMs: Long,
    val tag: String,
    val message: String,
    val isError: Boolean = false
)

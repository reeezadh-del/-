package com.example.data

import com.example.model.ProxyProfile
import com.example.model.ScanEndpointResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

object NetworkProbe {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    suspend fun measureTcpLatency(host: String, port: Int, timeoutMs: Int = 3500): Long =
        withContext(Dispatchers.IO) {
            val start = System.nanoTime()
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                }
                ((System.nanoTime() - start) / 1_000_000L).coerceAtLeast(1L)
            } catch (_: Exception) {
                -1L
            }
        }

    suspend fun measureTlsHandshake(
        host: String,
        port: Int,
        sni: String = host,
        timeoutMs: Int = 4500
    ): Pair<Long, Long> = withContext(Dispatchers.IO) {
        val tcpStart = System.nanoTime()
        try {
            Socket().use { rawSocket ->
                rawSocket.soTimeout = timeoutMs
                rawSocket.connect(InetSocketAddress(host, port), timeoutMs)
                val tcpMs = ((System.nanoTime() - tcpStart) / 1_000_000L).coerceAtLeast(1L)

                val tlsStart = System.nanoTime()
                val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
                (sslFactory.createSocket(rawSocket, host, port, true) as SSLSocket).use { sslSocket ->
                    sslSocket.soTimeout = timeoutMs
                    val params = sslSocket.sslParameters
                    if (sni.isNotBlank() && !isIpLiteral(sni)) {
                        params.serverNames = listOf(SNIHostName(sni))
                    }
                    sslSocket.sslParameters = params
                    sslSocket.startHandshake()
                }
                val tlsMs = ((System.nanoTime() - tlsStart) / 1_000_000L).coerceAtLeast(1L)
                tcpMs to tlsMs
            }
        } catch (_: Exception) {
            -1L to -1L
        }
    }

    suspend fun fetchSubscription(url: String): Result<List<ProxyProfile>> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url.trim())
                    .header("User-Agent", "SeparNet/1.0")
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            IllegalStateException("خطای سرور اشتراک: HTTP ${response.code}")
                        )
                    }
                    val body = response.body?.string().orEmpty()
                    val parsed = ConfigUriParser.parseBulk(body, subscriptionSource = url.trim())
                    if (parsed.isEmpty()) {
                        Result.failure(IllegalArgumentException("هیچ پروفایل معتبری در پاسخ اشتراک یافت نشد"))
                    } else {
                        Result.success(parsed)
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    data class ExitIpInfo(
        val ip: String,
        val country: String,
        val countryCode: String,
        val isp: String
    )

    suspend fun verifyInstagramLoadThroughTunnel(): Result<ExitIpInfo?> =
        withContext(Dispatchers.IO) {
            val tunnelClient = OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .followRedirects(true)
                .proxy(
                    java.net.Proxy(
                        java.net.Proxy.Type.HTTP,
                        InetSocketAddress("127.0.0.1", 10808)
                    )
                )
                .build()

            try {
                val request = Request.Builder()
                    .url("https://www.instagram.com/")
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                    )
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()

                tunnelClient.newCall(request).execute().use { response ->
                    val finalHost = response.request.url.host.lowercase()
                    if (finalHost.contains("peyvandha") || finalHost.startsWith("10.10.34.")) {
                        return@withContext Result.failure(
                            IllegalStateException("ترافیک اینستاگرام توسط فیلترینگ مسدود یا ریدایرکت شد")
                        )
                    }
                    if (!finalHost.endsWith("instagram.com") && !finalHost.endsWith("facebook.com")) {
                        return@withContext Result.failure(
                            IllegalStateException("پاسخ از سرور غیرمعتبر دریافت شد ($finalHost)")
                        )
                    }
                    if (response.code !in 200..499) {
                        return@withContext Result.failure(
                            IllegalStateException("خطای HTTP ${response.code} هنگام باز کردن اینستاگرام")
                        )
                    }
                    val snippet = response.body?.charStream()?.let { reader ->
                        val buf = CharArray(4096)
                        val read = reader.read(buf)
                        if (read > 0) String(buf, 0, read) else ""
                    }.orEmpty()

                    if (snippet.contains("peyvandha.ir", ignoreCase = true) ||
                        snippet.contains("10.10.34.", ignoreCase = true)
                    ) {
                        return@withContext Result.failure(
                            IllegalStateException("صفحه مسدودسازی داخلی شناسایی شد")
                        )
                    }
                    if (response.code == 200 && snippet.isBlank()) {
                        return@withContext Result.failure(
                            IllegalStateException("بدنه صفحه اینستاگرام خالی بود")
                        )
                    }
                }
            } catch (e: Exception) {
                return@withContext Result.failure(
                    IllegalStateException("لود نشدن سایت اینستاگرام: ${e.message ?: e.javaClass.simpleName}", e)
                )
            }

            // Instagram loaded cleanly; also grab exit IP info for display
            val exitInfo = checkExitIp(viaSingBoxProxy = true).getOrNull()
            Result.success(exitInfo)
        }

    suspend fun verifyTunnelReachability(): Result<ExitIpInfo> = checkExitIp(viaSingBoxProxy = true)

    suspend fun checkExitIp(viaSingBoxProxy: Boolean): Result<ExitIpInfo> =
        withContext(Dispatchers.IO) {
            val client = if (viaSingBoxProxy) {
                OkHttpClient.Builder()
                    .connectTimeout(7, TimeUnit.SECONDS)
                    .readTimeout(8, TimeUnit.SECONDS)
                    .proxy(
                        java.net.Proxy(
                            java.net.Proxy.Type.HTTP,
                            InetSocketAddress("127.0.0.1", 10808)
                        )
                    )
                    .build()
            } else {
                httpClient
            }

            // Try ipwho.is first, then ipinfo.io, then Cloudflare trace as reliable fallback
            runCatching {
                val req = Request.Builder()
                    .url("https://ipwho.is/")
                    .header("User-Agent", "SeparNet/1.0")
                    .build()
                client.newCall(req).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    val json = org.json.JSONObject(raw)
                    if (response.isSuccessful && json.optBoolean("success", true) && json.optString("ip").isNotBlank()) {
                        return@withContext Result.success(
                            ExitIpInfo(
                                ip = json.optString("ip", "Unknown"),
                                country = json.optString("country", "Unknown"),
                                countryCode = json.optString("country_code", ""),
                                isp = json.optJSONObject("connection")?.optString("isp").orEmpty()
                            )
                        )
                    }
                }
            }

            runCatching {
                val req = Request.Builder()
                    .url("https://ipinfo.io/json")
                    .header("User-Agent", "SeparNet/1.0")
                    .build()
                client.newCall(req).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    val json = org.json.JSONObject(raw)
                    val ip = json.optString("ip")
                    if (response.isSuccessful && ip.isNotBlank()) {
                        val countryCode = json.optString("country", "")
                        return@withContext Result.success(
                            ExitIpInfo(
                                ip = ip,
                                country = countryCode.ifBlank { "Unknown" },
                                countryCode = countryCode,
                                isp = json.optString("org", "")
                            )
                        )
                    }
                }
            }

            return@withContext try {
                val req = Request.Builder()
                    .url("https://1.1.1.1/cdn-cgi/trace")
                    .header("User-Agent", "SeparNet/1.0")
                    .build()
                client.newCall(req).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful || raw.isBlank()) {
                        return@withContext Result.failure(
                            IllegalStateException("عدم عبور ترافیک از تونل (HTTP ${response.code})")
                        )
                    }
                    val map = raw.lineSequence()
                        .mapNotNull { line ->
                            val idx = line.indexOf('=')
                            if (idx > 0) line.substring(0, idx) to line.substring(idx + 1) else null
                        }
                        .toMap()
                    val ip = map["ip"] ?: return@withContext Result.failure(
                        IllegalStateException("پاسخ معتبر از تونل دریافت نشد")
                    )
                    val loc = map["loc"].orEmpty()
                    Result.success(
                        ExitIpInfo(
                            ip = ip,
                            country = loc.ifBlank { "Unknown" },
                            countryCode = loc,
                            isp = "Cloudflare Edge"
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun defaultCleanScanTargets(): List<ScanEndpointResult> = listOf(
        ScanEndpointResult("Cloudflare Anycast #1", "104.16.132.229", 443, "Cloudflare IP"),
        ScanEndpointResult("Cloudflare Anycast #2", "162.159.192.1", 443, "Cloudflare IP"),
        ScanEndpointResult("Cloudflare Anycast #3", "172.64.155.1", 443, "Cloudflare IP"),
        ScanEndpointResult("Cloudflare Anycast #4", "188.114.96.1", 443, "Cloudflare IP"),
        ScanEndpointResult("Cloudflare DoH", "1.1.1.1", 443, "DoH Resolver"),
        ScanEndpointResult("Google DoH", "8.8.8.8", 443, "DoH Resolver"),
        ScanEndpointResult("Quad9 DoH", "9.9.9.9", 443, "DoH Resolver")
    )

    data class StartupNetworkCheckResult(
        val isOnline: Boolean,
        val networkTypeLabel: String,
        val isVpnTransportActive: Boolean,
        val dohReachable: Boolean,
        val directLatencyMs: Long?,
        val instagramBlocked: Boolean?,
        val cloudflareEdgeReachable: Boolean
    )

    suspend fun checkStartupInternetStatus(context: android.content.Context): StartupNetworkCheckResult =
        withContext(Dispatchers.IO) {
            val cm = runCatching {
                context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            }.getOrNull()

            val activeNetwork = cm?.activeNetwork
            val caps = activeNetwork?.let { runCatching { cm.getNetworkCapabilities(it) }.getOrNull() }
            val hasInternet = caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            val isVpnActive = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true

            val transportLabel = when {
                !hasInternet -> "آفلاین (بدون اینترنت)"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "وای‌فای (Wi-Fi)"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "اینترنت همراه (4G/5G)"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "شبکه کابلی"
                else -> "شبکه فعال"
            }

            if (!hasInternet) {
                return@withContext StartupNetworkCheckResult(
                    isOnline = false,
                    networkTypeLabel = transportLabel,
                    isVpnTransportActive = false,
                    dohReachable = false,
                    directLatencyMs = null,
                    instagramBlocked = null,
                    cloudflareEdgeReachable = false
                )
            }

            val (dnsProbe, igProbe, cfEdgeProbe) = coroutineScope {
                val dnsDeferred = async {
                    val primary = measureTlsHandshake("1.1.1.1", 443, "1.1.1.1", timeoutMs = 2200)
                    if (primary.first > 0L) primary else measureTlsHandshake("8.8.8.8", 443, "8.8.8.8", timeoutMs = 2000)
                }
                val igDeferred = async {
                    measureTlsHandshake("www.instagram.com", 443, "www.instagram.com", timeoutMs = 2200)
                }
                val cfDeferred = async {
                    measureTcpLatency("162.159.192.1", 443, timeoutMs = 2000)
                }
                Triple(dnsDeferred.await(), igDeferred.await(), cfDeferred.await())
            }

            val bestLatency = listOf(dnsProbe.first, cfEdgeProbe)
                .filter { it > 0L }
                .minOrNull()

            val anyReachable = bestLatency != null
            StartupNetworkCheckResult(
                isOnline = anyReachable || hasInternet,
                networkTypeLabel = transportLabel,
                isVpnTransportActive = isVpnActive,
                dohReachable = dnsProbe.first > 0L,
                directLatencyMs = bestLatency,
                instagramBlocked = if (anyReachable) igProbe.second <= 0L else null,
                cloudflareEdgeReachable = cfEdgeProbe > 0L || dnsProbe.first > 0L
            )
        }

    private fun isIpLiteral(value: String): Boolean {
        val parts = value.split(".")
        return parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }
    }
}

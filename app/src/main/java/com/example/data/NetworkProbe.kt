package com.example.data

import com.example.model.ProxyProfile
import com.example.model.ScanEndpointResult
import kotlinx.coroutines.Dispatchers
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

    fun defaultCleanScanTargets(): List<ScanEndpointResult> = listOf(
        ScanEndpointResult("Cloudflare Anycast #1", "104.16.132.229", 443, "Cloudflare IP"),
        ScanEndpointResult("Cloudflare Anycast #2", "162.159.192.1", 443, "Cloudflare IP"),
        ScanEndpointResult("Cloudflare Anycast #3", "172.64.155.1", 443, "Cloudflare IP"),
        ScanEndpointResult("Cloudflare Anycast #4", "188.114.96.1", 443, "Cloudflare IP"),
        ScanEndpointResult("Cloudflare DoH", "1.1.1.1", 443, "DoH Resolver"),
        ScanEndpointResult("Google DoH", "8.8.8.8", 443, "DoH Resolver"),
        ScanEndpointResult("Quad9 DoH", "9.9.9.9", 443, "DoH Resolver")
    )

    private fun isIpLiteral(value: String): Boolean {
        val parts = value.split(".")
        return parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }
    }
}

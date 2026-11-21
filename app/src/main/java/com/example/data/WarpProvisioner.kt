package com.example.data

import android.util.Base64
import com.example.model.ProxyProfile
import com.example.model.ProxyProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object WarpProvisioner {

    private val FIELD_PRIME: BigInteger = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19))
    private val A24: BigInteger = BigInteger.valueOf(121665)
    private val JSON_MEDIA = "application/json; charset=UTF-8".toMediaType()

    data class WarpEndpointCandidate(
        val host: String,
        val port: Int,
        val label: String
    )

    data class PublicFeedPreset(
        val id: String,
        val titleFa: String,
        val subtitleFa: String,
        val url: String
    )

    val WARP_ENDPOINT_CANDIDATES = listOf(
        WarpEndpointCandidate("162.159.192.1", 2408, "Cloudflare Anycast A"),
        WarpEndpointCandidate("162.159.193.10", 2408, "Cloudflare Anycast B"),
        WarpEndpointCandidate("162.159.195.1", 4500, "Cloudflare Anycast C (UDP 4500)"),
        WarpEndpointCandidate("188.114.96.1", 2408, "Cloudflare Edge D"),
        WarpEndpointCandidate("188.114.97.10", 2408, "Cloudflare Edge E"),
        WarpEndpointCandidate("188.114.98.5", 500, "Cloudflare Edge F (UDP 500)"),
        WarpEndpointCandidate("188.114.99.2", 1701, "Cloudflare Edge G (UDP 1701)"),
        WarpEndpointCandidate("162.159.192.12", 854, "Cloudflare Alt Port 854"),
        WarpEndpointCandidate("188.114.96.55", 894, "Cloudflare Alt Port 894"),
        WarpEndpointCandidate("188.114.97.170", 1018, "Cloudflare Alt Port 1018")
    )

    val PUBLIC_COMMUNITY_FEEDS = listOf(
        PublicFeedPreset(
            id = "barry_all",
            titleFa = "مخزن عمومی V2Ray/Sing-Box (بروزرسانی خودکار)",
            subtitleFa = "لیست سرورهای آزاد جمع‌آوری‌شده از گیت‌هاب (VLESS / Trojan / VMess / SS / HY2)",
            url = "https://raw.githubusercontent.com/barry-far/V2ray-Config/main/Sub1.txt"
        ),
        PublicFeedPreset(
            id = "yebekhe_mix",
            titleFa = "فید عمومی مقاوم در برابر فیلترینگ (Mix)",
            subtitleFa = "کانفیگ‌های تست‌شده عمومی از گیت‌هاب ویژه دسترسی آزاد",
            url = "https://raw.githubusercontent.com/Surfboardv2ray/TGParse/main/splitted/vless"
        ),
        PublicFeedPreset(
            id = "hysteria_feed",
            titleFa = "فید عمومی Hysteria2 و TUIC",
            subtitleFa = "سرورهای سریع مبتنی بر QUIC برای عبور از اختلال و پکت‌لاس",
            url = "https://raw.githubusercontent.com/Surfboardv2ray/TGParse/main/splitted/hy2"
        )
    )

    data class WireGuardKeyPair(
        val privateKeyBase64: String,
        val publicKeyBase64: String
    )

    fun generateWireGuardKeyPair(random: SecureRandom = SecureRandom()): WireGuardKeyPair {
        val priv = ByteArray(32)
        random.nextBytes(priv)
        priv[0] = (priv[0].toInt() and 248).toByte()
        priv[31] = ((priv[31].toInt() and 127) or 64).toByte()

        val basePoint = ByteArray(32).apply { this[0] = 9 }
        val pub = x25519(priv, basePoint)
        return WireGuardKeyPair(
            privateKeyBase64 = Base64.encodeToString(priv, Base64.NO_WRAP),
            publicKeyBase64 = Base64.encodeToString(pub, Base64.NO_WRAP)
        )
    }

    /**
     * Pure-Kotlin RFC 7748 X25519 scalar multiplication on Curve25519.
     */
    fun x25519(scalarBytes: ByteArray, uCoordBytes: ByteArray): ByteArray {
        require(scalarBytes.size == 32 && uCoordBytes.size == 32) {
            "X25519 inputs must be 32 bytes"
        }
        val k = scalarBytes.copyOf()
        k[0] = (k[0].toInt() and 248).toByte()
        k[31] = ((k[31].toInt() and 127) or 64).toByte()

        val u = uCoordBytes.copyOf()
        u[31] = (u[31].toInt() and 127).toByte()
        val x1 = BigInteger(1, u.reversedArray()).mod(FIELD_PRIME)

        var x2 = BigInteger.ONE
        var z2 = BigInteger.ZERO
        var x3 = x1
        var z3 = BigInteger.ONE
        var swap = 0

        for (t in 254 downTo 0) {
            val kt = (k[t / 8].toInt() ushr (t % 8)) and 1
            swap = swap xor kt
            if (swap != 0) {
                val tx = x2; x2 = x3; x3 = tx
                val tz = z2; z2 = z3; z3 = tz
            }
            swap = kt

            val a = x2.add(z2).mod(FIELD_PRIME)
            val aa = a.multiply(a).mod(FIELD_PRIME)
            val b = x2.subtract(z2).mod(FIELD_PRIME)
            val bb = b.multiply(b).mod(FIELD_PRIME)
            val e = aa.subtract(bb).mod(FIELD_PRIME)
            val c = x3.add(z3).mod(FIELD_PRIME)
            val d = x3.subtract(z3).mod(FIELD_PRIME)
            val da = d.multiply(a).mod(FIELD_PRIME)
            val cb = c.multiply(b).mod(FIELD_PRIME)

            val sum = da.add(cb)
            x3 = sum.multiply(sum).mod(FIELD_PRIME)
            val diff = da.subtract(cb)
            z3 = x1.multiply(diff.multiply(diff).mod(FIELD_PRIME)).mod(FIELD_PRIME)
            x2 = aa.multiply(bb).mod(FIELD_PRIME)
            z2 = e.multiply(aa.add(A24.multiply(e)).mod(FIELD_PRIME)).mod(FIELD_PRIME)
        }

        if (swap != 0) {
            val tx = x2; x2 = x3; x3 = tx
            val tz = z2; z2 = z3; z3 = tz
        }

        val zInv = z2.modPow(FIELD_PRIME.subtract(BigInteger.valueOf(2)), FIELD_PRIME)
        val res = x2.multiply(zInv).mod(FIELD_PRIME)
        return encodeLittleEndian32(res)
    }

    private fun encodeLittleEndian32(value: BigInteger): ByteArray {
        val raw = value.toByteArray()
        val bigEndian32 = ByteArray(32)
        val srcStart = if (raw.size > 32) raw.size - 32 else 0
        val copyLen = minOf(raw.size, 32)
        System.arraycopy(raw, srcStart, bigEndian32, 32 - copyLen, copyLen)
        return bigEndian32.reversedArray()
    }

    fun decodeReservedBytes(clientIdBase64: String): String {
        if (clientIdBase64.isBlank()) return ""
        return try {
            val bytes = Base64.decode(clientIdBase64.trim(), Base64.DEFAULT)
            if (bytes.size >= 3) {
                "${bytes[0].toInt() and 0xFF},${bytes[1].toInt() and 0xFF},${bytes[2].toInt() and 0xFF}"
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun findFastestWarpEndpoint(): WarpEndpointCandidate = withContext(Dispatchers.IO) {
        val tested = WARP_ENDPOINT_CANDIDATES.map { candidate ->
            async {
                val (tcpMs, _) = NetworkProbe.measureTlsHandshake(candidate.host, 443, "cloudflareclient.com", timeoutMs = 2500)
                val score = if (tcpMs > 0) tcpMs else NetworkProbe.measureTcpLatency(candidate.host, 443, timeoutMs = 2000)
                candidate to score
            }
        }.awaitAll()

        tested
            .filter { it.second > 0 }
            .minByOrNull { it.second }
            ?.first
            ?: WARP_ENDPOINT_CANDIDATES.first()
    }

    suspend fun registerWarpAccount(useLocalProxyIfConnected: Boolean): Result<ProxyProfile> =
        withContext(Dispatchers.IO) {
            try {
                val keys = generateWireGuardKeyPair()
                val bestEndpoint = findFastestWarpEndpoint()

                val clientBuilder = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(12, TimeUnit.SECONDS)
                if (useLocalProxyIfConnected) {
                    clientBuilder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 10808)))
                }
                val client = clientBuilder.build()

                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val payload = JSONObject().apply {
                    put("key", keys.publicKeyBase64)
                    put("install_id", "")
                    put("fcm_token", "")
                    put("tos", isoFormat.format(Date()))
                    put("type", "Android")
                    put("locale", "en_US")
                }

                val request = Request.Builder()
                    .url("https://api.cloudflareclient.com/v0a2158/reg")
                    .header("User-Agent", "okhttp/3.12.1")
                    .header("CF-Client-Version", "a-6.29-3594")
                    .post(payload.toString().toRequestBody(JSON_MEDIA))
                    .build()

                client.newCall(request).execute().use { response ->
                    val rawBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            IllegalStateException("خطای API کلادفلر WARP (HTTP ${response.code})")
                        )
                    }
                    val profile = parseWarpRegistrationJson(
                        jsonText = rawBody,
                        privateKeyBase64 = keys.privateKeyBase64,
                        endpointHost = bestEndpoint.host,
                        endpointPort = bestEndpoint.port
                    ) ?: return@withContext Result.failure(
                        IllegalStateException("پاسخ رجیستری WARP نامعتبر بود")
                    )
                    Result.success(profile)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun parseWarpRegistrationJson(
        jsonText: String,
        privateKeyBase64: String,
        endpointHost: String,
        endpointPort: Int
    ): ProxyProfile? {
        val root = runCatching { JSONObject(jsonText) }.getOrNull() ?: return null
        val config = root.optJSONObject("config") ?: return null
        val peer = config.optJSONArray("peers")?.optJSONObject(0) ?: return null
        val peerPubKey = peer.optString("public_key").trim()
        if (peerPubKey.isBlank()) return null

        val iface = config.optJSONObject("interface")?.optJSONObject("addresses")
        val v4 = iface?.optString("v4").orEmpty().trim().ifBlank { "172.16.0.2" }
        val v4Cidr = if (v4.contains("/")) v4 else "$v4/32"
        val clientId = config.optString("client_id").trim()
        val reservedCsv = decodeReservedBytes(clientId)

        val extra = buildList {
            add("address=$v4Cidr")
            add("mtu=1280")
            if (reservedCsv.isNotBlank()) add("reserved=$reservedCsv")
        }.joinToString(";")

        val encodedPriv = android.net.Uri.encode(privateKeyBase64)
        val encodedPub = android.net.Uri.encode(peerPubKey)
        val reservedQuery = if (reservedCsv.isNotBlank()) "&reserved=$reservedCsv" else ""
        val rawUri = "wireguard://$encodedPriv@$endpointHost:$endpointPort?publickey=$encodedPub&address=$v4Cidr&mtu=1280$reservedQuery#WARP-$endpointHost"

        return ProxyProfile(
            name = "Cloudflare WARP ($endpointHost)",
            protocol = ProxyProtocol.WIREGUARD,
            host = endpointHost,
            port = endpointPort,
            passwordOrUuid = privateKeyBase64,
            realityPublicKey = peerPubKey,
            extraParam = extra,
            rawUri = rawUri
        )
    }
}

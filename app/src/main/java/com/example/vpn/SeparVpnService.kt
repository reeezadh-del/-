package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.TrafficStats
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import com.example.data.SettingsRepository
import com.example.data.VpnRuntimeState
import com.example.model.ConnectionState
import com.example.model.VpnMode
import io.nekohasekai.libbox.CommandClient
import io.nekohasekai.libbox.CommandClientHandler
import io.nekohasekai.libbox.CommandClientOptions
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.ConnectionEvents
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LogIterator
import io.nekohasekai.libbox.OutboundGroupIterator
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.ServiceStatusMessage
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.libbox.StatusMessage
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.SystemProxyStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class SeparVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.example.vpn.ACTION_START"
        const val ACTION_STOP = "com.example.vpn.ACTION_STOP"
        const val CHANNEL_ID = "separ_vpn_channel"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        private var libboxInitialized = false
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunInterface: ParcelFileDescriptor? = null
    private var localSocksAntiDpiServer: LocalSocksAntiDpiServer? = null
    private var commandServer: CommandServer? = null
    private var commandClient: CommandClient? = null
    private var commandClientJob: Job? = null
    private var trafficMonitorJob: Job? = null
    private var latestBypassedPackages: Set<String> = emptySet()

    @Volatile
    private var lastLibboxStatusMs: Long = 0L

    @Volatile
    private var stopRequested = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpnTunnel()
            return START_NOT_STICKY
        }

        stopRequested = false
        ensureNotificationChannel()
        promoteToForeground(getString(R.string.notif_title_connecting))

        serviceScope.launch {
            startVpnTunnel()
        }
        return START_STICKY
    }

    override fun onRevoke() {
        VpnRuntimeState.appendLog("VPN", "دسترسی VPN توسط سیستم لغو شد", isError = true)
        stopVpnTunnel()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpnTunnel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun startVpnTunnel() {
        try {
            cleanupTunnelResources()

            val settingsRepo = SettingsRepository(applicationContext)
            val settings = settingsRepo.getSnapshot()
            latestBypassedPackages = settings.bypassedPackages
            var effectiveMode = settings.vpnMode
            val dao = AppDatabase.getInstance(applicationContext).proxyProfileDao()
            val allProfiles = dao.getAllProfilesOnce()
            var selectedProfile = dao.getSelectedProfile()

            ensureLibboxSetup()

            if (effectiveMode == VpnMode.AI_AUTO_DISCOVERY) {
                runAutonomousDiscoveryAndUpgrade(
                    settings = settings,
                    settingsRepo = settingsRepo,
                    dao = dao,
                    initialProfiles = allProfiles
                )
                return
            }

            if (effectiveMode == VpnMode.AUTO_BEST_SERVER) {
                if (allProfiles.isEmpty()) {
                    throw IllegalStateException("برای اتصال هوشمند خودکار، ابتدا حداقل یک سرور در تب «سرورها» اضافه کنید")
                }
            } else if (effectiveMode == VpnMode.DOUBLE_WARP_CHAIN) {
                if (allProfiles.none { it.protocol == com.example.model.ProxyProtocol.WIREGUARD }) {
                    throw IllegalStateException("برای تونل ترکیبی، ابتدا «ساخت اکانت رایگان Cloudflare WARP» را بزنید")
                }
            } else {
                if (selectedProfile == null) {
                    val firstSaved = allProfiles.firstOrNull()
                    if (firstSaved != null) {
                        dao.selectProfile(firstSaved.id)
                        selectedProfile = firstSaved.copy(isSelected = true)
                        effectiveMode = VpnMode.PROXY_CHAIN
                        settingsRepo.setVpnMode(VpnMode.PROXY_CHAIN)
                    }
                }
                if (effectiveMode == VpnMode.PROXY_CHAIN && selectedProfile == null) {
                    throw IllegalStateException("هیچ سروری انتخاب نشده است؛ ابتدا از تب «سرورها» یک کانفیگ اضافه کنید")
                }
            }

            val effectiveSettings = settings.copy(vpnMode = effectiveMode)
            VpnRuntimeState.updateState(ConnectionState.Connecting, effectiveMode)
            VpnRuntimeState.appendLog("VPN", "شروع هسته sing-box در حالت: ${effectiveMode.titleFa}")

            val attempt = tryCandidateTunnel(
                settings = effectiveSettings,
                profile = selectedProfile,
                allProfiles = allProfiles
            )
            val outcome = attempt.getOrElse {
                throw IllegalStateException("متصل نشد")
            }

            val exitInfo = outcome.exitIpInfo
            if (exitInfo != null) {
                VpnRuntimeState.setExitIpInfo(exitInfo)
                VpnRuntimeState.appendLog(
                    "VPN",
                    "عبور ترافیک از تونل تأیید شد — IP خروج: ${exitInfo.ip} (${exitInfo.countryCode})"
                )
            } else {
                VpnRuntimeState.appendLog("VPN", "عبور ترافیک از تونل با موفقیت تأیید شد")
            }

            val serverLabel = when {
                effectiveMode == VpnMode.AUTO_BEST_SERVER -> "انتخاب هوشمند (${allProfiles.size} سرور)"
                effectiveMode == VpnMode.DOUBLE_WARP_CHAIN -> "تونل ترکیبی + Cloudflare WARP"
                effectiveMode == VpnMode.PROXY_CHAIN && selectedProfile != null ->
                    "${selectedProfile.name} (${selectedProfile.protocol.displayName})"
                else -> null
            }

            finalizeConnectedState(
                effectiveMode = effectiveMode,
                effectiveSettings = effectiveSettings,
                antiDpiSocksPort = outcome.antiDpiSocksPort,
                serverLabel = serverLabel,
                exitInfo = exitInfo,
                baseUidRx = outcome.baseUidRx,
                baseUidTx = outcome.baseUidTx
            )
        } catch (e: Exception) {
            VpnRuntimeState.appendLog("VPN", "خطا: ${e.message ?: "متصل نشد"}", isError = true)
            VpnRuntimeState.updateState(ConnectionState.Error(e.message ?: "متصل نشد"))
            cleanupTunnelResources()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private data class CandidateTunnelOutcome(
        val exitIpInfo: com.example.data.NetworkProbe.ExitIpInfo?,
        val antiDpiSocksPort: Int,
        val baseUidRx: Long,
        val baseUidTx: Long
    )

    private suspend fun tryCandidateTunnel(
        settings: com.example.data.UserSettings,
        profile: com.example.model.ProxyProfile?,
        allProfiles: List<com.example.model.ProxyProfile>
    ): Result<CandidateTunnelOutcome> {
        if (stopRequested) {
            return Result.failure(kotlinx.coroutines.CancellationException("اتصال توسط کاربر لغو شد"))
        }
        return try {
            cleanupTunnelResources()

            var antiDpiSocksPort = 0
            if (settings.vpnMode == VpnMode.DPI_FRAGMENT_DOH) {
                val dohResolver = DohResolver(
                    vpnService = this,
                    dohEndpointUrl = settings.resolveActiveDnsUrl(),
                    bootstrapIp = settings.resolveBootstrapIp()
                )
                localSocksAntiDpiServer = LocalSocksAntiDpiServer(
                    vpnService = this,
                    dohResolver = dohResolver,
                    tlsFragmentEnabled = settings.tlsFragmentEnabled,
                    fragmentSizeBytes = settings.fragmentSizeBytes,
                    fragmentDelayMs = settings.fragmentDelayMs
                )
                antiDpiSocksPort = localSocksAntiDpiServer?.start() ?: 0
            }

            val configJson = SingBoxConfigBuilder.buildConfig(
                settings = settings,
                profile = profile,
                localSocksAntiDpiPort = antiDpiSocksPort,
                allProfiles = allProfiles
            )

            Libbox.checkConfig(configJson)

            val platformInterface = SeparPlatformInterface(
                vpnService = this,
                bypassedPackagesProvider = { latestBypassedPackages },
                onTunOpened = { pfd -> tunInterface = pfd }
            )

            val uid = Process.myUid()
            val baseUidRx = TrafficStats.getUidRxBytes(uid)
            val baseUidTx = TrafficStats.getUidTxBytes(uid)

            val server = Libbox.newCommandServer(serverHandler, platformInterface)
            server.start()
            commandServer = server

            val overrideOptions = OverrideOptions().apply {
                autoRedirect = false
            }
            server.startOrReloadService(configJson, overrideOptions)

            connectCommandClient()

            delay(400)
            if (stopRequested) {
                cleanupTunnelResources()
                return Result.failure(kotlinx.coroutines.CancellationException("اتصال توسط کاربر لغو شد"))
            }

            promoteToForeground("در حال بررسی پایداری اتصال…")
            VpnRuntimeState.appendLog("VPN", "تست واقعی عبور ترافیک از تونل…")

            val probeResult = com.example.data.NetworkProbe.verifyInstagramLoadThroughTunnel()
            if (probeResult.isSuccess) {
                Result.success(
                    CandidateTunnelOutcome(
                        exitIpInfo = probeResult.getOrNull(),
                        antiDpiSocksPort = antiDpiSocksPort,
                        baseUidRx = baseUidRx,
                        baseUidTx = baseUidTx
                    )
                )
            } else {
                cleanupTunnelResources()
                Result.failure(IllegalStateException("متصل نشد"))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            cleanupTunnelResources()
            throw e
        } catch (_: Exception) {
            cleanupTunnelResources()
            Result.failure(IllegalStateException("متصل نشد"))
        }
    }

    private suspend fun runAutonomousDiscoveryAndUpgrade(
        settings: com.example.data.UserSettings,
        settingsRepo: SettingsRepository,
        dao: com.example.data.ProxyProfileDao,
        initialProfiles: List<com.example.model.ProxyProfile>
    ) {
        VpnRuntimeState.updateState(ConnectionState.Connecting, VpnMode.AI_AUTO_DISCOVERY)
        VpnRuntimeState.appendLog("AI-VPN", "شروع کشف هوشمند و خودارتقای تمام روش‌های اتصال…")

        // Stage 1: Probe saved profiles concurrently & try top 4
        val directPool = initialProfiles.filter { it.protocol.supportsDirectTunnel }
        if (directPool.isNotEmpty() && !stopRequested) {
            promoteToForeground("مرحله ۱: تست سرورهای ذخیره‌شده…")
            VpnRuntimeState.appendLog("AI-VPN", "مرحله ۱: بررسی تأخیر ${directPool.size} سرور ذخیره‌شده…")

            val rankedSaved = coroutineScope {
                directPool.take(12).map { p ->
                    async {
                        val ms = com.example.data.NetworkProbe.measureTcpLatency(p.host, p.port, timeoutMs = 2200)
                        if (ms > 0L) {
                            runCatching { dao.updateLatency(p.id, ms, System.currentTimeMillis()) }
                        }
                        p to ms
                    }
                }.awaitAll()
            }.filter { it.second > 0L }
                .sortedBy { it.second }
                .take(4)

            for ((candidate, ms) in rankedSaved) {
                if (stopRequested) break
                VpnRuntimeState.appendLog("AI-VPN", "آزمایش سرور ${candidate.name} (${ms}ms)…")
                val candidateSettings = settings.copy(vpnMode = VpnMode.PROXY_CHAIN)
                val outcome = tryCandidateTunnel(candidateSettings, candidate, directPool).getOrNull()
                if (outcome != null) {
                    dao.selectProfile(candidate.id)
                    finalizeAutonomousSuccess(
                        effectiveMode = VpnMode.AI_AUTO_DISCOVERY,
                        effectiveSettings = candidateSettings,
                        outcome = outcome,
                        serverLabel = "${candidate.name} (کشف هوشمند)"
                    )
                    return
                }
            }
        }

        // Stage 2: Fresh Cloudflare WARP registration & Anycast scan (self-upgrade server pool)
        if (!stopRequested) {
            promoteToForeground("مرحله ۲: ساخت و ارتقای Cloudflare WARP…")
            VpnRuntimeState.appendLog("AI-VPN", "مرحله ۲: ثبت اکانت جدید Cloudflare WARP روی آی‌پی تمیز…")
            val warpResult = com.example.data.WarpProvisioner.registerWarpAccount(useLocalProxyIfConnected = false)
            val warpProfile = warpResult.getOrNull()
            if (warpProfile != null && !stopRequested) {
                val insertedId = dao.insertProfile(warpProfile)
                val savedWarp = warpProfile.copy(id = insertedId, isSelected = true)
                val candidateSettings = settings.copy(vpnMode = VpnMode.PROXY_CHAIN)
                val outcome = tryCandidateTunnel(candidateSettings, savedWarp, listOf(savedWarp)).getOrNull()
                if (outcome != null) {
                    dao.selectProfile(insertedId)
                    VpnRuntimeState.appendLog("AI-VPN", "خودارتقایی: اکانت تمیز WARP (${savedWarp.host}) در پایگاه‌داده ذخیره و متصل شد")
                    finalizeAutonomousSuccess(
                        effectiveMode = VpnMode.AI_AUTO_DISCOVERY,
                        effectiveSettings = candidateSettings,
                        outcome = outcome,
                        serverLabel = "${savedWarp.name} (خودارتقاگر)"
                    )
                    return
                }
            }
        }

        // Stage 3: Auto-discover from public community feeds, rank, save top reachable to DB & connect
        if (!stopRequested) {
            promoteToForeground("مرحله ۳: جستجو در مخازن آزاد سرور…")
            for (feed in com.example.data.WarpProvisioner.PUBLIC_COMMUNITY_FEEDS) {
                if (stopRequested) break
                VpnRuntimeState.appendLog("AI-VPN", "مرحله ۳: دریافت سرورهای تازه از ${feed.titleFa}…")
                val fetched = com.example.data.NetworkProbe.fetchSubscription(feed.url).getOrNull()
                    ?.filter { it.protocol.supportsDirectTunnel }
                    .orEmpty()
                if (fetched.isEmpty()) continue

                val probed = coroutineScope {
                    fetched.take(12).map { p ->
                        async {
                            val ms = com.example.data.NetworkProbe.measureTcpLatency(p.host, p.port, timeoutMs = 2000)
                            p.copy(latencyMs = if (ms > 0L) ms else -1L, lastTestedAt = System.currentTimeMillis()) to ms
                        }
                    }.awaitAll()
                }
                val topReachable = probed
                    .filter { it.second > 0L }
                    .sortedBy { it.second }
                    .map { it.first }
                    .take(5)

                if (topReachable.isNotEmpty()) {
                    dao.insertProfiles(topReachable)
                    val refreshedPool = dao.getAllProfilesOnce()
                    for (candidate in topReachable.take(3)) {
                        if (stopRequested) break
                        VpnRuntimeState.appendLog("AI-VPN", "آزمایش سرور کشف‌شده: ${candidate.name} (${candidate.latencyMs}ms)…")
                        val candidateSettings = settings.copy(vpnMode = VpnMode.PROXY_CHAIN)
                        val outcome = tryCandidateTunnel(candidateSettings, candidate, refreshedPool).getOrNull()
                        if (outcome != null) {
                            refreshedPool.firstOrNull { it.rawUri == candidate.rawUri }?.let {
                                dao.selectProfile(it.id)
                            }
                            VpnRuntimeState.appendLog("AI-VPN", "خودارتقایی: ${topReachable.size} سرور جدید به لیست اضافه شد و ${candidate.name} متصل گردید")
                            finalizeAutonomousSuccess(
                                effectiveMode = VpnMode.AI_AUTO_DISCOVERY,
                                effectiveSettings = candidateSettings,
                                outcome = outcome,
                                serverLabel = "${candidate.name} (کشف خودکار)"
                            )
                            return
                        }
                    }
                }
            }
        }

        // Stage 4: Try DOUBLE_WARP_CHAIN if any WireGuard profile exists
        val updatedProfiles = dao.getAllProfilesOnce()
        if (!stopRequested && updatedProfiles.any { it.protocol == com.example.model.ProxyProtocol.WIREGUARD }) {
            promoteToForeground("مرحله ۴: تست تونل ترکیبی WARP…")
            VpnRuntimeState.appendLog("AI-VPN", "مرحله ۴: آزمایش تونل ترکیبی Double WARP…")
            val chainSettings = settings.copy(vpnMode = VpnMode.DOUBLE_WARP_CHAIN)
            val outcome = tryCandidateTunnel(chainSettings, updatedProfiles.firstOrNull(), updatedProfiles).getOrNull()
            if (outcome != null) {
                finalizeAutonomousSuccess(
                    effectiveMode = VpnMode.AI_AUTO_DISCOVERY,
                    effectiveSettings = chainSettings,
                    outcome = outcome,
                    serverLabel = "تونل ترکیبی WARP (خودارتقاگر)"
                )
                return
            }
        }

        // Stage 5: Adaptive TLS Fragment + DoH sweep (persist winning DPI settings!)
        if (!stopRequested) {
            promoteToForeground("مرحله ۵: بهینه‌سازی ضد فیلتر TLS Fragment…")
            val dpiMatrix = listOf(
                Triple(16, 8, "cloudflare"),
                Triple(24, 12, "cloudflare"),
                Triple(10, 24, "google"),
                Triple(16, 10, "quad9")
            )
            for ((fragSize, fragDelay, dnsId) in dpiMatrix) {
                if (stopRequested) break
                VpnRuntimeState.appendLog("AI-VPN", "مرحله ۵: آزمایش ماتریس TLS Fragment (${fragSize}B / ${fragDelay}ms / $dnsId)…")
                val dpiSettings = settings.copy(
                    vpnMode = VpnMode.DPI_FRAGMENT_DOH,
                    tlsFragmentEnabled = true,
                    fragmentSizeBytes = fragSize,
                    fragmentDelayMs = fragDelay,
                    dnsPresetId = dnsId
                )
                val outcome = tryCandidateTunnel(dpiSettings, null, emptyList()).getOrNull()
                if (outcome != null) {
                    settingsRepo.setTlsFragmentEnabled(true)
                    settingsRepo.setFragmentParams(fragSize, fragDelay)
                    settingsRepo.setDnsPreset(dnsId)
                    VpnRuntimeState.appendLog("AI-VPN", "خودارتقایی: تنظیمات برنده TLS Fragment (${fragSize}B/${fragDelay}ms, $dnsId) ذخیره شد")
                    finalizeAutonomousSuccess(
                        effectiveMode = VpnMode.AI_AUTO_DISCOVERY,
                        effectiveSettings = dpiSettings,
                        outcome = outcome,
                        serverLabel = "ضد فیلتر هوشمند (${fragSize}B • $dnsId)"
                    )
                    return
                }
            }
        }

        throw IllegalStateException("متصل نشد")
    }

    private fun finalizeAutonomousSuccess(
        effectiveMode: VpnMode,
        effectiveSettings: com.example.data.UserSettings,
        outcome: CandidateTunnelOutcome,
        serverLabel: String
    ) {
        val exitInfo = outcome.exitIpInfo
        if (exitInfo != null) {
            VpnRuntimeState.setExitIpInfo(exitInfo)
            VpnRuntimeState.appendLog(
                "AI-VPN",
                "اتصال هوشمند برقرار شد — IP خروج: ${exitInfo.ip} (${exitInfo.countryCode})"
            )
        } else {
            VpnRuntimeState.appendLog("AI-VPN", "اتصال هوشمند با موفقیت برقرار شد")
        }
        finalizeConnectedState(
            effectiveMode = effectiveMode,
            effectiveSettings = effectiveSettings,
            antiDpiSocksPort = outcome.antiDpiSocksPort,
            serverLabel = serverLabel,
            exitInfo = exitInfo,
            baseUidRx = outcome.baseUidRx,
            baseUidTx = outcome.baseUidTx
        )
    }

    private fun finalizeConnectedState(
        effectiveMode: VpnMode,
        effectiveSettings: com.example.data.UserSettings,
        antiDpiSocksPort: Int,
        serverLabel: String?,
        exitInfo: com.example.data.NetworkProbe.ExitIpInfo?,
        baseUidRx: Long,
        baseUidTx: Long
    ) {
        VpnRuntimeState.updateSessionInfo(
            activeDnsLabel = effectiveSettings.resolveActiveDnsLabel(),
            localProxyPort = antiDpiSocksPort,
            activeServerLabel = serverLabel
        )
        VpnRuntimeState.updateState(ConnectionState.Connected, effectiveMode)
        startTrafficMonitor(localSocksAntiDpiServer, baseUidRx, baseUidTx)
        val countrySuffix = exitInfo?.countryCode?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
        promoteToForeground(
            if (serverLabel != null) "سپر نت متصل$countrySuffix: $serverLabel"
            else getString(R.string.notif_title_connected)
        )
        VpnRuntimeState.appendLog(
            "VPN",
            "تونل کامل دستگاه (0.0.0.0/0) برقرار شد" +
                if (serverLabel != null) " از طریق $serverLabel" else " (ضد فیلتر مستقیم)"
        )
    }

    private fun ensureLibboxSetup() {
        if (libboxInitialized) return
        synchronized(Companion) {
            if (libboxInitialized) return
            val baseDir = File(filesDir, "singbox").apply { mkdirs() }
            val workDir = File(baseDir, "work").apply { mkdirs() }
            val tempDir = File(cacheDir, "singbox_tmp").apply { mkdirs() }

            val options = SetupOptions().apply {
                basePath = baseDir.absolutePath
                workingPath = workDir.absolutePath
                tempPath = tempDir.absolutePath
                fixAndroidStack = true
                logMaxLines = 200
            }
            Libbox.setup(options)
            libboxInitialized = true
        }
    }

    private fun connectCommandClient() {
        commandClientJob?.cancel()
        runCatching { commandClient?.disconnect() }
        val options = CommandClientOptions().apply {
            addCommand(Libbox.CommandStatus)
            addCommand(Libbox.CommandLog)
            statusInterval = 1_000_000_000L // 1 second in nanoseconds
        }
        val client = Libbox.newCommandClient(clientHandler, options)
        commandClient = client
        commandClientJob = serviceScope.launch {
            repeat(5) { attempt ->
                if (!isActive || stopRequested || commandClient !== client) return@launch
                val res = runCatching { client.connect() }
                if (res.isSuccess) return@launch
                if (attempt == 4) {
                    VpnRuntimeState.appendLog(
                        "Core",
                        "اتصال CommandClient انجام نشد (${res.exceptionOrNull()?.message ?: "unknown"})؛ پایش ترافیک از مسیر سیستم فعال است"
                    )
                } else {
                    delay(250L)
                }
            }
        }
    }

    private fun startTrafficMonitor(
        socksServer: LocalSocksAntiDpiServer?,
        baseUidRx: Long,
        baseUidTx: Long
    ) {
        trafficMonitorJob?.cancel()
        val uid = Process.myUid()
        val uidSupported = baseUidRx >= 0L && baseUidTx >= 0L
        val safeBaseRx = if (uidSupported) baseUidRx else 0L
        val safeBaseTx = if (uidSupported) baseUidTx else 0L

        trafficMonitorJob = serviceScope.launch {
            var (prevRx, prevTx) = readSessionTrafficBytes(uid, uidSupported, safeBaseRx, safeBaseTx, socksServer)
            var lastTickMs = System.currentTimeMillis()
            if ((System.currentTimeMillis() - lastLibboxStatusMs) >= 2500L && (prevRx > 0L || prevTx > 0L)) {
                VpnRuntimeState.setTrafficTotals(prevRx, prevTx, 0L, 0L)
            }

            while (isActive && !stopRequested) {
                delay(1000L)
                val now = System.currentTimeMillis()
                val (curRx, curTx) = readSessionTrafficBytes(uid, uidSupported, safeBaseRx, safeBaseTx, socksServer)
                val elapsedMs = (now - lastTickMs).coerceAtLeast(250L)
                val rxBps = ((curRx - prevRx).coerceAtLeast(0L) * 1000L) / elapsedMs
                val txBps = ((curTx - prevTx).coerceAtLeast(0L) * 1000L) / elapsedMs
                prevRx = curRx
                prevTx = curTx
                lastTickMs = now

                val libboxFresh = (now - lastLibboxStatusMs) < 2500L
                if (!libboxFresh) {
                    VpnRuntimeState.setTrafficTotals(
                        rxTotal = curRx,
                        txTotal = curTx,
                        rxSpeed = rxBps,
                        txSpeed = txBps
                    )
                }
            }
        }
    }

    private fun readSessionTrafficBytes(
        uid: Int,
        uidSupported: Boolean,
        baseUidRx: Long,
        baseUidTx: Long,
        socksServer: LocalSocksAntiDpiServer?
    ): Pair<Long, Long> {
        val uidRx = if (uidSupported) {
            val raw = TrafficStats.getUidRxBytes(uid)
            if (raw >= baseUidRx) raw - baseUidRx else 0L
        } else 0L
        val uidTx = if (uidSupported) {
            val raw = TrafficStats.getUidTxBytes(uid)
            if (raw >= baseUidTx) raw - baseUidTx else 0L
        } else 0L
        val (socksRx, socksTx) = socksServer?.getTrafficSnapshot() ?: (0L to 0L)
        return maxOf(uidRx, socksRx) to maxOf(uidTx, socksTx)
    }

    private val serverHandler = object : CommandServerHandler {
        override fun getSystemProxyStatus(): SystemProxyStatus =
            SystemProxyStatus().apply {
                available = false
                enabled = false
            }

        override fun serviceReload() {}

        override fun serviceStop() {
            stopVpnTunnel()
        }

        override fun setSystemProxyEnabled(enabled: Boolean) {}

        override fun writeDebugMessage(message: String) {
            if (message.isNotBlank()) {
                VpnRuntimeState.appendLog("Core", message)
            }
        }
    }

    private val clientHandler = object : CommandClientHandler {
        override fun clearLogs() {}

        override fun connected() {}

        override fun disconnected(message: String?) {
            if (!message.isNullOrBlank()) {
                VpnRuntimeState.appendLog("Core", message, isError = true)
            }
        }

        override fun initializeClashMode(modeList: StringIterator?, currentMode: String?) {}

        override fun setDefaultLogLevel(level: Int) {}

        override fun updateClashMode(newMode: String?) {}

        override fun writeConnectionEvents(events: ConnectionEvents?) {}

        override fun writeGroups(groups: OutboundGroupIterator?) {}

        override fun writeLogs(messageList: LogIterator?) {
            if (messageList == null) return
            while (messageList.hasNext()) {
                val entry = messageList.next()
                val msg = entry.message
                if (msg.isNotBlank()) {
                    VpnRuntimeState.appendLog("sing-box", msg, isError = entry.level <= 2)
                }
            }
        }

        override fun writeServiceStatus(message: ServiceStatusMessage?) {}

        override fun writeStatus(message: StatusMessage?) {
            if (message == null || !message.trafficAvailable) return
            val rxTotal = message.downlinkTotal.coerceAtLeast(0L)
            val txTotal = message.uplinkTotal.coerceAtLeast(0L)
            val rxSpeed = message.downlink.coerceAtLeast(0L)
            val txSpeed = message.uplink.coerceAtLeast(0L)
            if (rxTotal > 0L || txTotal > 0L || rxSpeed > 0L || txSpeed > 0L) {
                lastLibboxStatusMs = System.currentTimeMillis()
                VpnRuntimeState.setTrafficTotals(
                    rxTotal = rxTotal,
                    txTotal = txTotal,
                    rxSpeed = rxSpeed,
                    txSpeed = txSpeed
                )
            }
        }
    }

    private fun stopVpnTunnel() {
        stopRequested = true
        VpnRuntimeState.updateState(ConnectionState.Disconnecting)
        cleanupTunnelResources()
        VpnRuntimeState.updateState(ConnectionState.Disconnected)
        VpnRuntimeState.appendLog("VPN", "اتصال تونل قطع شد")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanupTunnelResources() {
        trafficMonitorJob?.cancel()
        trafficMonitorJob = null
        commandClientJob?.cancel()
        commandClientJob = null
        lastLibboxStatusMs = 0L
        runCatching { commandClient?.disconnect() }
        commandClient = null
        runCatching { commandServer?.closeService() }
        runCatching { commandServer?.close() }
        commandServer = null
        runCatching { localSocksAntiDpiServer?.stop() }
        localSocksAntiDpiServer = null
        runCatching { tunInterface?.close() }
        tunInterface = null
    }

    private fun promoteToForeground(title: String) {
        val notification = buildNotification(title)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(title: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, SeparVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(getString(R.string.label_protection_summary))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.btn_disconnect),
                stopIntent
            )
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
            }
            nm.createNotificationChannel(channel)
        }
    }
}

package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    private var latestBypassedPackages: Set<String> = emptySet()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpnTunnel()
            return START_NOT_STICKY
        }

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

            val settings = SettingsRepository(applicationContext).getSnapshot()
            latestBypassedPackages = settings.bypassedPackages
            var effectiveMode = settings.vpnMode
            val dao = AppDatabase.getInstance(applicationContext).proxyProfileDao()
            var selectedProfile = dao.getSelectedProfile()

            if (selectedProfile == null) {
                val firstSaved = dao.getAllProfilesOnce().firstOrNull()
                if (firstSaved != null) {
                    dao.selectProfile(firstSaved.id)
                    selectedProfile = firstSaved.copy(isSelected = true)
                    effectiveMode = VpnMode.PROXY_CHAIN
                    SettingsRepository(applicationContext).setVpnMode(VpnMode.PROXY_CHAIN)
                }
            }

            if (effectiveMode == VpnMode.PROXY_CHAIN && selectedProfile == null) {
                throw IllegalStateException("هیچ سروری انتخاب نشده است؛ ابتدا از تب «سرورها» یک کانفیگ VLESS / VMess / Trojan / SS / SOCKS5 اضافه کنید")
            }

            val effectiveSettings = settings.copy(vpnMode = effectiveMode)
            VpnRuntimeState.updateState(ConnectionState.Connecting, effectiveMode)
            VpnRuntimeState.appendLog("VPN", "شروع هسته sing-box در حالت: ${effectiveMode.titleFa}")

            ensureLibboxSetup()

            var antiDpiSocksPort = 0
            if (effectiveMode == VpnMode.DPI_FRAGMENT_DOH) {
                val dohResolver = DohResolver(
                    vpnService = this,
                    dohEndpointUrl = effectiveSettings.resolveActiveDnsUrl(),
                    bootstrapIp = effectiveSettings.resolveBootstrapIp()
                )
                localSocksAntiDpiServer = LocalSocksAntiDpiServer(
                    vpnService = this,
                    dohResolver = dohResolver,
                    tlsFragmentEnabled = effectiveSettings.tlsFragmentEnabled,
                    fragmentSizeBytes = effectiveSettings.fragmentSizeBytes,
                    fragmentDelayMs = effectiveSettings.fragmentDelayMs
                )
                antiDpiSocksPort = localSocksAntiDpiServer?.start() ?: 0
            }

            val configJson = SingBoxConfigBuilder.buildConfig(
                settings = effectiveSettings,
                profile = selectedProfile,
                localSocksAntiDpiPort = antiDpiSocksPort
            )

            Libbox.checkConfig(configJson)

            val platformInterface = SeparPlatformInterface(
                vpnService = this,
                bypassedPackagesProvider = { latestBypassedPackages },
                onTunOpened = { pfd -> tunInterface = pfd }
            )

            val server = Libbox.newCommandServer(serverHandler, platformInterface)
            server.start()
            commandServer = server

            val overrideOptions = OverrideOptions().apply {
                autoRedirect = false
            }
            server.startOrReloadService(configJson, overrideOptions)

            connectCommandClient()

            // Wait briefly for sing-box mixed-in (127.0.0.1:10808) & TUN to bind, then verify Instagram actually loads
            kotlinx.coroutines.delay(400)
            promoteToForeground("در حال تست باز شدن اینستاگرام…")
            VpnRuntimeState.appendLog("VPN", "در حال تست واقعی لود شدن اینستاگرام از طریق تونل…")
            val probeResult = com.example.data.NetworkProbe.verifyInstagramLoadThroughTunnel()
            val exitInfo = probeResult.getOrElse { err ->
                throw IllegalStateException(
                    if (effectiveMode == VpnMode.PROXY_CHAIN) {
                        "اینستاگرام با این سرور باز نشد — اتصال لغو شد (${err.message ?: "خطای شبکه"}). سرور دیگری در تب سرورها انتخاب کنید."
                    } else {
                        "اینستاگرام بدون سرور خارجی باز نشد! برای باز شدن اینستاگرام یک کانفیگ معتبر در تب «سرورها» اضافه کنید."
                    }
                )
            }

            if (exitInfo != null) {
                VpnRuntimeState.setExitIpInfo(exitInfo)
                VpnRuntimeState.appendLog(
                    "VPN",
                    "اینستاگرام با موفقیت لود شد — IP خروج: ${exitInfo.ip} (${exitInfo.countryCode})"
                )
            } else {
                VpnRuntimeState.appendLog("VPN", "سایت اینستاگرام از طریق تونل با موفقیت لود شد")
            }

            val serverLabel = if (effectiveMode == VpnMode.PROXY_CHAIN && selectedProfile != null) {
                "${selectedProfile.name} (${selectedProfile.protocol.displayName})"
            } else {
                null
            }

            VpnRuntimeState.updateSessionInfo(
                activeDnsLabel = effectiveSettings.resolveActiveDnsLabel(),
                localProxyPort = antiDpiSocksPort,
                activeServerLabel = serverLabel
            )
            VpnRuntimeState.updateState(ConnectionState.Connected, effectiveMode)
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
        } catch (e: Exception) {
            VpnRuntimeState.appendLog("VPN", "خطا: ${e.message}", isError = true)
            VpnRuntimeState.updateState(ConnectionState.Error(e.message ?: "خطا در برقراری تونل"))
            cleanupTunnelResources()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
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
        val options = CommandClientOptions().apply {
            addCommand(Libbox.CommandStatus)
            addCommand(Libbox.CommandLog)
            statusInterval = 1_000_000_000L // 1 second in nanoseconds
        }
        val client = Libbox.newCommandClient(clientHandler, options)
        commandClient = client
        serviceScope.launch {
            runCatching { client.connect() }
        }
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
            if (message == null) return
            VpnRuntimeState.setTrafficTotals(
                rxTotal = message.downlinkTotal,
                txTotal = message.uplinkTotal,
                rxSpeed = message.downlink,
                txSpeed = message.uplink
            )
        }
    }

    private fun stopVpnTunnel() {
        VpnRuntimeState.updateState(ConnectionState.Disconnecting)
        cleanupTunnelResources()
        VpnRuntimeState.updateState(ConnectionState.Disconnected)
        VpnRuntimeState.appendLog("VPN", "اتصال تونل قطع شد")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanupTunnelResources() {
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

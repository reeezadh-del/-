package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ProxyInfo
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream

class SeparVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.example.vpn.ACTION_START"
        const val ACTION_STOP = "com.example.vpn.ACTION_STOP"
        const val CHANNEL_ID = "separ_vpn_channel"
        private const val NOTIFICATION_ID = 1001
        private const val TUN_ADDRESS = "10.120.0.2"
        private const val TUN_DNS_SERVER = "10.120.0.1"
    }

    private var serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunInterface: ParcelFileDescriptor? = null
    private var localProxyServer: LocalProxyServer? = null
    private var tunJob: Job? = null
    private var statsJob: Job? = null

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
            val selectedProfile = AppDatabase.getInstance(applicationContext)
                .proxyProfileDao()
                .getSelectedProfile()

            VpnRuntimeState.updateState(ConnectionState.Connecting, settings.vpnMode)
            VpnRuntimeState.appendLog("VPN", "شروع اتصال در حالت: ${settings.vpnMode.titleFa}")

            val dohResolver = DohResolver(
                vpnService = this,
                dohEndpointUrl = settings.resolveActiveDnsUrl(),
                bootstrapIp = settings.resolveBootstrapIp()
            )

            var localPort = 0
            val activeProxy = if (settings.vpnMode == VpnMode.PROXY_CHAIN &&
                selectedProfile != null && selectedProfile.protocol.supportsDirectTunnel
            ) {
                selectedProfile
            } else {
                null
            }

            if (settings.vpnMode == VpnMode.DPI_FRAGMENT_DOH || settings.vpnMode == VpnMode.PROXY_CHAIN) {
                localProxyServer = LocalProxyServer(
                    vpnService = this,
                    dohResolver = dohResolver,
                    tlsFragmentEnabled = settings.tlsFragmentEnabled,
                    fragmentSizeBytes = settings.fragmentSizeBytes,
                    fragmentDelayMs = settings.fragmentDelayMs,
                    upstreamProxy = activeProxy
                )
                localPort = localProxyServer?.start() ?: 0
            }

            val builder = Builder()
                .setSession(getString(R.string.app_name))
                .setMtu(1500)
                .addAddress(TUN_ADDRESS, 32)
                .addDnsServer(TUN_DNS_SERVER)
                .addRoute(TUN_DNS_SERVER, 32)
                .allowBypass()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && localPort > 0) {
                builder.setHttpProxy(ProxyInfo.buildDirectProxy("127.0.0.1", localPort))
            }

            // Always exclude self to prevent socket loop
            runCatching { builder.addDisallowedApplication(packageName) }

            // Exclude user-selected Iranian / bypassed apps
            settings.bypassedPackages.forEach { pkg ->
                runCatching { builder.addDisallowedApplication(pkg) }
            }

            val pfd = builder.establish()
                ?: throw IllegalStateException("امکان ایجاد رابط کاربری TUN وجود ندارد")

            tunInterface = pfd

            VpnRuntimeState.updateSessionInfo(
                activeDnsLabel = settings.resolveActiveDnsLabel(),
                localProxyPort = localPort,
                activeServerLabel = activeProxy?.name ?: selectedProfile?.name
            )
            VpnRuntimeState.updateState(ConnectionState.Connected, settings.vpnMode)
            promoteToForeground(getString(R.string.notif_title_connected))
            VpnRuntimeState.appendLog(
                "VPN",
                "تونل برقرار شد (${settings.resolveActiveDnsLabel()} • استثنا: ${settings.bypassedPackages.size} برنامه)"
            )

            startTunDnsLoop(pfd, dohResolver)
            startSpeedTicker()
        } catch (e: Exception) {
            VpnRuntimeState.appendLog("VPN", "خطا: ${e.message}", isError = true)
            VpnRuntimeState.updateState(ConnectionState.Error(e.message ?: "خطا در برقراری تونل"))
            cleanupTunnelResources()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startTunDnsLoop(pfd: ParcelFileDescriptor, dohResolver: DohResolver) {
        tunJob?.cancel()
        tunJob = serviceScope.launch {
            val input = FileInputStream(pfd.fileDescriptor)
            val output = FileOutputStream(pfd.fileDescriptor)
            val writeLock = Any()
            val buffer = ByteArray(32767)

            while (isActive) {
                val length = try {
                    input.read(buffer)
                } catch (_: Exception) {
                    -1
                }
                if (length <= 0) {
                    if (length < 0) break
                    continue
                }

                val packetCopy = buffer.copyOf(length)
                val dnsReq = PacketUtils.parseUdpDnsPacket(packetCopy, length) ?: continue
                VpnRuntimeState.addBytes(rxDelta = 0L, txDelta = length.toLong())

                launch {
                    val queryName = PacketUtils.extractDnsQueryName(dnsReq.dnsPayload)
                    val dnsResp = dohResolver.resolveWireDns(dnsReq.dnsPayload)
                    if (dnsResp != null) {
                        val responsePacket = PacketUtils.buildUdpDnsResponse(dnsReq, dnsResp)
                        synchronized(writeLock) {
                            runCatching {
                                output.write(responsePacket)
                                output.flush()
                            }
                        }
                        VpnRuntimeState.addBytes(rxDelta = responsePacket.size.toLong(), txDelta = 0L)
                        VpnRuntimeState.incrementDnsQueries()
                        VpnRuntimeState.appendLog("DoH", "پاسخ امن DNS برای $queryName")
                    }
                }
            }
        }
    }

    private fun startSpeedTicker() {
        statsJob?.cancel()
        statsJob = serviceScope.launch {
            var prevRx = VpnRuntimeState.telemetry.value.rxBytes
            var prevTx = VpnRuntimeState.telemetry.value.txBytes
            while (isActive) {
                delay(1000L)
                val snap = VpnRuntimeState.telemetry.value
                val rxRate = (snap.rxBytes - prevRx).coerceAtLeast(0L)
                val txRate = (snap.txBytes - prevTx).coerceAtLeast(0L)
                prevRx = snap.rxBytes
                prevTx = snap.txBytes
                VpnRuntimeState.updateSpeeds(rxRate, txRate)
            }
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
        tunJob?.cancel()
        tunJob = null
        statsJob?.cancel()
        statsJob = null
        runCatching { localProxyServer?.stop() }
        localProxyServer = null
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

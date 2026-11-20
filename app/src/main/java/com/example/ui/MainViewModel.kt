package com.example.ui

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.SeparApp
import com.example.data.ConfigUriParser
import com.example.data.NetworkProbe
import com.example.data.UserSettings
import com.example.data.VpnRuntimeState
import com.example.model.ConnectionState
import com.example.model.InstalledAppInfo
import com.example.model.ProxyProfile
import com.example.model.ScanEndpointResult
import com.example.model.TunnelLogEntry
import com.example.model.VpnMode
import com.example.model.VpnTelemetry
import com.example.vpn.SeparVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MainUiState(
    val telemetry: VpnTelemetry = VpnTelemetry(),
    val settings: UserSettings = UserSettings(),
    val profiles: List<ProxyProfile> = emptyList(),
    val selectedProfile: ProxyProfile? = null,
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val appsLoading: Boolean = false,
    val scanResults: List<ScanEndpointResult> = NetworkProbe.defaultCleanScanTargets(),
    val isScanningNetwork: Boolean = false,
    val isTestingProfiles: Boolean = false,
    val isImportingSubscription: Boolean = false,
    val logs: List<TunnelLogEntry> = emptyList(),
    val bannerMessage: String? = null,
    val exitIpInfo: NetworkProbe.ExitIpInfo? = null,
    val isCheckingExitIp: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SeparApp
    private val profileRepo = app.profileRepository
    private val settingsRepo = app.settingsRepository

    private val _installedAppsRaw = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    private val _appsLoading = MutableStateFlow(false)
    private val _scanResults = MutableStateFlow(NetworkProbe.defaultCleanScanTargets())
    private val _isScanningNetwork = MutableStateFlow(false)
    private val _isTestingProfiles = MutableStateFlow(false)
    private val _isImportingSub = MutableStateFlow(false)
    private val _bannerMessage = MutableStateFlow<String?>(null)
    private val _exitIpInfo = MutableStateFlow<NetworkProbe.ExitIpInfo?>(null)
    private val _isCheckingExitIp = MutableStateFlow(false)

    private val baseDataFlow = combine(
        VpnRuntimeState.telemetry,
        settingsRepo.settingsFlow,
        profileRepo.allProfiles,
        profileRepo.selectedProfile,
        VpnRuntimeState.logs
    ) { telemetry, settings, profiles, selectedProfile, logs ->
        BaseSnapshot(telemetry, settings, profiles, selectedProfile, logs)
    }

    private val ipStateFlow = combine(_exitIpInfo, _isCheckingExitIp) { info, checking ->
        info to checking
    }

    private val auxStateFlow = combine(
        _installedAppsRaw,
        _appsLoading,
        _scanResults,
        _isScanningNetwork,
        _isTestingProfiles,
        _isImportingSub,
        _bannerMessage,
        ipStateFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val ipPair = values[7] as Pair<NetworkProbe.ExitIpInfo?, Boolean>
        AuxSnapshot(
            apps = values[0] as List<InstalledAppInfo>,
            appsLoading = values[1] as Boolean,
            scanResults = values[2] as List<ScanEndpointResult>,
            isScanning = values[3] as Boolean,
            isTesting = values[4] as Boolean,
            isImporting = values[5] as Boolean,
            banner = values[6] as String?,
            exitIpInfo = ipPair.first,
            isCheckingExitIp = ipPair.second
        )
    }

    val uiState: StateFlow<MainUiState> = combine(baseDataFlow, auxStateFlow) { base, aux ->
        val bypassedSet = base.settings.bypassedPackages
        val mappedApps = aux.apps.map { it.copy(isBypassed = it.packageName in bypassedSet) }
        MainUiState(
            telemetry = base.telemetry,
            settings = base.settings,
            profiles = base.profiles,
            selectedProfile = base.selectedProfile,
            installedApps = mappedApps,
            appsLoading = aux.appsLoading,
            scanResults = aux.scanResults,
            isScanningNetwork = aux.isScanning,
            isTestingProfiles = aux.isTesting,
            isImportingSubscription = aux.isImporting,
            logs = base.logs,
            bannerMessage = aux.banner,
            exitIpInfo = aux.exitIpInfo,
            isCheckingExitIp = aux.isCheckingExitIp
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    init {
        loadInstalledApps()
    }

    fun startVpnService() {
        val intent = Intent(app, SeparVpnService::class.java).apply {
            action = SeparVpnService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            app.startForegroundService(intent)
        } else {
            app.startService(intent)
        }
    }

    fun stopVpnService() {
        val intent = Intent(app, SeparVpnService::class.java).apply {
            action = SeparVpnService.ACTION_STOP
        }
        app.startService(intent)
    }

    fun setVpnMode(mode: VpnMode) {
        viewModelScope.launch {
            settingsRepo.setVpnMode(mode)
            if (uiState.value.telemetry.state == ConnectionState.Connected) {
                showBanner("برای اعمال حالت جدید، یک بار قطع و وصل کنید")
            }
        }
    }

    fun setDnsPreset(presetId: String) {
        viewModelScope.launch {
            settingsRepo.setDnsPreset(presetId)
            showBanner("DNS امن به ${com.example.model.DnsPreset.findById(presetId).nameFa} تغییر یافت")
        }
    }

    fun setCustomDohUrl(url: String) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("https://")) {
            showBanner("آدرس DoH باید با https:// شروع شود")
            return
        }
        viewModelScope.launch {
            settingsRepo.setCustomDohUrl(trimmed)
            showBanner("DNS سفارشی ذخیره شد")
        }
    }

    fun setTlsFragmentEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setTlsFragmentEnabled(enabled)
        }
    }

    fun setFragmentParams(sizeBytes: Int, delayMs: Int) {
        viewModelScope.launch {
            settingsRepo.setFragmentParams(sizeBytes, delayMs)
        }
    }

    fun importFromTextOrUrl(rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            showBanner("متن یا لینک ورودی خالی است")
            return
        }

        if (trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true) && !trimmed.contains("@") && !trimmed.contains("#")
        ) {
            // Treat as subscription URL unless it's an HTTP proxy link with host:port
            val isLikelyHttpProxy = trimmed.matches(Regex("^https?://[^/]+:\\d+/?$"))
            if (!isLikelyHttpProxy) {
                fetchSubscription(trimmed)
                return
            }
        }

        viewModelScope.launch {
            val parsed = ConfigUriParser.parseBulk(trimmed)
            if (parsed.isEmpty()) {
                showBanner("فرمت لینک معتبر نبود (پشتیبانی از VLESS, VMess, Trojan, SS, SOCKS5, HTTP)")
            } else {
                profileRepo.insertAll(parsed)
                val firstInserted = profileRepo.getAllOnce().firstOrNull()
                if (firstInserted != null && profileRepo.getSelectedProfile() == null) {
                    profileRepo.select(firstInserted.id)
                    settingsRepo.setVpnMode(VpnMode.PROXY_CHAIN)
                }
                showBanner("${parsed.size} پروفایل با موفقیت اضافه شد")
                testAllProfiles()
            }
        }
    }

    fun addManualProxy(
        name: String,
        host: String,
        portText: String,
        protocol: com.example.model.ProxyProtocol,
        username: String,
        password: String
    ) {
        val cleanHost = host.trim()
        val port = portText.trim().toIntOrNull()
        if (cleanHost.isEmpty() || port == null || port !in 1..65535) {
            showBanner("آدرس سرور یا پورت معتبر نیست")
            return
        }

        val scheme = when (protocol) {
            com.example.model.ProxyProtocol.SOCKS5 -> "socks5"
            com.example.model.ProxyProtocol.HTTP -> "http"
            com.example.model.ProxyProtocol.VLESS -> "vless"
            com.example.model.ProxyProtocol.VMESS -> "vmess"
            com.example.model.ProxyProtocol.TROJAN -> "trojan"
            com.example.model.ProxyProtocol.SHADOWSOCKS -> "ss"
        }
        val creds = if (username.isNotBlank()) "$username:$password@" else ""
        val rawUri = "$scheme://$creds$cleanHost:$port#${name.ifBlank { cleanHost }}"

        viewModelScope.launch {
            val id = profileRepo.insert(
                ProxyProfile(
                    name = name.trim().ifBlank { "$cleanHost:$port" },
                    protocol = protocol,
                    host = cleanHost,
                    port = port,
                    username = username.trim(),
                    passwordOrUuid = password.trim(),
                    rawUri = rawUri
                )
            )
            profileRepo.select(id)
            if (protocol.supportsDirectTunnel) {
                settingsRepo.setVpnMode(VpnMode.PROXY_CHAIN)
            }
            showBanner("پروفایل ذخیره و انتخاب شد")
            testSingleProfile(id, cleanHost, port)
        }
    }

    private fun fetchSubscription(url: String) {
        viewModelScope.launch {
            _isImportingSub.value = true
            val result = NetworkProbe.fetchSubscription(url)
            _isImportingSub.value = false
            result.fold(
                onSuccess = { list ->
                    profileRepo.replaceSubscriptionProfiles(url, list)
                    val first = profileRepo.getAllOnce().firstOrNull()
                    if (first != null && profileRepo.getSelectedProfile() == null) {
                        profileRepo.select(first.id)
                        settingsRepo.setVpnMode(VpnMode.PROXY_CHAIN)
                    }
                    showBanner("${list.size} سرور از لینک اشتراک دریافت شد")
                    testAllProfiles()
                },
                onFailure = { err ->
                    showBanner("خطا در دریافت اشتراک: ${err.message ?: "نامشخص"}")
                }
            )
        }
    }

    fun selectProfile(profile: ProxyProfile) {
        viewModelScope.launch {
            profileRepo.select(profile.id)
            settingsRepo.setVpnMode(VpnMode.PROXY_CHAIN)
            showBanner("سرور ${profile.name} انتخاب و حالت تونل کامل فعال شد")
        }
    }

    fun checkExitIp() {
        if (_isCheckingExitIp.value) return
        viewModelScope.launch {
            _isCheckingExitIp.value = true
            val isConnected = uiState.value.telemetry.state == ConnectionState.Connected
            val result = NetworkProbe.checkExitIp(viaSingBoxProxy = isConnected)
            _isCheckingExitIp.value = false
            result.fold(
                onSuccess = { _exitIpInfo.value = it },
                onFailure = { showBanner("خطا در بررسی IP خروج: ${it.message ?: "نامشخص"}") }
            )
        }
    }

    fun deleteProfile(id: Long) {
        viewModelScope.launch {
            profileRepo.deleteById(id)
        }
    }

    fun testSingleProfile(id: Long, host: String, port: Int) {
        viewModelScope.launch {
            val latency = NetworkProbe.measureTcpLatency(host, port)
            profileRepo.updateLatency(id, latency)
        }
    }

    fun testAllProfiles() {
        viewModelScope.launch {
            val list = profileRepo.getAllOnce()
            if (list.isEmpty()) return@launch
            _isTestingProfiles.value = true
            list.map { p ->
                async(Dispatchers.IO) {
                    val ms = NetworkProbe.measureTcpLatency(p.host, p.port)
                    profileRepo.updateLatency(p.id, ms)
                }
            }.awaitAll()
            _isTestingProfiles.value = false
        }
    }

    fun runCleanIpNetworkScan() {
        if (_isScanningNetwork.value) return
        viewModelScope.launch {
            _isScanningNetwork.value = true
            val initial = NetworkProbe.defaultCleanScanTargets().map { it.copy(isTesting = true) }
            _scanResults.value = initial

            val updated = initial.map { target ->
                async(Dispatchers.IO) {
                    val (tcpMs, tlsMs) = NetworkProbe.measureTlsHandshake(target.host, target.port, target.host)
                    target.copy(
                        tcpLatencyMs = if (tcpMs > 0) tcpMs else null,
                        tlsLatencyMs = if (tlsMs > 0) tlsMs else null,
                        isReachable = tcpMs > 0,
                        isTesting = false
                    )
                }
            }.awaitAll()

            _scanResults.value = updated.sortedWith(
                compareByDescending<ScanEndpointResult> { it.isReachable == true }
                    .thenBy { it.tcpLatencyMs ?: Long.MAX_VALUE }
            )
            _isScanningNetwork.value = false
        }
    }

    fun toggleAppBypass(packageName: String, bypassed: Boolean) {
        viewModelScope.launch {
            settingsRepo.toggleBypassedPackage(packageName, bypassed)
        }
    }

    fun autoBypassIranianApps() {
        viewModelScope.launch {
            val suggested = _installedAppsRaw.value
                .filter { it.isIranianSuggested }
                .map { it.packageName }
            if (suggested.isEmpty()) {
                showBanner("برنامه ایرانی شناخته‌شده‌ای در دستگاه یافت نشد")
            } else {
                settingsRepo.addBypassedPackages(suggested)
                showBanner("${suggested.size} برنامه ایرانی برای عبور مستقیم انتخاب شدند")
            }
        }
    }

    fun clearAllBypassedApps() {
        viewModelScope.launch {
            settingsRepo.clearBypassedPackages()
            showBanner("لیست استثناها پاک شد")
        }
    }

    fun clearLogs() {
        VpnRuntimeState.clearLogs()
    }

    fun dismissBanner() {
        _bannerMessage.value = null
    }

    private fun showBanner(message: String) {
        _bannerMessage.value = message
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            _appsLoading.value = true
            val apps = withContext(Dispatchers.IO) {
                val pm = app.packageManager
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(intent, 0)
                }

                resolveInfos
                    .map { it.activityInfo.applicationInfo }
                    .distinctBy { it.packageName }
                    .filter { it.packageName != app.packageName }
                    .map { appInfo ->
                        val label = pm.getApplicationLabel(appInfo).toString()
                        val pkg = appInfo.packageName
                        InstalledAppInfo(
                            appName = label,
                            packageName = pkg,
                            isIranianSuggested = isLikelyIranianApp(pkg, label),
                            isBypassed = false
                        )
                    }
                    .sortedWith(
                        compareByDescending<InstalledAppInfo> { it.isIranianSuggested }
                            .thenBy { it.appName.lowercase() }
                    )
            }
            _installedAppsRaw.value = apps
            _appsLoading.value = false
        }
    }

    private fun isLikelyIranianApp(packageName: String, label: String): Boolean {
        val pkgLower = packageName.lowercase()
        val labelLower = label.lowercase()
        val keywords = listOf(
            ".ir", "ir.", "snapp", "tapsi", "divar", "digikala", "torob", "basalam",
            "bale", "rubika", "eitaa", "soroush", "shad", "neshan", "balad", "aparat",
            "filimo", "namava", "telewebion", "bank", "mellat", "melli", "tejarat",
            "saderat", "parsian", "pasargad", "saman", "blubank", "wepod", "asanpardakht",
            "bazaar", "myket", "sheypoor", "alibaba", "mci", "irancell", "rightel",
            "اسنپ", "تپسی", "دیوار", "دیجی", "بانک", "بلو", "بله", "ایتا", "روبیکا",
            "نشان", "بلد", "آپارات", "فیلیمو", "بازار", "مایکت"
        )
        return keywords.any { pkgLower.contains(it) || labelLower.contains(it) }
    }

    private data class BaseSnapshot(
        val telemetry: VpnTelemetry,
        val settings: UserSettings,
        val profiles: List<ProxyProfile>,
        val selectedProfile: ProxyProfile?,
        val logs: List<TunnelLogEntry>
    )

    private data class AuxSnapshot(
        val apps: List<InstalledAppInfo>,
        val appsLoading: Boolean,
        val scanResults: List<ScanEndpointResult>,
        val isScanning: Boolean,
        val isTesting: Boolean,
        val isImporting: Boolean,
        val banner: String?,
        val exitIpInfo: NetworkProbe.ExitIpInfo?,
        val isCheckingExitIp: Boolean
    )
}

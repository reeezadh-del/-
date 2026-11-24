package com.example.ui

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.SeparApp
import com.example.ai.AiCoreEngine
import com.example.ai.AiExpertPersona
import com.example.ai.AiUpgradePlan
import com.example.ai.getDeviceAppCheckDebugSecret
import com.example.data.ConfigUriParser
import com.example.data.GitHubVpnManager
import com.example.data.NetworkProbe
import com.example.data.UserSettings
import com.example.data.VpnRuntimeState
import com.example.data.WarpProvisioner
import com.example.model.ConnectionState
import com.example.model.InstalledAppInfo
import com.example.model.ProxyProfile
import com.example.model.ScanEndpointResult
import com.example.model.TunnelLogEntry
import com.example.model.VpnMode
import com.example.model.VpnTelemetry
import com.example.vpn.SeparVpnService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AiChatMessage(
    val id: Long,
    val isUser: Boolean,
    val text: String,
    val personaTitle: String
)

data class GitHubUiState(
    val isBuildingServer: Boolean = false,
    val buildProgressMessage: String = "",
    val liveServer: GitHubVpnManager.GitHubLiveServerResult? = null,
    val isCreatingGist: Boolean = false,
    val gistResult: GitHubVpnManager.GitHubGistResult? = null,
    val isSearchingRepos: Boolean = false,
    val discoveredRepos: List<GitHubVpnManager.GitHubRepoDiscoveryItem> = emptyList(),
    val errorMessage: String? = null
)

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
    val isCheckingExitIp: Boolean = false,
    val isGeneratingWarp: Boolean = false,
    val settingsLoaded: Boolean = false,
    val startupDiagnostic: StartupDiagnosticState = StartupDiagnosticState(),
    val startupDismissed: Boolean = false,
    val selectedAiPersona: AiExpertPersona = AiExpertPersona.VPN_OPTIMIZER,
    val aiMessages: List<AiChatMessage> = emptyList(),
    val aiStreamingText: String = "",
    val isAiLoading: Boolean = false,
    val aiErrorMessage: String? = null,
    val aiAppCheckDebugToken: String? = null,
    val isAiSelfUpgrading: Boolean = false,
    val lastAiUpgradePlan: AiUpgradePlan? = null,
    val github: GitHubUiState = GitHubUiState()
)

enum class StartupActionRecommendation {
    ALREADY_CONNECTED,
    CONNECT_SELECTED_SERVER,
    GENERATE_AND_CONNECT_WARP,
    CONNECT_ANTI_DPI_DOH,
    OFFLINE
}

data class StartupDiagnosticState(
    val isScanning: Boolean = true,
    val isCompleted: Boolean = false,
    val isOnline: Boolean = true,
    val networkTypeLabel: String = "در حال شناسایی شبکه…",
    val directLatencyMs: Long? = null,
    val dohReachable: Boolean = false,
    val instagramBlocked: Boolean? = null,
    val cloudflareEdgeReachable: Boolean = false,
    val fastestProfileName: String? = null,
    val fastestProfileLatencyMs: Long? = null,
    val recommendation: StartupActionRecommendation = StartupActionRecommendation.GENERATE_AND_CONNECT_WARP,
    val summaryMessage: String = "در حال بررسی کیفیت اینترنت و مسیرهای عبور…",
    val activeStepIndex: Int = 0
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
    private val _manualExitIpInfo = MutableStateFlow<NetworkProbe.ExitIpInfo?>(null)
    private val _isCheckingExitIp = MutableStateFlow(false)
    private val _isGeneratingWarp = MutableStateFlow(false)
    private val _startupDiagnostic = MutableStateFlow(StartupDiagnosticState())
    private val _startupDismissed = MutableStateFlow(false)
    private val _selectedAiPersona = MutableStateFlow(AiExpertPersona.VPN_OPTIMIZER)
    private val _aiMessages = MutableStateFlow<List<AiChatMessage>>(emptyList())
    private val _aiStreamingText = MutableStateFlow("")
    private val _isAiLoading = MutableStateFlow(false)
    private val _aiErrorMessage = MutableStateFlow<String?>(null)
    private val _aiAppCheckDebugToken = MutableStateFlow<String?>(null)
    private val _isAiSelfUpgrading = MutableStateFlow(false)
    private val _lastAiUpgradePlan = MutableStateFlow<AiUpgradePlan?>(null)
    private val _githubState = MutableStateFlow(GitHubUiState())
    private var aiChatJob: Job? = null

    private val baseDataFlow = combine(
        VpnRuntimeState.telemetry,
        settingsRepo.settingsFlow,
        profileRepo.allProfiles,
        profileRepo.selectedProfile,
        VpnRuntimeState.logs
    ) { telemetry, settings, profiles, selectedProfile, logs ->
        BaseSnapshot(telemetry, settings, profiles, selectedProfile, logs)
    }

    private val ipStateFlow = combine(
        VpnRuntimeState.exitIpInfo,
        _manualExitIpInfo,
        _isCheckingExitIp,
        _isGeneratingWarp,
        _startupDiagnostic,
        _startupDismissed
    ) { values ->
        val runtimeIp = values[0] as NetworkProbe.ExitIpInfo?
        val manualIp = values[1] as NetworkProbe.ExitIpInfo?
        val checking = values[2] as Boolean
        val generatingWarp = values[3] as Boolean
        val startupDiag = values[4] as StartupDiagnosticState
        val startupDismissed = values[5] as Boolean
        IpAndStartupSnapshot(
            exitIp = runtimeIp ?: manualIp,
            isCheckingExitIp = checking,
            isGeneratingWarp = generatingWarp,
            startupDiagnostic = startupDiag,
            startupDismissed = startupDismissed
        )
    }

    private val aiStatusSubFlow = combine(
        _isAiLoading,
        _aiErrorMessage,
        _aiAppCheckDebugToken,
        _isAiSelfUpgrading,
        _lastAiUpgradePlan
    ) { loading, error, debugToken, upgrading, plan ->
        AiStatusSnapshot(loading, error, debugToken, upgrading, plan)
    }

    private val aiStateFlow = combine(
        _selectedAiPersona,
        _aiMessages,
        _aiStreamingText,
        aiStatusSubFlow
    ) { persona, messages, streaming, status ->
        AiSnapshot(
            selectedPersona = persona,
            messages = messages,
            streamingText = streaming,
            isLoading = status.isLoading,
            errorMessage = status.errorMessage,
            appCheckDebugToken = status.appCheckDebugToken,
            isSelfUpgrading = status.isSelfUpgrading,
            lastUpgradePlan = status.lastUpgradePlan
        )
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
        val ipSnap = values[7] as IpAndStartupSnapshot
        AuxSnapshot(
            apps = values[0] as List<InstalledAppInfo>,
            appsLoading = values[1] as Boolean,
            scanResults = values[2] as List<ScanEndpointResult>,
            isScanning = values[3] as Boolean,
            isTesting = values[4] as Boolean,
            isImporting = values[5] as Boolean,
            banner = values[6] as String?,
            exitIpInfo = ipSnap.exitIp,
            isCheckingExitIp = ipSnap.isCheckingExitIp,
            isGeneratingWarp = ipSnap.isGeneratingWarp,
            startupDiagnostic = ipSnap.startupDiagnostic,
            startupDismissed = ipSnap.startupDismissed
        )
    }

    val uiState: StateFlow<MainUiState> = combine(
        baseDataFlow,
        auxStateFlow,
        aiStateFlow,
        _githubState
    ) { base, aux, ai, github ->
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
            isCheckingExitIp = aux.isCheckingExitIp,
            isGeneratingWarp = aux.isGeneratingWarp,
            settingsLoaded = true,
            startupDiagnostic = aux.startupDiagnostic,
            startupDismissed = aux.startupDismissed,
            selectedAiPersona = ai.selectedPersona,
            aiMessages = ai.messages,
            aiStreamingText = ai.streamingText,
            isAiLoading = ai.isLoading,
            aiErrorMessage = ai.errorMessage,
            aiAppCheckDebugToken = ai.appCheckDebugToken,
            isAiSelfUpgrading = ai.isSelfUpgrading,
            lastAiUpgradePlan = ai.lastUpgradePlan,
            github = github
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    init {
        loadInstalledApps()
        runStartupInternetDiagnostic()
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

    fun setMultiplexEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setMultiplexEnabled(enabled)
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
                showBanner("فرمت ورودی معتبر نبود (پشتیبانی از VLESS, VMess, Trojan, SS, Hysteria2, TUIC, WireGuard, SSH, AnyTLS, SOCKS5)")
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
            com.example.model.ProxyProtocol.HYSTERIA2 -> "hy2"
            com.example.model.ProxyProtocol.HYSTERIA -> "hysteria"
            com.example.model.ProxyProtocol.TUIC -> "tuic"
            com.example.model.ProxyProtocol.WIREGUARD -> "wireguard"
            com.example.model.ProxyProtocol.SHADOWTLS -> "shadowtls"
            com.example.model.ProxyProtocol.NAIVE -> "naive+https"
            com.example.model.ProxyProtocol.SSH -> "ssh"
            com.example.model.ProxyProtocol.ANYTLS -> "anytls"
        }
        val creds = if (username.isNotBlank()) "$username:$password@" else if (password.isNotBlank()) "$password@" else ""
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

    fun generateAndSelectWarp(onReadyToConnect: (() -> Unit)? = null) {
        if (_isGeneratingWarp.value) return
        viewModelScope.launch {
            _isGeneratingWarp.value = true
            val isConnected = uiState.value.telemetry.state == ConnectionState.Connected
            val result = WarpProvisioner.registerWarpAccount(useLocalProxyIfConnected = isConnected)
            _isGeneratingWarp.value = false
            result.fold(
                onSuccess = { warpProfile ->
                    val id = profileRepo.insert(warpProfile)
                    profileRepo.select(id)
                    settingsRepo.setVpnMode(VpnMode.PROXY_CHAIN)
                    showBanner("اکانت رایگان Cloudflare WARP روی آی‌پی تمیز ${warpProfile.host} ساخته و انتخاب شد")
                    onReadyToConnect?.invoke()
                },
                onFailure = { err ->
                    showBanner("خطا در ساخت اکانت WARP: ${err.message ?: "خطای شبکه"}")
                }
            )
        }
    }

    fun importPublicCommunityFeed(feed: WarpProvisioner.PublicFeedPreset) {
        fetchSubscription(feed.url)
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
                onSuccess = {
                    _manualExitIpInfo.value = it
                    if (isConnected) {
                        VpnRuntimeState.setExitIpInfo(it)
                    }
                },
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

    fun dismissOnboarding() {
        viewModelScope.launch {
            settingsRepo.setOnboardingSeen(true)
        }
    }

    fun dismissStartupLoading(markOnboardingSeen: Boolean = false) {
        _startupDismissed.value = true
        if (markOnboardingSeen) {
            viewModelScope.launch {
                settingsRepo.setOnboardingSeen(true)
            }
        }
    }

    fun selectAiPersona(persona: AiExpertPersona) {
        _selectedAiPersona.value = persona
        _aiErrorMessage.value = null
        _aiAppCheckDebugToken.value = null
    }

    fun dismissAiError() {
        _aiErrorMessage.value = null
        _aiAppCheckDebugToken.value = null
    }

    fun clearAiChat() {
        aiChatJob?.cancel()
        _aiMessages.value = emptyList()
        _aiStreamingText.value = ""
        _isAiLoading.value = false
        _aiErrorMessage.value = null
        _aiAppCheckDebugToken.value = null
    }

    fun sendAiChatMessage(rawPrompt: String) {
        val prompt = rawPrompt.trim()
        if (prompt.isEmpty() || _isAiLoading.value) return

        val persona = _selectedAiPersona.value
        val historySnapshot = _aiMessages.value.map { it.isUser to it.text }
        val userMsg = AiChatMessage(
            id = System.nanoTime(),
            isUser = true,
            text = prompt,
            personaTitle = persona.titleFa
        )
        _aiMessages.update { it + userMsg }
        _aiStreamingText.value = ""
        _isAiLoading.value = true
        _aiErrorMessage.value = null
        _aiAppCheckDebugToken.value = null

        aiChatJob?.cancel()
        aiChatJob = viewModelScope.launch {
            val acc = StringBuilder()
            try {
                AiCoreEngine.streamChat(
                    context = getApplication(),
                    persona = persona,
                    historyPairs = historySnapshot,
                    userPrompt = prompt
                ).collect { chunk ->
                    acc.append(chunk)
                    _aiStreamingText.value = acc.toString()
                }
                val finalAnswer = acc.toString().trim()
                if (finalAnswer.isNotEmpty()) {
                    _aiMessages.update {
                        it + AiChatMessage(
                            id = System.nanoTime(),
                            isUser = false,
                            text = finalAnswer,
                            personaTitle = persona.titleFa
                        )
                    }
                } else {
                    _aiErrorMessage.value = "پاسخی از هسته هوش مصنوعی دریافت نشد."
                }
            } catch (e: TimeoutCancellationException) {
                Log.e("MainViewModel", "AI chat timed out", e)
                _aiErrorMessage.value = "زمان پاسخ‌گویی هوش مصنوعی به پایان رسید. اتصال اینترنت یا VPN را بررسی کنید."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MainViewModel", "AI chat failed", e)
                _aiErrorMessage.value = AiCoreEngine.formatUserError(e, getApplication())
                _aiAppCheckDebugToken.value = if (AiCoreEngine.isAppCheckError(e)) {
                    getDeviceAppCheckDebugSecret(getApplication())
                } else {
                    null
                }
            } finally {
                _aiStreamingText.value = ""
                _isAiLoading.value = false
            }
        }
    }

    fun runAiSelfUpgrade() {
        if (_isAiSelfUpgrading.value) return
        viewModelScope.launch {
            _isAiSelfUpgrading.value = true
            _aiErrorMessage.value = null
            _aiAppCheckDebugToken.value = null
            val current = uiState.value
            val diag = current.startupDiagnostic
            val recentLogs = current.logs.takeLast(12).joinToString("\n") { "[${it.tag}] ${it.message}" }
            val telemetryPrompt = """
                Analyze this Android VPN client telemetry and return an optimized self-upgrade JSON plan:
                - Network Type: ${diag.networkTypeLabel}
                - Direct Latency (ms): ${diag.directLatencyMs ?: "unknown"}
                - DoH Reachable: ${diag.dohReachable}
                - Cloudflare Edge Reachable: ${diag.cloudflareEdgeReachable}
                - External Censorship Detected: ${diag.instagramBlocked ?: "unknown"}
                - Saved Server Profiles Count: ${current.profiles.size}
                - Current VpnMode: ${current.settings.vpnMode.name}
                - Current TLS Fragment: enabled=${current.settings.tlsFragmentEnabled}, size=${current.settings.fragmentSizeBytes}, delay=${current.settings.fragmentDelayMs}
                - Current Multiplex: ${current.settings.multiplexEnabled}
                - Current DNS Preset: ${current.settings.dnsPresetId}
                - Connection State: ${current.telemetry.state}
                - Recent Core Logs:
                $recentLogs
            """.trimIndent()

            val result = AiCoreEngine.generateSelfUpgradePlan(getApplication(), telemetryPrompt)
            _isAiSelfUpgrading.value = false
            result.fold(
                onSuccess = { plan ->
                    val targetMode = VpnMode.entries.firstOrNull { it.name == plan.recommendedMode }
                        ?: VpnMode.AI_AUTO_DISCOVERY
                    settingsRepo.setVpnMode(targetMode)
                    settingsRepo.setTlsFragmentEnabled(true)
                    settingsRepo.setFragmentParams(
                        plan.recommendedFragmentSize,
                        plan.recommendedFragmentDelayMs
                    )
                    settingsRepo.setMultiplexEnabled(plan.recommendedMultiplex)
                    settingsRepo.setDnsPreset(plan.recommendedDnsId)

                    _lastAiUpgradePlan.value = plan
                    VpnRuntimeState.appendLog(
                        "AI-Upgrade",
                        "خودارتقایی هسته VPN اعمال شد: ${targetMode.name} • Fragment ${plan.recommendedFragmentSize}B/${plan.recommendedFragmentDelayMs}ms • DNS=${plan.recommendedDnsId}"
                    )
                    showBanner("هسته VPN توسط هوش مصنوعی تحلیل و ارتقا یافت")
                },
                onFailure = { err ->
                    val msg = AiCoreEngine.formatUserError(err, getApplication())
                    _aiErrorMessage.value = msg
                    _aiAppCheckDebugToken.value = if (AiCoreEngine.isAppCheckError(err)) {
                        getDeviceAppCheckDebugSecret(getApplication())
                    } else {
                        null
                    }
                }
            )
        }
    }

    fun prepareAiAutoDiscoveryMode() {
        viewModelScope.launch {
            settingsRepo.setVpnMode(VpnMode.AI_AUTO_DISCOVERY)
            showBanner("حالت «هوش مصنوعی خودارتقاگر» فعال شد")
        }
    }

    fun dismissGitHubError() {
        _githubState.update { it.copy(errorMessage = null) }
    }

    fun saveGitHubCredentials(token: String, repo: String) {
        viewModelScope.launch {
            settingsRepo.setGitHubCredentials(token, repo)
        }
    }

    fun deployAndStartGitHubVpnServer(
        token: String,
        repo: String,
        protocol: GitHubVpnManager.GitHubServerProtocol,
        cleanCfIp: String
    ) {
        if (_githubState.value.isBuildingServer) return
        viewModelScope.launch {
            settingsRepo.setGitHubCredentials(token, repo)
            _githubState.update {
                it.copy(
                    isBuildingServer = true,
                    buildProgressMessage = "در حال اتصال به GitHub API…",
                    errorMessage = null
                )
            }
            val isConnected = uiState.value.telemetry.state == ConnectionState.Connected
            val result = GitHubVpnManager.deployAndStartGitHubServer(
                token = token,
                repoInput = repo,
                protocol = protocol,
                cleanCfIp = cleanCfIp,
                useLocalProxy = isConnected,
                onProgress = { msg ->
                    _githubState.update { it.copy(buildProgressMessage = msg) }
                }
            )
            result.fold(
                onSuccess = { live ->
                    settingsRepo.setGitHubCredentials(token, live.repoFullName)
                    if (live.profiles.isNotEmpty()) {
                        profileRepo.replaceSubscriptionProfiles(live.rawSubscriptionUrl, live.profiles)
                        val saved = profileRepo.getAllOnce()
                            .firstOrNull { it.rawUri == live.profiles.first().rawUri }
                        if (saved != null) {
                            profileRepo.select(saved.id)
                            settingsRepo.setVpnMode(VpnMode.PROXY_CHAIN)
                        }
                    }
                    _githubState.update {
                        it.copy(
                            isBuildingServer = false,
                            buildProgressMessage = "سرور گیت‌هاب روشن و آماده اتصال است (${live.tunnelHost})",
                            liveServer = live
                        )
                    }
                    VpnRuntimeState.appendLog(
                        "GitHub-VPN",
                        "سرور گیت‌هاب ساخته و انتخاب شد: ${live.repoFullName} (${live.tunnelHost})"
                    )
                    showBanner("سرور VPN گیت‌هاب ساخته، اضافه و انتخاب شد!")
                },
                onFailure = { err ->
                    val msg = err.message ?: "خطا در ساخت سرور گیت‌هاب"
                    _githubState.update {
                        it.copy(
                            isBuildingServer = false,
                            buildProgressMessage = "",
                            errorMessage = msg
                        )
                    }
                    showBanner(msg)
                }
            )
        }
    }

    fun fetchExistingGitHubLiveServer(token: String, repo: String) {
        if (_githubState.value.isBuildingServer) return
        viewModelScope.launch {
            settingsRepo.setGitHubCredentials(token, repo)
            _githubState.update {
                it.copy(
                    isBuildingServer = true,
                    buildProgressMessage = "در حال دریافت سرور فعال از مخزن گیت‌هاب…",
                    errorMessage = null
                )
            }
            val isConnected = uiState.value.telemetry.state == ConnectionState.Connected
            val result = GitHubVpnManager.fetchExistingLiveServer(
                token = token,
                repoInput = repo,
                useLocalProxy = isConnected
            )
            result.fold(
                onSuccess = { live ->
                    if (live.profiles.isNotEmpty()) {
                        profileRepo.replaceSubscriptionProfiles(live.rawSubscriptionUrl, live.profiles)
                        val saved = profileRepo.getAllOnce()
                            .firstOrNull { it.rawUri == live.profiles.first().rawUri }
                        if (saved != null) {
                            profileRepo.select(saved.id)
                            settingsRepo.setVpnMode(VpnMode.PROXY_CHAIN)
                        }
                    }
                    _githubState.update {
                        it.copy(
                            isBuildingServer = false,
                            buildProgressMessage = "سرور گیت‌هاب دریافت و انتخاب شد (${live.tunnelHost})",
                            liveServer = live
                        )
                    }
                    showBanner("سرور فعال گیت‌هاب وارد لیست و انتخاب شد")
                },
                onFailure = { err ->
                    val msg = err.message ?: "سرور فعالی در گیت‌هاب یافت نشد"
                    _githubState.update {
                        it.copy(
                            isBuildingServer = false,
                            buildProgressMessage = "",
                            errorMessage = msg
                        )
                    }
                    showBanner(msg)
                }
            )
        }
    }

    fun stopGitHubVpnServer(token: String) {
        val live = _githubState.value.liveServer ?: return
        val runId = live.runId ?: return
        viewModelScope.launch {
            val isConnected = uiState.value.telemetry.state == ConnectionState.Connected
            val res = GitHubVpnManager.stopGitHubServerRun(
                token = token,
                repoFullName = live.repoFullName,
                runId = runId,
                useLocalProxy = isConnected
            )
            res.fold(
                onSuccess = {
                    _githubState.update {
                        it.copy(
                            liveServer = null,
                            buildProgressMessage = "سرور گیت‌هاب (Run #$runId) متوقف شد"
                        )
                    }
                    showBanner("سرور گیت‌هاب متوقف شد")
                },
                onFailure = { err ->
                    showBanner(err.message ?: "خطا در توقف سرور گیت‌هاب")
                }
            )
        }
    }

    fun createOrUpdateGitHubGist(token: String, includeFreshWarp: Boolean) {
        if (_githubState.value.isCreatingGist) return
        viewModelScope.launch {
            val currentSettings = uiState.value.settings
            settingsRepo.setGitHubCredentials(token, currentSettings.githubRepo)
            _githubState.update { it.copy(isCreatingGist = true, errorMessage = null) }
            val isConnected = uiState.value.telemetry.state == ConnectionState.Connected
            val result = GitHubVpnManager.createOrUpdateGistSubscription(
                token = token,
                existingGistId = currentSettings.githubGistId,
                profiles = uiState.value.profiles,
                includeFreshWarp = includeFreshWarp,
                useLocalProxy = isConnected
            )
            result.fold(
                onSuccess = { gist ->
                    settingsRepo.setGitHubGistId(gist.gistId)
                    _githubState.update {
                        it.copy(
                            isCreatingGist = false,
                            gistResult = gist
                        )
                    }
                    showBanner("لینک اشتراک GitHub Gist با ${gist.serverCount} سرور ساخته شد")
                },
                onFailure = { err ->
                    val msg = err.message ?: "خطا در ساخت اشتراک Gist"
                    _githubState.update { it.copy(isCreatingGist = false, errorMessage = msg) }
                    showBanner(msg)
                }
            )
        }
    }

    fun searchAndImportFromGitHub(query: String) {
        if (_githubState.value.isSearchingRepos) return
        viewModelScope.launch {
            _githubState.update { it.copy(isSearchingRepos = true, errorMessage = null) }
            val isConnected = uiState.value.telemetry.state == ConnectionState.Connected
            val result = GitHubVpnManager.searchAndCollectGitHubServers(
                query = query,
                useLocalProxy = isConnected
            )
            result.fold(
                onSuccess = { repos ->
                    val allCollected = repos.flatMap { it.profiles }.distinctBy { it.rawUri }
                    if (allCollected.isNotEmpty()) {
                        profileRepo.insertAll(allCollected)
                        val fastest = allCollected.minByOrNull { it.latencyMs ?: Long.MAX_VALUE }
                        if (fastest != null) {
                            profileRepo.getAllOnce().firstOrNull { it.rawUri == fastest.rawUri }?.let {
                                profileRepo.select(it.id)
                                settingsRepo.setVpnMode(VpnMode.PROXY_CHAIN)
                            }
                        }
                    }
                    _githubState.update {
                        it.copy(
                            isSearchingRepos = false,
                            discoveredRepos = repos
                        )
                    }
                    showBanner("${allCollected.size} سرور سالم از ${repos.size} مخزن گیت‌هاب اضافه شد")
                },
                onFailure = { err ->
                    val msg = err.message ?: "خطا در جستجوی مخازن گیت‌هاب"
                    _githubState.update { it.copy(isSearchingRepos = false, errorMessage = msg) }
                    showBanner(msg)
                }
            )
        }
    }

    fun runStartupInternetDiagnostic() {
        viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            _startupDiagnostic.value = StartupDiagnosticState(
                isScanning = true,
                isCompleted = false,
                activeStepIndex = 0,
                summaryMessage = "بررسی نوع اینترنت و کیفیت شبکه…"
            )

            val stepJob = launch {
                kotlinx.coroutines.delay(520L)
                _startupDiagnostic.update {
                    if (it.isScanning) it.copy(
                        activeStepIndex = 1,
                        summaryMessage = "بررسی DNS امن و محدودیت‌های اینترنت…"
                    ) else it
                }
                kotlinx.coroutines.delay(560L)
                _startupDiagnostic.update {
                    if (it.isScanning) it.copy(
                        activeStepIndex = 2,
                        summaryMessage = "انتخاب سریع‌ترین سرور و مسیر اتصال VPN…"
                    ) else it
                }
            }

            val netCheck = runCatching {
                NetworkProbe.checkStartupInternetStatus(app)
            }.getOrElse {
                NetworkProbe.StartupNetworkCheckResult(
                    isOnline = false,
                    networkTypeLabel = "خطا در بررسی شبکه",
                    isVpnTransportActive = false,
                    dohReachable = false,
                    directLatencyMs = null,
                    instagramBlocked = null,
                    cloudflareEdgeReachable = false
                )
            }

            val profiles = runCatching { profileRepo.getAllOnce() }.getOrDefault(emptyList())
            var fastestProfile: ProxyProfile? = null
            var fastestMs: Long? = null

            if (netCheck.isOnline && profiles.isNotEmpty()) {
                val probed = profiles.take(6).map { p ->
                    async(Dispatchers.IO) {
                        val ms = NetworkProbe.measureTcpLatency(p.host, p.port, timeoutMs = 2000)
                        if (ms > 0L) profileRepo.updateLatency(p.id, ms)
                        p to ms
                    }
                }.awaitAll()

                val bestPair = probed.filter { it.second > 0L }.minByOrNull { it.second }
                if (bestPair != null) {
                    fastestProfile = bestPair.first
                    fastestMs = bestPair.second
                    val currentSelected = profileRepo.getSelectedProfile()
                    val currentSelectedReachable = currentSelected != null &&
                        probed.any { it.first.id == currentSelected.id && it.second > 0L }
                    if (!currentSelectedReachable) {
                        profileRepo.select(fastestProfile.id)
                    }
                }
            }

            val elapsed = System.currentTimeMillis() - startedAt
            if (elapsed < 1450L) {
                kotlinx.coroutines.delay(1450L - elapsed)
            }
            stepJob.cancel()

            val isAlreadyVpn = VpnRuntimeState.telemetry.value.state == ConnectionState.Connected
            val recommendation = when {
                isAlreadyVpn -> StartupActionRecommendation.ALREADY_CONNECTED
                !netCheck.isOnline -> StartupActionRecommendation.OFFLINE
                fastestProfile != null -> StartupActionRecommendation.CONNECT_SELECTED_SERVER
                netCheck.cloudflareEdgeReachable -> StartupActionRecommendation.GENERATE_AND_CONNECT_WARP
                else -> StartupActionRecommendation.CONNECT_ANTI_DPI_DOH
            }

            val summary = when (recommendation) {
                StartupActionRecommendation.ALREADY_CONNECTED ->
                    "تونل سپر نت هم‌اکنون فعال و متصل است."
                StartupActionRecommendation.OFFLINE ->
                    "اینترنت دستگاه برقرار نیست. وای‌فای یا دیتای موبایل را بررسی کنید."
                StartupActionRecommendation.CONNECT_SELECTED_SERVER ->
                    "سریع‌ترین سرور با پینگ ${fastestMs}ms آماده اتصال است: ${fastestProfile?.name}"
                StartupActionRecommendation.GENERATE_AND_CONNECT_WARP ->
                    if (netCheck.instagramBlocked == true) {
                        "اینترنت دارای محدودیت است؛ آماده کشف خودکار و اتصال فوری به VPN."
                    } else {
                        "اینترنت پایدار است (${netCheck.directLatencyMs ?: "--"}ms)؛ آماده اتصال با هسته هوشمند."
                    }
                StartupActionRecommendation.CONNECT_ANTI_DPI_DOH ->
                    "دسترسی مستقیم محدود است؛ هسته هوشمند تمام روش‌های عبور را خودکار آزمایش می‌کند."
            }

            _startupDiagnostic.value = StartupDiagnosticState(
                isScanning = false,
                isCompleted = true,
                isOnline = netCheck.isOnline,
                networkTypeLabel = netCheck.networkTypeLabel,
                directLatencyMs = netCheck.directLatencyMs,
                dohReachable = netCheck.dohReachable,
                instagramBlocked = netCheck.instagramBlocked,
                cloudflareEdgeReachable = netCheck.cloudflareEdgeReachable,
                fastestProfileName = fastestProfile?.name,
                fastestProfileLatencyMs = fastestMs,
                recommendation = recommendation,
                summaryMessage = summary,
                activeStepIndex = 3
            )
        }
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

    private data class IpAndStartupSnapshot(
        val exitIp: NetworkProbe.ExitIpInfo?,
        val isCheckingExitIp: Boolean,
        val isGeneratingWarp: Boolean,
        val startupDiagnostic: StartupDiagnosticState,
        val startupDismissed: Boolean
    )

    private data class AiStatusSnapshot(
        val isLoading: Boolean,
        val errorMessage: String?,
        val appCheckDebugToken: String?,
        val isSelfUpgrading: Boolean,
        val lastUpgradePlan: AiUpgradePlan?
    )

    private data class AiSnapshot(
        val selectedPersona: AiExpertPersona,
        val messages: List<AiChatMessage>,
        val streamingText: String,
        val isLoading: Boolean,
        val errorMessage: String?,
        val appCheckDebugToken: String?,
        val isSelfUpgrading: Boolean,
        val lastUpgradePlan: AiUpgradePlan?
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
        val isCheckingExitIp: Boolean,
        val isGeneratingWarp: Boolean,
        val startupDiagnostic: StartupDiagnosticState,
        val startupDismissed: Boolean
    )
}

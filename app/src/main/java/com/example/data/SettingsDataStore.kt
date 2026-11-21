package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.DnsPreset
import com.example.model.VpnMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "separ_settings")

data class UserSettings(
    val vpnMode: VpnMode = VpnMode.AI_AUTO_DISCOVERY,
    val dnsPresetId: String = "cloudflare",
    val customDohUrl: String = "",
    val tlsFragmentEnabled: Boolean = true,
    val fragmentSizeBytes: Int = 24,
    val fragmentDelayMs: Int = 12,
    val multiplexEnabled: Boolean = false,
    val bypassedPackages: Set<String> = emptySet(),
    val onboardingSeen: Boolean = false,
    val githubToken: String = "",
    val githubRepo: String = "",
    val githubGistId: String = ""
) {
    fun resolveActiveDnsUrl(): String {
        return if (dnsPresetId == "custom" && customDohUrl.startsWith("https://")) {
            customDohUrl.trim()
        } else {
            DnsPreset.findById(dnsPresetId).dohUrl
        }
    }

    fun resolveActiveDnsLabel(): String {
        return if (dnsPresetId == "custom" && customDohUrl.startsWith("https://")) {
            "DNS سفارشی (DoH)"
        } else {
            DnsPreset.findById(dnsPresetId).nameFa
        }
    }

    fun resolveBootstrapIp(): String {
        return if (dnsPresetId == "custom") "1.1.1.1" else DnsPreset.findById(dnsPresetId).bootstrapIp
    }
}

class SettingsRepository(private val context: Context) {
    private object Keys {
        val VPN_MODE = stringPreferencesKey("vpn_mode")
        val DNS_PRESET_ID = stringPreferencesKey("dns_preset_id")
        val CUSTOM_DOH_URL = stringPreferencesKey("custom_doh_url")
        val TLS_FRAGMENT_ENABLED = booleanPreferencesKey("tls_fragment_enabled")
        val FRAGMENT_SIZE_BYTES = intPreferencesKey("fragment_size_bytes")
        val FRAGMENT_DELAY_MS = intPreferencesKey("fragment_delay_ms")
        val MULTIPLEX_ENABLED = booleanPreferencesKey("multiplex_enabled")
        val BYPASSED_PACKAGES = stringSetPreferencesKey("bypassed_packages")
        val ONBOARDING_SEEN = booleanPreferencesKey("onboarding_seen")
        val GITHUB_TOKEN = stringPreferencesKey("github_token")
        val GITHUB_REPO = stringPreferencesKey("github_repo")
        val GITHUB_GIST_ID = stringPreferencesKey("github_gist_id")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        val modeName = prefs[Keys.VPN_MODE] ?: VpnMode.AI_AUTO_DISCOVERY.name
        UserSettings(
            vpnMode = VpnMode.entries.firstOrNull { it.name == modeName } ?: VpnMode.AI_AUTO_DISCOVERY,
            dnsPresetId = prefs[Keys.DNS_PRESET_ID] ?: "cloudflare",
            customDohUrl = prefs[Keys.CUSTOM_DOH_URL] ?: "",
            tlsFragmentEnabled = prefs[Keys.TLS_FRAGMENT_ENABLED] ?: true,
            fragmentSizeBytes = prefs[Keys.FRAGMENT_SIZE_BYTES] ?: 24,
            fragmentDelayMs = prefs[Keys.FRAGMENT_DELAY_MS] ?: 12,
            multiplexEnabled = prefs[Keys.MULTIPLEX_ENABLED] ?: false,
            bypassedPackages = prefs[Keys.BYPASSED_PACKAGES] ?: emptySet(),
            onboardingSeen = prefs[Keys.ONBOARDING_SEEN] ?: false,
            githubToken = prefs[Keys.GITHUB_TOKEN] ?: "",
            githubRepo = prefs[Keys.GITHUB_REPO] ?: "",
            githubGistId = prefs[Keys.GITHUB_GIST_ID] ?: ""
        )
    }

    suspend fun getSnapshot(): UserSettings = settingsFlow.first()

    suspend fun setVpnMode(mode: VpnMode) {
        context.dataStore.edit { it[Keys.VPN_MODE] = mode.name }
    }

    suspend fun setDnsPreset(presetId: String) {
        context.dataStore.edit { it[Keys.DNS_PRESET_ID] = presetId }
    }

    suspend fun setCustomDohUrl(url: String) {
        context.dataStore.edit {
            it[Keys.CUSTOM_DOH_URL] = url.trim()
            if (url.trim().startsWith("https://")) {
                it[Keys.DNS_PRESET_ID] = "custom"
            }
        }
    }

    suspend fun setTlsFragmentEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TLS_FRAGMENT_ENABLED] = enabled }
    }

    suspend fun setMultiplexEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MULTIPLEX_ENABLED] = enabled }
    }

    suspend fun setFragmentParams(sizeBytes: Int, delayMs: Int) {
        context.dataStore.edit {
            it[Keys.FRAGMENT_SIZE_BYTES] = sizeBytes.coerceIn(8, 128)
            it[Keys.FRAGMENT_DELAY_MS] = delayMs.coerceIn(0, 60)
        }
    }

    suspend fun toggleBypassedPackage(packageName: String, bypassed: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.BYPASSED_PACKAGES]?.toMutableSet() ?: mutableSetOf()
            if (bypassed) current.add(packageName) else current.remove(packageName)
            prefs[Keys.BYPASSED_PACKAGES] = current
        }
    }

    suspend fun addBypassedPackages(packages: Collection<String>) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.BYPASSED_PACKAGES]?.toMutableSet() ?: mutableSetOf()
            current.addAll(packages)
            prefs[Keys.BYPASSED_PACKAGES] = current
        }
    }

    suspend fun clearBypassedPackages() {
        context.dataStore.edit { it[Keys.BYPASSED_PACKAGES] = emptySet() }
    }

    suspend fun setOnboardingSeen(seen: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_SEEN] = seen }
    }

    suspend fun setGitHubCredentials(token: String, repo: String) {
        context.dataStore.edit {
            it[Keys.GITHUB_TOKEN] = token.trim()
            it[Keys.GITHUB_REPO] = repo.trim()
        }
    }

    suspend fun setGitHubGistId(gistId: String) {
        context.dataStore.edit { it[Keys.GITHUB_GIST_ID] = gistId.trim() }
    }
}

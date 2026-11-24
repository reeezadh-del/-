package com.example.data

import com.example.model.ConnectionState
import com.example.model.TunnelLogEntry
import com.example.model.VpnMode
import com.example.model.VpnTelemetry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object VpnRuntimeState {
    private const val MAX_LOGS = 120

    private val _telemetry = MutableStateFlow(VpnTelemetry())
    val telemetry: StateFlow<VpnTelemetry> = _telemetry.asStateFlow()

    private val _logs = MutableStateFlow<List<TunnelLogEntry>>(emptyList())
    val logs: StateFlow<List<TunnelLogEntry>> = _logs.asStateFlow()

    private val _exitIpInfo = MutableStateFlow<NetworkProbe.ExitIpInfo?>(null)
    val exitIpInfo: StateFlow<NetworkProbe.ExitIpInfo?> = _exitIpInfo.asStateFlow()

    fun setExitIpInfo(info: NetworkProbe.ExitIpInfo?) {
        _exitIpInfo.value = info
    }

    fun updateState(state: ConnectionState, mode: VpnMode? = null) {
        if (state == ConnectionState.Disconnected || state is ConnectionState.Error) {
            _exitIpInfo.value = null
        }
        _telemetry.update { current ->
            val resetTimes = state == ConnectionState.Disconnected || state is ConnectionState.Error
            current.copy(
                state = state,
                activeMode = mode ?: current.activeMode,
                connectedSinceMs = if (state == ConnectionState.Connected && current.connectedSinceMs == 0L) {
                    System.currentTimeMillis()
                } else if (resetTimes) {
                    0L
                } else {
                    current.connectedSinceMs
                },
                rxBytes = if (resetTimes) 0L else current.rxBytes,
                txBytes = if (resetTimes) 0L else current.txBytes,
                rxSpeedBps = if (resetTimes) 0L else current.rxSpeedBps,
                txSpeedBps = if (resetTimes) 0L else current.txSpeedBps,
                dnsQueriesResolved = if (resetTimes) 0 else current.dnsQueriesResolved,
                fragmentedConnections = if (resetTimes) 0 else current.fragmentedConnections,
                localProxyPort = if (resetTimes) 0 else current.localProxyPort,
                activeServerLabel = if (resetTimes) null else current.activeServerLabel
            )
        }
    }

    fun updateSessionInfo(
        activeDnsLabel: String,
        localProxyPort: Int,
        activeServerLabel: String?
    ) {
        _telemetry.update {
            it.copy(
                activeDnsLabel = activeDnsLabel,
                localProxyPort = localProxyPort,
                activeServerLabel = activeServerLabel
            )
        }
    }

    fun addBytes(rxDelta: Long, txDelta: Long) {
        _telemetry.update {
            it.copy(
                rxBytes = (it.rxBytes + rxDelta.coerceAtLeast(0L)),
                txBytes = (it.txBytes + txDelta.coerceAtLeast(0L))
            )
        }
    }

    fun updateSpeeds(rxBps: Long, txBps: Long) {
        _telemetry.update {
            it.copy(
                rxSpeedBps = rxBps.coerceAtLeast(0L),
                txSpeedBps = txBps.coerceAtLeast(0L)
            )
        }
    }

    fun setTrafficTotals(rxTotal: Long, txTotal: Long, rxSpeed: Long, txSpeed: Long) {
        _telemetry.update {
            it.copy(
                rxBytes = rxTotal.coerceAtLeast(0L),
                txBytes = txTotal.coerceAtLeast(0L),
                rxSpeedBps = rxSpeed.coerceAtLeast(0L),
                txSpeedBps = txSpeed.coerceAtLeast(0L)
            )
        }
    }

    fun incrementDnsQueries() {
        _telemetry.update { it.copy(dnsQueriesResolved = it.dnsQueriesResolved + 1) }
    }

    fun incrementFragmentedConnections() {
        _telemetry.update { it.copy(fragmentedConnections = it.fragmentedConnections + 1) }
    }

    fun appendLog(tag: String, message: String, isError: Boolean = false) {
        val entry = TunnelLogEntry(
            timestampMs = System.currentTimeMillis(),
            tag = tag,
            message = message,
            isError = isError
        )
        _logs.update { current ->
            (listOf(entry) + current).take(MAX_LOGS)
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}

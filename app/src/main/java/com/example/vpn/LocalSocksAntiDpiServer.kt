package com.example.vpn

import android.net.VpnService
import com.example.data.VpnRuntimeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong

class LocalSocksAntiDpiServer(
    private val vpnService: VpnService,
    private val dohResolver: DohResolver,
    private val tlsFragmentEnabled: Boolean,
    private val fragmentSizeBytes: Int,
    private val fragmentDelayMs: Int
) {
    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var acceptJob: Job? = null
    private val rxBytesCounter = AtomicLong(0L)
    private val txBytesCounter = AtomicLong(0L)

    fun getTrafficSnapshot(): Pair<Long, Long> = rxBytesCounter.get() to txBytesCounter.get()

    fun start(): Int {
        val ss = ServerSocket(0, 128, InetAddress.getByName("127.0.0.1"))
        serverSocket = ss
        acceptJob = scope.launch {
            VpnRuntimeState.appendLog("DPI", "موتور محلی ضد فیلتر SOCKS5 روی پورت ${ss.localPort} آماده شد")
            while (isActive && !ss.isClosed) {
                try {
                    val client = ss.accept()
                    launch { handleSocks5Client(client) }
                } catch (_: Exception) {
                    break
                }
            }
        }
        return ss.localPort
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        acceptJob?.cancel()
        scope.cancel()
    }

    private suspend fun handleSocks5Client(clientSocket: Socket) {
        clientSocket.use { client ->
            try {
                client.soTimeout = 30_000
                client.tcpNoDelay = true
                val inp = DataInputStream(client.getInputStream())
                val out = client.getOutputStream()

                // 1. Greeting: VER(1), NMETHODS(1), METHODS(n)
                val ver = inp.readUnsignedByte()
                if (ver != 0x05) return
                val nMethods = inp.readUnsignedByte()
                val methods = ByteArray(nMethods)
                inp.readFully(methods)

                // Reply: No auth required
                out.write(byteArrayOf(0x05, 0x00))
                out.flush()

                // 2. Request: VER(1), CMD(1), RSV(1), ATYP(1), DST.ADDR, DST.PORT(2)
                val reqVer = inp.readUnsignedByte()
                val cmd = inp.readUnsignedByte()
                inp.readUnsignedByte() // RSV
                val atyp = inp.readUnsignedByte()

                val targetHost: String = when (atyp) {
                    0x01 -> {
                        val ip = ByteArray(4)
                        inp.readFully(ip)
                        InetAddress.getByAddress(ip).hostAddress ?: return
                    }
                    0x03 -> {
                        val len = inp.readUnsignedByte()
                        val domain = ByteArray(len)
                        inp.readFully(domain)
                        String(domain, Charsets.US_ASCII)
                    }
                    0x04 -> {
                        val ip6 = ByteArray(16)
                        inp.readFully(ip6)
                        InetAddress.getByAddress(ip6).hostAddress ?: return
                    }
                    else -> return
                }
                val targetPort = inp.readUnsignedShort()

                if (cmd != 0x01) {
                    // Only CONNECT supported
                    out.write(byteArrayOf(0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                    out.flush()
                    return
                }

                val resolvedAddr = dohResolver.resolveHost(targetHost)
                if (resolvedAddr == null) {
                    out.write(byteArrayOf(0x05, 0x04, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                    out.flush()
                    return
                }

                val remote = Socket()
                vpnService.protect(remote)
                remote.tcpNoDelay = true
                remote.soTimeout = 30_000
                remote.connect(InetSocketAddress(resolvedAddr, targetPort), 7000)

                remote.use { server ->
                    out.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                    out.flush()

                    relayBidirectional(
                        clientIn = inp,
                        clientOut = out,
                        serverIn = server.getInputStream(),
                        serverOut = server.getOutputStream(),
                        serverSocket = server,
                        hostLabel = targetHost
                    )
                }
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun relayBidirectional(
        clientIn: InputStream,
        clientOut: OutputStream,
        serverIn: InputStream,
        serverOut: OutputStream,
        serverSocket: Socket,
        hostLabel: String
    ) {
        val upJob = scope.launch {
            val buf = ByteArray(16 * 1024)
            var isFirstWrite = true
            try {
                while (isActive) {
                    val read = clientIn.read(buf)
                    if (read <= 0) break

                    if (isFirstWrite && tlsFragmentEnabled &&
                        read > fragmentSizeBytes && buf[0] == 0x16.toByte() && buf[1] == 0x03.toByte()
                    ) {
                        isFirstWrite = false
                        var offset = 0
                        while (offset < read) {
                            val chunk = minOf(fragmentSizeBytes, read - offset)
                            serverOut.write(buf, offset, chunk)
                            serverOut.flush()
                            txBytesCounter.addAndGet(chunk.toLong())
                            offset += chunk
                            if (offset < read && fragmentDelayMs > 0) {
                                delay(fragmentDelayMs.toLong())
                            }
                        }
                        VpnRuntimeState.incrementFragmentedConnections()
                        VpnRuntimeState.appendLog("DPI", "قطعه‌بندی TLS برای $hostLabel (${fragmentSizeBytes}B)")
                    } else {
                        isFirstWrite = false
                        serverOut.write(buf, 0, read)
                        serverOut.flush()
                        txBytesCounter.addAndGet(read.toLong())
                    }
                }
            } catch (_: Exception) {
            } finally {
                runCatching { serverSocket.shutdownOutput() }
            }
        }

        val downJob = scope.launch {
            val buf = ByteArray(16 * 1024)
            try {
                while (isActive) {
                    val read = serverIn.read(buf)
                    if (read <= 0) break
                    clientOut.write(buf, 0, read)
                    clientOut.flush()
                    rxBytesCounter.addAndGet(read.toLong())
                }
            } catch (_: Exception) {
            }
        }

        upJob.join()
        downJob.join()
    }
}

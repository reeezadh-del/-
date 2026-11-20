package com.example.vpn

import android.net.VpnService
import com.example.data.VpnRuntimeState
import com.example.model.ProxyProfile
import com.example.model.ProxyProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

class LocalProxyServer(
    private val vpnService: VpnService,
    private val dohResolver: DohResolver,
    private val tlsFragmentEnabled: Boolean,
    private val fragmentSizeBytes: Int,
    private val fragmentDelayMs: Int,
    private val upstreamProxy: ProxyProfile?
) {
    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var acceptJob: Job? = null

    val localPort: Int
        get() = serverSocket?.localPort ?: 0

    fun start(): Int {
        val ss = ServerSocket(0, 128, InetAddress.getByName("127.0.0.1"))
        serverSocket = ss
        acceptJob = scope.launch {
            VpnRuntimeState.appendLog("Proxy", "پروکسی محلی ضد فیلتر روی پورت ${ss.localPort} فعال شد")
            while (isActive && !ss.isClosed) {
                try {
                    val client = ss.accept()
                    launch { handleClient(client) }
                } catch (_: Exception) {
                    break
                }
            }
        }
        return ss.localPort
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        acceptJob?.cancel()
        scope.cancel()
    }

    private suspend fun handleClient(clientSocket: Socket) {
        clientSocket.use { client ->
            try {
                client.soTimeout = 30_000
                val input = BufferedInputStream(client.getInputStream())
                val output = client.getOutputStream()

                val requestLine = readAsciiLine(input) ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return

                val method = parts[0].uppercase()
                val target = parts[1]

                // Drain HTTP headers
                val headers = mutableListOf<String>()
                while (true) {
                    val line = readAsciiLine(input) ?: break
                    if (line.isEmpty()) break
                    headers.add(line)
                }

                if (method == "CONNECT") {
                    val hostPort = target.split(":")
                    val host = hostPort[0]
                    val port = hostPort.getOrNull(1)?.toIntOrNull() ?: 443

                    val remote = connectProtectedRemote(host, port)
                    if (remote == null) {
                        output.write("HTTP/1.1 502 Bad Gateway\r\n\r\n".toByteArray())
                        output.flush()
                        return
                    }

                    remote.use { server ->
                        output.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                        output.flush()
                        relayBidirectional(
                            clientIn = input,
                            clientOut = output,
                            serverIn = server.getInputStream(),
                            serverOut = server.getOutputStream(),
                            serverSocket = server,
                            hostLabel = host
                        )
                    }
                } else {
                    val parsedUrl = java.net.URL(if (target.startsWith("http")) target else "http://localhost$target")
                    val host = parsedUrl.host
                    val port = if (parsedUrl.port > 0) parsedUrl.port else 80
                    val path = parsedUrl.file.ifEmpty { "/" }

                    val remote = connectProtectedRemote(host, port) ?: return
                    remote.use { server ->
                        val serverOut = server.getOutputStream()
                        val rebuiltHeader = buildString {
                            append("$method $path HTTP/1.1\r\n")
                            headers.filterNot { it.startsWith("Proxy-Connection", ignoreCase = true) }
                                .forEach { append("$it\r\n") }
                            append("Connection: close\r\n\r\n")
                        }
                        serverOut.write(rebuiltHeader.toByteArray(Charsets.US_ASCII))
                        serverOut.flush()

                        relayBidirectional(
                            clientIn = input,
                            clientOut = output,
                            serverIn = server.getInputStream(),
                            serverOut = serverOut,
                            serverSocket = server,
                            hostLabel = host
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun connectProtectedRemote(host: String, port: Int): Socket? {
        return try {
            val socket = Socket()
            vpnService.protect(socket)
            socket.tcpNoDelay = true
            socket.soTimeout = 30_000

            if (upstreamProxy != null && upstreamProxy.protocol.supportsDirectTunnel) {
                val proxyIp = dohResolver.resolveHost(upstreamProxy.host) ?: InetAddress.getByName(upstreamProxy.host)
                socket.connect(InetSocketAddress(proxyIp, upstreamProxy.port), 7000)

                val ok = when (upstreamProxy.protocol) {
                    ProxyProtocol.SOCKS5 -> performSocks5Handshake(socket, host, port, upstreamProxy)
                    ProxyProtocol.HTTP -> performHttpProxyHandshake(socket, host, port, upstreamProxy)
                    else -> false
                }
                if (!ok) {
                    socket.close()
                    null
                } else {
                    socket
                }
            } else {
                val resolvedIp = dohResolver.resolveHost(host) ?: return null
                socket.connect(InetSocketAddress(resolvedIp, port), 7000)
                socket
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun performSocks5Handshake(
        socket: Socket,
        targetHost: String,
        targetPort: Int,
        proxy: ProxyProfile
    ): Boolean {
        val out = socket.getOutputStream()
        val inp = socket.getInputStream()
        val hasAuth = proxy.username.isNotEmpty()
        if (hasAuth) {
            out.write(byteArrayOf(0x05, 0x02, 0x00, 0x02))
        } else {
            out.write(byteArrayOf(0x05, 0x01, 0x00))
        }
        out.flush()

        val methodResp = ByteArray(2)
        if (inp.read(methodResp) != 2 || methodResp[0] != 0x05.toByte()) return false

        if (methodResp[1] == 0x02.toByte()) {
            val uBytes = proxy.username.toByteArray()
            val pBytes = proxy.passwordOrUuid.toByteArray()
            val authPacket = ByteArrayOutputStream().apply {
                write(0x01)
                write(uBytes.size)
                write(uBytes)
                write(pBytes.size)
                write(pBytes)
            }.toByteArray()
            out.write(authPacket)
            out.flush()
            val authResp = ByteArray(2)
            if (inp.read(authResp) != 2 || authResp[1] != 0x00.toByte()) return false
        } else if (methodResp[1] != 0x00.toByte()) {
            return false
        }

        val hostBytes = targetHost.toByteArray(Charsets.US_ASCII)
        val connectReq = ByteArrayOutputStream().apply {
            write(0x05)
            write(0x01) // CONNECT
            write(0x00)
            write(0x03) // DOMAINNAME
            write(hostBytes.size)
            write(hostBytes)
            write((targetPort ushr 8) and 0xFF)
            write(targetPort and 0xFF)
        }.toByteArray()
        out.write(connectReq)
        out.flush()

        val respHeader = ByteArray(4)
        if (inp.read(respHeader) != 4 || respHeader[1] != 0x00.toByte()) return false
        when (respHeader[3].toInt() and 0xFF) {
            1 -> inp.read(ByteArray(6))
            3 -> {
                val len = inp.read()
                if (len > 0) inp.read(ByteArray(len + 2))
            }
            4 -> inp.read(ByteArray(18))
        }
        return true
    }

    private fun performHttpProxyHandshake(
        socket: Socket,
        targetHost: String,
        targetPort: Int,
        proxy: ProxyProfile
    ): Boolean {
        val out = socket.getOutputStream()
        val inp = BufferedInputStream(socket.getInputStream())
        val req = buildString {
            append("CONNECT $targetHost:$targetPort HTTP/1.1\r\n")
            append("Host: $targetHost:$targetPort\r\n")
            if (proxy.username.isNotEmpty()) {
                val token = android.util.Base64.encodeToString(
                    "${proxy.username}:${proxy.passwordOrUuid}".toByteArray(),
                    android.util.Base64.NO_WRAP
                )
                append("Proxy-Authorization: Basic $token\r\n")
            }
            append("\r\n")
        }
        out.write(req.toByteArray(Charsets.US_ASCII))
        out.flush()
        val statusLine = readAsciiLine(inp) ?: return false
        while (true) {
            val l = readAsciiLine(inp) ?: break
            if (l.isEmpty()) break
        }
        return statusLine.contains("200")
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

                    if (isFirstWrite && tlsFragmentEnabled && upstreamProxy == null &&
                        read > fragmentSizeBytes && buf[0] == 0x16.toByte() && buf[1] == 0x03.toByte()
                    ) {
                        isFirstWrite = false
                        serverSocket.tcpNoDelay = true
                        var offset = 0
                        while (offset < read) {
                            val chunk = minOf(fragmentSizeBytes, read - offset)
                            serverOut.write(buf, offset, chunk)
                            serverOut.flush()
                            offset += chunk
                            if (offset < read && fragmentDelayMs > 0) {
                                delay(fragmentDelayMs.toLong())
                            }
                        }
                        VpnRuntimeState.incrementFragmentedConnections()
                        VpnRuntimeState.appendLog("DPI", "قطعه‌بندی TLS ClientHello برای $hostLabel (${fragmentSizeBytes}B)")
                    } else {
                        isFirstWrite = false
                        serverOut.write(buf, 0, read)
                        serverOut.flush()
                    }
                    VpnRuntimeState.addBytes(rxDelta = 0L, txDelta = read.toLong())
                }
            } catch (_: Exception) {
            } finally {
                try { serverSocket.shutdownOutput() } catch (_: Exception) {}
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
                    VpnRuntimeState.addBytes(rxDelta = read.toLong(), txDelta = 0L)
                }
            } catch (_: Exception) {
            }
        }

        upJob.join()
        downJob.join()
    }

    private fun readAsciiLine(input: InputStream): String? {
        val baos = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) return if (baos.size() == 0) null else baos.toString("US-ASCII")
            if (b == '\n'.code) break
            if (b != '\r'.code) baos.write(b)
        }
        return baos.toString("US-ASCII")
    }
}

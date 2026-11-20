package com.example.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.IpPrefix
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.example.R
import com.example.data.VpnRuntimeState
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NetworkInterface
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface as JavaNetworkInterface

class SeparPlatformInterface(
    private val vpnService: VpnService,
    private val bypassedPackagesProvider: () -> Set<String>,
    private val onTunOpened: (ParcelFileDescriptor) -> Unit
) : PlatformInterface {

    private val connectivityManager =
        vpnService.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

    override fun autoDetectInterfaceControl(fd: Int) {
        if (!vpnService.protect(fd)) {
            throw IllegalStateException("VpnService.protect($fd) failed")
        }
    }

    override fun openTun(options: TunOptions): Int {
        val builder = vpnService.Builder()
            .setSession(vpnService.getString(R.string.app_name))
            .setMtu(options.mtu)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        val inet4 = options.inet4Address
        while (inet4.hasNext()) {
            val addr = inet4.next()
            builder.addAddress(addr.address(), addr.prefix())
        }

        val inet6 = options.inet6Address
        while (inet6.hasNext()) {
            val addr = inet6.next()
            builder.addAddress(addr.address(), addr.prefix())
        }

        if (options.autoRoute) {
            val dnsServer = runCatching { options.dnsServerAddress?.value }.getOrNull()
                ?.takeIf { it.isNotBlank() } ?: "172.19.0.2"
            runCatching { builder.addDnsServer(dnsServer) }

            var addedIpv4Route = false
            val inet4Routes = options.inet4RouteAddress
            while (inet4Routes.hasNext()) {
                val route = inet4Routes.next()
                builder.addRoute(route.address(), route.prefix())
                addedIpv4Route = true
            }
            if (!addedIpv4Route) {
                val ranges = options.inet4RouteRange
                while (ranges.hasNext()) {
                    val range = ranges.next()
                    builder.addRoute(range.address(), range.prefix())
                    addedIpv4Route = true
                }
            }
            if (!addedIpv4Route) {
                builder.addRoute("0.0.0.0", 0)
            }

            var addedIpv6Route = false
            val inet6Routes = options.inet6RouteAddress
            while (inet6Routes.hasNext()) {
                val route = inet6Routes.next()
                builder.addRoute(route.address(), route.prefix())
                addedIpv6Route = true
            }
            if (!addedIpv6Route) {
                val ranges6 = options.inet6RouteRange
                while (ranges6.hasNext()) {
                    val range = ranges6.next()
                    builder.addRoute(range.address(), range.prefix())
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val excludes4 = options.inet4RouteExcludeAddress
                while (excludes4.hasNext()) {
                    val ex = excludes4.next()
                    runCatching {
                        builder.excludeRoute(IpPrefix(InetAddress.getByName(ex.address()), ex.prefix()))
                    }
                }
                val excludes6 = options.inet6RouteExcludeAddress
                while (excludes6.hasNext()) {
                    val ex = excludes6.next()
                    runCatching {
                        builder.excludeRoute(IpPrefix(InetAddress.getByName(ex.address()), ex.prefix()))
                    }
                }
            }
        }

        // Always disallow our own package to prevent VPN routing loops
        runCatching { builder.addDisallowedApplication(vpnService.packageName) }

        // Disallow user-selected Iranian / bypassed apps
        bypassedPackagesProvider().forEach { pkg ->
            runCatching { builder.addDisallowedApplication(pkg) }
        }

        val pfd = builder.establish()
            ?: throw IllegalStateException("امکان ایجاد رابط TUN وجود ندارد")
        onTunOpened(pfd)
        return pfd.fd
    }

    override fun useProcFS(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int
    ): ConnectionOwner {
        val owner = ConnectionOwner()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val uid = connectivityManager.getConnectionOwnerUid(
                    ipProtocol,
                    InetSocketAddress(sourceAddress, sourcePort),
                    InetSocketAddress(destinationAddress, destinationPort)
                )
                owner.userId = uid
                val packages = vpnService.packageManager.getPackagesForUid(uid)
                if (!packages.isNullOrEmpty()) {
                    owner.androidPackageName = packages[0]
                }
            }
        }
        return owner
    }

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateListener(network, listener)
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                updateListener(network, listener, linkProperties)
            }

            override fun onLost(network: Network) {
                val remaining = findActivePhysicalNetwork()
                if (remaining != null) {
                    updateListener(remaining, listener)
                } else {
                    listener.updateDefaultInterface("", -1, false, false)
                }
            }
        }
        networkCallback = callback
        runCatching { connectivityManager.registerNetworkCallback(request, callback) }

        findActivePhysicalNetwork()?.let { active ->
            updateListener(active, listener)
        }
    }

    private fun findActivePhysicalNetwork(): Network? {
        val active = connectivityManager.activeNetwork
        if (active != null) {
            val caps = connectivityManager.getNetworkCapabilities(active)
            if (caps != null &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            ) {
                return active
            }
        }
        @Suppress("DEPRECATION")
        return connectivityManager.allNetworks.firstOrNull { net ->
            val caps = connectivityManager.getNetworkCapabilities(net)
            caps != null &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        }
    }

    private fun updateListener(
        network: Network,
        listener: InterfaceUpdateListener,
        lp: LinkProperties? = null
    ) {
        runCatching {
            val linkProps = lp ?: connectivityManager.getLinkProperties(network) ?: return
            val ifaceName = linkProps.interfaceName ?: return
            val ifaceIndex = runCatching {
                JavaNetworkInterface.getByName(ifaceName)?.index
            }.getOrNull() ?: 1
            val caps = connectivityManager.getNetworkCapabilities(network)
            val isExpensive = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false
            listener.updateDefaultInterface(ifaceName, ifaceIndex, isExpensive, false)
        }
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        networkCallback?.let { cb ->
            runCatching { connectivityManager.unregisterNetworkCallback(cb) }
        }
        networkCallback = null
    }

    override fun getInterfaces(): NetworkInterfaceIterator {
        val result = mutableListOf<NetworkInterface>()
        val seenNames = mutableSetOf<String>()

        // Primary: ConnectivityManager LinkProperties (reliable on Android 11+ where Netlink is restricted)
        runCatching {
            @Suppress("DEPRECATION")
            connectivityManager.allNetworks.forEach { net ->
                val caps = connectivityManager.getNetworkCapabilities(net) ?: return@forEach
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) return@forEach
                val lp = connectivityManager.getLinkProperties(net) ?: return@forEach
                val name = lp.interfaceName ?: return@forEach
                if (!seenNames.add(name)) return@forEach
                val addrs = lp.linkAddresses.mapNotNull { la ->
                    val host = la.address?.hostAddress?.substringBefore('%') ?: return@mapNotNull null
                    "$host/${la.prefixLength}"
                }
                if (addrs.isEmpty()) return@forEach
                val dnsList = lp.dnsServers.mapNotNull { it.hostAddress?.substringBefore('%') }
                val ifaceIndex = runCatching { JavaNetworkInterface.getByName(name)?.index }.getOrNull() ?: (result.size + 1)

                val libIface = NetworkInterface().apply {
                    index = ifaceIndex
                    mtu = if (lp.mtu > 0) lp.mtu else 1500
                    this.name = name
                    addresses = ListStringIterator(addrs)
                    flags = 65 // IFF_UP | IFF_RUNNING
                    dnsServer = ListStringIterator(dnsList)
                    metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                    type = when {
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Libbox.InterfaceTypeWIFI
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Libbox.InterfaceTypeCellular
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Libbox.InterfaceTypeEthernet
                        else -> Libbox.InterfaceTypeOther
                    }
                }
                result.add(libIface)
            }
        }

        // Secondary fallback via Java NetworkInterface
        runCatching {
            val enumIfaces = JavaNetworkInterface.getNetworkInterfaces()
            while (enumIfaces != null && enumIfaces.hasMoreElements()) {
                val nif = enumIfaces.nextElement()
                if (nif.isLoopback || nif.name in seenNames) continue
                val addrs = nif.interfaceAddresses.mapNotNull { ia ->
                    val host = ia.address?.hostAddress?.substringBefore('%') ?: return@mapNotNull null
                    "$host/${ia.networkPrefixLength}"
                }
                if (addrs.isEmpty()) continue

                val libIface = NetworkInterface().apply {
                    index = nif.index
                    mtu = runCatching { nif.mtu }.getOrDefault(1500)
                    name = nif.name
                    addresses = ListStringIterator(addrs)
                    flags = 65
                    type = when {
                        nif.name.startsWith("wlan") -> Libbox.InterfaceTypeWIFI
                        nif.name.startsWith("rmnet") || nif.name.startsWith("ccmni") -> Libbox.InterfaceTypeCellular
                        nif.name.startsWith("eth") -> Libbox.InterfaceTypeEthernet
                        else -> Libbox.InterfaceTypeOther
                    }
                }
                result.add(libIface)
            }
        }

        return object : NetworkInterfaceIterator {
            private var idx = 0
            override fun hasNext(): Boolean = idx < result.size
            override fun next(): NetworkInterface = result[idx++]
        }
    }

    override fun includeAllNetworks(): Boolean = false

    override fun underNetworkExtension(): Boolean = false

    override fun clearDNSCache() {}

    override fun readWIFIState(): WIFIState? = null

    override fun localDNSTransport(): LocalDNSTransport = object : LocalDNSTransport {
        override fun raw(): Boolean = false

        override fun exchange(ctx: io.nekohasekai.libbox.ExchangeContext, message: ByteArray) {
            ctx.errnoCode(1)
        }

        override fun lookup(ctx: io.nekohasekai.libbox.ExchangeContext, network: String, domain: String) {
            try {
                val physicalNet = findActivePhysicalNetwork()
                val addresses = if (physicalNet != null) {
                    physicalNet.getAllByName(domain).toList()
                } else {
                    InetAddress.getAllByName(domain).toList()
                }
                val filtered = when (network.lowercase()) {
                    "ip4" -> addresses.filterIsInstance<java.net.Inet4Address>()
                    "ip6" -> addresses.filterIsInstance<java.net.Inet6Address>()
                    else -> addresses
                }
                val joined = filtered.mapNotNull { it.hostAddress?.substringBefore('%') }
                    .distinct()
                    .joinToString("\n")
                if (joined.isNotBlank()) {
                    ctx.success(joined)
                } else {
                    ctx.errnoCode(2)
                }
            } catch (_: Exception) {
                ctx.errnoCode(1)
            }
        }
    }

    override fun systemCertificates(): StringIterator? = null

    override fun sendNotification(notification: Notification) {
        val text = notification.body.ifBlank { notification.title }
        if (text.isNotBlank()) {
            VpnRuntimeState.appendLog("Core", text)
        }
    }

    private class ListStringIterator(private val items: List<String>) : StringIterator {
        private var idx = 0
        override fun hasNext(): Boolean = idx < items.size
        override fun len(): Int = items.size
        override fun next(): String = items[idx++]
    }
}

package com.hiz.mirror.network

import android.content.Context
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {

    fun getLocalLanIpv4Address(): String {
        val candidates = mutableListOf<String>()
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback || iface.isVirtual) continue
                val addrs = Collections.list(iface.inetAddresses)
                for (addr in addrs) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val hostAddress = addr.hostAddress ?: continue
                        candidates.add(hostAddress)
                    }
                }
            }
        } catch (_: Exception) {
            // Fallback below
        }

        return candidates.firstOrNull {
            it.startsWith("192.168.") || it.startsWith("10.") || it.startsWith("172.")
        } ?: candidates.firstOrNull() ?: "127.0.0.1"
    }

    fun getBroadcastAddresses(): List<InetAddress> {
        val result = linkedSetOf<InetAddress>()
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (ifaceAddr in iface.interfaceAddresses) {
                    val broadcast = ifaceAddr.broadcast
                    if (broadcast is Inet4Address) {
                        result.add(broadcast)
                    }
                }
            }
        } catch (_: Exception) {
            // Fallback to global broadcast
        }
        runCatching { result.add(InetAddress.getByName("255.255.255.255")) }
        return result.toList()
    }

    fun acquireMulticastLock(context: Context): WifiManager.MulticastLock? {
        return try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifi?.createMulticastLock("HizLanDiscoveryLock")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {
            null
        }
    }
}

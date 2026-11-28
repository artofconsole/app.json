package com.hiz.mirror.network

import android.content.Context
import android.net.wifi.WifiManager
import com.hiz.mirror.model.DiscoveredDevice
import com.hiz.mirror.model.DiscoverySource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

class UdpDiscoveryManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val deviceMap = ConcurrentHashMap<String, DiscoveredDevice>()
    private var receiverBroadcastJob: Job? = null
    private var transmitterListenJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun startReceiverAnnouncer(
        deviceId: String,
        deviceNameProvider: () -> String,
        streamPort: Int,
        isBusyProvider: () -> Boolean
    ) {
        stopAll()
        multicastLock = NetworkUtils.acquireMulticastLock(context)

        receiverBroadcastJob = scope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    soTimeout = 1000
                    bind(InetSocketAddress(LanProtocol.UDP_DISCOVERY_PORT))
                }

                val listenJob = launch {
                    val buf = ByteArray(2048)
                    val packet = DatagramPacket(buf, buf.size)
                    while (isActive) {
                        try {
                            socket.receive(packet)
                            val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                            val json = JSONObject(text)
                            if (json.optString("magic") == MAGIC_TOKEN &&
                                json.optString("type") == TYPE_DISCOVER
                            ) {
                                sendAnnounce(
                                    socket = socket,
                                    targetAddr = packet.address,
                                    targetPort = packet.port,
                                    deviceId = deviceId,
                                    deviceName = deviceNameProvider(),
                                    streamPort = streamPort,
                                    isBusy = isBusyProvider()
                                )
                            }
                        } catch (_: SocketTimeoutException) {
                            // Continue loop
                        } catch (_: Exception) {
                            // Ignore malformed UDP datagram
                        }
                    }
                }

                while (isActive) {
                    val broadcasts = NetworkUtils.getBroadcastAddresses()
                    for (addr in broadcasts) {
                        sendAnnounce(
                            socket = socket,
                            targetAddr = addr,
                            targetPort = LanProtocol.UDP_DISCOVERY_PORT,
                            deviceId = deviceId,
                            deviceName = deviceNameProvider(),
                            streamPort = streamPort,
                            isBusy = isBusyProvider()
                        )
                    }
                    delay(2500L)
                }

                listenJob.cancel()
            } catch (_: Exception) {
                // Socket closed or restricted
            } finally {
                runCatching { socket?.close() }
            }
        }
    }

    fun startTransmitterDiscovery() {
        if (transmitterListenJob?.isActive == true) return
        multicastLock = NetworkUtils.acquireMulticastLock(context)

        transmitterListenJob = scope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    soTimeout = 900
                    bind(InetSocketAddress(LanProtocol.UDP_DISCOVERY_PORT))
                }

                val buf = ByteArray(2048)
                val packet = DatagramPacket(buf, buf.size)

                // Kick off initial scan probe
                launch { sendDiscoveryProbe(socket) }

                while (isActive) {
                    try {
                        socket.receive(packet)
                        val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        val json = JSONObject(text)
                        if (json.optString("magic") == MAGIC_TOKEN &&
                            json.optString("type") == TYPE_ANNOUNCE
                        ) {
                            val ip = json.optString("ip").takeIf { it.isNotBlank() }
                                ?: packet.address.hostAddress.orEmpty()
                            val port = json.optInt("port", LanProtocol.DEFAULT_STREAM_PORT)
                            val id = json.optString("id").ifBlank { "$ip:$port" }
                            val name = json.optString("name", "Hız TV Alıcısı")
                            val isBusy = json.optBoolean("busy", false)

                            deviceMap[id] = DiscoveredDevice(
                                id = id,
                                name = name,
                                ip = ip,
                                port = port,
                                source = DiscoverySource.UDP_BROADCAST,
                                isBusy = isBusy,
                                lastSeenMs = System.currentTimeMillis()
                            )
                            publishPrunedDevices()
                        }
                    } catch (_: SocketTimeoutException) {
                        publishPrunedDevices()
                    } catch (_: Exception) {
                        // Ignore malformed packet
                    }
                }
            } catch (_: Exception) {
                // Socket bind failure fallback
            } finally {
                runCatching { socket?.close() }
            }
        }
    }

    fun scanNow() {
        scope.launch(Dispatchers.IO) {
            _isScanning.value = true
            var tempSocket: DatagramSocket? = null
            try {
                tempSocket = DatagramSocket().apply { broadcast = true }
                sendDiscoveryProbe(tempSocket)
                delay(450L)
                sendDiscoveryProbe(tempSocket)
                delay(450L)
            } catch (_: Exception) {
                // Ignore
            } finally {
                runCatching { tempSocket?.close() }
                publishPrunedDevices()
                _isScanning.value = false
            }
        }
    }

    private fun sendDiscoveryProbe(socket: DatagramSocket) {
        val payload = JSONObject().apply {
            put("magic", MAGIC_TOKEN)
            put("type", TYPE_DISCOVER)
            put("fromIp", NetworkUtils.getLocalLanIpv4Address())
        }.toString().toByteArray(Charsets.UTF_8)

        for (addr in NetworkUtils.getBroadcastAddresses()) {
            runCatching {
                socket.send(DatagramPacket(payload, payload.size, addr, LanProtocol.UDP_DISCOVERY_PORT))
            }
        }
    }

    private fun sendAnnounce(
        socket: DatagramSocket,
        targetAddr: InetAddress,
        targetPort: Int,
        deviceId: String,
        deviceName: String,
        streamPort: Int,
        isBusy: Boolean
    ) {
        val localIp = NetworkUtils.getLocalLanIpv4Address()
        val payload = JSONObject().apply {
            put("magic", MAGIC_TOKEN)
            put("type", TYPE_ANNOUNCE)
            put("id", deviceId)
            put("name", deviceName)
            put("ip", localIp)
            put("port", streamPort)
            put("busy", isBusy)
        }.toString().toByteArray(Charsets.UTF_8)

        runCatching {
            socket.send(DatagramPacket(payload, payload.size, targetAddr, targetPort))
        }
    }

    private fun publishPrunedDevices() {
        val now = System.currentTimeMillis()
        val iterator = deviceMap.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.lastSeenMs > DEVICE_TTL_MS) {
                iterator.remove()
            }
        }
        _discoveredDevices.value = deviceMap.values.sortedBy { it.name }
    }

    fun stopAll() {
        receiverBroadcastJob?.cancel()
        receiverBroadcastJob = null
        transmitterListenJob?.cancel()
        transmitterListenJob = null
        runCatching {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        }
        multicastLock = null
    }

    companion object {
        private const val MAGIC_TOKEN = "HIZ_LAN_V1"
        private const val TYPE_DISCOVER = "DISCOVER"
        private const val TYPE_ANNOUNCE = "ANNOUNCE"
        private const val DEVICE_TTL_MS = 12_000L
    }
}

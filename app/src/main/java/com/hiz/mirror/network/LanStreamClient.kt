package com.hiz.mirror.network

import com.hiz.mirror.model.ConnectionState
import com.hiz.mirror.model.EndpointValidator
import com.hiz.mirror.model.QualityPreset
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
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class LanStreamClient(
    private val scope: CoroutineScope,
    private val senderName: String
) {
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _statusDetail = MutableStateFlow(ConnectionState.DISCONNECTED.description)
    val statusDetail: StateFlow<String> = _statusDetail.asStateFlow()

    private val _estimatedLatencyMs = MutableStateFlow(0)
    val estimatedLatencyMs: StateFlow<Int> = _estimatedLatencyMs.asStateFlow()

    private val _framesSent = AtomicLong(0L)
    private val _droppedFrames = AtomicLong(0L)

    private var socket: Socket? = null
    private var outputStream: DataOutputStream? = null
    private var sessionJob: Job? = null
    private var activePreset: QualityPreset = QualityPreset.BALANCED
    private var frameQueue = ArrayBlockingQueue<LanProtocol.Packet>(QualityPreset.BALANCED.bufferFrames.coerceAtLeast(1))
    private val manualDisconnect = AtomicBoolean(false)

    private var targetIp: String = ""
    private var targetPort: Int = LanProtocol.DEFAULT_STREAM_PORT
    private var reconnectAttempts = 0

    var onPeerStopped: ((String) -> Unit)? = null

    fun updatePreset(preset: QualityPreset) {
        activePreset = preset
        val newCapacity = preset.bufferFrames.coerceAtLeast(1)
        val nextQueue = ArrayBlockingQueue<LanProtocol.Packet>(newCapacity)
        frameQueue.clear()
        frameQueue = nextQueue
    }

    fun connect(ip: String, port: Int, preset: QualityPreset, onConnected: () -> Unit) {
        disconnect(sendStop = false)
        manualDisconnect.set(false)
        targetIp = ip
        targetPort = port
        reconnectAttempts = 0
        updatePreset(preset)
        startConnectionLoop(onConnected)
    }

    private fun startConnectionLoop(onConnected: () -> Unit) {
        sessionJob = scope.launch(Dispatchers.IO) {
            var notifiedInitialConnect = false
            while (isActive && !manualDisconnect.get()) {
                val stateToShow = if (reconnectAttempts == 0) {
                    ConnectionState.CONNECTING
                } else {
                    ConnectionState.RECONNECTING
                }
                _connectionState.value = stateToShow
                _statusDetail.value = if (reconnectAttempts == 0) {
                    "$targetIp:$targetPort adresine bağlanılıyor..."
                } else {
                    "Yeniden bağlanılıyor (deneme $reconnectAttempts)..."
                }

                try {
                    val clientSocket = Socket()
                    clientSocket.tcpNoDelay = true
                    clientSocket.soTimeout = 6000
                    clientSocket.connect(InetSocketAddress(targetIp, targetPort), 3500)
                    socket = clientSocket

                    val out = DataOutputStream(BufferedOutputStream(clientSocket.getOutputStream(), 65536))
                    val input = DataInputStream(BufferedInputStream(clientSocket.getInputStream(), 16384))
                    outputStream = out

                    // Send HELLO handshake
                    val helloPayload = JSONObject().apply {
                        put("senderName", senderName)
                        put("preset", activePreset.label)
                    }.toString().toByteArray(Charsets.UTF_8)
                    LanProtocol.writePacket(out, LanProtocol.PacketType.HELLO, System.currentTimeMillis(), helloPayload)

                    val ack = LanProtocol.readPacket(input)
                    if (ack == null || ack.type != LanProtocol.PacketType.HELLO_ACK) {
                        throw IllegalStateException("Alıcı cihaz el sıkışmayı onaylamadı.")
                    }

                    reconnectAttempts = 0
                    _connectionState.value = ConnectionState.CONNECTED
                    _statusDetail.value = "$targetIp:$targetPort ile bağlantı kuruldu."
                    if (!notifiedInitialConnect) {
                        notifiedInitialConnect = true
                        launch(Dispatchers.Main) { onConnected() }
                    }

                    // Reader loop (heartbeats + remote stop)
                    val readerJob = launch {
                        while (isActive && !manualDisconnect.get()) {
                            val pkt = LanProtocol.readPacket(input) ?: break
                            when (pkt.type) {
                                LanProtocol.PacketType.HEARTBEAT_PONG -> {
                                    val rtt = (System.currentTimeMillis() - pkt.timestampMs).toInt().coerceAtLeast(1)
                                    val oneWay = (rtt / 2) + activePreset.bufferFrames * 6
                                    val prev = _estimatedLatencyMs.value
                                    _estimatedLatencyMs.value = if (prev == 0) oneWay else ((prev * 2 + oneWay) / 3)
                                }
                                LanProtocol.PacketType.STOP -> {
                                    manualDisconnect.set(true)
                                    _connectionState.value = ConnectionState.DISCONNECTED
                                    _statusDetail.value = "Alıcı TV yayını durdurdu."
                                    launch(Dispatchers.Main) { onPeerStopped?.invoke("Alıcı TV yayını sonlandırdı.") }
                                    break
                                }
                                else -> Unit
                            }
                        }
                    }

                    // Heartbeat sender loop
                    val pingJob = launch {
                        while (isActive && !manualDisconnect.get()) {
                            delay(2000L)
                            synchronized(out) {
                                LanProtocol.writePacket(
                                    out,
                                    LanProtocol.PacketType.HEARTBEAT_PING,
                                    System.currentTimeMillis(),
                                    ByteArray(0)
                                )
                            }
                        }
                    }

                    // Outbound frame pump respecting preset buffer depth
                    while (isActive && !manualDisconnect.get() && readerJob.isActive) {
                        val packet = frameQueue.poll()
                        if (packet != null) {
                            synchronized(out) {
                                LanProtocol.writePacket(out, packet.type, packet.timestampMs, packet.payload)
                            }
                            _framesSent.incrementAndGet()
                        } else {
                            delay(4L)
                        }
                    }

                    pingJob.cancel()
                    readerJob.cancel()
                } catch (e: Exception) {
                    if (manualDisconnect.get()) break
                    _connectionState.value = ConnectionState.DROPPED
                    _statusDetail.value = e.message ?: "Bağlantı koptu."
                } finally {
                    closeSocketQuietly()
                }

                if (manualDisconnect.get()) break
                val delayMs = EndpointValidator.calculateReconnectDelayMs(reconnectAttempts)
                reconnectAttempts += 1
                delay(delayMs)
            }
        }
    }

    fun enqueueVideoFrame(width: Int, height: Int, jpegBytes: ByteArray) {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        val payload = LanProtocol.encodeVideoFramePayload(width, height, jpegBytes)
        val packet = LanProtocol.Packet(
            type = LanProtocol.PacketType.VIDEO_FRAME,
            timestampMs = System.currentTimeMillis(),
            payload = payload
        )
        // Drop oldest buffered frame if queue is full to preserve low latency
        if (!frameQueue.offer(packet)) {
            frameQueue.poll()
            _droppedFrames.incrementAndGet()
            frameQueue.offer(packet)
        }
    }

    fun enqueueMediaPhoto(width: Int, height: Int, jpegBytes: ByteArray) {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        val payload = LanProtocol.encodeVideoFramePayload(width, height, jpegBytes)
        val packet = LanProtocol.Packet(
            type = LanProtocol.PacketType.MEDIA_PHOTO,
            timestampMs = System.currentTimeMillis(),
            payload = payload
        )
        frameQueue.clear()
        frameQueue.offer(packet)
    }

    fun getFramesSent(): Long = _framesSent.get()
    fun getDroppedFrames(): Long = _droppedFrames.get()

    fun disconnect(sendStop: Boolean = true) {
        manualDisconnect.set(true)
        if (sendStop) {
            val out = outputStream
            if (out != null) {
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        synchronized(out) {
                            LanProtocol.writePacket(out, LanProtocol.PacketType.STOP, System.currentTimeMillis(), ByteArray(0))
                        }
                    }
                    closeSocketQuietly()
                }
            }
        } else {
            closeSocketQuietly()
        }
        sessionJob?.cancel()
        sessionJob = null
        frameQueue.clear()
        _framesSent.set(0L)
        _droppedFrames.set(0L)
        _estimatedLatencyMs.value = 0
        _connectionState.value = ConnectionState.DISCONNECTED
        _statusDetail.value = ConnectionState.DISCONNECTED.description
    }

    private fun closeSocketQuietly() {
        runCatching { outputStream?.close() }
        runCatching { socket?.close() }
        outputStream = null
        socket = null
    }
}

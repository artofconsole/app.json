package com.hiz.mirror.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.hiz.mirror.model.ConnectionState
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
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicInteger

class LanStreamServer(
    private val scope: CoroutineScope,
    private val listenPort: Int = LanProtocol.DEFAULT_STREAM_PORT
) {
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _currentFrame = MutableStateFlow<Bitmap?>(null)
    val currentFrame: StateFlow<Bitmap?> = _currentFrame.asStateFlow()

    private val _senderName = MutableStateFlow("")
    val senderName: StateFlow<String> = _senderName.asStateFlow()

    private val _incomingFps = MutableStateFlow(0)
    val incomingFps: StateFlow<Int> = _incomingFps.asStateFlow()

    private val _incomingResolution = MutableStateFlow("—")
    val incomingResolution: StateFlow<String> = _incomingResolution.asStateFlow()

    private val _isMirroringActive = MutableStateFlow(false)
    val isMirroringActive: StateFlow<Boolean> = _isMirroringActive.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var activeClientSocket: Socket? = null
    private var activeClientOut: DataOutputStream? = null
    private var acceptJob: Job? = null
    private var fpsTickerJob: Job? = null
    private val frameCounter = AtomicInteger(0)

    fun startListening() {
        if (acceptJob?.isActive == true) return
        _connectionState.value = ConnectionState.DISCONNECTED

        fpsTickerJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000L)
                _incomingFps.value = frameCounter.getAndSet(0)
            }
        }

        acceptJob = scope.launch(Dispatchers.IO) {
            try {
                val srv = ServerSocket(listenPort).apply {
                    reuseAddress = true
                }
                serverSocket = srv

                while (isActive) {
                    val client = try {
                        srv.accept()
                    } catch (_: SocketException) {
                        break
                    }
                    handleClientSession(client)
                }
            } catch (_: Exception) {
                _connectionState.value = ConnectionState.DROPPED
            }
        }
    }

    private fun handleClientSession(client: Socket) {
        activeClientSocket = client
        try {
            client.tcpNoDelay = true
            client.soTimeout = 8000
            val input = DataInputStream(BufferedInputStream(client.getInputStream(), 65536))
            val out = DataOutputStream(BufferedOutputStream(client.getOutputStream(), 8192))
            activeClientOut = out

            _connectionState.value = ConnectionState.CONNECTING

            val hello = LanProtocol.readPacket(input)
            if (hello == null || hello.type != LanProtocol.PacketType.HELLO) {
                runCatching { client.close() }
                _connectionState.value = ConnectionState.DISCONNECTED
                return
            }

            val helloJson = runCatching {
                JSONObject(String(hello.payload, Charsets.UTF_8))
            }.getOrNull()
            _senderName.value = helloJson?.optString("senderName").takeUnless { it.isNullOrBlank() }
                ?: "Hız Verici (Telefon)"

            LanProtocol.writePacket(
                out,
                LanProtocol.PacketType.HELLO_ACK,
                System.currentTimeMillis(),
                ByteArray(0)
            )

            _connectionState.value = ConnectionState.CONNECTED
            _isMirroringActive.value = true

            while (!client.isClosed) {
                val packet = LanProtocol.readPacket(input) ?: break
                when (packet.type) {
                    LanProtocol.PacketType.VIDEO_FRAME,
                    LanProtocol.PacketType.MEDIA_PHOTO -> {
                        val decoded = LanProtocol.decodeVideoFramePayload(packet.payload)
                        val bitmap = BitmapFactory.decodeByteArray(
                            decoded.jpegBytes,
                            0,
                            decoded.jpegBytes.size
                        )
                        if (bitmap != null) {
                            _currentFrame.value = bitmap
                            _incomingResolution.value = "${decoded.width}x${decoded.height}"
                            frameCounter.incrementAndGet()
                        }
                    }

                    LanProtocol.PacketType.HEARTBEAT_PING -> {
                        synchronized(out) {
                            LanProtocol.writePacket(
                                out,
                                LanProtocol.PacketType.HEARTBEAT_PONG,
                                packet.timestampMs,
                                ByteArray(0)
                            )
                        }
                    }

                    LanProtocol.PacketType.STOP -> {
                        break
                    }

                    else -> Unit
                }
            }
            _connectionState.value = ConnectionState.DISCONNECTED
        } catch (_: Exception) {
            _connectionState.value = ConnectionState.DROPPED
        } finally {
            _isMirroringActive.value = false
            _incomingFps.value = 0
            _currentFrame.value = null
            runCatching { activeClientOut?.close() }
            runCatching { client.close() }
            activeClientOut = null
            activeClientSocket = null
        }
    }

    fun stopCurrentSessionByReceiver() {
        val out = activeClientOut
        scope.launch(Dispatchers.IO) {
            if (out != null) {
                runCatching {
                    synchronized(out) {
                        LanProtocol.writePacket(
                            out,
                            LanProtocol.PacketType.STOP,
                            System.currentTimeMillis(),
                            ByteArray(0)
                        )
                    }
                }
            }
            runCatching { activeClientSocket?.close() }
        }
        _isMirroringActive.value = false
        _currentFrame.value = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    fun stopServer() {
        fpsTickerJob?.cancel()
        fpsTickerJob = null
        acceptJob?.cancel()
        acceptJob = null
        runCatching { activeClientSocket?.close() }
        runCatching { serverSocket?.close() }
        activeClientOut = null
        activeClientSocket = null
        serverSocket = null
        _isMirroringActive.value = false
        _currentFrame.value = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }
}

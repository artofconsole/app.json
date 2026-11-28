package com.hiz.mirror.model

enum class AppMode(val storageKey: String, val displayTitle: String) {
    TRANSMITTER("transmitter", "Verici Modu"),
    RECEIVER("receiver", "Alıcı Modu");

    companion object {
        fun fromStorageKey(key: String?): AppMode? =
            entries.firstOrNull { it.storageKey == key }
    }
}

enum class ConnectionState(
    val label: String,
    val description: String,
    val isPulsing: Boolean
) {
    DISCONNECTED(
        label = "Bağlı Değil",
        description = "Yerel ağda hedef cihaz seçin veya IP/Port girin.",
        isPulsing = false
    ),
    CONNECTING(
        label = "Bağlanıyor",
        description = "Yerel ağ TCP soket el sıkışması kuruluyor.",
        isPulsing = true
    ),
    CONNECTED(
        label = "Bağlandı",
        description = "Yerel ağ bağlantısı etkin ve kararlı.",
        isPulsing = true
    ),
    DROPPED(
        label = "Bağlantı Koptu",
        description = "Hedef cihaz ile ağ bağlantısı kesildi.",
        isPulsing = false
    ),
    RECONNECTING(
        label = "Yeniden Bağlanılıyor",
        description = "Otomatik yeniden bağlanma denemesi yürütülüyor.",
        isPulsing = true
    )
}

enum class QualityPreset(
    val storageKey: String,
    val label: String,
    val description: String,
    val fps: Int,
    val targetWidth: Int,
    val targetHeight: Int,
    val jpegQuality: Int,
    val bitrateKbps: Int,
    val bufferFrames: Int
) {
    LOW_LATENCY(
        storageKey = "low_latency",
        label = "Düşük Gecikme",
        description = "Sunum, oyun ve anlık etkileşim için minimum tamponlama",
        fps = 45,
        targetWidth = 960,
        targetHeight = 540,
        jpegQuality = 62,
        bitrateKbps = 2800,
        bufferFrames = 1
    ),
    BALANCED(
        storageKey = "balanced",
        label = "Dengeli",
        description = "Günlük ekran yansıtma için akıcı kare hızı ve net görüntü",
        fps = 30,
        targetWidth = 1280,
        targetHeight = 720,
        jpegQuality = 78,
        bitrateKbps = 4800,
        bufferFrames = 2
    ),
    HIGH_QUALITY(
        storageKey = "high_quality",
        label = "Yüksek Kalite",
        description = "Video, fotoğraf ve yüksek detay için Full HD akış",
        fps = 24,
        targetWidth = 1920,
        targetHeight = 1080,
        jpegQuality = 88,
        bitrateKbps = 8500,
        bufferFrames = 4
    );

    val frameIntervalMs: Long
        get() = (1000L / fps.coerceAtLeast(1))

    companion object {
        fun fromStorageKey(key: String?): QualityPreset =
            entries.firstOrNull { it.storageKey == key } ?: BALANCED
    }
}

enum class DiscoverySource(val label: String) {
    UDP_BROADCAST("UDP Keşif"),
    MANUAL("Manuel IP")
}

data class DiscoveredDevice(
    val id: String,
    val name: String,
    val ip: String,
    val port: Int,
    val source: DiscoverySource = DiscoverySource.UDP_BROADCAST,
    val isBusy: Boolean = false,
    val lastSeenMs: Long = System.currentTimeMillis()
)

data class StreamMetrics(
    val fps: Int = 0,
    val estimatedLatencyMs: Int = 0,
    val resolutionLabel: String = "1280x720",
    val bitrateKbps: Int = 4800,
    val framesSent: Long = 0L,
    val droppedFrames: Long = 0L
)

enum class CaptureSourceType(val label: String) {
    SCREEN_PROJECTION("Ekran Yakalama"),
    TEST_PATTERN("Canlı Test Akışı"),
    MEDIA_PHOTO("Fotoğraf Paylaşımı")
}

data class EndpointValidationResult(
    val isValid: Boolean,
    val ip: String = "",
    val port: Int = 0,
    val errorMessage: String? = null
)

object EndpointValidator {
    private val IPV4_REGEX = Regex(
        "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$"
    )

    fun validate(rawIp: String, rawPort: String): EndpointValidationResult {
        val ip = rawIp.trim()
        if (ip.isEmpty()) {
            return EndpointValidationResult(
                isValid = false,
                errorMessage = "Lütfen bir IPv4 adresi girin (ör. 192.168.1.42)."
            )
        }
        if (!IPV4_REGEX.matches(ip)) {
            return EndpointValidationResult(
                isValid = false,
                errorMessage = "Geçersiz IP adresi formatı. Örnek: 192.168.1.42"
            )
        }

        val portStr = rawPort.trim()
        val port = portStr.toIntOrNull()
        if (port == null || port !in 1..65535) {
            return EndpointValidationResult(
                isValid = false,
                errorMessage = "Port numarası 1 ile 65535 arasında olmalıdır."
            )
        }

        return EndpointValidationResult(isValid = true, ip = ip, port = port)
    }

    fun calculateReconnectDelayMs(attempt: Int): Long {
        val clamped = attempt.coerceIn(0, 5)
        return (1000L shl clamped).coerceAtMost(10_000L)
    }
}

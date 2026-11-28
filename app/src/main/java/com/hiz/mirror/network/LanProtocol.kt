package com.hiz.mirror.network

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.nio.ByteBuffer

object LanProtocol {
    const val MAGIC: Int = 0x48495A31 // "HIZ1"
    const val DEFAULT_STREAM_PORT: Int = 9876
    const val UDP_DISCOVERY_PORT: Int = 41234
    const val MAX_PACKET_BYTES: Int = 12 * 1024 * 1024

    enum class PacketType(val code: Byte) {
        HELLO(1),
        HELLO_ACK(2),
        VIDEO_FRAME(3),
        HEARTBEAT_PING(4),
        HEARTBEAT_PONG(5),
        STOP(6),
        AUDIO_PCM(7),    // Faz 2 ses aktarımı paketi
        MEDIA_PHOTO(8);  // Faz 2 fotoğraf paylaşımı paketi

        companion object {
            fun fromCode(code: Byte): PacketType? =
                entries.firstOrNull { it.code == code }
        }
    }

    data class Packet(
        val type: PacketType,
        val timestampMs: Long,
        val payload: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Packet) return false
            return type == other.type &&
                timestampMs == other.timestampMs &&
                payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int {
            var result = type.hashCode()
            result = 31 * result + timestampMs.hashCode()
            result = 31 * result + payload.contentHashCode()
            return result
        }
    }

    fun writePacket(out: DataOutputStream, type: PacketType, timestampMs: Long, payload: ByteArray) {
        out.writeInt(MAGIC)
        out.writeByte(type.code.toInt())
        out.writeLong(timestampMs)
        out.writeInt(payload.size)
        if (payload.isNotEmpty()) {
            out.write(payload)
        }
        out.flush()
    }

    fun readPacket(input: DataInputStream): Packet? {
        val magic = try {
            input.readInt()
        } catch (_: EOFException) {
            return null
        }
        if (magic != MAGIC) {
            throw IllegalStateException("Geçersiz Hız LAN paket başlığı: $magic")
        }
        val typeCode = input.readByte()
        val type = PacketType.fromCode(typeCode)
            ?: throw IllegalStateException("Bilinmeyen paket türü: $typeCode")
        val timestampMs = input.readLong()
        val length = input.readInt()
        if (length < 0 || length > MAX_PACKET_BYTES) {
            throw IllegalStateException("Geçersiz paket boyutu: $length")
        }
        val payload = ByteArray(length)
        if (length > 0) {
            input.readFully(payload)
        }
        return Packet(type = type, timestampMs = timestampMs, payload = payload)
    }

    fun encodeVideoFramePayload(width: Int, height: Int, jpegBytes: ByteArray): ByteArray {
        val buf = ByteBuffer.allocate(8 + jpegBytes.size)
        buf.putInt(width)
        buf.putInt(height)
        buf.put(jpegBytes)
        return buf.array()
    }

    data class DecodedVideoFrame(
        val width: Int,
        val height: Int,
        val jpegBytes: ByteArray
    )

    fun decodeVideoFramePayload(payload: ByteArray): DecodedVideoFrame {
        require(payload.size >= 8) { "Video kare paketi çok kısa" }
        val buf = ByteBuffer.wrap(payload)
        val width = buf.int
        val height = buf.int
        val jpeg = ByteArray(payload.size - 8)
        buf.get(jpeg)
        return DecodedVideoFrame(width = width, height = height, jpegBytes = jpeg)
    }
}

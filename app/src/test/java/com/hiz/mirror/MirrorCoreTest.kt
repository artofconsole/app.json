package com.hiz.mirror

import com.hiz.mirror.model.AppMode
import com.hiz.mirror.model.ConnectionState
import com.hiz.mirror.model.EndpointValidator
import com.hiz.mirror.model.QualityPreset
import com.hiz.mirror.network.LanProtocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class MirrorCoreTest {

    @Test
    fun `endpoint validator rejects invalid ip and out of range port`() {
        assertFalse(EndpointValidator.validate("", "9876").isValid)
        assertFalse(EndpointValidator.validate("999.168.1.1", "9876").isValid)
        assertFalse(EndpointValidator.validate("192.168.1.15", "0").isValid)
        assertFalse(EndpointValidator.validate("192.168.1.15", "70000").isValid)

        val ok = EndpointValidator.validate(" 192.168.1.42 ", " 9876 ")
        assertTrue(ok.isValid)
        assertEquals("192.168.1.42", ok.ip)
        assertEquals(9876, ok.port)
    }

    @Test
    fun `quality presets expose distinct fps resolution and buffer values`() {
        assertTrue(QualityPreset.LOW_LATENCY.fps > QualityPreset.BALANCED.fps)
        assertTrue(QualityPreset.LOW_LATENCY.bufferFrames < QualityPreset.HIGH_QUALITY.bufferFrames)
        assertTrue(QualityPreset.HIGH_QUALITY.targetWidth > QualityPreset.BALANCED.targetWidth)
        assertEquals(33L, QualityPreset.BALANCED.frameIntervalMs)
    }

    @Test
    fun `lan protocol round-trips video frame packet intact`() {
        val sampleJpeg = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x7F)
        val encoded = LanProtocol.encodeVideoFramePayload(1280, 720, sampleJpeg)

        val baos = ByteArrayOutputStream()
        LanProtocol.writePacket(
            out = DataOutputStream(baos),
            type = LanProtocol.PacketType.VIDEO_FRAME,
            timestampMs = 123456789L,
            payload = encoded
        )

        val decodedPacket = LanProtocol.readPacket(
            DataInputStream(ByteArrayInputStream(baos.toByteArray()))
        )
        assertNotNull(decodedPacket)
        assertEquals(LanProtocol.PacketType.VIDEO_FRAME, decodedPacket!!.type)
        assertEquals(123456789L, decodedPacket.timestampMs)

        val decodedFrame = LanProtocol.decodeVideoFramePayload(decodedPacket.payload)
        assertEquals(1280, decodedFrame.width)
        assertEquals(720, decodedFrame.height)
        assertArrayEquals(sampleJpeg, decodedFrame.jpegBytes)
    }

    @Test
    fun `connection state and app mode mappings cover all required Turkish labels`() {
        assertEquals("Bağlı Değil", ConnectionState.DISCONNECTED.label)
        assertEquals("Bağlanıyor", ConnectionState.CONNECTING.label)
        assertEquals("Bağlandı", ConnectionState.CONNECTED.label)
        assertEquals("Bağlantı Koptu", ConnectionState.DROPPED.label)
        assertEquals("Yeniden Bağlanılıyor", ConnectionState.RECONNECTING.label)
        assertEquals(AppMode.RECEIVER, AppMode.fromStorageKey("receiver"))
        assertEquals(AppMode.TRANSMITTER, AppMode.fromStorageKey("transmitter"))
    }
}

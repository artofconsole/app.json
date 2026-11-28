package com.hiz.mirror.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import com.hiz.mirror.model.CaptureSourceType
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
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

class ScreenCaptureManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onEncodedFrame: (width: Int, height: Int, jpegBytes: ByteArray) -> Unit
) {
    private val _previewBitmap = MutableStateFlow<Bitmap?>(null)
    val previewBitmap: StateFlow<Bitmap?> = _previewBitmap.asStateFlow()

    private val _currentFps = MutableStateFlow(0)
    val currentFps: StateFlow<Int> = _currentFps.asStateFlow()

    private var activePreset: QualityPreset = QualityPreset.BALANCED
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null

    private var patternJob: Job? = null
    private var fpsJob: Job? = null
    private var lastFrameTimestampMs = 0L
    private var framesInWindow = 0
    private var testSeq = 0L

    fun updatePreset(preset: QualityPreset, sourceType: CaptureSourceType) {
        val dimensionsChanged =
            activePreset.targetWidth != preset.targetWidth ||
                activePreset.targetHeight != preset.targetHeight
        activePreset = preset

        if (dimensionsChanged && mediaProjection != null && sourceType == CaptureSourceType.SCREEN_PROJECTION) {
            val proj = mediaProjection ?: return
            stopVirtualDisplayOnly()
            setupVirtualDisplay(proj, preset)
        }
    }

    fun startScreenProjection(projection: MediaProjection, preset: QualityPreset) {
        stop()
        activePreset = preset
        mediaProjection = projection
        startFpsTicker()
        setupVirtualDisplay(projection, preset)
    }

    private fun setupVirtualDisplay(projection: MediaProjection, preset: QualityPreset) {
        val thread = HandlerThread("HizCaptureThread").apply { start() }
        captureThread = thread
        val handler = Handler(thread.looper)
        captureHandler = handler

        val densityDpi = context.resources.displayMetrics.densityDpi
        val reader = ImageReader.newInstance(
            preset.targetWidth,
            preset.targetHeight,
            PixelFormat.RGBA_8888,
            2
        )
        imageReader = reader

        reader.setOnImageAvailableListener({ r ->
            val image = runCatching { r.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
            try {
                val now = System.currentTimeMillis()
                if (now - lastFrameTimestampMs < activePreset.frameIntervalMs) {
                    return@setOnImageAvailableListener
                }
                lastFrameTimestampMs = now

                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * image.width

                val paddedBitmap = Bitmap.createBitmap(
                    image.width + rowPadding / pixelStride,
                    image.height,
                    Bitmap.Config.ARGB_8888
                )
                paddedBitmap.copyPixelsFromBuffer(buffer)

                val cropped = if (rowPadding == 0) {
                    paddedBitmap
                } else {
                    Bitmap.createBitmap(paddedBitmap, 0, 0, image.width, image.height).also {
                        paddedBitmap.recycle()
                    }
                }

                val baos = ByteArrayOutputStream()
                cropped.compress(Bitmap.CompressFormat.JPEG, activePreset.jpegQuality, baos)
                val jpegBytes = baos.toByteArray()

                _previewBitmap.value = cropped
                framesInWindow += 1
                onEncodedFrame(cropped.width, cropped.height, jpegBytes)
            } finally {
                image.close()
            }
        }, handler)

        virtualDisplay = projection.createVirtualDisplay(
            "HizMirrorDisplay",
            preset.targetWidth,
            preset.targetHeight,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            handler
        )
    }

    fun startTestPatternStream(preset: QualityPreset) {
        stop()
        activePreset = preset
        startFpsTicker()

        patternJob = scope.launch(Dispatchers.Default) {
            val startTime = System.currentTimeMillis()
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale("tr", "TR"))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            while (isActive) {
                val w = activePreset.targetWidth
                val h = activePreset.targetHeight
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                val elapsed = (System.currentTimeMillis() - startTime) / 1000.0

                // Dark anthracite background gradient
                paint.shader = LinearGradient(
                    0f, 0f, w.toFloat(), h.toFloat(),
                    intArrayOf(0xFF090D16.toInt(), 0xFF0E1726.toInt(), 0xFF08111E.toInt()),
                    null,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                paint.shader = null

                // Subtle cyan wave
                paint.color = 0xFF00D4FF.toInt()
                paint.strokeWidth = (w / 280f).coerceAtLeast(2f)
                paint.style = Paint.Style.STROKE
                var prevX = 0f
                var prevY = h * 0.62f
                for (x in 16..w step 16) {
                    val y = (h * 0.62f +
                        sin(x * 0.015 + elapsed * 4.0) * (h * 0.09f) +
                        cos(x * 0.03 - elapsed * 2.5) * (h * 0.03f)).toFloat()
                    canvas.drawLine(prevX, prevY, x.toFloat(), y, paint)
                    prevX = x.toFloat()
                    prevY = y
                }

                // Moving green latency marker
                paint.style = Paint.Style.FILL
                paint.color = 0xFF10B981.toInt()
                val markerX = ((elapsed * 240.0) % (w - 140)).toFloat() + 60f
                canvas.drawRect(markerX, h * 0.78f, markerX + 48f, h * 0.78f + 10f, paint)

                // Headline & Telemetry text
                paint.color = Color.WHITE
                paint.textSize = w * 0.055f
                canvas.drawText("Hız LAN Yansıtma Aktif", w * 0.06f, h * 0.24f, paint)

                paint.color = 0xFF94A3B8.toInt()
                paint.textSize = w * 0.024f
                canvas.drawText(
                    "Profil: ${activePreset.label} · ${w}x${h} · ${activePreset.fps} FPS hedefi",
                    w * 0.06f,
                    h * 0.32f,
                    paint
                )

                testSeq += 1L
                paint.color = 0xFF00D4FF.toInt()
                paint.textSize = w * 0.036f
                canvas.drawText(
                    "${timeFormat.format(Date())} · Kare #$testSeq",
                    w * 0.06f,
                    h * 0.44f,
                    paint
                )

                val baos = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, activePreset.jpegQuality, baos)
                val bytes = baos.toByteArray()

                _previewBitmap.value = bmp
                framesInWindow += 1
                onEncodedFrame(w, h, bytes)

                delay(activePreset.frameIntervalMs)
            }
        }
    }

    private fun startFpsTicker() {
        fpsJob?.cancel()
        framesInWindow = 0
        fpsJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000L)
                _currentFps.value = framesInWindow
                framesInWindow = 0
            }
        }
    }

    private fun stopVirtualDisplayOnly() {
        runCatching { virtualDisplay?.release() }
        runCatching { imageReader?.close() }
        runCatching { captureThread?.quitSafely() }
        virtualDisplay = null
        imageReader = null
        captureThread = null
        captureHandler = null
    }

    fun stop() {
        patternJob?.cancel()
        patternJob = null
        fpsJob?.cancel()
        fpsJob = null
        stopVirtualDisplayOnly()
        runCatching { mediaProjection?.stop() }
        mediaProjection = null
        _currentFps.value = 0
        _previewBitmap.value = null
    }
}

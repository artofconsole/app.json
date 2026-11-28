package com.hiz.mirror.modules

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.hiz.mirror.model.QualityPreset
import java.io.ByteArrayOutputStream
import kotlin.math.min

data class EncodedPhotoFrame(
    val width: Int,
    val height: Int,
    val jpegBytes: ByteArray,
    val displayName: String
)

/**
 * Faz 2 Genişletilebilir Modül: Seçili Medya / Fotoğraf Paylaşımı
 * Seçilen görseli kalite ön ayarına göre ölçekleyip LAN soketine besler.
 */
class MediaShareModule(private val context: Context) {

    fun encodeUriForLan(uri: Uri, preset: QualityPreset): EncodedPhotoFrame? {
        val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return null
        val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return null

        val ratio = min(
            preset.targetWidth.toFloat() / decoded.width.toFloat(),
            preset.targetHeight.toFloat() / decoded.height.toFloat()
        ).coerceAtMost(1f)

        val targetW = ((decoded.width * ratio).toInt() / 2 * 2).coerceAtLeast(2)
        val targetH = ((decoded.height * ratio).toInt() / 2 * 2).coerceAtLeast(2)

        val scaled = if (targetW == decoded.width && targetH == decoded.height) {
            decoded
        } else {
            Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                decoded.recycle()
            }
        }

        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, preset.jpegQuality, baos)
        val jpeg = baos.toByteArray()
        val name = uri.lastPathSegment ?: "Seçili Görsel"
        scaled.recycle()

        return EncodedPhotoFrame(
            width = targetW,
            height = targetH,
            jpegBytes = jpeg,
            displayName = name
        )
    }
}

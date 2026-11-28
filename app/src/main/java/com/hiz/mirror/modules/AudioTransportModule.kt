package com.hiz.mirror.modules

/**
 * Faz 2 Genişletilebilir Modül: Yerel Ağ Ses Aktarımı
 * MVP'de odak ekran görüntüsü aktarımındadır. Bu modül Android AudioPlaybackCapture /
 * AudioRecord PCM paketleme sözleşmesini LanProtocol.PacketType.AUDIO_PCM için hazır tutar.
 */
data class AudioModuleState(
    val isEnabled: Boolean = false,
    val sampleRateHz: Int = 48_000,
    val channels: Int = 2,
    val statusSummary: String = "Sistem ve mikrofon sesi sonraki fazda PCM/Opus kanalı ile bağlanmaya hazır."
)

class AudioTransportModule {
    private var state = AudioModuleState()

    fun getState(): AudioModuleState = state

    fun setEnabled(enabled: Boolean): AudioModuleState {
        state = state.copy(
            isEnabled = enabled,
            statusSummary = if (enabled) {
                "Ses kanalı (48 kHz Stereo PCM) sonraki faz aktarım hattı için etkinleştirildi."
            } else {
                "Sistem ve mikrofon sesi sonraki fazda PCM/Opus kanalı ile bağlanmaya hazır."
            }
        )
        return state
    }
}

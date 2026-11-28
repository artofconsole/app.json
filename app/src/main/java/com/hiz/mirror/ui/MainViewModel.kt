package com.hiz.mirror.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.media.projection.MediaProjection
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hiz.mirror.capture.ScreenCaptureManager
import com.hiz.mirror.data.MirrorPreferencesRepository
import com.hiz.mirror.model.AppMode
import com.hiz.mirror.model.CaptureSourceType
import com.hiz.mirror.model.ConnectionState
import com.hiz.mirror.model.DiscoveredDevice
import com.hiz.mirror.model.EndpointValidator
import com.hiz.mirror.model.QualityPreset
import com.hiz.mirror.model.StreamMetrics
import com.hiz.mirror.modules.AudioModuleState
import com.hiz.mirror.modules.AudioTransportModule
import com.hiz.mirror.modules.MediaShareModule
import com.hiz.mirror.network.LanProtocol
import com.hiz.mirror.network.LanStreamClient
import com.hiz.mirror.network.LanStreamServer
import com.hiz.mirror.network.NetworkUtils
import com.hiz.mirror.network.UdpDiscoveryManager
import com.hiz.mirror.service.ScreenMirrorForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

data class HizUiState(
    val showSplash: Boolean = true,
    val lastSavedMode: AppMode? = null,
    val activeMode: AppMode? = null,
    val localLanIp: String = "127.0.0.1",
    val listenPort: Int = LanProtocol.DEFAULT_STREAM_PORT,
    val receiverName: String = "Hız TV Alıcısı",
    val discoveredDevices: List<DiscoveredDevice> = emptyList(),
    val selectedDeviceId: String? = null,
    val isScanning: Boolean = false,
    val manualIp: String = "",
    val manualPort: String = LanProtocol.DEFAULT_STREAM_PORT.toString(),
    val endpointError: String? = null,
    val activeTargetLabel: String = "Hedef Seçilmedi",
    val selectedPreset: QualityPreset = QualityPreset.BALANCED,
    val captureSource: CaptureSourceType = CaptureSourceType.SCREEN_PROJECTION,
    val isStreaming: Boolean = false,
    val transmitterConnectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val transmitterStatusDetail: String = ConnectionState.DISCONNECTED.description,
    val transmitterMetrics: StreamMetrics = StreamMetrics(),
    val transmitterPreview: Bitmap? = null,
    val receiverConnectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val isReceiverMirroringActive: Boolean = false,
    val receiverFrame: Bitmap? = null,
    val receiverSenderName: String = "",
    val receiverIncomingFps: Int = 0,
    val receiverIncomingResolution: String = "—",
    val audioModuleState: AudioModuleState = AudioModuleState(),
    val selectedPhotoName: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = MirrorPreferencesRepository(application)
    private val discoveryManager = UdpDiscoveryManager(application, viewModelScope)
    private val receiverId = "rx-${UUID.randomUUID().toString().take(8)}"

    private val streamClient = LanStreamClient(
        scope = viewModelScope,
        senderName = "${Build.MODEL} (Hız Verici)"
    )

    private val streamServer = LanStreamServer(
        scope = viewModelScope,
        listenPort = LanProtocol.DEFAULT_STREAM_PORT
    )

    private val captureManager = ScreenCaptureManager(
        context = application,
        scope = viewModelScope
    ) { width, height, jpegBytes ->
        streamClient.enqueueVideoFrame(width, height, jpegBytes)
    }

    private val audioModule = AudioTransportModule()
    private val mediaShareModule = MediaShareModule(application)

    private val _uiState = MutableStateFlow(
        HizUiState(
            localLanIp = NetworkUtils.getLocalLanIpv4Address(),
            audioModuleState = audioModule.getState()
        )
    )
    val uiState: StateFlow<HizUiState> = _uiState.asStateFlow()

    var requestMediaProjectionLauncher: (() -> Unit)? = null

    init {
        loadInitialSettings()
        observeNetworkStreams()
        wireServiceCallbacks()
    }

    private fun loadInitialSettings() {
        viewModelScope.launch {
            val saved = prefsRepo.readSnapshot()
            _uiState.update {
                it.copy(
                    lastSavedMode = saved.lastMode,
                    selectedPreset = saved.qualityPreset,
                    manualIp = saved.manualIp,
                    manualPort = saved.manualPort,
                    receiverName = saved.receiverName
                )
            }
        }
    }

    private fun observeNetworkStreams() {
        viewModelScope.launch {
            discoveryManager.discoveredDevices.collect { devices ->
                _uiState.update { it.copy(discoveredDevices = devices) }
            }
        }
        viewModelScope.launch {
            discoveryManager.isScanning.collect { scanning ->
                _uiState.update { it.copy(isScanning = scanning) }
            }
        }
        viewModelScope.launch {
            streamClient.connectionState.collect { conn ->
                _uiState.update { it.copy(transmitterConnectionState = conn) }
            }
        }
        viewModelScope.launch {
            streamClient.statusDetail.collect { detail ->
                _uiState.update { it.copy(transmitterStatusDetail = detail) }
            }
        }
        viewModelScope.launch {
            captureManager.previewBitmap.collect { bmp ->
                _uiState.update { it.copy(transmitterPreview = bmp) }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(500L)
                if (_uiState.value.activeMode == AppMode.TRANSMITTER) {
                    val preset = _uiState.value.selectedPreset
                    _uiState.update {
                        it.copy(
                            transmitterMetrics = StreamMetrics(
                                fps = captureManager.currentFps.value,
                                estimatedLatencyMs = streamClient.estimatedLatencyMs.value,
                                resolutionLabel = "${preset.targetWidth}x${preset.targetHeight}",
                                bitrateKbps = preset.bitrateKbps,
                                framesSent = streamClient.getFramesSent(),
                                droppedFrames = streamClient.getDroppedFrames()
                            )
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            streamServer.connectionState.collect { conn ->
                _uiState.update { it.copy(receiverConnectionState = conn) }
            }
        }
        viewModelScope.launch {
            streamServer.isMirroringActive.collect { active ->
                _uiState.update { it.copy(isReceiverMirroringActive = active) }
            }
        }
        viewModelScope.launch {
            streamServer.currentFrame.collect { bmp ->
                _uiState.update { it.copy(receiverFrame = bmp) }
            }
        }
        viewModelScope.launch {
            streamServer.senderName.collect { name ->
                _uiState.update { it.copy(receiverSenderName = name) }
            }
        }
        viewModelScope.launch {
            streamServer.incomingFps.collect { fps ->
                _uiState.update { it.copy(receiverIncomingFps = fps) }
            }
        }
        viewModelScope.launch {
            streamServer.incomingResolution.collect { res ->
                _uiState.update { it.copy(receiverIncomingResolution = res) }
            }
        }

        streamClient.onPeerStopped = { reason ->
            stopCaptureAndService()
            _uiState.update {
                it.copy(
                    isStreaming = false,
                    transmitterStatusDetail = reason
                )
            }
        }
    }

    private fun wireServiceCallbacks() {
        ScreenMirrorForegroundService.onProjectionReady = { projection: MediaProjection ->
            beginStreamingWithProjection(projection)
        }
        ScreenMirrorForegroundService.onStopRequestedByNotification = {
            stopMirroring()
        }
    }

    fun onSplashFinished() {
        val saved = _uiState.value.lastSavedMode
        _uiState.update { it.copy(showSplash = false) }
        if (saved != null) {
            enterMode(saved)
        }
    }

    fun selectMode(mode: AppMode) {
        viewModelScope.launch {
            prefsRepo.saveLastMode(mode)
        }
        _uiState.update { it.copy(lastSavedMode = mode) }
        enterMode(mode)
    }

    private fun enterMode(mode: AppMode) {
        teardownActiveMode()
        val ip = NetworkUtils.getLocalLanIpv4Address()
        _uiState.update {
            it.copy(
                activeMode = mode,
                localLanIp = ip,
                endpointError = null
            )
        }

        when (mode) {
            AppMode.TRANSMITTER -> {
                discoveryManager.startTransmitterDiscovery()
                discoveryManager.scanNow()
            }

            AppMode.RECEIVER -> {
                streamServer.startListening()
                discoveryManager.startReceiverAnnouncer(
                    deviceId = receiverId,
                    deviceNameProvider = { _uiState.value.receiverName },
                    streamPort = LanProtocol.DEFAULT_STREAM_PORT,
                    isBusyProvider = { _uiState.value.isReceiverMirroringActive }
                )
            }
        }
    }

    fun changeMode() {
        teardownActiveMode()
        _uiState.update {
            it.copy(
                activeMode = null,
                isStreaming = false,
                isReceiverMirroringActive = false
            )
        }
    }

    private fun teardownActiveMode() {
        stopCaptureAndService()
        streamClient.disconnect()
        streamServer.stopServer()
        discoveryManager.stopAll()
    }

    fun scanLanDevices() {
        discoveryManager.scanNow()
    }

    fun selectDiscoveredDevice(device: DiscoveredDevice) {
        viewModelScope.launch {
            prefsRepo.saveManualEndpoint(device.ip, device.port.toString())
        }
        _uiState.update {
            it.copy(
                selectedDeviceId = device.id,
                manualIp = device.ip,
                manualPort = device.port.toString(),
                endpointError = null,
                activeTargetLabel = "${device.name} (${device.ip}:${device.port})"
            )
        }
    }

    fun updateManualIp(value: String) {
        _uiState.update {
            it.copy(
                manualIp = value,
                selectedDeviceId = null,
                endpointError = null
            )
        }
    }

    fun updateManualPort(value: String) {
        _uiState.update {
            it.copy(
                manualPort = value,
                endpointError = null
            )
        }
    }

    fun connectManualEndpoint() {
        val validation = EndpointValidator.validate(_uiState.value.manualIp, _uiState.value.manualPort)
        if (!validation.isValid) {
            _uiState.update { it.copy(endpointError = validation.errorMessage) }
            return
        }

        viewModelScope.launch {
            prefsRepo.saveManualEndpoint(validation.ip, validation.port.toString())
        }

        _uiState.update {
            it.copy(
                endpointError = null,
                activeTargetLabel = "${validation.ip}:${validation.port}"
            )
        }
        startMirroring()
    }

    fun selectPreset(preset: QualityPreset) {
        viewModelScope.launch {
            prefsRepo.saveQualityPreset(preset)
        }
        _uiState.update { it.copy(selectedPreset = preset) }
        streamClient.updatePreset(preset)
        captureManager.updatePreset(preset, _uiState.value.captureSource)
    }

    fun selectCaptureSource(source: CaptureSourceType) {
        _uiState.update { it.copy(captureSource = source) }
    }

    fun startMirroring() {
        val state = _uiState.value
        val selectedDev = state.discoveredDevices.firstOrNull { it.id == state.selectedDeviceId }
        val targetIp: String
        val targetPort: Int
        val targetLabel: String

        if (selectedDev != null) {
            targetIp = selectedDev.ip
            targetPort = selectedDev.port
            targetLabel = "${selectedDev.name} (${selectedDev.ip}:${selectedDev.port})"
        } else {
            val validation = EndpointValidator.validate(state.manualIp, state.manualPort)
            if (!validation.isValid) {
                _uiState.update { it.copy(endpointError = validation.errorMessage) }
                return
            }
            targetIp = validation.ip
            targetPort = validation.port
            targetLabel = "$targetIp:$targetPort"
            viewModelScope.launch {
                prefsRepo.saveManualEndpoint(targetIp, targetPort.toString())
            }
        }

        _uiState.update {
            it.copy(
                endpointError = null,
                activeTargetLabel = targetLabel
            )
        }

        streamClient.connect(targetIp, targetPort, state.selectedPreset) {
            when (_uiState.value.captureSource) {
                CaptureSourceType.SCREEN_PROJECTION -> {
                    requestMediaProjectionLauncher?.invoke()
                }

                CaptureSourceType.TEST_PATTERN -> {
                    captureManager.startTestPatternStream(_uiState.value.selectedPreset)
                    _uiState.update { it.copy(isStreaming = true) }
                }

                CaptureSourceType.MEDIA_PHOTO -> {
                    _uiState.update { it.copy(isStreaming = true) }
                }
            }
        }
    }

    fun onMediaProjectionPermissionResult(resultCode: Int, data: Intent?) {
        if (resultCode != android.app.Activity.RESULT_OK || data == null) {
            // Fall back cleanly or inform user without crashing
            _uiState.update {
                it.copy(
                    transmitterStatusDetail = "Ekran yakalama izni verilmedi; Canlı Test Akışı başlatıldı.",
                    captureSource = CaptureSourceType.TEST_PATTERN,
                    isStreaming = true
                )
            }
            captureManager.startTestPatternStream(_uiState.value.selectedPreset)
            return
        }

        ScreenMirrorForegroundService.startWithProjection(
            context = getApplication(),
            resultCode = resultCode,
            data = data
        )
    }

    private fun beginStreamingWithProjection(projection: MediaProjection) {
        captureManager.startScreenProjection(projection, _uiState.value.selectedPreset)
        _uiState.update {
            it.copy(
                isStreaming = true,
                transmitterStatusDetail = "Ekran görüntüsü yerel ağ üzerinden aktarılıyor."
            )
        }
    }

    fun stopMirroring() {
        stopCaptureAndService()
        streamClient.disconnect(sendStop = true)
        _uiState.update {
            it.copy(
                isStreaming = false,
                transmitterStatusDetail = "Yansıtma durduruldu."
            )
        }
    }

    fun sharePhotoUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val frame = mediaShareModule.encodeUriForLan(uri, _uiState.value.selectedPreset)
            if (frame != null) {
                _uiState.update {
                    it.copy(
                        captureSource = CaptureSourceType.MEDIA_PHOTO,
                        selectedPhotoName = frame.displayName,
                        transmitterStatusDetail = "Fotoğraf aktarıma hazır: ${frame.displayName}"
                    )
                }
                if (_uiState.value.transmitterConnectionState == ConnectionState.CONNECTED) {
                    streamClient.enqueueMediaPhoto(frame.width, frame.height, frame.jpegBytes)
                }
            }
        }
    }

    fun toggleAudioModule() {
        val next = !audioModule.getState().isEnabled
        _uiState.update { it.copy(audioModuleState = audioModule.setEnabled(next)) }
    }

    fun updateReceiverName(name: String) {
        _uiState.update { it.copy(receiverName = name) }
        viewModelScope.launch {
            prefsRepo.saveReceiverName(name)
        }
    }

    fun stopReceiverSession() {
        streamServer.stopCurrentSessionByReceiver()
    }

    private fun stopCaptureAndService() {
        captureManager.stop()
        ScreenMirrorForegroundService.stop(getApplication())
    }

    override fun onCleared() {
        super.onCleared()
        teardownActiveMode()
    }
}

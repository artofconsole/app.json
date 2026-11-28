package com.hiz.mirror

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiz.mirror.model.AppMode
import com.hiz.mirror.ui.MainViewModel
import com.hiz.mirror.ui.components.HizTopBar
import com.hiz.mirror.ui.screens.ModeSelectionScreen
import com.hiz.mirror.ui.screens.ReceiverScreen
import com.hiz.mirror.ui.screens.SplashScreen
import com.hiz.mirror.ui.screens.TransmitterScreen
import com.hiz.mirror.ui.theme.HizColors
import com.hiz.mirror.ui.theme.HizTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onMediaProjectionPermissionResult(result.resultCode, result.data)
    }

    private val photoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.sharePhotoUri(uri)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        viewModel.requestMediaProjectionLauncher = {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjectionLauncher.launch(manager.createScreenCaptureIntent())
        }

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            // Receiver TV ekranı aktif yansıtmadayken tam ekran (immersive) ve ekranı uyanık tutma (FLAG_KEEP_SCREEN_ON)
            LaunchedEffect(state.showSplash, state.activeMode, state.isReceiverMirroringActive) {
                val shouldKeepAwakeAndImmersive =
                    state.showSplash ||
                        (state.activeMode == AppMode.RECEIVER && state.isReceiverMirroringActive)

                if (shouldKeepAwakeAndImmersive) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    enterImmersiveMode()
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    exitImmersiveMode()
                }
            }

            HizTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(HizColors.Background)
                ) {
                    AnimatedContent(
                        targetState = Triple(
                            state.showSplash,
                            state.activeMode,
                            state.isReceiverMirroringActive
                        ),
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "rootScreenTransition"
                    ) { (showSplash, activeMode, isReceiverMirroring) ->
                        if (showSplash) {
                            SplashScreen(onFinished = viewModel::onSplashFinished)
                        } else {
                            val hideTopBarForImmersiveTv =
                                activeMode == AppMode.RECEIVER && isReceiverMirroring

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .let { mod ->
                                        if (hideTopBarForImmersiveTv) mod else mod.safeDrawingPadding()
                                    }
                            ) {
                                if (!hideTopBarForImmersiveTv) {
                                    val barState = when (activeMode) {
                                        AppMode.TRANSMITTER -> state.transmitterConnectionState
                                        AppMode.RECEIVER -> state.receiverConnectionState
                                        null -> state.transmitterConnectionState
                                    }
                                    HizTopBar(
                                        mode = activeMode,
                                        connectionState = barState,
                                        onChangeMode = viewModel::changeMode
                                    )
                                }

                                when (activeMode) {
                                    null -> ModeSelectionScreen(
                                        lastSavedMode = state.lastSavedMode,
                                        onSelectMode = viewModel::selectMode
                                    )

                                    AppMode.TRANSMITTER -> TransmitterScreen(
                                        connectionState = state.transmitterConnectionState,
                                        statusDetail = state.transmitterStatusDetail,
                                        isStreaming = state.isStreaming,
                                        discoveredDevices = state.discoveredDevices,
                                        selectedDeviceId = state.selectedDeviceId,
                                        isScanning = state.isScanning,
                                        manualIp = state.manualIp,
                                        manualPort = state.manualPort,
                                        endpointError = state.endpointError,
                                        activeTargetLabel = state.activeTargetLabel,
                                        selectedPreset = state.selectedPreset,
                                        captureSource = state.captureSource,
                                        metrics = state.transmitterMetrics,
                                        previewBitmap = state.transmitterPreview,
                                        audioModuleState = state.audioModuleState,
                                        selectedPhotoName = state.selectedPhotoName,
                                        localLanIp = state.localLanIp,
                                        onScanDevices = viewModel::scanLanDevices,
                                        onSelectDevice = viewModel::selectDiscoveredDevice,
                                        onManualIpChanged = viewModel::updateManualIp,
                                        onManualPortChanged = viewModel::updateManualPort,
                                        onConnectManual = viewModel::connectManualEndpoint,
                                        onSelectPreset = viewModel::selectPreset,
                                        onSelectCaptureSource = viewModel::selectCaptureSource,
                                        onStartMirroring = viewModel::startMirroring,
                                        onStopMirroring = viewModel::stopMirroring,
                                        onPickPhoto = { photoPickerLauncher.launch("image/*") },
                                        onToggleAudioModule = viewModel::toggleAudioModule
                                    )

                                    AppMode.RECEIVER -> ReceiverScreen(
                                        localIp = state.localLanIp,
                                        listenPort = state.listenPort,
                                        receiverName = state.receiverName,
                                        connectionState = state.receiverConnectionState,
                                        isMirroringActive = state.isReceiverMirroringActive,
                                        currentFrame = state.receiverFrame,
                                        senderName = state.receiverSenderName,
                                        incomingFps = state.receiverIncomingFps,
                                        incomingResolution = state.receiverIncomingResolution,
                                        onReceiverNameChange = viewModel::updateReceiverName,
                                        onStopSession = viewModel::stopReceiverSession,
                                        onChangeMode = viewModel::changeMode
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun enterImmersiveMode() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun exitImmersiveMode() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}

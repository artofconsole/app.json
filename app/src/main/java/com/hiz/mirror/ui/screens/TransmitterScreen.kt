package com.hiz.mirror.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hiz.mirror.model.CaptureSourceType
import com.hiz.mirror.model.ConnectionState
import com.hiz.mirror.model.DiscoveredDevice
import com.hiz.mirror.model.QualityPreset
import com.hiz.mirror.model.StreamMetrics
import com.hiz.mirror.modules.AudioModuleState
import com.hiz.mirror.ui.components.hizDpadFocusable
import com.hiz.mirror.ui.theme.HizColors

@Composable
fun TransmitterScreen(
    connectionState: ConnectionState,
    statusDetail: String,
    isStreaming: Boolean,
    discoveredDevices: List<DiscoveredDevice>,
    selectedDeviceId: String?,
    isScanning: Boolean,
    manualIp: String,
    manualPort: String,
    endpointError: String?,
    activeTargetLabel: String,
    selectedPreset: QualityPreset,
    captureSource: CaptureSourceType,
    metrics: StreamMetrics,
    previewBitmap: Bitmap?,
    audioModuleState: AudioModuleState,
    selectedPhotoName: String?,
    localLanIp: String,
    onScanDevices: () -> Unit,
    onSelectDevice: (DiscoveredDevice) -> Unit,
    onManualIpChanged: (String) -> Unit,
    onManualPortChanged: (String) -> Unit,
    onConnectManual: () -> Unit,
    onSelectPreset: (QualityPreset) -> Unit,
    onSelectCaptureSource: (CaptureSourceType) -> Unit,
    onStartMirroring: () -> Unit,
    onStopMirroring: () -> Unit,
    onPickPhoto: () -> Unit,
    onToggleAudioModule: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(HizColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        val isWide = maxWidth >= 820.dp
        val isCompact = maxWidth < 560.dp

        if (isWide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1.65f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StreamControlCard(
                        isCompact = false,
                        isStreaming = isStreaming,
                        connectionState = connectionState,
                        statusDetail = statusDetail,
                        activeTargetLabel = activeTargetLabel,
                        selectedPreset = selectedPreset,
                        metrics = metrics,
                        previewBitmap = previewBitmap,
                        onStartMirroring = onStartMirroring,
                        onStopMirroring = onStopMirroring
                    )
                    DeviceConnectionCard(
                        isCompact = false,
                        discoveredDevices = discoveredDevices,
                        selectedDeviceId = selectedDeviceId,
                        isScanning = isScanning,
                        manualIp = manualIp,
                        manualPort = manualPort,
                        endpointError = endpointError,
                        onScanDevices = onScanDevices,
                        onSelectDevice = onSelectDevice,
                        onManualIpChanged = onManualIpChanged,
                        onManualPortChanged = onManualPortChanged,
                        onConnectManual = onConnectManual
                    )
                    SourceAndModulesCard(
                        isCompact = false,
                        captureSource = captureSource,
                        audioModuleState = audioModuleState,
                        selectedPhotoName = selectedPhotoName,
                        onSelectCaptureSource = onSelectCaptureSource,
                        onPickPhoto = onPickPhoto,
                        onToggleAudioModule = onToggleAudioModule
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    QualityPresetsCard(
                        selectedPreset = selectedPreset,
                        onSelectPreset = onSelectPreset
                    )
                    ConnectionLifecycleCard(
                        connectionState = connectionState,
                        statusDetail = statusDetail,
                        metrics = metrics,
                        localLanIp = localLanIp
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                StreamControlCard(
                    isCompact = isCompact,
                    isStreaming = isStreaming,
                    connectionState = connectionState,
                    statusDetail = statusDetail,
                    activeTargetLabel = activeTargetLabel,
                    selectedPreset = selectedPreset,
                    metrics = metrics,
                    previewBitmap = previewBitmap,
                    onStartMirroring = onStartMirroring,
                    onStopMirroring = onStopMirroring
                )
                DeviceConnectionCard(
                    isCompact = isCompact,
                    discoveredDevices = discoveredDevices,
                    selectedDeviceId = selectedDeviceId,
                    isScanning = isScanning,
                    manualIp = manualIp,
                    manualPort = manualPort,
                    endpointError = endpointError,
                    onScanDevices = onScanDevices,
                    onSelectDevice = onSelectDevice,
                    onManualIpChanged = onManualIpChanged,
                    onManualPortChanged = onManualPortChanged,
                    onConnectManual = onConnectManual
                )
                QualityPresetsCard(
                    selectedPreset = selectedPreset,
                    onSelectPreset = onSelectPreset
                )
                SourceAndModulesCard(
                    isCompact = isCompact,
                    captureSource = captureSource,
                    audioModuleState = audioModuleState,
                    selectedPhotoName = selectedPhotoName,
                    onSelectCaptureSource = onSelectCaptureSource,
                    onPickPhoto = onPickPhoto,
                    onToggleAudioModule = onToggleAudioModule
                )
                ConnectionLifecycleCard(
                    connectionState = connectionState,
                    statusDetail = statusDetail,
                    metrics = metrics,
                    localLanIp = localLanIp
                )
            }
        }
    }
}

@Composable
private fun StreamControlCard(
    isCompact: Boolean,
    isStreaming: Boolean,
    connectionState: ConnectionState,
    statusDetail: String,
    activeTargetLabel: String,
    selectedPreset: QualityPreset,
    metrics: StreamMetrics,
    previewBitmap: Bitmap?,
    onStartMirroring: () -> Unit,
    onStopMirroring: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HizColors.Surface)
            .border(1.dp, HizColors.SurfaceBorder, RoundedCornerShape(18.dp))
            .padding(if (isCompact) 16.dp else 20.dp)
    ) {
        if (isCompact) {
            Text(
                text = "Verici Kontrol Paneli",
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = statusDetail,
                color = HizColors.TextSecondary,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            StreamPrimaryActionButton(
                isStreaming = isStreaming,
                connectionState = connectionState,
                fillWidth = true,
                onStartMirroring = onStartMirroring,
                onStopMirroring = onStopMirroring
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Verici Kontrol Paneli",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = statusDetail,
                        color = HizColors.TextSecondary,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                StreamPrimaryActionButton(
                    isStreaming = isStreaming,
                    connectionState = connectionState,
                    fillWidth = false,
                    onStartMirroring = onStartMirroring,
                    onStopMirroring = onStopMirroring
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isCompact) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PreviewMonitorBox(
                    previewBitmap = previewBitmap,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                )
                MetricsGrid2x2(
                    isStreaming = isStreaming,
                    connectionState = connectionState,
                    selectedPreset = selectedPreset,
                    metrics = metrics,
                    activeTargetLabel = activeTargetLabel
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PreviewMonitorBox(
                    previewBitmap = previewBitmap,
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(16f / 9f)
                )
                Column(
                    modifier = Modifier.weight(1.25f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricsGrid2x2(
                        isStreaming = isStreaming,
                        connectionState = connectionState,
                        selectedPreset = selectedPreset,
                        metrics = metrics,
                        activeTargetLabel = activeTargetLabel
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamPrimaryActionButton(
    isStreaming: Boolean,
    connectionState: ConnectionState,
    fillWidth: Boolean,
    onStartMirroring: () -> Unit,
    onStopMirroring: () -> Unit
) {
    if (!isStreaming) {
        Box(
            modifier = Modifier
                .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                .clip(RoundedCornerShape(12.dp))
                .background(HizColors.AccentCyan)
                .hizDpadFocusable(
                    shape = RoundedCornerShape(12.dp),
                    unfocusedBorderColor = HizColors.AccentCyan,
                    onClick = onStartMirroring
                )
                .padding(horizontal = 18.dp, vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (connectionState == ConnectionState.DROPPED) {
                        Icons.Rounded.Refresh
                    } else {
                        Icons.Rounded.PlayArrow
                    },
                    contentDescription = null,
                    tint = Color(0xFF051018),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Yansıtmayı Başlat",
                    color = Color(0xFF051018),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    } else {
        Box(
            modifier = Modifier
                .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                .clip(RoundedCornerShape(12.dp))
                .background(HizColors.StatusCoral)
                .hizDpadFocusable(
                    shape = RoundedCornerShape(12.dp),
                    unfocusedBorderColor = HizColors.StatusCoral,
                    onClick = onStopMirroring
                )
                .padding(horizontal = 18.dp, vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Stop,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Durdur",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun PreviewMonitorBox(
    previewBitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(HizColors.Background)
            .border(1.dp, HizColors.SurfaceBorder, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (previewBitmap != null) {
            Image(
                bitmap = previewBitmap.asImageBitmap(),
                contentDescription = "Canlı yansıtma önizlemesi",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Cast,
                    contentDescription = null,
                    tint = HizColors.TextMuted,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Yayın önizlemesi",
                    color = HizColors.TextMuted,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun MetricsGrid2x2(
    isStreaming: Boolean,
    connectionState: ConnectionState,
    selectedPreset: QualityPreset,
    metrics: StreamMetrics,
    activeTargetLabel: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricTile(
                title = "Tahmini Gecikme",
                value = if (isStreaming || connectionState == ConnectionState.CONNECTED) {
                    "${metrics.estimatedLatencyMs} ms"
                } else {
                    "—"
                },
                valueColor = Color.White,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                title = "Anlık Kare Hızı",
                value = if (isStreaming) "${metrics.fps} FPS" else "0 / ${selectedPreset.fps} FPS",
                valueColor = HizColors.AccentCyan,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricTile(
                title = "Çözünürlük",
                value = "${selectedPreset.targetWidth}x${selectedPreset.targetHeight}",
                valueColor = HizColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                title = "Hedef Cihaz",
                value = activeTargetLabel,
                valueColor = HizColors.AccentCyan,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MetricTile(
    title: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(HizColors.SurfaceElevated)
            .border(1.dp, HizColors.SurfaceBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(
            text = title,
            color = HizColors.TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DeviceConnectionCard(
    isCompact: Boolean,
    discoveredDevices: List<DiscoveredDevice>,
    selectedDeviceId: String?,
    isScanning: Boolean,
    manualIp: String,
    manualPort: String,
    endpointError: String?,
    onScanDevices: () -> Unit,
    onSelectDevice: (DiscoveredDevice) -> Unit,
    onManualIpChanged: (String) -> Unit,
    onManualPortChanged: (String) -> Unit,
    onConnectManual: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HizColors.Surface)
            .border(1.dp, HizColors.SurfaceBorder, RoundedCornerShape(18.dp))
            .padding(if (isCompact) 16.dp else 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Yerel Ağ Cihaz Keşfi (LAN)",
                    color = Color.White,
                    fontSize = if (isCompact) 17.sp else 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "UDP broadcast keşif ile aynı ağdaki Hız TV alıcılarını bulun.",
                    color = HizColors.TextSecondary,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(HizColors.SurfaceElevated)
                    .hizDpadFocusable(
                        shape = RoundedCornerShape(12.dp),
                        onClick = onScanDevices
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = HizColors.AccentCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isScanning) "Taranıyor" else "Ağı Tara",
                        color = HizColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (discoveredDevices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(HizColors.Background)
                    .border(1.dp, HizColors.SurfaceBorder, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "Otomatik keşfedilen alıcı bulunamadı. TV'de Alıcı Modu'nu açın veya aşağıdan manuel IP/Port girin.",
                    color = HizColors.TextSecondary,
                    fontSize = 13.sp
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                discoveredDevices.forEach { device ->
                    val isSelected = device.id == selectedDeviceId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) HizColors.AccentCyanSoft else HizColors.SurfaceElevated)
                            .hizDpadFocusable(
                                isSelected = isSelected,
                                shape = RoundedCornerShape(12.dp),
                                onClick = { onSelectDevice(device) }
                            )
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tv,
                                contentDescription = null,
                                tint = if (isSelected) HizColors.AccentCyan else HizColors.TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = device.name,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${device.ip}:${device.port} · ${device.source.label}",
                                    color = HizColors.TextSecondary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = if (device.isBusy) "Yayında" else "Hazır",
                            color = if (device.isBusy) HizColors.StatusAmber else HizColors.StatusGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Manuel IP Adresi ve Port ile Bağlan",
            color = HizColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (isCompact) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = manualIp,
                        onValueChange = onManualIpChanged,
                        label = {
                            Text(
                                text = "IP Adresi",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        placeholder = { Text("192.168.1.42", maxLines = 1) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = HizColors.AccentCyan,
                            unfocusedBorderColor = HizColors.SurfaceBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = HizColors.AccentCyan,
                            unfocusedLabelColor = HizColors.TextSecondary
                        ),
                        modifier = Modifier.weight(1.65f)
                    )

                    OutlinedTextField(
                        value = manualPort,
                        onValueChange = onManualPortChanged,
                        label = {
                            Text(
                                text = "Port",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        placeholder = { Text("9876", maxLines = 1) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = HizColors.AccentCyan,
                            unfocusedBorderColor = HizColors.SurfaceBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = HizColors.AccentCyan,
                            unfocusedLabelColor = HizColors.TextSecondary
                        ),
                        modifier = Modifier.weight(0.95f)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(HizColors.SurfaceElevated)
                        .hizDpadFocusable(
                            shape = RoundedCornerShape(12.dp),
                            onClick = onConnectManual
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "IP ile Bağlan",
                        color = HizColors.AccentCyan,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = manualIp,
                    onValueChange = onManualIpChanged,
                    label = { Text("IP Adresi") },
                    placeholder = { Text("192.168.1.42") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HizColors.AccentCyan,
                        unfocusedBorderColor = HizColors.SurfaceBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = HizColors.AccentCyan,
                        unfocusedLabelColor = HizColors.TextSecondary
                    ),
                    modifier = Modifier.weight(1.7f)
                )

                OutlinedTextField(
                    value = manualPort,
                    onValueChange = onManualPortChanged,
                    label = { Text("Port") },
                    placeholder = { Text("9876") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HizColors.AccentCyan,
                        unfocusedBorderColor = HizColors.SurfaceBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = HizColors.AccentCyan,
                        unfocusedLabelColor = HizColors.TextSecondary
                    ),
                    modifier = Modifier.weight(0.9f)
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(HizColors.SurfaceElevated)
                        .hizDpadFocusable(
                            shape = RoundedCornerShape(12.dp),
                            onClick = onConnectManual
                        )
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = "Bağlan",
                        color = HizColors.AccentCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        if (endpointError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = HizColors.StatusCoral,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = endpointError,
                    color = HizColors.StatusCoral,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun QualityPresetsCard(
    selectedPreset: QualityPreset,
    onSelectPreset: (QualityPreset) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HizColors.Surface)
            .border(1.dp, HizColors.SurfaceBorder, RoundedCornerShape(18.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Kalite Ön Ayarları",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        QualityPreset.entries.forEach { preset ->
            val active = preset == selectedPreset
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) HizColors.AccentCyanSoft else HizColors.SurfaceElevated)
                    .hizDpadFocusable(
                        isSelected = active,
                        shape = RoundedCornerShape(12.dp),
                        onClick = { onSelectPreset(preset) }
                    )
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = preset.label,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${preset.fps} FPS",
                        color = HizColors.AccentCyan,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = preset.description,
                    color = HizColors.TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${preset.targetWidth}x${preset.targetHeight} · ${(preset.bitrateKbps / 1000f)} Mbps · Tampon: ${preset.bufferFrames} kare",
                    color = HizColors.TextPrimary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceAndModulesCard(
    isCompact: Boolean,
    captureSource: CaptureSourceType,
    audioModuleState: AudioModuleState,
    selectedPhotoName: String?,
    onSelectCaptureSource: (CaptureSourceType) -> Unit,
    onPickPhoto: () -> Unit,
    onToggleAudioModule: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HizColors.Surface)
            .border(1.dp, HizColors.SurfaceBorder, RoundedCornerShape(18.dp))
            .padding(if (isCompact) 16.dp else 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Yayın Kaynağı ve Genişletilebilir Modüller",
            color = Color.White,
            fontSize = if (isCompact) 17.sp else 18.sp,
            fontWeight = FontWeight.Bold
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CaptureSourceType.entries.forEach { src ->
                val isSelected = src == captureSource
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) HizColors.AccentCyanSoft else HizColors.SurfaceElevated)
                        .hizDpadFocusable(
                            isSelected = isSelected,
                            shape = RoundedCornerShape(10.dp),
                            onClick = {
                                if (src == CaptureSourceType.MEDIA_PHOTO) {
                                    onPickPhoto()
                                } else {
                                    onSelectCaptureSource(src)
                                }
                            }
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = src.label,
                        color = if (isSelected) Color.White else HizColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // Phase-2 Audio Transport Module
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(HizColors.SurfaceElevated)
                .border(1.dp, HizColors.SurfaceBorder, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VolumeUp,
                        contentDescription = null,
                        tint = HizColors.AccentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Ses Aktarımı · Faz 2 Modülü",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (audioModuleState.isEnabled) HizColors.StatusGreenSoft else HizColors.Background)
                        .hizDpadFocusable(
                            isSelected = audioModuleState.isEnabled,
                            shape = RoundedCornerShape(8.dp),
                            onClick = onToggleAudioModule
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (audioModuleState.isEnabled) "Hazır (Açık)" else "Hazır",
                        color = if (audioModuleState.isEnabled) HizColors.StatusGreen else HizColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            Text(
                text = audioModuleState.statusSummary,
                color = HizColors.TextSecondary,
                fontSize = 12.sp
            )
        }

        // Phase-2 Media / Photo Share Module
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(HizColors.SurfaceElevated)
                .border(1.dp, HizColors.SurfaceBorder, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Image,
                        contentDescription = null,
                        tint = HizColors.AccentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Medya / Fotoğraf Paylaşımı · Faz 2",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(HizColors.Background)
                        .hizDpadFocusable(
                            shape = RoundedCornerShape(8.dp),
                            onClick = onPickPhoto
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Fotoğraf Seç",
                        color = HizColors.AccentCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            Text(
                text = selectedPhotoName?.let { "Seçili görsel: $it" }
                    ?: "Seçili fotoğrafı yerel ağ üzerinden TV'ye aktarın.",
                color = HizColors.TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ConnectionLifecycleCard(
    connectionState: ConnectionState,
    statusDetail: String,
    metrics: StreamMetrics,
    localLanIp: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HizColors.Surface)
            .border(1.dp, HizColors.SurfaceBorder, RoundedCornerShape(18.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Bağlantı ve Akış Durumu",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = connectionState.label,
            color = HizColors.AccentCyan,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = statusDetail,
            color = HizColors.TextSecondary,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Gönderilen Kare",
                color = HizColors.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = metrics.framesSent.toString(),
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                maxLines = 1,
                softWrap = false
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Atlanan Kare (Tampon)",
                color = HizColors.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = metrics.droppedFrames.toString(),
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                maxLines = 1,
                softWrap = false
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Yerel IP",
                color = HizColors.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = localLanIp,
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

package com.quackify.compose.sheets

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quackify.compose.R
import com.quackify.backend.model.ConnectDevice
import com.quackify.backend.model.DeviceKind
import com.quackify.compose.components.EncoreIconButton
import com.quackify.compose.components.EncorePlayIndicator
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreCornerRadii
import com.quackify.compose.theme.EncoreSpacers
import com.quackify.compose.theme.EncoreAppTheme
import com.quackify.compose.theme.EncoreTheme
import androidx.compose.ui.res.stringResource

enum class DeviceType {
    PHONE,
    SPEAKER,
    COMPUTER,
    CAST,
    CAR
}

data class EncoreDevice(
    val id: String,
    val name: String,
    val type: DeviceType,
    val isCurrent: Boolean = false,
    val protocol: String = "",
    val volumeFraction: Float = -1f,
    val local: Boolean = false
)

/**
 * - Current listening device card with animated green equalizer soundwaves and volume slider
 * - Volume control with volume icon indicators
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncoreConnectDeviceSheet(
    onDismissRequest: () -> Unit,
    currentDeviceName: String,
    onSelectDevice: (EncoreDevice) -> Unit,
    devices: List<EncoreDevice> = emptyList(),
    initialVolume: Float = 0.7f,
    onVolumeChange: (String, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var volume by remember { mutableFloatStateOf(initialVolume.coerceIn(0f, 1f)) }
    val volumeEnabled = devices.firstOrNull { it.isCurrent }?.local ?: true

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        properties = BackNotDismissSheet,
        sheetState = sheetState,
        containerColor = EncoreColors.Card,
        contentColor = EncoreColors.TextPrimary,
        shape = RoundedCornerShape(topStart = EncoreCornerRadii.Larger3, topEnd = EncoreCornerRadii.Larger3),
        dragHandle = null,
        modifier = modifier
    ) {
        BackHandler { onDismissRequest() }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = EncoreSpacers.Base)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = EncoreSpacers.Base),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.listening_on),
                    style = EncoreTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                EncoreIconButton(
                    iconRes = R.drawable.encore_icon_x_24,
                    contentDescription = stringResource(R.string.close),
                    onClick = onDismissRequest,
                    tint = EncoreColors.TextPrimary,
                    size = 36.dp,
                    iconSize = 20.dp
                )
            }

            Spacer(modifier = Modifier.height(EncoreSpacers.Base))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = EncoreSpacers.Base)
                    .clip(RoundedCornerShape(EncoreCornerRadii.Larger2))
                    .background(Color(0xFF1E3A25))
                    .padding(EncoreSpacers.Base)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(EncoreColors.Green),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.encore_icon_device_mobile_24),
                                contentDescription = stringResource(R.string.active_device),
                                tint = EncoreColors.PureBlack,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(EncoreSpacers.Tighter))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.current_device),
                                style = EncoreTheme.typography.marginalBold.copy(color = EncoreColors.Green)
                            )
                            Text(
                                text = currentDeviceName,
                                style = EncoreTheme.typography.bodyMediumBold.copy(color = EncoreColors.TextPrimary)
                            )
                        }

                        EncorePlayIndicator(
                            isPlaying = true,
                            color = EncoreColors.Green,
                            size = 16.dp
                        )
                    }

                    Spacer(modifier = Modifier.height(EncoreSpacers.Base))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (volume == 0f) R.drawable.encore_icon_volume_off_24 else R.drawable.encore_icon_volume_24
                            ),
                            contentDescription = stringResource(R.string.volume),
                            tint = if (volumeEnabled) EncoreColors.Green else EncoreColors.TextDisabled,
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(EncoreSpacers.Tighter))

                        Slider(
                            value = volume,
                            onValueChange = { volume = it },
                            onValueChangeFinished = {
                                val target = devices.firstOrNull { it.isCurrent }
                                if (target != null) {
                                    onVolumeChange(target.id, (volume * 65535f).toInt())
                                }
                            },
                            enabled = volumeEnabled,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = EncoreColors.TextPrimary,
                                activeTrackColor = EncoreColors.Green,
                                inactiveTrackColor = EncoreColors.Divider
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(EncoreSpacers.Looser2))

            Text(
                text = stringResource(R.string.select_another_device),
                style = EncoreTheme.typography.bodySmallBold.copy(color = EncoreColors.TextSecondary),
                modifier = Modifier.padding(horizontal = EncoreSpacers.Base)
            )

            Spacer(modifier = Modifier.height(EncoreSpacers.Tighter2))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.85f)
            ) {
                items(devices.filter { !it.isCurrent }, key = { it.id }) { device ->
                    DeviceRow(
                        device = device,
                        onClick = { onSelectDevice(device) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: EncoreDevice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconRes = when (device.type) {
        DeviceType.PHONE -> R.drawable.encore_icon_device_mobile_24
        DeviceType.SPEAKER -> R.drawable.encore_icon_device_speaker_24
        DeviceType.COMPUTER -> R.drawable.encore_icon_devices_24
        DeviceType.CAST -> R.drawable.encore_icon_chromecast_disconnected_24
        DeviceType.CAR -> R.drawable.encore_icon_device_car_24
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Tighter),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = device.name,
            tint = EncoreColors.TextSecondary,
            modifier = Modifier.size(26.dp)
        )

        Spacer(modifier = Modifier.width(EncoreSpacers.Base))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name,
                style = EncoreTheme.typography.bodyMediumBold.copy(color = EncoreColors.TextPrimary)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = device.protocol.ifBlank { stringResource(R.string.connect) },
                style = EncoreTheme.typography.marginal.copy(color = EncoreColors.TextSecondary)
            )
        }
    }
}

@Composable
fun ConnectDevice.toEncoreDevice(): EncoreDevice = EncoreDevice(
    id = id,
    name = name.ifBlank { stringResource(R.string.unknown_device) },
    type = when (kind) {
        DeviceKind.PHONE, DeviceKind.TABLET -> DeviceType.PHONE
        DeviceKind.COMPUTER -> DeviceType.COMPUTER
        DeviceKind.SPEAKER, DeviceKind.RECEIVER, DeviceKind.STICK,
        DeviceKind.DONGLE, DeviceKind.CONSOLE, DeviceKind.TV -> DeviceType.SPEAKER
        DeviceKind.CAR -> DeviceType.CAR
        DeviceKind.CAST -> DeviceType.CAST
        DeviceKind.UNKNOWN -> DeviceType.COMPUTER
    },
    isCurrent = active,
    protocol = brand,
    volumeFraction = if (volumeSteps > 0) (volume.toFloat() / volumeSteps.toFloat()).coerceIn(0f, 1f) else -1f,
    local = local
)

@Preview
@Composable
fun PreviewEncoreConnectDeviceSheet() {
    EncoreAppTheme {
        EncoreConnectDeviceSheet(
            onDismissRequest = {},
            currentDeviceName = "Galaxy S24 Ultra",
            onSelectDevice = {}
        )
    }
}

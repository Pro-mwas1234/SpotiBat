package com.quackify.compose.sheets

import androidx.compose.animation.animateColorAsState
import android.os.Build
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
    val local: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncoreConnectDeviceSheet(
    onDismissRequest: () -> Unit,
    currentDeviceName: String,
    currentTrackTitle: String,
    currentDeviceKind: DeviceKind?,
    currentDeviceLocal: Boolean,
    onSelectDevice: (EncoreDevice) -> Unit,
    devices: List<EncoreDevice> = emptyList(),
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = EncoreSpacers.Base)
                    .clip(RoundedCornerShape(EncoreCornerRadii.Larger2))
                    .background(Color(0xFF242424))
                    .padding(EncoreSpacers.Base),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentDeviceName.ifBlank { stringResource(R.string.this_phone) },
                        style = EncoreTheme.typography.bodyMediumBold.copy(color = EncoreColors.TextPrimary)
                    )
                    if (currentDeviceName.isBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = Build.MODEL,
                            style = EncoreTheme.typography.marginal.copy(color = EncoreColors.TextSecondary)
                        )
                    }
                    if (currentTrackTitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(EncoreSpacers.Tighter))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (currentDeviceName.isBlank()) {
                                    currentTrackTitle
                                } else {
                                    stringResource(R.string.track_on_device, currentTrackTitle, currentDeviceName)
                                },
                                style = EncoreTheme.typography.marginal.copy(color = EncoreColors.TextSecondary),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(EncoreSpacers.Tighter4))
                            EncorePlayIndicator(
                                isPlaying = true,
                                color = EncoreColors.Green,
                                size = 16.dp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(EncoreSpacers.Base))

                Icon(
                    painter = painterResource(id = currentDeviceIconRes(currentDeviceKind, currentDeviceLocal)),
                    contentDescription = stringResource(R.string.active_device),
                    tint = EncoreColors.TextPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(EncoreSpacers.Looser2))

            Text(
                text = stringResource(R.string.select_another_device),
                style = EncoreTheme.typography.bodySmallBold.copy(color = EncoreColors.TextSecondary),
                modifier = Modifier.padding(horizontal = EncoreSpacers.Base)
            )

            Spacer(modifier = Modifier.height(EncoreSpacers.Tighter2))

            val playingRemote = devices.any { it.isCurrent && !it.local }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.85f)
            ) {
                items(
                    devices.filter { !it.isCurrent && (!it.local || playingRemote) },
                    key = { it.id }
                ) { device ->
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
    name = if (local) stringResource(R.string.this_phone) else name.ifBlank { stringResource(R.string.unknown_device) },
    type = when (kind) {
        DeviceKind.PHONE, DeviceKind.TABLET, DeviceKind.HEADPHONES -> DeviceType.PHONE
        DeviceKind.COMPUTER -> DeviceType.COMPUTER
        DeviceKind.SPEAKER, DeviceKind.RECEIVER, DeviceKind.STICK,
        DeviceKind.DONGLE, DeviceKind.CONSOLE, DeviceKind.TV -> DeviceType.SPEAKER
        DeviceKind.CAR -> DeviceType.CAR
        DeviceKind.CAST -> DeviceType.CAST
        DeviceKind.UNKNOWN -> DeviceType.COMPUTER
    },
    isCurrent = active,
    protocol = brand,
    local = local
)

private fun currentDeviceIconRes(kind: DeviceKind?, isLocal: Boolean): Int = when {
    isLocal -> R.drawable.encore_icon_device_mobile_24
    kind == DeviceKind.HEADPHONES -> R.drawable.encore_icon_device_headphones_24
    kind == DeviceKind.PHONE || kind == DeviceKind.TABLET -> R.drawable.encore_icon_device_mobile_24
    kind == DeviceKind.SPEAKER || kind == DeviceKind.RECEIVER -> R.drawable.encore_icon_device_speaker_24
    kind == DeviceKind.TV || kind == DeviceKind.STICK -> R.drawable.encore_icon_device_tv_24
    kind == DeviceKind.CAR -> R.drawable.encore_icon_device_car_24
    kind == DeviceKind.CAST -> R.drawable.encore_icon_chromecast_connected_24
    else -> R.drawable.encore_icon_devices_24
}

@Preview
@Composable
fun PreviewEncoreConnectDeviceSheet() {
    EncoreAppTheme {
        EncoreConnectDeviceSheet(
            onDismissRequest = {},
            currentDeviceName = "",
            currentTrackTitle = "Blinding Lights",
            currentDeviceKind = null,
            currentDeviceLocal = true,
            onSelectDevice = {}
        )
    }
}

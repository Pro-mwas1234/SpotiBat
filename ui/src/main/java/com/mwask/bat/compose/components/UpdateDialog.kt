package com.mwask.bat.compose.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mwask.bat.compose.R
import com.mwask.bat.compose.screens.AppUpdate
import com.mwask.bat.compose.theme.EncoreColors
import com.mwask.bat.compose.theme.EncoreTheme
import androidx.compose.ui.res.stringResource

enum class UpdateInstallPhase { Idle, Downloading, Installing }

@Composable
fun UpdateDialog(
    update: AppUpdate,
    currentVersion: String,
    phase: UpdateInstallPhase,
    downloadedMb: Float,
    totalMb: Float,
    speedMbps: Float,
    onLater: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler { if (phase == UpdateInstallPhase.Idle) onDismiss() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(EncoreColors.CardElevated)
                .padding(20.dp)
        ) {
            Text(
                text = stringResource(R.string.update_available),
                style = EncoreTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = EncoreColors.TextPrimary
                )
            )
            Spacer(modifier = Modifier.padding(top = 14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = currentVersion,
                    style = EncoreTheme.typography.bodyMedium.copy(color = EncoreColors.TextSecondary)
                )
                Icon(
                    painter = painterResource(R.drawable.encore_icon_arrow_right_24),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .size(18.dp),
                    tint = EncoreColors.TextSecondary
                )
                Text(
                    text = update.version,
                    style = EncoreTheme.typography.bodyMediumBold.copy(color = EncoreColors.Green),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(EncoreColors.Green.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.padding(top = 14.dp))
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (update.notes.isBlank()) {
                    Text(
                        text = stringResource(R.string.no_release_notes),
                        style = EncoreTheme.typography.bodyMedium.copy(color = EncoreColors.TextSecondary)
                    )
                } else {
                    MarkdownText(markdown = update.notes)
                }
            }
            Spacer(modifier = Modifier.padding(top = 18.dp))
            when (phase) {
                UpdateInstallPhase.Downloading -> {
                    LinearProgressIndicator(
                        progress = {
                            if (totalMb > 0f) (downloadedMb / totalMb).coerceIn(0f, 1f) else 0f
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = EncoreColors.Green,
                        trackColor = Color.White.copy(alpha = 0.14f)
                    )
                    Spacer(modifier = Modifier.padding(top = 8.dp))
                    Text(
                        text = if (totalMb > 0f) {
                            "%.1f / %.1f MB \u00B7 %.1f MB/s".format(downloadedMb, totalMb, speedMbps)
                        } else {
                            "%.1f MB \u00B7 %.1f MB/s".format(downloadedMb, speedMbps)
                        },
                        style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                UpdateInstallPhase.Installing -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = EncoreColors.Green
                        )
                        Spacer(modifier = Modifier.padding(horizontal = 5.dp))
                        Text(
                            text = stringResource(R.string.installing),
                            style = EncoreTheme.typography.bodyMediumBold.copy(color = EncoreColors.TextPrimary)
                        )
                    }
                }
                UpdateInstallPhase.Idle -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.later),
                            style = EncoreTheme.typography.bodyMediumBold.copy(color = EncoreColors.TextSecondary),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onLater() }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = stringResource(R.string.install),
                            style = EncoreTheme.typography.bodyMediumBold.copy(color = Color.White),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(EncoreColors.Green)
                                .clickable { onInstall() }
                                .padding(horizontal = 22.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

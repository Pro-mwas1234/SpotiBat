package com.quackify.compose.player

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quackify.compose.R
import com.quackify.compose.components.EncoreIconButton
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreTheme
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.ui.res.stringResource

/**
 * - `lyrics_share_button_action_label`
 * - `enhanced_share_card_text_alignment_state_center` / left / right
 * - Story card preview with multi-line selection (up to 5 lines)
 * - Color swatch background palette selection
 */
@Composable
fun LyricsShareDialog(
    title: String,
    artist: String,
    lyrics: List<TimeSyncedLyric>,
    initialDominantColor: Color,
    onDismiss: () -> Unit,
    onShareConfirm: (List<String>, Color, TextAlign) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedIndices = remember(lyrics) {
        mutableStateListOf<Int>().apply {
            if (lyrics.isNotEmpty()) add(0)
            if (lyrics.size > 1) add(1)
        }
    }

    val palette = remember {
        listOf(
            initialDominantColor,
            Color(0xFF1DB954),
            Color(0xFFE91429),
            Color(0xFF8D67AB), // Purple
            Color(0xFF2E77D0), // Blue
            Color(0xFFE1306C), // Magenta
            Color(0xFFF59B23), // Amber
            Color(0xFF282828)  // Dark Graphite
        )
    }

    var selectedColor by remember { mutableStateOf(palette.first()) }
    var textAlignIndex by remember { mutableIntStateOf(0) } // 0: Left, 1: Center, 2: Right

    val textAlign = when (textAlignIndex) {
        0 -> TextAlign.Start
        1 -> TextAlign.Center
        else -> TextAlign.End
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    val cardLayer = rememberGraphicsLayer()
    val context = LocalContext.current
    val shareScope = rememberCoroutineScope()

    BackHandler(onBack = onDismiss)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EncoreIconButton(
                iconRes = R.drawable.encore_icon_x_24,
                contentDescription = stringResource(R.string.close),
                onClick = onDismiss,
                tint = Color.White
            )

            Text(
                text = stringResource(R.string.share_lyrics),
                style = EncoreTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(EncoreColors.Green)
                    .clickable {
                        val selectedLines = selectedIndices.sorted().map { lyrics[it].text }
                        shareScope.launch {
                            val card = runCatching {
                                cardLayer.toImageBitmap().asAndroidBitmap()
                            }.getOrNull()
                            shareLyricCard(context, card, selectedLines, title, artist)
                            onShareConfirm(selectedLines, selectedColor, textAlign)
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.share),
                    style = EncoreTheme.typography.bodySmallBold.copy(
                        color = EncoreColors.PureBlack,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                ShareCardPreview(
                    title = title,
                    artist = artist,
                    lyrics = lyrics,
                    selectedIndices = selectedIndices,
                    selectedColor = selectedColor,
                    textAlign = textAlign,
                    textAlignIndex = textAlignIndex,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .drawWithContent {
                            cardLayer.record(IntSize(size.width.toInt(), size.height.toInt())) {
                                this@drawWithContent.drawContent()
                            }
                            drawContent()
                        }
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier
                        .width(300.dp)
                        .fillMaxHeight()
                ) {
                    ShareFormatControls(
                        textAlignIndex = textAlignIndex,
                        onCycleAlignment = { textAlignIndex = (textAlignIndex + 1) % 3 },
                        palette = palette,
                        selectedColor = selectedColor,
                        onSelectColor = { selectedColor = it },
                        selectedCount = selectedIndices.size
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ShareLineSelector(
                        lyrics = lyrics,
                        selectedIndices = selectedIndices,
                        selectedColor = selectedColor,
                        onToggleLine = { idx ->
                            if (selectedIndices.contains(idx)) {
                                selectedIndices.remove(idx)
                            } else if (selectedIndices.size < 5) {
                                selectedIndices.add(idx)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        } else {
            ShareCardPreview(
                title = title,
                artist = artist,
                lyrics = lyrics,
                selectedIndices = selectedIndices,
                selectedColor = selectedColor,
                textAlign = textAlign,
                textAlignIndex = textAlignIndex,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .drawWithContent {
                        cardLayer.record(IntSize(size.width.toInt(), size.height.toInt())) {
                            this@drawWithContent.drawContent()
                        }
                        drawContent()
                    }
            )

            Spacer(modifier = Modifier.height(16.dp))

            ShareFormatControls(
                textAlignIndex = textAlignIndex,
                onCycleAlignment = { textAlignIndex = (textAlignIndex + 1) % 3 },
                palette = palette,
                selectedColor = selectedColor,
                onSelectColor = { selectedColor = it },
                selectedCount = selectedIndices.size
            )

            Spacer(modifier = Modifier.height(12.dp))

            ShareLineSelector(
                lyrics = lyrics,
                selectedIndices = selectedIndices,
                selectedColor = selectedColor,
                onToggleLine = { idx ->
                    if (selectedIndices.contains(idx)) {
                        selectedIndices.remove(idx)
                    } else if (selectedIndices.size < 5) {
                        selectedIndices.add(idx)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            )
        }
    }
}

@Composable
private fun ShareCardPreview(
    title: String,
    artist: String,
    lyrics: List<TimeSyncedLyric>,
    selectedIndices: List<Int>,
    selectedColor: Color,
    textAlign: TextAlign,
    textAlignIndex: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .shadow(elevation = 16.dp, shape = RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        selectedColor,
                        selectedColor.copy(alpha = 0.85f),
                        Color(0xFF101010)
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = when (textAlignIndex) {
                0 -> Alignment.Start
                1 -> Alignment.CenterHorizontally
                else -> Alignment.End
            }
        ) {
            if (selectedIndices.isEmpty()) {
                Text(
                    text = stringResource(R.string.tap_up_to_5_lines_to_share),
                    style = EncoreTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.6f)),
                    textAlign = textAlign
                )
            } else {
                selectedIndices.sorted().forEach { idx ->
                    Text(
                        text = lyrics[idx].text,
                        style = EncoreTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            lineHeight = 28.sp,
                            color = Color.White
                        ),
                        textAlign = textAlign,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = EncoreTheme.typography.bodySmallBold.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = artist,
                        style = EncoreTheme.typography.marginal.copy(
                            color = Color.White.copy(alpha = 0.7f)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(EncoreColors.PureBlack),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "♫",
                            fontSize = 11.sp,
                            color = EncoreColors.Green,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.quackify),
                        style = EncoreTheme.typography.bodySmallBold.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = Color.White
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareFormatControls(
    textAlignIndex: Int,
    onCycleAlignment: () -> Unit,
    palette: List<Color>,
    selectedColor: Color,
    onSelectColor: (Color) -> Unit,
    selectedCount: Int
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF282828))
                    .clickable(onClick = onCycleAlignment)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = when (textAlignIndex) {
                        0 -> stringResource(R.string.align_left)
                        1 -> stringResource(R.string.align_center)
                        else -> stringResource(R.string.align_right)
                    },
                    style = EncoreTheme.typography.marginal.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                )
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                palette.forEach { color ->
                    val isSelected = color == selectedColor
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = Color.White,
                                shape = CircleShape
                            )
                            .clickable { onSelectColor(color) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(R.string.select_lines_limit, selectedCount),
            style = EncoreTheme.typography.marginal.copy(
                fontWeight = FontWeight.Medium,
                color = EncoreColors.TextSecondary
            )
        )
    }
}

@Composable
private fun ShareLineSelector(
    lyrics: List<TimeSyncedLyric>,
    selectedIndices: List<Int>,
    selectedColor: Color,
    onToggleLine: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E1E1E))
            .padding(8.dp)
    ) {
        itemsIndexed(lyrics) { idx, line ->
            val isChecked = selectedIndices.contains(idx)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isChecked) selectedColor.copy(alpha = 0.35f) else Color.Transparent)
                    .clickable { onToggleLine(idx) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .border(
                            width = 1.5.dp,
                            color = if (isChecked) selectedColor else Color.Gray,
                            shape = CircleShape
                        )
                        .background(if (isChecked) selectedColor else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    if (isChecked) {
                        Icon(
                            painter = painterResource(id = R.drawable.encore_icon_check_24),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = line.text,
                    style = EncoreTheme.typography.bodySmall.copy(
                        fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal,
                        color = if (isChecked) Color.White else Color.White.copy(alpha = 0.7f)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Fires the Android share sheet with the lyric card as a png image (and the
 * lyric text as a fallback for apps that only accept plain text).
 */
private fun shareLyricCard(
    context: Context,
    card: Bitmap?,
    lines: List<String>,
    title: String,
    artist: String,
) {
    val text = buildString {
        append(lines.joinToString("\n"))
        append("\n\n")
        append(title)
        if (artist.isNotBlank()) append(" • ").append(artist)
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = if (card != null) "image/png" else "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    if (card != null) {
        val shared = runCatching {
            val file = File(context.cacheDir, "share/lyric_card.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { out -> card.compress(Bitmap.CompressFormat.PNG, 100, out) }
            FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        }.getOrNull()
        if (shared != null) {
            send.putExtra(Intent.EXTRA_STREAM, shared)
            send.clipData = ClipData.newRawUri("lyric card", shared)
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            send.type = "text/plain"
        }
    }
    runCatching { context.startActivity(Intent.createChooser(send, context.getString(R.string.share_lyrics))) }
}

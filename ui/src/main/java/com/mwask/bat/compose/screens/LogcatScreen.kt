package com.mwask.bat.compose.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Process
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mwask.bat.compose.R
import com.mwask.bat.compose.components.EncoreIconButton
import com.mwask.bat.compose.theme.DmMono
import com.mwask.bat.compose.theme.EncoreColors
import com.mwask.bat.compose.theme.EncoreTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogcatEntry(
    val raw: String,
    val level: Char,
)

private val LOGCAT_LINE = Regex("^(\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+(\\d+)\\s+(\\d+)\\s+([VDIWEF])\\s+(.*)$")

private val LEVEL_COLORS = mapOf(
    'V' to Color(0xFF9E9E9E),
    'D' to Color(0xFF4FC3F7),
    'I' to Color(0xFF66BB6A),
    'W' to Color(0xFFFFB74D),
    'E' to Color(0xFFEF5350),
    'F' to Color(0xFFEF5350),
)

private const val MAX_BUFFER = 12000
private const val TRIM_TO = 10000

@Composable
fun LogcatScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val entries = remember { mutableStateListOf<LogcatEntry>() }
    var readerGeneration by remember { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf("") }
    var selectedLevels by remember { mutableStateOf(setOf<Char>()) }
    val listState = rememberLazyListState()
    var followTail by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            if (previous != null) activity.requestedOrientation = previous
        }
    }

    DisposableEffect(readerGeneration) {
        val process = ProcessBuilder(
            listOf("logcat", "-v", "threadtime", "--pid", Process.myPid().toString())
        ).redirectErrorStream(true).start()
        val scope = CoroutineScope(Dispatchers.IO)
        val job = scope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(process.inputStream), 8192)
                var pending = mutableListOf<LogcatEntry>()
                while (isActive) {
                    val line = reader.readLine() ?: break
                    val entry = parseLogcatLine(line) ?: continue
                    pending.add(entry)
                    if (pending.size >= 256) {
                        val batch = pending
                        pending = mutableListOf()
                        withContext(Dispatchers.Main) {
                            entries.addAll(batch)
                            if (entries.size > MAX_BUFFER) entries.removeRange(0, entries.size - TRIM_TO)
                        }
                    }
                }
                if (pending.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        entries.addAll(pending)
                        if (entries.size > MAX_BUFFER) entries.removeRange(0, entries.size - TRIM_TO)
                    }
                }
            } catch (_: Exception) {
            } finally {
                runCatching { process.destroy() }
            }
        }
        onDispose {
            job.cancel()
            runCatching { process.destroy() }
        }
    }

    val visible = entries
        .filter { selectedLevels.isEmpty() || it.level in selectedLevels }
        .filter { filter.isBlank() || it.raw.contains(filter.trim(), ignoreCase = true) }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) followTail = !listState.canScrollForward
    }
    LaunchedEffect(visible.size, followTail) {
        if (followTail && visible.isNotEmpty()) listState.scrollToItem(visible.lastIndex)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(entries.joinToString("\n") { it.raw }.toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    BackHandler { onBackClick() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EncoreColors.Background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EncoreIconButton(
                iconRes = R.drawable.encore_icon_arrow_left_24,
                contentDescription = stringResource(R.string.back),
                onClick = onBackClick,
                tint = EncoreColors.TextPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.logcat),
                style = EncoreTheme.typography.titleLarge.copy(fontSize = 20.sp)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.logcat_line_count, visible.size),
                style = EncoreTheme.typography.marginal,
                modifier = Modifier.padding(end = 12.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(EncoreColors.Card)
                .clickable { }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.encore_icon_search_24),
                contentDescription = null,
                tint = EncoreColors.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            BasicTextField(
                value = filter,
                onValueChange = { filter = it },
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = DmMono,
                    fontSize = 13.sp,
                    color = EncoreColors.TextPrimary
                ),
                cursorBrush = SolidColor(EncoreColors.Green),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (filter.isEmpty()) {
                            Text(
                                text = stringResource(R.string.logcat_filter_hint),
                                style = TextStyle(
                                    fontFamily = DmMono,
                                    fontSize = 13.sp,
                                    color = EncoreColors.TextDisabled
                                )
                            )
                        }
                        inner()
                    }
                }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf('V', 'D', 'I', 'W', 'E').forEach { level ->
                Text(
                    text = level.toString(),
                    style = EncoreTheme.typography.bodySmall.copy(
                        fontFamily = DmMono,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedLevels.isEmpty() || level in selectedLevels) {
                            LEVEL_COLORS[level] ?: EncoreColors.TextPrimary
                        } else {
                            EncoreColors.TextDisabled
                        }
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(EncoreColors.Card)
                        .clickable {
                            selectedLevels = when (level) {
                                in selectedLevels -> selectedLevels - level
                                else -> selectedLevels + level
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Box {
                var resetMenuOpen by remember { mutableStateOf(false) }
                IconButton(
                    onClick = { resetMenuOpen = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.encore_icon_filter_24),
                        contentDescription = stringResource(R.string.logcat_reset_filters),
                        tint = EncoreColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = resetMenuOpen,
                    onDismissRequest = { resetMenuOpen = false },
                    containerColor = EncoreColors.CardElevated
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.logcat_clear_filters),
                                style = EncoreTheme.typography.bodyMedium,
                                color = EncoreColors.TextPrimary
                            )
                        },
                        onClick = {
                            filter = ""
                            selectedLevels = emptySet()
                            resetMenuOpen = false
                        }
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            if (visible.isEmpty()) {
                Text(
                    text = stringResource(R.string.logcat_waiting),
                    style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 32.dp)
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(EncoreColors.PureBlack),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
            ) {
                itemsIndexed(visible) { index, entry ->
                    val color = LEVEL_COLORS[entry.level] ?: EncoreColors.TextSecondary
                    Text(
                        text = entry.raw,
                        style = TextStyle(
                            fontFamily = DmMono,
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            color = color
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (index < visible.lastIndex) {
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.End
            ) {
                var menuOpen by remember { mutableStateOf(false) }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = EncoreColors.CardElevated
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.logcat_restart),
                                style = EncoreTheme.typography.bodyMediumBold,
                                color = EncoreColors.TextPrimary
                            )
                        },
                        onClick = {
                            entries.clear()
                            followTail = true
                            readerGeneration++
                            menuOpen = false
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.logcat_export),
                                style = EncoreTheme.typography.bodyMediumBold,
                                color = EncoreColors.TextPrimary
                            )
                        },
                        onClick = {
                            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                            exportLauncher.launch("quackify_logcat_$stamp.log")
                            menuOpen = false
                        }
                    )
                }
                FloatingActionButton(
                    onClick = { menuOpen = true },
                    containerColor = EncoreColors.Green,
                    contentColor = EncoreColors.PureBlack
                ) {
                    Icon(
                        painter = painterResource(R.drawable.encore_icon_plus_24),
                        contentDescription = stringResource(R.string.logcat_actions),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

private fun parseLogcatLine(line: String): LogcatEntry? {
    val match = LOGCAT_LINE.find(line) ?: return null
    return LogcatEntry(raw = line, level = match.groupValues[4][0])
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

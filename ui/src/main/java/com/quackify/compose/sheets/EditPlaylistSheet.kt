package com.quackify.compose.sheets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quackify.compose.R
import com.quackify.compose.components.EncoreArtworkImage
import com.quackify.compose.theme.EncoreColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.min
import androidx.compose.ui.res.stringResource

private const val NAME_LIMIT = 100
private const val DESCRIPTION_LIMIT = 300
private const val COVER_SIDE = 640

/**
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPlaylistSheet(
    isOpen: Boolean,
    initialName: String,
    initialDescription: String,
    coverUrl: String?,
    canRemoveCover: Boolean,
    onSave: (name: String, description: String, cover: ByteArray?, clearCover: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onDeletePlaylist: (() -> Unit)? = null,
    title: String = stringResource(R.string.name_and_details),
    saveLabel: String = stringResource(R.string.save) 
) {
    if (!isOpen) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    var picked by remember { mutableStateOf<ByteArray?>(null) }
    var pickedFailed by remember { mutableStateOf(false) }
    var removeCover by remember { mutableStateOf(false) }
    var decoding by remember { mutableStateOf(false) }

    val previewBitmap = remember(picked) {
        picked?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        decoding = true
        pickedFailed = false
        scope.launch {
            val jpeg = withContext(Dispatchers.IO) { squareJpeg(context, uri) }
            decoding = false
            if (jpeg == null) {
                pickedFailed = true
            } else {
                picked = jpeg
                removeCover = false
            }
        }
    }

    val hasCover = picked != null || (!removeCover && !coverUrl.isNullOrBlank())
    val hasChanges = (name.trim() != initialName.trim()) ||
            (description.trim() != initialDescription.trim()) ||
            (picked != null) ||
            removeCover
    val canSave = name.isNotBlank() && !decoding && hasChanges

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        properties = DarkSystemBarsSheet,
        sheetState = sheetState,
        containerColor = Color(0xFF1F1F1F),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.cancel),
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.clickable { onDismiss() }
                )

                Text(
                    text = title,
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = saveLabel,
                    style = TextStyle(
                        color = if (canSave) EncoreColors.Green else Color(0xFF1E5230),
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.clickable(enabled = canSave) {
                        onSave(
                            name.trim(),
                            description.trim(),
                            picked,
                            removeCover && picked == null
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(108.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF282828))
                            .clickable { picker.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            decoding -> CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = EncoreColors.TextSecondary,
                                strokeWidth = 2.dp
                            )
                            previewBitmap != null -> Image(
                                bitmap = previewBitmap,
                                contentDescription = stringResource(R.string.new_cover),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            hasCover && !coverUrl.isNullOrBlank() -> EncoreArtworkImage(
                                imageUrl = coverUrl,
                                contentDescription = stringResource(R.string.current_cover),
                                modifier = Modifier.fillMaxSize(),
                                cornerRadius = 8.dp
                            )
                            else -> Icon(
                                painter = painterResource(id = R.drawable.encore_icon_playlist_24),
                                contentDescription = null,
                                tint = EncoreColors.TextSecondary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(Color(0xFF141414))
                            .border(1.5.dp, Color(0xFF282828), CircleShape)
                            .clickable { picker.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.encore_icon_edit_24),
                            contentDescription = stringResource(R.string.edit_photo),
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF363636))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        BasicTextField(
                            value = name,
                            onValueChange = { if (it.length <= NAME_LIMIT) name = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            cursorBrush = SolidColor(EncoreColors.Green),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { innerTextField ->
                                if (name.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.add_playlist_name),
                                        color = EncoreColors.TextSecondary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(68.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF363636))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        BasicTextField(
                            value = description,
                            onValueChange = { if (it.length <= DESCRIPTION_LIMIT) description = it },
                            singleLine = false,
                            maxLines = 3,
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            cursorBrush = SolidColor(EncoreColors.Green),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { innerTextField ->
                                if (description.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.add_description),
                                        color = EncoreColors.TextSecondary,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        onDeletePlaylist?.invoke()
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.encore_icon_delete_24),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = stringResource(R.string.delete_playlist),
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

        }
    }
}

private fun squareJpeg(context: Context, uri: Uri): ByteArray? {
    return runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null

        val sample = maxOf(1, min(width / COVER_SIDE, height / COVER_SIDE))
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null

        val side = min(decoded.width, decoded.height)
        val x = (decoded.width - side) / 2
        val y = (decoded.height - side) / 2
        val cropped = Bitmap.createBitmap(decoded, x, y, side, side)
        val scaled = if (side > COVER_SIDE) {
            Bitmap.createScaledBitmap(cropped, COVER_SIDE, COVER_SIDE, true)
        } else {
            cropped
        }

        ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        }
    }.getOrNull()
}

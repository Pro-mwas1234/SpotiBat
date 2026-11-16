package com.mwask.bat.compose.components

import android.graphics.Outline
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Surface for the music video mode: owns nothing but the TextureView and
 * hands its Surface up so the player engine can render into it. The DRM
 * session runs at L3, so the decoder output is a normal buffer the Render
 * thread can texture; the SurfaceTexture is released on a delay after the
 * view stops producing frames, so HWUI never touches a texture already gone.
 * The video lifecycle lives in the backend player.
 */
@Composable
fun MusicVideoSurface(
    onSurface: (Surface?) -> Unit,
    cornerRadius: Dp = 16.dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val radiusPx = with(LocalDensity.current) { cornerRadius.toPx() }
    val view = remember {
        TextureView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            isOpaque = true
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
                }
            }
        }
    }
    DisposableEffect(view) {
        val listener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                onSurface(Surface(st))
            }

            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = Unit

            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                onSurface(null)
                view.postDelayed({ st.release() }, 250)
                return false
            }

            override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
        }
        view.surfaceTextureListener = listener
        if (view.isAvailable) onSurface(Surface(view.surfaceTexture))
        onDispose {
            view.surfaceTextureListener = null
            onSurface(null)
        }
    }
    AndroidView(
        factory = { view },
        modifier = modifier,
    )
}

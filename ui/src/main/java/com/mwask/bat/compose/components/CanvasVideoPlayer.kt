package com.mwask.bat.compose.components

import android.content.Context
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import com.mwask.bat.backend.core.log.Logs
import java.io.File

private val log = Logs.of(Logs.Module.PLAYER)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun CanvasVideoPlayer(
    filePath: String,
    modifier: Modifier = Modifier,
    fadeFraction: Float = 0f,
    zoom: Float = 1f
) {
    val context = LocalContext.current
    val frame = remember {
        CanvasFadeFrame(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }
    val player = remember(filePath) {
        ExoPlayer.Builder(
            context,
            CanvasRenderersFactory(context).setEnableDecoderFallback(true),
        ).build().apply {
            setMediaItem(MediaItem.fromUri(File(filePath).toUri()))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            playWhenReady = true
        }
    }
    DisposableEffect(player, frame) {
        player.setVideoTextureView(frame.texture)
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                frame.videoWidth = videoSize.width
                frame.videoHeight = videoSize.height
                frame.applyVideoSize()
            }

            override fun onPlayerError(error: PlaybackException) {
                log.w("canvas playback error ${error.errorCodeName}")
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                log.d(
                    "canvas state=$playbackState posMs=${player.currentPosition} " +
                        "durMs=${player.duration}"
                )
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                log.d(
                    "canvas jump ${oldPosition.positionMs} -> ${newPosition.positionMs} " +
                        "reason=$reason"
                )
            }
        }
        val diagnostics = object : AnalyticsListener {
            override fun onDroppedVideoFrames(
                eventTime: AnalyticsListener.EventTime,
                droppedFrames: Int,
                elapsedMs: Long,
            ) {
                log.w(
                    "canvas dropped $droppedFrames video frame(s) over ${elapsedMs}ms " +
                        "(${File(filePath).name})"
                )
            }

            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long,
            ) {
                log.i("canvas decoder $decoderName initialized in ${initializationDurationMs}ms")
            }
        }
        player.addListener(listener)
        player.addAnalyticsListener(diagnostics)
        player.prepare()
        onDispose {
            player.removeListener(listener)
            player.removeAnalyticsListener(diagnostics)
            player.release()
        }
    }

    AndroidView(
        factory = { frame },
        update = { view ->
            view.fadeFraction = fadeFraction
            view.zoom = zoom
            view.applyVideoSize()
            view.applyFade()
        },
        modifier = modifier
    )
}

/**
 * Renderers factory swapping the stock video renderer for [CanvasVideoRenderer].
 *
 * Everything else - audio, text, metadata renderers - still comes from the
 * stock factory; only the video pipeline is canvas-specific.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private class CanvasRenderersFactory(context: Context) : DefaultRenderersFactory(context) {

    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>,
    ) {
        out.add(
            CanvasVideoRenderer(
                MediaCodecVideoRenderer.Builder(context)
                    .setCodecAdapterFactory(getCodecAdapterFactory())
                    .setMediaCodecSelector(mediaCodecSelector)
                    .setAllowedJoiningTimeMs(allowedVideoJoiningTimeMs)
                    .setEnableDecoderFallback(enableDecoderFallback)
                    .setEventHandler(eventHandler)
                    .setEventListener(eventListener)
                    .setMaxDroppedFramesToNotify(5)
            )
        )
    }
}

/**
 * The canvas video renderer: identical to media3's stock
 * [MediaCodecVideoRenderer] except that the drop-to-keyframe catch-up is off.
 *
 * When a decoded frame is more than 500ms late
 * (MediaCodecVideoRenderer.MIN_EARLY_US_VERY_LATE_THRESHOLD), the stock
 * renderer discards every buffered sample up to the next sync frame and
 * flushes the codec (shouldDropBuffersToKeyframe -> maybeDropBuffersToKeyframe
 * -> skipSource + flushOrReinitializeCodec). A canvas file carries exactly
 * two sync samples - one at 0 and one at the ~4s fragment boundary - so any
 * transient stall in the first half of the loop (a slow 200KB IDR decode, a
 * compositing hiccup, a GC pause) does not cost a few dropped frames: the
 * video freezes and jumps the whole chunk to the boundary keyframe. That is
 * the on-device "stutter at second 4" that survived the tfdt rebase while
 * the same file played clean on a desktop - its frames.csv is a perfect
 * 33.4ms ladder and its only irregularity is the 200KB IDR at 4.004s.
 *
 * Returning false here degrades the same event the way desktop players do:
 * VideoFrameReleaseControl falls through to shouldDropFrame, the late frame
 * is dropped on its own and playback catches up frame by frame, without the
 * codec flush that the skip path triggers. The loop seam is unaffected -
 * onPositionReset resets the source to a keyframe on its own and never
 * consults this hook.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private class CanvasVideoRenderer(
    builder: MediaCodecVideoRenderer.Builder,
) : MediaCodecVideoRenderer(builder) {

    override fun shouldDropBuffersToKeyframe(
        earlyUs: Long,
        elapsedRealtimeUs: Long,
        isLastBuffer: Boolean,
    ): Boolean = false

    override fun onPositionReset(
        positionUs: Long,
        joining: Boolean,
        sampleStreamIsResetToKeyFrame: Boolean,
    ) {
        log.d(
            "canvas position reset posUs=$positionUs joining=$joining " +
                "toKeyFrame=$sampleStreamIsResetToKeyFrame"
        )
        super.onPositionReset(positionUs, joining, sampleStreamIsResetToKeyFrame)
    }
}

private class CanvasFadeFrame(context: Context) : FrameLayout(context) {
    val texture = TextureView(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        isOpaque = false
    }

    var videoWidth = 0
    var videoHeight = 0

    var fadeFraction: Float = 0f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (clamped == field) return
            field = clamped
            gradient = null
            invalidate()
        }

    var zoom: Float = 1f
        set(value) {
            val clamped = value.coerceAtLeast(1f)
            if (clamped == field) return
            field = clamped
            applyVideoSize()
        }

    private val maskPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private var gradient: LinearGradient? = null

    private var transformKey = Long.MIN_VALUE
    private var transformZoom = -1f
    private var fadeKey = Long.MIN_VALUE

    init {
        addView(texture)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        gradient = null
        applyVideoSize()
        applyFade()
    }

    fun applyVideoSize() {
        if (videoWidth <= 0 || videoHeight <= 0) return
        val viewWidth = texture.width
        val viewHeight = texture.height
        if (viewWidth <= 0 || viewHeight <= 0) return
        val key = (videoWidth.toLong() and 0xFFFFL).shl(48) or
            (videoHeight.toLong() and 0xFFFFL).shl(32) or
            (viewWidth.toLong() and 0xFFFFL).shl(16) or
            (viewHeight.toLong() and 0xFFFFL)
        if (key == transformKey && zoom == transformZoom) return
        transformKey = key
        transformZoom = zoom
        val videoAspect = videoWidth.toFloat() / videoHeight
        val viewAspect = viewWidth.toFloat() / viewHeight
        val matrix = Matrix()
        val scaleX: Float
        val scaleY: Float
        when {
            videoAspect > viewAspect -> {
                scaleX = videoAspect / viewAspect
                scaleY = 1f
            }
            videoAspect < viewAspect -> {
                scaleX = 1f
                scaleY = viewAspect / videoAspect
            }
            else -> {
                scaleX = 1f
                scaleY = 1f
            }
        }
        matrix.setScale(scaleX * zoom, scaleY * zoom, viewWidth / 2f, 0f)
        texture.setTransform(matrix)
    }

    fun applyFade() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val key = (height.toLong() and 0xFFFFFFFFL).shl(32) or
                (fadeFraction.toRawBits().toLong() and 0xFFFFFFFFL)
            if (key == fadeKey) return
            fadeKey = key
            texture.setRenderEffect(fadeEffect(height, fadeFraction))
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        val fade = fadeFraction
        if (fade <= 0.001f || height <= 0 || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            super.dispatchDraw(canvas)
            return
        }
        val shader = gradient?.takeIf { gradientHeight == height } ?: fadeShader(height, fade).also {
            gradient = it
            gradientHeight = height
        }
        maskPaint.shader = shader
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        super.dispatchDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), maskPaint)
        canvas.restoreToCount(layer)
    }

    private var gradientHeight = 0
}

private const val FadeCurveStops = 16

private fun smootherStep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * x * (x * (x * 6f - 15f) + 10f)
}

private fun fadeShader(heightPx: Int, fraction: Float): LinearGradient {
    val endY = heightPx.toFloat()
    return LinearGradient(
        0f,
        endY * (1f - fraction.coerceIn(0f, 1f)),
        0f,
        endY,
        IntArray(FadeCurveStops + 1) { index ->
            val alpha = (255f * (1f - smootherStep(index / FadeCurveStops.toFloat()))).toInt()
            android.graphics.Color.argb(alpha, 0, 0, 0)
        },
        FloatArray(FadeCurveStops + 1) { index -> index / FadeCurveStops.toFloat() },
        Shader.TileMode.CLAMP
    )
}

@RequiresApi(Build.VERSION_CODES.S)
private fun fadeEffect(heightPx: Int, fraction: Float): RenderEffect? {
    if (fraction <= 0.001f || heightPx <= 0) return null
    return RenderEffect.createBlendModeEffect(
        RenderEffect.createOffsetEffect(0f, 0f),
        RenderEffect.createShaderEffect(fadeShader(heightPx, fraction)),
        BlendMode.DST_IN
    )
}

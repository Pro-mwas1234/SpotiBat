package com.mwask.bat.compose.screens

import android.content.Context
import android.os.Build
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mwask.bat.compose.theme.DmMono
import com.mwask.bat.compose.theme.EncoreColors
import androidx.compose.ui.res.stringResource
import com.mwask.bat.compose.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = remember { buildLabel(context) }
    val interaction = remember { MutableInteractionSource() }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val reveal by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(
            durationMillis = 460,
            delayMillis = 90,
            easing = LinearOutSlowInEasing,
        ),
        label = "splashReveal",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(EncoreColors.Background)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = {},
            ),
    ) {
        LoadingIndicator(
            modifier = Modifier
                .align(Alignment.Center)
                .size(72.dp),
            color = Color.White,
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 56.dp)
                .graphicsLayer {
                    alpha = reveal
                    translationY = (1f - reveal) * 10.dp.toPx()
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = stringResource(R.string.spotibat),
                color = Color.White,
                fontFamily = DmMono,
                fontWeight = FontWeight.Medium,
                fontSize = 21.sp,
                letterSpacing = 0.2.sp,
            )
            Text(
                text = version,
                color = EncoreColors.TextSecondary,
                fontFamily = DmMono,
                fontWeight = FontWeight.Normal,
                fontSize = 10.sp,
                letterSpacing = 0.6.sp,
            )
        }
    }
}

private fun buildLabel(context: Context): String = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        info.versionCode.toLong()
    }
    val name = info.versionName.orEmpty()
    if (name.isBlank()) "build $code" else "v$name · build $code"
}.getOrDefault("")

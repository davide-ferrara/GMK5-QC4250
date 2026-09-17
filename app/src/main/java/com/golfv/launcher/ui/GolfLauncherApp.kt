package com.golfv.launcher.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.golfv.launcher.R
import com.golfv.launcher.ui.theme.GolfLauncherTheme
import kotlinx.coroutines.delay

private enum class LauncherScreen { Splash, Home, Apps }

@Composable
fun GolfLauncherApp() {
    var screen by remember { mutableStateOf(LauncherScreen.Splash) }

    LaunchedEffect(Unit) {
        delay(900)
        if (screen == LauncherScreen.Splash) screen = LauncherScreen.Home
    }

    BackHandler(enabled = screen == LauncherScreen.Apps) {
        screen = LauncherScreen.Home
    }

    when (screen) {
        LauncherScreen.Splash -> SplashScreen()
        LauncherScreen.Home -> HomeScreen(onOpenApps = { screen = LauncherScreen.Apps })
        LauncherScreen.Apps -> AppDrawerPlaceholder(onClose = { screen = LauncherScreen.Home })
    }
}

@Composable
private fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090C10)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.volkswagen_logo_2000_2012),
            contentDescription = null,
            modifier = Modifier.size(220.dp),
        )
    }
}

@Composable
private fun HomeScreen(onOpenApps: () -> Unit) {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        PlaceholderCarBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 32.dp),
        ) {
            ClockAndDate()
            Spacer(modifier = Modifier.weight(1f))
            Dock(
                onAndroidAuto = { launchPackage(context, "com.zjinnova.zlink") },
                onRadio = { unavailable(context, "Radio target not configured") },
                onOemSettings = {
                    launchIntent(
                        context,
                        Intent().setComponent(
                            ComponentName("com.xyauto.Settings", "com.xyauto.Settings.MainActivity"),
                        ),
                    )
                },
                onAndroidSettings = { launchIntent(context, Intent(Settings.ACTION_SETTINGS)) },
                onOpenApps = onOpenApps,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun PlaceholderCarBackground() {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF111820), Color(0xFF07090C)),
                ),
            ),
    ) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x332B88D8), Color.Transparent),
                center = Offset(size.width * 0.58f, size.height * 0.48f),
                radius = size.minDimension * 0.58f,
            ),
            radius = size.minDimension * 0.58f,
            center = Offset(size.width * 0.58f, size.height * 0.48f),
        )

        val left = size.width * 0.30f
        val top = size.height * 0.40f
        val carWidth = size.width * 0.49f
        val carHeight = size.height * 0.25f
        val body = Path().apply {
            moveTo(left, top + carHeight * 0.72f)
            lineTo(left + carWidth * 0.12f, top + carHeight * 0.30f)
            quadraticTo(
                left + carWidth * 0.28f,
                top,
                left + carWidth * 0.52f,
                top + carHeight * 0.04f,
            )
            lineTo(left + carWidth * 0.80f, top + carHeight * 0.37f)
            quadraticTo(
                left + carWidth,
                top + carHeight * 0.48f,
                left + carWidth,
                top + carHeight * 0.76f,
            )
            lineTo(left, top + carHeight * 0.76f)
            close()
        }
        drawPath(
            path = body,
            brush = Brush.verticalGradient(
                listOf(Color(0xFFB8C0CA), Color(0xFF4B535D)),
                startY = top,
                endY = top + carHeight,
            ),
        )
        drawPath(body, Color(0x99EAF2FA), style = Stroke(width = 2.dp.toPx()))

        val wheelRadius = carHeight * 0.19f
        listOf(left + carWidth * 0.22f, left + carWidth * 0.80f).forEach { x ->
            drawCircle(Color(0xFF090A0C), wheelRadius, Offset(x, top + carHeight * 0.77f))
            drawCircle(
                Color(0xFF78838E),
                wheelRadius * 0.48f,
                Offset(x, top + carHeight * 0.77f),
            )
        }

        drawOval(
            color = Color(0x55000000),
            topLeft = Offset(left + carWidth * 0.02f, top + carHeight * 0.91f),
            size = Size(carWidth * 0.96f, carHeight * 0.14f),
        )
    }
}

@Composable
private fun Dock(
    onAndroidAuto: () -> Unit,
    onRadio: () -> Unit,
    onOemSettings: () -> Unit,
    onAndroidSettings: () -> Unit,
    onOpenApps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = Color(0xCC18212B),
        shadowElevation = 14.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DockButton(R.drawable.ic_android_auto, R.string.android_auto, true, onAndroidAuto)
            DockButton(R.drawable.ic_radio, R.string.radio, true, onRadio)
            DockButton(R.drawable.ic_tune, R.string.oem_settings, false, onOemSettings)
            DockButton(R.drawable.ic_settings, R.string.android_settings, false, onAndroidSettings)
            DockButton(R.drawable.ic_apps, R.string.app_drawer, true, onOpenApps)
        }
    }
}

@Composable
private fun DockButton(
    @DrawableRes icon: Int,
    label: Int,
    prominent: Boolean,
    onClick: () -> Unit,
) {
    val background = if (prominent) Color(0xFF236DA8) else Color(0xFF303A45)
    Surface(shape = CircleShape, color = background) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(72.dp),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = stringResource(label),
                tint = Color.White,
                modifier = Modifier.size(34.dp),
            )
        }
    }
}

@Composable
private fun AppDrawerPlaceholder(onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090C10))
            .padding(36.dp),
    ) {
        Column {
            Text(
                text = stringResource(R.string.app_drawer),
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "The filtered application grid is planned for Phase 2.",
                color = Color(0xFF9BA8B5),
                fontSize = 20.sp,
            )
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(64.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.close),
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

private fun launchPackage(context: Context, packageName: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (intent == null) {
        unavailable(context, "$packageName is not installed")
    } else {
        launchIntent(context, intent)
    }
}

private fun launchIntent(context: Context, intent: Intent) {
    val activity = intent.resolveActivity(context.packageManager)
    if (activity == null) {
        unavailable(context, "Application not available")
        return
    }

    runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        unavailable(context, "Unable to open application")
    }
}

private fun unavailable(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

@Preview(widthDp = 1024, heightDp = 600, showBackground = true)
@Composable
private fun LauncherPreview() {
    GolfLauncherTheme {
        GolfLauncherApp()
    }
}

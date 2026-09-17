package com.golfv.launcher.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.provider.Settings
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.golfv.launcher.BuildConfig
import com.golfv.launcher.R
import com.golfv.launcher.ui.theme.GolfLauncherTheme
import kotlinx.coroutines.delay

private enum class LauncherScreen { Splash, Home, Apps, Info }

@Composable
fun GolfLauncherApp() {
    var screen by remember { mutableStateOf(LauncherScreen.Splash) }
    var animationFinished by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(900)
        if (screen == LauncherScreen.Splash) screen = LauncherScreen.Home
    }

    BackHandler(enabled = screen == LauncherScreen.Apps || screen == LauncherScreen.Info) {
        screen = LauncherScreen.Home
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (screen != LauncherScreen.Apps) {
            HomeScreen(
                playCarAnimation = !animationFinished,
                onCarAnimationFinished = { animationFinished = true },
                onOpenApps = {
                    animationFinished = true
                    screen = LauncherScreen.Apps
                },
                onOpenInfo = { screen = LauncherScreen.Info },
            )
        } else {
            AppDrawerScreen(onClose = { screen = LauncherScreen.Home })
        }

        if (screen == LauncherScreen.Splash) {
            SplashScreen()
        }

        if (screen == LauncherScreen.Info) {
            ProjectInfoScreen(onClose = { screen = LauncherScreen.Home })
        }
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
private fun HomeScreen(
    playCarAnimation: Boolean,
    onCarAnimationFinished: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenInfo: () -> Unit,
) {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        CarBackground(
            playAnimation = playCarAnimation,
            onAnimationFinished = onCarAnimationFinished,
        )

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

        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(28.dp)
                .size(56.dp),
            shape = CircleShape,
            color = Color(0xCC18212B),
            shadowElevation = 8.dp,
        ) {
            IconButton(onClick = onOpenInfo) {
                Icon(
                    painter = painterResource(R.drawable.ic_info),
                    contentDescription = stringResource(R.string.project_info),
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun CarBackground(
    playAnimation: Boolean,
    onAnimationFinished: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Image(
            painter = painterResource(R.drawable.golf_mk5_final),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )

        if (playAnimation) {
            AndroidView(
                factory = { context -> CarVideoView(context, onAnimationFinished) },
                update = { it.onFinished = onAnimationFinished },
                modifier = Modifier.fillMaxSize(),
                onRelease = { it.release() },
            )
        }
    }
}

private class CarVideoView(
    context: Context,
    var onFinished: () -> Unit,
) : TextureView(context), TextureView.SurfaceTextureListener {
    private var player: MediaPlayer? = null
    private var videoSurface: Surface? = null

    init {
        surfaceTextureListener = this
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        videoSurface = Surface(texture)
        player = MediaPlayer().apply {
            setDataSource(
                context,
                Uri.parse("android.resource://${context.packageName}/${R.raw.golf_mk5_black_7s}"),
            )
            setSurface(videoSurface)
            isLooping = false
            setOnPreparedListener { it.start() }
            setOnCompletionListener { onFinished() }
            setOnErrorListener { _, _, _ ->
                onFinished()
                true
            }
            prepareAsync()
        }
    }

    override fun onSurfaceTextureSizeChanged(
        texture: SurfaceTexture,
        width: Int,
        height: Int,
    ) = Unit

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        release()
        return true
    }

    fun release() {
        player?.release()
        player = null
        videoSurface?.release()
        videoSurface = null
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
            DockButton(R.drawable.ic_android_auto, R.string.android_auto, onAndroidAuto)
            DockButton(R.drawable.ic_radio, R.string.radio, onRadio)
            DockButton(R.drawable.ic_car, R.string.oem_settings, onOemSettings)
            DockButton(R.drawable.ic_settings, R.string.android_settings, onAndroidSettings)
            DockButton(R.drawable.ic_apps, R.string.app_drawer, onOpenApps)
        }
    }
}

@Composable
private fun ProjectInfoScreen(onClose: () -> Unit) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF5090C10))
            .padding(horizontal = 72.dp, vertical = 42.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
        ) {
            Text(
                text = stringResource(R.string.project_info),
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.project_description),
                color = Color(0xFFC2CAD3),
                fontSize = 20.sp,
                lineHeight = 28.sp,
            )
            Spacer(modifier = Modifier.height(28.dp))
            InfoRow(stringResource(R.string.version_label), BuildConfig.VERSION_NAME)
            InfoRow(stringResource(R.string.author_label), stringResource(R.string.author_name))
            InfoRow(stringResource(R.string.repository_label), stringResource(R.string.repository_url))
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
            ) {
                OutlinedButton(
                    onClick = {
                        launchIntent(
                            context,
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/davide-ferrara/GMK5-QC4250"),
                            ),
                        )
                    },
                ) {
                    Text(stringResource(R.string.open_repository))
                }
                Button(
                    onClick = {
                        unavailable(context, context.getString(R.string.update_mock_message))
                    },
                ) {
                    Text(stringResource(R.string.check_updates))
                }
            }
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

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Color(0xFF8D9AA7),
            fontSize = 18.sp,
            modifier = Modifier.width(150.dp),
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun DockButton(
    @DrawableRes icon: Int,
    label: Int,
    onClick: () -> Unit,
) {
    Surface(shape = CircleShape, color = Color(0xFF236DA8)) {
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

private fun launchPackage(context: Context, packageName: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (intent == null) {
        unavailable(context, "$packageName is not installed")
    } else {
        launchIntent(context, intent)
    }
}

internal fun launchIntent(context: Context, intent: Intent) {
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

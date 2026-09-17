package com.golfv.launcher.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.provider.Settings
import android.widget.Toast
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.golfv.launcher.BuildConfig
import com.golfv.launcher.R
import com.golfv.launcher.UpdateManager
import com.golfv.launcher.UpdateResult
import com.golfv.launcher.ui.theme.GolfLauncherTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class LauncherScreen { Splash, Home, Apps, Info }

@Composable
fun GolfLauncherApp() {
    var screen by remember { mutableStateOf(LauncherScreen.Splash) }

    LaunchedEffect(Unit) {
        delay(3_000)
        if (screen == LauncherScreen.Splash) screen = LauncherScreen.Home
    }

    BackHandler(enabled = screen == LauncherScreen.Apps || screen == LauncherScreen.Info) {
        screen = LauncherScreen.Home
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HomeScreen(
            onOpenApps = { screen = LauncherScreen.Apps },
            onOpenInfo = { screen = LauncherScreen.Info },
        )

        if (screen == LauncherScreen.Splash) {
            SplashScreen()
        }

        if (screen == LauncherScreen.Info) {
            ProjectInfoScreen(onClose = { screen = LauncherScreen.Home })
        }

        if (screen == LauncherScreen.Apps) {
            AppDrawerScreen(onClose = { screen = LauncherScreen.Home })
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
    onOpenApps: () -> Unit,
    onOpenInfo: () -> Unit,
) {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        CarBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(2f)
                .padding(horizontal = 48.dp, vertical = 32.dp),
        ) {
            ClockAndDate()
            Spacer(modifier = Modifier.weight(1f))
            Dock(
                onAndroidAuto = { launchPackage(context, "com.zjinnova.zlink") },
                onRadio = { launchPackage(context, "com.acloud.stub.extradio") },
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
                .zIndex(3f)
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
private fun CarBackground() {
    var videoFailed by remember { mutableStateOf(false) }
    var carWebView by remember { mutableStateOf<CarWebView?>(null) }
    val replayInteractionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF17212D), Color(0xFF090D13)),
                ),
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x66316FA3), Color(0x2217314A), Color.Transparent),
                    center = Offset(size.width * 0.54f, size.height * 0.52f),
                    radius = size.minDimension * 0.68f,
                ),
                radius = size.minDimension * 0.68f,
                center = Offset(size.width * 0.54f, size.height * 0.52f),
            )
        }

        AndroidView(
            factory = { context ->
                CarWebView(context, onFailed = { videoFailed = true }).also {
                    carWebView = it
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = {
                if (carWebView === it) carWebView = null
                it.release()
            },
        )

        if (videoFailed) {
            Image(
                painter = painterResource(R.drawable.golf_mk5_final),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Keep the video view passive so it cannot steal touches from the dock.
        // This dedicated hit area preserves the tap-the-car replay easter egg.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-5).dp)
                .size(width = 500.dp, height = 220.dp)
                .testTag("carReplayArea")
                .clickable(
                    interactionSource = replayInteractionSource,
                    indication = null,
                    onClick = { carWebView?.replay() },
                ),
        )
    }
}

private class CarWebView(
    context: Context,
    onFailed: () -> Unit,
) : WebView(context) {
    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isClickable = false
        isLongClickable = false
        isFocusable = false
        isFocusableInTouchMode = false
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        settings.javaScriptEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = true
        settings.allowFileAccessFromFileURLs = true
        addJavascriptInterface(
            object {
                @JavascriptInterface
                fun onVideoError() {
                    post { onFailed() }
                }

            },
            "CarVideoNative",
        )
        webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView,
                request: android.webkit.WebResourceRequest,
                error: android.webkit.WebResourceError,
            ) {
                if (request.isForMainFrame) onFailed()
            }
        }
        loadDataWithBaseURL(
            "file:///android_res/",
            TRANSPARENT_CAR_PAGE,
            "text/html",
            "UTF-8",
            null,
        )
    }

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean = false

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean = false

    fun replay() {
        evaluateJavascript("window.replayCarVideo && window.replayCarVideo()", null)
    }

    fun release() {
        stopLoading()
        removeJavascriptInterface("CarVideoNative")
        destroy()
    }
}

private const val TRANSPARENT_CAR_PAGE = """
<!doctype html>
<html><head><meta name="viewport" content="width=device-width,height=device-height,initial-scale=1,maximum-scale=1,user-scalable=no">
<style>html,body{margin:0;width:1024px;height:600px;overflow:hidden;background:transparent}#car{display:none}canvas{position:absolute;left:0;top:0;width:1024px;height:600px}</style>
</head><body><video id="car" autoplay muted playsinline preload="auto" src="raw/golf_mk5_transparent_7s.webm"></video><canvas id="stage" width="1024" height="600"></canvas>
<script>
const car=document.getElementById('car');
const stage=document.getElementById('stage');
const paint=stage.getContext('2d',{alpha:true});
car.addEventListener('error',()=>CarVideoNative.onVideoError());
function drawCarFrame(){if(car.readyState>=2){paint.clearRect(0,0,1024,600);paint.drawImage(car,0,0,1024,600)}requestAnimationFrame(drawCarFrame)}
requestAnimationFrame(drawCarFrame);
car.play().catch(()=>CarVideoNative.onVideoError());
window.replayCarVideo=()=>{car.pause();car.currentTime=0;car.play().catch(()=>CarVideoNative.onVideoError());};
</script></body></html>
"""

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
    val scope = rememberCoroutineScope()
    val updater = remember(context.applicationContext) { UpdateManager(context.applicationContext) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var updateInProgress by remember { mutableStateOf(false) }
    var downloadedApk by remember { mutableStateOf<File?>(null) }
    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val apk = downloadedApk
        if (apk != null && updater.canInstallPackages() && updater.install(apk)) {
            updateStatus = context.getString(R.string.update_preparing)
        } else {
            updateStatus = context.getString(R.string.update_permission_denied)
        }
        updateInProgress = false
    }

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
            if (updateStatus != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = updateStatus.orEmpty(),
                    color = Color(0xFF8DBDEB),
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                )
            }
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
                if (BuildConfig.IS_STABLE) {
                    Button(
                        enabled = !updateInProgress,
                        onClick = {
                            updateInProgress = true
                            updateStatus = context.getString(R.string.update_checking)
                            scope.launch {
                                when (val result = updater.downloadLatest { version, percent ->
                                    withContext(Dispatchers.Main.immediate) {
                                        updateStatus = context.getString(
                                            R.string.update_downloading,
                                            version,
                                            percent,
                                        )
                                    }
                                }) {
                                    is UpdateResult.Current -> {
                                        updateStatus = context.getString(
                                            R.string.update_current,
                                            result.version,
                                        )
                                        updateInProgress = false
                                    }
                                    is UpdateResult.Downloaded -> {
                                        downloadedApk = result.apk
                                        updateStatus = context.getString(R.string.update_preparing)
                                        if (updater.canInstallPackages()) {
                                            if (!updater.install(result.apk)) {
                                                updateStatus = context.getString(R.string.update_failed)
                                            }
                                            updateInProgress = false
                                        } else {
                                            runCatching {
                                                installPermissionLauncher.launch(
                                                    updater.installPermissionIntent(),
                                                )
                                            }.onFailure {
                                                updateStatus = context.getString(
                                                    R.string.update_permission_denied,
                                                )
                                                updateInProgress = false
                                            }
                                        }
                                    }
                                    UpdateResult.NoRelease -> {
                                        updateStatus = context.getString(R.string.update_no_release)
                                        updateInProgress = false
                                    }
                                    UpdateResult.InvalidApk -> {
                                        updateStatus = context.getString(R.string.update_invalid_apk)
                                        updateInProgress = false
                                    }
                                    UpdateResult.Failed -> {
                                        updateStatus = context.getString(R.string.update_failed)
                                        updateInProgress = false
                                    }
                                }
                            }
                        },
                    ) {
                        Text(stringResource(R.string.check_updates))
                    }
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
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val highlighted = pressed || focused

    Surface(
        shape = CircleShape,
        color = if (highlighted) Color(0xFF8FD3FF) else Color(0xFF236DA8),
        shadowElevation = if (highlighted) 14.dp else 2.dp,
    ) {
        IconButton(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = Modifier.size(72.dp),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = stringResource(label),
                tint = if (highlighted) Color(0xFF06131F) else Color.White,
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

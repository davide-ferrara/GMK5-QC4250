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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.golfv.launcher.BuildConfig
import com.golfv.launcher.ExteriorLightsState
import com.golfv.launcher.DoorStates
import com.golfv.launcher.R
import com.golfv.launcher.UpdateManager
import com.golfv.launcher.UpdateResult
import com.golfv.launcher.ui.theme.AccentTheme
import com.golfv.launcher.ui.theme.GolfLauncherTheme
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class LauncherScreen { Splash, Home, Apps, Info }

private enum class LightsPreview(val label: Int) {
    Automatic(R.string.lights_preview_auto),
    On(R.string.lights_preview_on),
    Off(R.string.lights_preview_off),
}

private const val SPLASH_DURATION_MS = 2_000L
private const val SPLASH_FADE_DURATION_MS = 450
private const val SPLASH_AUDIO_DELAY_MS = 150L
private const val SPEED_DISPLAY_THRESHOLD_KPH = 0.05f
private const val PREFERENCES_NAME = "launcher_preferences"
private const val ACCENT_THEME_KEY = "accent_theme"

@Composable
fun GolfLauncherApp(
    exteriorLights: StateFlow<ExteriorLightsState>,
    doors: StateFlow<DoorStates?>,
    vehicleSpeedKph: StateFlow<Float>,
    lastVehicleSpeedKph: StateFlow<Float?>,
    onSplashFinished: () -> Unit = {},
) {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    var accentTheme by remember(preferences) {
        mutableStateOf(AccentTheme.fromPreference(preferences.getString(ACCENT_THEME_KEY, null)))
    }
    var screen by remember { mutableStateOf(LauncherScreen.Splash) }
    val exteriorLightsState by exteriorLights.collectAsState()
    val doorStates by doors.collectAsState()
    val speedKph by vehicleSpeedKph.collectAsState()
    val lastSpeedKph by lastVehicleSpeedKph.collectAsState()
    // Visual override only; never change the CAN state or persist a test mode.
    var lightsPreview by remember { mutableStateOf(LightsPreview.Automatic) }
    var doorsPreview by remember { mutableStateOf<Int?>(null) }
    val doorMask = doorsPreview ?: (doorStates?.renderMask ?: 0)
    val lightsOn = when (lightsPreview) {
        LightsPreview.Automatic -> exteriorLightsState == ExteriorLightsState.On
        LightsPreview.On -> true
        LightsPreview.Off -> false
    }

    GolfLauncherTheme(accentTheme) {
        LaunchedEffect(Unit) {
            delay(SPLASH_DURATION_MS)
            if (screen == LauncherScreen.Splash) {
                screen = LauncherScreen.Home
                // The audio HAL on the head unit can come up after the launcher UI.
                // Keep the Home visible first, then start the short welcome cue.
                delay(SPLASH_AUDIO_DELAY_MS)
                onSplashFinished()
            }
        }

        BackHandler(enabled = screen == LauncherScreen.Apps || screen == LauncherScreen.Info) {
            screen = LauncherScreen.Home
        }

        Box(modifier = Modifier.fillMaxSize()) {
            HomeScreen(
                accentTheme = accentTheme,
                speedKph = speedKph,
                lightsOn = lightsOn,
                doorMask = doorMask,
                forceTopView = doorsPreview != null,
                onOpenApps = { screen = LauncherScreen.Apps },
                onOpenInfo = { screen = LauncherScreen.Info },
            )

            AnimatedVisibility(
                visible = screen == LauncherScreen.Splash,
                exit = fadeOut(animationSpec = tween(SPLASH_FADE_DURATION_MS)),
            ) {
                SplashScreen()
            }

            if (screen == LauncherScreen.Info) {
                ProjectInfoScreen(
                    accentTheme = accentTheme,
                    exteriorLightsState = exteriorLightsState,
                    doorStates = doorStates,
                    lastSpeedKph = lastSpeedKph,
                    lightsPreview = lightsPreview,
                    onLightsPreviewChange = { lightsPreview = it },
                    doorsPreview = doorsPreview,
                    onDoorsPreviewChange = { doorsPreview = it },
                    onAccentThemeChange = { selectedTheme ->
                        accentTheme = selectedTheme
                        preferences.edit()
                            .putString(ACCENT_THEME_KEY, selectedTheme.preferenceValue)
                            .apply()
                    },
                    onClose = { screen = LauncherScreen.Home },
                )
            }

            if (screen == LauncherScreen.Apps) {
                AppDrawerScreen(onClose = { screen = LauncherScreen.Home })
            }
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
    accentTheme: AccentTheme,
    speedKph: Float,
    lightsOn: Boolean,
    doorMask: Int,
    forceTopView: Boolean,
    onOpenApps: () -> Unit,
    onOpenInfo: () -> Unit,
) {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        CarBackground(accentTheme, speedKph, lightsOn, doorMask, forceTopView)

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
                onRadio = { launchPackage(context, "com.golfv.radio") },
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
private fun CarBackground(
    accentTheme: AccentTheme,
    speedKph: Float,
    lightsOn: Boolean,
    doorMask: Int,
    forceTopView: Boolean,
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val vehicleIsMoving = abs(speedKph) > SPEED_DISPLAY_THRESHOLD_KPH
    var videoFailed by remember { mutableStateOf(false) }
    var introComplete by remember { mutableStateOf(false) }
    val replayInteractionSource = remember { MutableInteractionSource() }
    val showTopView = doorMask != 0 || forceTopView
    // A stop after driving must not restart the intro.
    LaunchedEffect(vehicleIsMoving, showTopView) {
        if (vehicleIsMoving || showTopView) introComplete = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(accentTheme.gradientStart, accentTheme.gradientEnd),
                ),
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.40f),
                        accentColor.copy(alpha = 0.14f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.54f, size.height * 0.52f),
                    radius = size.minDimension * 0.68f,
                ),
                radius = size.minDimension * 0.68f,
                center = Offset(size.width * 0.54f, size.height * 0.52f),
            )
        }

        if (vehicleIsMoving) {
            Text(
                text = abs(speedKph).roundToInt().toString(),
                color = Color.White,
                fontSize = 180.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            Crossfade(
                targetState = showTopView,
                animationSpec = tween(220),
                label = "carView",
                modifier = Modifier.fillMaxSize(),
            ) { topView ->
                if (topView) {
                    DoorRender(
                        mask = doorMask,
                        lightsOn = lightsOn,
                        modifier = Modifier.fillMaxSize().padding(top = 72.dp, bottom = 104.dp),
                    )
                } else if (introComplete || videoFailed) {
                    Image(
                        painter = painterResource(R.drawable.golf_mk5_final),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize(),
                    )
                    CarLightsOverlay(lightsOn)
                } else {
                    AndroidView(
                        factory = { context ->
                            CarWebView(
                                context,
                                onFailed = { videoFailed = true },
                                onFinished = { introComplete = true },
                            )
                        },
                        modifier = Modifier.fillMaxSize(),
                        onRelease = { it.release() },
                    )
                }
            }

            // Keep the video view passive so it cannot steal touches from the dock.
            // This dedicated hit area preserves the tap-the-car replay easter egg.
            if (!showTopView) Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-5).dp)
                    .size(width = 500.dp, height = 220.dp)
                    .testTag("carReplayArea")
                    .clickable(
                        interactionSource = replayInteractionSource,
                        indication = null,
                        onClick = { introComplete = false },
                    ),
            )
        }
    }
}

/** Match either the perspective frame (FillBounds) or the square top view (Fit). */
@Composable
internal fun CarLightsOverlay(lightsOn: Boolean, topView: Boolean = false) {
    val opacity by animateFloatAsState(
        targetValue = if (lightsOn) 1f else 0f,
        animationSpec = tween(220),
        label = "carLights",
    )
    Box(
        modifier = Modifier.fillMaxSize()
            .testTag(if (topView) {
                if (lightsOn) "topLightsOn" else "topLightsOff"
            } else if (lightsOn) "carLightsOn" else "carLightsOff")
            .graphicsLayer { alpha = opacity },
    ) {
        // A translucent lens mask retains the details of the original render.
        Image(
            painter = painterResource(if (topView) R.drawable.golf_top_lights_overlay else R.drawable.golf_lights_overlay),
            contentDescription = null,
            contentScale = if (topView) ContentScale.Fit else ContentScale.FillBounds,
            alpha = 0.55f,
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scaleX = if (topView) size.minDimension / 1024f else size.width / 1024f
            val scaleY = if (topView) scaleX else size.height / 600f
            val originX = if (topView) (size.width - size.minDimension) / 2f else 0f
            val originY = if (topView) (size.height - size.minDimension) / 2f else 0f
            fun glow(x: Float, y: Float, radius: Float, color: Color) {
                val center = Offset(originX + x * scaleX, originY + y * scaleY)
                val scaledRadius = radius * scaleX
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to color,
                        0.18f to color.copy(alpha = color.alpha * 0.72f),
                        0.48f to color.copy(alpha = color.alpha * 0.22f),
                        1f to Color.Transparent,
                        center = center,
                        radius = scaledRadius,
                    ),
                    radius = scaledRadius,
                    center = center,
                )
            }
            // Several soft radii approximate optical bloom without a live blur
            // or another render pass. Draw the wide spill before the bright core.
            fun bloom(x: Float, y: Float, radius: Float, color: Color) {
                glow(x, y, radius * 2.6f, color.copy(alpha = color.alpha * 0.18f))
                glow(x, y, radius * 1.35f, color.copy(alpha = color.alpha * 0.42f))
                glow(x, y, radius * 0.55f, color)
            }
            if (topView) {
                bloom(403f, 179f, 32f, Color(0xD9FFF0CE))
                bloom(621f, 179f, 32f, Color(0xD9FFF0CE))
                bloom(380f, 838f, 22f, Color(0x99FF3020))
                bloom(644f, 838f, 22f, Color(0x99FF3020))
            } else {
                bloom(404f, 352f, 30f, Color(0xD9FFF0CE))
                bloom(257f, 335f, 20f, Color(0xBFFFF0CE))
                bloom(741f, 274f, 14f, Color(0x99FF3020))
            }
        }
    }
}

private class CarWebView(
    context: Context,
    onFailed: () -> Unit,
    onFinished: () -> Unit,
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

                @JavascriptInterface
                fun onVideoFinished() {
                    post { onFinished() }
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
            "file:///android_asset/",
            TRANSPARENT_CAR_PAGE,
            "text/html",
            "UTF-8",
            null,
        )
    }

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean = false

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean = false

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
</head><body><video id="car" autoplay muted playsinline preload="auto" src="golf_mk5_transparent_7s_crf32.webm"></video><canvas id="stage" width="1024" height="600"></canvas>
<script>
const car=document.getElementById('car');
const stage=document.getElementById('stage');
const paint=stage.getContext('2d',{alpha:true});
car.addEventListener('error',()=>CarVideoNative.onVideoError());
car.addEventListener('ended',()=>CarVideoNative.onVideoFinished());
const frameInterval=1000/30;
let lastPaint=0;
function drawCarFrame(now){if(car.readyState>=2&&now-lastPaint>=frameInterval){lastPaint=now;paint.clearRect(0,0,1024,600);paint.drawImage(car,0,0,1024,600)}if(!car.paused&&!car.ended)requestAnimationFrame(drawCarFrame)}
car.addEventListener('play',()=>requestAnimationFrame(drawCarFrame));
car.play().catch(()=>CarVideoNative.onVideoError());
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
private fun ProjectInfoScreen(
    accentTheme: AccentTheme,
    exteriorLightsState: ExteriorLightsState,
    doorStates: DoorStates?,
    lastSpeedKph: Float?,
    lightsPreview: LightsPreview,
    onLightsPreviewChange: (LightsPreview) -> Unit,
    doorsPreview: Int?,
    onDoorsPreviewChange: (Int?) -> Unit,
    onAccentThemeChange: (AccentTheme) -> Unit,
    onClose: () -> Unit,
) {
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
                .padding(horizontal = 28.dp)
                .verticalScroll(rememberScrollState()),
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
            InfoRow(
                label = stringResource(R.string.exterior_lights_label),
                value = exteriorLightsState.label(context),
                valueColor = exteriorLightsState.color,
            )
            InfoRow(
                label = stringResource(R.string.doors_label),
                value = doorStates.label(context),
                valueColor = doorStates.color,
            )
            InfoRow(
                label = stringResource(R.string.can_speed_label),
                value = lastSpeedKph.label(context),
                valueColor = if (lastSpeedKph == null) Color(0xFF8D9AA7) else Color.White,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                stringResource(R.string.lights_preview_label),
                color = Color(0xFF8D9AA7),
                fontSize = 18.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LightsPreview.entries.forEach { mode ->
                    FilterChip(
                        selected = lightsPreview == mode,
                        onClick = { onLightsPreviewChange(mode) },
                        label = { Text(stringResource(mode.label)) },
                        modifier = Modifier.testTag("lightsPreview${mode.name}"),
                    )
                }
            }
            Text(
                stringResource(R.string.lights_preview_hint),
                color = Color(0xFF8D9AA7),
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(16.dp))
            DoorPreviewControls(
                preview = doorsPreview,
                canMask = doorStates?.renderMask ?: 0,
                onChange = onDoorsPreviewChange,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.accent_theme_label),
                color = Color(0xFF8D9AA7),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AccentThemeChoice(
                    theme = AccentTheme.VolkswagenBlue,
                    selected = accentTheme == AccentTheme.VolkswagenBlue,
                    onClick = { onAccentThemeChange(AccentTheme.VolkswagenBlue) },
                    modifier = Modifier.weight(1f),
                )
                AccentThemeChoice(
                    theme = AccentTheme.InstrumentRed,
                    selected = accentTheme == AccentTheme.InstrumentRed,
                    onClick = { onAccentThemeChange(AccentTheme.InstrumentRed) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (updateStatus != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = updateStatus.orEmpty(),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
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
private fun AccentThemeChoice(
    theme: AccentTheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(60.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) theme.primary.copy(alpha = 0.16f) else Color(0xFF111820),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) theme.primary else Color(0xFF38434F),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(theme.primary, CircleShape),
            )
            Text(
                text = stringResource(theme.labelRes),
                color = if (selected) theme.highlight else Color.White,
                fontSize = 18.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    valueColor: Color = Color.White,
) {
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
            color = valueColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun ExteriorLightsState.label(context: Context): String = when (this) {
    ExteriorLightsState.On -> context.getString(R.string.exterior_lights_on)
    ExteriorLightsState.Off -> context.getString(R.string.exterior_lights_off)
    ExteriorLightsState.Unknown -> context.getString(R.string.exterior_lights_unknown)
}

private val ExteriorLightsState.color: Color
    get() = when (this) {
        ExteriorLightsState.On -> Color(0xFF83D47C)
        ExteriorLightsState.Off -> Color(0xFFC2CAD3)
        ExteriorLightsState.Unknown -> Color(0xFF8D9AA7)
    }

private fun DoorStates?.label(context: Context): String = when {
    this == null -> context.getString(R.string.doors_unknown)
    !hasOpenDoor -> context.getString(R.string.doors_all_closed)
    else -> context.getString(
        R.string.doors_open,
        listOfNotNull(
            "1".takeIf { door1Driver },
            "2".takeIf { door2FrontPassenger },
            "3".takeIf { door3RearDriver },
            "4".takeIf { door4RearPassenger },
        ).joinToString(", "),
    )
}

private val DoorStates?.color: Color
    get() = when {
        this == null -> Color(0xFF8D9AA7)
        hasOpenDoor -> Color(0xFFF1B75B)
        else -> Color(0xFF83D47C)
    }

private fun Float?.label(context: Context): String = when (this) {
    null -> context.getString(R.string.can_speed_unknown)
    else -> String.format(Locale.getDefault(), "%.2f km/h", this)
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
        color = if (highlighted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
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
                tint = if (highlighted) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimary,
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
    GolfLauncherApp(
        exteriorLights = MutableStateFlow(ExteriorLightsState.On),
        doors = MutableStateFlow(DoorStates(false, true, false, false)),
        vehicleSpeedKph = MutableStateFlow(3.08f),
        lastVehicleSpeedKph = MutableStateFlow(3.08f),
    )
}

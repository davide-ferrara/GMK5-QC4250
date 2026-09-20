package com.golfv.launcher

import android.content.Intent
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.golfv.launcher.ui.GolfLauncherApp

class MainActivity : ComponentActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var vehicleSignals: VehicleSignals
    private var welcomeSoundPool: SoundPool? = null
    private var welcomeSoundId = 0
    private var welcomeSoundLoaded = false
    private var pendingWelcomePlayback = false
    private var welcomeStreamId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
        initializeWelcomeSound()
        vehicleSignals = VehicleSignals(applicationContext)

        setContent {
            GolfLauncherApp(
                exteriorLights = vehicleSignals.exteriorLights,
                doors = vehicleSignals.doors,
                vehicleSpeedKph = vehicleSignals.vehicleSpeedKph,
                lastVehicleSpeedKph = vehicleSignals.lastVehicleSpeedKph,
                onSplashFinished = ::playWelcomeSound,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        vehicleSignals.start()
    }

    override fun onStop() {
        vehicleSignals.stop()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // The activity is singleTask, so opening an already-running launcher
        // delivers a new intent instead of recreating the splash composable.
        window.decorView.postDelayed({ playWelcomeSound() }, WELCOME_START_DELAY_MS)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onDestroy() {
        stopWelcomeSound()
        welcomeSoundPool?.release()
        welcomeSoundPool = null
        super.onDestroy()
    }

    private fun initializeWelcomeSound() {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val pool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(attributes)
            .build()
        welcomeSoundPool = pool
        pool.setOnLoadCompleteListener { loadedPool, _, status ->
            mainHandler.post {
                if (welcomeSoundPool !== loadedPool) return@post
                welcomeSoundLoaded = status == 0
                if (welcomeSoundLoaded && pendingWelcomePlayback) {
                    pendingWelcomePlayback = false
                    playWelcomeSoundNow()
                }
            }
        }
        welcomeSoundId = pool.load(this, R.raw.welcome_sound, 1)
    }

    private fun playWelcomeSound() {
        stopWelcomeSound()
        if (!welcomeSoundLoaded) {
            pendingWelcomePlayback = true
            return
        }
        playWelcomeSoundNow()
    }

    private fun playWelcomeSoundNow() {
        val pool = welcomeSoundPool ?: return
        if (!welcomeSoundLoaded || welcomeSoundId == 0) return

        welcomeStreamId = pool.play(welcomeSoundId, 1f, 1f, 1, 0, 1f)
    }

    private fun stopWelcomeSound() {
        pendingWelcomePlayback = false
        val streamId = welcomeStreamId
        welcomeStreamId = 0
        if (streamId != 0) welcomeSoundPool?.stop(streamId)
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private companion object {
        const val WELCOME_START_DELAY_MS = 150L
    }
}

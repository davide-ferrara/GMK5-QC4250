package com.golfv.launcher

import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
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
    private var welcomePlaybackRequested = false
    private var welcomePlayAttempts = 0
    private var welcomeStreamId = 0
    private val retryWelcomePlayback = Runnable { playWelcomeSoundNow() }

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
                parkingBrakeApplied = vehicleSignals.parkingBrakeApplied,
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(retryWelcomePlayback)
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
                if (!welcomeSoundLoaded) {
                    Log.w(TAG, "Welcome sound failed to load: status=$status")
                } else if (welcomePlaybackRequested) {
                    playWelcomeSoundNow()
                }
            }
        }
        welcomeSoundId = pool.load(this, R.raw.welcome_sound, 1)
    }

    private fun playWelcomeSound() {
        // HOME is a singleTask activity and may receive several intents while the
        // unit is booting. Consume the splash callback only once so a later event
        // can never stop and restart a chime which is already playing.
        if (welcomePlaybackRequested) return
        welcomePlaybackRequested = true
        if (welcomeSoundLoaded) playWelcomeSoundNow()
    }

    private fun playWelcomeSoundNow() {
        val pool = welcomeSoundPool ?: return
        if (!welcomeSoundLoaded || welcomeSoundId == 0) return

        welcomePlayAttempts++
        val streamId = pool.play(welcomeSoundId, 1f, 1f, 1, 0, 1f)
        if (streamId != 0) {
            welcomeStreamId = streamId
            return
        }

        if (welcomePlayAttempts < MAX_WELCOME_PLAY_ATTEMPTS) {
            mainHandler.postDelayed(retryWelcomePlayback, WELCOME_RETRY_DELAY_MS)
        } else {
            Log.w(TAG, "Welcome sound did not start after $welcomePlayAttempts attempts")
        }
    }

    private fun stopWelcomeSound() {
        mainHandler.removeCallbacks(retryWelcomePlayback)
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
        const val TAG = "GolfLauncherAudio"
        const val MAX_WELCOME_PLAY_ATTEMPTS = 3
        const val WELCOME_RETRY_DELAY_MS = 200L
    }
}

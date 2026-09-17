package com.golfv.launcher

import android.animation.ValueAnimator
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
import com.golfv.launcher.ui.theme.GolfLauncherTheme

class MainActivity : ComponentActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var welcomeSoundPool: SoundPool? = null
    private var welcomeSoundId = 0
    private var welcomeSoundLoaded = false
    private var pendingWelcomePlayback = false
    private var welcomeStreamId = 0
    private var welcomeFadeAnimator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
        initializeWelcomeSound()

        setContent {
            GolfLauncherTheme {
                GolfLauncherApp(onSplashFinished = ::playWelcomeSound)
            }
        }
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

        val streamId = pool.play(welcomeSoundId, 0f, 0f, 1, 0, 1f)
        if (streamId == 0) return
        welcomeStreamId = streamId

        welcomeFadeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = WELCOME_SOUND_DURATION_MS
            addUpdateListener { animator ->
                if (welcomeStreamId != streamId) return@addUpdateListener

                val elapsedMs = (WELCOME_SOUND_DURATION_MS * animator.animatedFraction).toLong()
                val gain = when {
                    elapsedMs < WELCOME_FADE_IN_MS ->
                        elapsedMs.toFloat() / WELCOME_FADE_IN_MS
                    elapsedMs > WELCOME_SOUND_DURATION_MS - WELCOME_FADE_OUT_MS ->
                        (WELCOME_SOUND_DURATION_MS - elapsedMs).toFloat() / WELCOME_FADE_OUT_MS
                    else -> 1f
                }.coerceIn(0f, 1f)
                pool.setVolume(streamId, WELCOME_VOLUME * gain, WELCOME_VOLUME * gain)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (welcomeStreamId == streamId) {
                        welcomeStreamId = 0
                        pool.stop(streamId)
                    }
                }
            })
            start()
        }
    }

    private fun stopWelcomeSound() {
        pendingWelcomePlayback = false
        val streamId = welcomeStreamId
        welcomeStreamId = 0
        welcomeFadeAnimator?.cancel()
        welcomeFadeAnimator = null
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
        const val WELCOME_VOLUME = 1f
        const val WELCOME_START_DELAY_MS = 150L
        const val WELCOME_SOUND_DURATION_MS = 1_929L
        const val WELCOME_FADE_IN_MS = 280L
        const val WELCOME_FADE_OUT_MS = 720L
    }
}

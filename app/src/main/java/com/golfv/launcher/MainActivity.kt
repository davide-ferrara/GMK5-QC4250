package com.golfv.launcher

import android.animation.ValueAnimator
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.golfv.launcher.ui.GolfLauncherApp
import com.golfv.launcher.ui.theme.GolfLauncherTheme

class MainActivity : ComponentActivity() {
    private var welcomePlayer: MediaPlayer? = null
    private var welcomeFadeAnimator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

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
        super.onDestroy()
    }

    private fun playWelcomeSound() {
        stopWelcomeSound()

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val player = runCatching {
            MediaPlayer.create(
                this,
                R.raw.welcome_sound,
                attributes,
                AudioManager.AUDIO_SESSION_ID_GENERATE,
            )
        }.getOrNull() ?: return

        welcomePlayer = player
        player.setVolume(0f, 0f)
        player.setOnCompletionListener {
            if (welcomePlayer === it) {
                welcomeFadeAnimator?.cancel()
                welcomeFadeAnimator = null
                welcomePlayer = null
            }
            it.release()
        }
        runCatching { player.start() }
            .onSuccess { startWelcomeFade(player) }
            .onFailure {
                player.release()
                if (welcomePlayer === player) welcomePlayer = null
            }
    }

    private fun startWelcomeFade(player: MediaPlayer) {
        val durationMs = player.duration.toLong()
        if (durationMs <= 0L) {
            player.setVolume(WELCOME_VOLUME, WELCOME_VOLUME)
            return
        }

        welcomeFadeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            addUpdateListener { animator ->
                if (welcomePlayer !== player) return@addUpdateListener

                val elapsedMs = (durationMs * animator.animatedFraction).toLong()
                val gain = when {
                    elapsedMs < WELCOME_FADE_IN_MS ->
                        elapsedMs.toFloat() / WELCOME_FADE_IN_MS
                    elapsedMs > durationMs - WELCOME_FADE_OUT_MS ->
                        (durationMs - elapsedMs).toFloat() / WELCOME_FADE_OUT_MS
                    else -> 1f
                }.coerceIn(0f, 1f)
                player.setVolume(WELCOME_VOLUME * gain, WELCOME_VOLUME * gain)
            }
            start()
        }
    }

    private fun stopWelcomeSound() {
        welcomeFadeAnimator?.cancel()
        welcomeFadeAnimator = null
        welcomePlayer?.release()
        welcomePlayer = null
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
        const val WELCOME_START_DELAY_MS = 600L
        const val WELCOME_FADE_IN_MS = 280L
        const val WELCOME_FADE_OUT_MS = 720L
    }
}

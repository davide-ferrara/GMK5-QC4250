package com.golfv.launcher

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
        playWelcomeSoundOnce()

        setContent {
            GolfLauncherTheme {
                GolfLauncherApp()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onDestroy() {
        welcomePlayer?.release()
        welcomePlayer = null
        super.onDestroy()
    }

    private fun playWelcomeSoundOnce() {
        if (welcomeSoundPlayed) return

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
        player.setVolume(WELCOME_VOLUME, WELCOME_VOLUME)
        player.setOnCompletionListener {
            it.release()
            if (welcomePlayer === it) welcomePlayer = null
        }
        runCatching { player.start() }
            .onSuccess { welcomeSoundPlayed = true }
            .onFailure {
                player.release()
                welcomePlayer = null
            }
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private companion object {
        const val WELCOME_VOLUME = 0.55f
        var welcomeSoundPlayed = false
    }
}

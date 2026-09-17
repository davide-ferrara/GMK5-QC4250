package com.golfv.launcher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
internal fun ClockAndDate() {
    val context = LocalContext.current
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    var locale by remember { mutableStateOf(Locale.getDefault()) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                now = ZonedDateTime.now()
                locale = Locale.getDefault()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    val timeText = remember(now, locale) {
        DateTimeFormatter.ofPattern("HH:mm", locale).format(now)
    }
    val dateText = remember(now, locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
            .withLocale(locale)
            .format(now)
    }

    Column {
        Text(
            text = timeText,
            color = Color.White,
            fontSize = 76.sp,
            lineHeight = 76.sp,
            fontWeight = FontWeight.Light,
        )
        Text(
            text = dateText,
            color = Color(0xFFC2CAD3),
            fontSize = 22.sp,
            fontWeight = FontWeight.Normal,
        )
    }
}

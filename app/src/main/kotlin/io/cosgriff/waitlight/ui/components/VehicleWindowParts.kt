package io.cosgriff.waitlight.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.cosgriff.waitlight.R
import io.cosgriff.waitlight.domain.RapidScreenWindow
import io.cosgriff.waitlight.domain.VehicleRules
import io.cosgriff.waitlight.domain.VehicleWindow
import io.cosgriff.waitlight.domain.WindowState
import io.cosgriff.waitlight.ui.TimeText
import kotlin.math.roundToInt

/** "In your window · 132 days left", "Window opens May 1, 2026 · in 45 days", "Window closed · plan a station test". */
@Composable
fun windowStatusText(vehicle: VehicleWindow): String = when (val state = vehicle.state) {
    is WindowState.Open -> if (state.daysLeft == 0L) {
        stringResource(R.string.window_open_last_day)
    } else {
        pluralStringResource(R.plurals.window_open, state.daysLeft.toInt(), state.daysLeft.toInt())
    }
    is WindowState.Before -> pluralStringResource(R.plurals.window_before, state.daysUntil.toInt(), TimeText.mediumDate(vehicle.window.opens), state.daysUntil.toInt())
    WindowState.Closed -> stringResource(R.string.window_closed)
    is WindowState.Past -> stringResource(R.string.window_past)
}

/** "1 of 2 drive-bys", or "3 drive-bys" once two are logged. */
@Composable
fun windowCountText(count: Int): String = if (count < VehicleRules.READINGS_NEEDED) {
    pluralStringResource(R.plurals.window_count, VehicleRules.READINGS_NEEDED, count, VehicleRules.READINGS_NEEDED)
} else {
    pluralStringResource(R.plurals.window_count_many, count, count)
}

/**
 * How far through [window] today is: a rounded bar filled to [fraction], with a tick at today when [marker] is
 * given. Read aloud with the window's dates, so the fill is never the only signal.
 */
@Composable
fun WindowBar(
    window: RapidScreenWindow,
    fraction: Float,
    fill: Color,
    track: Color,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    marker: Color? = null,
) {
    val percent = (fraction * 100).roundToInt()
    val description = pluralStringResource(R.plurals.window_bar_desc, percent, TimeText.mediumDate(window.opens), TimeText.mediumDate(window.closes), percent)
    val shape = RoundedCornerShape(height / 2)
    Box(modifier.fillMaxWidth().height(height + if (marker != null) 10.dp else 0.dp).clearAndSetSemantics { contentDescription = description; role = Role.Image }) {
        Box(Modifier.fillMaxWidth().height(height).offset(y = if (marker != null) 5.dp else 0.dp).clip(shape).background(track)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).clip(shape).background(fill))
        }
        if (marker != null) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height + 10.dp)) {
                Box(Modifier.align(Alignment.CenterEnd).width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(marker))
            }
        }
    }
}

/**
 * Asks Android for permission to post notifications, only when a reminder is being turned on. [onAnswer] gets
 * whether reminders can show: at once when the permission is already granted, else after Android's dialog.
 */
@Composable
fun rememberNotificationAsk(onAnswer: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val answer by rememberUpdatedState(onAnswer)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> answer(granted) }
    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            answer(true)
        } else {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

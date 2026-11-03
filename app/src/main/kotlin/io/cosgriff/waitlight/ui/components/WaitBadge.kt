package io.cosgriff.waitlight.ui.components

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cosgriff.waitlight.R
import io.cosgriff.waitlight.domain.Badge
import io.cosgriff.waitlight.domain.ShownWait
import io.cosgriff.waitlight.domain.WaitBand
import io.cosgriff.waitlight.ui.TimeText
import io.cosgriff.waitlight.ui.theme.BadgeColors
import io.cosgriff.waitlight.ui.theme.WaitColors
import io.cosgriff.waitlight.ui.theme.waitColors

/** A badge's measurements: the list's 52dp, the detail's 76dp. */
data class BadgeSize(
    val diameter: Dp,
    val number: TextUnit,
    val word: TextUnit,
    val label: TextUnit,
    val iconCircle: Dp,
    val icon: Dp,
    val iconOffset: Dp,
) {
    companion object {
        val Row = BadgeSize(52.dp, 18.sp, 12.sp, 10.sp, 22.dp, 16.dp, 4.dp)
        val Detail = BadgeSize(76.dp, 28.sp, 16.sp, 12.sp, 28.dp, 20.dp, 2.dp)
    }
}

/**
 * A station's wait badge: the figure in a coloured circle, with the kind's icon on a small circle of [ringColor]
 * (the colour behind the badge) at its lower right. Colour is never the only signal: the icon and the content
 * description say the same.
 */
@Composable
fun WaitBadge(badge: Badge, size: BadgeSize, ringColor: Color, modifier: Modifier = Modifier) {
    val colors = badge.colors(MaterialTheme.waitColors)
    val description = badgeDescription(badge)
    Box(modifier.clearAndSetSemantics { contentDescription = description; role = Role.Image }) {
        Column(
            Modifier.size(size.diameter).background(colors.container, CircleShape),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val (big, small) = badge.figure(LocalResources.current)
            val isNumber = badge.shownWait() != null
            Text(
                big,
                color = colors.content,
                fontSize = if (isNumber) size.number else size.word,
                fontWeight = FontWeight.Bold,
                lineHeight = if (isNumber) size.number else size.word,
                maxLines = 1,
            )
            if (small != null) {
                Text(small, color = colors.content, fontSize = size.label, fontWeight = FontWeight.SemiBold, lineHeight = size.label * 1.3f)
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(size.iconOffset, size.iconOffset)
                .size(size.iconCircle)
                .background(ringColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(badge.icon(), contentDescription = null, tint = colors.icon, modifier = Modifier.size(size.icon))
        }
    }
}

/** The wait a badge shows, if it shows one. */
fun Badge.shownWait(): ShownWait? = when (this) {
    is Badge.Live -> wait
    is Badge.Stale -> wait
    else -> null
}

/** The big figure and the small word under it. Outside composition too, for the widget. */
fun Badge.figure(resources: Resources): Pair<String, String?> = when (val wait = shownWait()) {
    is ShownWait.Exact -> resources.getString(R.string.badge_minutes, wait.minutes) to resources.getString(R.string.badge_min)
    is ShownWait.Over -> resources.getString(R.string.badge_over, wait.minutes) to resources.getString(R.string.badge_min)
    null -> when (this) {
        Badge.Closed -> resources.getString(R.string.badge_closed) to null
        Badge.NoReading -> resources.getString(R.string.badge_no_reading) to null
        // Open, or the last reading was "Station Open".
        else -> resources.getString(R.string.badge_open) to resources.getString(R.string.badge_no_estimate)
    }
}

fun Badge.colors(colors: WaitColors): BadgeColors = when (this) {
    is Badge.Live -> colors.forBand(band)
    Badge.Open -> colors.open
    Badge.Closed -> colors.closed
    Badge.NoReading, is Badge.Stale -> colors.stale
}

fun WaitColors.forBand(band: WaitBand): BadgeColors = when (band) {
    WaitBand.SHORT -> short
    WaitBand.MODERATE -> moderate
    WaitBand.LONG -> long
}

fun Badge.icon(): ImageVector = when (this) {
    is Badge.Live -> when (band) {
        WaitBand.SHORT -> Icons.Rounded.CheckCircle
        WaitBand.MODERATE -> Icons.Rounded.Schedule
        WaitBand.LONG -> Icons.Rounded.Warning
    }
    Badge.Open -> Icons.Rounded.RemoveCircle
    Badge.Closed -> Icons.Rounded.Bedtime
    Badge.NoReading -> Icons.Rounded.HourglassEmpty
    is Badge.Stale -> Icons.Rounded.History
}

/** What a screen reader says for a badge: "Short wait, 13 minutes". */
@Composable
fun badgeDescription(badge: Badge): String = badgeDescription(badge, LocalResources.current)

/** The same, outside composition, for the widget. */
fun badgeDescription(badge: Badge, resources: Resources): String = when (badge) {
    is Badge.Live -> {
        val (id, minutes) = liveDescription(badge.band, badge.wait)
        resources.getQuantityString(id, minutes, minutes)
    }
    Badge.Open -> resources.getString(R.string.badge_desc_open)
    Badge.Closed -> resources.getString(R.string.badge_desc_closed)
    Badge.NoReading -> resources.getString(R.string.badge_desc_no_reading)
    is Badge.Stale -> {
        val asOf = TimeText.time(badge.asOf)
        val band = badge.band
        val wait = badge.wait
        if (band == null || wait == null) {
            resources.getString(R.string.badge_desc_stale_open, asOf)
        } else {
            val (id, minutes) = staleDescription(band, wait)
            resources.getQuantityString(id, minutes, minutes, asOf)
        }
    }
}

private fun liveDescription(band: WaitBand, wait: ShownWait): Pair<Int, Int> = when (wait) {
    is ShownWait.Exact -> when (band) {
        WaitBand.SHORT -> R.plurals.badge_desc_short_exact
        WaitBand.MODERATE -> R.plurals.badge_desc_moderate_exact
        WaitBand.LONG -> R.plurals.badge_desc_long_exact
    } to wait.minutes
    is ShownWait.Over -> when (band) {
        WaitBand.SHORT -> R.plurals.badge_desc_short_over
        WaitBand.MODERATE -> R.plurals.badge_desc_moderate_over
        WaitBand.LONG -> R.plurals.badge_desc_long_over
    } to wait.minutes
}

private fun staleDescription(band: WaitBand, wait: ShownWait): Pair<Int, Int> = when (wait) {
    is ShownWait.Exact -> when (band) {
        WaitBand.SHORT -> R.plurals.badge_desc_stale_short_exact
        WaitBand.MODERATE -> R.plurals.badge_desc_stale_moderate_exact
        WaitBand.LONG -> R.plurals.badge_desc_stale_long_exact
    } to wait.minutes
    is ShownWait.Over -> when (band) {
        WaitBand.SHORT -> R.plurals.badge_desc_stale_short_over
        WaitBand.MODERATE -> R.plurals.badge_desc_stale_moderate_over
        WaitBand.LONG -> R.plurals.badge_desc_stale_long_over
    } to wait.minutes
}

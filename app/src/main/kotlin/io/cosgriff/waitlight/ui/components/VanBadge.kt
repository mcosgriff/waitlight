package io.cosgriff.waitlight.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.PauseCircleOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.cosgriff.waitlight.R
import io.cosgriff.waitlight.domain.VanName
import io.cosgriff.waitlight.domain.VanStatus
import io.cosgriff.waitlight.ui.TimeText
import io.cosgriff.waitlight.ui.theme.waitColors

/** A van badge's look: its fill, icon colour and, for the outline kinds, its border. */
data class VanBadgeLook(val container: Color, val content: Color, val border: Color?, val icon: ImageVector)

/**
 * A van site's badge, as `design/vans/source/Main.dc.html` draws it: a rounded square with the status's icon.
 * Colour is never the only signal: the icon differs per status, and [description] says it in words.
 */
@Composable
fun VanBadge(status: VanStatus, description: String, size: Dp, modifier: Modifier = Modifier) {
    val look = vanBadgeLook(status)
    val shape = RoundedCornerShape(size * 0.29f)
    Box(
        modifier
            .size(size)
            .background(look.container, shape)
            .then(if (look.border != null) Modifier.border(1.5.dp, look.border, shape) else Modifier)
            .clearAndSetSemantics { contentDescription = description; role = Role.Image },
        contentAlignment = Alignment.Center,
    ) {
        Icon(look.icon, contentDescription = null, tint = look.content, modifier = Modifier.size(size / 2))
    }
}

@Composable
@ReadOnlyComposable
fun vanBadgeLook(status: VanStatus): VanBadgeLook {
    val colors = MaterialTheme.waitColors
    val outline = MaterialTheme.colorScheme.outline
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    return when (status) {
        VanStatus.Reading -> VanBadgeLook(colors.short.container, colors.short.content, null, Icons.Rounded.Sensors)
        VanStatus.Paused -> VanBadgeLook(colors.open.container, colors.open.content, null, Icons.Rounded.PauseCircleOutline)
        VanStatus.OnSiteUnknown -> VanBadgeLook(colors.stale.container, colors.stale.content, null, Icons.AutoMirrored.Rounded.HelpOutline)
        VanStatus.Done -> VanBadgeLook(Color.Transparent, muted, outline, Icons.Rounded.Schedule)
        VanStatus.NotSetUpYet, is VanStatus.StartsAt, VanStatus.Scheduled ->
            VanBadgeLook(Color.Transparent, muted, outline, Icons.Rounded.CalendarMonth)
    }
}

/** The status in words: "Reading cars now", "Starts at 6:35 AM". */
@Composable
fun vanStatusText(status: VanStatus): String = when (status) {
    VanStatus.Reading -> stringResource(R.string.van_status_reading)
    VanStatus.Paused -> stringResource(R.string.van_status_paused)
    VanStatus.OnSiteUnknown -> stringResource(R.string.van_status_unknown)
    VanStatus.NotSetUpYet -> stringResource(R.string.van_status_not_set_up)
    VanStatus.Done -> stringResource(R.string.van_status_done)
    is VanStatus.StartsAt -> stringResource(R.string.van_status_starts, TimeText.time(status.time))
    VanStatus.Scheduled -> stringResource(R.string.van_status_scheduled)
}

/** The status word's colour: green while reading, muted otherwise. */
@Composable
@ReadOnlyComposable
fun vanStatusColor(status: VanStatus): Color =
    if (status == VanStatus.Reading) MaterialTheme.waitColors.short.icon else MaterialTheme.colorScheme.onSurfaceVariant

/** A site's name to read: "Harlan St → EB I-70". */
@Composable
fun vanNameText(name: VanName): String = when (name) {
    is VanName.Ramp -> stringResource(R.string.van_name_ramp, name.from, name.onto)
    is VanName.Road -> stringResource(R.string.van_name_road, name.road, name.past)
    is VanName.Plain -> name.text
}

/** A site's name to hear, and for a map app's label: "Harlan St to EB I-70". */
@Composable
fun vanNameSpoken(name: VanName): String = when (name) {
    is VanName.Ramp -> stringResource(R.string.van_name_ramp_spoken, name.from, name.onto)
    is VanName.Road -> stringResource(R.string.van_name_road, name.road, name.past)
    is VanName.Plain -> name.text
}

/** "7 AM–5 PM". */
@Composable
fun vanHoursText(opens: java.time.LocalTime, closes: java.time.LocalTime): String =
    stringResource(R.string.van_hours, TimeText.time(opens), TimeText.time(closes))

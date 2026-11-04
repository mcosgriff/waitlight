package io.cosgriff.waitlight.widget

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.android.EntryPointAccessors
import io.cosgriff.waitlight.MainActivity
import io.cosgriff.waitlight.R
import io.cosgriff.waitlight.domain.Badge
import io.cosgriff.waitlight.domain.WaitBand
import io.cosgriff.waitlight.domain.WidgetContent
import io.cosgriff.waitlight.domain.StationRow
import io.cosgriff.waitlight.ui.TimeText
import io.cosgriff.waitlight.ui.components.badgeDescription
import io.cosgriff.waitlight.ui.components.colors
import io.cosgriff.waitlight.ui.components.figure
import io.cosgriff.waitlight.ui.components.shownWait
import io.cosgriff.waitlight.ui.theme.BadgeColors
import io.cosgriff.waitlight.ui.theme.WaitColors
import io.cosgriff.waitlight.ui.theme.waitColors

/**
 * The home-screen widget, as `design/png/Widget.png`: the three nearest stations with the list's badges, or a
 * line asking to open the app without a location. Colours from [waitColors], light or dark with the phone; the
 * system font; 28dp corners (the theme's `extraLarge`). A station opens its detail, anywhere else the list.
 */
class WaitsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val updater = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).widgetUpdater()
        val first = updater.current()
        provideContent {
            // A redraw while the widget runs reaches it here.
            val drawing by updater.drawing.collectAsState()
            WidgetBody(drawing ?: first)
        }
    }
}

class WaitsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WaitsWidget()

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        GlanceWidgetHost.cancelRedraws(context)
    }
}

private val light = waitColors(dark = false)
private val dark = waitColors(dark = true)

/** A colour that follows the phone's light or dark. */
private fun themed(pick: (WaitColors) -> Color): ColorProvider = ColorProvider(day = pick(light), night = pick(dark))

private fun themedBadge(badge: Badge, pick: (BadgeColors) -> Color): ColorProvider =
    themed { pick(badge.colors(it)) }

@Composable
private fun WidgetBody(drawing: WidgetDrawing) {
    val context = LocalContext.current
    val resources = context.resources
    Column(
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(28.dp)
            .background(themed { it.widget.background })
            .padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 14.dp)
            .clickable(actionStartActivity(MainActivity.listIntent(context))),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                resources.getString(R.string.widget_title),
                GlanceModifier.defaultWeight(),
                style = TextStyle(color = themed { it.widget.title }, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            val newest = (drawing.content as? WidgetContent.Nearest)?.newestUpdate
            if (newest != null) {
                Text(
                    resources.getString(R.string.widget_updated, TimeText.timeSince(newest, drawing.now)),
                    style = TextStyle(color = themed { it.widget.muted }, fontSize = 12.sp),
                    maxLines = 1,
                )
            }
        }
        Spacer(GlanceModifier.height(12.dp))
        when (val content = drawing.content) {
            WidgetContent.NeedsLocation -> Text(
                resources.getString(R.string.widget_needs_location),
                style = TextStyle(color = themed { it.widget.status }, fontSize = 14.sp),
            )
            is WidgetContent.Nearest -> content.rows.forEachIndexed { index, row ->
                if (index > 0) Spacer(GlanceModifier.height(10.dp))
                // An equal share each of a widget made taller than the rows, rather than a gap below them.
                Box(GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.CenterStart) {
                    StationRowItem(row, resources)
                }
            }
        }
    }
}

@Composable
private fun StationRowItem(row: StationRow, resources: Resources) {
    val context = LocalContext.current
    Row(
        GlanceModifier.fillMaxWidth().clickable(actionStartActivity(MainActivity.stationIntent(context, row.station.id))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetBadge(row.badge, resources)
        Spacer(GlanceModifier.width(12.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(
                row.station.name,
                style = TextStyle(color = themed { it.widget.title }, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            Text(
                resources.getString(R.string.widget_miles, row.distanceMiles),
                style = TextStyle(color = themed { it.widget.muted }, fontSize = 12.sp),
                maxLines = 1,
            )
        }
        Text(
            resources.getString(row.badge.statusWord()),
            style = TextStyle(color = themed { it.widget.status }, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
    }
}

/**
 * The list's badge at the design's 44dp, with its icon on a 20dp circle at the lower right. Colour is never the
 * only signal: the figure, the icon and the content description say the same.
 */
@Composable
private fun WidgetBadge(badge: Badge, resources: Resources) {
    Box(GlanceModifier.size(BADGE_BOX)) {
        Box(
            GlanceModifier
                .size(BADGE)
                .cornerRadius(BADGE / 2)
                .background(themedBadge(badge) { it.container })
                .semantics { contentDescription = badgeDescription(badge, resources) },
            contentAlignment = Alignment.Center,
        ) {
            val isNumber = badge.shownWait() != null
            Text(
                badge.figure(resources).first,
                style = TextStyle(
                    color = themedBadge(badge) { it.content },
                    fontSize = if (isNumber) 16.sp else 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
        }
        Box(GlanceModifier.size(BADGE_BOX), contentAlignment = Alignment.BottomEnd) {
            Box(
                GlanceModifier.size(20.dp).cornerRadius(10.dp).background(themed { it.widget.background }),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    ImageProvider(badge.iconRes()),
                    contentDescription = null,
                    modifier = GlanceModifier.size(14.dp),
                    colorFilter = ColorFilter.tint(themedBadge(badge) { it.icon }),
                )
            }
        }
    }
}

private val BADGE = 44.dp

// The icon's circle sits 4dp outside the badge, right and below.
private val BADGE_BOX = 48.dp

private fun Badge.iconRes(): Int = when (this) {
    is Badge.Live -> when (band) {
        WaitBand.SHORT -> R.drawable.ic_widget_check_circle
        WaitBand.MODERATE -> R.drawable.ic_widget_schedule
        WaitBand.LONG -> R.drawable.ic_widget_warning
    }
    Badge.Open -> R.drawable.ic_widget_remove_circle
    Badge.Closed -> R.drawable.ic_widget_bedtime
    Badge.NoReading -> R.drawable.ic_widget_hourglass_empty
    is Badge.Stale -> R.drawable.ic_widget_history
}

private fun Badge.statusWord(): Int = when (this) {
    is Badge.Live -> when (band) {
        WaitBand.SHORT -> R.string.widget_status_short
        WaitBand.MODERATE -> R.string.widget_status_moderate
        WaitBand.LONG -> R.string.widget_status_long
    }
    Badge.Open -> R.string.widget_status_open
    Badge.Closed -> R.string.widget_status_closed
    Badge.NoReading -> R.string.widget_status_no_reading
    is Badge.Stale -> R.string.widget_status_stale
}

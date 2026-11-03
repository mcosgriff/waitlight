package io.cosgriff.waitlight.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import org.robolectric.RuntimeEnvironment
import io.cosgriff.waitlight.domain.AirCareHours
import io.cosgriff.waitlight.domain.GeoPoint
import io.cosgriff.waitlight.domain.Stations
import io.cosgriff.waitlight.domain.WaitReading
import java.time.Instant
import java.time.LocalDateTime

/** A tall phone, so a whole screen fits without scrolling. */
const val TALL_PHONE = "w411dp-h1100dp-xhdpi"

/** A time in Denver, as AirCare keeps it: "2026-11-02T14:20" (a Monday). */
fun denver(text: String): Instant = LocalDateTime.parse(text).atZone(AirCareHours.zone).toInstant()

/** The app's own wording, as the screens show it. */
fun text(id: Int, vararg args: Any): String = RuntimeEnvironment.getApplication().getString(id, *args)

/** Standing at the Arvada station: Arvada is nearest, 0 miles and a 5-minute drive. */
val AT_ARVADA: GeoPoint = Stations.all.first { it.name == "Arvada" }.location

const val ARVADA = 38
const val AURORA = 49
const val BOULDER = 46

/** Three stations' readings, all "Last updated" at [at]: Arvada 13 min, Aurora 45, Boulder over 60. */
fun threeWaits(at: Instant): Map<Int, Pair<WaitReading, Instant>> = mapOf(
    ARVADA to (WaitReading.Minutes(13) to at),
    AURORA to (WaitReading.Minutes(45) to at),
    BOULDER to (WaitReading.MoreThan(60) to at),
)

/**
 * A node whose content description is [description]. Newer Android puts a narrow no-break space before "PM", so
 * spaces count as spaces whichever kind they are.
 */
fun hasDescription(description: String) = SemanticsMatcher("content description \"$description\"") { node ->
    node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.spaced() == description }
}

/** As [hasDescription], for a node's text. */
fun hasTextSpaced(text: String) = SemanticsMatcher("text \"$text\"") { node ->
    node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text.spaced() == text }
}

private fun String.spaced() = replace(' ', ' ').replace(' ', ' ')

package io.cosgriff.waitlight.ui.detail

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.cosgriff.waitlight.R
import io.cosgriff.waitlight.domain.GeoPoint
import io.cosgriff.waitlight.domain.Stations
import io.cosgriff.waitlight.domain.WaitReading
import io.cosgriff.waitlight.domain.WaitThresholds
import io.cosgriff.waitlight.domain.rowsFor
import io.cosgriff.waitlight.ui.ARVADA
import io.cosgriff.waitlight.ui.AT_ARVADA
import io.cosgriff.waitlight.ui.BOULDER
import io.cosgriff.waitlight.ui.TALL_PHONE
import io.cosgriff.waitlight.ui.denver
import io.cosgriff.waitlight.ui.hasDescription
import io.cosgriff.waitlight.ui.hasTextSpaced
import io.cosgriff.waitlight.ui.text
import io.cosgriff.waitlight.ui.theme.WaitlightTheme
import io.cosgriff.waitlight.ui.threeWaits
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** A station's detail, with its row and badge as the ViewModel would make them. Monday 2026-11-02 in Denver. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = TALL_PHONE, sdk = [35])
class StationDetailScreenTest {
    @get:Rule val compose = createComposeRule()

    private val tapped = mutableListOf<String>()

    private fun show(
        station: Int,
        now: Instant,
        waits: Map<Int, Pair<WaitReading, Instant>> = threeWaits(denver("2026-11-02T14:14")),
        here: GeoPoint? = AT_ARVADA,
        dataUsable: Boolean = true,
    ) {
        val row = rowsFor(waits, here).first { it.station.id == station }
        val badge = row.badge(now, WaitThresholds(), dataUsable)
        compose.setContent {
            WaitlightTheme(dark = false) {
                StationDetailContent(
                    row = row,
                    badge = badge,
                    onBack = { tapped += "back" },
                    onDirections = { tapped += "directions" },
                    onCall = { tapped += "call" },
                )
            }
        }
    }

    private fun shown(text: String) = compose.onNode(hasTextSpaced(text)).assertExists()

    @Test
    fun `a fresh short wait - its badge, status, total and AirCare's time`() {
        show(ARVADA, now = denver("2026-11-02T14:20"))
        compose.onNode(hasDescription("Short wait, 13 minutes")).assertExists()
        shown(text(R.string.detail_status_short))
        shown("≈ 18 min total · 5 min drive (0.0 mi)")
        shown("AirCare updated 2:14 PM")
        shown(text(R.string.detail_rims_no))
    }

    @Test
    fun `a stale wait - last known badge, out of date, and the drive without a total`() {
        show(ARVADA, now = denver("2026-11-02T14:50"))
        compose.onNode(hasDescription("Last known short wait, 13 minutes as of 2:14 PM, may be out of date")).assertExists()
        shown(text(R.string.detail_status_stale))
        shown("≈ 5 min drive (0.0 mi)")
        compose.onNode(hasTextSpaced("≈ 18 min total · 5 min drive (0.0 mi)")).assertDoesNotExist()
    }

    @Test
    fun `an unreadable page greys a wait that is still recent`() {
        show(ARVADA, now = denver("2026-11-02T14:20"), dataUsable = false)
        compose.onNode(hasDescription("Last known short wait, 13 minutes as of 2:14 PM, may be out of date")).assertExists()
        shown(text(R.string.detail_status_stale))
    }

    @Test
    fun `a long wait over 60, at a station that takes low-profile rims`() {
        show(BOULDER, now = denver("2026-11-02T14:20"))
        compose.onNode(hasDescription("Long wait, over 60 minutes")).assertExists()
        shown(text(R.string.detail_status_long))
        shown(text(R.string.detail_rims_yes))
    }

    @Test
    fun `closed - the closed badge and status`() {
        show(ARVADA, now = denver("2026-11-02T18:00"))
        compose.onNode(hasDescription(text(R.string.badge_desc_closed))).assertExists()
        shown(text(R.string.detail_status_closed))
    }

    @Test
    fun `no reading yet and no location - no wait, no drive`() {
        show(ARVADA, now = denver("2026-11-02T14:20"), waits = emptyMap(), here = null)
        compose.onNode(hasDescription(text(R.string.badge_desc_no_reading))).assertExists()
        shown(text(R.string.detail_status_no_reading))
        compose.onNode(hasTextSpaced("≈ 5 min drive (0.0 mi)")).assertDoesNotExist()
    }

    @Test
    fun `the station's map, described, with the OpenStreetMap credit`() {
        show(ARVADA, now = denver("2026-11-02T14:20"))
        compose.onNode(hasDescription(text(R.string.detail_map, "Arvada"))).assertExists()
        shown(text(R.string.detail_map_credit))
    }

    @Test
    fun `every station has its map`() {
        assertEquals(emptyList<String>(), Stations.all.filter { stationMap(it.id) == null }.map { it.name })
    }

    @Test
    fun `back, directions and call`() {
        show(ARVADA, now = denver("2026-11-02T14:20"))
        compose.onNode(hasDescription(text(R.string.detail_back))).performClick()
        compose.onNodeWithText(text(R.string.detail_directions)).performClick()
        compose.onNodeWithText(text(R.string.detail_call)).performClick()
        assertEquals(listOf("back", "directions", "call"), tapped)
    }
}

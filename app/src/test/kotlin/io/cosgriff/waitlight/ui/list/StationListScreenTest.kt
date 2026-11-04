package io.cosgriff.waitlight.ui.list

import android.app.Application
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import io.cosgriff.waitlight.R
import io.cosgriff.waitlight.data.aircare.WaitTimes
import io.cosgriff.waitlight.domain.FetchOutcome
import io.cosgriff.waitlight.domain.Here
import io.cosgriff.waitlight.domain.ListState
import io.cosgriff.waitlight.domain.SortOrder
import io.cosgriff.waitlight.domain.WaitReading
import io.cosgriff.waitlight.ui.ARVADA
import io.cosgriff.waitlight.ui.AT_ARVADA
import io.cosgriff.waitlight.ui.AURORA
import io.cosgriff.waitlight.ui.ZIP_80003
import io.cosgriff.waitlight.ui.TALL_PHONE
import io.cosgriff.waitlight.ui.TEST_DRIVES
import io.cosgriff.waitlight.ui.denver
import io.cosgriff.waitlight.ui.hasClickLabel
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

/**
 * The list screen in each of its states, drawn from the waits as the ViewModel would make them ([stationListUi]).
 * Monday 2026-11-02 in Denver; the stations are open 8 AM to 5:30 PM.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = TALL_PHONE, sdk = [35])
class StationListScreenTest {
    @get:Rule val compose = createComposeRule()

    private var refreshes = 0
    private var turnOnLocation = 0
    private var opened: Int? = null

    private val updated = denver("2026-11-02T14:14")

    private fun show(
        now: Instant,
        waits: Map<Int, Pair<WaitReading, Instant>> = threeWaits(updated),
        outcome: FetchOutcome = FetchOutcome.OK,
        here: Here? = Here.Device(AT_ARVADA, null),
        locationAllowed: Boolean = here != null,
    ): StationListUi {
        val ui = stationListUi(now, WaitTimes(waits, outcome), here, TEST_DRIVES, SortOrder.NEAREST, false, locationAllowed, false)
        compose.setContent {
            WaitlightTheme(dark = false) {
                StationListContent(
                    ui = ui,
                    onRefresh = { refreshes++ },
                    onSort = {},
                    onToggleRims = {},
                    onOpenStation = { opened = it },
                    onChangeLocation = { turnOnLocation++ },
                )
            }
        }
        return ui
    }

    // ---- What's on screen. ----

    private fun node(matcher: SemanticsMatcher) = compose.onNode(matcher)

    /** The first node in the list that matches, scrolled to first: the list composes only the rows in view. */
    private fun listed(matcher: SemanticsMatcher) =
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(matcher).let { compose.onAllNodes(matcher).onFirst() }

    private fun shown(text: String) = listed(hasTextSpaced(text)).assertExists()
    private fun notShown(text: String) = node(hasTextSpaced(text)).assertDoesNotExist()
    private fun badge(description: String) = listed(hasDescription(description)).assertExists()

    private fun refreshButton(id: Int) = node(hasDescription(text(id)))

    private val heroOpen get() = hasDescription(text(R.string.list_hero_open, "Arvada"))

    private fun bannersNotShown() {
        notShown(text(R.string.list_stale_title))
        notShown(text(R.string.list_offline_title))
        notShown(text(R.string.list_broken_title))
        notShown(text(R.string.list_try_again))
    }

    // ---- The states. ----

    @Test
    fun `live - the hero, live badges, and refresh off until AirCare's next update`() {
        val ui = show(now = denver("2026-11-02T14:20"))
        assertEquals(ListState.LIVE, ui.state)

        refreshButton(R.string.list_refresh_current).assertIsNotEnabled()
        shown("Next update in ~9 min")
        shown("Updated 6 min ago")
        bannersNotShown()

        node(heroOpen).assertExists()
        node(hasDescription("About 18 minutes in all")).assertExists()
        node(hasTextSpaced("≈ 5 min drive + 13 min wait")).assertExists()
        node(heroOpen).performClick()
        assertEquals(ARVADA, opened)

        badge("Short wait, 13 minutes")
        badge("Moderate wait, 45 minutes")
        badge("Long wait, over 60 minutes")
    }

    @Test
    fun `due - refresh on and filled, and the hero still shown`() {
        val ui = show(now = denver("2026-11-02T14:30"))
        assertEquals(ListState.DUE, ui.state)

        refreshButton(R.string.list_refresh).assertIsEnabled().performClick()
        assertEquals(1, refreshes)
        shown(text(R.string.list_new_data))
        bannersNotShown()
        node(heroOpen).assertExists()
        badge("Short wait, 13 minutes")
    }

    @Test
    fun `closed - the closed card, no hero, refresh off, every badge closed`() {
        val ui = show(now = denver("2026-11-02T18:00"))
        assertEquals(ListState.CLOSED, ui.state)

        refreshButton(R.string.list_refresh_closed).assertIsNotEnabled()
        shown(text(R.string.list_closed_title))
        shown(text(R.string.list_waits_return))
        shown("Closed since 5:30 PM")
        shown("All stations open at 8 AM tomorrow. Mid-to-late afternoon is usually quietest.")
        bannersNotShown()
        node(heroOpen).assertDoesNotExist()

        badge(text(R.string.badge_desc_closed))
        node(hasDescription("Short wait, 13 minutes")).assertDoesNotExist()
    }

    @Test
    fun `stale - the couldn't-refresh banner with try again, no hero, last known badges`() {
        val ui = show(now = denver("2026-11-02T14:50"), outcome = FetchOutcome.FAILED)
        assertEquals(ListState.STALE, ui.state)

        shown(text(R.string.list_stale_title))
        shown("AirCare’s site didn’t respond. Waits below are from 2:14 PM and may be off.")
        shown("36 min old")
        refreshButton(R.string.list_refresh).assertIsEnabled()
        node(heroOpen).assertDoesNotExist()

        badge("Last known short wait, 13 minutes as of 2:14 PM, may be out of date")
        badge("Last known moderate wait, 45 minutes as of 2:14 PM, may be out of date")
        badge("Last known long wait, over 60 minutes as of 2:14 PM, may be out of date")

        listed(hasTextSpaced(text(R.string.list_try_again))).performClick()
        assertEquals(1, refreshes)
    }

    @Test
    fun `offline - the offline banner with try again, no hero, last known badges`() {
        val ui = show(now = denver("2026-11-02T14:50"), outcome = FetchOutcome.OFFLINE)
        assertEquals(ListState.OFFLINE, ui.state)

        shown(text(R.string.list_offline_title))
        shown("Showing waits from 2:14 PM. They’ll refresh when you’re back online.")
        shown(text(R.string.list_try_again))
        notShown(text(R.string.list_stale_title))
        refreshButton(R.string.list_refresh).assertIsEnabled()
        node(heroOpen).assertDoesNotExist()
        badge("Last known short wait, 13 minutes as of 2:14 PM, may be out of date")
    }

    @Test
    fun `broken - the unavailable banner without try again, refresh still on, every badge greyed`() {
        // The waits are only six minutes old, but the page they came from can no longer be read.
        val ui = show(now = denver("2026-11-02T14:20"), outcome = FetchOutcome.UNREADABLE)
        assertEquals(ListState.BROKEN, ui.state)

        shown(text(R.string.list_broken_title))
        shown(text(R.string.list_waits_unavailable))
        notShown(text(R.string.list_try_again))
        refreshButton(R.string.list_refresh).assertIsEnabled()
        node(heroOpen).assertDoesNotExist()

        badge("Last known short wait, 13 minutes as of 2:14 PM, may be out of date")
        node(hasDescription("Short wait, 13 minutes")).assertDoesNotExist()
    }

    @Test
    fun `no data yet - getting waits, refresh on, no hero, no banner, every badge without a reading`() {
        val ui = show(now = denver("2026-11-02T14:20"), waits = emptyMap())
        assertEquals(ListState.DUE, ui.state)

        shown(text(R.string.list_getting_waits))
        refreshButton(R.string.list_refresh).assertIsEnabled()
        bannersNotShown()
        notShown(text(R.string.list_new_data))
        node(heroOpen).assertDoesNotExist()
        badge(text(R.string.badge_desc_no_reading))
    }

    @Test
    fun `no location - location off, alphabetical, no sort chips, no hero`() {
        val ui = show(now = denver("2026-11-02T14:20"), here = null)
        assertEquals(ListState.LIVE, ui.state)
        assertEquals(ui.rows.map { it.station.name }.sorted(), ui.rows.map { it.station.name })

        shown(text(R.string.list_location_off))
        notShown(text(R.string.list_near_you))
        notShown(text(R.string.list_sort_nearest))
        notShown(text(R.string.list_sort_fastest))
        shown(text(R.string.list_filter_rims))
        node(heroOpen).assertDoesNotExist()
        badge("Short wait, 13 minutes")

        compose.onNodeWithText(text(R.string.list_location_off)).performClick()
        assertEquals(1, turnOnLocation)
    }

    @Test
    fun `a fix names the nearest ZIP's place, and isn't a link`() {
        show(now = denver("2026-11-02T14:20"), here = Here.Device(AT_ARVADA, ZIP_80003))
        shown(text(R.string.list_place, "Arvada", "80003"))
        assertEquals("Arvada, CO 80003", text(R.string.list_place, "Arvada", "80003"))
        notShown(text(R.string.list_near_you))
        compose.onNodeWithText(text(R.string.list_place, "Arvada", "80003")).assertHasNoClickAction()
    }

    @Test
    fun `a fix near no Colorado ZIP is near you`() {
        show(now = denver("2026-11-02T14:20"), here = Here.Device(AT_ARVADA, null))
        shown(text(R.string.list_near_you))
    }

    @Test
    fun `a typed ZIP - its place, nearest first, and a tap opens the prompt to change it`() {
        val ui = show(now = denver("2026-11-02T14:20"), here = Here.Zip(ZIP_80003), locationAllowed = false)
        assertEquals("Arvada", ui.rows.first().station.name)
        assertEquals(ARVADA, ui.hero?.station?.id)
        shown(text(R.string.list_sort_nearest))
        notShown(text(R.string.list_location_off))

        val place = text(R.string.list_place, "Arvada", "80003")
        compose.onNodeWithText(place, useUnmergedTree = true).assertExists()
        compose.onNode(hasClickLabel(text(R.string.list_change_zip))).performClick()
        assertEquals(1, turnOnLocation)
    }

    @Test
    fun `location allowed but not found yet, with a ZIP - the ZIP stands in`() {
        show(now = denver("2026-11-02T14:20"), here = Here.Zip(ZIP_80003), locationAllowed = true)
        shown(text(R.string.list_place, "Arvada", "80003"))
        notShown(text(R.string.list_finding_location))
    }

    @Test
    fun `location allowed but not found yet - finding it, no hero`() {
        show(now = denver("2026-11-02T14:20"), here = null, locationAllowed = true)
        shown(text(R.string.list_finding_location))
        notShown(text(R.string.list_location_off))
        node(heroOpen).assertDoesNotExist()
    }

    @Test
    fun `one station behind the rest goes grey on its own`() {
        val waits = threeWaits(updated) + (AURORA to (WaitReading.Minutes(20) to denver("2026-11-02T13:14")))
        val ui = show(now = denver("2026-11-02T14:20"), waits = waits)
        assertEquals(ListState.LIVE, ui.state)
        badge("Short wait, 13 minutes")
        badge("Last known short wait, 20 minutes as of 1:14 PM, may be out of date")
        assertEquals(ARVADA, ui.hero?.station?.id)
    }
}

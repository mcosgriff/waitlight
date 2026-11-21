package io.cosgriff.waitlight.ui.vans

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.cosgriff.waitlight.R
import io.cosgriff.waitlight.domain.DriveBy
import io.cosgriff.waitlight.domain.FetchOutcome
import io.cosgriff.waitlight.domain.driveByLog
import io.cosgriff.waitlight.domain.GeoPoint
import io.cosgriff.waitlight.domain.Here
import io.cosgriff.waitlight.domain.VanDriveTable
import io.cosgriff.waitlight.domain.VanLiveStatus
import io.cosgriff.waitlight.domain.VanSchedule
import io.cosgriff.waitlight.domain.VanSite
import io.cosgriff.waitlight.domain.VanTab
import io.cosgriff.waitlight.domain.VanTabDays
import io.cosgriff.waitlight.domain.VanTabKind
import io.cosgriff.waitlight.domain.VanTimes
import io.cosgriff.waitlight.domain.VanWeek
import io.cosgriff.waitlight.domain.ZipTable
import io.cosgriff.waitlight.domain.vanSiteWeek
import io.cosgriff.waitlight.ui.TALL_PHONE
import io.cosgriff.waitlight.ui.ZIP_80003
import io.cosgriff.waitlight.ui.denver
import io.cosgriff.waitlight.ui.hasDescription
import io.cosgriff.waitlight.ui.hasTextSpaced
import io.cosgriff.waitlight.ui.text
import io.cosgriff.waitlight.ui.theme.WaitlightTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.TUESDAY
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Every site [minutes] away: one cell, 10 degrees on a side, round Arvada. */
internal fun uniformVanDrives(keys: Map<String, Int>): VanDriveTable {
    val bytes = ByteArrayOutputStream()
    DataOutputStream(bytes).apply {
        write("WLVD".toByteArray())
        writeByte(1)
        writeInt(39_825_400)
        writeInt(-105_064_400)
        writeInt(10_000_000)
        writeInt(10_000_000)
        writeInt(1)
        writeInt(1)
        writeInt(0)
        writeInt(1)
        writeInt(keys.size)
        keys.forEach { (key, minutes) ->
            writeShort(key.toByteArray().size)
            write(key.toByteArray())
            writeByte(minutes)
        }
    }
    return VanDriveTable.read(ByteArrayInputStream(bytes.toByteArray()), keys.keys)
}

private fun site(text: String, status: VanLiveStatus?, days: Set<java.time.DayOfWeek> = setOf(FRIDAY), closes: LocalTime = LocalTime.of(17, 0), lat: Double = 39.78313) =
    VanSite(text, GeoPoint(lat, -105.0598), days, LocalTime.of(7, 0), closes, "Arvada", status)

internal val HARLAN = site("ON-RAMP TO EB I-70 FROM HARLAN ST.", VanLiveStatus.TESTING)
internal val PECOS = site("ON-RAMP TO WB I-70 from PECOS ST.", VanLiveStatus.UNKNOWN, lat = 39.78355)
internal val TWO_UNITS = site("ON-RAMP to WB I-70 from NB I-25 (two units at site)", VanLiveStatus.NOT_TESTING, lat = 39.78143)
internal val SHERIDAN = site("ON-RAMP to SB US-285 (HAMPDEN ave) from SHERIDAN blvd", VanLiveStatus.NOT_ON_SITE_UNSCHEDULED, closes = LocalTime.of(12, 30), lat = 39.65369)

/** Fri 2026-11-20 as fetched at 4:52 PM; Thanksgiving week next, its Friday tab broken. */
internal val SCHEDULE = VanSchedule(
    listOf(
        VanTab(VanTabKind(VanWeek.CURRENT, VanTabDays.MON_THU), emptyList()),
        VanTab(VanTabKind(VanWeek.CURRENT, VanTabDays.FRIDAY), listOf(HARLAN, PECOS, TWO_UNITS, SHERIDAN)),
        VanTab(
            VanTabKind(VanWeek.NEXT, VanTabDays.MON_THU),
            listOf(HARLAN.copy(days = setOf(MONDAY, TUESDAY), status = VanLiveStatus.NOT_ON_SITE_UNSCHEDULED)),
        ),
        VanTab(VanTabKind(VanWeek.NEXT, VanTabDays.FRIDAY), null, "An unexpected error occurred."),
    ),
    fetchedAt = denver("2026-11-20T16:52"),
)

internal val DRIVES = uniformVanDrives(mapOf(HARLAN.key to 9, PECOS.key to 12, TWO_UNITS.key to 14, SHERIDAN.key to 25))

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = TALL_PHONE, sdk = [35])
class VansScreenTest {
    @get:Rule val compose = createComposeRule()

    private val picked = mutableListOf<LocalDate>()
    private val opened = mutableListOf<Pair<String, LocalDate>>()

    private fun show(
        now: Instant = denver("2026-11-20T16:52"),
        outcome: FetchOutcome = FetchOutcome.OK,
        selected: LocalDate? = null,
        readingOnly: Boolean = false,
        here: Here? = Here.Zip(ZIP_80003),
    ) {
        val ui = vansUi(now, VanTimes(SCHEDULE, outcome), here, DRIVES, ZipTable(listOf(ZIP_80003)), selected, readingOnly, false, false)
        compose.setContent {
            WaitlightTheme(dark = false) {
                VansContent(ui, onPick = { picked += it }, onToggleReading = {}, onRefresh = {}, onOpenSite = { k, d -> opened += k to d }, onChangeLocation = {}, onOpenVanPage = {})
            }
        }
    }

    @Test
    fun `no far-away note when a van is near`() {
        show()
        compose.onNodeWithText(text(R.string.vans_far_body)).assertDoesNotExist()
    }

    @Test
    fun `today has the hero, each status in words, nearest first by drive`() {
        show()
        compose.onNodeWithText(text(R.string.vans_hero_label).uppercase()).assertExists()
        // In the hero and in its row.
        compose.onAllNodesWithText("Harlan St → EB I-70").assertCountEquals(2)
        compose.onNode(hasDescription("Reading cars now, Harlan St to EB I-70")).assertExists()
        compose.onNode(hasDescription("On site, status unknown, Pecos St to WB I-70")).assertExists()
        compose.onNode(hasDescription("Set up, not reading, NB I-25 to WB I-70")).assertExists()
        compose.onNode(hasDescription("Done for today, Sheridan Blvd to SB US-285 (Hampden Ave)")).assertExists()
        compose.onNodeWithText(text(R.string.van_two_units)).assertExists()
        compose.onNodeWithText("4 sites today · 1 reading now").assertExists()
        compose.onNode(hasTextSpaced("Status checked Nov 20, 4:52 PM")).assertExists()
        compose.onNodeWithText("9 min").assertExists()
    }

    @Test
    fun `a far-off nearest van says a station may be quicker`() {
        val far = uniformVanDrives(mapOf(HARLAN.key to 34, PECOS.key to 40))
        val ui = vansUi(denver("2026-11-20T16:52"), VanTimes(SCHEDULE), Here.Zip(ZIP_80003), far, ZipTable(listOf(ZIP_80003)), null, false, false, false)
        compose.setContent { WaitlightTheme(dark = false) { VansContent(ui, {}, {}, {}, { _, _ -> }, {}, {}) } }
        compose.onNodeWithText("The nearest van is 34 min away").assertExists()
    }

    @Test
    fun `a row opens its site on its day`() {
        show()
        compose.onNodeWithText("Pecos St → WB I-70").performClick()
        assertEquals(listOf(PECOS.key to LocalDate.of(2026, 11, 20)), opened)
    }

    @Test
    fun `day chips pick a day, at least 48dp tall`() {
        show()
        compose.onNode(hasDescription("Monday, Nov 23")).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf(LocalDate.of(2026, 11, 23)), picked)
    }

    @Test
    fun `another day is scheduled, without the hero or the filter`() {
        show(selected = LocalDate.of(2026, 11, 23))
        compose.onNode(hasDescription("Scheduled Monday, Harlan St to EB I-70")).assertExists()
        compose.onNodeWithText(text(R.string.vans_hero_label).uppercase()).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.vans_filter_reading)).assertDoesNotExist()
    }

    @Test
    fun `a day with no sites, and one not posted`() {
        show(selected = LocalDate.of(2026, 11, 26))
        compose.onNodeWithText("No vans on Thursday, Nov 26").assertExists()
        compose.onNodeWithText(text(R.string.vans_none_body_holiday)).assertExists()
    }

    @Test
    fun `a day not posted yet`() {
        show(selected = LocalDate.of(2026, 11, 27))
        compose.onNodeWithText("Friday’s sites aren’t posted yet").assertExists()
    }

    @Test
    fun `the weekend shows next week, with its banner`() {
        show(now = denver("2026-11-21T10:05"))
        compose.onNodeWithText(text(R.string.vans_weekend_title)).assertExists()
        compose.onNode(hasDescription("Scheduled Monday, Harlan St to EB I-70")).assertExists()
        compose.onNodeWithText(text(R.string.vans_chip_today)).assertDoesNotExist()
    }

    @Test
    fun `an unreadable page shows the schedule, every site scheduled today`() {
        show(now = denver("2026-11-20T12:00"), outcome = FetchOutcome.UNREADABLE)
        compose.onNodeWithText(text(R.string.vans_broken_title)).assertExists()
        compose.onNode(hasDescription("Scheduled today, Harlan St to EB I-70")).assertExists()
        compose.onNodeWithText("4 sites today · live status unavailable").assertExists()
        compose.onNodeWithText(text(R.string.vans_hero_label).uppercase()).assertDoesNotExist()
    }

    @Test
    fun `the reading-now filter keeps the reading sites`() {
        show(readingOnly = true)
        compose.onNodeWithText("Pecos St → WB I-70").assertDoesNotExist()
        compose.onNodeWithText("1 of 4 sites reading cars now").assertExists()
    }

    @Test
    fun `the drive-by line counts the last 14 months and opens the log`() {
        var opens = 0
        val now = denver("2026-11-20T16:52")
        val log = driveByLog(
            listOf(
                DriveBy(1, HARLAN.key, HARLAN.description, HARLAN.point, denver("2026-11-20T07:15")),
                DriveBy(2, HARLAN.key, HARLAN.description, HARLAN.point, denver("2025-01-10T07:15")),
            ),
            now,
        )
        val ui = vansUi(now, VanTimes(SCHEDULE), Here.Zip(ZIP_80003), DRIVES, ZipTable(listOf(ZIP_80003)), null, false, false, false).copy(driveBys = log)
        compose.setContent {
            WaitlightTheme(dark = false) {
                VansContent(ui, {}, {}, {}, { _, _ -> }, {}, {}, onOpenDriveBys = { opens++ })
            }
        }
        compose.onNodeWithText("1 logged in the last 14 months").performClick()
        assertEquals(1, opens)
    }

    @Test
    fun `the info button opens how it works`() {
        show()
        compose.onNodeWithContentDescription(text(R.string.vans_info)).performClick()
        compose.onNodeWithText(text(R.string.vans_how_title)).assertExists()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = TALL_PHONE, sdk = [35])
class DriveByLogScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `newest first, older ones marked, each deletable`() {
        val deleted = mutableListOf<Long>()
        val now = denver("2026-11-20T16:52")
        val log = driveByLog(
            listOf(
                DriveBy(1, HARLAN.key, HARLAN.description, HARLAN.point, denver("2025-01-10T07:15")),
                DriveBy(2, PECOS.key, PECOS.description, PECOS.point, denver("2026-11-20T07:15")),
            ),
            now,
        )
        compose.setContent { WaitlightTheme(dark = false) { DriveByLogContent(DriveByLogUi(now, log), onBack = {}, onDelete = { deleted += it }) } }
        compose.onNodeWithText("1 logged in the last 14 months").assertExists()
        compose.onNodeWithText(text(R.string.driveby_older)).assertExists()
        compose.onNode(hasDescription("Delete drive-by Pecos St to WB I-70, Fri, Nov 20, 7:15 AM")).performClick()
        assertEquals(listOf(2L), deleted)
    }

    @Test
    fun `an empty log says how to start`() {
        compose.setContent { WaitlightTheme(dark = false) { DriveByLogContent(DriveByLogUi(denver("2026-11-20T16:52"), driveByLog(emptyList(), denver("2026-11-20T16:52"))), {}, {}) } }
        compose.onNodeWithText(text(R.string.driveby_list_empty)).assertExists()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = TALL_PHONE, sdk = [35])
class VanDetailScreenTest {
    @get:Rule val compose = createComposeRule()

    private var directions = 0

    private fun show(key: String, date: LocalDate, now: Instant = denver("2026-11-20T16:52")) {
        val list = vansUi(now, VanTimes(SCHEDULE), Here.Zip(ZIP_80003), DRIVES, ZipTable(listOf(ZIP_80003)), date, false, false, false)
        val day = list.list.days.first { it.date == date }
        val row = (day.content as io.cosgriff.waitlight.domain.VanDayContent.Sites).rows.first { it.site.key == key }
        val ui = VanDetailUi(now, row, vanSiteWeek(SCHEDULE, key, now), list.state, SCHEDULE.fetchedAt, day.isToday)
        compose.setContent {
            WaitlightTheme(dark = false) { VanDetailContent(ui, onBack = {}, onDirections = { directions++ }) }
        }
    }

    @Test
    fun `status, drive, the week, and directions`() {
        show(HARLAN.key, LocalDate.of(2026, 11, 20))
        compose.onNodeWithText("Harlan St → EB I-70").assertExists()
        compose.onNode(hasDescription("Reading cars now")).assertExists()
        compose.onNode(hasTextSpaced("Arvada · 9 min drive · until 5 PM")).assertExists()
        compose.onNode(hasTextSpaced("Today, Fri, Nov 20")).assertExists()
        compose.onNode(hasTextSpaced("Status checked Nov 20, 4:52 PM")).assertExists()
        compose.onNode(hasTextSpaced("Mon, Nov 23")).assertExists()
        compose.onNode(hasDescription("Diagram: the unit sits on the on-ramp from Harlan St onto EB I-70")).assertExists()
        compose.onNodeWithText(text(R.string.van_detail_directions)).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, directions)
    }

    @Test
    fun `a site not on next week's tabs says so`() {
        show(PECOS.key, LocalDate.of(2026, 11, 20))
        compose.onNodeWithText(text(R.string.van_detail_not_scheduled)).assertExists()
    }
}

package io.cosgriff.waitlight.data.vans

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.cosgriff.waitlight.data.aircare.denver
import io.cosgriff.waitlight.data.location.HereSource
import io.cosgriff.waitlight.data.location.LastLocationStore
import io.cosgriff.waitlight.data.location.PhoneFix
import io.cosgriff.waitlight.data.location.ZipCodes
import io.cosgriff.waitlight.data.settings.SettingsStore
import io.cosgriff.waitlight.domain.AirCareHours
import io.cosgriff.waitlight.domain.FetchReason
import io.cosgriff.waitlight.domain.GeoPoint
import io.cosgriff.waitlight.domain.VanDayContent
import io.cosgriff.waitlight.domain.VanRow
import io.cosgriff.waitlight.domain.VanStatus
import io.cosgriff.waitlight.domain.readingCount
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The shared van sites at now, as the list, the detail and the bottom bar read them: from the saved van page of
 * Friday 2026-11-20, "here" a typed ZIP code, and a drive table with a time for every site on the page.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VanSitesNowTest {
    private class SetClock(var now: Instant) : Clock() {
        override fun instant() = now
        override fun getZone(): ZoneOffset = ZoneOffset.UTC
        override fun withZone(zone: ZoneId) = this
    }

    private class NoPhone : PhoneFix {
        override val permitted: StateFlow<Boolean> = MutableStateFlow(false)
        override fun recheck() = Unit
        override suspend fun find(): GeoPoint? = null
    }

    @get:Rule val folder = TemporaryFolder()
    private val settingsScope = CoroutineScope(Dispatchers.Unconfined)

    @After fun tearDown() = settingsScope.cancel()

    /** Before the reading sites close at 5 PM. */
    private val clock = SetClock(denver("2026-11-20T16:52"))

    private val vans by lazy {
        VanRepository(
            VanClient(
                HttpClient(
                    MockEngine { request ->
                        val day = request.url.parameters["day"]?.toInt()
                        if (day == null) respond(savedVanPage(), HttpStatusCode.InternalServerError) else respond(savedVanTable(day))
                    },
                ) { expectSuccess = true },
            ),
            clock,
            VanStore({ File(folder.root, VanStore.FILE_NAME) }, Dispatchers.Unconfined),
            CoroutineScope(Dispatchers.Unconfined),
        )
    }

    private suspend fun TestScope.sitesNow(): VanSitesNow {
        vans.refresh(FetchReason.LAUNCH)
        val settings = SettingsStore(PreferenceDataStoreFactory.create(scope = settingsScope) { File(folder.root, "settings.preferences_pb") })
        settings.setZip("80003")
        val zips = ZipCodes({ ZIPS }, Dispatchers.Unconfined)
        val here = HereSource(
            NoPhone(),
            LastLocationStore({ File(folder.root, LastLocationStore.FILE_NAME) }, Dispatchers.Unconfined),
            settings,
            zips,
            CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
        )
        here.here.first { it != null }
        val keys = vans.vans.value.schedule!!.tabs.flatMap { it.sites.orEmpty() }.map { it.key }.distinct()
        val drives = VanDriveTimes({ ByteArrayInputStream(driveTable(keys.withIndex().associate { (i, key) -> key to 5 + i })) }, Dispatchers.Unconfined)
        val driveBys = DriveByRepository({ File(folder.root, DriveByRepository.FILE_NAME) }, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined)
        return VanSitesNow(vans, here, zips, drives, driveBys, clock)
    }

    private fun VanSitesAt.rowsOn(date: LocalDate?): List<VanRow> = (day(date)?.content as? VanDayContent.Sites)?.rows.orEmpty()

    @Test
    fun `a row goes from reading to done on the tick at closing time`() = runTest {
        val sitesNow = sitesNow()
        val seen = mutableListOf<VanSitesAt>()
        val list = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { sitesNow.list(MutableStateFlow(null)).collect { seen += it } }
        runCurrent()
        val reading = seen.last().rowsOn(TODAY).first { it.status == VanStatus.Reading }

        clock.now = TODAY.atTime(reading.site.closes).atZone(AirCareHours.zone).toInstant()
        advanceTimeBy(VanSitesNow.TICK_MILLIS)
        runCurrent()
        assertEquals(VanStatus.Done, seen.last().rowsOn(TODAY).first { it.site.key == reading.site.key }.status)
        list.cancel()
    }

    @Test
    fun `the detail's row matches the list's row`() = runTest {
        val sitesNow = sitesNow()
        val list = sitesNow.list(MutableStateFlow(null)).first { it.rowsOn(TODAY).all { row -> row.drive != null } }
        val row = list.rowsOn(TODAY)[3]
        val site = sitesNow.site(row.site.key, TODAY).first { it.detail?.row?.drive != null }.detail!!
        assertEquals(row, site.row)
        assertTrue(site.isToday)
        assertTrue(site.week.isNotEmpty())
        assertEquals(list.list.fetchedAt, site.checkedAt)
    }

    @Test
    fun `the reading count matches the list's`() = runTest {
        val sitesNow = sitesNow()
        val list = sitesNow.list(MutableStateFlow(null)).first()
        val count = sitesNow.readingNow().first()
        assertEquals(3, count)
        assertEquals(readingCount(list.day(TODAY)), count)
    }

    @Test
    fun `drive minutes appear for the selected day's sites`() = runTest {
        val sitesNow = sitesNow()
        val day = MutableStateFlow<LocalDate?>(null)
        val seen = mutableListOf<VanSitesAt>()
        val list = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { sitesNow.list(day).collect { seen += it } }
        runCurrent()
        assertTrue(seen.last().rowsOn(TODAY).all { it.drive != null })

        day.value = MONDAY
        runCurrent()
        val monday = seen.last().rowsOn(MONDAY)
        assertTrue(monday.isNotEmpty())
        assertTrue(monday.all { it.drive != null })
        // Nearest first.
        assertEquals(monday.map { it.drive?.minutes }, monday.map { it.drive?.minutes }.sortedBy { it })
        list.cancel()
    }

    private companion object {
        const val ZIPS = "80003|39.8254|-105.0644|Arvada"
        val TODAY: LocalDate = LocalDate.of(2026, 11, 20)
        val MONDAY: LocalDate = LocalDate.of(2026, 11, 23)

        /** A van drive table, in the layout `scripts/van_drive_times.py` writes, of one cell over all Colorado. */
        fun driveTable(minutes: Map<String, Int>): ByteArray {
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
                writeInt(minutes.size)
                minutes.forEach { (key, value) ->
                    writeShort(key.toByteArray().size)
                    write(key.toByteArray())
                    writeByte(value)
                }
            }
            return bytes.toByteArray()
        }
    }
}

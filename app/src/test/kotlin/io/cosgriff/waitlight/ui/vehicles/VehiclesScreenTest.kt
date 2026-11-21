package io.cosgriff.waitlight.ui.vehicles

import android.app.Application
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.cosgriff.waitlight.R
import io.cosgriff.waitlight.domain.DriveBy
import io.cosgriff.waitlight.domain.ReminderKind
import io.cosgriff.waitlight.domain.Vehicle
import io.cosgriff.waitlight.domain.vehicleWindows
import io.cosgriff.waitlight.ui.TALL_PHONE
import io.cosgriff.waitlight.ui.denver
import io.cosgriff.waitlight.ui.hasDescription
import io.cosgriff.waitlight.ui.hasTextSpaced
import io.cosgriff.waitlight.ui.text
import io.cosgriff.waitlight.ui.theme.WaitlightTheme
import io.cosgriff.waitlight.ui.vans.HARLAN
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth

/** The mockup's two vehicles, seen on Fri Nov 20, 2026: a GTI with one drive-by, a Fit with none. */
internal val NOW = denver("2026-11-20T16:52")
internal val GTI = Vehicle(1, "2011 Volkswagen GTI", YearMonth.of(2027, 7), ReminderKind.DEFAULTS - ReminderKind.TEST_DUE)
internal val FIT = Vehicle(2, "2016 Honda Fit", YearMonth.of(2027, 3), ReminderKind.DEFAULTS)
internal val GTI_DRIVE_BY = DriveBy(1, HARLAN.key, HARLAN.description, HARLAN.point, denver("2026-11-20T07:15"), vehicleId = 1)
internal val WINDOWS = vehicleWindows(listOf(GTI, FIT), listOf(GTI_DRIVE_BY), NOW)

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = TALL_PHONE, sdk = [35])
class VehiclesScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `each vehicle's window, and a warning when one is closing without two drive-bys`() {
        val opened = mutableListOf<Long>()
        compose.setContent { WaitlightTheme(dark = false) { VehiclesContent(WINDOWS, onBack = {}, onOpen = { opened += it }, onAdd = {}) } }
        compose.onNode(hasTextSpaced("Renews July 2027 · test due by Aug 31, 2027")).assertExists()
        compose.onNodeWithText("In your window · 132 days left").assertExists()
        compose.onNodeWithText("1 of 2 drive-bys").assertExists()
        compose.onNode(hasDescription("Window from May 1, 2026 to Apr 1, 2027; today is about 61 percent through it")).assertExists()
        compose.onNode(hasTextSpaced("Window closes Dec 1 · 11 days left. No drive-bys logged yet. After that, plan a station test.")).assertExists()
        compose.onNodeWithText("2016 Honda Fit").performClick()
        assertEquals(listOf(2L), opened)
        compose.onNodeWithText(text(R.string.vehicles_add)).assertExists()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = TALL_PHONE, sdk = [35])
class VehicleDetailScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `the window, the drive-bys, the dates and the reminders`() {
        val switched = mutableListOf<Pair<ReminderKind, Boolean>>()
        compose.setContent {
            WaitlightTheme(dark = false) {
                VehicleDetailContent(WINDOWS[0], NOW, notificationsOff = false, onBack = {}, onEdit = {}, onFindVan = {}, onReminder = { k, on -> switched += k to on }, onOpenSettings = {})
            }
        }
        compose.onNodeWithText("In your window · 132 days left").assertExists()
        compose.onNode(hasTextSpaced("Opened May 1, 2026")).assertExists()
        compose.onNode(hasTextSpaced("Closes Apr 1, 2027")).assertExists()
        compose.onNodeWithText("1 of 2").assertExists()
        compose.onNode(hasTextSpaced("Fri, Nov 20, 7:15 AM")).assertExists()
        compose.onNodeWithText("By Aug 31, 2027").assertExists()
        compose.onNodeWithText("Station test in July").assertExists()
        compose.onNodeWithText(text(R.string.reminder_switch_opens)).assertIsOn()
        compose.onNodeWithText(text(R.string.reminder_switch_due)).assertIsOff().performClick()
        assertEquals(listOf(ReminderKind.TEST_DUE to true), switched)
        compose.onNodeWithText(text(R.string.vehicle_notifications_off)).assertDoesNotExist()
    }

    @Test
    fun `with notifications off, it says so`() {
        compose.setContent {
            WaitlightTheme(dark = false) {
                VehicleDetailContent(WINDOWS[1], NOW, notificationsOff = true, onBack = {}, onEdit = {}, onFindVan = {}, onReminder = { _, _ -> }, onOpenSettings = {})
            }
        }
        compose.onNodeWithText(text(R.string.vehicle_notifications_off)).assertExists()
        compose.onNodeWithText(text(R.string.vehicle_drivebys_none)).assertExists()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = TALL_PHONE, sdk = [35])
class EditVehicleScreenTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 20)

    @Test
    fun `the window is shown as the renewal is picked`() {
        compose.setContent {
            WaitlightTheme(dark = false) {
                EditVehicleContent(VehicleForm("2011 Volkswagen GTI", 7, 2027), today, editing = false, onChange = {}, onClose = {}, onSave = {})
            }
        }
        compose.onNodeWithText("May 1, 2026 to Apr 1, 2027").assertExists()
        compose.onNode(hasTextSpaced("Two clean readings in this window, at least 90 days before July, can skip the July 2027 test. Otherwise the test is due by Aug 31, 2027.")).assertExists()
        compose.onNodeWithText(text(R.string.vehicle_edit_remind)).assertExists()
    }

    @Test
    fun `nothing to save without a description and a month`() {
        compose.setContent {
            WaitlightTheme(dark = false) {
                EditVehicleContent(VehicleForm(year = 2027), today, editing = false, onChange = {}, onClose = {}, onSave = {})
            }
        }
        compose.onNodeWithText(text(R.string.vehicle_edit_save)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.vehicle_edit_preview_title)).assertDoesNotExist()
    }

    @Test
    fun `a renewal too soon for the vans says so`() {
        compose.setContent {
            WaitlightTheme(dark = false) {
                EditVehicleContent(VehicleForm("2016 Honda Fit", 1, 2027), today, editing = true, onChange = {}, onClose = {}, onSave = {})
            }
        }
        compose.onNode(hasTextSpaced("Too late for the vans this time: the window closed Oct 1, 2026. The test is due by Feb 28, 2027.")).assertExists()
        compose.onNodeWithText(text(R.string.vehicle_edit_delete)).assertExists()
    }
}

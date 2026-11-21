package io.cosgriff.waitlight.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Period
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * A vehicle the user keeps track of: a description in their own words ("2011 Volkswagen GTI") and the month its
 * registration renews. No VIN: typing 17 characters is a pain, and nothing would use it. Kept on the phone.
 */
data class Vehicle(
    val id: Long,
    val description: String,
    val renews: YearMonth,
    /** The reminders switched on. */
    val reminders: Set<ReminderKind> = emptySet(),
    /** Reminders for this renewal already sent, or passed over because their day had gone when the vehicle was set. */
    val sent: Set<ReminderKind> = emptySet(),
) {
    val window: RapidScreenWindow get() = RapidScreenWindow.of(renews)

    /** The name a notification's title uses: "GTI" for "2011 Volkswagen GTI" ([VehicleRules.shortName]). */
    val shortName: String get() = VehicleRules.shortName(description)
}

object VehicleRules {
    /** Clean readings needed in the window. */
    const val READINGS_NEEDED = 2

    /** The window opens this many months before the renewal month (AirCare: "within 14 months"). */
    const val WINDOW_MONTHS = 14L

    /**
     * Readings must come at least this many days before the renewal month: AirCare's van page says 90, its
     * RapidScreen page 60. The app keeps the stricter 90 (decided 2026-11-20), so nobody counts on a late reading.
     */
    const val DAYS_BEFORE_RENEWAL = 90L

    /** As the van page words it, the window ends on the first of the month this many months before renewal. */
    const val MONTHS_BEFORE_RENEWAL = 3L

    /** A window this close to closing is called out, and the "closes soon" reminder comes this many days ahead. */
    const val CLOSING_SOON_DAYS = 14L

    /** Most vehicles test every two years: the likely next renewal after one has passed. */
    const val YEARS_BETWEEN_TESTS = 2L

    /** Reminders go out at this time of day, in Denver. */
    val REMINDER_TIME: LocalTime = LocalTime.of(9, 0)

    private val year = Regex("(19|20)\\d{2}")
    private val space = Regex("\\s+")

    /**
     * "2011 Volkswagen GTI" is "GTI" and "2018 Toyota Land Cruiser" "Land Cruiser": a model year then a make are
     * dropped. A description that doesn't start with a year is kept whole.
     */
    fun shortName(description: String): String {
        val words = description.trim().split(space)
        return if (words.size >= 3 && year.matches(words[0])) words.drop(2).joinToString(" ") else description.trim()
    }
}

/**
 * The RapidScreen window for a renewal in [renews]: two clean readings from [opens] through [closes], both days
 * included, can skip that renewal's test. It opens on the first of the month 14 months before the renewal month and
 * closes on the first of the month three months before it, or earlier if that is under 90 days before it
 * ([VehicleRules.DAYS_BEFORE_RENEWAL]). July 2027: May 1, 2026 to April 1, 2027.
 */
data class RapidScreenWindow(val renews: YearMonth, val opens: LocalDate, val closes: LocalDate) {
    /** The test is due by the end of the renewal month, with a month's grace (AirCare's FAQ): July's by Aug 31. */
    val testDueBy: LocalDate get() = renews.plusMonths(1).atEndOfMonth()

    /** Days from [opens] to [closes]. */
    val length: Long get() = ChronoUnit.DAYS.between(opens, closes)

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(opens) && !date.isAfter(closes)

    /** Where [today] falls. */
    fun stateOn(today: LocalDate): WindowState = when {
        today.isBefore(opens) -> WindowState.Before(ChronoUnit.DAYS.between(today, opens))
        !today.isAfter(closes) -> {
            val fraction = if (length == 0L) 1f else ChronoUnit.DAYS.between(opens, today).toFloat() / length
            WindowState.Open(ChronoUnit.DAYS.between(today, closes), fraction)
        }
        !today.isAfter(testDueBy) -> WindowState.Closed
        else -> {
            var next = renews.plusYears(VehicleRules.YEARS_BETWEEN_TESTS)
            while (next.plusMonths(1).atEndOfMonth().isBefore(today)) next = next.plusYears(VehicleRules.YEARS_BETWEEN_TESTS)
            WindowState.Past(next)
        }
    }

    /** What's left of the window on [today]: whole months, or days when under a month. */
    fun timeLeft(today: LocalDate): TimeLeft {
        val left = Period.between(today, closes)
        val months = left.toTotalMonths().toInt()
        return if (months >= 1) TimeLeft.Months(months) else TimeLeft.Days(ChronoUnit.DAYS.between(today, closes).toInt().coerceAtLeast(0))
    }

    /** The day [kind] goes out. */
    fun reminderDate(kind: ReminderKind): LocalDate = when (kind) {
        ReminderKind.WINDOW_OPENS -> opens
        ReminderKind.HALFWAY -> opens.plusDays((length + 1) / 2)
        ReminderKind.CLOSING_SOON -> closes.minusDays(VehicleRules.CLOSING_SOON_DAYS)
        // A month before the end of the renewal month, when the test is due without the grace month.
        ReminderKind.TEST_DUE -> renews.atDay(1)
    }

    /** When [kind] goes out: its day at [VehicleRules.REMINDER_TIME] in Denver. */
    fun reminderAt(kind: ReminderKind): Instant = reminderDate(kind).atTime(VehicleRules.REMINDER_TIME).atZone(AirCareHours.zone).toInstant()

    /** From when [kind] is no use: the day after the window closes, or after the test is due. */
    fun reminderExpires(kind: ReminderKind): Instant {
        val last = if (kind == ReminderKind.TEST_DUE) testDueBy else closes
        return last.plusDays(1).atStartOfDay(AirCareHours.zone).toInstant()
    }

    companion object {
        fun of(renews: YearMonth): RapidScreenWindow {
            val month = renews.atDay(1)
            val closes = minOf(renews.minusMonths(VehicleRules.MONTHS_BEFORE_RENEWAL).atDay(1), month.minusDays(VehicleRules.DAYS_BEFORE_RENEWAL))
            return RapidScreenWindow(renews, renews.minusMonths(VehicleRules.WINDOW_MONTHS).atDay(1), closes)
        }
    }
}

/** Where a day falls against a vehicle's window. */
sealed interface WindowState {
    /** Not open yet; it opens in [daysUntil] days. */
    data class Before(val daysUntil: Long) : WindowState

    /** In the window, with [daysLeft] days after today (0 on its last day), [fraction] of the way through it. */
    data class Open(val daysLeft: Long, val fraction: Float) : WindowState {
        val closingSoon: Boolean get() = daysLeft <= VehicleRules.CLOSING_SOON_DAYS
    }

    /** Too late for readings to count for this renewal; a station test is due. */
    data object Closed : WindowState

    /** The renewal's test due date has gone; [next] is the likely next renewal. */
    data class Past(val next: YearMonth) : WindowState
}

/** What's left of a window. */
sealed interface TimeLeft {
    data class Months(val months: Int) : TimeLeft

    data class Days(val days: Int) : TimeLeft
}

/** The reminders a vehicle can have, each switched on or off on its own. */
enum class ReminderKind {
    /** The day the window opens. */
    WINDOW_OPENS,

    /** Halfway through, if fewer than two drive-bys are logged for the vehicle. */
    HALFWAY,

    /** Two weeks before the window closes. */
    CLOSING_SOON,

    /** The first of the renewal month: the test is due by the end of the next. */
    TEST_DUE,
    ;

    companion object {
        /** What "Remind me" switches on for a new vehicle: all of them ("window opening and closing, and the test date"). */
        val DEFAULTS: Set<ReminderKind> = entries.toSet()
    }
}

/** A vehicle with its window, where today falls in it, and its drive-bys in the window up to now, newest first. */
data class VehicleWindow(val vehicle: Vehicle, val today: LocalDate, val driveBys: List<DriveBy>) {
    val window: RapidScreenWindow get() = vehicle.window
    val state: WindowState get() = window.stateOn(today)
    val count: Int get() = driveBys.size
}

/**
 * Whether [this] drive-by was by [vehicle]: tagged with it, or, while [vehicle] is the only one, not tagged at all
 * (drive-bys logged before vehicles were, or with no vehicle saved).
 */
fun DriveBy.isBy(vehicle: Vehicle, onlyVehicle: Boolean): Boolean = vehicleId == vehicle.id || (vehicleId == null && onlyVehicle)

/** Each of [vehicles] at [now], with the drive-bys in [entries] that count toward its window. */
fun vehicleWindows(vehicles: List<Vehicle>, entries: List<DriveBy>, now: Instant): List<VehicleWindow> {
    val today = now.atZone(AirCareHours.zone).toLocalDate()
    val onlyOne = vehicles.size == 1
    return vehicles.sortedBy { it.id }.map { vehicle ->
        val window = vehicle.window
        val counted = entries
            .filter { it.isBy(vehicle, onlyOne) && !it.at.isAfter(now) && it.at.atZone(AirCareHours.zone).toLocalDate() in window }
            .sortedWith(compareByDescending<DriveBy> { it.at }.thenByDescending { it.id })
        VehicleWindow(vehicle, today, counted)
    }
}

/**
 * The vehicle a new drive-by is for, chosen first: the first whose window is open, else the first. Null with no
 * vehicles; with one, it is that one and the question isn't asked.
 */
fun firstChoice(windows: List<VehicleWindow>): VehicleWindow? = windows.firstOrNull { it.state is WindowState.Open } ?: windows.firstOrNull()

/** [this] with every reminder in [kinds] whose time has passed at [now] counted as sent, so none goes out late. */
fun Vehicle.settled(now: Instant, kinds: Set<ReminderKind> = ReminderKind.entries.toSet()): Vehicle =
    copy(sent = sent + kinds.filter { !window.reminderAt(it).isAfter(now) })

/** [this] renewing in [renews] instead, its reminders reset for that renewal; those already past at [now] are skipped. */
fun Vehicle.renewing(renews: YearMonth, now: Instant): Vehicle =
    if (renews == this.renews) this else copy(renews = renews, sent = emptySet()).settled(now)

/** [this] with [kind] switched [on]; switched on after its time, it doesn't go out late. */
fun Vehicle.withReminder(kind: ReminderKind, on: Boolean, now: Instant): Vehicle =
    if (on) copy(reminders = reminders + kind).settled(now, setOf(kind)) else copy(reminders = reminders - kind)

/** A reminder to post now, with the vehicle as it stands. */
data class DueReminder(val kind: ReminderKind, val vehicle: VehicleWindow)

/** What a reminder check does: the notifications to post, and each vehicle's reminders to mark sent. */
data class ReminderRun(val post: List<DueReminder>, val settled: Map<Long, Set<ReminderKind>>)

/**
 * The reminders due at [now]: switched on, not sent, their time come. Each is then marked sent. One that's no use
 * any more ([RapidScreenWindow.reminderExpires]) is dropped, and "halfway" only goes out with fewer than two
 * drive-bys logged. If several are due for one vehicle (the phone was off), only the latest is posted.
 */
fun reminderRun(vehicles: List<Vehicle>, entries: List<DriveBy>, now: Instant): ReminderRun {
    val post = mutableListOf<DueReminder>()
    val settled = mutableMapOf<Long, Set<ReminderKind>>()
    for (vehicle in vehicleWindows(vehicles, entries, now)) {
        val window = vehicle.window
        val due = (vehicle.vehicle.reminders - vehicle.vehicle.sent).filter { !window.reminderAt(it).isAfter(now) }
        if (due.isEmpty()) continue
        settled[vehicle.vehicle.id] = due.toSet()
        due.filter { now.isBefore(window.reminderExpires(it)) && (it != ReminderKind.HALFWAY || vehicle.count < VehicleRules.READINGS_NEEDED) }
            .maxByOrNull { window.reminderAt(it) }
            ?.let { post += DueReminder(it, vehicle) }
    }
    return ReminderRun(post, settled)
}

/** When the next reminder check should run: the earliest reminder switched on and not sent; null with none. */
fun nextReminderAt(vehicles: List<Vehicle>): Instant? =
    vehicles.flatMap { vehicle -> (vehicle.reminders - vehicle.sent).map { vehicle.window.reminderAt(it) } }.minOrNull()

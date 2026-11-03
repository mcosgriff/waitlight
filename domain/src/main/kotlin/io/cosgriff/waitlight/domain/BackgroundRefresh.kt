package io.cosgriff.waitlight.domain

import java.time.Instant

/** What the background refresh does next, after a check or when the app starts. */
sealed interface BackgroundPlan {
    /**
     * Check every [Freshness.REFRESH_INTERVAL]. A check fetches only when [shouldFetch] says so, so it never
     * fetches more often than AirCare updates.
     */
    data object Poll : BackgroundPlan

    /** Don't wake until the stations open at [opening]: nothing can be fetched before then. */
    data class SleepUntil(val opening: Instant) : BackgroundPlan
}

/**
 * Poll while the stations are open and a check one [Freshness.REFRESH_INTERVAL] from [now] would still find them
 * open; otherwise sleep until [AirCareHours.nextOpening], so evenings, Sundays and holidays cost no wakes.
 */
fun backgroundPlan(now: Instant): BackgroundPlan {
    val closing = AirCareHours.closingTime(now)?.toInstant()
    return if (closing != null && now.plus(Freshness.REFRESH_INTERVAL) < closing) {
        BackgroundPlan.Poll
    } else {
        BackgroundPlan.SleepUntil(AirCareHours.nextOpening(now).toInstant())
    }
}

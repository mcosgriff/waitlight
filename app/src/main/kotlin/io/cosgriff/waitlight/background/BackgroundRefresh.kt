package io.cosgriff.waitlight.background

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.cosgriff.waitlight.domain.BackgroundPlan
import io.cosgriff.waitlight.domain.Freshness
import io.cosgriff.waitlight.domain.backgroundPlan
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the waits current with the app closed: while the stations are open, [RefreshWorker] checks every
 * [Freshness.REFRESH_INTERVAL] (WorkManager's shortest period); otherwise one [OpeningWorker] waits for the next
 * opening and starts the checks again. Which of the two comes from [backgroundPlan].
 */
@Singleton
class BackgroundRefresh @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: Clock,
) {
    private val workManager get() = WorkManager.getInstance(context)

    /** Sets up the work [backgroundPlan] calls for now. Safe to call again: a running period isn't restarted. */
    fun schedule() {
        when (val plan = backgroundPlan(clock.instant())) {
            BackgroundPlan.Poll -> {
                val checks = PeriodicWorkRequestBuilder<RefreshWorker>(Freshness.REFRESH_INTERVAL)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
                // UPDATE, not REPLACE: an app start or the opening wake keeps the running period.
                workManager.enqueueUniquePeriodicWork(CHECKS, ExistingPeriodicWorkPolicy.UPDATE, checks)
            }
            is BackgroundPlan.SleepUntil -> {
                val delay = Duration.between(clock.instant(), plan.opening).coerceAtLeast(Duration.ZERO)
                val wake = OneTimeWorkRequestBuilder<OpeningWorker>().setInitialDelay(delay).build()
                // The wake first: this may be running inside the last check, which the cancel stops.
                workManager.enqueueUniqueWork(OPENING, ExistingWorkPolicy.REPLACE, wake)
                workManager.cancelUniqueWork(CHECKS)
                Log.i(TAG, "Background refresh sleeps until ${plan.opening}")
            }
        }
    }

    private companion object {
        const val TAG = "Waitlight"
        const val CHECKS = "refresh-checks"
        const val OPENING = "refresh-at-opening"
    }
}

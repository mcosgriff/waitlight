package io.cosgriff.waitlight.background

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.cosgriff.waitlight.data.aircare.WaitTimesRepository

/**
 * One background check, while the stations are open. It fetches only when [WaitTimesRepository.refresh]'s
 * `shouldFetch` says the data is due, never as a manual refresh, so it never fetches more often than AirCare
 * updates. Afterwards it asks [BackgroundRefresh] what comes next, which near closing time means sleeping until
 * the next opening.
 */
@HiltWorker
class RefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: WaitTimesRepository,
    private val backgroundRefresh: BackgroundRefresh,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // A failed fetch is kept as the waits' outcome, and the next check tries again; nothing to retry here.
        repository.refresh()
        backgroundRefresh.schedule()
        return Result.success()
    }
}

/**
 * Wakes at the stations' next opening and starts the checks again. It fetches nothing itself: the first
 * [RefreshWorker] check runs as soon as the checks are scheduled.
 */
@HiltWorker
class OpeningWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backgroundRefresh: BackgroundRefresh,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        backgroundRefresh.schedule()
        return Result.success()
    }
}

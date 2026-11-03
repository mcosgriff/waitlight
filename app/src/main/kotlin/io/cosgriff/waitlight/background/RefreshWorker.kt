package io.cosgriff.waitlight.background

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.cosgriff.waitlight.data.aircare.WaitTimesRepository
import io.cosgriff.waitlight.widget.WidgetUpdater

/**
 * One background check, while the stations are open. It fetches only when [WaitTimesRepository.refresh]'s
 * `shouldFetch` says the data is due, never as a manual refresh, so it never fetches more often than AirCare
 * updates. Afterwards it asks [BackgroundRefresh] what comes next, which near closing time means sleeping until
 * the next opening. It redraws the widget too, fetched or not, so its badges and age follow the clock.
 */
@HiltWorker
class RefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: WaitTimesRepository,
    private val backgroundRefresh: BackgroundRefresh,
    private val widget: WidgetUpdater,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // A failed fetch is kept as the waits' outcome, and the next check tries again; nothing to retry here.
        repository.refresh()
        widget.update()
        backgroundRefresh.schedule()
        return Result.success()
    }
}

/**
 * Wakes at the stations' next opening and starts the checks again. It fetches nothing itself: the first
 * [RefreshWorker] check runs as soon as the checks are scheduled. It redraws the widget, which would otherwise
 * show the stations closed until that check runs, later still if the check waits for a network.
 */
@HiltWorker
class OpeningWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backgroundRefresh: BackgroundRefresh,
    private val widget: WidgetUpdater,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        widget.update()
        backgroundRefresh.schedule()
        return Result.success()
    }
}

package io.cosgriff.waitlight.widget

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Redraws the widget when a badge goes stale or the stations close. Fetches nothing. */
@HiltWorker
class WidgetRedrawWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val widget: WidgetUpdater,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        widget.update()
        return Result.success()
    }
}

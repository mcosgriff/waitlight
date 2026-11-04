package io.cosgriff.waitlight.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.cosgriff.waitlight.data.aircare.WaitTimesRepository
import io.cosgriff.waitlight.data.location.HereSource
import io.cosgriff.waitlight.domain.WidgetContent
import io.cosgriff.waitlight.domain.widgetContent
import io.cosgriff.waitlight.domain.widgetRedrawAt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** What the widget draws, worked out at [now]. */
data class WidgetDrawing(val content: WidgetContent, val now: Instant)

/**
 * Keeps the home-screen widget current: after each background check ([update]), whenever the app's waits or
 * "here" (a new fix or ZIP code, [HereSource]) change ([start]), and once more when the first of its badges goes stale or the stations close
 * ([widgetRedrawAt]), so it greys out on time even with no network. Does nothing while no widget is placed.
 */
@Singleton
class WidgetUpdater @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: WaitTimesRepository,
    private val here: HereSource,
    private val clock: Clock,
) {
    private val state = MutableStateFlow<WidgetDrawing?>(null)

    /** The latest drawing, which the widget's composition follows. */
    val drawing: StateFlow<WidgetDrawing?> = state.asStateFlow()

    /** Redraws the widget whenever the app's waits or "here" change, for as long as [scope] lives. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(repository.waitTimes, here.here) { _, _ -> }.collectLatest { update() }
        }
    }

    /** Works out the widget now and sets its next redraw. */
    suspend fun prepare(): WidgetDrawing {
        val now = clock.instant()
        val times = repository.current()
        val drawing = WidgetDrawing(widgetContent(times, here.current()?.point, now), now)
        state.value = drawing
        scheduleRedraw(widgetRedrawAt(drawing.content, now), now)
        return drawing
    }

    /** Redraws every placed widget. */
    suspend fun update() {
        if (GlanceAppWidgetManager(context).getGlanceIds(WaitsWidget::class.java).isEmpty()) {
            cancelRedraws(context)
            return
        }
        prepare()
        WaitsWidget().updateAll(context)
    }

    private fun scheduleRedraw(at: Instant?, now: Instant) {
        if (at == null) {
            cancelRedraws(context)
            return
        }
        // No network needed: this only draws what the app already has.
        val redraw = OneTimeWorkRequestBuilder<WidgetRedrawWorker>()
            .setInitialDelay(Duration.between(now, at).coerceAtLeast(Duration.ZERO))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(REDRAW, ExistingWorkPolicy.REPLACE, redraw)
    }

    companion object {
        private const val REDRAW = "widget-redraw"

        /** The last widget was removed: nothing to redraw. */
        fun cancelRedraws(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(REDRAW)
        }
    }
}

/** How the widget, which Hilt can't build, reaches the app's singletons. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgetUpdater(): WidgetUpdater
}

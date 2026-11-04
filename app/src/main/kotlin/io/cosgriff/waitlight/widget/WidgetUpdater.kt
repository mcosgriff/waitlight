package io.cosgriff.waitlight.widget

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.cosgriff.waitlight.data.aircare.WaitTimesRepository
import io.cosgriff.waitlight.data.location.HereSource
import io.cosgriff.waitlight.domain.WidgetContent
import io.cosgriff.waitlight.domain.widgetContent
import io.cosgriff.waitlight.domain.widgetRedrawAt
import io.cosgriff.waitlight.domain.widgetStillCurrent
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

/** What the widget draws: [content] worked out at [drawnAt], shown at [now] (which the age reads). */
data class WidgetDrawing(val content: WidgetContent, val drawnAt: Instant, val now: Instant = drawnAt)

/**
 * Keeps the home-screen widget current: after each background check ([update]), whenever the app's waits or
 * "here" (a new fix or ZIP code, [HereSource]) change ([start]), and once more when the first of its badges goes
 * stale or the stations close ([widgetRedrawAt]), so it greys out on time even with no network. Does nothing while
 * no widget is placed.
 *
 * Each redraw works the widget out once, in [update]. Glance then either starts the widget, which takes that
 * drawing from [current], or, when the widget is already running, only recomposes it, without asking again: so
 * the running widget follows [drawing].
 */
@Singleton
class WidgetUpdater @Inject constructor(
    private val host: WidgetHost,
    private val repository: WaitTimesRepository,
    private val here: HereSource,
    private val clock: Clock,
) {
    private val latest = MutableStateFlow<WidgetDrawing?>(null)

    /** The latest drawing, which a running widget's composition follows. */
    val drawing: StateFlow<WidgetDrawing?> = latest.asStateFlow()

    /** Redraws the widget whenever the app's waits or "here" change, for as long as [scope] lives. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(repository.waitTimes, here.here) { _, _ -> }.collectLatest { update() }
        }
    }

    /** Works the widget out, sets its next redraw and redraws every placed widget. */
    suspend fun update() {
        if (!host.anyPlaced()) {
            host.cancelRedraws()
            return
        }
        prepare()
        host.redrawAll()
    }

    /**
     * What the widget shows now, as it starts: the latest drawing while it's still current ([widgetStillCurrent]),
     * with the age read now, or else a new one. A widget Glance starts on its own (placed, resized, after a
     * restart) may find none, or one gone out of date.
     */
    suspend fun current(): WidgetDrawing {
        val now = clock.instant()
        val last = latest.value
        if (last != null && widgetStillCurrent(last.content, last.drawnAt, now)) return last.copy(now = now)
        return prepare()
    }

    /** Works out the widget now and sets its next redraw. */
    private suspend fun prepare(): WidgetDrawing {
        val now = clock.instant()
        val drawing = WidgetDrawing(widgetContent(repository.current(), here.current()?.point, now), now)
        latest.value = drawing
        when (val at = widgetRedrawAt(drawing.content, now)) {
            null -> host.cancelRedraws()
            else -> host.redrawAfter(Duration.between(now, at).coerceAtLeast(Duration.ZERO))
        }
        return drawing
    }
}

/** How the widget, which Hilt can't build, reaches the app's singletons. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgetUpdater(): WidgetUpdater
}

package io.cosgriff.waitlight.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import javax.inject.Inject

/** The home screen the widget sits on, and the timer that redraws it: what [WidgetUpdater] can't test itself. */
interface WidgetHost {
    /** At least one widget is on the home screen. */
    suspend fun anyPlaced(): Boolean

    /** Asks Glance to draw every placed widget again. */
    suspend fun redrawAll()

    /** Redraws the widget once more after [delay], in place of any redraw set before. */
    fun redrawAfter(delay: Duration)

    /** Drops the pending redraw. */
    fun cancelRedraws()
}

/** [WidgetHost] on Glance and WorkManager. */
class GlanceWidgetHost @Inject constructor(@param:ApplicationContext private val context: Context) : WidgetHost {
    override suspend fun anyPlaced() = GlanceAppWidgetManager(context).getGlanceIds(WaitsWidget::class.java).isNotEmpty()

    override suspend fun redrawAll() = WaitsWidget().updateAll(context)

    override fun redrawAfter(delay: Duration) {
        // No network needed: this only draws what the app already has.
        val redraw = OneTimeWorkRequestBuilder<WidgetRedrawWorker>().setInitialDelay(delay).build()
        WorkManager.getInstance(context).enqueueUniqueWork(REDRAW, ExistingWorkPolicy.REPLACE, redraw)
    }

    override fun cancelRedraws() = cancelRedraws(context)

    companion object {
        private const val REDRAW = "widget-redraw"

        /** For the widget's receiver, which Hilt doesn't inject: the last widget was removed. */
        fun cancelRedraws(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(REDRAW)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface WidgetModule {
    @Binds fun widgetHost(host: GlanceWidgetHost): WidgetHost
}

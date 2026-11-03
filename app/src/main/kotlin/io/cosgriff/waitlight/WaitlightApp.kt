package io.cosgriff.waitlight

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.cosgriff.waitlight.background.BackgroundRefresh
import io.cosgriff.waitlight.data.di.ApplicationScope
import io.cosgriff.waitlight.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

@HiltAndroidApp
class WaitlightApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var backgroundRefresh: BackgroundRefresh

    @Inject lateinit var widgetUpdater: WidgetUpdater

    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    /** WorkManager starts from here, not its own initializer (removed in the manifest), so workers get Hilt. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        backgroundRefresh.schedule()
        widgetUpdater.start(scope)
    }
}

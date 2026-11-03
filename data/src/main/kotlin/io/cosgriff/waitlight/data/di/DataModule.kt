package io.cosgriff.waitlight.data.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.cosgriff.waitlight.data.aircare.WaitTimesStore
import io.cosgriff.waitlight.data.location.LastLocationStore
import io.cosgriff.waitlight.data.location.ZipCodes
import io.cosgriff.waitlight.data.settings.SettingsStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    /** The one clock: opening hours and freshness are worked out from it, so tests can set the time. */
    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemUTC()

    /** For AirCare's page, the one host the app talks to (ADR-0001). Error statuses throw. */
    @Provides
    @Singleton
    fun httpClient(): HttpClient = HttpClient(OkHttp) {
        expectSuccess = true
        install(HttpTimeout) { requestTimeoutMillis = 15_000 }
    }

    /** The one settings file: a DataStore must be the only one open on its file. */
    @Provides
    @Singleton
    fun settings(@ApplicationContext context: Context): SettingsStore =
        SettingsStore(PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") })

    /** The bundled Colorado ZIP codes (ADR-0005), read on first use. */
    @Provides
    @Singleton
    fun zipCodes(@ApplicationContext context: Context): ZipCodes =
        ZipCodes(
            read = { context.assets.open(ZipCodes.ASSET).bufferedReader().use { it.readText() } },
            readOutlines = { context.assets.open(ZipCodes.OUTLINES_ASSET).bufferedReader().use { it.readText() } },
        )

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The last waits, in the app's own files and left out of backups (`backup_rules.xml`). */
    @Provides
    @Singleton
    fun waitTimesStore(@ApplicationContext context: Context): WaitTimesStore =
        WaitTimesStore({ File(context.filesDir, WaitTimesStore.FILE_NAME) })

    /** The last location found, for the widget; in the app's own files and left out of backups. */
    @Provides
    @Singleton
    fun lastLocationStore(@ApplicationContext context: Context): LastLocationStore =
        LastLocationStore({ File(context.filesDir, LastLocationStore.FILE_NAME) })
}

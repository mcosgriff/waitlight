package io.cosgriff.waitlight.data.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.cosgriff.waitlight.data.settings.SettingsStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
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
}

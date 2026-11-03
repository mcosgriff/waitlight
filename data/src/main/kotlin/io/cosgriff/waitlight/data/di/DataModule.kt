package io.cosgriff.waitlight.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
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
}

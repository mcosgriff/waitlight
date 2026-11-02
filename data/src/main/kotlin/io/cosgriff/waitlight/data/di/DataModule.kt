package io.cosgriff.waitlight.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    /** The one clock: opening hours and freshness are worked out from it, so tests can set the time. */
    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemUTC()
}

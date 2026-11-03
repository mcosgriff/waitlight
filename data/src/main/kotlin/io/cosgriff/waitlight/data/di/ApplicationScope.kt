package io.cosgriff.waitlight.data.di

import javax.inject.Qualifier

/** For work that outlives any one screen, such as loading the stored waits. Lives as long as the app. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

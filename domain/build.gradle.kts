// Pure Kotlin — no Android. Opening hours, wait badges, sorting and the other rules live here
// so they can be unit-tested quickly without an emulator.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}

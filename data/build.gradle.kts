plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "io.cosgriff.waitlight.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // android.util.Log does nothing in unit tests instead of throwing.
    testOptions.unitTests.isReturnDefaultValues = true

    // As in :app: no warnings are kept, and version news is left to Dependabot.
    lint {
        abortOnError = true
        warningsAsErrors = true
        disable += "NewerVersionAvailable"
        disable += "AndroidGradlePluginVersion"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // api: Hilt generates the app's component in :app, so it must see the HttpClient binding's type.
    api(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.bragi.smartcasekit"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}

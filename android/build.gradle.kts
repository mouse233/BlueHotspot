buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin") {
            version { strictly("2.4.10") }
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

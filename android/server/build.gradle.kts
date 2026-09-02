plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.plugin.parcelize")
}

android {
    namespace = "io.github.mouse233.bluehotspot.server"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.mouse233.bluehotspot.server"
        minSdk = 36
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.librootkotlinx)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation("org.jetbrains.kotlin:kotlin-test:2.4.10")
    testImplementation("junit:junit:4.13.2")
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    androidResources {
        generateLocaleConfig = true
        localeFilters += listOf("en", "es")
    }
    namespace = "dev.hostyn.gallery"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.hostyn.gallery"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "1.2.1"
    }

    buildTypes {
        // Development builds install next to the released app instead of replacing it.
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Optimized like a release, but signed with the debug key and installed next to the
        // released app: for trying performance on a phone without the release keystore.
        create("dev") {
            initWith(getByName("release"))
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.telephoto.zoomable.image.coil)
    implementation(libs.media3.exoplayer)
    implementation(libs.androidx.exifinterface)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
}

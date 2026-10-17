plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val appVersion = providers.gradleProperty("appVersion").getOrElse("0.0.0")
val versionParts = appVersion.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 } + listOf(0, 0, 0)
val geckoAbi = providers.gradleProperty("geckoAbi").getOrElse("arm64-v8a")
val signingStore = providers.environmentVariable("SIGNING_KEYSTORE").orNull

android {
    namespace = "io.github.kriakiku.holtdroid.webview"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "io.github.kriakiku.holtdroid.webview"
        minSdk = 26
        targetSdk = 37
        versionCode = maxOf(1, versionParts[0] * 1_000_000 + versionParts[1] * 1_000 + versionParts[2])
        versionName = appVersion
    }

    flavorDimensions += "engine"
    productFlavors {
        create("webview") {
            dimension = "engine"
        }
        create("gecko") {
            dimension = "engine"
            versionNameSuffix = "-gecko"
            ndk { abiFilters += geckoAbi.split(',') }
        }
    }

    signingConfigs {
        if (signingStore != null) {
            create("release") {
                storeFile = file(signingStore)
                storePassword = providers.environmentVariable("SIGNING_STORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs { useLegacyPackaging = true }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
        disable += setOf(
            "GradleDependency",
            "NewerVersionAvailable",
            "AndroidGradlePluginVersion",
            "OldTargetApi",
            "AcceptsUserCertificates",
            "InsecureBaseConfiguration",
            "DataExtractionRules",
            "ChromeOsAbiSupport",
            "UnusedAttribute",
        )
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material.icons)
    implementation(libs.activity.compose)
    implementation(libs.appcompat)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.webkit)
    implementation(libs.browser)
    implementation(libs.serialization.json)
    "geckoImplementation"(libs.geckoview)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
}

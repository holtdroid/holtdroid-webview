import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val erudaVersion = "3.4.3" // renovate: datasource=npm depName=eruda

@CacheableTask
abstract class DownloadEruda : DefaultTask() {
    @get:Input
    abstract val version: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val archives: ArchiveOperations

    @get:Inject
    abstract val files: FileSystemOperations

    @TaskAction
    fun download() {
        val tarball = temporaryDir.resolve("eruda.tgz")
        URI("https://registry.npmjs.org/eruda/-/eruda-${version.get()}.tgz").toURL().openStream().use { input ->
            tarball.outputStream().use { input.copyTo(it) }
        }
        files.sync {
            from(archives.tarTree(archives.gzip(tarball))) {
                include("package/eruda.js")
                eachFile { path = name }
                includeEmptyDirs = false
            }
            into(outputDir)
        }
    }
}

val downloadEruda = tasks.register<DownloadEruda>("downloadEruda") {
    version = erudaVersion
    outputDir = layout.buildDirectory.dir("generated/eruda")
}

val appVersion = providers.gradleProperty("appVersion").getOrElse("0.0.0")
val versionParts = appVersion.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 } + listOf(0, 0, 0)
val geckoAbi = providers.gradleProperty("geckoAbi").getOrElse("arm64-v8a,armeabi-v7a,x86_64")
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
            applicationIdSuffix = ".gecko"
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

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(downloadEruda, DownloadEruda::outputDir)
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

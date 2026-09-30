plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.elsewhere"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.elsewhere"
        minSdk = 26
        targetSdk = 37
        versionCode = 13
        versionName = "1.1.0"
    }

    signingConfigs {
        // A private key when ELSEWHERE_KEYSTORE* env vars are set (CI secrets); otherwise the shared test key
        // in signing/, so local and GitHub builds carry the same signature and install over each other.
        val keystore = System.getenv("ELSEWHERE_KEYSTORE")?.takeIf { it.isNotBlank() }
        create("shared") {
            if (keystore != null) {
                storeFile = file(keystore)
                storePassword = System.getenv("ELSEWHERE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ELSEWHERE_KEY_ALIAS")
                keyPassword = System.getenv("ELSEWHERE_KEY_PASSWORD")
            } else {
                storeFile = rootProject.file("signing/elsewhere-test.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                test.systemProperty("robolectric.graphicsMode", "NATIVE")
                test.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                test.systemProperty("elsewhere.verifyOut", rootProject.file("verify/app").absolutePath)
                test.systemProperty("elsewhere.liveNetwork", project.findProperty("elsewhere.liveNetwork")?.toString() ?: "false")
                test.maxHeapSize = "3g"
            }
        }
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "DebugProbesKt.bin", "kotlin-tooling-metadata.json")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

composeCompiler {
    if (project.findProperty("composeReports") == "true") {
        reportsDestination.set(layout.buildDirectory.dir("compose_reports"))
        metricsDestination.set(layout.buildDirectory.dir("compose_metrics"))
    }
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("app/compose-stability.conf"))
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.coil.core)
    implementation(libs.coil.network.okhttp)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.tooling.preview)

    // Test-only: JVM screenshot rendering for visual verification (no emulator in CI). Not shipped.
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

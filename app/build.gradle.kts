import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Version lives in gradle.properties (cairn.versionName / cairn.versionCode): one place to bump.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
// Optional real release keystore, read from local.properties (never committed):
//   release.storeFile=/abs/path/cairn-release.jks
//   release.storePassword=...   release.keyAlias=...   release.keyPassword=...
val hasReleaseKeystore = listOf("release.storeFile", "release.storePassword", "release.keyAlias", "release.keyPassword")
    .all { !localProps.getProperty(it).isNullOrBlank() } && file(localProps.getProperty("release.storeFile") ?: "").isFile

android {
    namespace = "app.cairn"

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(localProps.getProperty("release.storeFile"))
                storePassword = localProps.getProperty("release.storePassword")
                keyAlias = localProps.getProperty("release.keyAlias")
                keyPassword = localProps.getProperty("release.keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "app.cairn"
        versionCode = providers.gradleProperty("cairn.versionCode").get().toInt()
        versionName = providers.gradleProperty("cairn.versionName").get()
    }

    buildTypes {
        release {
            // Falls back to the local debug keystore ONLY when no release keystore is configured, so the
            // APK can be sideloaded. Such a build is not suitable for distribution.
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":source:healthconnect"))
    implementation(project(":source:corosmcp"))
    implementation(project(":core:datastore"))
    implementation(project(":core:database"))
    implementation(project(":data"))
    implementation(project(":sync"))
    implementation(project(":widget"))
    implementation(libs.work.runtime.ktx)
    implementation(libs.androidx.browser)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    testImplementation(libs.junit)
    debugImplementation(libs.compose.ui.tooling)
}

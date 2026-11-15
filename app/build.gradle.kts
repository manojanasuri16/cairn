plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Version lives in gradle.properties (cairn.versionName / cairn.versionCode): one place to bump.
android {
    namespace = "app.cairn"

    signingConfigs {
        // The release key lives outside the repo; its path and passwords are in ~/.gradle/gradle.properties as
        // CAIRN_STORE_FILE / CAIRN_STORE_PASSWORD / CAIRN_KEY_ALIAS / CAIRN_KEY_PASSWORD. Never commit them.
        val storeFile = providers.gradleProperty("CAIRN_STORE_FILE").orNull
        if (storeFile != null && file(storeFile).exists()) {
            create("release") {
                this.storeFile = file(storeFile)
                storePassword = providers.gradleProperty("CAIRN_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("CAIRN_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("CAIRN_KEY_PASSWORD").get()
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
            // Falls back to the debug key ONLY when the release key is not configured; such a build is not for distribution.
            signingConfig = signingConfigs.findByName("release") ?: run {
                logger.warn("WARNING: CAIRN_STORE_FILE not set or missing; signing release with the DEBUG key (not for distribution).")
                signingConfigs.getByName("debug")
            }
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

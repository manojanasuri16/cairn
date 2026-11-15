plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    testOptions.unitTests.isIncludeAndroidResources = true
}

// Same offline Robolectric setup as :sync.
val robolectricJars = File(System.getProperty("user.home"), ".robolectric-jars")
tasks.withType<Test>().configureEach {
    if (robolectricJars.isDirectory) {
        systemProperty("robolectric.offline", "true")
        systemProperty("robolectric.dependency.dir", robolectricJars.absolutePath)
    }
}

dependencies {
    api(project(":sync"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":data"))
    api(project(":core:designsystem"))
    implementation(libs.datastore.preferences)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.glance.appwidget.testing)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui)
}

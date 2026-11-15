plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    testOptions.unitTests.isIncludeAndroidResources = true
}

// Robolectric normally downloads its android-all jar at test time. If a pre-fetched copy exists in
// ~/.robolectric-jars (useful behind restricted networks) use it offline; otherwise fetch as usual.
val robolectricJars = File(System.getProperty("user.home"), ".robolectric-jars")
tasks.withType<Test>().configureEach {
    if (robolectricJars.isDirectory) {
        systemProperty("robolectric.offline", "true")
        systemProperty("robolectric.dependency.dir", robolectricJars.absolutePath)
    }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:database"))
    api(project(":core:datastore"))
    api(project(":data"))
    api(project(":source:corosmcp"))
    api(project(":source:healthconnect"))
    api(libs.work.runtime.ktx)
    api(libs.datastore.preferences)
    api(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.work.testing)
}

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    testOptions.unitTests.isIncludeAndroidResources = true
    sourceSets.getByName("test").assets.srcDir("$projectDir/schemas")
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
    api(libs.room.runtime)
    api(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}

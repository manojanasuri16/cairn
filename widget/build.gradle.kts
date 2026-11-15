plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

dependencies {
    api(project(":sync"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":data"))
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}

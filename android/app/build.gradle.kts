plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.steady.accountability"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.steady.accountability"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("androidx.core:core-ktx:1.19.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    implementation("io.github.jan-tennert.supabase:auth-kt:3.6.0")
    implementation("io.github.jan-tennert.supabase:postgrest-kt:3.6.0")
    implementation("io.ktor:ktor-client-android:3.4.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
}

val localConfig = java.util.Properties().apply {
    val config = rootProject.file("local.properties")
    if (config.exists()) config.inputStream().use(::load)
}
fun buildConfigValue(key: String): String = localConfig.getProperty(key, "").replace("\\", "\\\\").replace("\"", "\\\"")
android.defaultConfig {
    buildConfigField("String", "SUPABASE_URL", "\"${buildConfigValue("supabase.url")}\"")
    buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"${buildConfigValue("supabase.publishableKey")}\"")
}

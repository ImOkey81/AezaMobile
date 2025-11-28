import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.kapt")
}
apply(plugin = "dagger.hilt.android.plugin")

private fun String.toBuildConfigString(): String =
    this.replace("\\", "\\\\").replace("\"", "\\\"")

private val localProperties: Properties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

private val apiBaseUrl = localProperties.getProperty("api.baseUrl", "https://check-host.net/")
private val apiUsername = localProperties.getProperty("api.username", "siderea_78")
private val apiPassword = localProperties.getProperty("api.password", "sidereaGISART_78")
android {
    namespace = "aeza.hostmaster.mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "aeza.hostmaster.mobile"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl.toBuildConfigString()}\"")
        buildConfigField("String", "API_USERNAME", "\"${apiUsername.toBuildConfigString()}\"")
        buildConfigField("String", "API_PASSWORD", "\"${apiPassword.toBuildConfigString()}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}
kapt {
    correctErrorTypes = true
}

dependencies {
    // --- AndroidX Core ---
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.3")

    // --- Compose ---
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // --- Navigation Compose ---
    implementation("androidx.navigation:navigation-compose:2.8.3")

    // --- Lifecycle / ViewModel Compose ---
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")

    // --- Hilt Dependency Injection ---
    implementation("com.google.dagger:hilt-android:${libs.versions.hilt.get()}")
    kapt("com.google.dagger:hilt-compiler:${libs.versions.hilt.get()}")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // --- Retrofit / Networking ---
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // --- Coroutines ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // --- WebSocket (SockJS / STOMP) ---
    implementation("org.java-websocket:Java-WebSocket:1.5.6")
    implementation("com.github.NaikSoftware:StompProtocolAndroid:1.6.6")

    // --- Accompanist (для адаптивного UI) ---
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.34.0")

    // --- Coil (для загрузки изображений, если нужно) ---
    implementation("io.coil-kt:coil-compose:2.6.0")

    // --- Testing ---
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
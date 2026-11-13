plugins { id("com.android.application"); kotlin("android") }

android {
    namespace = "id.recygo.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "id.recygo.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
        val apiUrl = providers.gradleProperty("recygoApiUrl").orElse("http://10.0.2.2:8080").get()
        require(!apiUrl.contains('"') && !apiUrl.contains('\\') && !apiUrl.contains('\n'))
        buildConfigField("String", "API_BASE_URL", "\"$apiUrl\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildTypes { getByName("release") { isMinifyEnabled = false } }
    lint { abortOnError = true }
}
dependencies {
    implementation("com.caverock:androidsvg-aar:1.4")
}

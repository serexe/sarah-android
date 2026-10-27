plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
 namespace = "com.serexe.sarah"
 compileSdk = 35
 signingConfigs {
  create("localReview") {
   val secret = rootProject.file(".local-review/signing-pass")
   storeFile = rootProject.file(".local-review/review.jks")
   storePassword = if(secret.isFile) secret.readText().trim() else "not-configured"
   keyAlias = "sarah-review"
   keyPassword = storePassword
  }
 }
 defaultConfig { applicationId = "com.serexe.sarah"; minSdk = 26; targetSdk = 35; versionCode = 3; versionName = "0.1.2-review"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
 buildTypes { getByName("debug") { signingConfig = signingConfigs.getByName("localReview") }; release { isMinifyEnabled = false } }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 buildFeatures { compose = true }
 packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
 testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
 implementation(platform("androidx.compose:compose-bom:2025.04.01"))
 implementation("androidx.activity:activity-compose:1.10.1")
 implementation("androidx.compose.ui:ui")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.foundation:foundation")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
 testImplementation("junit:junit:4.13.2")
 testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
 testImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
 testImplementation("org.json:json:20240303")
 androidTestImplementation("androidx.test.ext:junit:1.2.1")
 androidTestImplementation("androidx.test:core:1.6.1")
 androidTestImplementation("androidx.test:runner:1.6.2")
}

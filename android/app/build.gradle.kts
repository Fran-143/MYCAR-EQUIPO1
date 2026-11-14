plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

// Sólo valores legales aprobados externamente; nunca se proporciona una versión ficticia.
fun legalConfigString(value: String) = "\"" +
    value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""

android {
    namespace = "com.mycar.app"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.mycar.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Release conserva el documento externo. La política local académica se selecciona únicamente en debug.
        buildConfigField("String", "PRIVACY_SOURCE", legalConfigString("external"))
        buildConfigField("String", "PRIVACY_VERSION",
            legalConfigString(providers.gradleProperty("mycar.privacy.version").getOrElse("")))
        buildConfigField("String", "PRIVACY_DOCUMENT_URL",
            legalConfigString(providers.gradleProperty("mycar.privacy.documentUrl").getOrElse("")))
    }

    buildTypes {
        debug {
            // Sufijo solo si -Pmycar.hotfixSidecar=true (coexistencia con Release en el mismo dispositivo).
            // Por defecto (emulador) conserva com.mycar.app para no pelear con google-services.json.
            // Nota: com.mycar.app.hotfix exige cliente en google-services.json (FCM puede fallar; sesión no).
            if (providers.gradleProperty("mycar.hotfixSidecar").orNull == "true") {
                applicationIdSuffix = ".hotfix"
            }
            buildConfigField("String", "PRIVACY_SOURCE",
                legalConfigString(providers.gradleProperty("mycar.privacy.source").getOrElse("local-academic")))
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"${providers.gradleProperty("mycar.apiBaseUrl").getOrElse("http://10.0.2.2:8081/")}\""
            )
        }

        release {
            // HTTPS público (Render). Override: -Pmycar.apiBaseUrl=https://otro-host/
            // Debug local sigue en 10.0.2.2:8081 (no se altera).
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"${providers.gradleProperty("mycar.apiBaseUrl").getOrElse("https://mycar-backend-1dxi.onrender.com/")}\""
            )

            optimization {
                enable = true
                packageScope = setOf(
                    "androidx.**",
                    "kotlin.**",
                    "kotlinx.**"
                )
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    sourceSets.getByName("test").resources.srcDir("src/main/res/raw")

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
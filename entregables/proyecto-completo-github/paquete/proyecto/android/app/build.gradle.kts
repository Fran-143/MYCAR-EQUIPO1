plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
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
            buildConfigField("String", "PRIVACY_SOURCE",
                legalConfigString(providers.gradleProperty("mycar.privacy.source").getOrElse("local-academic")))
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"${providers.gradleProperty("mycar.apiBaseUrl").getOrElse("http://10.0.2.2:8081/")}\""
            )
        }

        release {
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"https://api.example.invalid/\""
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
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
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
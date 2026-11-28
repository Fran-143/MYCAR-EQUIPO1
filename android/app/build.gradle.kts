plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

// Sólo valores legales aprobados externamente; nunca se proporciona una versión ficticia.
fun legalConfigString(value: String) = "\"" +
    value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""

fun loadKeystoreProperties(): Map<String, String>? {
    val override = providers.gradleProperty("mycar.keystoreProperties").orNull
    val candidates = listOfNotNull(
        override?.let { file(it) },
        rootProject.file("keystore.properties"),
        file("${System.getenv("LOCALAPPDATA")}/MyCar/release-signing/keystore.properties")
    )
    val propsFile = candidates.firstOrNull { it.isFile } ?: return null
    return propsFile.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
        .associate { line ->
            val idx = line.indexOf('=')
            line.substring(0, idx).trim() to line.substring(idx + 1).trim()
        }
}

val releaseKeystoreProps = loadKeystoreProperties()

android {
    namespace = "com.mycar.app"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.mycar.app"
        minSdk = 26
        targetSdk = 37
        // 89 / 1.0.17: teléfono PE +51, layout TIVe, fix subida/guardado SOAT.
        versionCode = 96
        versionName = "1.0.24"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Política académica v1.0 embebida (local-academic) en release y debug.
        // Override: -Pmycar.privacy.source=external + URL/versión externas.
        buildConfigField("String", "PRIVACY_SOURCE",
            legalConfigString(providers.gradleProperty("mycar.privacy.source").getOrElse("local-academic")))
        buildConfigField("String", "PRIVACY_VERSION",
            legalConfigString(providers.gradleProperty("mycar.privacy.version").getOrElse("")))
        buildConfigField("String", "PRIVACY_DOCUMENT_URL",
            legalConfigString(providers.gradleProperty("mycar.privacy.documentUrl").getOrElse("")))
        buildConfigField("boolean", "LOCAL_TEST_TOOLS", "false")
    }

    signingConfigs {
        create("release") {
            val props = releaseKeystoreProps
            if (props != null) {
                val storePath = props["storeFile"]
                    ?: error("keystore.properties sin storeFile")
                storeFile = file(storePath)
                storePassword = props["storePassword"]
                keyAlias = props["keyAlias"]
                keyPassword = props["keyPassword"]
            }
        }
    }

    buildTypes {
        debug {
            // Sufijo solo si -Pmycar.hotfixSidecar=true (coexistencia con Release en el mismo dispositivo).
            // Por defecto (emulador) conserva com.mycar.app para no pelear con google-services.json.
            // Nota: com.mycar.app.hotfix exige cliente en google-services.json (FCM puede fallar; sesión no).
            val hotfix = providers.gradleProperty("mycar.hotfixSidecar").orNull == "true"
            if (hotfix) {
                applicationIdSuffix = ".hotfix"
            }
            buildConfigField("boolean", "LOCAL_TEST_TOOLS", if (hotfix) "true" else "false")
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
            buildConfigField("boolean", "LOCAL_TEST_TOOLS", "false")
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"${providers.gradleProperty("mycar.apiBaseUrl").getOrElse("https://mycar-backend-1dxi.onrender.com/")}\""
            )

            if (releaseKeystoreProps != null) {
                signingConfig = signingConfigs.getByName("release")
            }

            // AGP optimization sobre kotlin.** provocaba IllegalAccessError en runtime
            // (kotlin.text.* inaccesible desde clase ofuscada en MainActivity.onCreate).
            // Se conserva minify/recursos estándar; no se optimiza el paquete kotlin.
            optimization {
                enable = true
                packageScope = setOf(
                    "androidx.**"
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
    // Requerido por lintVitalRelease: registerForActivityResult exige Fragment ≥ 1.3.0
    implementation("androidx.fragment:fragment-ktx:1.8.6")
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
    implementation("com.google.zxing:core:3.5.3")

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

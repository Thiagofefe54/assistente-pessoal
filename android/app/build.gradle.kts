import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val koiLocal = Properties().apply {
    val config = rootProject.file("koiwai.local.properties")
    if (config.exists()) config.inputStream().use { load(it) }
}
fun koiConfig(name: String, fallback: String = ""): String =
    providers.gradleProperty(name).orNull ?: koiLocal.getProperty(name) ?: fallback
fun configLiteral(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.thiago.assistentepessoal"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.thiago.assistentepessoal"
        minSdk = 26
        targetSdk = 37
        versionCode = 9
        versionName = "1.7"

        // Pass -PkoiBackendUrl=https://your-server.example for cloud builds.
        val backendUrl = koiConfig("koiBackendUrl").takeIf { it.isNotBlank() }
        require(backendUrl == null || backendUrl.matches(Regex("[a-zA-Z0-9:/._-]+"))) {
            "koiBackendUrl must be an HTTP(S) origin without credentials or query parameters"
        }
        buildConfigField("String", "BACKEND_URL", "\"${backendUrl ?: ""}\"")

        buildConfigField("String", "SUPABASE_URL", configLiteral(koiConfig("koiSupabaseUrl")))
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", configLiteral(koiConfig("koiSupabasePublishableKey")))

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            val backendUrl = koiConfig("koiBackendUrl", "http://10.0.2.2:8000")
            buildConfigField("String", "BACKEND_URL", "\"$backendUrl\"")
            manifestPlaceholders["cleartextAllowed"] = "true"
        }
        release {
            manifestPlaceholders["cleartextAllowed"] = "false"
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.room.runtime)
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    ksp(libs.androidx.room.compiler)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

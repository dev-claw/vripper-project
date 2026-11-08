import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

val releaseKeystorePath = providers.environmentVariable("VRIPPER_KEYSTORE_FILE").orNull
val releaseKeystore = releaseKeystorePath
    ?.takeIf { it.isNotBlank() }
    ?.let { file(it) }
val hasReleaseSigning = releaseKeystore?.exists() == true

// The mobile client is released together with the rest of the project, so derive
// its version from the root pom.xml (updated by release-please). Nightly builds
// pass -Pvripper.versionName to include their date/commit suffix.
val appVersionName = providers.gradleProperty("vripper.versionName").orNull
    ?.takeIf { it.isNotBlank() }
    ?: providers.fileContents(
        rootProject.layout.projectDirectory.file("../pom.xml")
    ).asText.map { pom ->
        Regex("<revision>([^<]+)</revision>")
            .find(pom)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "1.0"
    }.orElse("1.0").get()

val appVersionCode = providers.gradleProperty("vripper.versionCode").orNull
    ?.toIntOrNull()
    ?: parseVersionCode(appVersionName)

android {
    namespace = "me.vripper"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "me.vripper"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = appVersionCode
        versionName = appVersionName
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = providers.environmentVariable("VRIPPER_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("VRIPPER_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("VRIPPER_KEY_PASSWORD").orNull
            }
        }
    }
    buildTypes {
        release {
            // Falls back to the debug key so local/CI release builds are always installable.
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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
    buildFeatures {
        compose = true
    }
}

private fun parseVersionCode(version: String): Int {
    val parts = version.substringBefore('-').split('.')
    val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minor = (parts.getOrNull(1)?.toIntOrNull() ?: 0).coerceAtMost(999)
    val patch = (parts.getOrNull(2)?.toIntOrNull() ?: 0).coerceAtMost(999)
    return major * 1_000_000 + minor * 1_000 + patch
}
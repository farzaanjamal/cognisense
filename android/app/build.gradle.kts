import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Git commit embedded in every session record, with "-dirty" if there are uncommitted changes.
fun git(vararg args: String): String? = try {
    val p = ProcessBuilder("git", *args).directory(rootDir).redirectErrorStream(true).start()
    val out = p.inputStream.bufferedReader().readText().trim()
    if (p.waitFor() == 0) out else null
} catch (e: Exception) { null }

val gitCommit: String = git("rev-parse", "--short=12", "HEAD")
    ?.let { hash -> if (git("status", "--porcelain").isNullOrEmpty()) hash else "$hash-dirty" }
    ?: "uncommitted"

android {
    namespace = "org.cognisense.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.cognisense.app"
        minSdk = 26        // Android 8.0: keeps older, cheaper field phones supported
        targetSdk = 35     // Android 15
        versionCode = 1
        versionName = "0.1.0-dev"
        buildConfigField("String", "GIT_COMMIT", "\"$gitCommit\"")
    }
    buildFeatures { buildConfig = true }

    // Two distributions. "reviewer" opens directly in Expert Review: Session mode and the dashboard
    // are unreachable, so it cannot be used as a field tool. Installed side by side with "standard".
    flavorDimensions += "distribution"
    productFlavors {
        create("standard") {
            dimension = "distribution"
            buildConfigField("boolean", "REVIEWER", "false")
            manifestPlaceholders["appLabel"] = "Cognisense"
        }
        create("reviewer") {
            dimension = "distribution"
            applicationIdSuffix = ".reviewer"
            versionNameSuffix = "-reviewer"
            buildConfigField("boolean", "REVIEWER", "true")
            manifestPlaceholders["appLabel"] = "Cognisense Review"
        }
    }

    // Release signing comes ONLY from environment variables (set as GitHub Actions secrets).
    // The keystore and passwords are never committed. Without them, release builds are unsigned.
    val keystore = System.getenv("COGNISENSE_KEYSTORE")
    signingConfigs {
        if (keystore != null) create("release") {
            storeFile = file(keystore)
            storePassword = System.getenv("COGNISENSE_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("COGNISENSE_KEY_ALIAS")
            keyPassword = System.getenv("COGNISENSE_KEY_PASSWORD")
        }
    }
    // Single source of truth: config/tasks.json is packaged unchanged as the asset "tasks.json".
    sourceSets["main"].assets.srcDir("../../config")
    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystore != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":core")) // the only dependency
}

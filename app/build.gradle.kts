plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services) apply false
}

// google-services.json holds real Firebase project credentials and is gitignored.
// Only apply the plugin when a file is actually present (CI provides one from a
// secret, or falls back to the checked-in placeholder — see .github/workflows/build.yml)
// so a fresh checkout without secrets still compiles.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// versionCode must strictly increase for Android to treat a new install as
// an update rather than a downgrade — GITHUB_RUN_NUMBER (auto-provided by
// Actions on every run) is a simple monotonically increasing source for it.
// Bump appVersionName by hand when there's an actual product-version change;
// versionCode/the +build suffix take care of making every CI build unique
// and installable over the last regardless.
val appVersionName = "0.12.0"
val appVersionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
val appFullVersionName = "$appVersionName+$appVersionCode"

android {
    namespace = "com.glimpse.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.glimpse.app"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appFullVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Fine-grained PAT (Contents: Read-only, scoped to this repo) used
        // to check/download GitHub Releases from the Android app — the repo
        // is private, so the unauthenticated public API isn't usable here.
        // Left blank (and the in-app update check silently does nothing)
        // when the secret isn't set, e.g. for local/PR builds.
        buildConfigField(
            "String",
            "GLIMPSE_RELEASES_TOKEN",
            "\"${System.getenv("GLIMPSE_RELEASES_TOKEN") ?: ""}\""
        )
        buildConfigField("String", "RELEASES_REPO_OWNER", "\"aman-dhakar-191\"")
        buildConfigField("String", "RELEASES_REPO_NAME", "\"glimpse\"")
    }

    // Release signing comes from env vars CI populates from repo secrets
    // (RELEASE_KEYSTORE_PATH/_PASSWORD/_ALIAS/_KEY_PASSWORD — see
    // .github/workflows/build.yml). Left unset, release builds are unsigned;
    // nothing here requires them to be present since PR validation only runs
    // assembleDebug.
    val releaseKeystorePath = System.getenv("RELEASE_KEYSTORE_PATH")
    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseKeystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDirs("src/main/kotlin")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.material)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.functions)

    implementation(libs.play.services.auth)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

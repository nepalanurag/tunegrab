plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release keystore credentials (0600, never committed). Parsed with plain
// stdlib calls because the script classpath can't resolve java.util here.
val ksProps: Map<String, String> =
    file("keystore.properties").takeIf { it.exists() }
        ?.readLines()
        ?.mapNotNull { line ->
            val i = line.indexOf('=')
            if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
        }
        ?.toMap()
        ?: emptyMap()
val ksFile: File? =
    ksProps["storeFile"]?.let { file(it) }?.takeIf { it.exists() }

android {
    namespace = "com.tunegrab.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tunegrab.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.1.0"
    }

    signingConfigs {
        create("release") {
            if (ksFile != null) {
                storeFile = ksFile
                storePassword = ksProps["storePassword"]
                keyAlias = ksProps["keyAlias"]
                keyPassword = ksProps["keyPassword"]
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // R8: obfuscation is part of the anti-tamper story (harder to
            // find and patch the license checks), plus a smaller APK.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Pinned at publish time from the release keystore's SHA-256
            // (keytool -list -v). TamperCheck compares lowercase hex.
            buildConfigField("String", "RELEASE_CERT_SHA256", "\"7326a3bb4875fed32f917f78bfa3818a1e493e9990cd70a439dfba2c22dfd0ca\"")
            buildConfigField("String", "INTEGRITY_VERIFY_URL", "\"\"")
            buildConfigField("boolean", "FORCE_PRO", "false")
        }
        debug {
            buildConfigField("String", "RELEASE_CERT_SHA256", "\"\"")
            buildConfigField("String", "INTEGRITY_VERIFY_URL", "\"\"")
            buildConfigField("boolean", "FORCE_PRO", "false")
        }
        create("pro") {
            // Personal Pro build: identical to release (shrunk, optimized)
            // but with Pro permanently unlocked and ads never initialized.
            // Not for publishing — the store build must use the purchase flow.
            initWith(getByName("release"))
            // Library modules (:ffmpeg, :common, …) only ship debug/release
            // variants — fall back to their release artifacts for pro builds.
            matchingFallbacks += "release"
            buildConfigField("String", "RELEASE_CERT_SHA256", "\"\"")
            buildConfigField("String", "INTEGRITY_VERIFY_URL", "\"\"")
            buildConfigField("boolean", "FORCE_PRO", "true")
        }
    }

    // Distribution channels. This repo builds the free player: a clean
    // local music player with no downloader code at all. (The Pro build
    // with downloads lives in the private tunegrab-pro repo.)
    //
    // - playstore: AdMob rewarded ads + Play Integrity (proprietary SDKs).
    // - fdroid: 100% FOSS — no proprietary dependencies. Ad-gated
    //   features are unlocked outright; a Ko-fi link points to Pro.
    flavorDimensions += "channel"
    productFlavors {
        create("playstore") {
            dimension = "channel"
            buildConfigField("boolean", "INCLUDE_DOWNLOADER", "false")
            resValue("string", "app_name", "TuneGrab")
            // Pixel 11 is arm64-v8a; shipping only that ABI keeps the APK small.
            ndk {
                abiFilters += "arm64-v8a"
            }
        }
        create("fdroid") {
            dimension = "channel"
            buildConfigField("boolean", "INCLUDE_DOWNLOADER", "false")
            resValue("string", "app_name", "TuneGrab")
            // No ABI filter: F-Droid serves every device type, so the
            // APK is universal.
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
        // TamperCheck / IntegrityManager read BuildConfig fields.
        buildConfig = true
    }
    packaging {
        // youtubedl-android README requires extracted native libs.
        jniLibs {
            useLegacyPackaging = true
        }
    }
    lint {
        // Pre-existing Fragment version issue, not caused by our code.
        disable += "InvalidFragmentVersionForActivityResult"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")

    // AdMob: rewarded ads to unlock pro features (equalizer, song info cleaner)
    // in the free Play Store build. Playstore flavor only — the SDK is
    // proprietary and F-Droid forbids it.
    "playstoreImplementation"("com.google.android.gms:play-services-ads:23.4.0")

    // Note: the yt-dlp + ffmpeg downloader engine (vendored from
    // yausername/youtubedl-android) and the direct flavor live in the
    // private tunegrab-pro repo only. This repo is the free player.

    // jaudiotagger: read/write audio tags for the "Clean up song info"
    // pass (Settings → Library). Pure Java, no Android dependencies.
    implementation("net.jthink:jaudiotagger:3.0.1")

    // Ko-fi build: permanently unlocked, no billing or ads.
    // Anti-tamper: Play Integrity API (verdict enforced when the server
    // backend from PUBLISH_GUIDE.md is configured). Playstore flavor only —
    // the API is proprietary and F-Droid forbids it.
    "playstoreImplementation"("com.google.android.play:integrity:1.4.0")

    // Pro Phase 1: Media3 playback engine (ExoPlayer + MediaSession).
    val media3Version = "1.7.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-common:$media3Version")

    // Unit tests for the freemium rules (JVM, no device needed).
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.0.20")
}

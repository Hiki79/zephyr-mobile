// AGP 9 compiles Kotlin itself, so org.jetbrains.kotlin.android is not applied;
// the two compiler plugins below still are, and jvmTarget follows compileOptions.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// The release signing material is supplied by the build environment, never by a
// file in the repository. Without it the build still succeeds and produces an
// APK signed with the local debug key, which is fine for a scratch build but
// cannot be installed over a release one.
val keystorePath: String? = System.getenv("ZEPHYR_KEYSTORE")
val keystorePassword: String? = System.getenv("ZEPHYR_KEYSTORE_PASSWORD")
val keyAlias: String? = System.getenv("ZEPHYR_KEY_ALIAS")
val keyPassword: String? = System.getenv("ZEPHYR_KEY_PASSWORD")
val hasReleaseSigning = !keystorePath.isNullOrBlank() && file(keystorePath).isFile

android {
    namespace = "dev.zephyr.mobile"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.zephyr.mobile"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        // The Go core is built for arm64 only, which is every phone this decade.
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
                storeType = "PKCS12"
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/*.kotlin_module",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
            )
        }
        // The Go core is a 40 MB shared object; leaving it uncompressed costs
        // download size but lets Android map it instead of unpacking it.
        jniLibs { useLegacyPackaging = false }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    // zephyrcore.aar is produced by `gomobile bind` in CI (see .github/workflows)
    // and is the only binary in the build. It contains mihomo v1.19.31 plus the
    // ~200-line bridge in core/zephyr.go, and nothing else.
    implementation(files("libs/zephyrcore.aar"))

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-service:2.11.0")

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // HTTP for subscriptions and mihomo's REST API; WebSocket for its log stream.
    implementation("com.squareup.okhttp3:okhttp:5.5.0")

    // Merging our runtime keys over the subscription's YAML, same job the desktop
    // build does with serde_yaml.
    implementation("org.yaml:snakeyaml:2.7")
}

// AGP 9 compiles Kotlin itself, so org.jetbrains.kotlin.android is not applied;
// the two compiler plugins below still are, and jvmTarget follows compileOptions.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Release builds come out of Gradle unsigned on purpose. Signing happens as a
// visible step in the CI workflow with apksigner, so the key never has to be
// threaded through the build script and an auditor can see exactly what signs
// the APK and with which flags.
android {
    namespace = "dev.zephyr.mobile"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.zephyr.mobile"
        minSdk = 26
        targetSdk = 37
        versionCode = 5
        versionName = "0.1.4"

        // The Go core is built for arm64 only, which is every phone this decade.
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        buildConfig = true
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
    testImplementation("junit:junit:4.13.2")
}

val buildPython = if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3"
val bundleGeoData by tasks.registering(Exec::class) {
    workingDir(rootProject.projectDir)
    commandLine(buildPython, "scripts/fetch_geodata.py")
}
tasks.named("preBuild") { dependsOn(bundleGeoData) }

val testCoreBridge by tasks.registering(Exec::class) {
    workingDir(rootProject.file("core"))
    commandLine("go", "test", "-p", "2", "-v", "-tags", "foss,with_gvisor,cmfa", "./...")
}

val verifyRelease by tasks.registering(Exec::class) {
    dependsOn("packageRelease")
    workingDir(rootProject.projectDir)
    commandLine(buildPython, "scripts/verify_release.py",
        "app/build/outputs/mapping/release/mapping.txt",
        "app/build/outputs/apk/release/app-release-unsigned.apk")
}
tasks.matching { it.name == "assembleRelease" }.configureEach {
    dependsOn("testDebugUnitTest", testCoreBridge, verifyRelease)
}
tasks.withType<Test>().configureEach {
    testLogging { events("passed", "failed", "skipped") }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    id("kotlin-parcelize")
}

android {
    namespace = "com.example.recemotion"
    compileSdk = 35
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "com.example.recemotion"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // Only arm64-v8a for 16KB page alignment compatibility
            abiFilters.clear()
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                cppFlags("-std=c++11", "-fexceptions", "-frtti")
                arguments(
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_ABI=arm64-v8a"
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
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
    kotlinOptions {
        jvmTarget = "11"
    }
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
    // Kuromoji META-INF ファイル重複を除外
    packaging {
        resources {
            excludes += listOf(
                "META-INF/CONTRIBUTORS.md",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE.md"
            )
        }
    }
    
    buildFeatures {
        viewBinding = true
        compose = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-fragment:1.2.0")
    
    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")

    // Compose BOM — manages all androidx.compose.* versions
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Compose libraries (no versions needed — managed by BOM)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Lifecycle — collectAsStateWithLifecycle
    implementation(libs.androidx.lifecycle.compose)

    // activity-compose
    implementation(libs.androidx.activity.compose)

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // MediaPipe
    val mediapipeVersion = "0.10.+"
    implementation("com.google.mediapipe:tasks-vision:$mediapipeVersion")
    implementation("com.google.mediapipe:tasks-genai:$mediapipeVersion")

    // Kuromoji - 日本語形態素解析（JNI不要）
    implementation("com.atilika.kuromoji:kuromoji-ipadic:0.9.0")

    // CameraX
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Markwon - Markdown rendering
    implementation("io.noties.markwon:core:4.6.2")

    // Settings system
    compileOnly(project(":settings-processor"))
    ksp(project(":settings-processor"))

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ViewPager2
    implementation("androidx.viewpager2:viewpager2:1.1.0")

}

tasks.register<Exec>("cargoBuild") {
    workingDir = file("src/main/rust")

    val ndkPath = System.getenv("ANDROID_NDK_HOME")
        ?: run {
            val props = java.util.Properties()
            val localProps = rootProject.file("local.properties")
            if (localProps.exists()) props.load(localProps.inputStream())
            val sdkDir = props.getProperty("sdk.dir") ?: error("sdk.dir not found")
            "$sdkDir/ndk/29.0.14206865"
        }

    val cargoPath = System.getenv("CARGO_HOME")
        ?.let { "$it/bin/cargo" }
        ?: "cargo"  // CI環境ではPATH経由で解決

    environment("ANDROID_NDK_HOME", ndkPath)
    environment("RUSTFLAGS", "-C link-arg=-Wl,-z,max-page-size=16384")

    commandLine(
        cargoPath, "ndk",
        "-t", "aarch64-linux-android",
        "-o", "../jniLibs",
        "build", "--release",
        "--lib"   // 障害C: [lib]のcdylibのみ対象、[[bin]]を除外
    )
}

tasks.named("preBuild") {
    dependsOn("cargoBuild")
}

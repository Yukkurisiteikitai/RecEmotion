plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.recemotion"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.recemotion"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("androidx.work:work-runtime-ktx:2.9.0")
}

tasks.register<Exec>("cargoBuild") {
    // Determine the NDK path (this is a simplified approach, ideally read from local.properties)
    // Failing that, cargo-ndk often finds it if ANDROID_NDK_HOME is set or via simple lookup.
    // For now, we rely on cargo-ndk's auto-discovery or user setting the env var.

    workingDir = file("src/main/rust")
    environment("ANDROID_NDK_HOME", "/Users/yuuto/Library/Android/sdk/ndk/29.0.14206865")
    commandLine("/Users/yuuto/.cargo/bin/cargo", "ndk", "-t", "aarch64-linux-android", "-t", "x86_64-linux-android", "-o", "../jniLibs", "build", "--release")
}

tasks.named("preBuild") {
    dependsOn("cargoBuild")
}
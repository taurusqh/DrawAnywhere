plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "drawanywhere"
    compileSdk = 36

    defaultConfig {
        applicationId = "drawanywhere"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "1.0.7"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
                ?: findProperty("KEYSTORE_PASSWORD") as String?
            keyPassword = System.getenv("KEYSTORE_KEY_PASSWORD")
                ?: findProperty("KEYSTORE_KEY_PASSWORD") as String?
            keyAlias = System.getenv("KEY_ALIAS")
                ?: findProperty("KEY_ALIAS") as String?
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.core:core-ktx:1.15.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
plugins {
    id("com.android.application")
}

val releaseKeystore = System.getenv("ANDROID_KEYSTORE_FILE")
val releaseStorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("ANDROID_KEY_ALIAS")
val releaseKeyPassword = System.getenv("ANDROID_KEY_PASSWORD")
val releaseVersionName = System.getenv("ANDROID_VERSION_NAME") ?: "0.1.0"
val releaseVersionCode = (System.getenv("ANDROID_VERSION_CODE") ?: "1").toInt()

android {
    namespace = "hu.craftlive.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "hu.craftlive.android"
        minSdk = 26
        targetSdk = 35
        versionCode = releaseVersionCode
        versionName = releaseVersionName
    }

    signingConfigs {
        if (!releaseKeystore.isNullOrBlank()) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        getByName("release") {
            isMinifyEnabled = false
            if (!releaseKeystore.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt"
            )
        }
    }
}

dependencies {
    implementation("androidx.core:core:1.15.0")
    implementation("org.java-websocket:Java-WebSocket:1.5.7")
    // Screen capture, in-memory viewer overlay and RTMP/RTMPS publishing.
    implementation("com.github.pedroSG94.RootEncoder:library:2.6.1")
    // Nem hivatalos, hitelesítés nélküli TikTok LIVE kliens. A CraftLive reflexión
    // keresztül használja, így a csatlakozó később az UI átírása nélkül cserélhető.
    implementation("com.github.jwdeveloper.TikTok-Live-Java:Client:1.10.0-Release")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

// CI decodes the release keystore here; without it the release build is signed with the debug key.
val releaseKeystore = System.getenv("SCREENLATE_KEYSTORE")?.let(::File)?.takeIf { it.isFile }

fun modelProperty(name: String) = providers.gradleProperty("screenlate.$name").get()

android {
    namespace = "com.screenlate.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.screenlate.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "0.4.0"

        ndk {
            abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            cmake {
                targets += "spm_jni"
                arguments += "-DANDROID_STL=c++_static"
            }
        }

        buildConfigField("String", "MODEL_VERSION", "\"${modelProperty("modelVersion")}\"")
        buildConfigField("String", "MODEL_URL", "\"${modelProperty("modelUrl")}\"")
        buildConfigField("String", "MODEL_SHA256", "\"${modelProperty("modelSha256")}\"")
        buildConfigField("long", "MODEL_BYTES", "${modelProperty("modelBytes")}L")
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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

    // ONNX Runtime's library is 33 MB; compressing it keeps the APK under the spec's 30 MB.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

// CI builds the tokenizer library for the host and unpacks the model so JVM tests can use both.
tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
    System.getenv("SCREENLATE_HOST_NATIVE_DIR")?.let { systemProperty("java.library.path", it) }
    System.getenv("SCREENLATE_MODEL_DIR")?.let { systemProperty("screenlate.modelDir", it) }
}

// JVM tests run the translator on desktop ONNX Runtime; the Android build has no host natives.
configurations.configureEach {
    if (name.endsWith("UnitTestRuntimeClasspath")) {
        exclude(group = "com.microsoft.onnxruntime", module = "onnxruntime-android")
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.onnxruntime.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // android.jar only has stubs of org.json; tests need the real thing.
    testImplementation(libs.org.json)
    testImplementation(libs.onnxruntime.desktop)
}

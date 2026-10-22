import org.jetbrains.kotlin.gradle.dsl.JvmTarget

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.hermes.companion"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hermes.companion"
        minSdk = 31
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0"
    }

    val signingProps = Properties().apply {
        val f = rootProject.file("signing/keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    signingConfigs {
        if (signingProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "DEFAULT_SERVER_URL", "\"http://10.0.2.2:8020\"")
        }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            buildConfigField("String", "DEFAULT_SERVER_URL", "\"${project.findProperty("hermes.defaultServerUrl") ?: ""}\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime.ktx)
}

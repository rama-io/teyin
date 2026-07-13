import java.time.LocalDate
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
}

val currentYear = LocalDate.now().year

android {
    namespace = "com.rama.teyin"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.rama.teyin"
        minSdk = 21
        targetSdk = 37
        versionCode = 7
        versionName = "$currentYear.$versionCode"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            vcsInfo.include = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }

        create("beta") {
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }

        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    androidResources {
        generateLocaleConfig = true
    }

    packaging {
        resources {
            excludes += "META-INF/*.version"
            excludes += "META-INF/com/android/build/gradle/app-metadata.properties"
        }

        jniLibs {
            useLegacyPackaging = true
        }
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set(
                output.versionName.map { name ->
                    "teyin_${name}.apk"
                }
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation(project(":bohio"))
}
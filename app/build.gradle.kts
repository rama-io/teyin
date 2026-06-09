import java.time.LocalDate

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val currentYear = LocalDate.now().year

android {
    namespace = "com.rama.teyin"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rama.teyin"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "$currentYear.$versionCode"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            vcsInfo.include = false
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

    applicationVariants.all {
        outputs.all {
            this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            outputFileName = "teyin_${versionName}.apk"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    androidResources {
        generateLocaleConfig = true
    }

    packaging {
        resources.excludes += "META-INF/*.version"
        resources.excludes += "META-INF/com/android/build/gradle/app-metadata.properties"
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
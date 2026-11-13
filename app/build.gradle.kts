import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinParcelize)
    alias(libs.plugins.navigationSafeargs)
}

val mapsProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val mapsApiKey = providers.environmentVariable("MOTOLOG_MAPS_API_KEY")
    .orElse(providers.provider { mapsProperties.getProperty("MAPS_API_KEY", "") }).get()

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.gwynn7.motolog"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.gwynn7.motolog"
        minSdk = 28
        targetSdk = 36
        versionCode = 35
        versionName = "3.5"
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        manifestPlaceholders["APP_LABEL"] = "Motolog"
        buildConfigField("boolean", "MAPS_ENABLED", mapsApiKey.isNotBlank().toString())

    }

    // The in-app language picker must work offline for every installed language.
    bundle {
        language {
            enableSplit = false
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
            manifestPlaceholders["APP_LABEL"] = "MotoLog Dev"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sign through Android Studio/CI using the existing Play upload key.
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures{
        viewBinding = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-maps:20.1.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.gridlayout)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.preference.ktx)

    implementation(libs.image.cropper)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)

    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    ksp(libs.androidx.lifecycle.compiler)

    implementation(libs.androidx.datastore.preferences)
}

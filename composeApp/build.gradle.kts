import org.gradle.kotlin.dsl.implementation
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    listOf(
        iosArm64(),           // Real Device
        iosSimulatorArm64()   // ✅ if targeting M1/M2 simulator
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            binaryOption("bundleId", "nl.rvt.gatas.composeapp")
        }
    }
    
    sourceSets {
        
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.kable)
            implementation(libs.moko.permissions)
            // Location permission is needed only for BLE scanning on older Android versions.
            implementation(libs.moko.permissions.location)
            implementation(libs.ktor.okhttp)
            implementation(libs.compose.material3)
            implementation(libs.androidx.material) // FOr theme Dark mode

            implementation(libs.androidx.appcompat)
            implementation(libs.koin.android)
            implementation(libs.koin.ktor)
            implementation(libs.koin.logger.slf4j)
        }

        commonMain.dependencies {
//            implementation(libs.gatas.library)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.moko.permissions)
            implementation(libs.moko.permissions.compose)
            implementation(libs.moko.permissions.bluetooth)
            implementation(libs.kable)
            implementation(libs.compose.material3)
            implementation(libs.bundles.ktor)
            implementation(libs.ktor.network)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.composeIcons.lineAwesome)
            implementation(libs.kermit)
            implementation(libs.koin.core)
            implementation(libs.okio)
        }

        iosMain.dependencies {
            implementation(libs.ktor.darwin)
            implementation(libs.bundles.ktor)
        }
//        val iosMain by getting {
//            dependencies {
//                implementation(libs.ktor.darwin)
//            }
//        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }

    // Should be the top one?
    sourceSets.commonTest.dependencies {
        implementation(kotlin("test"))
    }
}

android {
    namespace = "nl.rvt.gatas"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "nl.rvt.gatas"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

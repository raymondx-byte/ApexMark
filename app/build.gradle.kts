import com.android.build.api.dsl.ApplicationExtension
import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// ── Release 签名配置 ──
// keystore.properties 必须 .gitignore，绝不入库。
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(FileInputStream(keystorePropertiesFile))
    }
}

android {
    namespace = "com.apexmark"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.apexmark"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val storeFilePath = keystoreProperties["storeFile"] as String?
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = keystoreProperties["storePassword"] as String?
                keyAlias = keystoreProperties["keyAlias"] as String?
                keyPassword = keystoreProperties["keyPassword"] as String?
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 仅当 keystore.properties 存在时启用 release 签名；否则 fallback 到 debug 签名以便本地调试 release 行为。
            signingConfig = if (keystorePropertiesFile.exists())
                signingConfigs.getByName("release")
            else
                signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
        }
    }
}

/** CI / 发版：release 目录只保留 `ApexMark.<versionName>.apk`，便于上传与校验。 */
tasks.register("brandReleaseApk") {
    group = "build"
    description = "Run assembleRelease, then rename output to ApexMark.<versionName>.apk and remove other APKs in release/"
    dependsOn("assembleRelease")
    doLast {
        val ext = project.extensions.getByName("android") as ApplicationExtension
        val version = ext.defaultConfig.versionName ?: error("Set android.defaultConfig.versionName")
        val outDir = layout.buildDirectory.dir("outputs/apk/release").get().asFile
        check(outDir.isDirectory) { "Missing release output dir: $outDir — assembleRelease failed?" }
        val dest = File(outDir, "ApexMark.$version.apk")
        val allApks = outDir.listFiles()?.filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }.orEmpty()
        check(allApks.isNotEmpty()) { "No APK in $outDir" }
        val preferred = listOf("app-release.apk", "app-release-unsigned.apk")
        val source = preferred.mapNotNull { n -> allApks.find { it.name == n } }.firstOrNull()
            ?: allApks.filter { it.name != dest.name }.maxByOrNull { it.lastModified() }
            ?: allApks.first()
        if (source.canonicalFile != dest.canonicalFile) {
            source.copyTo(dest, overwrite = true)
        }
        allApks.forEach { f ->
            if (f.name != dest.name && f.exists()) f.delete()
        }
    }
}

tasks.register("printVersionName") {
    group = "help"
    description = "Print android.defaultConfig.versionName (for CI tag check)"
    doLast {
        val ext = project.extensions.getByName("android") as ApplicationExtension
        println(ext.defaultConfig.versionName ?: error("Set android.defaultConfig.versionName"))
    }
}

dependencies {
    implementation(project(":apex-link-core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.0.10")
}

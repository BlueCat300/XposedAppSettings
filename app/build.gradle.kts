plugins {
    id ("com.android.application")
}

android {
    namespace = "ru.bluecat.android.xposed.mods.appsettings"
    compileSdk = 33

    defaultConfig {
        applicationId = "ru.bluecat.android.xposed.mods.appsettings"
        minSdk = 27
        targetSdk = 33
        versionName = "1.7"
        versionCode = 29
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = true
        }

        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles("proguard-android-optimize.txt", "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        disable.add("MissingTranslation")
    }

    applicationVariants.all {
        if (this.buildType.isDebuggable.not()) {
            outputs.all {
                this as com.android.build.gradle.internal.api.ApkVariantOutputImpl
                outputFileName = "App Settings Reborn v${versionName}.apk"
            }
        }
    }
}

dependencies {
    //noinspection GradleDependency
    implementation ("com.mikepenz:materialdrawer:6.1.2")
    implementation ("androidx.appcompat:appcompat:1.6.1")

    implementation ("io.reactivex.rxjava3:rxjava:3.1.5")
    implementation ("io.reactivex.rxjava3:rxandroid:3.0.2")
    implementation ("org.apache.commons:commons-lang3:3.12.0")

    compileOnly ("de.robv.android.xposed:api:82")
}

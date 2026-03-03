import java.util.Properties
import kotlin.apply

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example.jammate"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.jammate"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        val localProps = Properties().apply {
            rootProject.file("local.properties").inputStream().use { load(it) }
        }

        val placesKey = localProps.getProperty("PLACES_API_KEY") ?: ""
        manifestPlaceholders["PLACES_API_KEY"] = localProps.getProperty("PLACES_API_KEY") ?: ""

        buildConfigField(
            "String",
            "PLACES_API_KEY",
            "\"$placesKey\""
        )
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.core)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.libraries.places:places:5.0.0")
    implementation ("androidx.media3:media3-exoplayer:1.2.1")
    implementation ("androidx.media3:media3-ui:1.2.1")
    implementation("com.airbnb.android:lottie:6.4.0")

    //Glide:
    implementation (libs.glide)

    //firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)

    //Auth UI
    implementation(libs.firebase.ui.auth)


    //Real Time Database
    implementation(libs.firebase.database)

    //storage
    implementation(libs.firebase.storage)
}
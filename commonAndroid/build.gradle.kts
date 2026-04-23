plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "ch.threema.android"
    compileSdk = 35
    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        jvmToolchain(21)
    }

    buildTypes {
        debug {}
        release {}
    }
}

dependencies {
    implementation(project(":common"))

    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.lifecycle.runtime.android)
    compileOnly(libs.compose.annotation)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.turbine)
    testImplementation(project(":test-helpers"))
}

sonarqube {
    properties {
        property("sonar.projectKey", "android-client")
        property("sonar.projectName", "Threema for Android")
        property("sonar.sources", "src/main/")
        property("sonar.tests", "src/test/")
        property("sonar.sourceEncoding", "UTF-8")
        property("sonar.verbose", "true")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

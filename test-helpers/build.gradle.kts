plugins {
    alias(libs.plugins.java.library)
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":common"))
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.junit)
    implementation(libs.mockk)
    implementation(libs.kotlin.test)
    implementation(libs.kotlinx.coroutines.test)

    api(libs.turbine)
    api(platform(libs.okhttp3.bom))
    api(libs.okhttp3)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

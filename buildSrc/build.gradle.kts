plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    `kotlin-dsl-precompiled-script-plugins`
}

repositories {
    google()
    maven("https://plugins.gradle.org/m2/")
}

dependencies {
    implementation(libs.android.gradle)
}

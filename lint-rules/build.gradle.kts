plugins {
    alias(libs.plugins.java.library)
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    compileOnly(libs.lint.api)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

tasks.withType<Jar> {
    manifest {
        attributes["Lint-Registry-v2"] = "ch.threema.lint.ThreemaLintRegistry"
    }
}

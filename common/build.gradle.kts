plugins {
    alias(libs.plugins.java.library)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.jacoco)
}

dependencies {
    // Standard libraries
    api(libs.kotlin.stdlib)
    api(libs.kotlinx.coroutines.core)

    // Dependency injection
    api(project.dependencies.platform(libs.koin.bom))
    api(libs.koin.core)

    // HTTP
    api(platform(libs.okhttp3.bom))
    api(libs.okhttp3)
    api(libs.okhttp3.coroutines)
    api(libs.okhttp3.loggingInterceptor)

    // JSON
    implementation(libs.kotlinx.serialization.json)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.turbine)
    testImplementation(project(":test-helpers"))
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<JacocoReport> {
    reports {
        xml.required = true
        html.required = false
    }
}

sonarqube {
    properties {
        property("sonar.projectKey", "android-client")
        property("sonar.projectName", "Threema for Android")
        property("sonar.sources", "src/main/")
        property("sonar.tests", "src/test/")
        property("sonar.sourceEncoding", "UTF-8")
        property("sonar.verbose", "true")
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            "${projectDir.parentFile.path}/build/reports/jacoco/codeCoverageReport/codeCoverageReport.xml",
        )
    }
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

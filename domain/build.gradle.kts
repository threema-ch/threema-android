/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

import com.android.build.gradle.internal.tasks.factory.dependsOn
import utils.getGitVersion

plugins {
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.java.library)
    alias(libs.plugins.java.testFixtures)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.jacoco)
}

dependencies {
    implementation(project(":common"))

    api(libs.kotlin.stdlib)
    api(libs.kotlinx.coroutines.core)
    api(libs.libphonenumber)
    api(libs.androidx.annotation)
    api(libs.streamsupport.flow)
    api(libs.protobuf.kotlin.lite)

    implementation(libs.slf4j.api)
    implementation(libs.commonsIo)
    implementation(libs.eddsa)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.jna)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.slf4j.simple)
    testImplementation(libs.kotlin.test)
    testImplementation(project(":test-helpers"))
}

sourceSets {
    assert(file("./protocol/src/common.proto").exists()) {
        "Error: Git protobuf submodule missing. Please run `git submodule update --init`.\n"
    }

    main {
        java.srcDir("./build/generated/source/proto/main/java")
        java.srcDir("./build/generated/source/proto/main/kotlin")
        java.srcDir("./build/generated/source/libthreema")
    }
}

tasks.withType<Test> {
    // Necessary to load the dynamic libthreema library in unit tests
    systemProperty("jna.library.path", "${project.projectDir}/libthreema/target/release")

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
        property("sonar.exclusions", "src/main/java/ove/crypto/**")
        property("sonar.tests", "src/test/")
        property("sonar.sourceEncoding", "UTF-8")
        property("sonar.verbose", "true")
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            "${projectDir.parentFile.path}/build/reports/jacoco/codeCoverageReport/codeCoverageReport.xml",
        )
    }
}

afterEvaluate {
    val bindingsDirectory = "../build/generated/source/libthreema"

    // Define the task to generate libthreema library (only used to generate bindings for it)
    val generateLibthreema = tasks.register<Exec>("generateLibthreema") {
        workingDir("${project.projectDir}/libthreema")
        commandLine("cargo", "build", "-F", "uniffi", "-p", "libthreema", "--release", "--locked")
    }

    // Define the task to generate the uniffi bindings for libthreema
    val uniffiBindings = tasks.register("generateUniFFIBindings") {
        dependsOn(generateLibthreema)
        doLast {
            // It seems that the uniffi package generates a "*.so" file on linux and a "*.dylib" on mac
            // while using the cargo build command from the gradle task above ("generateLibthreema").
            val uniffiLibraryFilePathPrefix = "${project.projectDir}/libthreema/target/release/liblibthreema"
            val uniffiLibraryFile = file("$uniffiLibraryFilePathPrefix.so")
                .takeIf { it.exists() }
                ?: file("$uniffiLibraryFilePathPrefix.dylib")
            assert(uniffiLibraryFile.exists()) {
                "Error: Missing pre-generated uniffy library file in libthreema/target/*/ directory.\n"
            }

            val processBuilder = ProcessBuilder(
                "cargo",
                "run",
                "-p",
                "uniffi-bindgen",
                "generate",
                "--library",
                uniffiLibraryFile.path,
                "--language",
                "kotlin",
                "--out-dir",
                bindingsDirectory,
                "--no-format",
            )
            processBuilder.directory(file("${project.projectDir}/libthreema"))
            processBuilder.start().waitFor()
        }
    }

    tasks["compileKotlin"].dependsOn(uniffiBindings)
}

publishing {
    publications {
        register<MavenPublication>("library") {
            from(components["java"])
            version = getGitVersion()
        }
    }
    repositories {
        maven {
            url = run {
                val apiV4Url = System.getenv("CI_API_V4_URL")
                val projectId = System.getenv("CI_PROJECT_ID")
                uri("$apiV4Url/projects/$projectId/packages/maven")
            }
            name = "Gitlab"
            credentials(HttpHeaderCredentials::class.java) {
                name = "Job-Token"
                value = System.getenv("CI_JOB_TOKEN")
            }
            authentication {
                create<HttpHeaderAuthentication>("header")
            }
        }
    }
}

tasks.register<Exec>("compileProto") {
    group = "build"
    description = "generate class bindings from protobuf files in the 'protocol/src' directory"
    workingDir(project.projectDir)
    commandLine("./compile-proto.sh")
}

tasks.compileKotlin.dependsOn("compileProto")

tasks.register<Exec>("libthreemaCleanUp") {
    workingDir("${project.projectDir}/libthreema")
    commandLine("cargo", "clean")
}

tasks.clean.dependsOn("libthreemaCleanUp")

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

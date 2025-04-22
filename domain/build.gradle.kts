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
    api(libs.kotlin.stdlib)
    api(libs.kotlinx.coroutines.core)
    api(libs.libphonenumber)
    api(libs.androidx.annotation)
    api(libs.streamsupport.flow)
    api(libs.protobuf.kotlin.lite)
    api(platform(libs.okhttp3.bom))
    api(libs.okhttp3)
    api(libs.okhttp3.loggingInterceptor)

    implementation(libs.slf4j.api)
    implementation(libs.commonsIo)
    implementation(libs.eddsa)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.jna)

    testImplementation(libs.junit)
    testImplementation(libs.mockito.powermock.reflect)
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
        commandLine("cargo", "build", "-F", "uniffi", "-p", "libthreema", "--release")
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

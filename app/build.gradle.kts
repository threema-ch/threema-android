/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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

import com.android.build.gradle.internal.api.ApkVariantOutputImpl
import com.android.build.gradle.internal.tasks.factory.dependsOn
import config.BuildFeatureFlags
import config.PublicKeys
import config.setProductNames
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.kotlin.dsl.lintChecks
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import utils.*

plugins {
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.rust.android)
    id("com.android.application")
    id("kotlin-android")
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.stem)
}

// only apply the plugin if we are dealing with an AppGallery build
if (gradle.startParameter.taskRequests.toString().contains("Hms")) {
    logger.info("enabling hms plugin")
    apply {
        plugin("com.huawei.agconnect")
    }
}

/**
 * Only use the scheme "<major>.<minor>.<patch>" for the appVersion
 */
val appVersion = "6.3.0"

/**
 * betaSuffix with leading dash (e.g. `-beta1`).
 * Should be one of (alpha|beta|rc) and an increasing number, or empty for a regular release.
 * Note: in nightly builds this will be overwritten with a nightly version "-n12345"
 */
val betaSuffix = ""

val defaultVersionCode = 1113

/**
 * Map with keystore paths (if found).
 */
val keystores: Map<String, KeystoreConfig?> = mapOf(
    "debug" to findKeystore(projectDir, "debug"),
    "release" to findKeystore(projectDir, "threema"),
    "hms_release" to findKeystore(projectDir, "threema_hms"),
    "onprem_release" to findKeystore(projectDir, "onprem"),
    "blue_release" to findKeystore(projectDir, "threema_blue"),
)

android {
    // NOTE: When adjusting compileSdkVersion, buildToolsVersion or ndkVersion,
    //       make sure to adjust them in `scripts/Dockerfile` as well!
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    ndkVersion = "28.2.13676358"

    defaultConfig {
        // https://developer.android.com/training/testing/espresso/setup#analytics
        with(testInstrumentationRunnerArguments) {
            put("notAnnotation", "ch.threema.app.DangerousTest")
            put("disableAnalytics", "true")
        }
        minSdk = 24
        targetSdk = 35
        vectorDrawables.useSupportLibrary = true
        applicationId = "ch.threema.app"
        testApplicationId = "$applicationId.test"
        versionCode = defaultVersionCode
        versionName = "$appVersion$betaSuffix"

        setProductNames(
            appName = "Threema",
        )
        intBuildConfigField("DEFAULT_VERSION_CODE", defaultVersionCode)
        // package name used for sync adapter - needs to match mime types below
        stringResValue("package_name", applicationId!!)
        stringResValue("contacts_mime_type", "vnd.android.cursor.item/vnd.$applicationId.profile")
        stringResValue("call_mime_type", "vnd.android.cursor.item/vnd.$applicationId.call")

        intBuildConfigField("MAX_GROUP_SIZE", 256)
        stringBuildConfigField("CHAT_SERVER_PREFIX", "g-")
        stringBuildConfigField("CHAT_SERVER_IPV6_PREFIX", "ds.g-")
        stringBuildConfigField("CHAT_SERVER_SUFFIX", ".0.threema.ch")
        intArrayBuildConfigField("CHAT_SERVER_PORTS", intArrayOf(5222, 443))
        stringBuildConfigField("MEDIA_PATH", "Threema")
        booleanBuildConfigField("CHAT_SERVER_GROUPS", true)
        booleanBuildConfigField("DISABLE_CERT_PINNING", false)
        booleanBuildConfigField("VIDEO_CALLS_ENABLED", true)
        // This public key is pinned for the chat server protocol.
        byteArrayBuildConfigField("SERVER_PUBKEY", PublicKeys.prodServer)
        byteArrayBuildConfigField("SERVER_PUBKEY_ALT", PublicKeys.prodServerAlt)
        stringBuildConfigField("GIT_HASH", getGitHash())
        stringBuildConfigField("GIT_BRANCH", getGitBranch())
        stringBuildConfigField("DIRECTORY_SERVER_URL", "https://apip.threema.ch/")
        stringBuildConfigField("DIRECTORY_SERVER_IPV6_URL", "https://ds-apip.threema.ch/")
        stringBuildConfigField("WORK_SERVER_URL", null)
        stringBuildConfigField("WORK_SERVER_IPV6_URL", null)
        stringBuildConfigField("MEDIATOR_SERVER_URL", "wss://mediator-{deviceGroupIdPrefix4}.threema.ch/{deviceGroupIdPrefix8}")

        // Base blob url used for "download" and "done" calls
        stringBuildConfigField("BLOB_SERVER_URL", "https://blobp-{blobIdPrefix}.threema.ch")
        stringBuildConfigField("BLOB_SERVER_IPV6_URL", "https://ds-blobp-{blobIdPrefix}.threema.ch")

        // Specific blob url used for "upload" calls
        stringBuildConfigField("BLOB_SERVER_URL_UPLOAD", "https://blobp-upload.threema.ch/upload")
        stringBuildConfigField("BLOB_SERVER_IPV6_URL_UPLOAD", "https://ds-blobp-upload.threema.ch/upload")

        // Base blob mirror url used for "download", "upload", "done"
        stringBuildConfigField("BLOB_MIRROR_SERVER_URL", "https://blob-mirror-{deviceGroupIdPrefix4}.threema.ch/{deviceGroupIdPrefix8}")

        stringBuildConfigField("AVATAR_FETCH_URL", "https://avatar.threema.ch/")
        stringBuildConfigField("SAFE_SERVER_URL", "https://safe-{backupIdPrefix8}.threema.ch/")
        stringBuildConfigField("WEB_SERVER_URL", "https://web.threema.ch/")
        stringBuildConfigField("APP_RATING_URL", "https://threema.com/app-rating/android/{rating}")
        stringBuildConfigField("MAP_STYLES_URL", "https://map.threema.ch/styles/threema/style.json")
        stringBuildConfigField("MAP_POI_AROUND_URL", "https://poi.threema.ch/around/{latitude}/{longitude}/{radius}/")
        stringBuildConfigField("MAP_POI_NAMES_URL", "https://poi.threema.ch/names/{latitude}/{longitude}/{query}/")
        byteArrayBuildConfigField("THREEMA_PUSH_PUBLIC_KEY", PublicKeys.threemaPush)
        stringBuildConfigField("ONPREM_ID_PREFIX", "O")
        stringBuildConfigField("LOG_TAG", "3ma")
        stringBuildConfigField("DEFAULT_APP_THEME", "2")

        stringArrayBuildConfigField("ONPREM_CONFIG_TRUSTED_PUBLIC_KEYS", emptyArray())
        booleanBuildConfigField("MD_SYNC_DISTRIBUTION_LISTS", false)
        booleanBuildConfigField("AVAILABILITY_STATUS_ENABLED", BuildFeatureFlags["availability_status"] ?: false)
        booleanBuildConfigField("CRASH_REPORTING_SUPPORTED", BuildFeatureFlags["crash_reporting"] ?: false)

        // TODO(ANDR-4376): Remove this build flag
        booleanBuildConfigField("REFERRAL_PROGRAM_AVAILABLE", BuildFeatureFlags["referral_program_available"] ?: false)

        // config fields for action URLs / deep links
        stringBuildConfigField("uriScheme", "threema")
        stringBuildConfigField("actionUrl", "go.threema.ch")
        stringBuildConfigField("contactActionUrl", "threema.id")

        // The OPPF url must be null in the default config. Do not change this.
        stringBuildConfigField("PRESET_OPPF_URL", null)

        with(manifestPlaceholders) {
            put("uriScheme", "threema")
            put("contactActionUrl", "threema.id")
            put("actionUrl", "go.threema.ch")
            put("callMimeType", "vnd.android.cursor.item/vnd.$applicationId.call")
        }

        testInstrumentationRunner = "$applicationId.ThreemaTestRunner"

        // Only include language resources for those languages
        androidResources.localeFilters.addAll(
            setOf(
                "en",
                "be-rBY",
                "bg",
                "ca",
                "cs",
                "de",
                "es",
                "fr",
                "gsw",
                "hu",
                "it",
                "ja",
                "nl-rNL",
                "no",
                "pl",
                "pt-rBR",
                "ru",
                "sk",
                "tr",
                "uk",
                "zh-rCN",
                "zh-rTW",
            ),
        )
    }

    splits {
        abi {
            isEnable = true
            reset()
            if (project.hasProperty("noAbiSplits")) {
                isUniversalApk = true
            } else {
                include("armeabi-v7a", "x86", "arm64-v8a", "x86_64")
                isUniversalApk = project.hasProperty("buildUniversalApk")
            }
        }
    }

    // Assign different version code for each output
    android.applicationVariants.all {
        outputs.all {
            if (this is ApkVariantOutputImpl) {
                val abi = getFilter("ABI")
                val abiVersionCode = when (abi) {
                    "armeabi-v7a" -> 2
                    "arm64-v8a" -> 3
                    "x86" -> 8
                    "x86_64" -> 9
                    else -> 0
                }
                versionCodeOverride = abiVersionCode * 1_000_000 + defaultVersionCode
            }
        }
    }

    namespace = "ch.threema.app"
    flavorDimensions.add("default")
    productFlavors {
        create("none")
        create("store_google")
        create("store_threema") {
            stringResValue("shop_download_filename", "Threema-update.apk")
        }
        create("store_google_work") {
            versionName = "${appVersion}k$betaSuffix"
            applicationId = "ch.threema.app.work"
            testApplicationId = "$applicationId.test"
            setProductNames(appName = "Threema Work")
            stringResValue("package_name", applicationId!!)
            stringResValue("contacts_mime_type", "vnd.android.cursor.item/vnd.$applicationId.profile")
            stringResValue("call_mime_type", "vnd.android.cursor.item/vnd.$applicationId.call")
            stringBuildConfigField("CHAT_SERVER_PREFIX", "w-")
            stringBuildConfigField("CHAT_SERVER_IPV6_PREFIX", "ds.w-")
            stringBuildConfigField("MEDIA_PATH", "ThreemaWork")
            stringBuildConfigField("WORK_SERVER_URL", "https://apip-work.threema.ch/")
            stringBuildConfigField("WORK_SERVER_IPV6_URL", "https://ds-apip-work.threema.ch/")
            stringBuildConfigField("APP_RATING_URL", "https://threema.com/app-rating/android-work/{rating}")
            stringBuildConfigField("LOG_TAG", "3mawrk")
            stringBuildConfigField("DEFAULT_APP_THEME", "2")

            // config fields for action URLs / deep links
            stringBuildConfigField("uriScheme", "threemawork")
            stringBuildConfigField("actionUrl", "work.threema.ch")

            with(manifestPlaceholders) {
                put("uriScheme", "threemawork")
                put("actionUrl", "work.threema.ch")
                put("callMimeType", "vnd.android.cursor.item/vnd.$applicationId.call")
            }
        }
        create("green") {
            applicationId = "ch.threema.app.green"
            testApplicationId = "$applicationId.test"
            setProductNames(appName = "Threema Green")
            stringResValue("package_name", applicationId!!)
            stringResValue("contacts_mime_type", "vnd.android.cursor.item/vnd.$applicationId.profile")
            stringResValue("call_mime_type", "vnd.android.cursor.item/vnd.$applicationId.call")
            stringBuildConfigField("MEDIA_PATH", "ThreemaGreen")
            stringBuildConfigField("CHAT_SERVER_SUFFIX", ".0.test.threema.ch")
            // This public key is pinned for the chat server protocol.
            byteArrayBuildConfigField("SERVER_PUBKEY", PublicKeys.sandboxServer)
            byteArrayBuildConfigField("SERVER_PUBKEY_ALT", PublicKeys.sandboxServer)
            stringBuildConfigField("DIRECTORY_SERVER_URL", "https://apip.test.threema.ch/")
            stringBuildConfigField("DIRECTORY_SERVER_IPV6_URL", "https://ds-apip.test.threema.ch/")
            stringBuildConfigField("MEDIATOR_SERVER_URL", "wss://mediator-{deviceGroupIdPrefix4}.test.threema.ch/{deviceGroupIdPrefix8}")
            stringBuildConfigField("BLOB_SERVER_URL", "https://blobp-{blobIdPrefix}.test.threema.ch")
            stringBuildConfigField("BLOB_SERVER_IPV6_URL", "https://ds-blobp-{blobIdPrefix}.test.threema.ch")
            stringBuildConfigField("BLOB_SERVER_URL_UPLOAD", "https://blobp-upload.test.threema.ch/upload")
            stringBuildConfigField("BLOB_SERVER_IPV6_URL_UPLOAD", "https://ds-blobp-upload.test.threema.ch/upload")
            stringBuildConfigField("AVATAR_FETCH_URL", "https://avatar.test.threema.ch/")
            stringBuildConfigField("APP_RATING_URL", "https://test.threema.com/app-rating/android/{rating}")
            stringBuildConfigField("MAP_STYLES_URL", "https://map.test.threema.ch/styles/threema/style.json")
            stringBuildConfigField("MAP_POI_AROUND_URL", "https://poi.test.threema.ch/around/{latitude}/{longitude}/{radius}/")
            stringBuildConfigField("MAP_POI_NAMES_URL", "https://poi.test.threema.ch/names/{latitude}/{longitude}/{query}/")
            stringBuildConfigField("BLOB_MIRROR_SERVER_URL", "https://blob-mirror-{deviceGroupIdPrefix4}.test.threema.ch/{deviceGroupIdPrefix8}")
            booleanBuildConfigField("CRASH_REPORTING_SUPPORTED", true)

            // TODO(ANDR-4376): Remove this build flag
            booleanBuildConfigField("REFERRAL_PROGRAM_AVAILABLE", true)
        }
        create("sandbox_work") {
            versionName = "${appVersion}k$betaSuffix"
            applicationId = "ch.threema.app.sandbox.work"
            testApplicationId = "$applicationId.test"
            setProductNames(
                appName = "Threema Sandbox Work",
                appNameDesktop = "Threema Blue",
            )
            stringResValue("package_name", applicationId!!)
            stringResValue("contacts_mime_type", "vnd.android.cursor.item/vnd.$applicationId.profile")
            stringResValue("call_mime_type", "vnd.android.cursor.item/vnd.$applicationId.call")
            stringBuildConfigField("CHAT_SERVER_PREFIX", "w-")
            stringBuildConfigField("CHAT_SERVER_IPV6_PREFIX", "ds.w-")
            stringBuildConfigField("CHAT_SERVER_SUFFIX", ".0.test.threema.ch")
            stringBuildConfigField("MEDIA_PATH", "ThreemaWorkSandbox")
            // This public key is pinned for the chat server protocol.
            byteArrayBuildConfigField("SERVER_PUBKEY", PublicKeys.sandboxServer)
            byteArrayBuildConfigField("SERVER_PUBKEY_ALT", PublicKeys.sandboxServer)
            stringBuildConfigField("DIRECTORY_SERVER_URL", "https://apip.test.threema.ch/")
            stringBuildConfigField("DIRECTORY_SERVER_IPV6_URL", "https://ds-apip.test.threema.ch/")
            stringBuildConfigField("WORK_SERVER_URL", "https://apip-work.test.threema.ch/")
            stringBuildConfigField("WORK_SERVER_IPV6_URL", "https://ds-apip-work.test.threema.ch/")
            stringBuildConfigField("MEDIATOR_SERVER_URL", "wss://mediator-{deviceGroupIdPrefix4}.test.threema.ch/{deviceGroupIdPrefix8}")
            stringBuildConfigField("BLOB_SERVER_URL", "https://blobp-{blobIdPrefix}.test.threema.ch")
            stringBuildConfigField("BLOB_SERVER_IPV6_URL", "https://ds-blobp-{blobIdPrefix}.test.threema.ch")
            stringBuildConfigField("BLOB_SERVER_URL_UPLOAD", "https://blobp-upload.test.threema.ch/upload")
            stringBuildConfigField("BLOB_SERVER_IPV6_URL_UPLOAD", "https://ds-blobp-upload.test.threema.ch/upload")
            stringBuildConfigField("AVATAR_FETCH_URL", "https://avatar.test.threema.ch/")
            stringBuildConfigField("APP_RATING_URL", "https://test.threema.com/app-rating/android-work/{rating}")
            stringBuildConfigField("MAP_STYLES_URL", "https://map.test.threema.ch/styles/threema/style.json")
            stringBuildConfigField("MAP_POI_AROUND_URL", "https://poi.test.threema.ch/around/{latitude}/{longitude}/{radius}/")
            stringBuildConfigField("MAP_POI_NAMES_URL", "https://poi.test.threema.ch/names/{latitude}/{longitude}/{query}/")
            stringBuildConfigField("LOG_TAG", "3mawrk")
            stringBuildConfigField("DEFAULT_APP_THEME", "2")
            stringBuildConfigField("BLOB_MIRROR_SERVER_URL", "https://blob-mirror-{deviceGroupIdPrefix4}.test.threema.ch/{deviceGroupIdPrefix8}")

            // config fields for action URLs / deep links
            stringBuildConfigField("uriScheme", "threemawork")
            stringBuildConfigField("actionUrl", "work.test.threema.ch")

            booleanBuildConfigField("CRASH_REPORTING_SUPPORTED", true)

            stringBuildConfigField("MD_CLIENT_DOWNLOAD_URL", "https://three.ma/mdw")

            with(manifestPlaceholders) {
                put("uriScheme", "threemawork")
                put("actionUrl", "work.test.threema.ch")
            }
        }
        create("onprem") {
            versionName = "${appVersion}o$betaSuffix"
            applicationId = "ch.threema.app.onprem"
            testApplicationId = "$applicationId.test"
            setProductNames(
                appName = "Threema OnPrem",
                shortAppName = "Threema",
                companyName = "Threema",
            )
            stringResValue("package_name", applicationId!!)
            stringResValue("contacts_mime_type", "vnd.android.cursor.item/vnd.$applicationId.profile")
            stringResValue("call_mime_type", "vnd.android.cursor.item/vnd.$applicationId.call")
            intBuildConfigField("MAX_GROUP_SIZE", 256)
            stringBuildConfigField("CHAT_SERVER_PREFIX", "")
            stringBuildConfigField("CHAT_SERVER_IPV6_PREFIX", "")
            stringBuildConfigField("CHAT_SERVER_SUFFIX", null)
            stringBuildConfigField("MEDIA_PATH", "ThreemaOnPrem")
            booleanBuildConfigField("CHAT_SERVER_GROUPS", false)
            byteArrayBuildConfigField("SERVER_PUBKEY", null)
            byteArrayBuildConfigField("SERVER_PUBKEY_ALT", null)
            stringBuildConfigField("DIRECTORY_SERVER_URL", null)
            stringBuildConfigField("DIRECTORY_SERVER_IPV6_URL", null)
            stringBuildConfigField("BLOB_SERVER_URL", null)
            stringBuildConfigField("BLOB_SERVER_IPV6_URL", null)
            stringBuildConfigField("BLOB_SERVER_URL_UPLOAD", null)
            stringBuildConfigField("BLOB_SERVER_IPV6_URL_UPLOAD", null)
            stringBuildConfigField("BLOB_MIRROR_SERVER_URL", null)
            stringArrayBuildConfigField("ONPREM_CONFIG_TRUSTED_PUBLIC_KEYS", PublicKeys.onPremTrusted)
            stringBuildConfigField("LOG_TAG", "3maop")

            // config fields for action URLs / deep links
            val uriScheme = "threemaonprem"
            val actionUrl = "onprem.threema.ch"
            stringBuildConfigField("uriScheme", uriScheme)
            stringBuildConfigField("actionUrl", actionUrl)

            stringBuildConfigField("PRESET_OPPF_URL", null)

            stringBuildConfigField("MD_CLIENT_DOWNLOAD_URL", "https://three.ma/mdo")

            with(manifestPlaceholders) {
                put("uriScheme", uriScheme)
                put("actionUrl", actionUrl)
                put("callMimeType", "vnd.android.cursor.item/vnd.$applicationId.call")
            }
        }
        create("blue") {
            // Essentially like sandbox work, but with a different icon and application id, used for internal testing
            versionName = "${appVersion}b$betaSuffix"
            // The app was previously named `red`. The app id remains unchanged to still be able to install updates.
            applicationId = "ch.threema.app.red"
            testApplicationId = "ch.threema.app.blue.test"
            setProductNames(appName = "Threema Blue")
            stringResValue("package_name", applicationId!!)
            stringResValue("contacts_mime_type", "vnd.android.cursor.item/vnd.ch.threema.app.blue.profile")
            stringResValue("call_mime_type", "vnd.android.cursor.item/vnd.ch.threema.app.blue.call")

            stringBuildConfigField("CHAT_SERVER_PREFIX", "w-")
            stringBuildConfigField("CHAT_SERVER_IPV6_PREFIX", "ds.w-")
            stringBuildConfigField("CHAT_SERVER_SUFFIX", ".0.test.threema.ch")
            stringBuildConfigField("MEDIA_PATH", "ThreemaBlue")
            // This public key is pinned for the chat server protocol.
            byteArrayBuildConfigField("SERVER_PUBKEY", PublicKeys.sandboxServer)
            byteArrayBuildConfigField("SERVER_PUBKEY_ALT", PublicKeys.sandboxServer)
            stringBuildConfigField("DIRECTORY_SERVER_URL", "https://apip.test.threema.ch/")
            stringBuildConfigField("DIRECTORY_SERVER_IPV6_URL", "https://ds-apip.test.threema.ch/")
            stringBuildConfigField("WORK_SERVER_URL", "https://apip-work.test.threema.ch/")
            stringBuildConfigField("WORK_SERVER_IPV6_URL", "https://ds-apip-work.test.threema.ch/")
            stringBuildConfigField("MEDIATOR_SERVER_URL", "wss://mediator-{deviceGroupIdPrefix4}.test.threema.ch/{deviceGroupIdPrefix8}")
            stringBuildConfigField("BLOB_SERVER_URL", "https://blobp-{blobIdPrefix}.test.threema.ch")
            stringBuildConfigField("BLOB_SERVER_IPV6_URL", "https://ds-blobp-{blobIdPrefix}.test.threema.ch")
            stringBuildConfigField("BLOB_SERVER_URL_UPLOAD", "https://blobp-upload.test.threema.ch/upload")
            stringBuildConfigField("BLOB_SERVER_IPV6_URL_UPLOAD", "https://ds-blobp-upload.test.threema.ch/upload")
            stringBuildConfigField("AVATAR_FETCH_URL", "https://avatar.test.threema.ch/")
            stringBuildConfigField("APP_RATING_URL", "https://test.threema.com/app-rating/android-work/{rating}")
            stringBuildConfigField("MAP_STYLES_URL", "https://map.test.threema.ch/styles/threema/style.json")
            stringBuildConfigField("MAP_POI_AROUND_URL", "https://poi.test.threema.ch/around/{latitude}/{longitude}/{radius}/")
            stringBuildConfigField("MAP_POI_NAMES_URL", "https://poi.test.threema.ch/names/{latitude}/{longitude}/{query}/")
            stringBuildConfigField("LOG_TAG", "3mablue")
            stringBuildConfigField("BLOB_MIRROR_SERVER_URL", "https://blob-mirror-{deviceGroupIdPrefix4}.test.threema.ch/{deviceGroupIdPrefix8}")

            booleanBuildConfigField("CRASH_REPORTING_SUPPORTED", true)

            // config fields for action URLs / deep links
            stringBuildConfigField("uriScheme", "threemablue")
            stringBuildConfigField("actionUrl", "blue.threema.ch")

            with(manifestPlaceholders) {
                put("uriScheme", "threemablue")
                put("actionUrl", "blue.threema.ch")
                put("callMimeType", "vnd.android.cursor.item/vnd.ch.threema.app.blue.call")
            }
        }
        create("hms") {
            applicationId = "ch.threema.app.hms"
        }
        create("hms_work") {
            versionName = "${appVersion}k$betaSuffix"
            applicationId = "ch.threema.app.work.hms"
            testApplicationId = "ch.threema.app.work.test.hms"
            setProductNames(appName = "Threema Work")
            stringResValue("package_name", "ch.threema.app.work")
            stringResValue("contacts_mime_type", "vnd.android.cursor.item/vnd.ch.threema.app.work.profile")
            stringResValue("call_mime_type", "vnd.android.cursor.item/vnd.ch.threema.app.work.call")
            stringBuildConfigField("CHAT_SERVER_PREFIX", "w-")
            stringBuildConfigField("CHAT_SERVER_IPV6_PREFIX", "ds.w-")
            stringBuildConfigField("MEDIA_PATH", "ThreemaWork")
            stringBuildConfigField("WORK_SERVER_URL", "https://apip-work.threema.ch/")
            stringBuildConfigField("WORK_SERVER_IPV6_URL", "https://ds-apip-work.threema.ch/")
            stringBuildConfigField("APP_RATING_URL", "https://threema.com/app-rating/android-work/{rating}")
            stringBuildConfigField("LOG_TAG", "3mawrk")
            stringBuildConfigField("DEFAULT_APP_THEME", "2")

            // config fields for action URLs / deep links
            stringBuildConfigField("uriScheme", "threemawork")
            stringBuildConfigField("actionUrl", "work.threema.ch")

            with(manifestPlaceholders) {
                put("uriScheme", "threemawork")
                put("actionUrl", "work.threema.ch")
                put("callMimeType", "vnd.android.cursor.item/vnd.ch.threema.app.work.call")
            }
        }
        create("libre") {
            versionName = "${appVersion}l$betaSuffix"
            applicationId = "ch.threema.app.libre"
            testApplicationId = "$applicationId.test"
            stringResValue("package_name", applicationId!!)
            setProductNames(
                appName = "Threema Libre",
                appNameDesktop = "Threema",
            )
            stringBuildConfigField("MEDIA_PATH", "ThreemaLibre")
        }
    }

    signingConfigs {
        // Debug config
        keystores["debug"]
            ?.let { keystore ->
                getByName("debug") {
                    storeFile = keystore.storeFile
                }
            }
            ?: run {
                logger.warn("No debug keystore found. Falling back to locally generated keystore.")
            }

        // Release config
        keystores["release"]
            ?.let { keystore ->
                create("release") {
                    apply(keystore)
                }
            }
            ?: run {
                logger.warn("No release keystore found. Falling back to locally generated keystore.")
            }

        // Release config
        keystores["hms_release"]
            ?.let { keystore ->
                create("hms_release") {
                    apply(keystore)
                }
            }
            ?: run {
                logger.warn("No hms keystore found. Falling back to locally generated keystore.")
            }

        // Onprem release config
        keystores["onprem_release"]
            ?.let { keystore ->
                create("onprem_release") {
                    apply(keystore)
                }
            }
            ?: run {
                logger.warn("No onprem keystore found. Falling back to locally generated keystore.")
            }

        // Blue release config
        keystores["blue_release"]
            ?.let { keystore ->
                create("blue_release") {
                    apply(keystore)
                }
            }
            ?: run {
                logger.warn("No blue keystore found. Falling back to locally generated keystore.")
            }

        // Note: Libre release is signed with HSM, no config here
    }

    sourceSets {
        getByName("main") {
            assets.srcDirs("assets")
            jniLibs.srcDirs("libs")
            res.srcDir("src/main/res-rendezvous")
            java.srcDir("./build/generated/source/protobuf/main/java")
            java.srcDir("./build/generated/source/protobuf/main/kotlin")
        }

        // Based on Google services
        getByName("none") {
            java.srcDir("src/google_services_based/java")
        }
        getByName("store_google") {
            java.srcDir("src/google_services_based/java")
        }
        getByName("store_google_work") {
            java.srcDir("src/google_services_based/java")
        }
        getByName("store_threema") {
            java.srcDir("src/google_services_based/java")
        }
        getByName("libre") {
            assets.srcDirs("src/foss_based/assets")
            java.srcDir("src/foss_based/java")
        }
        getByName("onprem") {
            java.srcDir("src/google_services_based/java")
        }
        getByName("green") {
            java.srcDir("src/google_services_based/java")
            manifest.srcFile("src/store_google/AndroidManifest.xml")
        }
        getByName("sandbox_work") {
            java.srcDir("src/google_services_based/java")
            res.srcDir("src/store_google_work/res")
            manifest.srcFile("src/store_google_work/AndroidManifest.xml")
        }
        getByName("blue") {
            java.srcDir("src/google_services_based/java")
            res.srcDir("src/blue/res")
        }

        // Based on Huawei services
        getByName("hms") {
            java.srcDir("src/hms_services_based/java")
        }
        getByName("hms_work") {
            java.srcDir("src/hms_services_based/java")
            res.srcDir("src/store_google_work/res")
        }

        // FOSS, no proprietary services
        getByName("libre") {
            assets.srcDirs("src/foss_based/assets")
            java.srcDir("src/foss_based/java")
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            ndk {
                debugSymbolLevel = "FULL"
            }
            enableUnitTestCoverage = false
            enableAndroidTestCoverage = false

            if (keystores["debug"] != null) {
                signingConfig = signingConfigs["debug"]
            }
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = false // Caused inconsistencies between local and CI builds
            vcsInfo.include = false // For reproducible builds independent from git history
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-project.txt")
            ndk {
                debugSymbolLevel = "FULL" // 'SYMBOL_TABLE'
            }

            if (keystores["release"] != null) {
                val releaseSigningConfig = signingConfigs["release"]
                productFlavors["store_google"].signingConfig = releaseSigningConfig
                productFlavors["store_google_work"].signingConfig = releaseSigningConfig
                productFlavors["store_threema"].signingConfig = releaseSigningConfig
                productFlavors["green"].signingConfig = releaseSigningConfig
                productFlavors["sandbox_work"].signingConfig = releaseSigningConfig
                productFlavors["none"].signingConfig = releaseSigningConfig
            }

            if (keystores["hms_release"] != null) {
                val hmsReleaseSigningConfig = signingConfigs["hms_release"]
                productFlavors["hms"].signingConfig = hmsReleaseSigningConfig
                productFlavors["hms_work"].signingConfig = hmsReleaseSigningConfig
            }

            if (keystores["onprem_release"] != null) {
                productFlavors["onprem"].signingConfig = signingConfigs["onprem_release"]
            }

            if (keystores["blue_release"] != null) {
                productFlavors["blue"].signingConfig = signingConfigs["blue_release"]
            }

            // Note: Libre release is signed with HSM, no config here
        }
    }

    packaging {
        jniLibs {
            // replacement for extractNativeLibs in AndroidManifest
            useLegacyPackaging = true
        }
        resources {
            excludes.addAll(
                setOf(
                    "META-INF/DEPENDENCIES.txt",
                    "META-INF/LICENSE.txt",
                    "META-INF/LICENSE.md",
                    "META-INF/LICENSE-notice.md",
                    "META-INF/NOTICE.txt",
                    "META-INF/NOTICE",
                    "META-INF/LICENSE",
                    "META-INF/DEPENDENCIES",
                    "META-INF/notice.txt",
                    "META-INF/license.txt",
                    "META-INF/dependencies.txt",
                    "META-INF/LGPL2.1",
                    "**/*.proto",
                    "DebugProbesKt.bin",
                ),
            )
        }
    }

    testOptions {
        // Disable animations in instrumentation tests
        animationsDisabled = true

        unitTests {
            all { test ->
                test.outputs.upToDateWhen { false }
                test.testLogging {
                    events("passed", "skipped", "failed", "standardOut", "standardError")
                    exceptionFormat = TestExceptionFormat.FULL
                }

                test.jvmArgs = test.jvmArgs!! + listOf(
                    "--add-opens=java.base/java.util=ALL-UNNAMED",
                    "--add-opens=java.base/java.util.stream=ALL-UNNAMED",
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "-Xmx4096m",
                )
            }
            // By default, local unit tests throw an exception any time the code you are testing tries to access
            // Android platform APIs (unless you mock Android dependencies yourself or with a testing
            // framework like Mockk). However, you can enable the following property so that the test
            // returns either null or zero when accessing platform APIs, rather than throwing an exception.
            isReturnDefaultValues = true
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    kotlin {
        jvmToolchain(17)
    }

    androidResources {
        noCompress.add("png")
    }

    lint {
        // if true, stop the gradle build if errors are found
        abortOnError = true
        // if true, check all issues, including those that are off by default
        checkAllWarnings = true
        // check dependencies
        checkDependencies = true
        // set to true to have all release builds run lint on issues with severity=fatal
        // and abort the build (controlled by abortOnError above) if fatal issues are found
        checkReleaseBuilds = true
        // turn off checking the given issue id's
        disable.addAll(setOf("TypographyFractions", "TypographyQuotes", "RtlHardcoded", "RtlCompat", "RtlEnabled"))
        // Set the severity of the given issues to error
        error.addAll(setOf("Wakelock", "TextViewEdits", "ResourceAsColor"))
        // Set the severity of the given issues to fatal (which means they will be
        // checked during release builds (even if the lint target is not included)
        fatal.addAll(setOf("NewApi", "InlinedApi", "LoggerName"))
        ignoreWarnings = false
        // if true, don't include source code lines in the error output
        noLines = false
        // if true, show all locations for an error, do not truncate lists, etc.
        showAll = true
        // Set the severity of the given issues to warning
        warning.add("MissingTranslation")
        // if true, treat all warnings as errors
        warningsAsErrors = false
        // file to write report to (if not specified, defaults to lint-results.xml)
        xmlOutput = file("lint-report.xml")
        // if true, generate an XML report for use by for example Jenkins
        xmlReport = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

composeCompiler {
    includeSourceInformation = true
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("stability_config.conf"))
}

// Only build relevant buildType / flavor combinations
androidComponents {
    beforeVariants { variant ->
        val name = variant.name
        if (variant.buildType == "release" && ("green" in name || "sandbox_work" in name)) {
            variant.enable = false
        }
    }
}

dependencies {
    configurations.all {
        // Prefer modules that are part of this build (multi-project or composite build)
        // over external modules
        resolutionStrategy.preferProjectModules()

        // Alternatively, we can fail eagerly on version conflict to see the conflicts
        // resolutionStrategy.failOnVersionConflict()
    }

    coreLibraryDesugaring(libs.desugarJdkLibs)

    implementation(project(":domain"))
    implementation(project("::commonAndroid"))
    implementation(project(":common"))
    lintChecks(project(":lint-rules"))

    // Dependency Injection
    implementation(libs.koin.android)
    implementation(libs.koin.androidCompat)
    implementation(libs.koin.compose)
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit4)

    implementation(libs.sqlcipher.android)

    implementation(libs.subsamplingScaleImageView)
    implementation(libs.opencsv)
    implementation(libs.zip4j)
    implementation(libs.taptargetview)
    implementation(libs.commonsIo)
    implementation(libs.slf4j.api)
    implementation(libs.androidImageCropper)
    implementation(libs.fastscroll)
    implementation(libs.ezVcard)
    implementation(libs.gestureViews)

    // AndroidX / Jetpack support libraries
    implementation(libs.androidx.preference)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.palette)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.sqlite)
    implementation(libs.androidx.concurrent.futures)
    implementation(libs.androidx.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.media)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.commonJava8)
    implementation(libs.androidx.lifecycle.extensions)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.sharetarget)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.window)
    implementation(libs.androidx.splashscreen)
    ksp(libs.androidx.room.compiler)

    // Jetpack Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.material3)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.androidx.ui.tooling)
    androidTestImplementation(platform(libs.compose.bom))

    implementation(libs.bcprov.jdk15to18)

    implementation(libs.material)
    implementation(libs.zxing)
    implementation(libs.libphonenumber)

    // webclient dependencies
    implementation(libs.msgpack.core)
    implementation(libs.jackson.core)
    implementation(libs.nvWebsocket.client)

    implementation(libs.streamsupport.cfuture)

    implementation(libs.saltyrtc.client) {
        exclude(group = "org.json")
    }

    implementation(libs.chunkedDc)
    implementation(libs.webrtcAndroid)
    implementation(libs.saltyrtc.taskWebrtc) {
        exclude(module = "saltyrtc-client")
    }

    // Glide components
    implementation(libs.glide)
    ksp(libs.glide.ksp)

    // Kotlin
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test)
    androidTestImplementation(libs.kotlin.test)

    // use leak canary in debug builds if requested
    if (project.hasProperty("leakCanary")) {
        debugImplementation(libs.leakcanary)
    }

    // test dependencies
    testImplementation(libs.junit)
    testImplementation(testFixtures(project(":domain")))

    // custom test helpers, shared between unit test and android tests
    testImplementation(project(":test-helpers"))
    androidTestImplementation(project(":test-helpers"))

    testImplementation(libs.mockk)
    androidTestImplementation(libs.mockkAndroid)

    // add JSON support to tests without mocking
    testImplementation(libs.json)

    testImplementation(libs.archunit.junit4)

    androidTestImplementation(testFixtures(project(":domain")))
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.fastlane.screengrab) {
        exclude(group = "androidx.annotation", module = "annotation")
    }
    androidTestImplementation(libs.androidx.espresso.core) {
        exclude(group = "androidx.annotation", module = "annotation")
    }
    androidTestImplementation(libs.androidx.test.runner) {
        exclude(group = "androidx.annotation", module = "annotation")
    }
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.contrib) {
        exclude(group = "androidx.annotation", module = "annotation")
        exclude(group = "androidx.appcompat", module = "appcompat")
        exclude(group = "androidx.legacy", module = "legacy-support-v4")
        exclude(group = "com.google.android.material", module = "material")
        exclude(group = "androidx.recyclerview", module = "recyclerview")
        exclude(group = "org.checkerframework", module = "checker")
        exclude(module = "protobuf-lite")
    }
    androidTestImplementation(libs.androidx.espresso.intents) {
        exclude(group = "androidx.annotation", module = "annotation")
    }
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.coroutines.test)

    // Google Play Services and related libraries
    "noneImplementation"(libs.playServices.base)
    "store_googleImplementation"(libs.playServices.base)
    "store_google_workImplementation"(libs.playServices.base)
    "store_threemaImplementation"(libs.playServices.base)
    "onpremImplementation"(libs.playServices.base)
    "greenImplementation"(libs.playServices.base)
    "sandbox_workImplementation"(libs.playServices.base)
    "blueImplementation"(libs.playServices.base)

    fun ExternalModuleDependency.excludeFirebaseDependencies() {
        exclude(group = "com.google.firebase", module = "firebase-core")
        exclude(group = "com.google.firebase", module = "firebase-analytics")
        exclude(group = "com.google.firebase", module = "firebase-measurement-connector")
    }
    "noneImplementation"(libs.firebase.messaging) { excludeFirebaseDependencies() }
    "store_googleImplementation"(libs.firebase.messaging) { excludeFirebaseDependencies() }
    "store_google_workImplementation"(libs.firebase.messaging) { excludeFirebaseDependencies() }
    "store_threemaImplementation"(libs.firebase.messaging) { excludeFirebaseDependencies() }
    "onpremImplementation"(libs.firebase.messaging) { excludeFirebaseDependencies() }
    "greenImplementation"(libs.firebase.messaging) { excludeFirebaseDependencies() }
    "sandbox_workImplementation"(libs.firebase.messaging) { excludeFirebaseDependencies() }
    "blueImplementation"(libs.firebase.messaging) { excludeFirebaseDependencies() }

    // Google Assistant Voice Action verification library
    "noneImplementation"(group = "", name = "libgsaverification-client", ext = "aar")
    "store_googleImplementation"(group = "", name = "libgsaverification-client", ext = "aar")
    "store_google_workImplementation"(group = "", name = "libgsaverification-client", ext = "aar")
    "onpremImplementation"(group = "", name = "libgsaverification-client", ext = "aar")
    "store_threemaImplementation"(group = "", name = "libgsaverification-client", ext = "aar")
    "greenImplementation"(group = "", name = "libgsaverification-client", ext = "aar")
    "sandbox_workImplementation"(group = "", name = "libgsaverification-client", ext = "aar")
    "blueImplementation"(group = "", name = "libgsaverification-client", ext = "aar")

    // Maplibre (may have transitive dependencies on Google location services)
    "noneImplementation"(libs.maplibre)
    "store_googleImplementation"(libs.maplibre)
    "store_google_workImplementation"(libs.maplibre)
    "store_threemaImplementation"(libs.maplibre)
    "libreImplementation"(libs.maplibre) {
        exclude(group = "com.google.android.gms")
    }
    "onpremImplementation"(libs.maplibre)
    "greenImplementation"(libs.maplibre)
    "sandbox_workImplementation"(libs.maplibre)
    "blueImplementation"(libs.maplibre)
    "hmsImplementation"(libs.maplibre)
    "hms_workImplementation"(libs.maplibre)

    // Huawei related libraries (only for hms* build variants)
    // Exclude agconnect dependency, we'll replace it with the vendored version below
    "hmsImplementation"(libs.hmsPush) {
        exclude(group = "com.huawei.agconnect")
    }
    "hms_workImplementation"(libs.hmsPush) {
        exclude(group = "com.huawei.agconnect")
    }
    "hmsImplementation"(group = "", name = "agconnect-core-1.9.1.301", ext = "aar")
    "hms_workImplementation"(group = "", name = "agconnect-core-1.9.1.301", ext = "aar")
}

// Define the cargo attributes. These will be used by the rust-android plugin that will create the
// 'cargoBuild' task that builds native libraries that will be added to the apk. Note that the
// kotlin bindings are created in the domain module. Building native libraries with rust-android
// cannot be done in any other module than 'app'.
cargo {
    prebuiltToolchains = true
    targetDirectory = "$projectDir/build/generated/source/libthreema"
    module = "$projectDir/../domain/libthreema" // must contain Cargo.toml
    libname = "libthreema" // must match the Cargo.toml's package name
    profile = "release"
    pythonCommand = "python3"
    targets = listOf("x86_64", "arm64", "arm", "x86")
    features {
        defaultAnd(arrayOf("uniffi"))
    }
    extraCargoBuildArguments = listOf("--lib", "--target-dir", "$projectDir/build/generated/source/libthreema", "--locked")
    verbose = false
}

afterEvaluate {
    // The `cargoBuild` task isn't available until after evaluation.
    android.applicationVariants.configureEach {
        val variantName = name.replaceFirstChar { it.uppercase() }
        // Set the dependency so that cargoBuild is executed before the native libs are merged
        tasks["merge${variantName}NativeLibs"].dependsOn(tasks["cargoBuild"])
    }
}

sonarqube {
    properties {
        property(
            "sonar.sources",
            listOf(
                "src/main/",
                "../scripts/",
                "../scripts-internal/",
            )
                .joinToString(separator = ", "),
        )
        property(
            "sonar.exclusions",
            listOf(
                "src/**/res/",
                "src/**/res-rendezvous/",
                "**/emojis/EmojiParser.kt",
                "**/emojis/EmojiSpritemap.kt",
            )
                .joinToString(separator = ", "),
        )
        property("sonar.tests", "src/test/")
        property("sonar.sourceEncoding", "UTF-8")
        property("sonar.verbose", "true")
        property("sonar.projectKey", "android-client")
        property("sonar.projectName", "Threema for Android")
    }
}

androidStem {
    includeLocalizedOnlyTemplates = true
}

tasks.register<Exec>("compileProto") {
    group = "build"
    description = "generate class bindings from protobuf files in the 'protobuf' directory"
    workingDir(project.projectDir)
    commandLine("./compile-proto.sh")
}
project.tasks.preBuild.dependsOn("compileProto")

tasks.withType<Test> {
    // Necessary to load the dynamic libthreema library in unit tests
    systemProperty("jna.library.path", "${project.projectDir}/../domain/libthreema/target/release")
}

// Set up Gradle tasks to fetch screenshots on UI test failures
// See https://medium.com/stepstone-tech/how-to-capture-screenshots-for-failed-ui-tests-9927eea6e1e4
val reportsDirectory = "${layout.buildDirectory}/reports/androidTests/connected"
val screenshotsDirectory = "/sdcard/testfailures/screenshots/"
val clearScreenshotsTask = tasks.register<Exec>("clearScreenshots") {
    executable = android.adbExecutable.toString()
    args("shell", "rm", "-r", screenshotsDirectory)
}
val createScreenshotsDirectoryTask = tasks.register<Exec>("createScreenshotsDirectory") {
    group = "reporting"
    executable = android.adbExecutable.toString()
    args("shell", "mkdir", "-p", screenshotsDirectory)
}
val fetchScreenshotsTask = tasks.register<Exec>("fetchScreenshots") {
    group = "reporting"
    executable = android.adbExecutable.toString()
    args("pull", "$screenshotsDirectory.", reportsDirectory)
    finalizedBy(clearScreenshotsTask)
    dependsOn(createScreenshotsDirectoryTask)
    doFirst {
        file(reportsDirectory).mkdirs()
    }
}
tasks.whenTaskAdded {
    if (name == "connectedDebugAndroidTest") {
        finalizedBy(fetchScreenshotsTask)
    }
}

// Let the compose compiler generate stability reports
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        val composeCompilerReportsPath = "${project.layout.buildDirectory.get().dir("compose_conpiler").asFile.absolutePath}/reports"
        freeCompilerArgs.addAll(
            listOf(
                "-P",
                "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=$composeCompilerReportsPath",
            ),
        )
    }
}

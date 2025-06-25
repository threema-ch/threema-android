/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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

package utils

import com.android.build.api.dsl.ApkSigningConfig
import java.util.Properties
import java.io.File

/**
 * Look up the keystore with the specified name in a `keystore` directory
 * adjacent to this project directory. If it exists, return a signing config.
 * Otherwise, return null.
 */
fun findKeystore(projectDir: File, name: String): KeystoreConfig? {
    val basePath = "${projectDir.absolutePath}/../../keystore"
    val storePath = "$basePath/$name.keystore"
    val storeFile = File(storePath)
    if (!storeFile.isFile) {
        return null
    }

    val propertiesPath = "$basePath/$name.properties"
    val propertiesFile = File(propertiesPath)
    val props = if (propertiesFile.isFile) {
        readPropertiesFile(propertiesFile)
    } else {
        null
    }

    return KeystoreConfig(
        storeFile = storeFile,
        storePassword = props?.getProperty("storePassword"),
        keyAlias = props?.getProperty("keyAlias"),
        keyPassword = props?.getProperty("keyPassword"),
    )
}

private fun readPropertiesFile(propertiesFile: File): Properties =
    Properties()
        .apply {
            propertiesFile.inputStream().use { inStream ->
                load(inStream)
            }
        }

fun ApkSigningConfig.apply(keystore: KeystoreConfig) {
    storeFile = keystore.storeFile
    storePassword = keystore.storePassword
    keyAlias = keystore.keyAlias
    keyPassword = keystore.keyPassword
}

data class KeystoreConfig(
    val storeFile: File,
    val storePassword: String?,
    val keyAlias: String?,
    val keyPassword: String?,
)

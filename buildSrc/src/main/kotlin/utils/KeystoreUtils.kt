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

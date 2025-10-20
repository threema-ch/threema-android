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

package ch.threema.app

import android.content.Context
import android.os.Build
import ch.threema.app.onprem.OnPremCertPinning
import ch.threema.app.restrictions.AppRestrictionUtil
import ch.threema.app.services.ServiceManagerProvider
import ch.threema.app.stores.EncryptedPreferenceStoreImpl
import ch.threema.app.stores.PreferenceStoreImpl
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.FileUtil
import ch.threema.base.ThreemaException
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.libthreema.LibthreemaHttpClient
import ch.threema.domain.models.WorkClientInfo
import ch.threema.domain.onprem.OnPremConfigStore
import ch.threema.localcrypto.MasterKeyFileProvider
import ch.threema.localcrypto.MasterKeyManagerImpl
import ch.threema.localcrypto.MasterKeyStorageManager
import ch.threema.localcrypto.NoOpRemoteSecretManagerImpl
import ch.threema.localcrypto.RemoteSecretClient
import ch.threema.localcrypto.RemoteSecretManagerImpl
import ch.threema.localcrypto.RemoteSecretMonitor
import ch.threema.localcrypto.Version1MasterKeyFileManager
import ch.threema.localcrypto.Version2MasterKeyFileManager
import ch.threema.localcrypto.Version2MasterKeyStorageDecoder
import ch.threema.localcrypto.Version2MasterKeyStorageEncoder
import ch.threema.storage.DatabaseNonceStore
import ch.threema.storage.DatabaseService
import ch.threema.storage.SQLDHSessionStore
import java.io.IOException
import java.util.Locale
import okhttp3.OkHttpClient

private val logger = LoggingUtil.getThreemaLogger("MasterKeyManagerFactory")

object MasterKeyManagerFactory {
    fun createMasterKeyManager(
        context: Context,
        baseOkHttpClient: OkHttpClient,
        onPremConfigStore: OnPremConfigStore?,
        serviceManagerProvider: ServiceManagerProvider,
    ): MasterKeyManagerImpl {
        val version1MasterKeyFile = MasterKeyFileProvider.getVersion1MasterKeyFile(context)
        val version2MasterKeyFile = MasterKeyFileProvider.getVersion2MasterKeyFile(context)
        val masterKeyStorageEncoder = Version2MasterKeyStorageEncoder()
        val masterKeyStorageDecoder = Version2MasterKeyStorageDecoder()
        val masterKeyStorageManager = MasterKeyStorageManager(
            version2KeyFileManager = Version2MasterKeyFileManager(version2MasterKeyFile, masterKeyStorageEncoder, masterKeyStorageDecoder),
            version1KeyFileManager = Version1MasterKeyFileManager(version1MasterKeyFile),
        )
        if (!masterKeyStorageManager.keyExists()) {
            onMasterKeyNotFound(context)
        }
        return MasterKeyManagerImpl(
            keyStorageManager = masterKeyStorageManager,
            remoteSecretManager = if (ConfigUtils.isRemoteSecretsSupported()) {
                val remoteSecretClient = RemoteSecretClient(
                    clientInfo = getClientInfo(),
                    httpClientWithOnPremCertPinning = LibthreemaHttpClient(
                        okHttpClient = getOkHttpClientWithCertificatePinning(
                            baseOkHttpClient = baseOkHttpClient,
                            onPremConfigStore = onPremConfigStore,
                            serviceManagerProvider = serviceManagerProvider,
                        ),
                    ),
                    httpClientWithoutOnPremCertPinning = LibthreemaHttpClient(
                        okHttpClient = baseOkHttpClient,
                    ),
                )
                RemoteSecretManagerImpl(
                    remoteSecretClient = remoteSecretClient,
                    shouldUseRemoteSecretProtection = {
                        AppRestrictionUtil.shouldEnableRemoteSecret(context)
                    },
                    remoteSecretMonitor = RemoteSecretMonitor(
                        remoteSecretClient = remoteSecretClient,
                    ),
                    getWorkServerBaseUrl = {
                        getWorkServerBaseUrl(onPremConfigStore)
                    },
                )
            } else {
                NoOpRemoteSecretManagerImpl()
            },
        )
    }

    private fun getOkHttpClientWithCertificatePinning(
        baseOkHttpClient: OkHttpClient,
        onPremConfigStore: OnPremConfigStore?,
        serviceManagerProvider: ServiceManagerProvider,
    ): OkHttpClient =
        if (onPremConfigStore != null) {
            OnPremCertPinning.createClientWithCertPinning(
                baseClient = baseOkHttpClient,
                getOnPremConfigDomains = {
                    val config = onPremConfigStore.get()
                        ?: run {
                            logger.warn("No stored OPPF found, trying to fetch it")
                            val serviceManager = serviceManagerProvider.getServiceManagerOrNull()
                                ?: throw IOException("cannot enforce certificate pinning, no service manager available to fetch OPPF")
                            try {
                                serviceManager.onPremConfigFetcherProvider.getOnPremConfigFetcher().fetch()
                            } catch (e: ThreemaException) {
                                throw IOException("cannot enforce certificate pinning, failed to fetch OPPF", e)
                            }
                        }
                    config.domains
                },
            )
        } else {
            baseOkHttpClient
        }

    private fun getWorkServerBaseUrl(onPremConfigStore: OnPremConfigStore?): String =
        onPremConfigStore?.get()?.work?.url
            ?: throw IOException("cannot monitor remote secret, no stored OPPF found")

    private fun getClientInfo(): WorkClientInfo =
        WorkClientInfo(
            appVersion = ConfigUtils.getAppVersion(),
            appLocale = Locale.getDefault().toString(),
            deviceModel = Build.MODEL,
            osVersion = Build.VERSION.RELEASE,
            workFlavor = when {
                BuildFlavor.current.isOnPrem -> WorkClientInfo.WorkFlavor.ON_PREM
                BuildFlavor.current.isWork -> WorkClientInfo.WorkFlavor.WORK
                else -> error("Not a work build")
            },
        )

    private fun onMasterKeyNotFound(context: Context) {
        // If the MasterKey does not exist, remove every file that is encrypted with this non-existing MasterKey
        logger.warn("master key is missing or does not match, deleting DB and preferences")
        deleteDatabaseFiles(context)
        deleteAllPreferences(context)
    }

    private fun deleteDatabaseFiles(context: Context) {
        val defaultDatabaseFile = DatabaseService.getDatabaseFile(context)
        if (defaultDatabaseFile.exists()) {
            val databaseBackup = DatabaseService.getDatabaseBackupFile(context)
            if (!defaultDatabaseFile.renameTo(databaseBackup)) {
                FileUtil.deleteFileOrWarn(defaultDatabaseFile, "threema4 database", logger)
            }
        }

        val nonceDatabaseFile = DatabaseNonceStore.getDatabaseFile(context)
        if (nonceDatabaseFile.exists()) {
            FileUtil.deleteFileOrWarn(nonceDatabaseFile, "nonce4 database", logger)
        }

        val sqldhSessionDatabaseFile = context.getDatabasePath(SQLDHSessionStore.DATABASE_NAME)
        if (sqldhSessionDatabaseFile.exists()) {
            FileUtil.deleteFileOrWarn(sqldhSessionDatabaseFile, "sql dh session database", logger)
        }
    }

    private fun deleteAllPreferences(context: Context) {
        PreferenceStoreImpl(context).clear()
        EncryptedPreferenceStoreImpl.clear(context)
    }
}

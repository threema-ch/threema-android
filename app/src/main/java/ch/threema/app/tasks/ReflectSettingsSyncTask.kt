/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.app.tasks

import ch.threema.app.managers.ServiceManager
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.services.BlockedIdentitiesService
import ch.threema.app.services.PreferenceService
import ch.threema.base.crypto.NonceFactory
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TRANSACTION_TTL_MAX
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.createTransaction
import ch.threema.domain.taskmanager.getEncryptedSettingsSyncUpdate
import ch.threema.protobuf.d2d.MdD2D.TransactionScope
import ch.threema.protobuf.d2d.sync.MdD2DSync.ReadReceiptPolicy
import ch.threema.protobuf.d2d.sync.MdD2DSync.Settings
import ch.threema.protobuf.d2d.sync.MdD2DSync.Settings.UnknownContactPolicy
import ch.threema.protobuf.d2d.sync.MdD2DSync.TypingIndicatorPolicy
import ch.threema.protobuf.d2d.sync.settings
import ch.threema.protobuf.identities
import kotlinx.serialization.Serializable

/**
 * Note that the setting reflection tasks currently deviate from the protocol specification. We
 * first apply the setting locally and schedule a persistent task afterwards that reflects the
 * currently applied setting. This is due to the preference system mechanism in android that does
 * not allow us to delay the change of settings in the UI by waiting for network communication.
 */
abstract class ReflectSettingsSyncTask(
    protected val multiDeviceManager: MultiDeviceManager,
    private val nonceFactory: NonceFactory,
) : ActiveTask<Unit> {
    private val mdProperties by lazy { multiDeviceManager.propertiesProvider.get() }

    protected abstract fun getSettings(): Settings

    override suspend fun invoke(handle: ActiveTaskCodec) {
        check(multiDeviceManager.isMultiDeviceActive) { "Cannot sync settings when md is disabled" }

        handle.createTransaction(
            keys = mdProperties.keys,
            scope = TransactionScope.Scope.SETTINGS_SYNC,
            ttl = TRANSACTION_TTL_MAX,
            precondition = { true },
        ).execute {
            val encryptedEnvelopeResult = getEncryptedSettingsSyncUpdate(
                settings = getSettings(),
                multiDeviceProperties = mdProperties,
            )

            handle.reflectAndAwaitAck(
                encryptedEnvelopeResult = encryptedEnvelopeResult,
                storeD2dNonce = true,
                nonceFactory = nonceFactory,
            )
        }
    }

    /**
     * Reflect a settings sync update that contains the result of applying the provided [settingsCreators].
     *
     * Note that this task must be used synchronously as it won't be persisted. It is the scheduler's responsibility to check that it has run to
     * completion.
     */
    class ReflectMultipleSettingsSyncUpdate(
        multiDeviceManager: MultiDeviceManager,
        nonceFactory: NonceFactory,
        private val settingsCreators: List<(Settings.Builder) -> Unit>,
    ) : ReflectSettingsSyncTask(
        multiDeviceManager = multiDeviceManager,
        nonceFactory = nonceFactory,
    ) {
        override val type = "ReflectMultipleSettingsSyncUpdate"

        override fun getSettings(): Settings {
            val settingsBuilder = Settings.newBuilder()

            settingsCreators.forEach { settingsCreator ->
                settingsCreator.invoke(settingsBuilder)
            }

            return settingsBuilder.build()
        }
    }

    class ReflectUnknownContactPolicySyncUpdate(
        multiDeviceManager: MultiDeviceManager,
        nonceFactory: NonceFactory,
        private val preferenceService: PreferenceService,
    ) : ReflectSettingsSyncTask(
        multiDeviceManager = multiDeviceManager,
        nonceFactory = nonceFactory,
    ),
        PersistableTask {
        override val type = "ReflectUnknownContactPolicySyncUpdate"

        override fun getSettings(): Settings = settings {
            unknownContactPolicy = preferenceService.getUnknownContactPolicy()
        }

        private fun PreferenceService.getUnknownContactPolicy(): UnknownContactPolicy =
            if (isBlockUnknown) {
                UnknownContactPolicy.BLOCK_UNKNOWN
            } else {
                UnknownContactPolicy.ALLOW_UNKNOWN
            }

        override fun serialize(): SerializableTaskData = ReflectUnknownContactPolicySyncUpdateData

        @Serializable
        data object ReflectUnknownContactPolicySyncUpdateData : SerializableTaskData {
            override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
                ReflectUnknownContactPolicySyncUpdate(
                    multiDeviceManager = serviceManager.multiDeviceManager,
                    nonceFactory = serviceManager.nonceFactory,
                    preferenceService = serviceManager.preferenceService,
                )
        }
    }

    class ReflectReadReceiptPolicySyncUpdate(
        multiDeviceManager: MultiDeviceManager,
        nonceFactory: NonceFactory,
        private val preferenceService: PreferenceService,
    ) : ReflectSettingsSyncTask(
        multiDeviceManager = multiDeviceManager,
        nonceFactory = nonceFactory,
    ),
        PersistableTask {
        override val type = "ReflectReadReceiptPolicySyncUpdate"

        override fun getSettings(): Settings = settings {
            readReceiptPolicy = preferenceService.getReadReceiptPolicy()
        }

        private fun PreferenceService.getReadReceiptPolicy(): ReadReceiptPolicy =
            if (isReadReceipts) {
                ReadReceiptPolicy.SEND_READ_RECEIPT
            } else {
                ReadReceiptPolicy.DONT_SEND_READ_RECEIPT
            }

        override fun serialize(): SerializableTaskData = ReadReceiptPolicySyncUpdateData

        @Serializable
        data object ReadReceiptPolicySyncUpdateData : SerializableTaskData {
            override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
                ReflectReadReceiptPolicySyncUpdate(
                    multiDeviceManager = serviceManager.multiDeviceManager,
                    nonceFactory = serviceManager.nonceFactory,
                    preferenceService = serviceManager.preferenceService,
                )
        }
    }

    class ReflectTypingIndicatorPolicySyncUpdate(
        multiDeviceManager: MultiDeviceManager,
        nonceFactory: NonceFactory,
        private val preferenceService: PreferenceService,
    ) : ReflectSettingsSyncTask(
        multiDeviceManager = multiDeviceManager,
        nonceFactory = nonceFactory,
    ),
        PersistableTask {
        override val type = "ReflectTypingIndicatorPolicySyncUpdate"

        override fun getSettings(): Settings = settings {
            typingIndicatorPolicy = preferenceService.getTypingIndicatorPolicy()
        }

        private fun PreferenceService.getTypingIndicatorPolicy(): TypingIndicatorPolicy =
            if (isTypingIndicator) {
                TypingIndicatorPolicy.SEND_TYPING_INDICATOR
            } else {
                TypingIndicatorPolicy.DONT_SEND_TYPING_INDICATOR
            }

        override fun serialize(): SerializableTaskData = ReflectTypingIndicatorPolicySyncUpdateData

        @Serializable
        data object ReflectTypingIndicatorPolicySyncUpdateData : SerializableTaskData {
            override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
                ReflectTypingIndicatorPolicySyncUpdate(
                    multiDeviceManager = serviceManager.multiDeviceManager,
                    nonceFactory = serviceManager.nonceFactory,
                    preferenceService = serviceManager.preferenceService,
                )
        }
    }

    class ReflectBlockedIdentitiesSyncUpdate(
        multiDeviceManager: MultiDeviceManager,
        nonceFactory: NonceFactory,
        private val blockedIdentitiesService: BlockedIdentitiesService,
    ) : ReflectSettingsSyncTask(
        multiDeviceManager = multiDeviceManager,
        nonceFactory = nonceFactory,
    ),
        PersistableTask {
        override val type = "ReflectBlockedIdentitiesSyncUpdate"

        override fun getSettings(): Settings = settings {
            blockedIdentities = identities {
                identities.clear()
                identities.addAll(blockedIdentitiesService.getAllBlockedIdentities())
            }
        }

        override fun serialize(): SerializableTaskData = ReflectBlockedIdentitiesSyncUpdateData

        @Serializable
        data object ReflectBlockedIdentitiesSyncUpdateData : SerializableTaskData {
            override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
                ReflectBlockedIdentitiesSyncUpdate(
                    multiDeviceManager = serviceManager.multiDeviceManager,
                    nonceFactory = serviceManager.nonceFactory,
                    blockedIdentitiesService = serviceManager.blockedIdentitiesService,
                )
        }
    }
}

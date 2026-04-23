package ch.threema.app.tasks

import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.preference.service.SynchronizedSettingsService
import ch.threema.app.services.BlockedIdentitiesService
import ch.threema.app.services.ExcludedSyncIdentitiesService
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TRANSACTION_TTL_MAX
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.createTransaction
import ch.threema.domain.taskmanager.getEncryptedSettingsSyncUpdate
import ch.threema.protobuf.common.identities
import ch.threema.protobuf.d2d.TransactionScope
import ch.threema.protobuf.d2d.sync.Settings
import ch.threema.protobuf.d2d.sync.settings
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("ReflectSettingsSyncTask")

/**
 * Note that the setting reflection tasks currently deviate from the protocol specification. We
 * first apply the setting locally and schedule a persistent task afterwards that reflects the
 * currently applied setting. This is due to the preference system mechanism in android that does
 * not allow us to delay the change of settings in the UI by waiting for network communication.
 */
abstract class ReflectSettingsSyncTask() : ActiveTask<Unit>, KoinComponent {
    protected val blockedIdentitiesService: BlockedIdentitiesService by inject()
    protected val excludedSyncIdentitiesService: ExcludedSyncIdentitiesService by inject()
    protected val multiDeviceManager: MultiDeviceManager by inject()
    private val nonceFactory: NonceFactory by inject()
    protected val preferenceService: PreferenceService by inject()
    protected val synchronizedSettingsService: SynchronizedSettingsService by inject()

    private val mdProperties by lazy { multiDeviceManager.propertiesProvider.get() }

    protected abstract fun getSettings(): Settings

    override suspend fun invoke(handle: ActiveTaskCodec) {
        if (!multiDeviceManager.isMultiDeviceActive) {
            logger.warn("Cannot reflect settings sync of type {} because multi device is not active", type)
            return
        }

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
        private val settingsCreators: List<(Settings.Builder) -> Unit>,
    ) : ReflectSettingsSyncTask() {
        override val type = "ReflectMultipleSettingsSyncUpdate"

        override fun getSettings(): Settings {
            val settingsBuilder = Settings.newBuilder()

            settingsCreators.forEach { settingsCreator ->
                settingsCreator.invoke(settingsBuilder)
            }

            return settingsBuilder.build()
        }
    }

    class ReflectContactSyncPolicySyncUpdate() : ReflectSettingsSyncTask(), PersistableTask {
        override val type = "ReflectContactSyncPolicySyncUpdate"

        override fun getSettings(): Settings = settings {
            contactSyncPolicy = synchronizedSettingsService.getContactSyncPolicySetting().getContactSyncPolicy()
        }

        override fun serialize(): SerializableTaskData = ReflectContactSyncPolicySyncUpdateData

        @Serializable
        data object ReflectContactSyncPolicySyncUpdateData : SerializableTaskData {
            override fun createTask(): Task<*, TaskCodec> =
                ReflectContactSyncPolicySyncUpdate()
        }
    }

    class ReflectUnknownContactPolicySyncUpdate() : ReflectSettingsSyncTask(), PersistableTask {
        override val type = "ReflectUnknownContactPolicySyncUpdate"

        override fun getSettings(): Settings = settings {
            unknownContactPolicy = synchronizedSettingsService.getUnknownContactPolicySetting().getUnknownContactPolicy()
        }

        override fun serialize(): SerializableTaskData = ReflectUnknownContactPolicySyncUpdateData

        @Serializable
        data object ReflectUnknownContactPolicySyncUpdateData : SerializableTaskData {
            override fun createTask(): Task<*, TaskCodec> =
                ReflectUnknownContactPolicySyncUpdate()
        }
    }

    class ReflectReadReceiptPolicySyncUpdate() : ReflectSettingsSyncTask(), PersistableTask {
        override val type = "ReflectReadReceiptPolicySyncUpdate"

        override fun getSettings(): Settings = settings {
            readReceiptPolicy = synchronizedSettingsService.getReadReceiptPolicySetting().getReadReceiptPolicy()
        }

        override fun serialize(): SerializableTaskData = ReadReceiptPolicySyncUpdateData

        @Serializable
        data object ReadReceiptPolicySyncUpdateData : SerializableTaskData {
            override fun createTask(): Task<*, TaskCodec> =
                ReflectReadReceiptPolicySyncUpdate()
        }
    }

    class ReflectTypingIndicatorPolicySyncUpdate() : ReflectSettingsSyncTask(), PersistableTask {
        override val type = "ReflectTypingIndicatorPolicySyncUpdate"

        override fun getSettings(): Settings = settings {
            typingIndicatorPolicy = synchronizedSettingsService.getTypingIndicatorPolicySetting().getTypingIndicatorPolicy()
        }

        override fun serialize(): SerializableTaskData = ReflectTypingIndicatorPolicySyncUpdateData

        @Serializable
        data object ReflectTypingIndicatorPolicySyncUpdateData : SerializableTaskData {
            override fun createTask(): Task<*, TaskCodec> =
                ReflectTypingIndicatorPolicySyncUpdate()
        }
    }

    class ReflectO2oCallPolicySyncUpdate() : ReflectSettingsSyncTask(), PersistableTask {
        override val type = "ReflectO2oCallPolicySyncUpdate"

        override fun getSettings(): Settings = settings {
            o2OCallPolicy = synchronizedSettingsService.getO2oCallPolicySetting().getO2oCallPolicy()
        }

        override fun serialize(): SerializableTaskData = ReflectO2oCallPolicySyncUpdateData

        @Serializable
        data object ReflectO2oCallPolicySyncUpdateData : SerializableTaskData {
            override fun createTask(): Task<*, TaskCodec> =
                ReflectO2oCallPolicySyncUpdate()
        }
    }

    class ReflectO2oCallConnectionPolicySyncUpdate() : ReflectSettingsSyncTask(), PersistableTask {
        override val type = "ReflectO2oCallConnectionPolicySyncUpdate"

        override fun getSettings(): Settings = settings {
            o2OCallConnectionPolicy = synchronizedSettingsService.getO2oCallConnectionPolicySetting().getO2oCallConnectionPolicy()
        }

        override fun serialize(): SerializableTaskData = ReflectO2oCallConnectionPolicySyncUpdateData

        @Serializable
        data object ReflectO2oCallConnectionPolicySyncUpdateData : SerializableTaskData {
            override fun createTask(): Task<*, TaskCodec> =
                ReflectO2oCallConnectionPolicySyncUpdate()
        }
    }

    class ReflectO2oCallVideoPolicySyncUpdate() : ReflectSettingsSyncTask(), PersistableTask {
        override val type = "ReflectO2oCallVideoPolicySyncUpdate"

        override fun getSettings(): Settings = settings {
            o2OCallVideoPolicy = synchronizedSettingsService.getO2oCallVideoPolicySetting().getO2oCallVideoPolicy()
        }

        override fun serialize(): SerializableTaskData = ReflectO2oCallVideoPolicySyncUpdateData

        @Serializable
        data object ReflectO2oCallVideoPolicySyncUpdateData : SerializableTaskData {
            override fun createTask(): Task<*, TaskCodec> =
                ReflectO2oCallVideoPolicySyncUpdate()
        }
    }

    class ReflectGroupCallPolicySyncUpdate() : ReflectSettingsSyncTask(), PersistableTask {
        override val type = "ReflectGroupCallPolicySyncUpdate"

        override fun getSettings(): Settings = settings {
            groupCallPolicy = synchronizedSettingsService.getGroupCallPolicySetting().getGroupCallPolicy()
        }

        override fun serialize(): SerializableTaskData = ReflectGroupCallPolicySyncUpdateData

        @Serializable
        data object ReflectGroupCallPolicySyncUpdateData : SerializableTaskData {
            override fun createTask(): Task<*, TaskCodec> =
                ReflectGroupCallPolicySyncUpdate()
        }
    }

    class ReflectScreenshotPolicySyncUpdate() : ReflectSettingsSyncTask(), PersistableTask {
        override val type = "ReflectScreenshotPolicySyncUpdate"

        override fun getSettings(): Settings = settings {
            screenshotPolicy = synchronizedSettingsService.getScreenshotPolicySetting().getScreenshotPolicy()
        }

        override fun serialize(): SerializableTaskData = ReflectScreenshotPolicySyncUpdateData

        @Serializable
        data object ReflectScreenshotPolicySyncUpdateData : SerializableTaskData {
            override fun createTask(): Task<*, TaskCodec> =
                ReflectScreenshotPolicySyncUpdate()
        }
    }

    class ReflectKeyboardDataCollectionPolicySyncUpdate() : ReflectSettingsSyncTask(), PersistableTask {
        override val type = "ReflectKeyboardDataCollectionPolicySyncUpdate"

        override fun getSettings(): Settings = settings {
            keyboardDataCollectionPolicy = synchronizedSettingsService.getKeyboardDataCollectionPolicySetting().getKeyboardDataCollectionPolicy()
        }

        override fun serialize(): SerializableTaskData = ReflectKeyboardDataCollectionPolicySyncUpdateData

        @Serializable
        data object ReflectKeyboardDataCollectionPolicySyncUpdateData : SerializableTaskData {
            override fun createTask(): Task<*, TaskCodec> =
                ReflectKeyboardDataCollectionPolicySyncUpdate()
        }
    }

    class ReflectBlockedIdentitiesSyncUpdate() :
        ReflectSettingsSyncTask(),
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
            override fun createTask(): Task<*, TaskCodec> =
                ReflectBlockedIdentitiesSyncUpdate()
        }
    }

    class ReflectExcludeFromSyncIdentitiesSyncUpdate() : ReflectSettingsSyncTask(), PersistableTask {
        override val type = "ReflectExcludeFromSyncIdentitiesUpdate"

        override fun getSettings(): Settings = settings {
            excludeFromSyncIdentities = identities {
                identities.clear()
                identities.addAll(excludedSyncIdentitiesService.getExcludedIdentities())
            }
        }

        override fun serialize(): SerializableTaskData = ReflectExcludeFromSyncIdentitiesSyncUpdateData

        @Serializable
        data object ReflectExcludeFromSyncIdentitiesSyncUpdateData : SerializableTaskData {
            override fun createTask(): Task<*, TaskCodec> =
                ReflectExcludeFromSyncIdentitiesSyncUpdate()
        }
    }
}

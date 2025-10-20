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
import ch.threema.app.services.ApiService
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.services.ConversationTagService
import ch.threema.app.services.FileService
import ch.threema.app.utils.ContactUtil
import ch.threema.app.utils.ConversationUtil
import ch.threema.app.utils.runtimeAssert
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.crypto.SymmetricEncryptionService
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.datatypes.NotificationTriggerPolicyOverride
import ch.threema.data.models.ContactModelData
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.protocol.blob.BlobScope
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.TransactionScope
import ch.threema.domain.taskmanager.getEncryptedContactSyncUpdate
import ch.threema.domain.types.Identity
import ch.threema.protobuf.Common
import ch.threema.protobuf.blob
import ch.threema.protobuf.d2d.sync.ContactKt.NotificationTriggerPolicyOverrideKt.policy
import ch.threema.protobuf.d2d.sync.ContactKt.notificationTriggerPolicyOverride
import ch.threema.protobuf.d2d.sync.ContactKt.readReceiptPolicyOverride
import ch.threema.protobuf.d2d.sync.ContactKt.typingIndicatorPolicyOverride
import ch.threema.protobuf.d2d.sync.MdD2DSync
import ch.threema.protobuf.d2d.sync.MdD2DSync.Contact
import ch.threema.protobuf.d2d.sync.MdD2DSync.Contact.NotificationTriggerPolicyOverride.Policy
import ch.threema.protobuf.d2d.sync.contact
import ch.threema.protobuf.deltaImage
import ch.threema.protobuf.image
import ch.threema.protobuf.unit
import ch.threema.storage.models.ContactModel.AcquaintanceLevel
import ch.threema.storage.models.ConversationTag
import com.google.protobuf.kotlin.toByteString
import kotlinx.serialization.Serializable

private val logger = LoggingUtil.getThreemaLogger("ReflectContactSyncUpdateTask")

abstract class ReflectContactSyncUpdateBaseTask(
    protected val contactIdentity: Identity,
    private val contactModelRepository: ContactModelRepository,
    multiDeviceManager: MultiDeviceManager,
    private val nonceFactory: NonceFactory,
) : ReflectContactSyncTask<Unit, Unit>(multiDeviceManager) {
    /**
     * The task type. This is just used for debugging.
     */
    protected abstract val type: String

    /**
     * This method is called to check whether the contact reflection is still necessary. Note that
     * the general strategy to decide may differ between different types of tasks.
     */
    abstract fun isUpdateRequired(currentData: ContactModelData): Boolean

    /**
     * Get the contact sync that contains the delta updates.
     */
    abstract fun getContactSync(): Contact

    /**
     * As a precondition for an update task, the contact with the given identity must exist and the
     * changed data must differ from the current data.
     */
    final override val runPrecondition: () -> Boolean = {
        // The contact must exist and there needs to be a change
        contactModelRepository.getByIdentity(contactIdentity)?.data?.let {
            isUpdateRequired(it)
        } ?: false
    }

    /**
     * Inside the transaction we just reflect the contact sync update from [getContactSync] with the
     * [runPrecondition].
     */
    override val runInsideTransaction: suspend (handle: ActiveTaskCodec) -> Unit = { handle ->
        logger.info("Reflecting contact sync update of type {} for {}", type, contactIdentity)

        val encryptedEnvelopeResult = getEncryptedContactSyncUpdate(
            getContactSync().also {
                runtimeAssert(it.identity == contactIdentity, "Identity must match")
            },
            mdProperties,
        )
        handle.reflectAndAwaitAck(
            encryptedEnvelopeResult = encryptedEnvelopeResult,
            storeD2dNonce = true,
            nonceFactory = nonceFactory,
        )
    }

    override val runAfterSuccessfulTransaction: (transactionResult: Unit) -> Unit = {
        // Nothing to do
    }
}

/**
 * This task must be run *before* the changes have been persisted. It cannot be run using the task
 * manager as it must be run immediately inside another task.
 */
abstract class ReflectContactSyncUpdateImmediateTask(
    contactIdentity: Identity,
    contactModelRepository: ContactModelRepository,
    multiDeviceManager: MultiDeviceManager,
    nonceFactory: NonceFactory,
) : ReflectContactSyncUpdateBaseTask(
    contactIdentity,
    contactModelRepository,
    multiDeviceManager,
    nonceFactory,
) {
    /**
     * Check whether the data has changed. Note that immediate tasks are executed before the update
     * has been persisted. In this case, we reflect only if the current data is different to the new
     * data.
     */
    abstract fun hasDataChanged(currentData: ContactModelData): Boolean

    /**
     * An update of an immediate task is required if the data has changed.
     */
    final override fun isUpdateRequired(currentData: ContactModelData) = hasDataChanged(currentData)

    /**
     * Reflect the current change.
     *
     * @throws TransactionScope.TransactionException if the current contact data already contains
     * this change
     */
    suspend fun reflect(handle: ActiveTaskCodec) {
        reflectContactSync(handle)
    }

    /**
     * A task to reflect a new nickname.
     */
    class ReflectContactNickname(
        contactIdentity: Identity,
        private val newNickname: String,
        contactModelRepository: ContactModelRepository,
        multiDeviceManager: MultiDeviceManager,
        nonceFactory: NonceFactory,
    ) : ReflectContactSyncUpdateImmediateTask(
        contactIdentity,
        contactModelRepository,
        multiDeviceManager,
        nonceFactory,
    ) {
        override val type = "ReflectContactNickname"

        override fun hasDataChanged(currentData: ContactModelData): Boolean {
            return currentData.nickname != newNickname
        }

        override fun getContactSync() = contact {
            identity = contactIdentity
            nickname = newNickname
        }
    }

    /**
     * A task to reflect a new profile picture. Note that this task should always be run when a
     * profile picture has been received to ensure that the other devices can mark the blob as done
     * on the blob mirror.
     */
    class ReflectContactProfilePicture(
        contactIdentity: Identity,
        private val profilePictureUpdate: ProfilePictureUpdate,
        contactModelRepository: ContactModelRepository,
        multiDeviceManager: MultiDeviceManager,
        nonceFactory: NonceFactory,
    ) : ReflectContactSyncUpdateImmediateTask(
        contactIdentity = contactIdentity,
        contactModelRepository = contactModelRepository,
        multiDeviceManager = multiDeviceManager,
        nonceFactory = nonceFactory,
    ) {
        sealed interface ProfilePictureUpdate

        class UpdatedProfilePicture(
            val blobId: ByteArray,
            val nonce: ByteArray,
            val encryptionKey: ByteArray,
        ) : ProfilePictureUpdate

        data object RemovedProfilePicture : ProfilePictureUpdate

        override val type: String = "ReflectContactProfilePicture"

        /**
         * Note that we cannot check this based on the contact model data. Therefore, we assume that
         * the data has changed and rely on the caller to check this.
         */
        override fun hasDataChanged(currentData: ContactModelData): Boolean = true

        override fun getContactSync() = contact {
            identity = contactIdentity
            contactDefinedProfilePicture = deltaImage {
                when (profilePictureUpdate) {
                    is UpdatedProfilePicture -> {
                        updated = image {
                            type = Common.Image.Type.JPEG
                            blob = blob {
                                id = profilePictureUpdate.blobId.toByteString()
                                nonce = profilePictureUpdate.nonce.toByteString()
                                key = profilePictureUpdate.encryptionKey.toByteString()
                            }
                        }
                    }

                    is RemovedProfilePicture -> {
                        removed = unit { }
                    }
                }
            }
        }
    }
}

/**
 * This task must be executed *after* the updates have been persisted to local storage. It must be
 * run using the task manager.
 */
abstract class ReflectContactSyncUpdateTask(
    contactIdentity: Identity,
    contactModelRepository: ContactModelRepository,
    multiDeviceManager: MultiDeviceManager,
    nonceFactory: NonceFactory,
) : ReflectContactSyncUpdateBaseTask(
    contactIdentity,
    contactModelRepository,
    multiDeviceManager,
    nonceFactory,
),
    ActiveTask<Unit>,
    PersistableTask {
    /**
     * Return true if the change that should be reflected still matches the current data. Note that
     * if a task performs several changes, then *all* of the new values must be equal to
     * [currentData].
     */
    abstract fun isChangeValid(currentData: ContactModelData): Boolean

    /**
     * We only reflect a change if it is still valid. This is a consequence of the strategy to first
     * persist changes locally followed by scheduling a persistent task to reflect the changes.
     *
     * There are two cases where the data has been changed in the meantime:
     * - Bypassed d2d messages may have altered the contact, as the same fields have been changed on
     *   a linked device. In this case we do not reflect the change, as it already is outdated.
     * - The user has already changed the values again. In this case we can also skip the reflection
     *   because a new update task containing the changes will be scheduled.
     */
    final override fun isUpdateRequired(currentData: ContactModelData) = isChangeValid(currentData)

    /**
     * Invoke the task. Note that the reflected changes must already be written to the database.
     */
    final override suspend fun invoke(handle: ActiveTaskCodec) {
        if (!multiDeviceManager.isMultiDeviceActive) {
            logger.warn("Cannot reflect contact sync update of type {} when md is not active", type)
            return
        }

        reflectContactSync(handle)
    }

    /**
     * Reflect a new name.
     */
    class ReflectNameUpdate(
        private val newFirstName: String,
        private val newLastName: String,
        contactIdentity: Identity,
        contactModelRepository: ContactModelRepository,
        multiDeviceManager: MultiDeviceManager,
        nonceFactory: NonceFactory,
    ) : ReflectContactSyncUpdateTask(
        contactIdentity,
        contactModelRepository,
        multiDeviceManager,
        nonceFactory,
    ) {
        override val type: String = "ReflectNameUpdate"

        override fun isChangeValid(currentData: ContactModelData): Boolean =
            currentData.firstName == newFirstName &&
                currentData.lastName == newLastName

        override fun getContactSync(): Contact = contact {
            identity = contactIdentity
            firstName = newFirstName
            lastName = newLastName
        }

        override fun serialize(): SerializableTaskData = ReflectNameUpdateData(
            firstName = newFirstName,
            lastName = newLastName,
            identity = contactIdentity,
        )

        @Serializable
        data class ReflectNameUpdateData(
            private val firstName: String,
            private val lastName: String,
            private val identity: Identity,
        ) : SerializableTaskData {
            override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
                ReflectNameUpdate(
                    newFirstName = firstName,
                    newLastName = lastName,
                    contactIdentity = identity,
                    serviceManager.modelRepositories.contacts,
                    serviceManager.multiDeviceManager,
                    serviceManager.nonceFactory,
                )
        }
    }

    /**
     * Reflect a new read receipt policy.
     */
    class ReflectReadReceiptPolicyUpdate(
        private val readReceiptPolicy: ReadReceiptPolicy,
        contactIdentity: Identity,
        contactModelRepository: ContactModelRepository,
        multiDeviceManager: MultiDeviceManager,
        nonceFactory: NonceFactory,
    ) : ReflectContactSyncUpdateTask(
        contactIdentity,
        contactModelRepository,
        multiDeviceManager,
        nonceFactory,
    ) {
        override val type: String = "ReflectReadReceiptPolicyUpdate"

        override fun isChangeValid(currentData: ContactModelData): Boolean =
            currentData.readReceiptPolicy == readReceiptPolicy

        override fun getContactSync(): Contact = contact {
            identity = contactIdentity
            readReceiptPolicyOverride = readReceiptPolicyOverride {
                when (readReceiptPolicy) {
                    ReadReceiptPolicy.DEFAULT -> default = unit {}
                    ReadReceiptPolicy.SEND -> policy = MdD2DSync.ReadReceiptPolicy.SEND_READ_RECEIPT
                    ReadReceiptPolicy.DONT_SEND -> policy = MdD2DSync.ReadReceiptPolicy.DONT_SEND_READ_RECEIPT
                }
            }
        }

        override fun serialize(): SerializableTaskData = ReflectReadReceiptPolicyUpdateData(
            readReceiptPolicy = readReceiptPolicy,
            identity = contactIdentity,
        )

        @Serializable
        data class ReflectReadReceiptPolicyUpdateData(
            private val readReceiptPolicy: ReadReceiptPolicy,
            private val identity: Identity,
        ) : SerializableTaskData {
            override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
                ReflectReadReceiptPolicyUpdate(
                    readReceiptPolicy,
                    identity,
                    serviceManager.modelRepositories.contacts,
                    serviceManager.multiDeviceManager,
                    serviceManager.nonceFactory,
                )
        }
    }

    /**
     * Reflect a new typing indicator policy.
     */
    class ReflectTypingIndicatorPolicyUpdate(
        private val typingIndicatorPolicy: TypingIndicatorPolicy,
        contactIdentity: Identity,
        contactModelRepository: ContactModelRepository,
        multiDeviceManager: MultiDeviceManager,
        nonceFactory: NonceFactory,
    ) : ReflectContactSyncUpdateTask(
        contactIdentity,
        contactModelRepository,
        multiDeviceManager,
        nonceFactory,
    ) {
        override val type: String = "ReflectTypingIndicatorPolicyUpdate"

        override fun isChangeValid(currentData: ContactModelData): Boolean =
            currentData.typingIndicatorPolicy == typingIndicatorPolicy

        override fun getContactSync(): Contact = contact {
            identity = contactIdentity
            typingIndicatorPolicyOverride = typingIndicatorPolicyOverride {
                when (typingIndicatorPolicy) {
                    TypingIndicatorPolicy.DEFAULT -> default = unit {}

                    TypingIndicatorPolicy.SEND -> policy = MdD2DSync.TypingIndicatorPolicy.SEND_TYPING_INDICATOR

                    TypingIndicatorPolicy.DONT_SEND -> policy = MdD2DSync.TypingIndicatorPolicy.DONT_SEND_TYPING_INDICATOR
                }
            }
        }

        override fun serialize(): SerializableTaskData = ReflectTypingIndicatorPolicyUpdateData(
            typingIndicatorPolicy = typingIndicatorPolicy,
            identity = contactIdentity,
        )

        @Serializable
        data class ReflectTypingIndicatorPolicyUpdateData(
            private val typingIndicatorPolicy: TypingIndicatorPolicy,
            private val identity: Identity,
        ) : SerializableTaskData {
            override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
                ReflectTypingIndicatorPolicyUpdate(
                    typingIndicatorPolicy,
                    identity,
                    serviceManager.modelRepositories.contacts,
                    serviceManager.multiDeviceManager,
                    serviceManager.nonceFactory,
                )
        }
    }

    /**
     * Reflect a new activity state.
     */
    class ReflectActivityStateUpdate(
        private val newIdentityState: IdentityState,
        contactIdentity: Identity,
        contactModelRepository: ContactModelRepository,
        multiDeviceManager: MultiDeviceManager,
        nonceFactory: NonceFactory,
    ) : ReflectContactSyncUpdateTask(
        contactIdentity,
        contactModelRepository,
        multiDeviceManager,
        nonceFactory,
    ) {
        override val type = "ReflectActivityStateUpdate"

        override fun isChangeValid(currentData: ContactModelData) =
            currentData.activityState == newIdentityState

        override fun getContactSync(): Contact = contact {
            identity = contactIdentity
            activityState = when (newIdentityState) {
                IdentityState.ACTIVE -> Contact.ActivityState.ACTIVE
                IdentityState.INACTIVE -> Contact.ActivityState.INACTIVE
                IdentityState.INVALID -> Contact.ActivityState.INVALID
            }
        }

        override fun serialize(): SerializableTaskData = ReflectActivityStateUpdateData(
            identityState = newIdentityState,
            identity = contactIdentity,
        )

        @Serializable
        data class ReflectActivityStateUpdateData(
            private val identityState: IdentityState,
            private val identity: Identity,
        ) : SerializableTaskData {
            override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
                ReflectActivityStateUpdate(
                    identityState,
                    identity,
                    serviceManager.modelRepositories.contacts,
                    serviceManager.multiDeviceManager,
                    serviceManager.nonceFactory,
                )
        }
    }

    /**
     * Reflect a new feature mask.
     */
    class ReflectFeatureMaskUpdate(
        private val newFeatureMask: Long,
        contactIdentity: Identity,
        contactModelRepository: ContactModelRepository,
        multiDeviceManager: MultiDeviceManager,
        nonceFactory: NonceFactory,
    ) : ReflectContactSyncUpdateTask(
        contactIdentity,
        contactModelRepository,
        multiDeviceManager,
        nonceFactory,
    ) {
        override val type = "ReflectFeatureMaskUpdate"

        override fun isChangeValid(currentData: ContactModelData) =
            currentData.featureMask == newFeatureMask.toULong()

        override fun getContactSync(): Contact = contact {
            identity = contactIdentity
            featureMask = newFeatureMask
        }

        override fun serialize(): SerializableTaskData = ReflectFeatureMaskUpdateData(
            featureMask = newFeatureMask,
            identity = contactIdentity,
        )

        @Serializable
        data class ReflectFeatureMaskUpdateData(
            private val featureMask: Long,
            private val identity: Identity,
        ) : SerializableTaskData {
            override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
                ReflectFeatureMaskUpdate(
                    featureMask,
                    identity,
                    serviceManager.modelRepositories.contacts,
                    serviceManager.multiDeviceManager,
                    serviceManager.nonceFactory,
                )
        }
    }

    /**
     * Reflect a new verification level.
     */
    class ReflectVerificationLevelUpdate(
        private val newVerificationLevel: VerificationLevel,
        contactIdentity: Identity,
        contactModelRepository: ContactModelRepository,
        multiDeviceManager: MultiDeviceManager,
        nonceFactory: NonceFactory,
    ) : ReflectContactSyncUpdateTask(
        contactIdentity,
        contactModelRepository,
        multiDeviceManager,
        nonceFactory,
    ) {
        override val type = "ReflectVerificationLevelUpdate"

        override fun isChangeValid(currentData: ContactModelData) =
            currentData.verificationLevel == newVerificationLevel

        override fun getContactSync(): Contact = contact {
            identity = contactIdentity
            verificationLevel = when (newVerificationLevel) {
                VerificationLevel.FULLY_VERIFIED -> Contact.VerificationLevel.FULLY_VERIFIED
                VerificationLevel.SERVER_VERIFIED -> Contact.VerificationLevel.SERVER_VERIFIED
                VerificationLevel.UNVERIFIED -> Contact.VerificationLevel.UNVERIFIED
            }
        }

        override fun serialize(): SerializableTaskData = ReflectVerificationLevelUpdateData(
            verificationLevel = newVerificationLevel,
            identity = contactIdentity,
        )

        @Serializable
        data class ReflectVerificationLevelUpdateData(
            private val verificationLevel: VerificationLevel,
            private val identity: Identity,
        ) : SerializableTaskData {
            override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
                ReflectVerificationLevelUpdate(
                    verificationLevel,
                    identity,
                    serviceManager.modelRepositories.contacts,
                    serviceManager.multiDeviceManager,
                    serviceManager.nonceFactory,
                )
        }
    }

    /**
     * Reflect a new work verification level.
     */
    class ReflectWorkVerificationLevelUpdate(
        private val newWorkVerificationLevel: WorkVerificationLevel,
        contactIdentity: Identity,
        contactModelRepository: ContactModelRepository,
        multiDeviceManager: MultiDeviceManager,
        nonceFactory: NonceFactory,
    ) : ReflectContactSyncUpdateTask(
        contactIdentity,
        contactModelRepository,
        multiDeviceManager,
        nonceFactory,
    ) {
        override val type = "ReflectWorkVerificationLevelUpdate"

        override fun isChangeValid(currentData: ContactModelData) =
            currentData.workVerificationLevel == newWorkVerificationLevel

        override fun getContactSync(): Contact = contact {
            identity = contactIdentity
            workVerificationLevel = when (newWorkVerificationLevel) {
                WorkVerificationLevel.NONE -> Contact.WorkVerificationLevel.NONE
                WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED -> Contact.WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED
            }
        }

        override fun serialize(): SerializableTaskData = ReflectWorkVerificationLevelUpdateData(
            workVerificationLevel = newWorkVerificationLevel,
            identity = contactIdentity,
        )

        @Serializable
        data class ReflectWorkVerificationLevelUpdateData(
            private val workVerificationLevel: WorkVerificationLevel,
            private val identity: Identity,
        ) : SerializableTaskData {
            override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
                ReflectWorkVerificationLevelUpdate(
                    workVerificationLevel,
                    identity,
                    serviceManager.modelRepositories.contacts,
                    serviceManager.multiDeviceManager,
                    serviceManager.nonceFactory,
                )
        }
    }

    /**
     * Reflect a new identity type.
     */
    class ReflectIdentityTypeUpdate(
        private val newIdentityType: IdentityType,
        contactIdentity: Identity,
        contactModelRepository: ContactModelRepository,
        multiDeviceManager: MultiDeviceManager,
        nonceFactory: NonceFactory,
    ) : ReflectContactSyncUpdateTask(
        contactIdentity,
        contactModelRepository,
        multiDeviceManager,
        nonceFactory,
    ) {
        override val type = "ReflectIdentityTypeUpdate"

        override fun isChangeValid(currentData: ContactModelData) =
            currentData.identityType == newIdentityType

        override fun getContactSync(): Contact = contact {
            identity = contactIdentity
            identityType = when (newIdentityType) {
                IdentityType.NORMAL -> Contact.IdentityType.REGULAR
                IdentityType.WORK -> Contact.IdentityType.WORK
            }
        }

        override fun serialize(): SerializableTaskData = ReflectIdentityTypeUpdateData(
            identityType = newIdentityType,
            identity = contactIdentity,
        )

        @Serializable
        data class ReflectIdentityTypeUpdateData(
            private val identityType: IdentityType,
            private val identity: Identity,
        ) : SerializableTaskData {
            override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
                ReflectIdentityTypeUpdate(
                    identityType,
                    identity,
                    serviceManager.modelRepositories.contacts,
                    serviceManager.multiDeviceManager,
                    serviceManager.nonceFactory,
                )
        }
    }

    /**
     * Reflect a new acquaintance level.
     */
    class ReflectAcquaintanceLevelUpdate(
        private val newAcquaintanceLevel: AcquaintanceLevel,
        contactIdentity: Identity,
        contactModelRepository: ContactModelRepository,
        multiDeviceManager: MultiDeviceManager,
        nonceFactory: NonceFactory,
    ) : ReflectContactSyncUpdateTask(
        contactIdentity,
        contactModelRepository,
        multiDeviceManager,
        nonceFactory,
    ) {
        override val type = "ReflectAcquaintanceLevelUpdate"

        override fun isChangeValid(currentData: ContactModelData): Boolean =
            currentData.acquaintanceLevel == newAcquaintanceLevel

        override fun getContactSync(): Contact = contact {
            identity = contactIdentity
            acquaintanceLevel = when (newAcquaintanceLevel) {
                AcquaintanceLevel.DIRECT -> Contact.AcquaintanceLevel.DIRECT
                AcquaintanceLevel.GROUP -> Contact.AcquaintanceLevel.GROUP_OR_DELETED
            }
        }

        override fun serialize(): SerializableTaskData = ReflectAcquaintanceLevelUpdateData(
            acquaintanceLevel = newAcquaintanceLevel,
            identity = contactIdentity,
        )

        @Serializable
        data class ReflectAcquaintanceLevelUpdateData(
            private val acquaintanceLevel: AcquaintanceLevel,
            private val identity: Identity,
        ) : SerializableTaskData {
            override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
                ReflectAcquaintanceLevelUpdate(
                    acquaintanceLevel,
                    identity,
                    serviceManager.modelRepositories.contacts,
                    serviceManager.multiDeviceManager,
                    serviceManager.nonceFactory,
                )
        }
    }

    /**
     * Reflect a new user defined profile picture.
     */
    class ReflectUserDefinedProfilePictureUpdate(
        identity: Identity,
        contactModelRepository: ContactModelRepository,
        multiDeviceManager: MultiDeviceManager,
        nonceFactory: NonceFactory,
        private val fileService: FileService,
        private val symmetricEncryptionService: SymmetricEncryptionService,
        private val apiService: ApiService,
    ) : ReflectContactSyncUpdateTask(
        contactIdentity = identity,
        contactModelRepository = contactModelRepository,
        multiDeviceManager = multiDeviceManager,
        nonceFactory = nonceFactory,
    ) {
        override val type = "ReflectUserDefinedProfilePictureUpdate"

        /**
         * Note that we do always sync the currently set user defined profile picture. This is to
         * prevent having to persist the whole profile picture. In the worst case, this can lead to
         * the same profile picture uploaded twice: once from a reflected device and later from this
         * device (if a race occurs).
         */
        override fun isChangeValid(currentData: ContactModelData) = true

        override fun getContactSync(): Contact {
            val userDefinedProfilePictureBytes =
                fileService.getUserDefinedProfilePictureStream(contactIdentity)
                    ?.buffered()
                    ?.use { it.readBytes() }
            return if (userDefinedProfilePictureBytes != null) {
                val encryptionResult = symmetricEncryptionService.encrypt(
                    userDefinedProfilePictureBytes,
                    symmetricEncryptionService.generateSymmetricKey(),
                    ProtocolDefines.CONTACT_PHOTO_NONCE,
                )
                val blobId = apiService.createUploader(
                    data = encryptionResult.data,
                    shouldPersist = false,
                    blobScope = BlobScope.Local,
                ).upload()

                checkNotNull(blobId) { "UploadCancelled" }

                contact {
                    identity = contactIdentity
                    userDefinedProfilePicture = deltaImage {
                        updated = image {
                            type = Common.Image.Type.JPEG
                            blob = blob {
                                id = blobId.toByteString()
                                nonce = ProtocolDefines.CONTACT_PHOTO_NONCE.toByteString()
                                key = encryptionResult.key.toByteString()
                            }
                        }
                    }
                }
            } else {
                contact {
                    identity = contactIdentity
                    userDefinedProfilePicture = deltaImage {
                        removed = unit { }
                    }
                }
            }
        }

        override fun serialize(): SerializableTaskData = ReflectUserDefinedProfilePictureUpdateData(
            identity = contactIdentity,
        )

        @Serializable
        data class ReflectUserDefinedProfilePictureUpdateData(
            private val identity: Identity,
        ) : SerializableTaskData {
            override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
                ReflectUserDefinedProfilePictureUpdate(
                    identity,
                    serviceManager.modelRepositories.contacts,
                    serviceManager.multiDeviceManager,
                    serviceManager.nonceFactory,
                    serviceManager.fileService,
                    serviceManager.symmetricEncryptionService,
                    serviceManager.apiService,
                )
        }
    }

    /**
     * Reflect a new notification-trigger-policy-override
     */
    class ReflectNotificationTriggerPolicyOverrideUpdate(
        private val newNotificationTriggerPolicyOverride: NotificationTriggerPolicyOverride,
        contactIdentity: Identity,
        contactModelRepository: ContactModelRepository,
        multiDeviceManager: MultiDeviceManager,
        nonceFactory: NonceFactory,
    ) : ReflectContactSyncUpdateTask(
        contactIdentity,
        contactModelRepository,
        multiDeviceManager,
        nonceFactory,
    ) {
        override val type = "ReflectNotificationTriggerPolicyOverrideUpdate"

        override fun isChangeValid(currentData: ContactModelData) =
            currentData.notificationTriggerPolicyOverride == newNotificationTriggerPolicyOverride.dbValue

        override fun getContactSync(): Contact = contact {
            identity = contactIdentity

            notificationTriggerPolicyOverride = notificationTriggerPolicyOverride {
                when (newNotificationTriggerPolicyOverride) {
                    NotificationTriggerPolicyOverride.NotMuted -> default = unit {}

                    NotificationTriggerPolicyOverride.MutedIndefinite -> policy = policy {
                        policy = Policy.NotificationTriggerPolicy.NEVER
                    }

                    NotificationTriggerPolicyOverride.MutedIndefiniteExceptMentions -> throw IllegalStateException(
                        "Contact receivers can never have this setting",
                    )

                    is NotificationTriggerPolicyOverride.MutedUntil -> policy = policy {
                        policy = Policy.NotificationTriggerPolicy.NEVER
                        expiresAt = newNotificationTriggerPolicyOverride.utcMillis
                    }
                }
            }
        }

        override fun serialize(): SerializableTaskData = ReflectNotificationTriggerPolicyOverrideUpdateData(
            notificationTriggerPolicyOverride = newNotificationTriggerPolicyOverride.dbValue,
            contactIdentity = contactIdentity,
        )

        @Serializable
        data class ReflectNotificationTriggerPolicyOverrideUpdateData(
            private val notificationTriggerPolicyOverride: Long?,
            private val contactIdentity: Identity,
        ) : SerializableTaskData {
            override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
                ReflectNotificationTriggerPolicyOverrideUpdate(
                    newNotificationTriggerPolicyOverride = NotificationTriggerPolicyOverride.fromDbValueContact(
                        notificationTriggerPolicyOverride,
                    ),
                    contactIdentity = contactIdentity,
                    contactModelRepository = serviceManager.modelRepositories.contacts,
                    multiDeviceManager = serviceManager.multiDeviceManager,
                    nonceFactory = serviceManager.nonceFactory,
                )
        }
    }

    /**
     * Note that this task currently just reflects the current conversation category state of the contact as the conversation category is not part of
     * the contact model.
     */
    class ReflectConversationCategoryUpdate(
        contactIdentity: Identity,
        private val isPrivateChat: Boolean,
        contactModelRepository: ContactModelRepository,
        multiDeviceManager: MultiDeviceManager,
        nonceFactory: NonceFactory,
        private val conversationCategoryService: ConversationCategoryService,
    ) : ReflectContactSyncUpdateTask(
        contactIdentity,
        contactModelRepository,
        multiDeviceManager,
        nonceFactory,
    ) {
        override val type = "ReflectConversationCategoryUpdate"

        override fun isChangeValid(currentData: ContactModelData): Boolean {
            return conversationCategoryService.isPrivateChat(ContactUtil.getUniqueIdString(contactIdentity)) == isPrivateChat
        }

        override fun getContactSync(): Contact = contact {
            identity = contactIdentity

            conversationCategory = if (isPrivateChat) {
                MdD2DSync.ConversationCategory.PROTECTED
            } else {
                MdD2DSync.ConversationCategory.DEFAULT
            }
        }

        override fun serialize(): SerializableTaskData = ReflectContactConversationCategoryUpdateData(
            contactIdentity = contactIdentity,
            isPrivateChat = isPrivateChat,
        )

        @Serializable
        data class ReflectContactConversationCategoryUpdateData(
            private val contactIdentity: Identity,
            private val isPrivateChat: Boolean,
        ) : SerializableTaskData {
            override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
                ReflectConversationCategoryUpdate(
                    isPrivateChat = isPrivateChat,
                    contactIdentity = contactIdentity,
                    contactModelRepository = serviceManager.modelRepositories.contacts,
                    multiDeviceManager = serviceManager.multiDeviceManager,
                    nonceFactory = serviceManager.nonceFactory,
                    conversationCategoryService = serviceManager.conversationCategoryService,
                )
        }
    }

    /**
     * Reflect a new conversation visibility regarding the archive option.
     *
     * TODO(ANDR-3721): There should only be one task that reflects the conversation visibility.
     */
    class ReflectConversationVisibilityArchiveUpdate(
        private val isArchived: Boolean,
        contactIdentity: Identity,
        contactModelRepository: ContactModelRepository,
        multiDeviceManager: MultiDeviceManager,
        nonceFactory: NonceFactory,
    ) : ReflectContactSyncUpdateTask(
        contactIdentity,
        contactModelRepository,
        multiDeviceManager,
        nonceFactory,
    ) {
        override val type = "ReflectConversationVisibilityArchiveUpdate"

        override fun isChangeValid(currentData: ContactModelData) = currentData.isArchived == isArchived

        override fun getContactSync(): Contact = contact {
            identity = contactIdentity

            conversationVisibility = if (isArchived) {
                MdD2DSync.ConversationVisibility.ARCHIVED
            } else {
                MdD2DSync.ConversationVisibility.NORMAL
            }
        }

        override fun serialize(): SerializableTaskData = ReflectConversationVisibilityArchiveUpdateData(
            isArchived = isArchived,
            contactIdentity = contactIdentity,
        )

        @Serializable
        data class ReflectConversationVisibilityArchiveUpdateData(
            private val isArchived: Boolean,
            private val contactIdentity: Identity,
        ) : SerializableTaskData {
            override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
                ReflectConversationVisibilityArchiveUpdate(
                    isArchived = isArchived,
                    contactIdentity = contactIdentity,
                    contactModelRepository = serviceManager.modelRepositories.contacts,
                    multiDeviceManager = serviceManager.multiDeviceManager,
                    nonceFactory = serviceManager.nonceFactory,
                )
        }
    }

    /**
     * Reflect a new conversation visibility regarding the pin option.
     *
     * TODO(ANDR-3721): There should only be one task that reflects the conversation visibility.
     */
    class ReflectConversationVisibilityPinnedUpdate(
        private val isPinned: Boolean,
        contactIdentity: Identity,
        private val conversationTagService: ConversationTagService,
        contactModelRepository: ContactModelRepository,
        multiDeviceManager: MultiDeviceManager,
        nonceFactory: NonceFactory,
    ) : ReflectContactSyncUpdateTask(
        contactIdentity,
        contactModelRepository,
        multiDeviceManager,
        nonceFactory,
    ) {
        override val type = "ReflectConversationVisibilityPinnedUpdate"

        override fun isChangeValid(currentData: ContactModelData) =
            conversationTagService.isTaggedWith(ConversationUtil.getIdentityConversationUid(contactIdentity), ConversationTag.PINNED) == isPinned

        override fun getContactSync(): Contact = contact {
            identity = contactIdentity

            conversationVisibility = if (isPinned) {
                MdD2DSync.ConversationVisibility.PINNED
            } else {
                MdD2DSync.ConversationVisibility.NORMAL
            }
        }

        override fun serialize(): SerializableTaskData = ReflectConversationVisibilityPinnedUpdateData(
            isPinned = isPinned,
            contactIdentity = contactIdentity,
        )

        @Serializable
        data class ReflectConversationVisibilityPinnedUpdateData(
            private val isPinned: Boolean,
            private val contactIdentity: Identity,
        ) : SerializableTaskData {
            override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
                ReflectConversationVisibilityPinnedUpdate(
                    isPinned = isPinned,
                    contactIdentity = contactIdentity,
                    conversationTagService = serviceManager.conversationTagService,
                    contactModelRepository = serviceManager.modelRepositories.contacts,
                    multiDeviceManager = serviceManager.multiDeviceManager,
                    nonceFactory = serviceManager.nonceFactory,
                )
        }
    }
}

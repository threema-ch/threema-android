/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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

package ch.threema.app.multidevice.linking

import android.graphics.Bitmap
import androidx.annotation.WorkerThread
import ch.threema.app.BuildConfig
import ch.threema.app.R
import ch.threema.app.managers.ServiceManager
import ch.threema.app.restrictions.AppRestrictionUtil
import ch.threema.app.services.ContactService
import ch.threema.app.services.license.LicenseServiceUser
import ch.threema.app.tasks.ReflectUserProfileIdentityLinksTask
import ch.threema.app.utils.BitmapUtil
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.ContactUtil
import ch.threema.app.utils.ConversationUtil
import ch.threema.app.utils.ConversationUtil.getConversationUid
import ch.threema.app.utils.GroupUtil
import ch.threema.base.crypto.NaCl
import ch.threema.base.crypto.NonceScope
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.datatypes.NotificationTriggerPolicyOverride.*
import ch.threema.data.models.ContactModel
import ch.threema.data.models.ContactModelData
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.protobuf.Common.BlobData
import ch.threema.protobuf.Common.DeltaImage
import ch.threema.protobuf.Common.Identities
import ch.threema.protobuf.Common.Image
import ch.threema.protobuf.blob
import ch.threema.protobuf.blobData
import ch.threema.protobuf.d2d.join.EssentialDataKt.augmentedContact
import ch.threema.protobuf.d2d.join.EssentialDataKt.augmentedDistributionList
import ch.threema.protobuf.d2d.join.EssentialDataKt.augmentedGroup
import ch.threema.protobuf.d2d.join.EssentialDataKt.deviceGroupData
import ch.threema.protobuf.d2d.join.EssentialDataKt.identityData
import ch.threema.protobuf.d2d.join.MdD2DJoin.EssentialData
import ch.threema.protobuf.d2d.join.MdD2DJoin.EssentialData.AugmentedContact
import ch.threema.protobuf.d2d.join.MdD2DJoin.EssentialData.AugmentedDistributionList
import ch.threema.protobuf.d2d.join.MdD2DJoin.EssentialData.AugmentedGroup
import ch.threema.protobuf.d2d.join.MdD2DJoin.EssentialData.IdentityData
import ch.threema.protobuf.d2d.sync.ContactKt
import ch.threema.protobuf.d2d.sync.ContactKt.readReceiptPolicyOverride
import ch.threema.protobuf.d2d.sync.ContactKt.typingIndicatorPolicyOverride
import ch.threema.protobuf.d2d.sync.GroupKt
import ch.threema.protobuf.d2d.sync.MdD2DSync
import ch.threema.protobuf.d2d.sync.MdD2DSync.Contact
import ch.threema.protobuf.d2d.sync.MdD2DSync.Contact.NotificationSoundPolicyOverride
import ch.threema.protobuf.d2d.sync.MdD2DSync.Contact.NotificationTriggerPolicyOverride
import ch.threema.protobuf.d2d.sync.MdD2DSync.Contact.NotificationTriggerPolicyOverride.Policy.NotificationTriggerPolicy
import ch.threema.protobuf.d2d.sync.MdD2DSync.Contact.SyncState
import ch.threema.protobuf.d2d.sync.MdD2DSync.Contact.VerificationLevel
import ch.threema.protobuf.d2d.sync.MdD2DSync.Group.UserState
import ch.threema.protobuf.d2d.sync.MdD2DSync.Settings
import ch.threema.protobuf.d2d.sync.MdD2DSync.UserProfile.IdentityLinks
import ch.threema.protobuf.d2d.sync.MdD2DSync.UserProfile.ProfilePictureShareWith
import ch.threema.protobuf.d2d.sync.MdmParametersKt.parameter
import ch.threema.protobuf.d2d.sync.UserProfileKt.profilePictureShareWith
import ch.threema.protobuf.d2d.sync.contact
import ch.threema.protobuf.d2d.sync.distributionList
import ch.threema.protobuf.d2d.sync.group
import ch.threema.protobuf.d2d.sync.mdmParameters
import ch.threema.protobuf.d2d.sync.settings
import ch.threema.protobuf.d2d.sync.threemaWorkCredentials
import ch.threema.protobuf.d2d.sync.userProfile
import ch.threema.protobuf.deltaImage
import ch.threema.protobuf.groupIdentity
import ch.threema.protobuf.identities
import ch.threema.protobuf.image
import ch.threema.protobuf.unit
import ch.threema.storage.models.ContactModel.AcquaintanceLevel
import ch.threema.storage.models.DistributionListModel
import com.google.protobuf.ByteString
import com.google.protobuf.kotlin.toByteString
import java.nio.ByteBuffer

private val logger = getThreemaLogger("DeviceLinkingDataCollector")

data class DeviceLinkingData(val blobs: Sequence<BlobData>, val essentialDataProvider: EssentialDataProvider)

class BlobDataProvider(private val blobId: ByteArray, private val dataProvider: () -> ByteArray?) {
    private enum class BlobDataProviderState {
        SUCCESS,
        FAIL,
        NOT_USED,
    }
    private var state = BlobDataProviderState.NOT_USED

    /**
     * Get this providers [BlobData]. Note that this method must only be called once.
     *
     * @throws [IllegalStateException] if this method already has been called
     * @return [BlobData] or null if no blobId or actual data is available
     */
    fun get(): BlobData? {
        check(state == BlobDataProviderState.NOT_USED) {
            "Cannot get the blob data several times"
        }

        val blobData = constructBlobData()

        state = if (blobData != null) {
            BlobDataProviderState.SUCCESS
        } else {
            BlobDataProviderState.FAIL
        }

        return blobData
    }

    private fun constructBlobData(): BlobData? {
        logger.debug("Invoke blob data provider")
        return dataProvider.invoke()?.toByteString()?.let { blobData ->
            blobData {
                id = blobId.toByteString()
                data = blobData
            }
        }
    }

    /**
     * Check whether the blob data provider has been successfully used or not.
     * @throws [IllegalStateException] if the blob data provider has not been used yet
     */
    fun hasBeenSuccessfullyUsed(): Boolean {
        return when (state) {
            BlobDataProviderState.SUCCESS -> true
            BlobDataProviderState.FAIL -> false
            BlobDataProviderState.NOT_USED -> throw IllegalStateException("Blob data provider has not yet been used")
        }
    }
}

class AugmentedContactProvider(
    private val augmentedContact: AugmentedContact,
    private val contactDefinedProfilePictureProvider: BlobDataProvider?,
    private val userDefinedProfilePictureProvider: BlobDataProvider?,
) {
    /**
     * Get the augmented contact.
     *
     * @throws IllegalStateException if the profile picture providers have not been used at all
     */
    fun get(): AugmentedContact {
        val invalidContactDefinedProfilePicture = contactDefinedProfilePictureProvider?.hasBeenSuccessfullyUsed() == false
        val invalidUserDefinedProfilePicture = userDefinedProfilePictureProvider?.hasBeenSuccessfullyUsed() == false
        if (invalidContactDefinedProfilePicture || invalidUserDefinedProfilePicture) {
            // In case one of the profile pictures could not be used successfully, we remove it from the contact. Otherwise, the contact would contain
            // an invalid blob id.
            val contactBuilder = augmentedContact.contact.toBuilder()
            if (invalidContactDefinedProfilePicture) {
                logger.warn("Skipping contact defined profile picture for {}", augmentedContact.contact.identity)
                contactBuilder.clearContactDefinedProfilePicture()
            }
            if (invalidUserDefinedProfilePicture) {
                logger.warn("Skipping user defined profile picture for {}", augmentedContact.contact.identity)
                contactBuilder.clearUserDefinedProfilePicture()
            }
            val augmentedContactBuilder = augmentedContact.toBuilder()
            augmentedContactBuilder.setContact(contactBuilder)
            return augmentedContactBuilder.build()
        }
        return augmentedContact
    }
}

class AugmentedGroupProvider(
    private val augmentedGroup: AugmentedGroup,
    private val groupProfilePictureProvider: BlobDataProvider?,
) {
    /**
     * Get the augmented group.
     *
     * @throws IllegalStateException if the profile picture provider has not been used at all
     */
    fun get(): AugmentedGroup {
        if (groupProfilePictureProvider?.hasBeenSuccessfullyUsed() == false) {
            // In case the group profile picture could be used successfully, we remove it from the group. Otherwise, the group would contain an
            // invalid blob id.
            logger.warn("Skipping group profile picture")
            val groupBuilder = augmentedGroup.group.toBuilder()
            groupBuilder.clearProfilePicture()
            val augmentedGroupBuilder = augmentedGroup.toBuilder()
            augmentedGroupBuilder.setGroup(groupBuilder)
            return augmentedGroupBuilder.build()
        }
        return augmentedGroup
    }
}

/**
 * This provider contains the required information to create the essential data.
 *
 * The [essentialDataBuilder] must be complete except for augmented contacts and groups.
 * The provided [augmentedContactProviders] and [augmentedGroupProviders] will be used to add the augmented contacts and groups to the essential data
 * builder. Note that during this process, the required blobs for the contacts and groups should already be sent. In case some of them could not have
 * been sent successfully (because the files were corrupt), the augmented contacts or groups will be updated to not contain these blobs.
 */
class EssentialDataProvider(
    private val essentialDataBuilder: EssentialData.Builder,
    private val augmentedContactProviders: Collection<AugmentedContactProvider>,
    private val augmentedGroupProviders: Collection<AugmentedGroupProvider>,
) {
    fun get(): EssentialData {
        essentialDataBuilder.addAllContacts(augmentedContactProviders.map(AugmentedContactProvider::get))
        essentialDataBuilder.addAllGroups(augmentedGroupProviders.map(AugmentedGroupProvider::get))
        return essentialDataBuilder.build()
    }
}

class DeviceLinkingDataCollector(
    serviceManager: ServiceManager,
) {
    private val identityStore by lazy { serviceManager.identityStore }
    private val userService by lazy { serviceManager.userService }
    private val contactService by lazy { serviceManager.contactService }
    private val contactModelRepository by lazy { serviceManager.modelRepositories.contacts }
    private val groupModelRepository by lazy { serviceManager.modelRepositories.groups }
    private val distributionListService by lazy { serviceManager.distributionListService }
    private val deviceCookieManager by lazy { serviceManager.deviceCookieManager }
    private val preferenceService by lazy { serviceManager.preferenceService }
    private val blockedIdentitiesService by lazy { serviceManager.blockedIdentitiesService }
    private val excludeFromSyncService by lazy { serviceManager.excludedSyncIdentitiesService }
    private val fileService by lazy { serviceManager.fileService }
    private val conversationCategoryService by lazy { serviceManager.conversationCategoryService }
    private val conversationService by lazy { serviceManager.conversationService }
    private val ringtoneService by lazy { serviceManager.ringtoneService }
    private val nonceFactory by lazy { serviceManager.nonceFactory }
    private val licenseService by lazy { serviceManager.licenseService }
    private val context by lazy { serviceManager.context }

    @WorkerThread
    fun collectData(dgk: ByteArray): DeviceLinkingData {
        val blobDataProviders = mutableListOf<BlobDataProvider>()
        val augmentedContactProviders = mutableListOf<AugmentedContactProvider>()
        val augmentedGroupProviders = mutableListOf<AugmentedGroupProvider>()

        val essentialDataBuilder = EssentialData.newBuilder()

        logger.trace("Collect identity data")
        essentialDataBuilder.setIdentityData(collectIdentityData())

        logger.trace("Collect device group data")
        essentialDataBuilder.setDeviceGroupData(
            deviceGroupData {
                this.dgk = dgk.toByteString()
            },
        )

        logger.trace("Collect user profile")
        val (userProfileBlobProvider, userProfileData) = collectUserProfile()
        userProfileBlobProvider?.let {
            blobDataProviders.add(it)
        }
        essentialDataBuilder.setUserProfile(userProfileData)

        logger.trace("Collect settings")
        essentialDataBuilder.setSettings(collectSettings())

        val conversationsStats = collectConversationsStats()

        logger.trace("Collect contacts")
        collectContacts(conversationsStats).forEach { (contactBlobDataProviders, augmentedContactProvider) ->
            blobDataProviders.addAll(contactBlobDataProviders)
            augmentedContactProviders.add(augmentedContactProvider)
        }

        logger.trace("Collect groups")
        collectGroups(conversationsStats).forEach { (groupBlobDataProviders, augmentedGroupProvider) ->
            blobDataProviders.addAll(groupBlobDataProviders)
            augmentedGroupProviders.add(augmentedGroupProvider)
        }

        if (BuildConfig.MD_SYNC_DISTRIBUTION_LISTS) {
            logger.trace("Collect distribution lists")
            essentialDataBuilder.addAllDistributionLists(collectDistributionLists(conversationsStats))
        } else {
            logger.trace("Skip collection of distribution lists")
            essentialDataBuilder.clearDistributionLists()
        }

        logger.trace("Collect csp nonce hashes")
        essentialDataBuilder.addAllCspHashedNonces(collectCspNonceHashes())

        logger.trace("Collect d2d nonce hashes")
        essentialDataBuilder.addAllD2DHashedNonces(collectD2dNonceHashes())

        // work
        if (ConfigUtils.isWorkBuild()) {
            logger.trace("Collect work credentials")
            essentialDataBuilder.setWorkCredentials(
                collectWorkCredentials()
                    ?: throw IllegalStateException("No work credentials available in work build"),
            )
            collectMdmParameters()?.let { mdmParameters ->
                essentialDataBuilder.setMdmParameters(mdmParameters)
            }
        }

        logger.debug("Number of blobDataProviders: {}", blobDataProviders.size)
        val blobsSequence = blobDataProviders
            .asSequence()
            .mapNotNull { it.get() }

        val essentialDataProvider = EssentialDataProvider(
            essentialDataBuilder = essentialDataBuilder,
            augmentedContactProviders = augmentedContactProviders,
            augmentedGroupProviders = augmentedGroupProviders,
        )

        return DeviceLinkingData(blobsSequence, essentialDataProvider)
    }

    private fun collectIdentityData(): IdentityData {
        return identityData {
            identity = identityStore.getIdentity()!!
            ck = identityStore.getPrivateKey()!!.toByteString()
            cspDeviceCookie = deviceCookieManager.obtainDeviceCookie().toByteString()
            cspServerGroup = identityStore.getServerGroup()!!
        }
    }

    private fun collectUserProfile(): Pair<BlobDataProvider?, MdD2DSync.UserProfile> {
        return collectUserProfilePicture().let { profilePictureData ->
            profilePictureData?.first to userProfile {
                nickname = identityStore.getPublicNickname()
                profilePictureData?.second?.let {
                    profilePicture = it
                }
                profilePictureShareWith = collectProfilePictureShareWith()
                identityLinks = collectIdentityLinks()
            }
        }
    }

    private fun collectUserProfilePicture(): Pair<BlobDataProvider, DeltaImage>? {
        val profilePictureData = userService.uploadUserProfilePictureOrGetPreviousUploadData()

        val hasProfilePicture = profilePictureData.blobId != null &&
            !profilePictureData.blobId.contentEquals(ch.threema.storage.models.ContactModel.NO_PROFILE_PICTURE_BLOB_ID)

        return if (hasProfilePicture) {
            val blobMeta = blob {
                id = profilePictureData.blobId.toByteString()
                nonce = ProtocolDefines.CONTACT_PHOTO_NONCE.toByteString()
                key = profilePictureData.encryptionKey.toByteString()
                logInvalidTimestamp(profilePictureData.uploadedAt, "User profile picture uploadedAt")
                uploadedAt = profilePictureData.uploadedAt
            }

            val profilePicture = deltaImage {
                updated = image {
                    type = Image.Type.JPEG
                    blob = blobMeta
                }
            }

            val blobDataProvider = BlobDataProvider(profilePictureData.blobId) {
                profilePictureData.profilePicture.bytes
            }

            blobDataProvider to profilePicture
        } else {
            null
        }
    }

    private fun collectProfilePictureShareWith(): ProfilePictureShareWith {
        val policy = contactService.profilePictureSharePolicy

        return profilePictureShareWith {
            when (policy.policy) {
                ContactService.ProfilePictureSharePolicy.Policy.NOBODY -> nobody = unit {}
                ContactService.ProfilePictureSharePolicy.Policy.EVERYONE -> everyone = unit {}
                ContactService.ProfilePictureSharePolicy.Policy.ALLOW_LIST -> {
                    allowList = identities { identities += policy.allowedIdentities }
                }
            }
        }
    }

    private fun collectIdentityLinks(): IdentityLinks {
        return ReflectUserProfileIdentityLinksTask.getUserProfileSyncIdentityLinks(userService)
    }

    private fun collectSettings(): Settings {
        return settings {
            contactSyncPolicy = if (preferenceService.isSyncContacts) {
                Settings.ContactSyncPolicy.SYNC
            } else {
                Settings.ContactSyncPolicy.NOT_SYNCED
            }
            unknownContactPolicy = if (preferenceService.isBlockUnknown) {
                Settings.UnknownContactPolicy.BLOCK_UNKNOWN
            } else {
                Settings.UnknownContactPolicy.ALLOW_UNKNOWN
            }
            readReceiptPolicy = if (preferenceService.areReadReceiptsEnabled()) {
                MdD2DSync.ReadReceiptPolicy.SEND_READ_RECEIPT
            } else {
                MdD2DSync.ReadReceiptPolicy.DONT_SEND_READ_RECEIPT
            }
            typingIndicatorPolicy = if (preferenceService.isTypingIndicatorEnabled) {
                MdD2DSync.TypingIndicatorPolicy.SEND_TYPING_INDICATOR
            } else {
                MdD2DSync.TypingIndicatorPolicy.DONT_SEND_TYPING_INDICATOR
            }
            o2OCallPolicy = if (preferenceService.isVoipEnabled) {
                Settings.O2oCallPolicy.ALLOW_O2O_CALL
            } else {
                Settings.O2oCallPolicy.DENY_O2O_CALL
            }
            o2OCallConnectionPolicy = if (preferenceService.forceTURN) {
                Settings.O2oCallConnectionPolicy.REQUIRE_RELAYED_CONNECTION
            } else {
                Settings.O2oCallConnectionPolicy.ALLOW_DIRECT_CONNECTION
            }
            o2OCallVideoPolicy = if (preferenceService.areVideoCallsEnabled()) {
                Settings.O2oCallVideoPolicy.ALLOW_VIDEO
            } else {
                Settings.O2oCallVideoPolicy.DENY_VIDEO
            }
            groupCallPolicy = if (preferenceService.areGroupCallsEnabled()) {
                Settings.GroupCallPolicy.ALLOW_GROUP_CALL
            } else {
                Settings.GroupCallPolicy.DENY_GROUP_CALL
            }
            screenshotPolicy = if (preferenceService.areScreenshotsDisabled()) {
                Settings.ScreenshotPolicy.DENY_SCREENSHOT
            } else {
                Settings.ScreenshotPolicy.ALLOW_SCREENSHOT
            }
            keyboardDataCollectionPolicy = if (preferenceService.isIncognitoKeyboardRequested) {
                Settings.KeyboardDataCollectionPolicy.DENY_DATA_COLLECTION
            } else {
                Settings.KeyboardDataCollectionPolicy.ALLOW_DATA_COLLECTION
            }
            blockedIdentities = collectBlockedIdentities()
            excludeFromSyncIdentities = collectExcludeFromSyncIdentities()
        }
    }

    private fun collectBlockedIdentities(): Identities {
        return identities {
            identities += blockedIdentitiesService.getAllBlockedIdentities()
        }
    }

    private fun collectExcludeFromSyncIdentities(): Identities {
        return identities {
            identities += excludeFromSyncService.getExcludedIdentities()
        }
    }

    private data class ConversationStats(
        val isArchived: Boolean,
        val isPinned: Boolean,
    )

    private fun collectConversationsStats(): Map<String, ConversationStats> {
        val notArchived = conversationService.getAll(true).associate {
            it.uid to ConversationStats(false, it.isPinTagged)
        }
        val archived = conversationService.getArchived().associate {
            it.uid to ConversationStats(
                isArchived = true,
                isPinned = false,
            )
        }
        return notArchived + archived
    }

    private fun collectContacts(conversationsStats: Map<String, ConversationStats>): List<Pair<List<BlobDataProvider>, AugmentedContactProvider>> {
        return contactModelRepository.getAll()
            .mapNotNull(this::getAndValidateData)
            .map { mapToAugmentedContact(it, conversationsStats) }
            .also { logger.trace("{} contacts", it.size) }
    }

    private fun mapToAugmentedContact(
        contactModelData: ContactModelData,
        conversationsStats: Map<String, ConversationStats>,
    ): Pair<List<BlobDataProvider>, AugmentedContactProvider> {
        val blobDataProviders = mutableListOf<BlobDataProvider>()

        val conversationStats = conversationsStats[ConversationUtil.getIdentityConversationUid(contactModelData.identity)]

        val contactDefinedProfilePictureInfo: Pair<BlobDataProvider, DeltaImage>? = collectContactDefinedProfilePicture(contactModelData)
        val userDefinedProfilePictureInfo: Pair<BlobDataProvider, DeltaImage>? = collectUserDefinedProfilePicture(contactModelData)

        val contact = contact {
            identity = contactModelData.identity
            publicKey = contactModelData.publicKey.toByteString()
            logInvalidTimestamp(contactModelData.createdAt.time, "Contact createdAt (${contactModelData.identity})")
            createdAt = contactModelData.createdAt.time
            firstName = contactModelData.firstName
            lastName = contactModelData.lastName
            nickname = contactModelData.nickname ?: ""
            verificationLevel = mapVerificationLevel(contactModelData)
            workVerificationLevel = mapWorkVerificationLevel(contactModelData)
            identityType = mapIdentityState(contactModelData)
            acquaintanceLevel = mapAcquaintanceLevel(contactModelData)
            activityState = mapActivityState(contactModelData)
            featureMask = contactModelData.featureMask.toLong()
            syncState = collectSyncState(contactModelData)
            readReceiptPolicyOverride = mapReadReceiptPolicyOverride(contactModelData)
            typingIndicatorPolicyOverride = mapTypingIndicatorPolicyOverride(contactModelData)
            notificationTriggerPolicyOverride = collectContactNotificationTriggerPolicyOverride(contactModelData)
            notificationSoundPolicyOverride = collectNotificationSoundPolicyOverride(contactModelData)

            if (contactDefinedProfilePictureInfo != null) {
                blobDataProviders.add(contactDefinedProfilePictureInfo.first)
                contactDefinedProfilePicture = contactDefinedProfilePictureInfo.second
            }

            if (userDefinedProfilePictureInfo != null) {
                blobDataProviders.add(userDefinedProfilePictureInfo.first)
                userDefinedProfilePicture = userDefinedProfilePictureInfo.second
            }

            conversationCategory = if (conversationCategoryService.isPrivateChat(ContactUtil.getUniqueIdString(contactModelData.identity))) {
                MdD2DSync.ConversationCategory.PROTECTED
            } else {
                MdD2DSync.ConversationCategory.DEFAULT
            }

            conversationVisibility = if (conversationStats?.isPinned == true) {
                MdD2DSync.ConversationVisibility.PINNED
            } else if (conversationStats?.isArchived == true) {
                MdD2DSync.ConversationVisibility.ARCHIVED
            } else {
                MdD2DSync.ConversationVisibility.NORMAL
            }
        }

        val augmentedContact = augmentedContact {
            this.contact = contact
            contactService.getLastUpdate(contactModelData.identity)?.let {
                logInvalidTimestamp(it.time, "Contact lastUpdateAt (${contactModelData.identity})")
                this.lastUpdateAt = it.time
            }
        }

        val augmentedContactProvider = AugmentedContactProvider(
            augmentedContact = augmentedContact,
            contactDefinedProfilePictureProvider = contactDefinedProfilePictureInfo?.first,
            userDefinedProfilePictureProvider = userDefinedProfilePictureInfo?.first,
        )

        return blobDataProviders to augmentedContactProvider
    }

    private fun collectSyncState(contactModelData: ContactModelData): SyncState {
        // TODO(ANDR-2327): Consolidate this mechanism
        return if (contactModelData.isLinkedToAndroidContact()) {
            SyncState.IMPORTED
        } else if (contactModelData.lastName.isBlank() && contactModelData.firstName.isBlank()) {
            SyncState.INITIAL
        } else {
            SyncState.CUSTOM
        }
    }

    private fun mapReadReceiptPolicyOverride(contactModelData: ContactModelData): Contact.ReadReceiptPolicyOverride {
        return readReceiptPolicyOverride {
            when (contactModelData.readReceiptPolicy) {
                ReadReceiptPolicy.DEFAULT -> default = unit {}
                ReadReceiptPolicy.SEND -> policy = MdD2DSync.ReadReceiptPolicy.SEND_READ_RECEIPT
                ReadReceiptPolicy.DONT_SEND -> policy = MdD2DSync.ReadReceiptPolicy.DONT_SEND_READ_RECEIPT
            }
        }
    }

    private fun mapTypingIndicatorPolicyOverride(contactModelData: ContactModelData): Contact.TypingIndicatorPolicyOverride {
        return typingIndicatorPolicyOverride {
            when (contactModelData.typingIndicatorPolicy) {
                TypingIndicatorPolicy.DEFAULT -> default = unit {}
                TypingIndicatorPolicy.SEND -> policy = MdD2DSync.TypingIndicatorPolicy.SEND_TYPING_INDICATOR
                TypingIndicatorPolicy.DONT_SEND -> policy = MdD2DSync.TypingIndicatorPolicy.DONT_SEND_TYPING_INDICATOR
            }
        }
    }

    private fun collectContactNotificationTriggerPolicyOverride(contactModelData: ContactModelData): NotificationTriggerPolicyOverride {
        return ContactKt.notificationTriggerPolicyOverride {
            when (val modelPolicy = contactModelData.currentNotificationTriggerPolicyOverride) {
                NotMuted -> default = unit {}

                MutedIndefinite -> policy = ContactKt.NotificationTriggerPolicyOverrideKt.policy {
                    policy = NotificationTriggerPolicy.NEVER
                }

                MutedIndefiniteExceptMentions -> throw IllegalStateException(
                    "Contact receivers can never have this setting",
                )

                is MutedUntil -> policy = ContactKt.NotificationTriggerPolicyOverrideKt.policy {
                    policy = NotificationTriggerPolicy.NEVER
                    logInvalidTimestamp(modelPolicy.utcMillis, "Contact.notificationTriggerPolicyOverride expiresAt (${contactModelData.identity})")
                    expiresAt = modelPolicy.utcMillis
                }
            }
        }
    }

    private fun collectNotificationSoundPolicyOverride(contactModelData: ContactModelData): NotificationSoundPolicyOverride {
        return ContactKt.notificationSoundPolicyOverride {
            if (ringtoneService.isSilent(ContactUtil.getUniqueIdString(contactModelData.identity), false)) {
                policy = MdD2DSync.NotificationSoundPolicy.MUTED
            } else {
                default = unit {}
            }
        }
    }

    private fun collectContactDefinedProfilePicture(contactModelData: ContactModelData): Pair<BlobDataProvider, DeltaImage>? {
        return if (fileService.hasContactDefinedProfilePicture(contactModelData.identity)) {
            createJpegBlobAssets { fileService.getContactDefinedProfilePicture(contactModelData.identity) }
        } else {
            null
        }
    }

    private fun collectUserDefinedProfilePicture(contactModelData: ContactModelData): Pair<BlobDataProvider, DeltaImage>? {
        return if (fileService.hasUserDefinedProfilePicture(contactModelData.identity)) {
            createJpegBlobAssets { fileService.getUserDefinedProfilePicture(contactModelData.identity) }
        } else {
            null
        }
    }

    private fun createJpegBlobAssets(bitmapProvider: () -> Bitmap?): Pair<BlobDataProvider, DeltaImage> {
        val blobId = getNextBlobId()

        val blobDataProvider = BlobDataProvider(blobId) {
            bitmapProvider.invoke()?.let { BitmapUtil.bitmapToJpegByteArray(it) }
        }

        val blobMeta = blob { id = blobId.toByteString() }

        val picture = deltaImage {
            updated = image {
                type = Image.Type.JPEG
                blob = blobMeta
            }
        }
        return blobDataProvider to picture
    }

    private fun mapVerificationLevel(contactModelData: ContactModelData): VerificationLevel {
        return when (contactModelData.verificationLevel) {
            ch.threema.domain.models.VerificationLevel.UNVERIFIED -> VerificationLevel.UNVERIFIED
            ch.threema.domain.models.VerificationLevel.SERVER_VERIFIED -> VerificationLevel.SERVER_VERIFIED
            ch.threema.domain.models.VerificationLevel.FULLY_VERIFIED -> VerificationLevel.FULLY_VERIFIED
        }
    }

    private fun mapWorkVerificationLevel(contactModelData: ContactModelData): Contact.WorkVerificationLevel {
        return when (contactModelData.workVerificationLevel) {
            WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED -> Contact.WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED
            WorkVerificationLevel.NONE -> Contact.WorkVerificationLevel.NONE
        }
    }

    private fun mapIdentityState(contactModelData: ContactModelData): Contact.IdentityType {
        return when (contactModelData.identityType) {
            IdentityType.NORMAL -> Contact.IdentityType.REGULAR
            IdentityType.WORK -> Contact.IdentityType.WORK
        }
    }

    private fun mapAcquaintanceLevel(contactModelData: ContactModelData): Contact.AcquaintanceLevel {
        return when (contactModelData.acquaintanceLevel) {
            AcquaintanceLevel.GROUP -> Contact.AcquaintanceLevel.GROUP_OR_DELETED
            AcquaintanceLevel.DIRECT -> Contact.AcquaintanceLevel.DIRECT
        }
    }

    private fun mapActivityState(contactModelData: ContactModelData): Contact.ActivityState {
        return when (contactModelData.activityState) {
            IdentityState.ACTIVE -> Contact.ActivityState.ACTIVE
            IdentityState.INACTIVE -> Contact.ActivityState.INACTIVE
            IdentityState.INVALID -> Contact.ActivityState.INVALID
        }
    }

    private fun getAndValidateData(contactModel: ContactModel): ContactModelData? {
        val contactModelData = contactModel.data ?: return null

        if (contactModelData.publicKey.size != NaCl.PUBLIC_KEY_BYTES) {
            logger.error("Public key of contact {} has an invalid length: {}", contactModelData.identity, contactModelData.publicKey.size)
            throw DeviceLinkingInvalidContact(contactModel.identity)
        }

        return contactModelData
    }

    private fun collectGroups(conversationsStats: Map<String, ConversationStats>): List<Pair<List<BlobDataProvider>, AugmentedGroupProvider>> {
        return groupModelRepository.getAll()
            .map { mapToAugmentedGroup(it, conversationsStats) }
            .also { logger.trace("{} groups", it.size) }
    }

    private fun mapToAugmentedGroup(
        groupModel: GroupModel,
        conversationsStats: Map<String, ConversationStats>,
    ): Pair<List<BlobDataProvider>, AugmentedGroupProvider> {
        val blobDataProviders = mutableListOf<BlobDataProvider>()

        val conversationStats = conversationsStats[groupModel.getConversationUid()]

        val data = groupModel.data!!

        val groupAvatarInfo = collectGroupAvatar(groupModel)

        val group = group {
            groupIdentity = groupIdentity {
                groupId = groupModel.groupIdentity.groupId
                creatorIdentity = groupModel.groupIdentity.creatorIdentity
            }
            name = data.name ?: ""
            logInvalidTimestamp(data.createdAt.time, "Group createdAt (${groupModel.groupIdentity})")
            createdAt = data.createdAt.time
            userState = collectUserState(data)
            notificationTriggerPolicyOverride =
                collectGroupNotificationTriggerPolicyOverride(groupModel)
            notificationSoundPolicyOverride =
                collectGroupNotificationSoundPolicyOverride(groupModel)
            if (groupAvatarInfo != null) {
                blobDataProviders.add(groupAvatarInfo.first)
                profilePicture = groupAvatarInfo.second
            }
            memberIdentities = collectGroupIdentities(data)
            conversationCategory = if (conversationCategoryService.isPrivateGroupChat(groupModel.getDatabaseId())) {
                MdD2DSync.ConversationCategory.PROTECTED
            } else {
                MdD2DSync.ConversationCategory.DEFAULT
            }
            conversationVisibility = if (conversationStats?.isPinned == true) {
                MdD2DSync.ConversationVisibility.PINNED
            } else if (conversationStats?.isArchived == true) {
                MdD2DSync.ConversationVisibility.ARCHIVED
            } else {
                MdD2DSync.ConversationVisibility.NORMAL
            }
        }

        val augmentedGroup = augmentedGroup {
            this.group = group
            data.lastUpdate?.let {
                logInvalidTimestamp(it.time, "Group lastUpdateAt (${data.groupIdentity})")
                this.lastUpdateAt = it.time
            }
        }

        val augmentedGroupProvider = AugmentedGroupProvider(
            augmentedGroup = augmentedGroup,
            groupProfilePictureProvider = groupAvatarInfo?.first,
        )

        return blobDataProviders to augmentedGroupProvider
    }

    private fun collectGroupAvatar(groupModel: GroupModel): Pair<BlobDataProvider, DeltaImage>? {
        return if (fileService.hasGroupProfilePicture(groupModel)) {
            createJpegBlobAssets { fileService.getGroupProfilePictureBitmap(groupModel) }
        } else {
            null
        }
    }

    private fun collectUserState(groupModelData: GroupModelData): UserState {
        return when (groupModelData.userState) {
            ch.threema.storage.models.GroupModel.UserState.MEMBER -> UserState.MEMBER
            ch.threema.storage.models.GroupModel.UserState.LEFT -> UserState.LEFT
            ch.threema.storage.models.GroupModel.UserState.KICKED -> UserState.KICKED
        }
    }

    /**
     * @return The provided group's member identities NOT including the user itself
     */
    private fun collectGroupIdentities(groupModelData: GroupModelData): Identities {
        return identities {
            identities += groupModelData.otherMembers
        }
    }

    private fun collectGroupNotificationTriggerPolicyOverride(groupModel: GroupModel): MdD2DSync.Group.NotificationTriggerPolicyOverride {
        return GroupKt.notificationTriggerPolicyOverride {
            when (val modelPolicy = groupModel.data?.currentNotificationTriggerPolicyOverride) {
                NotMuted -> default = unit {}

                MutedIndefinite -> policy = GroupKt.NotificationTriggerPolicyOverrideKt.policy {
                    policy = MdD2DSync.Group.NotificationTriggerPolicyOverride.Policy.NotificationTriggerPolicy.NEVER
                }

                MutedIndefiniteExceptMentions -> policy = GroupKt.NotificationTriggerPolicyOverrideKt.policy {
                    policy = MdD2DSync.Group.NotificationTriggerPolicyOverride.Policy.NotificationTriggerPolicy.MENTIONED
                }

                is MutedUntil -> policy = GroupKt.NotificationTriggerPolicyOverrideKt.policy {
                    policy = MdD2DSync.Group.NotificationTriggerPolicyOverride.Policy.NotificationTriggerPolicy.NEVER
                    logInvalidTimestamp(modelPolicy.utcMillis, "Group.notificationTriggerPolicyOverride expiresAt (${groupModel.groupIdentity})")
                    expiresAt = modelPolicy.utcMillis
                }

                null -> throw IllegalStateException("Missing GroupModelData instance in GroupModel")
            }
        }
    }

    private fun collectGroupNotificationSoundPolicyOverride(groupModel: GroupModel): MdD2DSync.Group.NotificationSoundPolicyOverride {
        return GroupKt.notificationSoundPolicyOverride {
            if (ringtoneService.isSilent(groupModel.getUniqueId(), true)) {
                policy = MdD2DSync.NotificationSoundPolicy.MUTED
            } else {
                default = unit {}
            }
        }
    }

    private fun GroupModel.getUniqueId(): String {
        return GroupUtil.getUniqueIdString(this)
    }

    /**
     * Collect the distribution lists and ignore lists without members.
     */
    private fun collectDistributionLists(conversationsStats: Map<String, ConversationStats>): List<AugmentedDistributionList> {
        return distributionListService.all.mapNotNull {
            mapToAugmentedDistributionList(it, conversationsStats)
        }.also { logger.trace("{} distribution lists", it.size) }
    }

    /**
     * Returns null if the [distributionListModel] does not have any members
     */
    private fun mapToAugmentedDistributionList(
        distributionListModel: DistributionListModel,
        conversationsStats: Map<String, ConversationStats>,
    ): AugmentedDistributionList? {
        val conversationStats = conversationsStats[distributionListModel.getConversationUid()]

        return collectDistributionListIdentities(distributionListModel)?.let { identities ->
            distributionList {
                distributionListId = distributionListModel.id
                name = distributionListModel.name ?: ""
                logInvalidTimestamp(distributionListModel.createdAt.time, "DistributionList createdAt (${distributionListModel.id})")
                createdAt = distributionListModel.createdAt.time
                memberIdentities = identities
                conversationCategory =
                    if (conversationCategoryService.isPrivateChat(distributionListModel.getUniqueId())) {
                        MdD2DSync.ConversationCategory.PROTECTED
                    } else {
                        MdD2DSync.ConversationCategory.DEFAULT
                    }
                conversationVisibility = if (conversationStats?.isPinned == true) {
                    MdD2DSync.ConversationVisibility.PINNED
                } else if (conversationStats?.isArchived == true) {
                    MdD2DSync.ConversationVisibility.ARCHIVED
                } else {
                    MdD2DSync.ConversationVisibility.NORMAL
                }
            }
        }?.let {
            augmentedDistributionList {
                this.distributionList = it
                distributionListModel.lastUpdate?.let {
                    logInvalidTimestamp(it.time, "DistributionList lastUpdateAt (${distributionListModel.id})")
                    this.lastUpdateAt = it.time
                }
            }
        }
    }

    private fun DistributionListModel.getUniqueId(): String {
        return distributionListService.getUniqueIdString(this)
    }

    private fun collectDistributionListIdentities(distributionListModel: DistributionListModel): Identities? {
        return distributionListService
            .getDistributionListIdentities(distributionListModel)
            .toList()
            .ifEmpty { null }
            ?.let {
                identities {
                    identities += it
                }
            }
    }

    private fun collectCspNonceHashes(): Set<ByteString> {
        return nonceFactory.getAllHashedNonces(NonceScope.CSP).map { it.bytes.toByteString() }
            .toSet()
            .also { logger.trace("{} csp nonce hashes", it.size) }
    }

    private fun collectD2dNonceHashes(): Set<ByteString> {
        return nonceFactory.getAllHashedNonces(NonceScope.D2D).map { it.bytes.toByteString() }
            .toSet()
            .also { logger.trace("{} d2d nonce hashes", it.size) }
    }

    private fun collectWorkCredentials(): MdD2DSync.ThreemaWorkCredentials? {
        val credentials = licenseService.let {
            if (it is LicenseServiceUser) {
                it.loadCredentials()
            } else {
                null
            }
        }
        return credentials?.let {
            threemaWorkCredentials {
                username = it.username
                password = it.password
            }
        }
    }

    // TODO(ANDR-2670): Collect all mdm parameters
    private fun collectMdmParameters(): MdD2DSync.MdmParameters? {
        // Currently we only send the remote secret mdm parameter
        val remoteSecretMdmParam = context.getString(R.string.restriction__enable_remote_secret)
        val remoteSecretMdmParamValue = AppRestrictionUtil.getBooleanRestriction(remoteSecretMdmParam)
        if (remoteSecretMdmParamValue != null) {
            logger.info("Including remote secret mdm parameter")
            return mdmParameters {
                // Note that we currently set it as a threema parameter as we can't distinguish it easily here.
                threemaParameters.put(
                    remoteSecretMdmParam,
                    parameter {
                        booleanValue = remoteSecretMdmParamValue
                    },
                )
            }
        }

        // In case the remote secret mdm parameter is not set, we don't include any mdm parameters
        return null
    }

    /**
     * If the provided [timestamp] is null or invalid, it will be logged with the provided [message].
     * Otherwise this method has no effect.
     */
    private fun logInvalidTimestamp(timestamp: Long?, message: String) {
        if (timestamp == null) {
            logger.warn("Null timestamp: {}", message)
            return
        }
        // 8_640_000_000_000_000L is the maximum timestamp that is supported by threema desktop
        if (timestamp < 0L || timestamp > 8_640_000_000_000_000L) {
            logger.error("Invalid timestamp {}: {}", timestamp, message)
        }
    }

    private companion object {
        private var nextBlobId = 1L
        fun getNextBlobId(): ByteArray {
            return ByteBuffer.wrap(ByteArray(ProtocolDefines.BLOB_ID_LEN))
                .putLong(nextBlobId++)
                .array()
                .also {
                    check(it.size == ProtocolDefines.BLOB_ID_LEN) { "Invalid blob id generated" }
                }
        }
    }
}

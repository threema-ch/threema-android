/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2024 Threema GmbH
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
import ch.threema.app.managers.ServiceManager
import ch.threema.app.services.ContactService
import ch.threema.app.services.DeadlineListService
import ch.threema.app.services.license.LicenseServiceUser
import ch.threema.app.utils.BitmapUtil
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.ContactUtil
import ch.threema.app.utils.ConversationUtil.getConversationUid
import ch.threema.base.crypto.NonceScope
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
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
import ch.threema.protobuf.d2d.join.essentialData
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
import ch.threema.protobuf.d2d.sync.UserProfileKt.IdentityLinksKt.identityLink
import ch.threema.protobuf.d2d.sync.UserProfileKt.identityLinks
import ch.threema.protobuf.d2d.sync.UserProfileKt.profilePictureShareWith
import ch.threema.protobuf.d2d.sync.contact
import ch.threema.protobuf.d2d.sync.distributionList
import ch.threema.protobuf.d2d.sync.group
import ch.threema.protobuf.d2d.sync.settings
import ch.threema.protobuf.d2d.sync.threemaWorkCredentials
import ch.threema.protobuf.d2d.sync.userProfile
import ch.threema.protobuf.deltaImage
import ch.threema.protobuf.groupIdentity
import ch.threema.protobuf.identities
import ch.threema.protobuf.image
import ch.threema.protobuf.unit
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.DistributionListModel
import ch.threema.storage.models.GroupModel
import com.google.protobuf.ByteString
import java.nio.ByteBuffer

private val logger = LoggingUtil.getThreemaLogger("DeviceLinkingDataCollector")

data class DeviceLinkingData(val blobs: Sequence<BlobData>, val essentialData: EssentialData)

class BlobDataProvider(private val blobId: ByteArray?, private val dataProvider: () -> ByteArray?) {
    /**
     * Get this providers [BlobData].
     *
     * @return [BlobData] or null if no blobId or actual data is available
     */
    fun get(): BlobData? {
        if (blobId == null) {
            return null
        }

        logger.debug("Invoke blob data provider")
        return dataProvider.invoke()?.toByteString()?.let {
            blobData {
                id = blobId.toByteString()
                data = it
            }
        }
    }
}

class DeviceLinkingDataCollector(
    serviceManager: ServiceManager
) {
    private val identityStore by lazy { serviceManager.identityStore }
    private val userService by lazy { serviceManager.userService }
    private val contactService by lazy { serviceManager.contactService }
    private val groupService by lazy { serviceManager.groupService }
    private val distributionListService by lazy { serviceManager.distributionListService }
    private val deviceCookieManager by lazy { serviceManager.deviceCookieManager }
    private val preferenceService by lazy { serviceManager.preferenceService }
    private val blockedIdentitiesService by lazy { serviceManager.blockedIdentitiesService }
    private val excludeFromSyncService by lazy { serviceManager.excludedSyncIdentitiesService }
    private val fileService by lazy { serviceManager.fileService }
    private val hiddenChatsService by lazy { serviceManager.hiddenChatsListService }
    private val conversationService by lazy { serviceManager.conversationService }
    private val mutedChatsService by lazy { serviceManager.mutedChatsListService }
    private val mentionOnlyChatsService by lazy { serviceManager.mentionOnlyChatsListService }
    private val ringtoneService by lazy { serviceManager.ringtoneService }
    private val nonceFactory by lazy { serviceManager.nonceFactory }
    private val licenseService by lazy { serviceManager.licenseService }

    @WorkerThread
    fun collectData(dgk: ByteArray): DeviceLinkingData {
        val blobDataProviders = mutableListOf<BlobDataProvider>()

        val data = essentialData {
            logger.trace("Collect identity data")
            this.identityData = collectIdentityData()

            logger.trace("Collect device group data")
            this.deviceGroupData = deviceGroupData {
                this.dgk = dgk.toByteString()
            }

            logger.trace("Collect user profile")
            val (userProfileBlobProvider, userProfileData) = collectUserProfile()
            userProfileBlobProvider?.let {
                blobDataProviders.add(it)
            }
            this.userProfile = userProfileData

            logger.trace("Collect settings")
            this.settings = collectSettings()

            val conversationsStats = collectConversationsStats()

            logger.trace("Collect contacts")
            collectContacts(conversationsStats).forEach { (contactBlobDataProviders, contact) ->
                blobDataProviders.addAll(contactBlobDataProviders)
                this.contacts += contact
            }

            logger.trace("Collect groups")
            collectGroups(conversationsStats).forEach { (groupBlobDataProviders, group) ->
                blobDataProviders.addAll(groupBlobDataProviders)
                this.groups += group
            }

            if (BuildConfig.MD_SYNC_DISTRIBUTION_LISTS) {
                logger.trace("Collect distribution lists")
                this.distributionLists += collectDistributionLists(conversationsStats)
            } else {
                logger.trace("Skip collection of distribution lists")
                this.distributionLists.clear()
            }

            logger.trace("Collect csp nonce hashes")
            this.cspHashedNonces += collectCspNonceHashes()

            logger.trace("Collect d2d nonce hashes")
            this.d2DHashedNonces += collectD2dNonceHashes()

            // work
            if (ConfigUtils.isWorkBuild()) {
                logger.trace("Collect work credentials")
                this.workCredentials = collectWorkCredentials()
                    ?: throw IllegalStateException("No work credentials available in work build")

                // mdm parameters TODO(ANDR-2670)
                //            this.mdmParameters = collectMdmParameters()
            }
        }

        logger.debug("Number of blobDataProviders: {}", blobDataProviders.size)
        val blobsSequence = blobDataProviders
            .asSequence()
            .mapNotNull { it.get() }

        return DeviceLinkingData(blobsSequence, data)
    }

    private fun collectIdentityData(): IdentityData {
        return identityData {
            identity = identityStore.identity
            ck = identityStore.privateKey.toByteString()
            cspDeviceCookie = deviceCookieManager.obtainDeviceCookie().toByteString()
            cspServerGroup = identityStore.serverGroup
        }
    }

    private fun collectUserProfile(): Pair<BlobDataProvider?, MdD2DSync.UserProfile> {
        return collectUserProfilePicture().let { profilePictureData ->
            profilePictureData?.first to userProfile {
                nickname = identityStore.publicNickname
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

        val hasProfilePicture = profilePictureData.blobId != null
            && !profilePictureData.blobId.contentEquals(ContactModel.NO_PROFILE_PICTURE_BLOB_ID)

        return if (hasProfilePicture) {
            val blobMeta = blob {
                id = profilePictureData.blobId.toByteString()
                nonce = ProtocolDefines.CONTACT_PHOTO_NONCE.toByteString()
                key = profilePictureData.encryptionKey.toByteString()
                uploadedAt = profilePictureData.uploadedAt
            }

            val profilePicture = deltaImage {
                updated = image {
                    type = Image.Type.JPEG
                    blob = blobMeta
                }
            }

            val blobDataProvider = BlobDataProvider(profilePictureData.blobId) {
                profilePictureData.bitmapArray
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
        return identityLinks {
            userService.linkedMobileE164?.let {
                links += identityLink { phoneNumber = it }
            }
            userService.linkedEmail?.let {
                links += identityLink { email = it }
            }
        }
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
            readReceiptPolicy = if (preferenceService.isReadReceipts) {
                MdD2DSync.ReadReceiptPolicy.SEND_READ_RECEIPT
            } else {
                MdD2DSync.ReadReceiptPolicy.DONT_SEND_READ_RECEIPT
            }
            typingIndicatorPolicy = if (preferenceService.isTypingIndicator) {
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
            o2OCallVideoPolicy = if (preferenceService.isVideoCallsEnabled) {
                Settings.O2oCallVideoPolicy.ALLOW_VIDEO
            } else {
                Settings.O2oCallVideoPolicy.DENY_VIDEO
            }
            groupCallPolicy = if (preferenceService.isGroupCallsEnabled) {
                Settings.GroupCallPolicy.ALLOW_GROUP_CALL
            } else {
                Settings.GroupCallPolicy.DENY_GROUP_CALL
            }
            screenshotPolicy = if (preferenceService.isDisableScreenshots) {
                Settings.ScreenshotPolicy.DENY_SCREENSHOT
            } else {
                Settings.ScreenshotPolicy.ALLOW_SCREENSHOT
            }
            keyboardDataCollectionPolicy = if (preferenceService.incognitoKeyboard) {
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
            identities += excludeFromSyncService.all.toSet()
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
        val archived = conversationService.getArchived(null).associate {
            it.uid to ConversationStats(
                isArchived = true,
                isPinned = false
            )
        }
        return notArchived + archived
    }

    private fun collectContacts(conversationsStats: Map<String, ConversationStats>): List<Pair<List<BlobDataProvider>, AugmentedContact>> {
        return contactService.all
            .map { mapToAugmentedContact(it, conversationsStats) }
            .also { logger.trace("{} contacts", it.size) }
    }

    private fun mapToAugmentedContact(
        contactModel: ContactModel,
        conversationsStats: Map<String, ConversationStats>
    ): Pair<List<BlobDataProvider>, AugmentedContact> {
        val blobDataProviders = mutableListOf<BlobDataProvider>()

        val conversationStats = conversationsStats[contactModel.getConversationUid()]

        val contact = contact {
            identity = contactModel.identity
            publicKey = contactModel.publicKey.toByteString()
            createdAt = contactModel.dateCreated?.time ?: System.currentTimeMillis()
            firstName = contactModel.firstName ?: ""
            lastName = contactModel.lastName ?: ""
            nickname = contactModel.publicNickName ?: ""
            verificationLevel = mapVerificationLevel(contactModel)
            workVerificationLevel = when {
                contactModel.isWork -> Contact.WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED
                else -> Contact.WorkVerificationLevel.NONE
            }
            identityType = when (contactModel.identityType) {
                IdentityType.NORMAL -> Contact.IdentityType.REGULAR
                IdentityType.WORK -> Contact.IdentityType.WORK
                else -> Contact.IdentityType.UNRECOGNIZED
            }
            acquaintanceLevel = when (contactModel.acquaintanceLevel) {
                ContactModel.AcquaintanceLevel.GROUP -> Contact.AcquaintanceLevel.GROUP_OR_DELETED
                ContactModel.AcquaintanceLevel.DIRECT -> Contact.AcquaintanceLevel.DIRECT
            }
            activityState = when (contactModel.state) {
                IdentityState.ACTIVE -> Contact.ActivityState.ACTIVE
                IdentityState.INACTIVE -> Contact.ActivityState.INACTIVE
                IdentityState.INVALID -> Contact.ActivityState.INVALID
                else -> throw IllegalStateException("Contact ${contactModel.identity} has missing state")
            }
            featureMask = contactModel.featureMask
            syncState = collectSyncState(contactModel)
            readReceiptPolicyOverride = readReceiptPolicyOverride {
                val readReceipts = contactModel.readReceipts
                if (readReceipts == ContactModel.DEFAULT) {
                    default = unit {}
                } else {
                    policy = when (readReceipts) {
                        ContactModel.DONT_SEND -> MdD2DSync.ReadReceiptPolicy.DONT_SEND_READ_RECEIPT
                        ContactModel.SEND -> MdD2DSync.ReadReceiptPolicy.SEND_READ_RECEIPT
                        else -> throw IllegalStateException("Invalid read receipt policy override $readReceipts for contact ${contactModel.identity}")
                    }
                }
            }
            typingIndicatorPolicyOverride = typingIndicatorPolicyOverride {
                if (contactModel.typingIndicators == ContactModel.DEFAULT) {
                    default = unit {}
                } else {
                    policy = when (contactModel.typingIndicators) {
                        ContactModel.DONT_SEND -> MdD2DSync.TypingIndicatorPolicy.DONT_SEND_TYPING_INDICATOR
                        ContactModel.SEND -> MdD2DSync.TypingIndicatorPolicy.SEND_TYPING_INDICATOR
                        else -> throw IllegalStateException("Invalid typing indicator policy override ${contactModel.typingIndicators} for contact ${contactModel.identity}")
                    }
                }
            }
            notificationTriggerPolicyOverride =
                collectContactNotificationTriggerPolicyOverride(contactModel)
            notificationSoundPolicyOverride = collectNotificationSoundPolicyOverride(contactModel)

            collectContactDefinedProfilePicture(contactModel)?.let { (blobDataProvider, image) ->
                blobDataProviders.add(blobDataProvider)
                contactDefinedProfilePicture = image
            }
            collectUserDefinedProfilePicture(contactModel)?.let { (blobDataProvider, image) ->
                blobDataProviders.add(blobDataProvider)
                userDefinedProfilePicture = image
            }

            conversationCategory = if (hiddenChatsService.has(contactModel.getUniqueId())) {
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
            contactModel.lastUpdate?.let { this.lastUpdateAt = it.time }
        }

        return blobDataProviders to augmentedContact
    }

    private fun collectSyncState(contactModel: ContactModel): SyncState {
        // TODO(ANDR-2327): Consolidate this mechanism
        return if (contactModel.isLinkedToAndroidContact) {
            SyncState.IMPORTED
        } else if (contactModel.lastName.isNullOrBlank() && contactModel.firstName.isNullOrBlank()) {
            SyncState.INITIAL
        } else {
            SyncState.CUSTOM
        }
    }

    private fun collectContactNotificationTriggerPolicyOverride(contactModel: ContactModel): NotificationTriggerPolicyOverride {
        val mutedUntil = mutedChatsService.getDeadline(contactModel.getUniqueId())
        return if (mutedUntil == DeadlineListService.DEADLINE_INDEFINITE || mutedUntil > 0) {
            ContactKt.notificationTriggerPolicyOverride {
                policy = ContactKt.NotificationTriggerPolicyOverrideKt.policy {
                    policy = NotificationTriggerPolicy.NEVER
                    if (mutedUntil > 0) {
                        expiresAt = mutedUntil
                    }
                }
            }
        } else {
            ContactKt.notificationTriggerPolicyOverride {
                default = unit {}
            }
        }
    }

    private fun collectNotificationSoundPolicyOverride(contactModel: ContactModel): NotificationSoundPolicyOverride {
        return ContactKt.notificationSoundPolicyOverride {
            if (ringtoneService.isSilent(contactModel.getUniqueId(), false)) {
                policy = MdD2DSync.NotificationSoundPolicy.MUTED
            } else {
                default = unit {}
            }
        }
    }

    private fun ContactModel.getUniqueId(): String {
        return ContactUtil.getUniqueIdString(identity)
    }

    private fun collectContactDefinedProfilePicture(contactModel: ContactModel): Pair<BlobDataProvider, DeltaImage>? {
        return if (fileService.hasContactDefinedProfilePicture(contactModel.identity)) {
            createJpegBlobAssets { fileService.getContactDefinedProfilePicture(contactModel.identity) }
        } else {
            null
        }
    }

    private fun collectUserDefinedProfilePicture(contactModel: ContactModel): Pair<BlobDataProvider, DeltaImage>? {
        return if (fileService.hasUserDefinedProfilePicture(contactModel.identity)) {
            createJpegBlobAssets { fileService.getUserDefinedProfilePicture(contactModel.identity) }
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

    private fun mapVerificationLevel(contactModel: ContactModel): VerificationLevel {
        return when (contactModel.verificationLevel) {
            ch.threema.domain.models.VerificationLevel.UNVERIFIED -> VerificationLevel.UNVERIFIED
            ch.threema.domain.models.VerificationLevel.SERVER_VERIFIED -> VerificationLevel.SERVER_VERIFIED
            ch.threema.domain.models.VerificationLevel.FULLY_VERIFIED -> VerificationLevel.FULLY_VERIFIED
        }
    }

    private fun collectGroups(conversationsStats: Map<String, ConversationStats>): List<Pair<List<BlobDataProvider>, AugmentedGroup>> {
        return groupService.all
            .filter { !it.isDeleted }
            .map { mapToAugmentedGroup(it, conversationsStats) }
            .also { logger.trace("{} groups", it.size) }
    }

    private fun mapToAugmentedGroup(
        groupModel: GroupModel,
        conversationsStats: Map<String, ConversationStats>
    ): Pair<List<BlobDataProvider>, AugmentedGroup> {
        val blobDataProviders = mutableListOf<BlobDataProvider>()

        val conversationStats = conversationsStats[groupModel.getConversationUid()]

        val group = group {
            groupIdentity = groupIdentity {
                groupId = groupModel.apiGroupId.toLong()
                creatorIdentity = groupModel.creatorIdentity
            }
            name = groupModel.name ?: ""
            createdAt = groupModel.createdAt.time
            userState = collectUserState(groupModel)
            notificationTriggerPolicyOverride =
                collectGroupNotificationTriggerPolicyOverride(groupModel)
            notificationSoundPolicyOverride =
                collectGroupNotificationSoundPolicyOverride(groupModel)
            collectGroupAvatar(groupModel)?.let { (groupAvatarBlobDataProvider, image) ->
                blobDataProviders.add(groupAvatarBlobDataProvider)
                profilePicture = image
            }
            memberIdentities = collectGroupIdentities(groupModel)
            conversationCategory = if (hiddenChatsService.has(groupModel.getUniqueId())) {
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
            groupModel.lastUpdate?.let {
                this.lastUpdateAt = it.time
            }
        }
        return blobDataProviders to augmentedGroup
    }

    private fun collectGroupAvatar(groupModel: GroupModel): Pair<BlobDataProvider, DeltaImage>? {
        return if (fileService.hasGroupAvatarFile(groupModel)) {
            createJpegBlobAssets { fileService.getGroupAvatar(groupModel) }
        } else {
            null
        }
    }

    private fun collectUserState(groupModel: GroupModel) = when (groupModel.userState) {
        GroupModel.UserState.MEMBER -> UserState.MEMBER
        GroupModel.UserState.KICKED -> UserState.KICKED
        GroupModel.UserState.LEFT -> UserState.LEFT
        null -> {
            logger.warn("User state of group model is null; using member as default")
            UserState.MEMBER
        }
    }

    /**
     * @return The provided group's member identities NOT including the user itself
     */
    private fun collectGroupIdentities(groupModel: GroupModel): Identities {
        return identities {
            identities += groupService
                .getGroupIdentities(groupModel)
                .filter { it != identityStore.identity }
        }
    }

    private fun collectGroupNotificationTriggerPolicyOverride(groupModel: GroupModel): MdD2DSync.Group.NotificationTriggerPolicyOverride {
        val uid = groupModel.getUniqueId()

        return if (mentionOnlyChatsService.has(uid)) {
            GroupKt.notificationTriggerPolicyOverride {
                policy = GroupKt.NotificationTriggerPolicyOverrideKt.policy {
                    policy =
                        MdD2DSync.Group.NotificationTriggerPolicyOverride.Policy.NotificationTriggerPolicy.MENTIONED
                }
            }
        } else {
            val mutedUntil = mutedChatsService.getDeadline(uid)
            if (mutedUntil == DeadlineListService.DEADLINE_INDEFINITE || mutedUntil > 0) {
                GroupKt.notificationTriggerPolicyOverride {
                    policy = GroupKt.NotificationTriggerPolicyOverrideKt.policy {
                        policy =
                            MdD2DSync.Group.NotificationTriggerPolicyOverride.Policy.NotificationTriggerPolicy.NEVER
                        if (mutedUntil > 0) {
                            expiresAt = mutedUntil
                        }
                    }
                }
            } else {
                GroupKt.notificationTriggerPolicyOverride {
                    default = unit {}
                }
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
        return groupService.getUniqueIdString(this)
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
        conversationsStats: Map<String, ConversationStats>
    ): AugmentedDistributionList? {
        val conversationStats = conversationsStats[distributionListModel.getConversationUid()]

        return collectDistributionListIdentities(distributionListModel)?.let { identities ->
            distributionList {
                distributionListId = distributionListModel.id
                name = distributionListModel.name ?: ""
                createdAt = distributionListModel.createdAt.time
                memberIdentities = identities
                conversationCategory =
                    if (hiddenChatsService.has(distributionListModel.getUniqueId())) {
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

    //    TODO(ANDR-2670)
//    private fun collectMdmParameters(): MdmParameters {
//        return mdmParameters {
//            externalParameters = ...
//            threemaParameters = ...
//            parameterPrecedence = ...
//        }
//    }

    private companion object {
        private var nextBlobId = 1L
        fun getNextBlobId(): ByteArray {
            return ByteBuffer.wrap(ByteArray(ProtocolDefines.BLOB_ID_LEN))
                .putLong(nextBlobId++)
                .array()
                .also {
                    if (it.size != ProtocolDefines.BLOB_ID_LEN) {
                        throw IllegalStateException("Invalid blob id generated")
                    }
                }
        }
    }
}

private fun ByteArray.toByteString(): ByteString {
    return ByteString.copyFrom(this)
}

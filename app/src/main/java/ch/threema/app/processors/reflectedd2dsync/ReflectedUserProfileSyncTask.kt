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

package ch.threema.app.processors.reflectedd2dsync

import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.profilepicture.RawProfilePicture
import ch.threema.app.services.ContactService.ProfilePictureUploadData
import ch.threema.app.services.IdListService
import ch.threema.app.services.UserService
import ch.threema.base.crypto.SymmetricEncryptionService
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.models.AppVersion
import ch.threema.domain.protocol.ServerAddressProvider
import ch.threema.domain.protocol.blob.BlobScope
import ch.threema.domain.protocol.connection.d2m.MultiDevicePropertyProvider
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.taskmanager.ProtocolException
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.protobuf.Common.DeltaImage
import ch.threema.protobuf.d2d.MdD2D.UserProfileSync
import ch.threema.protobuf.d2d.sync.MdD2DSync.UserProfile
import ch.threema.protobuf.d2d.sync.MdD2DSync.UserProfile.IdentityLinks.IdentityLink
import ch.threema.protobuf.d2d.sync.MdD2DSync.UserProfile.ProfilePictureShareWith
import ch.threema.protobuf.d2d.userProfileOrNull
import okhttp3.OkHttpClient

private val logger = LoggingUtil.getThreemaLogger("ReflectedUserProfileSyncTask")

class ReflectedUserProfileSyncTask(
    private val userProfileSync: UserProfileSync,
    private val userService: UserService,
    private val okHttpClient: OkHttpClient,
    private val serverAddressProvider: ServerAddressProvider,
    private val multiDevicePropertyProvider: MultiDevicePropertyProvider,
    private val symmetricEncryptionService: SymmetricEncryptionService,
    private val appVersion: AppVersion,
    private val preferenceService: PreferenceService,
    private val profilePicRecipientsService: IdListService,
) {

    fun run() {
        when (userProfileSync.actionCase) {
            UserProfileSync.ActionCase.UPDATE -> handleUserProfileSyncUpdate(userProfileSync.update)
            UserProfileSync.ActionCase.ACTION_NOT_SET -> logger.warn("No action set for user profile sync")
            null -> logger.warn("Action is null for user profile sync")
        }
    }

    private fun handleUserProfileSyncUpdate(userProfileSyncUpdate: UserProfileSync.Update) {
        logger.info("Processing reflected user profile sync update")

        val userProfile = userProfileSyncUpdate.userProfileOrNull ?: run {
            logger.warn("No user profile available in user profile sync")
            return
        }

        applyReflectedNickname(userProfile)
        applyReflectedProfilePicture(userProfile)
        applyReflectedProfilePictureShareWith(userProfile)
        applyReflectedIdentityLinks(userProfile)
    }

    private fun applyReflectedNickname(userProfile: UserProfile) {
        if (!userProfile.hasNickname()) {
            return
        }

        logger.info("Received nickname in user profile sync")
        userService.setPublicNickname(userProfile.nickname, TriggerSource.SYNC)
    }

    private fun applyReflectedProfilePicture(userProfile: UserProfile) {
        if (!userProfile.hasProfilePicture()) {
            return
        }

        when (userProfile.profilePicture.imageCase) {
            DeltaImage.ImageCase.UPDATED -> {
                logger.info("Received updated profile picture in user profile sync")
                loadAndPersistUserProfilePicture(userProfile.profilePicture)
            }

            DeltaImage.ImageCase.REMOVED -> {
                logger.info("Received removed profile picture in user profile sync")
                userService.removeUserProfilePicture(TriggerSource.SYNC)
            }

            DeltaImage.ImageCase.IMAGE_NOT_SET -> logger.warn("Image is not set in profile picture")
            null -> logger.warn("Image is null in profile picture")
        }
    }

    private fun applyReflectedProfilePictureShareWith(userProfile: UserProfile) {
        if (!userProfile.hasProfilePictureShareWith()) {
            return
        }

        when (userProfile.profilePictureShareWith.policyCase) {
            ProfilePictureShareWith.PolicyCase.NOBODY -> {
                logger.info("Apply sharing profile picture with nobody")
                preferenceService.profilePicRelease = PreferenceService.PROFILEPIC_RELEASE_NOBODY
            }
            ProfilePictureShareWith.PolicyCase.EVERYONE -> {
                logger.info("Apply sharing profile picture with everyone")
                preferenceService.profilePicRelease = PreferenceService.PROFILEPIC_RELEASE_EVERYONE
            }
            ProfilePictureShareWith.PolicyCase.ALLOW_LIST -> {
                logger.info("Apply sharing profile picture with allow list")
                preferenceService.profilePicRelease = PreferenceService.PROFILEPIC_RELEASE_ALLOW_LIST
                profilePicRecipientsService.replaceAll(userProfile.profilePictureShareWith.allowList.identitiesList.toTypedArray())
            }
            ProfilePictureShareWith.PolicyCase.POLICY_NOT_SET -> {
                logger.warn("Profile picture share with policy is not set")
            }
            null -> {
                logger.warn("Profile picture share with policy is null")
            }
        }
    }

    private fun applyReflectedIdentityLinks(userProfile: UserProfile) {
        if (!userProfile.hasIdentityLinks()) {
            return
        }

        // Note: We only consider the first provided phone link. If there is no phone identity link, we remove the locally persisted linked phone
        // number.
        val phoneIdentityLink = userProfile.identityLinks.linksList.firstOrNull { identityLink ->
            identityLink.typeCase == IdentityLink.TypeCase.PHONE_NUMBER
        }
        userService.persistPhoneIdentityLinkFromSync(phoneIdentityLink?.phoneNumber, TriggerSource.SYNC)

        // Note: We only consider the first provided email link. If there is no email identity link, we remove the locally persisted linked email
        // address.
        val emailIdentityLink = userProfile.identityLinks.linksList.firstOrNull { identityLink ->
            identityLink.typeCase == IdentityLink.TypeCase.EMAIL
        }
        userService.persistEmailIdentityLinkFromSync(emailIdentityLink?.email, TriggerSource.SYNC)

        // Do some sanity checks on provided identity links. This just logs if there are ignored or invalid identity links.
        userProfile.identityLinks.linksList.forEach { identityLink ->
            when (identityLink.typeCase) {
                IdentityLink.TypeCase.PHONE_NUMBER -> {
                    if (phoneIdentityLink?.phoneNumber != identityLink.phoneNumber) {
                        logger.warn("Ignoring identity link of type phone because we only can store one linked phone number")
                    }
                }
                IdentityLink.TypeCase.EMAIL -> {
                    if (emailIdentityLink?.email != identityLink.email) {
                        logger.warn("Ignoring identity link of type email because we only can store one linked email address")
                    }
                }
                IdentityLink.TypeCase.TYPE_NOT_SET -> {
                    logger.warn("Ignoring identity link as type is not set")
                }
                null -> {
                    logger.warn("Ignoring identity link as type is null")
                }
            }
        }
    }

    private fun loadAndPersistUserProfilePicture(profilePicture: DeltaImage) {
        val blobLoadingResult: ReflectedBlobDownloader.BlobLoadingResult = profilePicture.updated.blob.loadAndMarkAsDone(
            okHttpClient = okHttpClient,
            version = appVersion,
            serverAddressProvider = serverAddressProvider,
            multiDevicePropertyProvider = multiDevicePropertyProvider,
            symmetricEncryptionService = symmetricEncryptionService,
            fallbackNonce = ProtocolDefines.CONTACT_PHOTO_NONCE,
            downloadBlobScope = BlobScope.Local,
            markAsDoneBlobScope = BlobScope.Local,
        )
        when (blobLoadingResult) {
            is ReflectedBlobDownloader.BlobLoadingResult.Success -> {
                val profilePictureUploadData = ProfilePictureUploadData().apply {
                    this.profilePicture = RawProfilePicture(blobLoadingResult.blobBytes)
                    this.blobId = profilePicture.updated.blob.id.toByteArray()
                    this.encryptionKey = profilePicture.updated.blob.key.toByteArray()
                    this.size = blobLoadingResult.blobSize
                    this.uploadedAt = profilePicture.updated.blob.uploadedAt
                }
                userService.setUserProfilePictureFromSync(profilePictureUploadData, TriggerSource.SYNC)
            }

            is ReflectedBlobDownloader.BlobLoadingResult.BlobMirrorNotAvailable -> {
                logger.warn("Cannot download blob because blob mirror is not available", blobLoadingResult.exception)
                throw ProtocolException("Blob mirror not available")
            }

            is ReflectedBlobDownloader.BlobLoadingResult.DecryptionFailed -> {
                logger.warn("Could not decrypt user profile picture blob", blobLoadingResult.exception)
            }

            is ReflectedBlobDownloader.BlobLoadingResult.BlobNotFound -> {
                logger.warn("Could not download user profile picture because the blob was not found")
            }

            is ReflectedBlobDownloader.BlobLoadingResult.BlobDownloadCancelled -> {
                logger.error("Could not download user profile picture because the download was cancelled")
            }

            is ReflectedBlobDownloader.BlobLoadingResult.Other -> {
                logger.error("Could not download user profile picture because of an exception", blobLoadingResult.exception)
            }
        }
    }
}

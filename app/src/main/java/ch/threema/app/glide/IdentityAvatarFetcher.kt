/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2025 Threema GmbH
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

package ch.threema.app.glide

import android.content.Context
import android.graphics.Bitmap
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import ch.threema.app.R
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.AvatarCacheServiceImpl
import ch.threema.app.services.ContactService
import ch.threema.app.services.UserService
import ch.threema.app.utils.AndroidContactUtil
import ch.threema.app.utils.AvatarConverterUtil
import ch.threema.app.utils.ContactUtil
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.datatypes.IdColor
import ch.threema.data.models.ContactModel
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.types.Identity
import com.bumptech.glide.Priority
import com.bumptech.glide.load.data.DataFetcher

private val logger = LoggingUtil.getThreemaLogger("IdentityAvatarFetcher")

/**
 * This class is used to get the avatars from the database or create the default avatars. The results of the loaded bitmaps will be cached by glide (if possible).
 * Avatars are referenced by the threema identity. Therefore, this fetcher can also deal with the user's own profile picture.
 */
class IdentityAvatarFetcher(
    context: Context,
    private val userService: UserService?,
    private val contactService: ContactService?,
    private val contactModelRepository: ContactModelRepository?,
    private val identityAvatarConfig: AvatarCacheServiceImpl.IdentityAvatarConfig,
    private val preferenceService: PreferenceService?,
) : AvatarFetcher(context) {
    private val identityDefaultAvatar: VectorDrawableCompat? by lazy {
        VectorDrawableCompat.create(
            context.resources,
            R.drawable.ic_contact,
            null,
        )
    }
    private val identityGatewayAvatar: VectorDrawableCompat? by lazy {
        VectorDrawableCompat.create(
            context.resources,
            R.drawable.ic_business,
            null,
        )
    }

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
        val highRes = identityAvatarConfig.options.highRes
        // Show profile picture from contact (if set)
        val profilePicReceive: Boolean
        // Show default avatar
        val defaultAvatar: Boolean
        // Return default avatar if no other avatar is found
        val returnDefaultIfNone: Boolean
        when (identityAvatarConfig.options.defaultAvatarPolicy) {
            AvatarOptions.DefaultAvatarPolicy.DEFAULT_FALLBACK -> {
                profilePicReceive = preferenceService?.profilePicReceive == true
                defaultAvatar = false
                returnDefaultIfNone = true
            }

            AvatarOptions.DefaultAvatarPolicy.CUSTOM_AVATAR -> {
                profilePicReceive = true
                defaultAvatar = false
                returnDefaultIfNone = false
            }

            AvatarOptions.DefaultAvatarPolicy.DEFAULT_AVATAR -> {
                profilePicReceive = false
                defaultAvatar = true
                returnDefaultIfNone = true
            }
        }
        val backgroundColor = getBackgroundColor(identityAvatarConfig.options)

        val identity = identityAvatarConfig.subject
        val avatar = if (identity != null && userService?.isMe(identity) == true) {
            getUserDefinedProfilePicture(identity, highRes)
                ?: if (returnDefaultIfNone) {
                    buildDefaultAvatar(identity = identity, highRes, backgroundColor)
                } else {
                    null
                }
        } else {
            if (defaultAvatar) {
                buildDefaultAvatar(identity, highRes, backgroundColor)
            } else {
                val contactModel = identity?.let { contactModelRepository?.getByIdentity(it) }
                loadContactAvatar(
                    contactModel,
                    highRes,
                    profilePicReceive,
                    returnDefaultIfNone,
                    backgroundColor,
                )
            }
        }

        callback.onDataReady(avatar)
    }

    private fun loadContactAvatar(
        contactModel: ContactModel?,
        highRes: Boolean,
        profilePicReceive: Boolean,
        returnDefaultIfNone: Boolean,
        backgroundColor: Int,
    ): Bitmap? {
        if (contactModel == null) {
            return buildDefaultAvatar(null, highRes, backgroundColor)
        }

        // Try the contact defined profile picture
        if (profilePicReceive) {
            getContactDefinedProfilePicture(contactModel.identity, highRes)?.let {
                return it
            }
        }

        // Try the user defined profile picture
        getUserDefinedProfilePicture(contactModel.identity, highRes)?.let {
            return it
        }

        // Try the android defined profile picture
        getAndroidDefinedProfilePicture(contactModel, highRes)?.let {
            return it
        }

        return if (returnDefaultIfNone) {
            buildDefaultAvatar(contactModel.identity, highRes, backgroundColor)
        } else {
            null
        }
    }

    private fun getContactDefinedProfilePicture(
        identity: String,
        highRes: Boolean,
    ): Bitmap? {
        try {
            val result = fileService?.getContactDefinedProfilePicture(identity)
            if (result != null && !highRes) {
                return AvatarConverterUtil.convert(this.context.resources, result)
            }
            return result
        } catch (e: Exception) {
            logger.error("Could not get contact defined profile picture", e)
            return null
        }
    }

    private fun getUserDefinedProfilePicture(
        identity: Identity,
        highRes: Boolean,
    ): Bitmap? {
        return try {
            var result = fileService?.getUserDefinedProfilePicture(identity)
            if (result != null && !highRes) {
                result = AvatarConverterUtil.convert(this.context.resources, result)
            }
            result
        } catch (e: Exception) {
            logger.error("Could not get user defined profile picture", e)
            null
        }
    }

    private fun getAndroidDefinedProfilePicture(
        contactModel: ContactModel,
        highRes: Boolean,
    ): Bitmap? {
        if (ContactUtil.isGatewayContact(contactModel.identity) || AndroidContactUtil.getInstance()
                .getAndroidContactUri(contactModel) == null
        ) {
            return null
        }
        // regular contacts
        return try {
            var result = fileService?.getAndroidDefinedProfilePicture(contactModel)
            if (result != null && !highRes) {
                result = AvatarConverterUtil.convert(this.context.resources, result)
            }
            result
        } catch (e: Exception) {
            logger.error("Could not get android defined profile picture", e)
            null
        }
    }

    private fun buildDefaultAvatar(
        identity: String?,
        highRes: Boolean,
        backgroundColor: Int,
    ): Bitmap {
        val color = contactService?.getAvatarColor(identity) ?: IdColor.invalid().getThemedColor(context)
        val drawable =
            if (identity != null && ContactUtil.isGatewayContact(identity)) {
                identityGatewayAvatar
            } else {
                identityDefaultAvatar
            }
        return if (highRes) {
            buildDefaultAvatarHighRes(drawable, color, backgroundColor)
        } else {
            buildDefaultAvatarLowRes(drawable, color)
        }
    }
}

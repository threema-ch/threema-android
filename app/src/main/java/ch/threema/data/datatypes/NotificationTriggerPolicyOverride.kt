/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2025 Threema GmbH
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

package ch.threema.data.datatypes

import androidx.annotation.DrawableRes
import ch.threema.app.R
import ch.threema.app.services.ContactService
import ch.threema.app.services.DeadlineListService.DEADLINE_INDEFINITE
import ch.threema.app.services.DeadlineListService.DEADLINE_INDEFINITE_EXCEPT_MENTIONS
import ch.threema.domain.types.Identity
import kotlinx.serialization.Serializable

/**
 *  This class represents the protocol values of
 *  - `Contact.NotificationTriggerPolicyOverride`
 *  - `Group.NotificationTriggerPolicyOverride`
 *
 *  The state of [MutedIndefiniteExceptMentions] can never be applied to contacts.
 *  This is enforced by [fromDbValueContact] and `ContactModel.setNotificationTriggerPolicyOverrideFromLocal`.
 */
@Serializable
sealed class NotificationTriggerPolicyOverride private constructor(val dbValue: Long?) {
    companion object {
        /**
         *  Passing a [dbValue] of exactly `0` or lower than `-1` will result in [NotMuted].
         */
        @JvmStatic
        fun fromDbValueContact(dbValue: Long?): NotificationTriggerPolicyOverride = when (dbValue) {
            null -> NotMuted
            DEADLINE_INDEFINITE -> MutedIndefinite
            else -> when {
                dbValue > 0 -> MutedUntil(dbValue)
                else -> NotMuted
            }
        }

        /**
         *  Passing a [dbValue] of exactly `0` or lower than `-2` will result in [NotMuted].
         */
        @JvmStatic
        fun fromDbValueGroup(dbValue: Long?): NotificationTriggerPolicyOverride = when (dbValue) {
            null -> NotMuted
            DEADLINE_INDEFINITE -> MutedIndefinite
            DEADLINE_INDEFINITE_EXCEPT_MENTIONS -> MutedIndefiniteExceptMentions
            else -> when {
                dbValue > 0 -> MutedUntil(dbValue)
                else -> NotMuted
            }
        }
    }

    /**
     *  @return `True` if the current policy value should block notifications.
     *  - In case of [MutedUntil] we also consider the current system time.
     *  So in this case it could result in `false` if the mute expired.
     *  - In case of [MutedIndefiniteExceptMentions] this value will also be true,
     *  **not** considering any possible mentions.
     */
    abstract val muteAppliesRightNow: Boolean

    /**
     *  @return The same value as [muteAppliesRightNow] except for the policy case of [MutedIndefiniteExceptMentions].
     *  There we also search for mentions regarding either [myIdentity] or `@All`. If a mention is found, `false` will
     *  be returned.
     */
    abstract fun muteAppliesRightNowToMessage(message: String, myIdentity: Identity): Boolean

    /**
     *  @return The correct icon to show in the ui for the current policy value.
     *  - In case of [MutedUntil] we also consider the current system time. So it could result in `null` if the mute expired.
     */
    abstract val iconResRightNow: Int?

    @Serializable
    data object NotMuted : NotificationTriggerPolicyOverride(dbValue = null) {
        override val muteAppliesRightNow: Boolean = false

        override fun muteAppliesRightNowToMessage(message: String, myIdentity: Identity) = false

        @DrawableRes
        override val iconResRightNow: Int? = null
    }

    @Serializable
    data object MutedIndefinite : NotificationTriggerPolicyOverride(dbValue = DEADLINE_INDEFINITE) {
        override val muteAppliesRightNow: Boolean = true

        override fun muteAppliesRightNowToMessage(message: String, myIdentity: Identity): Boolean = muteAppliesRightNow

        @DrawableRes
        override val iconResRightNow: Int = R.drawable.ic_do_not_disturb_filled
    }

    @Serializable
    data object MutedIndefiniteExceptMentions : NotificationTriggerPolicyOverride(dbValue = DEADLINE_INDEFINITE_EXCEPT_MENTIONS) {
        override val muteAppliesRightNow: Boolean = true

        override fun muteAppliesRightNowToMessage(message: String, myIdentity: Identity): Boolean =
            !message.contains("@[${ContactService.ALL_USERS_PLACEHOLDER_ID}]") &&
                !message.contains("@[$myIdentity]")

        @DrawableRes
        override val iconResRightNow: Int = R.drawable.ic_dnd_mention_black_18dp
    }

    @Serializable
    data class MutedUntil(val utcMillis: Long) : NotificationTriggerPolicyOverride(dbValue = utcMillis) {
        override val muteAppliesRightNow: Boolean
            get() = utcMillis > System.currentTimeMillis()

        override fun muteAppliesRightNowToMessage(message: String, myIdentity: Identity): Boolean = muteAppliesRightNow

        @DrawableRes
        override val iconResRightNow: Int? = when (muteAppliesRightNow) {
            true -> R.drawable.ic_do_not_disturb_filled
            false -> null
        }
    }
}

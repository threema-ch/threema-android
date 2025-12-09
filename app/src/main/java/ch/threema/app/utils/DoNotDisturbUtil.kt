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

package ch.threema.app.utils

import ch.threema.app.stores.IdentityProvider
import ch.threema.data.datatypes.NotificationTriggerPolicyOverride

abstract class DoNotDisturbUtil {

    protected abstract val identityProvider: IdentityProvider

    /**
     * Returns true if the chat for the provided MessageReceiver is permanently or temporarily muted AT THIS TIME and
     * no intrusive notification should be shown for an incoming message
     * If a message text is provided it is checked for possible mentions - group messages only
     *
     * @param rawMessageText Text of the incoming message (optional, group messages only)
     */
    fun isMessageMuted(
        notificationTriggerPolicyOverride: NotificationTriggerPolicyOverride?,
        rawMessageText: CharSequence?,
    ): Boolean {
        if (notificationTriggerPolicyOverride == null) {
            return false
        }
        val myIdentity = identityProvider.getIdentity()
        val isMutedByOverrideSetting = if (rawMessageText != null && myIdentity != null) {
            notificationTriggerPolicyOverride.muteAppliesRightNowToMessage(rawMessageText.toString(), myIdentity)
        } else {
            notificationTriggerPolicyOverride.muteAppliesRightNow
        }
        return isMutedByOverrideSetting || isDoNotDisturbActive()
    }

    /**
     * Check if Work DND schedule is currently active
     *
     * @return true if we're currently outside of the working hours set by the user and Work DND is currently enabled, false otherwise
     */
    abstract fun isDoNotDisturbActive(): Boolean
}

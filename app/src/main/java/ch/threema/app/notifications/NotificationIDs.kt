/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2025 Threema GmbH
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

package ch.threema.app.notifications

object NotificationIDs {
    /**
     * Used for `BackgroundErrorNotification` notifications.
     */
    const val BACKGROUND_ERROR = 439873
    const val PASSPHRASE_SERVICE_NOTIFICATION_ID = 587
    const val NEW_MESSAGE_NOTIFICATION_ID = 723
    const val MASTER_KEY_LOCKED_NOTIFICATION_ID = 724
    const val NEW_MESSAGE_LOCKED_NOTIFICATION_ID = 725
    const val NEW_MESSAGE_PIN_LOCKED_NOTIFICATION_ID = 726
    const val SAFE_FAILED_NOTIFICATION_ID = 727
    const val SERVER_MESSAGE_NOTIFICATION_ID = 730
    const val UNSENT_MESSAGE_NOTIFICATION_ID = 732
    const val WORK_SYNC_NOTIFICATION_ID = 735
    const val NEW_SYNCED_CONTACTS_NOTIFICATION_ID = 736
    const val WEB_RESUME_FAILED_NOTIFICATION_ID = 737
    const val VOICE_MSG_PLAYER_NOTIFICATION_ID = 749
    const val INCOMING_CALL_NOTIFICATION_ID = 800
    const val INCOMING_GROUP_CALL_NOTIFICATION_ID = 803
    const val REMOTE_SECRET_ACTIVATED_OR_DEACTIVATED = 810
}

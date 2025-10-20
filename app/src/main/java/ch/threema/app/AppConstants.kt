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

package ch.threema.app

object AppConstants {

    const val INTENT_DATA_CONTACT = "identity"
    const val INTENT_DATA_CONTACT_READONLY = "readonly"
    const val INTENT_DATA_TEXT = "text"
    const val INTENT_DATA_ID_BACKUP = "idbackup"
    const val INTENT_DATA_ID_BACKUP_PW = "idbackuppw"
    const val INTENT_DATA_IS_FORWARD = "is_forward"
    const val INTENT_DATA_TIMESTAMP = "timestamp"
    const val INTENT_DATA_EDITFOCUS = "editfocus"
    const val INTENT_DATA_GROUP_DATABASE_ID = "group"
    const val INTENT_DATA_DISTRIBUTION_LIST_ID = "distribution_list"
    const val INTENT_DATA_ARCHIVE_FILTER = "archiveFilter"
    const val INTENT_DATA_QRCODE = "qrcodestring"
    const val INTENT_DATA_MESSAGE_ID = "messageid"
    const val EXTRA_VOICE_REPLY = "voicereply"
    const val EXTRA_OUTPUT_FILE = "output"
    const val INTENT_DATA_CHECK_ONLY = "check"
    const val INTENT_DATA_ANIM_CENTER = "itemPos"
    const val INTENT_DATA_PICK_FROM_CAMERA = "useCam"
    const val INTENT_PUSH_REGISTRATION_COMPLETE = "registrationComplete"
    const val INTENT_DATA_HIDE_RECENTS = "hiderec"
    const val INTENT_ACTION_FORWARD = "ch.threema.app.intent.FORWARD"
    const val INTENT_ACTION_SHORTCUT_ADDED = BuildConfig.APPLICATION_ID + ".intent.SHORTCUT_ADDED"

    const val CONFIRM_TAG_CLOSE_BALLOT = "cb"
    const val ECHO_USER_IDENTITY = "ECHOECHO"
    const val PHONE_LINKED_PLACEHOLDER = "***"
    const val EMAIL_LINKED_PLACEHOLDER = "***@***"
    const val ACTIVITY_CONNECTION_TAG = "threemaApplication"

    const val MAX_BLOB_SIZE_MB = 100
    const val MAX_BLOB_SIZE = MAX_BLOB_SIZE_MB * 1024 * 1024
    const val MIN_PIN_LENGTH = 4
    const val MAX_PIN_LENGTH = 8
    const val MIN_PW_LENGTH_BACKUP = 8
    const val MAX_PW_LENGTH_BACKUP = 256

    const val ACTIVITY_CONNECTION_LIFETIME = 60_000L
}

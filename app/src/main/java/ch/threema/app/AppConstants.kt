package ch.threema.app

import ch.threema.domain.types.IdentityString

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
    const val INTENT_DATA_MESSAGE_ID = "messageid"
    const val EXTRA_VOICE_REPLY = "voicereply"
    const val EXTRA_OUTPUT_FILE = "output"
    const val INTENT_DATA_ANIM_CENTER = "itemPos"
    const val INTENT_DATA_PICK_FROM_CAMERA = "useCam"
    const val INTENT_PUSH_REGISTRATION_COMPLETE = "registrationComplete"
    const val INTENT_DATA_HIDE_RECENTS = "hiderec"
    const val INTENT_ACTION_FORWARD = "ch.threema.app.intent.FORWARD"

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

    const val THREEMA_SUPPORT_IDENTITY: IdentityString = "*SUPPORT"
    const val THREEMA_CHANNEL_IDENTITY: IdentityString = "*THREEMA"
    const val THREEMA_WORK_SYNC_IDENTITY: IdentityString = "*3MAW0RK"
}

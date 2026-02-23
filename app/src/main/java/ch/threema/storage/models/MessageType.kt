package ch.threema.storage.models

/**
 * Do not change the order of the values, since this would break the persisted type in the database
 *
 * @param canBeEdited Whether the given message type allows editing in general.
 * To check whether the user should be able to edit a particular message, [ch.threema.app.utils.canBeEdited] should be used.
 */
enum class MessageType(val canBeEdited: Boolean = false) {
    TEXT(canBeEdited = true),

    @Deprecated("Only used for backwards compatibility")
    IMAGE,

    @Deprecated("Only used for backwards compatibility")
    VIDEO,

    @Deprecated("Only used for backwards compatibility")
    VOICEMESSAGE,
    LOCATION,

    @Deprecated("Only used for backwards compatibility")
    CONTACT,
    STATUS,
    BALLOT,
    FILE(canBeEdited = true),
    VOIP_STATUS,
    DATE_SEPARATOR,
    GROUP_CALL_STATUS,
    FORWARD_SECURITY_STATUS,
    GROUP_STATUS,
}

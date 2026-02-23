package ch.threema.storage.models

/**
 * Represents a tag on a conversation.
 *
 * Do not change a tag's value, as it is used for persisting tags in the DB.
 */
enum class ConversationTag(@JvmField val value: String) {
    PINNED("star"),
    MARKED_AS_UNREAD("unread"),
}

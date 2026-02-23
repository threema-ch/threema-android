package ch.threema.app.backuprestore

import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.models.MessageId
import ch.threema.domain.types.Identity
import ch.threema.domain.types.MessageIdString

private val logger = getThreemaLogger("MessageIdCache")

/**
 * A cache for message ids as used in our database but can be retrieved by other infos (see MessageKey).
 * This is used when a data backup is restored, as we must not store database ids in a backup and must
 * therefore use another format for data that is related by the id.
 *
 * Note that this cache only retains a single entry. It is therefore only usable when the ids are
 * requested in a sorted manner (aka all entries referencing the same id are queried successively)
 * and must therefore not be computed over and over again.
 */
class MessageIdCache<K : MessageIdCache.MessageKey>(val computeIfAbsent: (key: K) -> Int) {
    private var entry: Pair<K, Int>? = null

    @Throws(NoSuchElementException::class)
    fun get(key: K): Int {
        val currentEntry = entry
        return if (currentEntry?.first?.equals(key) == true) {
            currentEntry.second
        } else {
            try {
                computeIfAbsent(key).also { computedValue ->
                    entry = key to computedValue
                }
            } catch (exception: Exception) {
                logger.warn("Could not compute value", exception)
                throw NoSuchElementException()
            }
        }
    }

    sealed interface MessageKey {
        val apiMessageId: MessageIdString

        val messageId: MessageId
            get() = MessageId.fromString(apiMessageId)
    }

    data class ContactMessageKey(
        val contactIdentity: Identity,
        override val apiMessageId: MessageIdString,
    ) : MessageKey

    data class GroupMessageKey(
        val apiGroupId: String,
        val groupCreatorIdentity: Identity,
        override val apiMessageId: MessageIdString,
    ) : MessageKey
}

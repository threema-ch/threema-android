package ch.threema.data.models

import androidx.compose.runtime.Immutable
import ch.threema.data.storage.DbEmojiReaction
import ch.threema.domain.types.Identity
import java.time.Instant

@Immutable
data class EmojiReactionData(
    /** The id of the message this reaction refers to - see [ch.threema.storage.models.AbstractMessageModel.COLUMN_ID] */
    @JvmField val messageId: Int,
    /** The identity of the person who reacted. This may differ from the sender of the message **/
    @JvmField val senderIdentity: Identity,
    /** The emoji codepoint sequence of the reaction. This can never be empty */
    @JvmField val emojiSequence: String,
    /** Timestamp when the reaction was locally created. */
    @JvmField val reactedAt: Instant,
) {
    override fun equals(other: Any?): Boolean {
        if (other !is EmojiReactionData) return false

        return this.messageId == other.messageId &&
            this.senderIdentity == other.senderIdentity &&
            this.emojiSequence == other.emojiSequence
    }

    override fun hashCode(): Int {
        var result = messageId
        result = 31 * result + senderIdentity.hashCode()
        result = 31 * result + emojiSequence.hashCode()
        return result
    }
}

fun DbEmojiReaction.toDataType() = EmojiReactionData(
    messageId = this.messageId,
    senderIdentity = this.senderIdentity,
    emojiSequence = this.emojiSequence,
    reactedAt = this.reactedAt.toInstant(),
)

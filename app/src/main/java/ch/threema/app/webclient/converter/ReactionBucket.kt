package ch.threema.app.webclient.converter

import ch.threema.data.models.EmojiReactionData
import ch.threema.domain.types.Identity
import java.time.Instant

data class ReactionBucket(
    val reaction: String,
    val mostRecentReactedAt: Instant,
    val identities: List<Identity>,
) {
    companion object {
        @JvmStatic
        fun fromReactions(reactions: List<EmojiReactionData>): List<ReactionBucket> {
            return reactions
                .groupBy { it.emojiSequence }
                .map { (reactionSequence, reactionsForSequence) ->
                    ReactionBucket(
                        reactionSequence,
                        reactionsForSequence.maxOf { it.reactedAt },
                        reactionsForSequence.map { it.senderIdentity },
                    )
                }
                .sortedByDescending(ReactionBucket::mostRecentReactedAt)
        }
    }
}

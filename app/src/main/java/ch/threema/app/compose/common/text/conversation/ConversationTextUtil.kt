package ch.threema.app.compose.common.text.conversation

import ch.threema.app.emojis.EmojiParser

object ConversationTextUtil {

    /**
     *  Truncate the given [text] to a maximum length of characters counting every multi-char emoji sequence as **one** character. The effective count
     *  of characters in the result may exceed the passed [maxLength] due to contained emojis.
     *
     *  Because the [EmojiParser] is used to to determine emoji character-sequences, this implementation works across all Java versions with
     *  potentially older unicode definitions.
     *
     *  This truncation logic is especially useful for views that want to truncate a string that potentially contains emojis, but would like to
     *  present the given [maxLength] in **visual characters**.
     *
     *  @param maxLength the maximum count of raw non-emoji characters and/or **visual emojis**
     *
     *  @throws IllegalStateException if the [maxLength] is negative
     */
    fun truncateRespectingEmojis(text: String, maxLength: Int): String {
        check(maxLength >= 0)
        if (text.length <= maxLength) {
            return text
        }
        return buildString {
            var index = 0
            var effectiveLength = 0
            while (index < text.length && effectiveLength < maxLength) {
                val emojiParseResult: EmojiParser.ParseResult? = EmojiParser.parseAt(text, index)
                if (emojiParseResult != null && emojiParseResult.length > 0) {
                    append(text.substring(index, index + emojiParseResult.length))
                    index += emojiParseResult.length
                } else {
                    append(text[index])
                    index += 1
                }
                effectiveLength += 1
            }
        }
    }
}

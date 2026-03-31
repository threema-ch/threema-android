package ch.threema.app.utils;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;

import androidx.annotation.Nullable;
import ch.threema.app.emojis.EmojiParser;

public class TextUtil {
    public static final String TILDE = "~";
    public static final String SPACE = " ";
    public static final String ELLIPSIS = "…";

    /**
     * Splits a given text string into multiple strings no longer than maxLength bytes keeping in account emojis (even composite emojis such as flags)
     *
     * @param text      input text
     * @param maxLength maximum length of one result string in bytes
     * @return List of text strings
     */
    public static ArrayList<String> splitEmojiText(String text, int maxLength) {
        ArrayList<String> splitText = new ArrayList<>();
        if (text.getBytes(StandardCharsets.UTF_8).length > maxLength) {
            String toAdd;
            StringBuilder newString = new StringBuilder();

            for (int i = 0; i < text.length(); i++) {
                final EmojiParser.ParseResult result = EmojiParser.parseAt(text, i);
                if (result != null && result.length > 0) {
                    // emoji found at this position
                    toAdd = text.substring(i, i + result.length);
                    i += result.length - 1;
                } else {
                    toAdd = text.substring(i, i + 1);
                }

                if (newString.toString().getBytes(StandardCharsets.UTF_8).length +
                    toAdd.getBytes(StandardCharsets.UTF_8).length
                    > maxLength) {
                    splitText.add(newString.toString());
                    newString = new StringBuilder();
                }
                newString.append(toAdd);
            }
            if (!newString.toString().isEmpty()) {
                splitText.add(newString.toString());
            }
        } else {
            splitText.add(text);
        }
        return splitText;
    }

    /**
     * Check if the query matches the text. A query matches the text if
     * <ul>
     *     <li>the text contains the query, or</li>
     *     <li>the normalized text without the diacritics contains the query.</li>
     * </ul>
     * <p>
     * If any of the arguments is null, {@code false} is returned.
     *
     * @return {@code true} if there is a match, {@code false} otherwise
     */
    public static boolean matchesQueryDiacriticInsensitive(@Nullable String text, @Nullable String query) {
        if (text == null || query == null) {
            return false;
        }

        text = text.toUpperCase();
        query = query.toUpperCase();

        if (text.contains(query)) {
            return true;
        }

        // Only normalize the query without removing the diacritics
        String queryNorm = Normalizer.isNormalized(query, Normalizer.Form.NFD)
            ? query
            : Normalizer.normalize(query, Normalizer.Form.NFD);

        // Normalize conversation and remove diacritics
        String conversationNormDiacritics = LocaleUtil.normalize(text);

        return conversationNormDiacritics.contains(queryNorm);
    }
}

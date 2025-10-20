/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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

package ch.threema.app.utils;

import android.content.Context;

import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import ch.threema.app.emojis.EmojiParser;
import ch.threema.app.restrictions.AppRestrictionUtil;
import ch.threema.base.utils.LoggingUtil;

public class TextUtil {
    private static final Logger logger = LoggingUtil.getThreemaLogger("TextUtil");

    public static final String TILDE = "~";
    public static final String SPACE = " ";

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

    private static final String[] passwordChecks = {
        "(.)\\1+",    // do not allow single repeating characters
        "^[0-9]{0,15}$",    // do not short numeric-only passwords
    };

    /**
     * Check a given password string for badness
     *
     * @return true if the password is considered bad or listed in the list of bad passwords, false otherwise
     */
    @WorkerThread
    public static boolean checkBadPassword(@NonNull Context context, @NonNull String password) {
        if (AppRestrictionUtil.isSafePasswordPatternSet(context)) {
            try {
                Pattern pattern = Pattern.compile(AppRestrictionUtil.getSafePasswordPattern(context));
                return !pattern.matcher(password).matches();
            } catch (Exception e) {
                return true;
            }
        } else {
            // check if password is unsafe
            for (String pattern : passwordChecks) {
                if (password.matches(pattern)) {
                    return true;
                }
            }

            BufferedReader bufferedReader = null;
            try {
                bufferedReader = new BufferedReader(new InputStreamReader(context.getAssets().open("passwords/bad_passwords.txt")));
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    if (password.equalsIgnoreCase(line)) {
                        return true;
                    }
                }
            } catch (IOException e) {
                logger.error("Exception", e);
            } finally {
                if (bufferedReader != null) {
                    try {
                        bufferedReader.close();
                    } catch (IOException e) {
                        logger.error("Exception", e);
                    }
                }
            }
        }
        return false;
    }

    /**
     * Check if the query matches the text. A query matches the text
     * text if
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

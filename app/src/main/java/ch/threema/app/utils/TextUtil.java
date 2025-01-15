/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2024 Threema GmbH
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
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;

import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import ch.threema.app.R;
import ch.threema.app.emojis.EmojiParser;
import ch.threema.base.utils.LoggingUtil;

public class TextUtil {
    private static final Logger logger = LoggingUtil.getThreemaLogger("TextUtil");

    public static final String TILDE = "~";
    public static final String SPACE = " ";

    public static String trim(String string, int maxLength, String postFix) {
        if ((maxLength > 0) && (string.length() > maxLength)) {
            return string.substring(0, maxLength - postFix.length()) + postFix;
        }
        return string;
    }

    public static String trim(String string, int maxLength) {
        return trim(string, maxLength, "...");
    }

    public static CharSequence trim(CharSequence string, int maxLength, CharSequence postFix) {
        if ((maxLength > 0) && (string.length() > maxLength)) {
            if (postFix != null) {
                TextUtils.concat(string.subSequence(0, maxLength), postFix);
            } else {
                string.subSequence(0, maxLength);
            }
        }
        return string;
    }

    public static Spannable highlightMatches(Context context, @Nullable CharSequence fullText, @Nullable String filterText, boolean background, boolean normalize) {
        if (fullText == null) {
            return new SpannableString("");
        }

        int stringLength = fullText.length();
        Spannable spannableString = new SpannableString(fullText);

        if (filterText != null && stringLength > 0) {
            int start, end, index = 0, length = filterText.length();
            int highlightColor = context.getResources().getColor(R.color.match_highlight_color);
            String fullUpperText = normalize ? LocaleUtil.normalize(fullText.toString()) : fullText.toString().toLowerCase();
            String filterUpperText = normalize ? LocaleUtil.normalize(filterText) : filterText.toLowerCase();

            while ((start = fullUpperText.indexOf(filterUpperText, index)) >= 0) {
                end = start + length;

                if (end <= stringLength) {
                    spannableString.setSpan(background ?
                            new BackgroundColorSpan(highlightColor) :
                            new ForegroundColorSpan(highlightColor),
                        start, end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                index = start + 1;
            }
        }
        return spannableString;
    }

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

    @NonNull
    public static String capitalize(String string) {
        if (TestUtil.isEmptyOrNull(string)) {
            return "";
        }
        if (string.length() > 1) {
            return Character.toUpperCase(string.charAt(0)) + string.substring(1);
        } else {
            return Character.toString(Character.toUpperCase(string.charAt(0)));
        }
    }
}

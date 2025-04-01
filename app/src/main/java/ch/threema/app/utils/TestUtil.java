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

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Date;

import androidx.annotation.Nullable;

public class TestUtil {
    @Deprecated
    public static boolean required(Object o) {
        return o != null;
    }

    public static boolean required(Object... o) {
        for (Object x : o) {
            if (!required(x)) {
                return false;
            }
        }
        return true;
    }

    public static boolean requireOne(Object... o) {
        for (Object x : o) {
            if (x != null) {
                return true;
            }
        }
        return false;
    }

    public static boolean requireAll(Object[] o) {
        for (Object x : o) {
            if (x == null) {
                return false;
            }
        }
        return true;
    }

    public static boolean compare(Object[] a, Object[] b) {
        if (a == null) {
            return b == null;
        }

        if (b == null) {
            return a == null;
        }

        //not the same length
        if (a.length != b.length) {
            return false;
        }

        for (int n = 0; n < a.length; n++) {
            if (b.length < n) {
                return false;
            }

            if (!compare(a[n], b[n])) {
                return false;
            }
        }

        return true;
    }

    public static boolean compare(Object a, Object b) {
        if (a == null) {
            return b == null;
        }

        if (b == null) {
            return a == null;
        }

        if (a instanceof byte[]) {
            return compare((byte[]) a, (byte[]) b);
        }

        return a == null ? b == null : a.equals(b);
    }

    public static boolean compare(byte[] a, byte[] b) {
        return a == null ? b == null : Arrays.equals(a, b);
    }

    public static boolean compare(int a, int b) {
        return a == b;
    }

    public static boolean compare(float a, float b) {
        return a == b;
    }

    public static boolean compare(double a, double b) {
        return a == b;
    }

    public static boolean compare(Date a, Date b) {
        return a == null ? b == null :
            a.compareTo(b) == 0;
    }

    /**
     * Returns true if the provided string is null or empty.
     */
    public static boolean isEmptyOrNull(@Nullable String string) {
        return string == null || string.isEmpty();
    }

    /**
     * Returns true if the provided strings are null or empty. Returns false if at least one
     * of them is not empty.
     */
    public static boolean isEmptyOrNull(@Nullable String... string) {
        if (string == null) {
            return true;
        }
        for (String s : string) {
            if (!isEmptyOrNull(s)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true if the provided string is null, empty or blank.
     */
    public static boolean isBlankOrNull(@Nullable String string) {
        return string == null || string.isBlank();
    }

    /**
     * Returns true if the char sequence is null, empty or blank.
     */
    public static boolean isBlankOrNull(@Nullable CharSequence charSequence) {
        if (charSequence == null) {
            return true;
        }
        String string = charSequence.toString();
        return isBlankOrNull(string);
    }

    public static boolean contains(String search, String string) {
        return contains(search, string, false);
    }

    public static boolean contains(String search, String string, boolean caseSensitive) {
        return string != null
            && search != null
            && (!caseSensitive ?
            string.toLowerCase().contains(search.toLowerCase()) :
            string.contains(search));
    }

    /**
     * Check if the query string matches the conversation title. A query matches the conversation
     * text if
     * <ul>
     *     <li>the conversation text contains the query, or</li>
     *     <li>the normalized conversation text without the diacritics contains the query.</li>
     * </ul>
     * <p>
     * If any of the arguments is null, {@code false} is returned.
     *
     * @param query        the query
     * @param conversation the conversation text
     * @return {@code true} if there is a match, {@code false} otherwise
     */
    public static boolean matchesConversationSearch(@Nullable String query, @Nullable String conversation) {
        if (query == null || conversation == null) {
            return false;
        }

        query = query.toUpperCase();
        conversation = conversation.toUpperCase();

        if (conversation.contains(query)) {
            return true;
        }

        // Only normalize the query without removing the diacritics
        String queryNorm = Normalizer.isNormalized(query, Normalizer.Form.NFD) ? query :
            Normalizer.normalize(query, Normalizer.Form.NFD);

        // Normalize conversation and remove diacritics
        String conversationNormDiacritics = LocaleUtil.normalize(conversation);

        return conversationNormDiacritics.contains(queryNorm);
    }
}

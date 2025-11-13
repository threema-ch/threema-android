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

import java.util.Arrays;

import androidx.annotation.Nullable;

public class TestUtil {
    public static boolean required(Object... o) {
        for (Object x : o) {
            if (x == null) {
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
            return false;
        }

        if (a instanceof byte[] && b instanceof byte[]) {
            return Arrays.equals((byte[]) a, (byte[]) b);
        }

        return a.equals(b);
    }

    /**
     * Returns true if the provided string is null or empty.
     */
    public static boolean isEmptyOrNull(@Nullable String string) {
        return string == null || string.isEmpty();
    }

    /**
     * Returns true if the provided strings are null or empty. Returns false if at least one of them is not empty.
     * <p>
     * Do not use this, as it is not intuitively clear whether it performs a logical AND or an OR on the inputs' nullability and emptiness,
     * which can easily lead to subtle bugs.
     */
    @Deprecated
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

    public static boolean isInTest() {
        return isClassAvailable("org.junit.Test");
    }

    public static boolean isInDeviceTest() {
        return isClassAvailable("ch.threema.app.ThreemaTestRunner");
    }

    private static boolean isClassAvailable(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}

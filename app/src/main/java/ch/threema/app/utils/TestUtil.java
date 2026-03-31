package ch.threema.app.utils;

import java.util.Arrays;

import androidx.annotation.Nullable;

import static ch.threema.common.ReflectionExtensionsKt.isClassAvailable;

public class TestUtil {
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

    public static boolean isInDeviceTest() {
        return isClassAvailable("ch.threema.app.ThreemaTestRunner");
    }
}

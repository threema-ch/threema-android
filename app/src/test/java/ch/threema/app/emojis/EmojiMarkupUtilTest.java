/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2025 Threema GmbH
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

package ch.threema.app.emojis;

import org.junit.Assert;
import org.junit.Test;

public class EmojiMarkupUtilTest {
    private static void assertMatches(String string, String regex) {
        Assert.assertTrue(
            "String '" + string + "' does not match regex '" + regex + "'",
            string.matches(regex)
        );
    }

    private static void assertNoMatch(String string, String regex) {
        Assert.assertFalse(
            "String '" + string + "' should not match regex '" + regex + "'",
            string.matches(regex)
        );
    }

    @Test
    public void testRegexPositive() {
        // Gateway ID
        assertMatches("@[*THREEMA]", EmojiMarkupUtil.MENTION_REGEX);
        // Regular ID
        assertMatches("@[BNKTHA3X]", EmojiMarkupUtil.MENTION_REGEX);
        // Sandbox ID
        assertMatches("@[0PFX5ZXF]", EmojiMarkupUtil.MENTION_REGEX);
    }

    @Test
    public void testRegexNegative() {
        // Too long
        assertNoMatch("@[*THREEMAA]", EmojiMarkupUtil.MENTION_REGEX);
        // Disallowed characters
        assertNoMatch("@[BNK%HA3X]", EmojiMarkupUtil.MENTION_REGEX);
    }
}

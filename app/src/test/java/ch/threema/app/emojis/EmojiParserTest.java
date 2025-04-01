/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2025 Threema GmbH
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

import org.junit.Test;

import static junit.framework.Assert.assertEquals;

public class EmojiParserTest {

    // Parse a single 1-codepoint emoji
    @Test
    public void parseSimple() throws Exception {
        final int woman = 0x1F469;
        final char[] chars = Character.toChars(woman);
        final String string = new String(chars);
        final EmojiParser.ParseResult res = EmojiParser.parseAt(string, 0);
        assertEquals(Integer.valueOf(2), res.length);
    }

    // Parse an emoji with a skintone modifier
    @Test
    public void parseSkintoneModifier() throws Exception {
        final char[] woman = Character.toChars(0x1F469);
        final char[] mediumDark = Character.toChars(0x1F3FE);
        String string = new String(woman);
        string += new String(mediumDark);
        final EmojiParser.ParseResult res = EmojiParser.parseAt(string, 0);
        assertEquals(Integer.valueOf(4), res.length);
    }
}

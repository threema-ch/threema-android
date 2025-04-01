/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2025 Threema GmbH
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

package ch.threema.base.utils;

import org.junit.Test;

import static org.junit.Assert.*;

public class LoggingUtilTest {

    @Test
    public void cleanTag() {
        String[] prefixes = new String[]{
            "ch.threema."
        };

        assertEquals("", LoggingUtil.cleanTag("", prefixes));
        assertEquals("Tag", LoggingUtil.cleanTag("Tag", prefixes));
        assertEquals("tagging.Tag", LoggingUtil.cleanTag("tagging.Tag", prefixes));
        assertEquals("tagging.Tag", LoggingUtil.cleanTag("ch.threema.tagging.Tag", prefixes));
        assertEquals("ch.threema.Tag", LoggingUtil.cleanTag("ch.threema.ch.threema.Tag", prefixes));
    }
}

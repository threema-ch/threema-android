/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2024 Threema GmbH
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

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;
import ch.threema.app.ThreemaApplication;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

/**
 * Ensure the Call SDP does not contain any "funny" easter eggs such as silly header extensions
 * that are not encrypted and contain sensitive information.
 * <p>
 * This may need updating from time to time, so if it breaks, you will have to do some
 * research on what changed and why.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class TextUtilTest {

    @Test
    public void testCheckBadPasswordNumericOnly() {
        final Context context = ThreemaApplication.getAppContext();
        assertTrue(TextUtil.checkBadPassword(context, "1234"));
        assertTrue(TextUtil.checkBadPassword(context, "1234567890"));
        assertTrue(TextUtil.checkBadPassword(context, "123456789012345"));
        assertFalse(TextUtil.checkBadPassword(context, "1234567890123456"));
        assertFalse(TextUtil.checkBadPassword(context, "12345678901234567890"));
    }

    @Test
    public void testCheckBadPasswordSameCharacter() {
        final Context context = ThreemaApplication.getAppContext();
        assertTrue(TextUtil.checkBadPassword(context, "aaaaaaaaaaaa"));
        assertFalse(TextUtil.checkBadPassword(context, "aaaaaaaaaaab"));
    }

    @Test
    public void testCheckBadPasswordWarnList() {
        final Context context = ThreemaApplication.getAppContext();
        assertTrue(TextUtil.checkBadPassword(context, "1Rainbow"));
        assertTrue(TextUtil.checkBadPassword(context, "apples123"));
        assertFalse(TextUtil.checkBadPassword(context, "kajsdlfkjalskdjflkajsdfl"));
    }
}

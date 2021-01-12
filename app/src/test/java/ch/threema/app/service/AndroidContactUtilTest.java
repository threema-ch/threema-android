/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2021 Threema GmbH
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

package ch.threema.app.service;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import androidx.core.util.Pair;
import ch.threema.app.utils.AndroidContactUtil;

public class AndroidContactUtilTest {
	/**
	 * Wrapper to test private method.
	 */
	@SuppressWarnings("unchecked")
	private static Pair<String, String> getFirstLastNameFromDisplayName(String displayName) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
		final Method method = AndroidContactUtil.class.getDeclaredMethod("getFirstLastNameFromDisplayName", String.class);
		method.setAccessible(true);
		return (Pair<String, String>) method.invoke(null, displayName);
	}

	@Test
	public void testGetFirstLastNameFromDisplayNameNull() throws Exception {
		final Pair<String, String> firstLastName = getFirstLastNameFromDisplayName(null);
		Assert.assertEquals("", firstLastName.first);
		Assert.assertEquals("", firstLastName.second);
	}

	@Test
	public void testGetFirstLastNameFromDisplayNameEmpty() throws Exception {
		final Pair<String, String> firstLastName = getFirstLastNameFromDisplayName("");
		Assert.assertEquals("", firstLastName.first);
		Assert.assertEquals("", firstLastName.second);
	}

	@Test
	public void testGetFirstLastNameFromDisplayNameOnlyFirst() throws Exception {
		final Pair<String, String> firstLastName = getFirstLastNameFromDisplayName("joe");
		Assert.assertEquals("joe", firstLastName.first);
		Assert.assertEquals("", firstLastName.second);
	}

	@Test
	public void testGetFirstLastNameFromDisplayNameTwoParts() throws Exception {
		final Pair<String, String> firstLastName = getFirstLastNameFromDisplayName("john doe");
		Assert.assertEquals("john", firstLastName.first);
		Assert.assertEquals("doe", firstLastName.second);
	}

	@Test
	public void testGetFirstLastNameFromDisplayNameSpanishCraziness() throws Exception {
		final Pair<String, String> firstLastName = getFirstLastNameFromDisplayName("Pablo Diego José Francisco de Paula Juan Nepomuceno");
		// Yes, this is actually wrong, but we cannot know how to properly split first and last name.
		Assert.assertEquals("Pablo", firstLastName.first);
		Assert.assertEquals("Diego José Francisco de Paula Juan Nepomuceno", firstLastName.second);
	}
}

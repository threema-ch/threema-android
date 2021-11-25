/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2021 Threema GmbH
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

import org.junit.Test;
import java.util.Date;
import static junit.framework.Assert.*;

public class TestUtilTest {

	@Test
	public void nullable() {
		Object nullObject = null;
		Object notNullObject = new Object();

		assertTrue(TestUtil.compare(nullObject, nullObject));
		assertFalse(TestUtil.compare(nullObject, notNullObject));
		assertTrue(TestUtil.compare(notNullObject, notNullObject));
		assertFalse(TestUtil.compare(notNullObject, nullObject));
	}

	@Test
	public void string() {

		assertTrue(TestUtil.compare("Threema", "Threema"));

		assertFalse(TestUtil.compare("Threema", "threema"));

		assertFalse(TestUtil.compare("Threema", " Threema"));

		assertFalse(TestUtil.compare("Threema", "Threema "));

		assertFalse(TestUtil.compare("Threema", null));
	}

	@Test
	public void integer() {
		assertTrue(TestUtil.compare(100, 100));

		assertFalse(TestUtil.compare(100, 101));

		assertFalse(TestUtil.compare(100, null));
	}

	@Test
	public void byteArray() {
		assertTrue(TestUtil.compare(new byte[]{1,2,3,4}, new byte[]{1,2,3,4}));

		//different size
		assertFalse(TestUtil.compare(new byte[]{1,2,3,4}, new byte[]{1,2,3}));
		assertFalse(TestUtil.compare(new byte[]{1,2,3,4}, new byte[]{1,2,3,5}));
	}

	@Test
	public void array() {

		//strings
		assertTrue(TestUtil.compare(
				new Object[] {"string1", "string2", "string3"},
				new Object[] {"string1", "string2", "string3"}));

		assertFalse(TestUtil.compare(
				new Object[] {"string1", "string2", "string3"},
				new Object[] {"string1", "string2"}));

		assertFalse(TestUtil.compare(
				new Object[] {"string1", "string2", "string3"},
				new Object[] {"string1", "string2", "string5"}));

		//int
		assertTrue(TestUtil.compare(
				new Object[] {1,2,3,4},
				new Object[] {1,2,3,4}));

		assertFalse(TestUtil.compare(
				new Object[] {1,2,3,4},
				new Object[] {1,2,3}));

		assertFalse(TestUtil.compare(
				new Object[] {1,2,3,4},
				new Object[] {1,2,3,5}));

		//mixed!!
		assertTrue(TestUtil.compare(
				new Object[] {"a",1, 2.0, new byte[]{3}},
				new Object[] {"a",1, 2.0, new byte[]{3}}));


		assertFalse(TestUtil.compare(
				new Object[] {"a",1, 2.0, new byte[]{3}},
				new Object[] {"a",1, 2.0, new byte[]{4}}));

		assertFalse(TestUtil.compare(
				new Object[] {"a",1},
				new Object[] {"a",1, 2.0, new byte[]{3}}));

	}

	@Test
	public void date() {
		Date date1Instance1 = new Date(2000, 2, 2);
		Date date1Instance2 = new Date(2000, 2, 2);
		Date date2Instance1 = new Date(2000, 3, 2);
		Date date2Instance2 = new Date(2000, 3, 2);

		assertTrue(TestUtil.compare(
				date1Instance1,
				date1Instance2));

		assertTrue(TestUtil.compare(
				date2Instance1,
				date2Instance2));

		assertFalse(TestUtil.compare(
				date1Instance1,
				date2Instance1));

		assertFalse(TestUtil.compare(
				date1Instance2,
				date2Instance2));

	}

}

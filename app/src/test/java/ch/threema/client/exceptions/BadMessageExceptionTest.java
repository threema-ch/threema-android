/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema Java Client
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

package ch.threema.client.exceptions;

import ch.threema.client.BadMessageException;
import org.junit.Assert;
import org.junit.Test;

public class BadMessageExceptionTest {

	@Test
	public void testShouldDrop() {
		final BadMessageException e1 = new BadMessageException("oh no");
		Assert.assertEquals(false, e1.shouldDrop());

		final BadMessageException e2 = new BadMessageException("oh no", new Throwable("aiaiaiai"));
		Assert.assertEquals(false, e2.shouldDrop());

		final BadMessageException e3 = new BadMessageException("oh no", false);
		Assert.assertEquals(false, e3.shouldDrop());

		final BadMessageException e4 = new BadMessageException("drop me baby", true);
		Assert.assertEquals(true, e4.shouldDrop());

		final BadMessageException e5 = new BadMessageException("drop me baby", true, new Throwable("yeahyeah"));
		Assert.assertEquals(true, e5.shouldDrop());
	}

}

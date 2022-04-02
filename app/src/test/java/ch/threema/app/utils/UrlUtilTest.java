/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2022 Threema GmbH
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

import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
public class UrlUtilTest {

	@Mock
	private Uri uri;

	@Test
	public void isLegalUri() {
		when(uri.getHost()).thenReturn("threema.ch");
		assertTrue(UrlUtil.isLegalUri(uri));

		when(uri.getHost()).thenReturn("threemа.ch");
		assertFalse(UrlUtil.isLegalUri(uri));

		when(uri.getHost()).thenReturn("人人贷.公司");
		assertTrue(UrlUtil.isLegalUri(uri));

		when(uri.getHost()).thenReturn("gfrör.li");
		assertFalse(UrlUtil.isLegalUri(uri));

		when(uri.getHost()).thenReturn("wikipedia.org");
		assertTrue(UrlUtil.isLegalUri(uri));

		when(uri.getHost()).thenReturn("wikipediа.org");
		assertFalse(UrlUtil.isLegalUri(uri));

		when(uri.getHost()).thenReturn("wíkipedia.org");
		assertFalse(UrlUtil.isLegalUri(uri));

		// check if Uri.getHost() was really called
		verify(uri, times(7)).getHost();
	}
}

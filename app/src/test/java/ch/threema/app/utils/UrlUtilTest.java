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

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
public class UrlUtilTest {

	private static final String LATIN = "a";
	private static final String CYRILLIC = "а";
	private static final String GREEK = "Ͱ";
	private static final String HAN = "繁";
	private static final String BOPOMOFO = "ㄅ";
	private static final String HIRAGANA = "ぁ";
	private static final String KATAKANA = "ァ";
	private static final String HANGUL = "ᄀ";
	private static final String A_INHERITED = "á";
	private static final String A_LATIN = "á";

	@Mock
	private Uri uri;

	private int callCount = 0;

	private void assertUri(boolean legalExpected, String host) {
		when(uri.getHost()).thenReturn(host);
		callCount++;
		assertEquals(legalExpected, UrlUtil.isLegalUri(uri));
		verify(uri, times(callCount)).getHost();
	}

	private void assertLegalUri(String host) {
		assertUri(true, host);
	}

	private void assertIllegalUri(String host) {
		assertUri(false, host);
	}

	@Test
	public void emptyUris() {
		assertIllegalUri("");

		assertIllegalUri(".");

		assertIllegalUri("..");
	}

	@Test
	public void simpleUris() {
		assertLegalUri("threema.ch");

		assertIllegalUri("threemа.ch"); // cyrillic a

		assertLegalUri("人人贷.公司");

		assertLegalUri("gfrör.li");

		assertLegalUri("wikipedia.org");
	}

	@Test
	public void mixedScriptUris() {
		assertLegalUri(GREEK + GREEK + ".com");

		assertIllegalUri(LATIN + GREEK + ".ch");

		assertLegalUri(LATIN + HAN + HIRAGANA + KATAKANA + ".香港");

		assertLegalUri(HAN + BOPOMOFO + ".香港");

		assertLegalUri(LATIN + HAN + BOPOMOFO + ".香港");

		assertLegalUri(LATIN + HAN + HANGUL + ".com");

		assertIllegalUri(HIRAGANA + BOPOMOFO + ".com");

		assertIllegalUri(LATIN + HAN + HANGUL + BOPOMOFO + ".com");

		assertIllegalUri(KATAKANA + HANGUL + ".ch");

		assertIllegalUri(HAN + CYRILLIC + ".com");

		assertLegalUri(A_LATIN + LATIN + ".ch");

		assertLegalUri(A_INHERITED + A_LATIN + LATIN + ".ch");
	}

	@Test
	public void mixedScriptComponentUris() {
		assertLegalUri(GREEK + "." + LATIN + "." + HANGUL + ".рф");

		assertLegalUri(GREEK + "." + LATIN + HANGUL + "." + HANGUL + ".рф");

		assertIllegalUri(GREEK + LATIN + "." + LATIN + "." + HANGUL + ".рф");
	}
}

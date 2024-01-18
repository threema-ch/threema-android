/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2024 Threema GmbH
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

package ch.threema.app.webclient.services;

import org.junit.Test;

import java.util.Arrays;

import ch.threema.base.utils.Base64;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

public class QRCodeParserImplTest {

	@Test
	public void parseServerKey() throws Exception {
		String base64QrCodeContent = "BTkCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkIjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIxM3EzcTNxM3EzcTNxM3EzcTNxM3EzcTNxM3EzcTNxM3BNJzYWx0eXJ0Yy5leGFtcGxlLm9yZw==";

		QRCodeParser service = new QRCodeParserImpl();
		QRCodeParser.Result result = service.parse(Base64.decode(base64QrCodeContent));
		assertNotNull(result);
		assertEquals("saltyrtc.example.org", result.saltyRtcHost);
		assertEquals(1234, result.saltyRtcPort);
		assertEquals(true, result.isPermanent);
		assertTrue(Arrays.equals(result.authToken, new byte[]{
				35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35,
				35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35
		}));
		assertTrue(Arrays.equals(result.key, new byte[]{
				66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66,
				66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66
		}));
		assertTrue(Arrays.equals(result.serverKey, new byte[] {
				19, 55, 19, 55, 19, 55, 19, 55, 19, 55, 19, 55, 19, 55, 19, 55,
				19, 55, 19, 55, 19, 55, 19, 55, 19, 55, 19, 55, 19, 55, 19, 55
		}));
	}

	@Test
	public void parseNoneServerKey() throws Exception {
		String base64QrCodeContent = "BTkCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkIjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABNJzYWx0eXJ0Yy5leGFtcGxlLm9yZw==";

		QRCodeParser service = new QRCodeParserImpl();
		QRCodeParser.Result result = service.parse(Base64.decode(base64QrCodeContent));
		assertNotNull(result);
		assertEquals("saltyrtc.example.org", result.saltyRtcHost);
		assertEquals(1234, result.saltyRtcPort);
		assertEquals(true, result.isPermanent);
		assertTrue(Arrays.equals(result.authToken, new byte[]{
				35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35,
				35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35
		}));
		assertTrue(Arrays.equals(result.key, new byte[]{
				66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66,
				66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66
		}));

		//no server key
		assertNull(result.serverKey);
	}
}

/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema Java Client
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

package ch.threema.client.voip;

import ch.threema.client.BadMessageException;
import junit.framework.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;

public class VoipCallHangupDataTest {

	@Test
	public void testValidHangup() throws Exception {
		final VoipCallHangupData msg = new VoipCallHangupData()
			.setCallId(1234);

		final ByteArrayOutputStream bos = new ByteArrayOutputStream();
		msg.write(bos);
		final String json = bos.toString();

		Assert.assertEquals("{\"callId\":1234}", json);
	}

	@Test
	public void parseHangupWithCallId() throws BadMessageException {
		final VoipCallHangupData parsed = VoipCallHangupData.parse("{\"callId\":1337}");
		Assert.assertEquals(Long.valueOf(1337), parsed.getCallId());
	}

	@Test
	public void parseHangupWithoutCallId() throws BadMessageException {
		final VoipCallHangupData parsed = VoipCallHangupData.parse("{}");
		Assert.assertNull(parsed.getCallId());
	}
}

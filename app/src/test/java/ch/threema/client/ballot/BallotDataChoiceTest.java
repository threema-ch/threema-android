/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema Java Client
 * Copyright (c) 2013-2021 Threema GmbH
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

package ch.threema.client.ballot;

import ch.threema.client.BadMessageException;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

public class BallotDataChoiceTest {

	class BallotDataChoiceString extends BallotDataChoice {
		public BallotDataChoiceString(int resultSize) {
			super(resultSize);
		}

		@Override
		public String toString() {
			try {
				return this.generateString();
			} catch (BadMessageException e) {
				return "ERROR: " + e.getMessage();
			}
		}
	}
	@Test
	public void parseValidString() {
		String correct = "{"
				+ "\"i\": 0,"
				+ "\"n\": \"desc\","
				+ "\"o\": 123"
				+ "}";

		BallotDataChoice result = null;
		try {
			result = BallotDataChoice.parse(correct);
		} catch (BadMessageException e) {
			Assert.fail(e.getMessage());
		}
		Assert.assertNotNull(result);
	}

	@Test
	public void parseInvalidType() {
		String correct = "{"
				+ "\"i\": 0,"
				+ "\"t\": 123123,"
				+ "\"n\": \"desc\","
				+ "\"v\": 200"
				+ "}";

		try {
			BallotDataChoice.parse(correct);
			Assert.fail("wrong type parsed");
		} catch (BadMessageException e) {
			//cool!
		}
	}

	@Test
	public void parseInvalidString() {
		try {
			BallotDataChoice.parse("i want to be a hippie");
			Assert.fail("invalid string parsed");
		} catch (BadMessageException e) {
			//ok! exception received
		}
	}

	@Test
	public void toStringTest() {
		BallotDataChoice c = new BallotDataChoiceString(4);
		c.setId(100);
		c.setOrder(123);
		int pos = 0;
		c
				.addResult(pos++, 1)
				.addResult(pos++, 0)
				.addResult(pos++, 0)
				.addResult(pos++, 1);
		c.setName("Test");

		try {
			JSONObject o = new JSONObject("{\"i\":100,\"n\":\"Test\",\"o\":123, \"r\": [1,0,0,1]}");
			Assert.assertEquals(
					o.toString(),
					c.toString()
			);
		} catch (JSONException e) {
			Assert.fail("internal error");
		}

	}
}

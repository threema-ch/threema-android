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
import org.json.JSONArray;
import org.json.JSONException;
import org.junit.Assert;
import org.junit.Test;

public class BallotVoteTest {
	class BallotVotString extends BallotVote {
		@Override
		public String toString() {
			try {
				return this.generateString();
			} catch (BadMessageException e) {
				return "ERROR " + e.getMessage();
			}
		}
	}

	@Test
	public void parseValidString() {
		String correct = "[10,1]";

		try {
			Assert.assertNotNull(BallotVote.parse(new JSONArray(correct)));
		} catch (Exception e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void parseInvalidType() {
		String correct = "[\"a\",\"b\"]";

		try {
			try {
				BallotVote.parse(new JSONArray(correct));
			} catch (JSONException e) {
				e.printStackTrace();
			}
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
		BallotVote v = new BallotVotString();
		v.setId(100);
		v.setValue(1);

		try {
			JSONArray o = new JSONArray("[100, 1]");
			Assert.assertEquals(
					o.toString(),
					v.toString()
			);
		} catch (JSONException e) {
			Assert.fail("internal error");
		}

	}
}

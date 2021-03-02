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

package ch.threema.client.voip;

import ch.threema.client.BadMessageException;
import junit.framework.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;

import static ch.threema.client.voip.VoipICECandidatesData.Candidate;

public class VoipICECandidatesDataDataTest {

	/**
	 * A valid candidate.
	 */
	@Test
	public void testSerializeValidCandidate() throws Exception {
		final Candidate candidate1 = new Candidate("foo", "bar", 3, "baz");
		//noinspection ConstantConditions
		final Candidate candidate2 = new Candidate("a", null, null, null);

		// Note: Order of fields depends on the JSON impl :(
		final String json1 = candidate1.toJSON().toString();
		Assert.assertTrue(
			json1,
			json1.equals("{\"candidate\":\"foo\",\"sdpMid\":\"bar\",\"sdpMLineIndex\":3,\"ufrag\":\"baz\"}") ||
			json1.equals("{\"sdpMLineIndex\":3,\"candidate\":\"foo\",\"ufrag\":\"baz\",\"sdpMid\":\"bar\"}")
		);

		final String json2 = candidate2.toJSON().toString();
		Assert.assertTrue(
			json2,
			json2.equals("{\"candidate\":\"a\",\"sdpMid\":null,\"sdpMLineIndex\":null,\"ufrag\":null}") ||
			json2.equals("{\"sdpMLineIndex\":null,\"candidate\":\"a\",\"ufrag\":null,\"sdpMid\":null}")
		);
	}

	/**
	 * Serialize a valid candidates message.
	 */
	@Test
	public void testSerializeValidCandidates() throws Exception {
		final Candidate candidate1 = new Candidate("foo", "bar", 3, "baz");
		//noinspection ConstantConditions
		final Candidate candidate2 = new Candidate("a", null, null, null);

		final VoipICECandidatesData msg = new VoipICECandidatesData()
			.setCandidates(new Candidate[] { candidate1, candidate2 });

		final ByteArrayOutputStream bos = new ByteArrayOutputStream();
		msg.write(bos);
		final String json = bos.toString();

		Assert.assertTrue(json, json.contains("\"removed\":false"));
		Assert.assertTrue(json, json.contains("\"candidates\":["));
		Assert.assertTrue(
			json,
			json.contains("{\"candidate\":\"foo\",\"sdpMid\":\"bar\",\"sdpMLineIndex\":3,\"ufrag\":\"baz\"}") ||
			json.contains("{\"sdpMLineIndex\":3,\"candidate\":\"foo\",\"ufrag\":\"baz\",\"sdpMid\":\"bar\"}")
		);
		Assert.assertTrue(
			json,
			json.contains("{\"candidate\":\"a\",\"sdpMid\":null,\"sdpMLineIndex\":null,\"ufrag\":null}") ||
			json.contains("{\"sdpMLineIndex\":null,\"candidate\":\"a\",\"ufrag\":null,\"sdpMid\":null}")
		);
	}

	/**
	 * Parse a valid candidates message.
	 */
	@Test
	public void testParseValidCandidates() throws Exception {
		final VoipICECandidatesData parsed = VoipICECandidatesData.parse("{\"candidates\":[{\"sdpMLineIndex\":3,\"candidate\":\"foo\",\"ufrag\":\"baz\",\"sdpMid\":\"bar\"},{\"sdpMLineIndex\":null,\"candidate\":\"a\",\"ufrag\":null,\"sdpMid\":null}],\"removed\":true}");

		Assert.assertTrue(parsed.isRemoved());
		Assert.assertNotNull(parsed.getCandidates());
		Assert.assertEquals(2, parsed.getCandidates().length);

		final Candidate candidate1 = parsed.getCandidates()[0];
		Assert.assertEquals("foo", candidate1.getCandidate());
		Assert.assertEquals("bar", candidate1.getSdpMid());
		Assert.assertEquals(Integer.valueOf(3), candidate1.getSdpMLineIndex());
		Assert.assertEquals("baz", candidate1.getUfrag());

		final Candidate candidate2 = parsed.getCandidates()[1];
		Assert.assertEquals("a", candidate2.getCandidate());
		Assert.assertNull(candidate2.getSdpMid());
		Assert.assertNull(candidate2.getSdpMLineIndex());
		Assert.assertNull(candidate2.getUfrag());
	}

	/**
	 * Candidates message without candidates
	 */
	@Test
	public void parseNoCandidates() {
		try {
			VoipICECandidatesData.parse("{\"removed\":true}");
			Assert.fail("BadMessageException not thrown");
		} catch (BadMessageException e) { /* ok */ }
	}

	/**
	 * Candidates message with null candidates list
	 */
	@Test
	public void parseNullCandidatesList() {
		try {
			VoipICECandidatesData.parse("{\"removed\":true,\"candidates\":null}");
			Assert.fail("BadMessageException not thrown");
		} catch (BadMessageException e) { /* ok */ }
	}

	/**
	 * Candidates message with null candidates
	 */
	@Test
	public void parseNullCandidates() throws BadMessageException {
		try {
			VoipICECandidatesData.parse("{\"removed\":true,\"candidates\":[null]}");
			Assert.fail("BadMessageException not thrown");
		} catch (BadMessageException e) { /* ok */ }
	}

}

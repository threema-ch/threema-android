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
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;

public class VoipICECandidatesDataTest {
	@Test
	public void filterTest() {
		VoipICECandidatesData data = new VoipICECandidatesData();

		data.setCandidates(new VoipICECandidatesData.Candidate[]{
			new VoipICECandidatesData.Candidate("c1", "c1", 0, "c1"),
			new VoipICECandidatesData.Candidate("c2", "c2", 0, "c2"),
			new VoipICECandidatesData.Candidate("c3", "c3", 0, "c3"),
			new VoipICECandidatesData.Candidate("c4", "c4", 0, "c4"),
			new VoipICECandidatesData.Candidate("c5", "c5", 0, "c5")
		});

		// Filter candidates
		data.filter(candidate -> {
			return candidate.getCandidate().equals("c1")
				|| candidate.getCandidate().equals("c3")
				|| candidate.getCandidate().equals("c5");
		});

		Assert.assertNotNull(data.getCandidates());
		Assert.assertEquals("3 candidates expected", 3, data.getCandidates().length);
		Assert.assertEquals("c1", data.getCandidates()[0].getCandidate());
		Assert.assertEquals("c3", data.getCandidates()[1].getCandidate());
		Assert.assertEquals("c5", data.getCandidates()[2].getCandidate());
	}

	@Test
	public void filterNoCandidatesTest() {
		VoipICECandidatesData data = new VoipICECandidatesData();
		data.setCandidates(new VoipICECandidatesData.Candidate[]{
			new VoipICECandidatesData.Candidate("c1", "c1", 0, "c1")
		});
	}

	@Test
	public void createCandidatesWithCallId() throws Exception {
		final VoipICECandidatesData msg = new VoipICECandidatesData()
			.setCallId(9001)
			.setCandidates(new VoipICECandidatesData.Candidate[]{
				new VoipICECandidatesData.Candidate("c1", "c1", 0, "c1")
			});

		final ByteArrayOutputStream bos = new ByteArrayOutputStream();
		msg.write(bos);
		final String json = bos.toString();

		Assert.assertTrue(json, json.contains("\"callId\":9001"));
		Assert.assertTrue(json, json.contains("\"removed\":false"));

		Assert.assertTrue(json, json.contains("\"candidates\":["));
		Assert.assertTrue(json, json.contains("\"candidate\":\"c1\""));
		Assert.assertTrue(json, json.contains("\"sdpMid\":\"c1\""));
		Assert.assertTrue(json, json.contains("\"sdpMLineIndex\":0"));
		Assert.assertTrue(json, json.contains("\"ufrag\":\"c1\""));
	}

	@Test
	public void parseCandidatesWithCallId() throws BadMessageException {
		final VoipICECandidatesData parsed = VoipICECandidatesData.parse(
			"{\"callId\":42,\"candidates\":[{\"sdpMLineIndex\":0,\"candidate\":\"c1\",\"ufrag\":\"c1\",\"sdpMid\":\"c1\"}],\"removed\":false}"
		);
		junit.framework.Assert.assertEquals(Long.valueOf(42), parsed.getCallId());
	}

	@Test
	public void parseCandidatesWithoutCallId() throws BadMessageException {
		final VoipICECandidatesData parsed = VoipICECandidatesData.parse(
			"{\"candidates\":[{\"sdpMLineIndex\":0,\"candidate\":\"c1\",\"ufrag\":\"c1\",\"sdpMid\":\"c1\"}],\"removed\":false}"
		);
		junit.framework.Assert.assertNull(parsed.getCallId());
	}
}

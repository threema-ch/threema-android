/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2025 Threema GmbH
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

package ch.threema.domain.protocol.csp.messages.voip;

import ch.threema.domain.protocol.csp.messages.BadMessageException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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

        Assertions.assertNotNull(data.getCandidates());
        Assertions.assertEquals(3, data.getCandidates().length, "3 candidates expected");
        Assertions.assertEquals("c1", data.getCandidates()[0].getCandidate());
        Assertions.assertEquals("c3", data.getCandidates()[1].getCandidate());
        Assertions.assertEquals("c5", data.getCandidates()[2].getCandidate());
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

        Assertions.assertTrue(json.contains("\"callId\":9001"), json);
        Assertions.assertTrue(json.contains("\"removed\":false"), json);

        Assertions.assertTrue(json.contains("\"candidates\":["), json);
        Assertions.assertTrue(json.contains("\"candidate\":\"c1\""), json);
        Assertions.assertTrue(json.contains("\"sdpMid\":\"c1\""), json);
        Assertions.assertTrue(json.contains("\"sdpMLineIndex\":0"), json);
        Assertions.assertTrue(json.contains("\"ufrag\":\"c1\""), json);
    }

    @Test
    public void parseCandidatesWithCallId() throws BadMessageException {
        final VoipICECandidatesData parsed = VoipICECandidatesData.parse(
            "{\"callId\":42,\"candidates\":[{\"sdpMLineIndex\":0,\"candidate\":\"c1\",\"ufrag\":\"c1\",\"sdpMid\":\"c1\"}],\"removed\":false}"
        );
        Assertions.assertEquals(Long.valueOf(42), parsed.getCallId());
    }

    @Test
    public void parseCandidatesWithoutCallId() throws BadMessageException {
        final VoipICECandidatesData parsed = VoipICECandidatesData.parse(
            "{\"candidates\":[{\"sdpMLineIndex\":0,\"candidate\":\"c1\",\"ufrag\":\"c1\",\"sdpMid\":\"c1\"}],\"removed\":false}"
        );
        Assertions.assertNull(parsed.getCallId());
    }
}

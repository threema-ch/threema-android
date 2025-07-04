/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

package ch.threema.domain.protocol.csp.messages.ballot;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import ch.threema.domain.protocol.csp.messages.BadMessageException;

public class BallotDataTest {
    private static final String testBallot = "{"
        + "\"d\":\"Ballotelli\","
        + "\"s\":0,"
        + "\"a\":0,"
        + "\"t\":1,"
        + "\"o\":0,"
        + "\"c\":["
        + "{"
        + "\"i\":1,"
        + "\"n\":\"desc1\","
        + "\"o\":1,"
        + "\"r\":[1,0],"
        + "\"t\":2"
        + "},"
        + "{"
        + "\"i\":2,"
        + "\"n\":\"desc2\","
        + "\"o\":2,"
        + "\"r\":[1,1],"
        + "\"t\":2"
        + "}"
        + "]," +
        "\"p\":[\"ECHOECH1\",\"ECHOECH2\"],"
        + "\"u\":0"
        + "}";

    @Test
    public void parseValidString() {
        BallotData result = null;
        try {
            result = BallotData.parse(testBallot);
        } catch (BadMessageException e) {
            Assertions.fail(e.getMessage());
        }
        Assertions.assertNotNull(result);

        Assertions.assertEquals("Ballotelli", result.getDescription());

        Assertions.assertEquals(2, result.getChoiceList().size());

        Assertions.assertEquals(BallotData.State.OPEN, result.getState());
        Assertions.assertEquals(BallotData.AssessmentType.SINGLE, result.getAssessmentType());
        Assertions.assertEquals(BallotData.Type.INTERMEDIATE, result.getType());
        Assertions.assertEquals(BallotData.ChoiceType.TEXT, result.getChoiceType());
        Assertions.assertEquals(2, result.getParticipants().size());
        Assertions.assertEquals("ECHOECH2", result.getParticipants().get(1));
    }


    @Test
    public void parseInvalidString() {
        try {
            BallotData.parse("i want to be a hippie");
            Assertions.fail("invalid string parsed");
        } catch (BadMessageException e) {
            //ok! exception received
        }
    }

    @Test
    public void generateStringTest() {
        BallotData d = new BallotData();
        d.setDescription("Ballotelli");
        d.setState(BallotData.State.OPEN);
        d.setAssessmentType(BallotData.AssessmentType.SINGLE);
        d.setType(BallotData.Type.INTERMEDIATE);
        d.setChoiceType(BallotData.ChoiceType.TEXT);
        int posEcho1 = d.addParticipant("ECHOECH1");
        Assertions.assertEquals(0, posEcho1);
        int posEcho2 = d.addParticipant("ECHOECH2");
        Assertions.assertEquals(1, posEcho2);


        BallotDataChoice c1 = new BallotDataChoice(2);
        c1.setId(1);
        c1.setName("desc1");
        c1.setOrder(1);
        c1.addResult(0, 1).addResult(1, 0);
        c1.setTotalVotes(2);
        d.getChoiceList().add(c1);

        BallotDataChoice c2 = new BallotDataChoice(2);
        c2.setId(2);
        c2.setOrder(2);
        c2.setName("desc2");
        c2.addResult(0, 1).addResult(1, 1);
        c2.setTotalVotes(2);
        d.getChoiceList().add(c2);
        d.setDisplayType(BallotData.DisplayType.LIST_MODE);

        try {
            BallotData b = BallotData.parse(testBallot);
            Assertions.assertEquals(b.generateString(), d.generateString());
        } catch (BadMessageException e) {
            Assertions.fail(e.getMessage());
        }
    }
}

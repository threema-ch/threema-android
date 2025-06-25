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
import ch.threema.domain.protocol.csp.messages.voip.features.CallFeature;
import ch.threema.domain.protocol.csp.messages.voip.features.FeatureList;
import ch.threema.domain.protocol.csp.messages.voip.features.VideoFeature;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.stream.Collectors;

public class VoipCallAnswerDataTest {

    /**
     * A valid accept answer.
     */
    @Test
    void testAcceptAnswer() throws Exception {
        final VoipCallAnswerData.AnswerData answerData = new VoipCallAnswerData.AnswerData();
        answerData.setSdp("sdpsdp");
        answerData.setSdpType("answer");

        final VoipCallAnswerData msg = new VoipCallAnswerData();
        msg.setAction(VoipCallAnswerData.Action.ACCEPT);
        msg.setAnswerData(answerData);

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        msg.write(bos);
        final String json = bos.toString();

        Assertions.assertTrue(json.contains("\"action\":1"));
        Assertions.assertTrue(json.contains("\"answer\":{\"sdpType\":\"answer\",\"sdp\":\"sdpsdp\"}"));
    }

    /**
     * A valid accept answer (rollback -> sdp is null).
     */
    @Test
    void testAcceptRollbackAnswer() throws Exception {
        final VoipCallAnswerData.AnswerData answerData = new VoipCallAnswerData.AnswerData();
        answerData.setSdp(null);
        answerData.setSdpType("rollback");

        final VoipCallAnswerData msg = new VoipCallAnswerData();
        msg.setAction(VoipCallAnswerData.Action.ACCEPT);
        msg.setAnswerData(answerData);

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        msg.write(bos);
        final String json = bos.toString();

        Assertions.assertTrue(json.contains("\"action\":1"));
        Assertions.assertTrue(json.contains("\"answer\":{\"sdpType\":\"rollback\",\"sdp\":null}"));
    }

    /**
     * A valid reject answer.
     */
    @Test
    void testRejectAnswer() throws Exception {
        final VoipCallAnswerData msg = new VoipCallAnswerData();
        msg.setAction(VoipCallAnswerData.Action.REJECT);
        msg.setRejectReason(VoipCallAnswerData.RejectReason.BUSY);

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        msg.write(bos);
        final String json = bos.toString();

        Assertions.assertTrue(json.contains("\"action\":0"));
        Assertions.assertTrue(json.contains("\"rejectReason\":1"));
    }

    /**
     * Reject answer without reason.
     */
    @Test
    void testRejectAnswerInvalid() throws Exception {
        final VoipCallAnswerData msg = new VoipCallAnswerData();
        msg.setAction(VoipCallAnswerData.Action.REJECT);
        // No reject reason
        try {
            msg.write(new ByteArrayOutputStream());
            Assertions.fail("BadMessageException not thrown");
        } catch (BadMessageException e) { /* ok */ }
    }

    /**
     * Accept answer without reason.
     */
    @Test
    void testAcceptAnswerInvalid() throws Exception {
        final VoipCallAnswerData msg = new VoipCallAnswerData();
        msg.setAction(VoipCallAnswerData.Action.ACCEPT);
        // No answer data
        try {
            msg.write(new ByteArrayOutputStream());
            Assertions.fail("BadMessageException not thrown");
        } catch (BadMessageException e) { /* ok */ }
    }

    /**
     * Answer with both accept data and reject reason.
     */
    @Test
    void testMixedAnswerInvalid() throws Exception {
        final VoipCallAnswerData.AnswerData answerData = new VoipCallAnswerData.AnswerData();
        answerData.setSdp("sdpsdp");
        answerData.setSdpType("answer");

        final VoipCallAnswerData msg = new VoipCallAnswerData();
        msg.setAction(VoipCallAnswerData.Action.ACCEPT);
        msg.setAnswerData(answerData);
        msg.setRejectReason(VoipCallAnswerData.RejectReason.UNKNOWN);

        try {
            msg.write(new ByteArrayOutputStream());
            Assertions.fail("BadMessageException not thrown");
        } catch (BadMessageException e) { /* ok */ }
    }

    /**
     * Answer with unknown action type.
     */
    @Test
    void testInvalidAction() throws Exception {
        final VoipCallAnswerData msg = new VoipCallAnswerData();
        msg.setAction((byte) 7);
        try {
            msg.write(new ByteArrayOutputStream());
            Assertions.fail("BadMessageException not thrown");
        } catch (BadMessageException e) { /* ok */ }
    }

    /**
     * Valid accept answer.
     */
    @Test
    void parseValidAcceptAnswer() throws BadMessageException {
        final VoipCallAnswerData answer = VoipCallAnswerData.parse(
            "{\"answer\":{\"sdpType\":\"answer\",\"sdp\":\"sdpsdp\"},\"action\":1}"
        );
        Assertions.assertEquals((Byte) VoipCallAnswerData.Action.ACCEPT, answer.getAction());
        Assertions.assertNull(answer.getRejectReason());
        VoipCallAnswerData.AnswerData data = answer.getAnswerData();
        Assertions.assertNotNull(data);
        Assertions.assertEquals("answer", data.getSdpType());
        Assertions.assertEquals("sdpsdp", data.getSdp());
    }

    /**
     * Valid reject answer.
     */
    @Test
    void parseValidRejectAnswer() throws BadMessageException {
        final VoipCallAnswerData answer = VoipCallAnswerData.parse(
            "{\"rejectReason\":1,\"action\":0}"
        );
        Assertions.assertEquals((Byte) VoipCallAnswerData.Action.REJECT, answer.getAction());
        Assertions.assertNull(answer.getAnswerData());
        Assertions.assertEquals((Byte) VoipCallAnswerData.RejectReason.BUSY, answer.getRejectReason());
    }

    /**
     * Accept answer with sdpType = offer
     */
    @Test
    void parseAcceptAnswerTypeOffer() {
        try {
            VoipCallAnswerData.parse(
                "{\"answer\":{\"sdpType\":\"offer\",\"sdp\":\"sdpsdp\"},\"action\":1}"
            );
            Assertions.fail("BadMessageException not thrown");
        } catch (BadMessageException e) { /* ok */ }
    }

    /**
     * Accept answer with sdpType = answer and sdp = null
     */
    @Test
    void parseAcceptAnswerTypeAnswerSdpNull() {
        try {
            VoipCallAnswerData.parse(
                "{\"answer\":{\"sdpType\":\"answer\",\"sdp\":null},\"action\":1}"
            );
            Assertions.fail("BadMessageException not thrown");
        } catch (BadMessageException e) { /* ok */ }
    }

    /**
     * Accept answer with sdpType = rollback and sdp = null
     */
    @Test
    void parseAcceptAnswerTypeRollbackSdpNull() {
        Assertions.assertDoesNotThrow(() -> VoipCallAnswerData.parse(
                "{\"answer\":{\"sdpType\":\"rollback\",\"sdp\":null},\"action\":1}"
        ));
    }

    @Test
    void createAnswerWithFeatures() throws Exception {
        final VoipCallAnswerData.AnswerData answerData = new VoipCallAnswerData.AnswerData()
            .setSdpType("answer")
            .setSdp("sdpsdp");

        final VoipCallAnswerData msg = new VoipCallAnswerData()
            .setAction(VoipCallAnswerData.Action.ACCEPT)
            .setAnswerData(answerData)
            .addFeature(new VideoFeature());

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        msg.write(bos);
        final String json = bos.toString();

        Assertions.assertTrue(json.contains("\"action\":1"));
        Assertions.assertTrue(json.contains("\"answer\":{\"sdpType\":\"answer\",\"sdp\":\"sdpsdp\"}"));
        Assertions.assertTrue(json.contains("\"features\":{\"video\":null}"));
    }

    @Test
    void parseAnswerWithoutFeatures() throws BadMessageException {
        final VoipCallAnswerData parsed = VoipCallAnswerData.parse(
            "{\"answer\":{\"sdpType\":\"answer\",\"sdp\":\"sdpsdp\"},\"action\":1}"
        );
        Assertions.assertNotNull(parsed.getAnswerData());
        Assertions.assertEquals("answer", parsed.getAnswerData().getSdpType());
        Assertions.assertTrue(parsed.getFeatures().isEmpty());
    }

    @Test
    void parseAnswerWithFeatures() throws BadMessageException {
        final VoipCallAnswerData parsed = VoipCallAnswerData.parse(
            "{" +
                "\"answer\":{\"sdpType\":\"answer\",\"sdp\":\"sdpsdp\"}," +
                "\"features\":{" +
                "\"video\":null," +
                "\"superextension\":{\"a\":1}" +
                "}," +
                "\"action\":1" +
                "}"
        );
        Assertions.assertNotNull(parsed.getAnswerData());
        Assertions.assertEquals("answer", parsed.getAnswerData().getSdpType());
        final FeatureList features = parsed.getFeatures();
        Assertions.assertEquals(2, features.size());

        final List<String> names = features
            .getList()
            .stream()
            .map(CallFeature::getName)
            .sorted()
            .collect(Collectors.toList());
        Assertions.assertEquals("superextension", names.get(0));
        Assertions.assertEquals("video", names.get(1));
    }

    @Test
    void createAnswerWithCallId() throws Exception {
        final VoipCallAnswerData msg = new VoipCallAnswerData()
            .setCallId(42)
            .setAction(VoipCallAnswerData.Action.REJECT)
            .setRejectReason(VoipCallAnswerData.RejectReason.BUSY);

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        msg.write(bos);
        final String json = bos.toString();

        Assertions.assertTrue(json.contains("\"callId\":42"));
        Assertions.assertTrue(json.contains("\"action\":0"));
        Assertions.assertTrue(json.contains("\"rejectReason\":1"));
    }

    @Test
    void parseAnswerWithCallId() throws BadMessageException {
        final VoipCallAnswerData parsed = VoipCallAnswerData.parse(
            "{\"rejectReason\":1,\"action\":0,\"callId\":1337}"
        );
        Assertions.assertEquals(Long.valueOf(1337), parsed.getCallId());
    }

    @Test
    void parseAnswerWithoutCallId() throws BadMessageException {
        final VoipCallAnswerData parsed = VoipCallAnswerData.parse(
            "{\"rejectReason\":1,\"action\":0}"
        );
        Assertions.assertNull(parsed.getCallId());
    }
}

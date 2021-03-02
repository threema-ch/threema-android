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
import ch.threema.client.voip.features.CallFeature;
import ch.threema.client.voip.features.FeatureList;
import ch.threema.client.voip.features.VideoFeature;
import junit.framework.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.stream.Collectors;

public class VoipCallAnswerDataTest {

	/**
	 * A valid accept answer.
	 */
	@Test
	public void testAcceptAnswer() throws Exception {
		final VoipCallAnswerData.AnswerData answerData = new VoipCallAnswerData.AnswerData();
		answerData.setSdp("sdpsdp");
		answerData.setSdpType("answer");

		final VoipCallAnswerData msg = new VoipCallAnswerData();
		msg.setAction(VoipCallAnswerData.Action.ACCEPT);
		msg.setAnswerData(answerData);

		final ByteArrayOutputStream bos = new ByteArrayOutputStream();
		msg.write(bos);
		final String json = bos.toString();

		Assert.assertTrue(json.contains("\"action\":1"));
		Assert.assertTrue(json.contains("\"answer\":{\"sdpType\":\"answer\",\"sdp\":\"sdpsdp\"}"));
	}

	/**
	 * A valid accept answer (rollback -> sdp is null).
	 */
	@Test
	public void testAcceptRollbackAnswer() throws Exception {
		final VoipCallAnswerData.AnswerData answerData = new VoipCallAnswerData.AnswerData();
		answerData.setSdp(null);
		answerData.setSdpType("rollback");

		final VoipCallAnswerData msg = new VoipCallAnswerData();
		msg.setAction(VoipCallAnswerData.Action.ACCEPT);
		msg.setAnswerData(answerData);

		final ByteArrayOutputStream bos = new ByteArrayOutputStream();
		msg.write(bos);
		final String json = bos.toString();

		Assert.assertTrue(json.contains("\"action\":1"));
		Assert.assertTrue(json.contains("\"answer\":{\"sdpType\":\"rollback\",\"sdp\":null}"));
	}

	/**
	 * A valid reject answer.
	 */
	@Test
	public void testRejectAnswer() throws Exception {
		final VoipCallAnswerData msg = new VoipCallAnswerData();
		msg.setAction(VoipCallAnswerData.Action.REJECT);
		msg.setRejectReason(VoipCallAnswerData.RejectReason.BUSY);

		final ByteArrayOutputStream bos = new ByteArrayOutputStream();
		msg.write(bos);
		final String json = bos.toString();

		Assert.assertTrue(json.contains("\"action\":0"));
		Assert.assertTrue(json.contains("\"rejectReason\":1"));
	}

	/**
	 * Reject answer without reason.
	 */
	@Test
	public void testRejectAnswerInvalid() throws Exception {
		final VoipCallAnswerData msg = new VoipCallAnswerData();
		msg.setAction(VoipCallAnswerData.Action.REJECT);
		// No reject reason
		try {
			msg.write(new ByteArrayOutputStream());
			Assert.fail("BadMessageException not thrown");
		} catch (BadMessageException e) { /* ok */ }
	}

	/**
	 * Accept answer without reason.
	 */
	@Test
	public void testAcceptAnswerInvalid() throws Exception {
		final VoipCallAnswerData msg = new VoipCallAnswerData();
		msg.setAction(VoipCallAnswerData.Action.ACCEPT);
		// No answer data
		try {
			msg.write(new ByteArrayOutputStream());
			Assert.fail("BadMessageException not thrown");
		} catch (BadMessageException e) { /* ok */ }
	}

	/**
	 * Answer with both accept data and reject reason.
	 */
	@Test
	public void testMixedAnswerInvalid() throws Exception {
		final VoipCallAnswerData.AnswerData answerData = new VoipCallAnswerData.AnswerData();
		answerData.setSdp("sdpsdp");
		answerData.setSdpType("answer");

		final VoipCallAnswerData msg = new VoipCallAnswerData();
		msg.setAction(VoipCallAnswerData.Action.ACCEPT);
		msg.setAnswerData(answerData);
		msg.setRejectReason(VoipCallAnswerData.RejectReason.UNKNOWN);

		try {
			msg.write(new ByteArrayOutputStream());
			Assert.fail("BadMessageException not thrown");
		} catch (BadMessageException e) { /* ok */ }
	}

	/**
	 * Answer with unknown action type.
	 */
	@Test
	public void testInvalidAction() throws Exception {
		final VoipCallAnswerData msg = new VoipCallAnswerData();
		msg.setAction((byte) 7);
		try {
			msg.write(new ByteArrayOutputStream());
			Assert.fail("BadMessageException not thrown");
		} catch (BadMessageException e) { /* ok */ }
	}

	/**
	 * Valid accept answer.
	 */
	@Test
	public void parseValidAcceptAnswer() throws BadMessageException {
		final VoipCallAnswerData answer = VoipCallAnswerData.parse(
			"{\"answer\":{\"sdpType\":\"answer\",\"sdp\":\"sdpsdp\"},\"action\":1}"
		);
		Assert.assertEquals((Byte) VoipCallAnswerData.Action.ACCEPT, answer.getAction());
		Assert.assertNull(answer.getRejectReason());
		VoipCallAnswerData.AnswerData data = answer.getAnswerData();
		Assert.assertNotNull(data);
		Assert.assertEquals(data.getSdpType(), "answer");
		Assert.assertEquals(data.getSdp(), "sdpsdp");
	}

	/**
	 * Valid reject answer.
	 */
	@Test
	public void parseValidRejectAnswer() throws BadMessageException {
		final VoipCallAnswerData answer = VoipCallAnswerData.parse(
			"{\"rejectReason\":1,\"action\":0}"
		);
		Assert.assertEquals((Byte) VoipCallAnswerData.Action.REJECT, answer.getAction());
		Assert.assertEquals(null, answer.getAnswerData());
		Assert.assertEquals(answer.getRejectReason(), (Byte) VoipCallAnswerData.RejectReason.BUSY);
	}

	/**
	 * Accept answer with sdpType = offer
	 */
	@Test
	public void parseAcceptAnswerTypeOffer() throws BadMessageException {
		try {
			VoipCallAnswerData.parse(
					"{\"answer\":{\"sdpType\":\"offer\",\"sdp\":\"sdpsdp\"},\"action\":1}"
			);
			Assert.fail("BadMessageException not thrown");
		} catch (BadMessageException e) { /* ok */ }
	}

	/**
	 * Accept answer with sdpType = answer and sdp = null
	 */
	@Test
	public void parseAcceptAnswerTypeAnswerSdpNull() throws BadMessageException {
		try {
			VoipCallAnswerData.parse(
					"{\"answer\":{\"sdpType\":\"answer\",\"sdp\":null},\"action\":1}"
			);
			Assert.fail("BadMessageException not thrown");
		} catch (BadMessageException e) { /* ok */ }
	}

	/**
	 * Accept answer with sdpType = rollback and sdp = null
	 */
	@Test
	public void parseAcceptAnswerTypeRollbackSdpNull() throws BadMessageException {
		VoipCallAnswerData.parse(
				"{\"answer\":{\"sdpType\":\"rollback\",\"sdp\":null},\"action\":1}"
		);
	}

	@Test
	public void createAnswerWithFeatures() throws Exception {
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

		Assert.assertTrue(json.contains("\"action\":1"));
		Assert.assertTrue(json.contains("\"answer\":{\"sdpType\":\"answer\",\"sdp\":\"sdpsdp\"}"));
		Assert.assertTrue(json.contains("\"features\":{\"video\":null}"));
	}

	@Test
	public void parseAnswerWithoutFeatures() throws BadMessageException {
		final VoipCallAnswerData parsed = VoipCallAnswerData.parse(
			"{\"answer\":{\"sdpType\":\"answer\",\"sdp\":\"sdpsdp\"},\"action\":1}"
		);
		Assert.assertNotNull(parsed.getAnswerData());
		Assert.assertEquals("answer", parsed.getAnswerData().getSdpType());
		Assert.assertTrue(parsed.getFeatures().isEmpty());
	}

	@Test
	public void parseAnswerWithFeatures() throws BadMessageException {
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
		Assert.assertNotNull(parsed.getAnswerData());
		Assert.assertEquals("answer", parsed.getAnswerData().getSdpType());
		final FeatureList features = parsed.getFeatures();
		Assert.assertEquals(2, features.size());

		final List<String> names = features
			.getList()
			.stream()
			.map(CallFeature::getName)
			.sorted()
			.collect(Collectors.toList());
		Assert.assertEquals(names.get(0), "superextension");
		Assert.assertEquals(names.get(1), "video");
	}

	@Test
	public void createAnswerWithCallId() throws Exception {
		final VoipCallAnswerData msg = new VoipCallAnswerData()
			.setCallId(42)
			.setAction(VoipCallAnswerData.Action.REJECT)
			.setRejectReason(VoipCallAnswerData.RejectReason.BUSY);

		final ByteArrayOutputStream bos = new ByteArrayOutputStream();
		msg.write(bos);
		final String json = bos.toString();

		Assert.assertTrue(json.contains("\"callId\":42"));
		Assert.assertTrue(json.contains("\"action\":0"));
		Assert.assertTrue(json.contains("\"rejectReason\":1"));
	}

	@Test
	public void parseAnswerWithCallId() throws BadMessageException {
		final VoipCallAnswerData parsed = VoipCallAnswerData.parse(
			"{\"rejectReason\":1,\"action\":0,\"callId\":1337}"
		);
		Assert.assertEquals(Long.valueOf(1337), parsed.getCallId());
	}

	@Test
	public void parseAnswerWithoutCallId() throws BadMessageException {
		final VoipCallAnswerData parsed = VoipCallAnswerData.parse(
			"{\"rejectReason\":1,\"action\":0}"
		);
		Assert.assertNull(parsed.getCallId());
	}
}

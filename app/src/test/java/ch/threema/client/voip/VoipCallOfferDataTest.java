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

public class VoipCallOfferDataTest {

	/**
	 * A valid offer.
	 */
	@Test
	public void testValidOffer() throws Exception {
		final VoipCallOfferData.OfferData offerData = new VoipCallOfferData.OfferData()
			.setSdpType("offer")
			.setSdp("sdpsdp");

		final VoipCallOfferData msg = new VoipCallOfferData()
			.setOfferData(offerData);

		final ByteArrayOutputStream bos = new ByteArrayOutputStream();
		msg.write(bos);
		final String json = bos.toString();

		Assert.assertEquals("{\"offer\":{\"sdpType\":\"offer\",\"sdp\":\"sdpsdp\"}}", json);
	}

	@Test
	public void testToString() {
		final VoipCallOfferData.OfferData offerData = new VoipCallOfferData.OfferData();
		offerData.setSdpType("offer");
		offerData.setSdp("sdpsdp");
		Assert.assertEquals("OfferData{sdpType='offer', sdp='sdpsdp'}", offerData.toString());
	}

	/**
	 * A valid offer (rollback -> sdp is null).
	 */
	@Test
	public void testRollbackOffer() throws Exception {
		final VoipCallOfferData.OfferData offerData = new VoipCallOfferData.OfferData();
		offerData.setSdpType("rollback");
		//noinspection ConstantConditions
		offerData.setSdp(null);

		final VoipCallOfferData msg = new VoipCallOfferData();
		msg.setOfferData(offerData);

		final ByteArrayOutputStream bos = new ByteArrayOutputStream();
		msg.write(bos);
		final String json = bos.toString();

		Assert.assertEquals("{\"offer\":{\"sdpType\":\"rollback\",\"sdp\":null}}", json);
	}

	/**
	 * Offer sdpType = answer
	 */
	@Test
	public void parseOfferTypeAnswer() {
		try {
			VoipCallOfferData.parse(
					"{\"offer\":{\"sdpType\":\"answer\",\"sdp\":\"sdpsdp\"}}"
			);
			Assert.fail("BadMessageException not thrown");
		} catch (BadMessageException e) { /* ok */ }
	}

	/**
	 * Offer sdpType = pranswer
	 */
	@Test
	public void parseOfferTypePranswer() {
		try {
			VoipCallOfferData.parse(
					"{\"offer\":{\"sdpType\":\"pranswer\",\"sdp\":\"sdpsdp\"}}"
			);
			Assert.fail("BadMessageException not thrown");
		} catch (BadMessageException e) { /* ok */ }
	}

	/**
	 * Offer with sdpType = offer and sdp = null
	 */
	@Test
	public void parseOfferSdpNull() {
		try {
			VoipCallOfferData.parse(
					"{\"offer\":{\"sdpType\":\"offer\",\"sdp\":null}}"
			);
			Assert.fail("BadMessageException not thrown");
		} catch (BadMessageException e) { /* ok */ }
	}

	/**
	 * Accept answer with sdpType = rollback and sdp = null
	 */
	@Test
	public void parseOfferTypeRollback() throws BadMessageException {
		VoipCallOfferData.parse(
				"{\"offer\":{\"sdpType\":\"rollback\",\"sdp\":null}}"
		);
	}

	@Test
	public void createOfferWithFeatures() throws Exception {
		final VoipCallOfferData.OfferData offerData = new VoipCallOfferData.OfferData()
			.setSdpType("offer")
			.setSdp("sdpsdp");

		final VoipCallOfferData msg = new VoipCallOfferData()
			.setOfferData(offerData)
			.addFeature(new VideoFeature());

		final ByteArrayOutputStream bos = new ByteArrayOutputStream();
		msg.write(bos);
		final String json = bos.toString();

		Assert.assertEquals("{\"offer\":{\"sdpType\":\"offer\",\"sdp\":\"sdpsdp\"},\"features\":{\"video\":null}}", json);
	}

	@Test
	public void parseOfferWithoutFeatures() throws BadMessageException {
		final VoipCallOfferData parsed = VoipCallOfferData.parse(
			"{\"offer\":{\"sdpType\":\"offer\",\"sdp\":\"sdpsdp\"}}"
		);
		Assert.assertNotNull(parsed.getOfferData());
		Assert.assertEquals("offer", parsed.getOfferData().getSdpType());
		Assert.assertTrue(parsed.getFeatures().isEmpty());
	}

	@Test
	public void parseOfferWithFeatures() throws BadMessageException {
		final VoipCallOfferData parsed = VoipCallOfferData.parse(
			"{" +
				"\"offer\":{\"sdpType\":\"offer\",\"sdp\":\"sdpsdp\"}," +
				"\"features\":{" +
					"\"video\":null," +
					"\"superextension\":{\"a\":1}" +
				"}" +
			"}"
		);
		Assert.assertNotNull(parsed.getOfferData());
		Assert.assertEquals("offer", parsed.getOfferData().getSdpType());
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
	public void createOfferWithCallId() throws Exception {
		final VoipCallOfferData.OfferData offerData = new VoipCallOfferData.OfferData()
			.setSdpType("offer")
			.setSdp("sdpsdp");

		final VoipCallOfferData msg = new VoipCallOfferData()
			.setCallId(1337)
			.setOfferData(offerData);

		final ByteArrayOutputStream bos = new ByteArrayOutputStream();
		msg.write(bos);
		final String json = bos.toString();

		Assert.assertEquals("{\"callId\":1337,\"offer\":{\"sdpType\":\"offer\",\"sdp\":\"sdpsdp\"}}", json);
	}

	@Test
	public void validateCallIdZero() {
		// 0 is is valid
		new VoipCallOfferData().setCallId(0);
	}

	@Test
	public void validateCallIdNegative() {
		try {
			// Call ID must be positive
			new VoipCallOfferData().setCallId(-1);
			Assert.fail("IllegalArgumentException not thrown");
		} catch (IllegalArgumentException e) { /* ok */ }
	}

	@Test
	public void validateCallIdTooLarge() {
		try {
			// Call ID must fit in a 32 bit integer
			new VoipCallOfferData().setCallId(4294967296L);
			Assert.fail("IllegalArgumentException not thrown");
		} catch (IllegalArgumentException e) { /* ok */ }
	}

	@Test
	public void parseOfferWithCallId() throws BadMessageException {
		final VoipCallOfferData parsed = VoipCallOfferData.parse(
			"{" +
				"\"callId\":1337," +
				"\"offer\":{\"sdpType\":\"offer\",\"sdp\":\"sdpsdp\"}" +
			"}"
		);
		Assert.assertNotNull(parsed.getOfferData());
		Assert.assertEquals("offer", parsed.getOfferData().getSdpType());
		Assert.assertEquals(Long.valueOf(1337), parsed.getCallId());
	}

	@Test
	public void parseOfferWithoutCallId() throws BadMessageException {
		final VoipCallOfferData parsed = VoipCallOfferData.parse(
			"{\"offer\":{\"sdpType\":\"offer\",\"sdp\":\"sdpsdp\"}}"
		);
		Assert.assertNotNull(parsed.getOfferData());
		Assert.assertEquals("offer", parsed.getOfferData().getSdpType());
		Assert.assertNull(parsed.getCallId());
	}

	@Test
	public void parseOfferWithCallIdNegative() {
		try {
			VoipCallOfferData.parse(
				"{" +
					"\"callId\":-1," +
					"\"offer\":{\"sdpType\":\"offer\",\"sdp\":\"sdpsdp\"}" +
					"}"
			);
			Assert.fail("BadMessageException not thrown");
		} catch (BadMessageException e) { /* ok */ }
	}

	@Test
	public void parseOfferWithCallIdTooLarge() {
		try {
			// Call ID fits in a long, but is larger than 2**32 (which is disallowed by the spec)
			VoipCallOfferData.parse(
				"{" +
					"\"callId\":4294967297," +
					"\"offer\":{\"sdpType\":\"offer\",\"sdp\":\"sdpsdp\"}" +
				"}"
			);
			Assert.fail("BadMessageException not thrown");
		} catch (BadMessageException e) { /* ok */ }
	}

	@Test
	public void parseOfferWithCallIdOutOfRange() {
		try {
			// Call ID does not fit in a long
			VoipCallOfferData.parse(
				"{" +
					"\"callId\":1180591620717411303424," +
					"\"offer\":{\"sdpType\":\"offer\",\"sdp\":\"sdpsdp\"}" +
				"}"
			);
			Assert.fail("BadMessageException not thrown");
		} catch (BadMessageException e) { /* ok */ }
	}

	@Test
	public void parseOfferWithCallIdBoolean() {
		try {
			VoipCallOfferData.parse(
				"{" +
					"\"callId\":false," +
					"\"offer\":{\"sdpType\":\"offer\",\"sdp\":\"sdpsdp\"}" +
				"}"
			);
			Assert.fail("BadMessageException not thrown");
		} catch (BadMessageException e) { /* ok */ }
	}
}

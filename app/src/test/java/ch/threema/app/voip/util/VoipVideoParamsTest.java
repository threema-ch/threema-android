/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2023 Threema GmbH
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

package ch.threema.app.voip.util;

import com.google.protobuf.InvalidProtocolBufferException;

import org.junit.Test;

import ch.threema.protobuf.Common;
import ch.threema.protobuf.callsignaling.O2OCall;
import ch.threema.protobuf.callsignaling.O2OCall.VideoQualityProfile;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;

public class VoipVideoParamsTest {
	/**
	 * Profile round-trip serialization
	 */
	@Test
	public void serializeProfile() throws InvalidProtocolBufferException {
		// Create a high quality profile
		final VoipVideoParams highProfile = VoipVideoParams.high();

		// Serialize to protobuf
		byte[] bytes = highProfile.toSignalingMessageBytes();

		// Deserialize
		final O2OCall.Envelope envelope = O2OCall.Envelope.parseFrom(bytes);

		assertTrue(envelope.hasVideoQualityProfile());
		final VideoQualityProfile profile = envelope.getVideoQualityProfile();
		assertTrue(profile.hasMaxResolution());
		assertEquals(VideoQualityProfile.QualityProfile.HIGH, profile.getProfile());
		assertEquals(2000, profile.getMaxBitrateKbps());
		assertEquals(25, profile.getMaxFps());
		assertEquals(1280, profile.getMaxResolution().getWidth());
		assertEquals(720, profile.getMaxResolution().getHeight());
	}

	/**
	 * Helper class.
	 */
	static class Pair {
		final VoipVideoParams a, b;
		Pair(VoipVideoParams a, VoipVideoParams b) {
			this.a = a;
			this.b = b;
		}
	}

	/**
	 * Find common profile.
	 */
	@Test
	public void findCommonProfileLow() {
		final Pair[] pairs = new Pair[]{
			new Pair(VoipVideoParams.low(), VoipVideoParams.low()),
			new Pair(VoipVideoParams.low(), VoipVideoParams.high()),
			new Pair(VoipVideoParams.low(), VoipVideoParams.max()),
			new Pair(VoipVideoParams.high(), VoipVideoParams.low()),
			new Pair(VoipVideoParams.max(), VoipVideoParams.low()),
		};
		for (Pair pair : pairs) {
			final VoipVideoParams common = pair.a.findCommonProfile(pair.b, false);
			assertEquals(VideoQualityProfile.QualityProfile.LOW, common.getProfile());
		}
	}

	/**
	 * Find common profile.
	 */
	@Test
	public void findCommonProfileHigh() {
		final Pair[] pairs = new Pair[]{
			new Pair(VoipVideoParams.high(), VoipVideoParams.high()),
			new Pair(VoipVideoParams.high(), VoipVideoParams.max()),
			new Pair(VoipVideoParams.max(), VoipVideoParams.high()),
		};
		for (Pair pair : pairs) {
			final VoipVideoParams common = pair.a.findCommonProfile(pair.b, false);
			assertEquals(VideoQualityProfile.QualityProfile.HIGH, common.getProfile());
		}
	}

	/**
	 * Find common profile.
	 */
	@Test
	public void findCommonProfileMax() {
		final VoipVideoParams a = VoipVideoParams.max();
		final VoipVideoParams b = VoipVideoParams.max();

		final VoipVideoParams commonNonRelayed = a.findCommonProfile(b, false);
		final VoipVideoParams commonRelayed = a.findCommonProfile(b, true);
		assertEquals(VideoQualityProfile.QualityProfile.MAX, commonNonRelayed.getProfile());
		assertEquals(VideoQualityProfile.QualityProfile.HIGH, commonRelayed.getProfile());
	}

	/**
	 * Find common profile.
	 */
	@Test
	public void findCommonProfileNull() {
		final VoipVideoParams[] params = new VoipVideoParams[]{
			VoipVideoParams.low(),
			VoipVideoParams.high(),
			VoipVideoParams.max(),
		};
		for (VoipVideoParams param : params) {
			final VoipVideoParams commonNonRelayed = param.findCommonProfile(null, false);
			final VoipVideoParams commonRelayed = param.findCommonProfile(null, true);
			assertEquals(commonNonRelayed, param);
			assertEquals(commonRelayed, param);
		}
	}

	@Test
	public void findCommonProfileRawValues() {
		final VoipVideoParams a = VoipVideoParams.high();
		final VoipVideoParams b = VoipVideoParams.fromSignalingMessage(
			O2OCall.VideoQualityProfile.newBuilder()
				.setProfileValue(1234) // Invalid / unknown
				.setMaxBitrateKbps(600)
				.setMaxFps(23)
				.setMaxResolution(
					Common.Resolution.newBuilder()
						.setWidth(2000)
						.setHeight(700)
				).build()
		);
		assertNotNull(b);

		final VoipVideoParams common = a.findCommonProfile(b, false);
		assertNull(common.getProfile());
		assertEquals(600, common.getMaxBitrateKbps());
		assertEquals(23, common.getMaxFps());
		assertEquals(1280, common.getMaxWidth()); // Value from "high" profile
		assertEquals(700, common.getMaxHeight());
	}

	/**
	 * When the peer sends values that are too low, clamp them.
	 */
	@Test
	public void findCommonProfileRawValuesWithClamping() {
		final VoipVideoParams a = VoipVideoParams.high();
		final VoipVideoParams b = VoipVideoParams.fromSignalingMessage(
			O2OCall.VideoQualityProfile.newBuilder()
				.setProfileValue(1234) // Invalid / unknown
				.setMaxBitrateKbps(1)
				.setMaxFps(1)
				.setMaxResolution(
					Common.Resolution.newBuilder()
						.setWidth(1)
						.setHeight(1)
				).build()
		);
		assertNotNull(b);

		final VoipVideoParams common = a.findCommonProfile(b, false);
		assertNull(common.getProfile());
		assertEquals(VoipVideoParams.MIN_BITRATE, common.getMaxBitrateKbps());
		assertEquals(VoipVideoParams.MIN_FPS, common.getMaxFps());
		assertEquals(VoipVideoParams.MIN_WIDTH, common.getMaxWidth());
		assertEquals(VoipVideoParams.MIN_HEIGHT, common.getMaxHeight());
	}
}

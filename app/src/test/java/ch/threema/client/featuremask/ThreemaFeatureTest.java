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

package ch.threema.client.featuremask;

import ch.threema.client.ThreemaFeature;
import org.junit.Assert;
import org.junit.Test;

public class ThreemaFeatureTest {

	@Test
	public void levelTest() {
		Assert.assertTrue(ThreemaFeature.canText(0x00));
		Assert.assertTrue(ThreemaFeature.canText(0x01));
		Assert.assertTrue(ThreemaFeature.canImage(0x00));
		Assert.assertTrue(ThreemaFeature.canImage(0x01));
		Assert.assertTrue(ThreemaFeature.canVideo(0x00));
		Assert.assertTrue(ThreemaFeature.canVideo(0x01));

		// Test Audio Flag
		Assert.assertFalse(ThreemaFeature.canAudio(0x00));
		Assert.assertFalse(ThreemaFeature.canAudio(0x00 | 0x02 | 0x04));
		Assert.assertTrue(ThreemaFeature.canAudio(0x01));
		Assert.assertTrue(ThreemaFeature.canAudio(0xfa | 0x01));
		Assert.assertTrue(ThreemaFeature.canAudio(1));
		Assert.assertTrue(ThreemaFeature.canAudio(3));

		// Test Group Flag
		Assert.assertFalse(ThreemaFeature.canGroupChat(0x00));
		Assert.assertFalse(ThreemaFeature.canGroupChat(0x00 | 0x01 | 0x04));
		Assert.assertTrue(ThreemaFeature.canGroupChat(0x02));
		Assert.assertTrue(ThreemaFeature.canGroupChat(0xfa | 0x02));
		Assert.assertTrue(ThreemaFeature.canGroupChat(2));
		Assert.assertTrue(ThreemaFeature.canGroupChat(3));

		// Test Ballot Flag
		Assert.assertFalse(ThreemaFeature.canBallot(0x00));
		Assert.assertFalse(ThreemaFeature.canBallot(0x00 | 0x01 | 0x02));
		Assert.assertTrue(ThreemaFeature.canBallot(0x04));
		Assert.assertTrue(ThreemaFeature.canBallot(0xfa | 0x04));
		Assert.assertTrue(ThreemaFeature.canBallot(4));
		Assert.assertTrue(ThreemaFeature.canBallot(5));

		// Test File Flag
		Assert.assertFalse(ThreemaFeature.canFile(0x00));
		Assert.assertFalse(ThreemaFeature.canFile(0x00 | 0x01 | 0x04));
		Assert.assertTrue(ThreemaFeature.canFile(0x08));
		Assert.assertTrue(ThreemaFeature.canFile(0xfa | 0x08));
		Assert.assertTrue(ThreemaFeature.canFile(8));
		Assert.assertTrue(ThreemaFeature.canFile(9));

		// Test Voip Flag
		Assert.assertFalse(ThreemaFeature.canVoip(0x00));
		Assert.assertFalse(ThreemaFeature.canVoip(0x00 | 0x01 | 0x04));
		Assert.assertTrue(ThreemaFeature.canVoip(0x10));
		Assert.assertTrue(ThreemaFeature.canVoip(0xfa | 0x10));
		Assert.assertTrue(ThreemaFeature.canVoip(16));
		Assert.assertTrue(ThreemaFeature.canVoip(17));
	}
}

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

package ch.threema.domain.protocol;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ThreemaFeatureTest {

    @Test
    public void levelTest() {
        Assertions.assertTrue(ThreemaFeature.canText(0x00));
        Assertions.assertTrue(ThreemaFeature.canText(0x01));
        Assertions.assertTrue(ThreemaFeature.canImage(0x00));
        Assertions.assertTrue(ThreemaFeature.canImage(0x01));
        Assertions.assertTrue(ThreemaFeature.canVideo(0x00));
        Assertions.assertTrue(ThreemaFeature.canVideo(0x01));

        // Test Audio Flag
        Assertions.assertFalse(ThreemaFeature.canAudio(0x00));
        Assertions.assertFalse(ThreemaFeature.canAudio(0x00 | 0x02 | 0x04));
        Assertions.assertTrue(ThreemaFeature.canAudio(0x01));
        Assertions.assertTrue(ThreemaFeature.canAudio(0xfa | 0x01));
        Assertions.assertTrue(ThreemaFeature.canAudio(1));
        Assertions.assertTrue(ThreemaFeature.canAudio(3));

        // Test Group Flag
        Assertions.assertFalse(ThreemaFeature.canGroupChat(0x00));
        Assertions.assertFalse(ThreemaFeature.canGroupChat(0x00 | 0x01 | 0x04));
        Assertions.assertTrue(ThreemaFeature.canGroupChat(0x02));
        Assertions.assertTrue(ThreemaFeature.canGroupChat(0xfa | 0x02));
        Assertions.assertTrue(ThreemaFeature.canGroupChat(2));
        Assertions.assertTrue(ThreemaFeature.canGroupChat(3));

        // Test Ballot Flag
        Assertions.assertFalse(ThreemaFeature.canBallot(0x00));
        Assertions.assertFalse(ThreemaFeature.canBallot(0x00 | 0x01 | 0x02));
        Assertions.assertTrue(ThreemaFeature.canBallot(0x04));
        Assertions.assertTrue(ThreemaFeature.canBallot(0xfa | 0x04));
        Assertions.assertTrue(ThreemaFeature.canBallot(4));
        Assertions.assertTrue(ThreemaFeature.canBallot(5));

        // Test File Flag
        Assertions.assertFalse(ThreemaFeature.canFile(0x00));
        Assertions.assertFalse(ThreemaFeature.canFile(0x00 | 0x01 | 0x04));
        Assertions.assertTrue(ThreemaFeature.canFile(0x08));
        Assertions.assertTrue(ThreemaFeature.canFile(0xfa | 0x08));
        Assertions.assertTrue(ThreemaFeature.canFile(8));
        Assertions.assertTrue(ThreemaFeature.canFile(9));

        // Test Voip Flag
        Assertions.assertFalse(ThreemaFeature.canVoip(0x00));
        Assertions.assertFalse(ThreemaFeature.canVoip(0x00 | 0x01 | 0x04));
        Assertions.assertTrue(ThreemaFeature.canVoip(0x10));
        Assertions.assertTrue(ThreemaFeature.canVoip(0xfa | 0x10));
        Assertions.assertTrue(ThreemaFeature.canVoip(16));
        Assertions.assertTrue(ThreemaFeature.canVoip(17));

        // Test GroupCalls Flag
        Assertions.assertFalse(ThreemaFeature.canGroupCalls(0x00));
        Assertions.assertFalse(ThreemaFeature.canGroupCalls(0x00 | 0x01 | 0x04 | 0x08 | 0x10 | 0x20 | 0x40));
        Assertions.assertTrue(ThreemaFeature.canGroupCalls(0x80));
        Assertions.assertTrue(ThreemaFeature.canGroupCalls(0xfa | 0x80));
        Assertions.assertTrue(ThreemaFeature.canGroupCalls(128));
        Assertions.assertTrue(ThreemaFeature.canGroupCalls(129));
    }
}

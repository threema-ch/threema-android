/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2024 Threema GmbH
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

package ch.threema.app.utils;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.annotation.NonNull;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupModel;
import java8.util.stream.Collectors;
import java8.util.stream.StreamSupport;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

public class RandomUtilTest {
	@Test
	@SuppressWarnings("ConstantConditions")
	public void generateRandomPadding() {
		// Generate paddings
		int iterations = 40000;
		int min = 2;
		int max = 5;
		final List<byte[]> paddings = new ArrayList<>(iterations);
		for (int i = 0; i < iterations; i++) {
			paddings.add(RandomUtil.generateRandomPadding(min, max));
		}

		// Create count histogram
		final Map<Integer, Integer> counts = new HashMap<>(max -  min + 1);
		for (byte[] padding : paddings) {
			final Integer k = padding.length;
			counts.put(k, counts.getOrDefault(k, 0) + 1);
		}

		// Ensure that the groups deviate less than 5% from a uniform distribution
		int targetSize = iterations / (max - min + 1);
		int lowerBound = (int)(targetSize * 0.95);
		int upperBound = (int)(targetSize * 1.05);
		for (int i = min; i <= max; i++) {
			int size = counts.getOrDefault(i, 0);
			assertTrue(
				"There are " + size + " paddings with size " + i + ", more than 5% below the uniform distribution (" + targetSize + ")",
				size > lowerBound
			);
			assertTrue(
				"There are " + size + " paddings with size " + i + ", more than 5% above the uniform distribution (" + targetSize + ")",
				size < upperBound
			);
		}
	}

	@Test
	public void generateRandomU32() {
		for (int i = 0; i < 10000; i++) {
			assertTrue(RandomUtil.generateRandomU32() >= 0);
			assertTrue(RandomUtil.generateRandomU32() < (1L<<32));
		}
	}

	@Test
	public void generateRandomString() {
		for (int i = 1; i < 20; i++) {
			final String s = RandomUtil.generateInsecureRandomAsciiString(i);

			// Ensure correct length
			assertEquals(i, s.length());

			// Ensure that not all characters are identical
			if (i >= 3) {
				Set<Character> chars = new HashSet<>();
				for (char c : s.toCharArray()) {
					chars.add(c);
				}
				assertTrue(chars.size() > 1);
			}
		}
	}
}

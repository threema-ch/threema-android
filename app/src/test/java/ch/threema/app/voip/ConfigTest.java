/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2021 Threema GmbH
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

package ch.threema.app.voip;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConfigTest {
	@Test
	public void testAllowHardwareVideoCodec() {
		// Allow if exclusion list is empty
		assertTrue(Config.allowHardwareVideoCodec(new String[] { }, "Samsung;SM-A320FL;8.0.0"));

		// Deny if exclusion list contains model
		assertFalse(Config.allowHardwareVideoCodec(new String[] { "Samsung;SM-A320FL;8" }, "Samsung;SM-A320FL;8.0.0"));

		// Compare android major version
		assertTrue(Config.allowHardwareVideoCodec(new String[] { "Samsung;SM-A320FL;8" }, "Samsung;SM-A320FL;9.0.0"));
		assertFalse(Config.allowHardwareVideoCodec(new String[] { "Samsung;SM-A320FL;8" }, "Samsung;SM-A320FL;8.1.2"));

		// Compare model name
		assertTrue(Config.allowHardwareVideoCodec(new String[] { "Samsung;SM-A320FL;8" }, "Samsung;XX-A320FL;8.0.0"));

		// Compare manufacturer
		assertTrue(Config.allowHardwareVideoCodec(new String[] { "Fairphone;SM-A320FL;8" }, "Samsung;SM-A320FL;8.0.0"));

		// Comparison is case insensitive
		assertFalse(Config.allowHardwareVideoCodec(new String[] { "Samsung;SM-A320FL;8" }, "samsung;sm-A320FL;8.0.0"));

		// Compare with every entry in the list
		assertFalse(Config.allowHardwareVideoCodec(new String[] { "Samsung;SM-A320FL;8", "Fairphone;FP2;8" }, "Fairphone;FP2;8.0.1"));
	}
}

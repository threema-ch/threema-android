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

package ch.threema.app.utils;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

public class FileUtilTest {
	@Test
	public void testDeleteFileOrWarn() throws IOException {
		// Create temporary file
		final File tempFile = Files.createTempFile("FileUtilTest", "tmp").toFile();
		Assert.assertTrue("Temporary file was not created", tempFile.exists());

		// Mock logger
		final Logger logger = Mockito.mock(Logger.class);

		// Remove it
		FileUtil.deleteFileOrWarn(tempFile, "testfile", logger);
		Assert.assertFalse("Temporary file was not deleted", tempFile.exists());
		Mockito.verify(logger, never()).warn(anyString(), anyString());

		// Deleting fails the second time
		FileUtil.deleteFileOrWarn(tempFile, "testfile", logger);
		Mockito.verify(logger, times(1)).warn("Could not delete {}", "testfile");
	}
}

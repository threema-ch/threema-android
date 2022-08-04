/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2022 Threema GmbH
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

package ch.threema.logging.backend;

import android.Manifest;
import android.util.Log;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;
import ch.threema.app.BuildConfig;
import ch.threema.app.DangerousTest;

/**
 * Debug log file test
 */
@RunWith(AndroidJUnit4.class)
@DangerousTest // Deletes logfile
public class DebugLogFileBackendTest {

	@Rule
	public GrantPermissionRule permissionRule = GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE);

	@Before
	public void disableLogfile() {
		DebugLogFileBackend.setEnabled(false);
	}

	/**
	 * Make sure that logging into the debug log file actually creates the debug log file.
	 * Also test that the file is only created when enabled.
	 */
	@Test
	public void testEnable() throws Exception {
		final File logFilePath = DebugLogFileBackend.getLogFilePath();

		// Log with the debug log file disabled
		final DebugLogFileBackend backend = new DebugLogFileBackend(Log.INFO);
		backend.print(Log.WARN, BuildConfig.LOG_TAG, null, "hi");

		// Enabling the debug log file won't create the log file just yet
		Assert.assertFalse(logFilePath.exists());
		DebugLogFileBackend.setEnabled(true);
		Assert.assertFalse(logFilePath.exists());

		// Logs below the min log level are filtered
		backend.printAsync(Log.DEBUG, BuildConfig.LOG_TAG, null, "hey").get(500, TimeUnit.MILLISECONDS);
		Assert.assertFalse(logFilePath.exists());

		// Log with the debug log file enabled
		backend.printAsync(Log.WARN, BuildConfig.LOG_TAG, null, "hi").get(500, TimeUnit.MILLISECONDS);
		Assert.assertTrue(logFilePath.exists());
	}

	/**
	 * Make sure that enabling the debug log file actually creates the debug log file.
	 */
	@Test
	public void testDisableRemovesFile() throws IOException {
		final File logFilePath = DebugLogFileBackend.getLogFilePath();
		Assert.assertFalse(logFilePath.exists());
		Assert.assertTrue("Could not create logfile", logFilePath.createNewFile());
		Assert.assertTrue(logFilePath.exists());
		DebugLogFileBackend.setEnabled(false);
		Assert.assertFalse(logFilePath.exists());
	}

}

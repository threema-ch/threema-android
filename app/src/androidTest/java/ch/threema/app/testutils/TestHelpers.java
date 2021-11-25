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

package ch.threema.app.testutils;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import androidx.annotation.NonNull;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.UserService;
import ch.threema.base.utils.Utils;

import static org.junit.Assert.assertNotNull;

public class TestHelpers {
	private static final String TAG = "TestHelpers";

	/**
	 * Open the notification area and wait for the notifications to become visible.
	 *
	 * @param device UiDevice instance
	 */
	public static void openNotificationArea(@NonNull UiDevice device) throws AssertionError {
		device.openNotification();

		// Wait for notifications to appear
		final BySelector selector = By.res("android:id/status_bar_latest_event_content");
		assertNotNull(
			"Notification bar latest event content not found",
			device.wait(Until.findObject(selector), 1000)
		);
	}

	/**
	 * Source: https://stackoverflow.com/a/5921190/284318
	 */
	public static boolean iServiceRunning(@NonNull Context appContext, @NonNull Class<?> serviceClass) {
		ActivityManager manager = (ActivityManager) appContext.getSystemService(Context.ACTIVITY_SERVICE);
		assert manager != null;
		for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
			if (serviceClass.getName().equals(service.service.getClassName())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Ensure that an identity is set up.
	 */
	public static String ensureIdentity(@NonNull ServiceManager serviceManager) throws Exception {
		// Check whether identity already exists
		final UserService userService = serviceManager.getUserService();
		if (userService.hasIdentity()) {
            final String identity = userService.getIdentity();
            Log.i(TAG, "Identity already exists: " + identity);
            return identity;
		}

		// Otherwise, create identity
		final String identity = "XERCUKNS";
		final byte[] publicKey = Utils.hexStringToByteArray("2bbc16092ff45ffcd0045c00f2f5e1e9597621f89360bbca23a2a2956b3c3b36");
		final byte[] privateKey = Utils.hexStringToByteArray("977aba4ab367041f6137afef69ab9676d445011ca7aca0455a5c64805b80b77a");
		userService.restoreIdentity(identity, privateKey, publicKey);
		Log.i(TAG, "Test identity restored: " + identity);
		return identity;
	}

	public static void clearLogcat() {
		try {
			Runtime.getRuntime().exec(new String[] { "logcat", "-c" });
		} catch (IOException e) {
			Log.e(TAG, "Could not clear logcat", e);
		}
	}

	/**
	 * Return adb logs since the start of the specified test.
	 *
	 * Based on https://www.braze.com/resources/articles/logcat-junit-android-tests
	 */
	public static String getTestLogs(@NonNull String testName) {
		final StringBuilder logLines = new StringBuilder();

		// Process id is used to filter messages
		final String currentProcessId = Integer.toString(android.os.Process.myPid());

		// A snippet of text that uniquely determines where the relevant logs start in the logcat
		final String testStartMessage = "TestRunner: started: " + testName;

		// When true, write every line from the logcat buffer to the string builder
		boolean recording = false;

		// Logcat command:
		//   -d asks the command to completely dump to our buffer, then return
		//   -v threadtime sets the output log format
		final String[] command = new String[] { "logcat", "-d", "-v", "threadtime" };

		BufferedReader bufferedReader = null;
		try {
			final Process process = Runtime.getRuntime().exec(command);
			bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line;
			while ((line = bufferedReader.readLine()) != null) {
				if (line.contains(testStartMessage)) {
					recording = true;
				}
				if (recording) {
					logLines.append(line);
					logLines.append('\n');
				}
			}
		} catch (IOException e) {
			Log.e(TAG, "Failed to run logcat command", e);
		} finally {
			if (bufferedReader != null) {
				try {
					bufferedReader.close();
				} catch (IOException e) {
					Log.e(TAG, "Failed to close buffered reader", e);
				}
			}
		}

		return logLines.toString();
	}
}

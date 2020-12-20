/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020 Threema GmbH
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

package ch.threema.app;

import android.util.Log;

import org.junit.Rule;
import org.junit.rules.RuleChain;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.uiautomator.UiDevice;

/**
 * When a test fails, take a screenshot.
 *
 * Finally, close all open UI elements by pressing back and home buttons.
 */
public class ScreenshotTakingRule extends TestWatcher {
	private final static String TAG = "ScreenshotTakingRule";

	private ScreenshotTakingRule() { /* Use getRuleChain instead */ }

	public static RuleChain getRuleChain() {
		return RuleChain
			.outerRule(GrantPermissionRule.grant(
				"android.permission.READ_EXTERNAL_STORAGE",
				"android.permission.WRITE_EXTERNAL_STORAGE"
			))
			.around(new ScreenshotTakingRule());
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	@Override
	protected void failed(Throwable e, Description description) {
		final UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
		if (device == null) {
			Log.e(TAG, "failed: Device is null");
			return;
		}

		// Create screenshot directory
		final File baseDir = new File("/sdcard/testfailures/screenshots/" + description.getTestClass().getSimpleName() + "/");
		if (!baseDir.exists() && !baseDir.mkdirs()) {
			Log.e(TAG, "failed: Could not create screenshot directory");
			return;
		}
		final String basePath = baseDir.getPath() + "/" + description.getMethodName();

		// Dump UI state
		try {
			try (OutputStream stream = new BufferedOutputStream(new FileOutputStream(new File(basePath + ".uix")))) {
				// Note: Explicitly opening and closing stream since the UiAutomator dumpWindowHierarchy(File)
				// method leaks a file descriptor.
				device.dumpWindowHierarchy(stream);
			}
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		device.takeScreenshot(new File(basePath + ".png"));
	}

	/**
	 * Close any open UI elements.
	 *
	 * This runs after {@link #failed(Throwable, Description)}.
	 */
	@Override
	protected void finished(Description description) {
		final UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
		if (device == null) {
			Log.e(TAG, "finished: Device is null");
			return;
		}

		// Close all UI elements
		device.pressBack();
		device.pressHome();
	}
}

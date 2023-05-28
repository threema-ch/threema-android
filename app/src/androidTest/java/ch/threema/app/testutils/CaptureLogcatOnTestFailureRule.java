/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2023 Threema GmbH
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

import org.junit.AssumptionViolatedException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Capture adb logcat on test failure.
 *
 * Based on https://www.braze.com/resources/articles/logcat-junit-android-tests
 */
public class CaptureLogcatOnTestFailureRule implements TestRule {
	private static final String LOGCAT_HEADER = "\n================ Logcat Output ================\n";
	private static final String STACKTRACE_HEADER = "\n================ Stacktrace ================\n";
	private static final String ORIGINAL_CLASS_HEADER = "\nOriginal class: ";

	@Override
	public Statement apply(Statement base, Description description) {
		return new Statement() {
			@Override
			public void evaluate() throws Throwable {
				// Before test, clear logcat
				TestHelpers.clearLogcat();

				try {
					// Run statement
					base.evaluate();
				} catch (Throwable originalThrowable) {
					if (originalThrowable instanceof AssumptionViolatedException) {
						throw originalThrowable;
					}

					// Fetch logcat logs
					final String testName = description.getMethodName() + "(" + description.getClassName() + ")";
					final String logcatLogs = TestHelpers.getTestLogs(testName);

					// Throw updated throwable
					final String thrownMessage = originalThrowable.getMessage()
						+ ORIGINAL_CLASS_HEADER + originalThrowable.getClass().getName()
						+ LOGCAT_HEADER + logcatLogs
						+ STACKTRACE_HEADER;
					final Throwable modifiedThrowable = new Throwable(thrownMessage);
					modifiedThrowable.setStackTrace(originalThrowable.getStackTrace());
					throw modifiedThrowable;
				}
			}
		};
	}
}

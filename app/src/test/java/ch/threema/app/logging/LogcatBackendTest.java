/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2024 Threema GmbH
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

package ch.threema.app.logging;

import android.util.Log;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import ch.threema.app.BuildConfig;
import ch.threema.logging.LogLevel;
import ch.threema.logging.backend.LogcatBackend;

import static org.mockito.Mockito.times;

/**
 * Logcat backend test.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({Log.class})
public class LogcatBackendTest {
	private final ArgumentCaptor<String> tagCaptor = ArgumentCaptor.forClass(String.class);
	private final ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
	private final ArgumentCaptor<Integer> levelCaptor = ArgumentCaptor.forClass(Integer.class);

	private void assertLogArguments(@LogLevel int level, String tag, String msg) {
		Assert.assertEquals(Integer.valueOf(level), levelCaptor.getValue());
		Assert.assertEquals(tag, tagCaptor.getValue());
		Assert.assertEquals(msg, msgCaptor.getValue());
	}

	/**
	 * Make sure that enabling the debug log file actually creates the debug log file.
	 * Also test that the file is only created when enabled.
	 */
	@Test
	public void testTagCleaning() {
		PowerMockito.mockStatic(Log.class);
		final LogcatBackend backend = new LogcatBackend(Log.INFO);

		backend.print(Log.WARN, "ch.threema.app.Hello", null, "hello");
		PowerMockito.verifyStatic(Log.class, times(1));
		Log.println(levelCaptor.capture(), tagCaptor.capture(), msgCaptor.capture());
		this.assertLogArguments(Log.WARN, BuildConfig.LOG_TAG, "Hello: hello");

		backend.print(Log.INFO, "ch.threema.domain.Bye", null, "goodbye");
		PowerMockito.verifyStatic(Log.class, times(2));
		Log.println(levelCaptor.capture(), tagCaptor.capture(), msgCaptor.capture());
		this.assertLogArguments(Log.INFO, BuildConfig.LOG_TAG, "Bye: goodbye");

		backend.print(Log.INFO, "ch.threema.app.subpackage.Abcd", null, "msg");
		PowerMockito.verifyStatic(Log.class, times(3));
		Log.println(levelCaptor.capture(), tagCaptor.capture(), msgCaptor.capture());
		this.assertLogArguments(Log.INFO, BuildConfig.LOG_TAG, "subpackage.Abcd: msg");

		backend.print(Log.ERROR, "any.other.package", null, "hmmmm");
		PowerMockito.verifyStatic(Log.class, times(4));
		Log.println(levelCaptor.capture(), tagCaptor.capture(), msgCaptor.capture());
		this.assertLogArguments(Log.ERROR, BuildConfig.LOG_TAG, "any.other.package: hmmmm");
	}
}

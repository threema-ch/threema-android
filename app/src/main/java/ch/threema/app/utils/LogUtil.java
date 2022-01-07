/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2022 Threema GmbH
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentActivity;
import ch.threema.app.R;
import ch.threema.app.dialogs.SimpleStringAlertDialog;

public class LogUtil {
	private static final Logger logger = LoggerFactory.getLogger(LogUtil.class);

	private LogUtil() { }

	/**
	 * Log an exception. Additionally, show an error message to the user.
	 */
	public static void exception(Throwable e, FragmentActivity showInActivity) {
		exception(e, (AppCompatActivity) showInActivity);
	}

	/**
	 * Log an exception. Additionally, show an error message to the user.
	 */
	public static void exception(Throwable e, AppCompatActivity showInActivity) {
		String message;
		if(showInActivity != null) {
			if (e != null && !TestUtil.empty(e.getMessage())) {
				message = showInActivity.getString(R.string.an_error_occurred_more, e.getMessage());
			} else {
				message = showInActivity.getString(R.string.an_error_occurred);
			}
		}
		else {
			message = e.getMessage();
		}
		logger.error("Exception", e);
		RuntimeUtil.runOnUiThread(() -> {
			if (showInActivity != null && !showInActivity.isFinishing()) {
				SimpleStringAlertDialog.newInstance(R.string.whoaaa, message)
						.show(showInActivity.getSupportFragmentManager(), "tex");
			}
		});
	}

	/**
	 * Log an error. Additionally, show an error message to the user.
	 */
	public static void error(final String s, final AppCompatActivity showInActivity) {
		logger.error(s);
		RuntimeUtil.runOnUiThread(() -> {
			if (showInActivity != null && !showInActivity.isFinishing()) {
				SimpleStringAlertDialog.newInstance(R.string.whoaaa, s)
					.show(showInActivity.getSupportFragmentManager(), "ter");
			}
		});
	}

	/**
	 * A /dev/null like logger.
	 */
	public static class NullLogger implements Logger {
		@Override
		public String getName() {
			return "<void>";
		}

		@Override
		public boolean isTraceEnabled() {
			return false;
		}

		@Override
		public void trace(String msg) {}

		@Override
		public void trace(String format, Object arg) {}

		@Override
		public void trace(String format, Object arg1, Object arg2) {}

		@Override
		public void trace(String format, Object... arguments) {}

		@Override
		public void trace(String msg, Throwable t) {}

		@Override
		public boolean isTraceEnabled(Marker marker) {
			return false;
		}

		@Override
		public void trace(Marker marker, String msg) {}

		@Override
		public void trace(Marker marker, String format, Object arg) {}

		@Override
		public void trace(Marker marker, String format, Object arg1, Object arg2) {}

		@Override
		public void trace(Marker marker, String format, Object... argArray) {}

		@Override
		public void trace(Marker marker, String msg, Throwable t) {}

		@Override
		public boolean isDebugEnabled() {
			return false;
		}

		@Override
		public void debug(String msg) {}

		@Override
		public void debug(String format, Object arg) {}

		@Override
		public void debug(String format, Object arg1, Object arg2) {}

		@Override
		public void debug(String format, Object... arguments) {}

		@Override
		public void debug(String msg, Throwable t) {}

		@Override
		public boolean isDebugEnabled(Marker marker) {
			return false;
		}

		@Override
		public void debug(Marker marker, String msg) {}

		@Override
		public void debug(Marker marker, String format, Object arg) {}

		@Override
		public void debug(Marker marker, String format, Object arg1, Object arg2) {}

		@Override
		public void debug(Marker marker, String format, Object... arguments) {}

		@Override
		public void debug(Marker marker, String msg, Throwable t) {}

		@Override
		public boolean isInfoEnabled() {
			return false;
		}

		@Override
		public void info(String msg) {}

		@Override
		public void info(String format, Object arg) {}

		@Override
		public void info(String format, Object arg1, Object arg2) {}

		@Override
		public void info(String format, Object... arguments) {}

		@Override
		public void info(String msg, Throwable t) {}

		@Override
		public boolean isInfoEnabled(Marker marker) {
			return false;
		}

		@Override
		public void info(Marker marker, String msg) {}

		@Override
		public void info(Marker marker, String format, Object arg) {}

		@Override
		public void info(Marker marker, String format, Object arg1, Object arg2) {}

		@Override
		public void info(Marker marker, String format, Object... arguments) {}

		@Override
		public void info(Marker marker, String msg, Throwable t) {}

		@Override
		public boolean isWarnEnabled() {
			return false;
		}

		@Override
		public void warn(String msg) {}

		@Override
		public void warn(String format, Object arg) {}

		@Override
		public void warn(String format, Object... arguments) {}

		@Override
		public void warn(String format, Object arg1, Object arg2) {}

		@Override
		public void warn(String msg, Throwable t) {}

		@Override
		public boolean isWarnEnabled(Marker marker) {
			return false;
		}

		@Override
		public void warn(Marker marker, String msg) {}

		@Override
		public void warn(Marker marker, String format, Object arg) {}

		@Override
		public void warn(Marker marker, String format, Object arg1, Object arg2) {}

		@Override
		public void warn(Marker marker, String format, Object... arguments) {}

		@Override
		public void warn(Marker marker, String msg, Throwable t) {}

		@Override
		public boolean isErrorEnabled() {
			return false;
		}

		@Override
		public void error(String msg) {}

		@Override
		public void error(String format, Object arg) {}

		@Override
		public void error(String format, Object arg1, Object arg2) {}

		@Override
		public void error(String format, Object... arguments) {}

		@Override
		public void error(String msg, Throwable t) {}

		@Override
		public boolean isErrorEnabled(Marker marker) {
			return false;
		}

		@Override
		public void error(Marker marker, String msg) {}

		@Override
		public void error(Marker marker, String format, Object arg) {}

		@Override
		public void error(Marker marker, String format, Object arg1, Object arg2) {}

		@Override
		public void error(Marker marker, String format, Object... arguments) {}

		@Override
		public void error(Marker marker, String msg, Throwable t) {}
	}

	public static final Logger NULL_LOGGER = new NullLogger();
}

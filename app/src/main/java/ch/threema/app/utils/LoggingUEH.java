/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2024 Threema GmbH
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

import android.content.Context;

import org.slf4j.Logger;

import ch.threema.base.utils.LoggingUtil;

public class LoggingUEH implements Thread.UncaughtExceptionHandler {
	private static final Logger logger = LoggingUtil.getThreemaLogger("LoggingUEH");

	private final Thread.UncaughtExceptionHandler defaultUEH;
	private Context context;
	private Runnable runOnUncaughtException;

	public LoggingUEH(Context context) {
		this.defaultUEH = Thread.getDefaultUncaughtExceptionHandler();
		this.context = context;
	}

	public void setRunOnUncaughtException(Runnable runOnUncaughtException) {
		this.runOnUncaughtException = runOnUncaughtException;
	}

	@Override
	public void uncaughtException(Thread thread, Throwable ex) {
		logger.error("Uncaught exception", ex);

		if (runOnUncaughtException != null)
			runOnUncaughtException.run();

//		restart();

		if (defaultUEH != null) {
			defaultUEH.uncaughtException(thread, ex);
		}

//		System.exit(2);
	}
}

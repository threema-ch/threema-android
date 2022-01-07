/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2022 Threema GmbH
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

package ch.threema.logging;

import android.util.Log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webrtc.Loggable;
import org.webrtc.Logging;

/**
 * An adapter that sends WebRTC native logs to the SLFJ logger.
 */
public class WebRTCLoggable implements Loggable {
	private static final Logger logger = LoggerFactory.getLogger("libwebrtc");

	private static int minLevel = Log.WARN;

	/**
	 * Set the minimal log level that will be forwarded. Default is {@link Log#WARN}.
	 *
	 * Note: For the log level to be actually logged, the log level in
	 * {@link ch.threema.app.utils.WebRTCUtil#initializeAndroidGlobals} must be set accordingly.
	 */
	public static void setMinLevelFilter(int level) {
		minLevel = level;
	}

	@Override
	public void onLogMessage(String msg, Logging.Severity severity, String file) {
		final String fullMsg = file + msg.trim();
		switch (severity) {
			case LS_VERBOSE:
				if (minLevel <= Log.DEBUG) {
					logger.debug(fullMsg);
				}
				break;
			case LS_INFO:
				if (minLevel <= Log.INFO) {
					logger.info(fullMsg);
				}
				break;
			case LS_WARNING:
				if (minLevel <= Log.WARN) {
					logger.warn(fullMsg);
				}
				break;
			case LS_ERROR:
				if (minLevel <= Log.ERROR) {
					logger.error(fullMsg);
				}
				break;
			case LS_NONE:
				// No log
				break;
		}
	}
}

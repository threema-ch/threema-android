/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021 Threema GmbH
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

package ch.threema.app.video.transcoder.audio;

import android.media.MediaFormat;

public class UnsupportedAudioFormatException extends Exception {

	public UnsupportedAudioFormatException(final MediaFormat inputFormat) {
		super(inputFormat.toString());
	}

	public UnsupportedAudioFormatException(final String message) {
		super(message);
	}

	public UnsupportedAudioFormatException(final String msg, final IllegalStateException cause) {
		super(msg, cause);
	}
}

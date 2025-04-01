/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2024 Threema GmbH
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

package ch.threema.app.camera;

class CameraConfig {
    private final static int DEFAULT_IMAGE_SIZE = 2592;
    private final static int DEFAULT_AUDIO_BITRATE = 128000;
    private final static int DEFAULT_VIDEO_BITRATE = 2000000;

    static int getDefaultImageSize() {
        return DEFAULT_IMAGE_SIZE;
    }

    /**
     * Get the default audio bitrate. Note that this value is only used to guess the estimated file
     * size and therefore to limit the duration of the recording. The actual audio bitrate depends
     * on the device.
     */
    static int getDefaultAudioBitrate() {
        return DEFAULT_AUDIO_BITRATE;
    }

    /**
     * Get the default video bitrate. Note that this value is only used to guess the estimated file
     * size and therefore to limit the duration of the recording. The actual video bitrate depend on
     * the device.
     */
    static int getDefaultVideoBitrate() {
        return DEFAULT_VIDEO_BITRATE;
    }
}

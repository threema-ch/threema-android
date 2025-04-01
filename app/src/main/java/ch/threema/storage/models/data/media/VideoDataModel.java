/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

package ch.threema.storage.models.data.media;

import android.util.JsonReader;
import android.util.JsonWriter;

import androidx.annotation.NonNull;

import org.slf4j.Logger;

import java.io.StringReader;
import java.io.StringWriter;

import ch.threema.app.utils.StringConversionUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.base.utils.Utils;

@Deprecated
public class VideoDataModel implements MediaMessageDataInterface {
    private static final Logger logger = LoggingUtil.getThreemaLogger("VideoDataModel");

    private int duration, videoSize;
    private byte[] videoBlobId;
    private byte[] encryptionKey;
    private boolean isDownloaded;

    private VideoDataModel() {
    }

    public VideoDataModel(int duration, int videoSize, byte[] videoBlobId, byte[] encryptedKey) {
        this.duration = duration;
        this.videoBlobId = videoBlobId;
        this.encryptionKey = encryptedKey;
        this.isDownloaded = false;
        this.videoSize = videoSize;
    }

    /**
     * Get Duration of video in SECONDS
     *
     * @return duration
     */
    public int getDuration() {
        return this.duration;
    }

    public int getVideoSize() {
        return this.videoSize;
    }

    @Override
    public byte[] getBlobId() {
        return this.videoBlobId;
    }

    @Override
    public byte[] getEncryptionKey() {
        return this.encryptionKey;
    }

    @Override
    public boolean isDownloaded() {
        return this.isDownloaded;
    }

    @Override
    public void isDownloaded(boolean isDownloaded) {
        this.isDownloaded = isDownloaded;
    }

    @Override
    public byte[] getNonce() {
        return new byte[0];
    }

    public String getDurationString() {
        try {
            int duration = getDuration();
            if (duration > 0) {
                return StringConversionUtil.secondsToString(duration, false);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public void fromString(@NonNull String s) {
        JsonReader r = new JsonReader(new StringReader(s));

        try {
            r.beginArray();
            this.duration = r.nextInt();
            this.isDownloaded = r.nextBoolean();
            this.encryptionKey = Utils.hexStringToByteArray(r.nextString());
            this.videoBlobId = Utils.hexStringToByteArray(r.nextString());
            if (r.hasNext()) {
                this.videoSize = r.nextInt();
            }
        } catch (Exception x) {
            logger.error("Exception", x);
            //DO NOTHING!!
        }

    }

    @Override
    public String toString() {
        StringWriter sw = new StringWriter();
        JsonWriter j = new JsonWriter(sw);

        try {
            j.beginArray();
            j
                .value(this.getDuration())
                .value(this.isDownloaded())
                .value(Utils.byteArrayToHexString(this.getEncryptionKey()))
                .value(Utils.byteArrayToHexString(this.getBlobId()))
                .value(this.getVideoSize());
            j.endArray();
        } catch (Exception x) {
            logger.error("Exception", x);
            return null;
        }

        return sw.toString();

    }

    /**
     * Convert a FileDataModel (containing video data) to a VideoDataModel.
     * <p>
     * This method should only be used for backwards compatibility!
     */
    public static VideoDataModel fromFileData(@NonNull FileDataModel fileDataModel) {
        final int duration = (int) Math.min(fileDataModel.getDurationSeconds(), (long) Integer.MAX_VALUE);
        final int size = (int) Math.min(fileDataModel.getFileSize(), (long) Integer.MAX_VALUE);
        return new VideoDataModel(duration, size, fileDataModel.getBlobId(), fileDataModel.getEncryptionKey());
    }

    public static VideoDataModel create(@NonNull String s) {
        VideoDataModel m = new VideoDataModel();
        m.fromString(s);
        return m;
    }
}

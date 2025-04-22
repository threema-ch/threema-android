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

import org.slf4j.Logger;

import java.io.StringReader;
import java.io.StringWriter;

import androidx.annotation.Nullable;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.base.utils.Utils;

@Deprecated
public class ImageDataModel implements MediaMessageDataInterface {
    private static final Logger logger = LoggingUtil.getThreemaLogger("ImageDataModel");

    private byte[] imageBlobId;
    private byte[] encryptionKey;
    private byte[] nonce;
    private boolean isDownloaded;

    private ImageDataModel() {
    }

    public ImageDataModel(byte[] imageBlobId, byte[] encryptionKey, byte[] nonce) {
        this.imageBlobId = imageBlobId;
        this.encryptionKey = encryptionKey;
        this.nonce = nonce;
        this.isDownloaded = false;
    }

    public ImageDataModel(boolean isDownloaded) {
        this.isDownloaded = isDownloaded;
        this.imageBlobId = new byte[0];
        this.encryptionKey = new byte[0];
    }


    @Override
    public byte[] getBlobId() {
        return this.imageBlobId;
    }

    @Override
    public byte[] getEncryptionKey() {
        return this.encryptionKey;
    }

    public byte[] getNonce() {
        return this.nonce;
    }

    @Override
    public boolean isDownloaded() {
        return this.isDownloaded;
    }

    @Override
    public void isDownloaded(boolean isDownloaded) {
        this.isDownloaded = isDownloaded;

        if (this.isDownloaded) {
            // Clear stuff
            this.nonce = new byte[0];
            this.encryptionKey = new byte[0];
            this.imageBlobId = new byte[0];
        }
    }

    public void fromString(@Nullable String s) {
        if (TestUtil.isEmptyOrNull(s)) {
            // "old" image model, set defaults
            this.isDownloaded = true;
            this.encryptionKey = new byte[0];
            this.imageBlobId = new byte[0];
            this.nonce = new byte[0];
            return;
        }

        JsonReader r = new JsonReader(new StringReader(s));

        try {
            r.beginArray();
            this.isDownloaded = r.nextBoolean();
            this.encryptionKey = Utils.hexStringToByteArray(r.nextString());
            this.imageBlobId = Utils.hexStringToByteArray(r.nextString());
            this.nonce = Utils.hexStringToByteArray(r.nextString());
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
                .value(this.isDownloaded())
                .value(Utils.byteArrayToHexString(this.getEncryptionKey()))
                .value(Utils.byteArrayToHexString(this.getBlobId()))
                .value(Utils.byteArrayToHexString(this.getNonce()));
            j.endArray();
        } catch (Exception x) {
            logger.error("Exception", x);
            return null;
        }

        return sw.toString();

    }

    public static ImageDataModel create(String s) {
        ImageDataModel m = new ImageDataModel();
        m.fromString(s);
        return m;
    }

    /**
     * Do not use this in new code. It only exists to handle places where a [ImageDataModel] needs to be returned and `null` is not allowed.
     */
    @Deprecated()
    public static ImageDataModel createEmpty() {
        return new ImageDataModel();
    }
}

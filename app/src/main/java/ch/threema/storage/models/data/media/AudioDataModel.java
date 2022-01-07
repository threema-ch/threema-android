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

package ch.threema.storage.models.data.media;

import android.util.JsonReader;
import android.util.JsonWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.io.StringWriter;

import ch.threema.base.utils.Utils;

public class AudioDataModel implements MediaMessageDataInterface {
	private static final Logger logger = LoggerFactory.getLogger(AudioDataModel.class);

	private int duration;
	private byte[] audioBlobId;
	private byte[] encryptionKey;
	private boolean isDownloaded;

	private AudioDataModel() {
	}

	public AudioDataModel(int duration, byte[] audioBlobId, byte[] encryptedKey) {
		this.duration = duration;
		this.audioBlobId = audioBlobId;
		this.encryptionKey = encryptedKey;
		this.isDownloaded = false;
	}
	public AudioDataModel(int duration, boolean isDownloaded) {
		this.duration = duration;
		this.isDownloaded = isDownloaded;
		this.audioBlobId = new byte[0];
		this.encryptionKey = new byte[0];
	}

	public int getDuration() {
		return this.duration;
	}

	@Override
	public byte[] getBlobId() {
		return this.audioBlobId;
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

	public void fromString(String s) {
		JsonReader r = new JsonReader(new StringReader(s));

		try {
			r.beginArray();
			this.duration = r.nextInt();
			this.isDownloaded = r.nextBoolean();
			this.encryptionKey = Utils.hexStringToByteArray(r.nextString());
			this.audioBlobId = Utils.hexStringToByteArray(r.nextString());
		}
		catch (Exception x) {
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
					.value(Utils.byteArrayToHexString(this.getBlobId()));
			j.endArray();
		}
		catch (Exception x) {
			logger.error("Exception", x);
			return null;
		}

		return sw.toString();

	}

	public static AudioDataModel create(String s) {
		AudioDataModel m = new AudioDataModel();
		m.fromString(s);
		return m;
	}
}

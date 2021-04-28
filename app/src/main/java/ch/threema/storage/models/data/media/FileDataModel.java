/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2021 Threema GmbH
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

import android.util.JsonWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.utils.JsonUtil;
import ch.threema.app.utils.ListReader;
import ch.threema.app.utils.MimeUtil;
import ch.threema.app.utils.StringConversionUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.client.Utils;
import ch.threema.client.file.FileData;

public class FileDataModel implements MediaMessageDataInterface {
	private static final Logger logger = LoggerFactory.getLogger(FileDataModel.class);

	public static final String METADATA_KEY_DURATION = "d";
	public static final String METADATA_KEY_WIDTH = "w";
	public static final String METADATA_KEY_HEIGHT = "h";

	private byte[] fileBlobId;
	private byte[] encryptionKey;
	private String mimeType;
	private String thumbnailMimeType;
	private long fileSize;
	private @Nullable String fileName;
	private @FileData.RenderingType int renderingType;
	private boolean isDownloaded;
	private String caption;
	private Map<String, Object> metaData;

	// incoming
	public FileDataModel(byte[] fileBlobId,
	                     byte[] encryptionKey,
	                     String mimeType,
	                     String thumbnailMimeType,
	                     long fileSize,
	                     @Nullable String fileName,
	                     @FileData.RenderingType int renderingType,
	                     String caption,
	                     boolean isDownloaded,
	                     Map<String, Object> metaData) {
		this.fileBlobId = fileBlobId;
		this.encryptionKey = encryptionKey;
		this.mimeType = mimeType;
		this.thumbnailMimeType = thumbnailMimeType;
		this.fileSize = fileSize;
		this.fileName = fileName;
		this.renderingType = renderingType;
		this.isDownloaded = isDownloaded;
		this.caption = caption;
		this.metaData = metaData;
	}

	// outgoing
	public FileDataModel(String mimeType,
	                     String thumbnailMimeType,
	                     long fileSize,
	                     @Nullable String fileName,
	                     @FileData.RenderingType int renderingType,
	                     String caption,
	                     boolean isDownloaded,
	                     Map<String, Object> metaData) {
		this.mimeType = mimeType;
		this.thumbnailMimeType = thumbnailMimeType;
		this.fileSize = fileSize;
		this.fileName = fileName;
		this.renderingType = renderingType;
		this.isDownloaded = isDownloaded;
		this.caption = caption;
		this.metaData = metaData;
	}

	private FileDataModel() {
	}

	public void setCaption(String caption) {
		this.caption = caption;
	}

	public void setFileName(@Nullable String fileName) {
		this.fileName = fileName;
	}

	public void setRenderingType(@FileData.RenderingType int renderingType) {
		this.renderingType = renderingType;
	}

	@Override
	public byte[] getBlobId() {
		return this.fileBlobId;
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

	public @NonNull String getMimeType() {
		if (this.mimeType == null) {
			return MimeUtil.MIME_TYPE_DEFAULT;
		}
		return this.mimeType;
	}

	public void setMimeType(String mimeType) {
		this.mimeType = mimeType;
	}

	public @NonNull String getThumbnailMimeType() {
		if (this.thumbnailMimeType == null) {
			return MimeUtil.MIME_TYPE_IMAGE_JPG;
		}
		return this.thumbnailMimeType;
	}

	public void setThumbnailMimeType(String thumbnailMimeType) {
		this.thumbnailMimeType = thumbnailMimeType;
	}

	public void setFileSize(long fileSize) {
		this.fileSize = fileSize;
	}

	public long getFileSize() {
		return this.fileSize;
	}

	public @Nullable String getFileName() {
		return this.fileName;
	}

	public @FileData.RenderingType int getRenderingType() {
		return this.renderingType;
	}

	public String getCaption() {
		return this.caption;
	}

	public Map<String, Object> getMetaData() {
		return this.metaData;
	}

	public void setMetaData(Map<String, Object> metaData) {
		this.metaData = metaData;
	}

	@Nullable
	public Integer getMetaDataInt(String metaDataKey) {
		return this.metaData != null
			&& this.metaData.containsKey(metaDataKey)
			&& this.metaData.get(metaDataKey) instanceof Number ?
			(Integer) this.metaData.get(metaDataKey) : null;
	}

	@Nullable
	public String getMetaDataString(String metaDataKey) {
		return this.metaData != null
			&& this.metaData.containsKey(metaDataKey)
			&& this.metaData.get(metaDataKey) instanceof String ?
			(String) this.metaData.get(metaDataKey) : null;
	}

	@Nullable
	public Boolean getMetaDataBool(String metaDataKey) {
		return this.metaData != null
			&& this.metaData.containsKey(metaDataKey)
			&& this.metaData.get(metaDataKey) instanceof Boolean ?
			(Boolean) this.metaData.get(metaDataKey) : null;
	}

	@Nullable
	public Float getMetaDataFloat(String metaDataKey) {
		if (this.metaData != null && this.metaData.containsKey(metaDataKey)) {

			Object value = this.metaData.get(metaDataKey);
			if (value instanceof Number) {
				if (value instanceof Double) {
					return ((Double) value).floatValue();
				} else if (value instanceof Float) {
					return (Float) value;
				} else if (value instanceof Integer) {
					return ((Integer) value).floatValue();
				} else {
					return 0F;
				}
			}
		}
		return null;
	}

	public String getDurationString() {
		try {
			Float durationF = getMetaDataFloat(METADATA_KEY_DURATION);
			if (durationF != null) {
				long duration = durationF.longValue();
				if (duration > 0) {
					return StringConversionUtil.secondsToString(duration, false);
				}
			}
		} catch (Exception ignored) {}
		return null;
	}

	/**
	 * Return the duration as set in the metadata field.
	 *
	 * Note: Floats are converted to long integers.
	 */
	public long getDuration() {
		try {
			Float durationF = getMetaDataFloat(METADATA_KEY_DURATION);
			if (durationF != null) {
				return durationF.longValue();
			}
		} catch (Exception ignored) {}
		return 0;
	}

	private void fromString(String s) {
		if (TestUtil.empty(s)) {
			return;
		}

		try {
			ListReader reader  = new ListReader(JsonUtil.convertArray(s));
			this.fileBlobId = reader.nextStringAsByteArray();
			this.encryptionKey = reader.nextStringAsByteArray();
			this.mimeType = reader.nextString();
			this.fileSize = reader.nextInteger();
			this.fileName = reader.nextString();
			Integer typeId = reader.nextInteger();
			if (typeId != null) {
				this.renderingType = typeId;
			}
			this.isDownloaded = reader.nextBool();
			this.caption = reader.nextString();
			this.thumbnailMimeType = reader.nextString();
			this.metaData = reader.nextMap();
		} catch (Exception e) {
			// Ignore error, just log
			logger.error("Extract file data model", e);
		}
	}

	@Override
	public String toString() {
		StringWriter sw = new StringWriter();
		JsonWriter j = new JsonWriter(sw);

		try {
			j.beginArray();
			j
					.value(Utils.byteArrayToHexString(this.getBlobId()))
					.value(Utils.byteArrayToHexString(this.getEncryptionKey()))
					.value(this.mimeType)
					.value(this.fileSize)
					.value(this.fileName)
					.value(this.renderingType)
					.value(this.isDownloaded)
					.value(this.caption)
					.value(this.thumbnailMimeType);

			// Always write the meta data object
			JsonWriter metaDataObject = j.beginObject();
			if (this.metaData != null) {
				Iterator<String> keys = this.metaData.keySet().iterator();

				while (keys.hasNext()) {
					String key = keys.next();
					Object value = this.metaData.get(key);

					metaDataObject.name(key);

					try {
						if (value instanceof Integer) {
							metaDataObject.value((Integer) value);
						} else if (value instanceof Float) {
							metaDataObject.value((Float) value);
						} else if (value instanceof Double) {
							metaDataObject.value((Double) value);
						} else if (value instanceof Boolean) {
							metaDataObject.value((Boolean) value);
						} else if (value == null) {
							metaDataObject.nullValue();
						} else {
							metaDataObject.value(value.toString());
						}
					}
					catch (IOException x) {
						logger.error("Failed to write meta data", x);
						// Write a NULL
						metaDataObject.nullValue();
					}
				}
			}
			j.endObject();
			j.endArray();
		}
		catch (Exception x) {
			logger.error("Exception", x);
			return null;
		}

		return sw.toString();
	}

	public static FileDataModel create(@NonNull String s) {
		FileDataModel m = new FileDataModel();
		m.fromString(s);
		return m;
	}
}

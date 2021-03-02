/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema Java Client
 * Copyright (c) 2013-2021 Threema GmbH
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

package ch.threema.client.file;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import androidx.annotation.IntDef;
import ch.threema.client.BadMessageException;
import ch.threema.client.Utils;

public class FileData {
	@Retention(RetentionPolicy.SOURCE)
	@IntDef({RENDERING_DEFAULT, RENDERING_MEDIA, RENDERING_STICKER})
	public @interface RenderingType {}

	public static final int RENDERING_DEFAULT = 0;
	public static final int RENDERING_MEDIA = 1;
	public static final int RENDERING_STICKER = 2;

	private final static String KEY_BLOB_ID = "b";
	private final static String KEY_THUMBNAIL_BLOB_ID = "t";
	private final static String KEY_ENCRYPTION_KEY = "k";
	private final static String KEY_MIME_TYPE = "m";
	private final static String KEY_THUMBNAIL_MIME_TYPE = "p";
	private final static String KEY_FILE_NAME = "n";
	private final static String KEY_FILE_SIZE = "s";
	private final static String KEY_RENDERING_TYPE_DEPRECATED = "i";
	private final static String KEY_RENDERING_TYPE = "j";
	private final static String KEY_DESCRIPTION = "d";
	private final static String KEY_CORRELATION_ID = "c";
	private final static String KEY_META_DATA = "x";

	private byte[] fileBlobId;
	private byte[] thumbnailBlobId;
	private byte[] encryptionKey;
	private String mimeType;
	private String thumbnailMimeType;
	private long fileSize;
	private String fileName;
	private @RenderingType int renderingType;
	private String description;
	private String correlationId;
	private Map<String, Object> metaData;

	public byte[] getFileBlobId() {
		return fileBlobId;
	}

	public FileData setFileBlobId(byte[] fileBlobId) {
		this.fileBlobId = fileBlobId;
		return this;
	}

	public byte[] getThumbnailBlobId() {
		return thumbnailBlobId;
	}

	public FileData setThumbnailBlobId(byte[] thumbnailBlobId) {
		this.thumbnailBlobId = thumbnailBlobId;
		return this;
	}

	public byte[] getEncryptionKey() {
		return encryptionKey;
	}

	public FileData setEncryptionKey(byte[] encryptionKey) {
		this.encryptionKey = encryptionKey;
		return this;
	}

	public String getMimeType() {
		return mimeType;
	}

	public FileData setMimeType(String mimeType) {
		this.mimeType = mimeType;
		return this;
	}

	public String getThumbnailMimeType() {
		return thumbnailMimeType;
	}

	public FileData setThumbnailMimeType(String thumbnailMimeType) {
		this.thumbnailMimeType = thumbnailMimeType;
		return this;
	}

	public long getFileSize() {
		return fileSize;
	}

	public FileData setFileSize(long fileSize) {
		this.fileSize = fileSize;
		return this;
	}

	public String getFileName() {
		return fileName;
	}

	public FileData setFileName(String fileName) {
		this.fileName = fileName;
		return this;
	}

	public @RenderingType int getRenderingType() {
		return this.renderingType;
	}

	public FileData setRenderingType(@RenderingType int renderingType) {
		this.renderingType = renderingType;
		return this;
	}

	public String getDescription() {
		return this.description;
	}

	public FileData setDescription(String description) {
		this.description = description;
		return this;
	}

	public String getCorrelationId() {
		return this.correlationId;
	}

	public FileData setCorrelationId(String correlationId) {
		this.correlationId = correlationId;
		return this;
	}

	public Map<String, Object> getMetaData() {
		return this.metaData;
	}

	public FileData setMetaData(Map<String, Object> metaData) {
		this.metaData = metaData;
		return this;
	}

	public static FileData parse(String jsonObjectString) throws BadMessageException {
		try {
			JSONObject o = new JSONObject(jsonObjectString);

			FileData fileData = new FileData();

			try {
				fileData.fileBlobId = Utils.hexStringToByteArray(o.getString(KEY_BLOB_ID));
			}
			catch (IllegalArgumentException e) {
				throw new BadMessageException("TM038");
			}

			//optional field
			if(o.has(KEY_THUMBNAIL_BLOB_ID)) {
				try {
					fileData.thumbnailBlobId = Utils.hexStringToByteArray(o.getString(KEY_THUMBNAIL_BLOB_ID));
				}
				catch (IllegalArgumentException e) {
					throw new BadMessageException("TM039");
				}
			}

			try {
				fileData.encryptionKey = Utils.hexStringToByteArray(o.getString(KEY_ENCRYPTION_KEY));
			}
			catch (IllegalArgumentException e) {
				throw new BadMessageException("TM040");
			}

			try {
				fileData.mimeType = o.getString(KEY_MIME_TYPE);
			}
			catch (IllegalArgumentException e) {
				throw new BadMessageException("TM041");
			}

			//optional field
			if(o.has(KEY_THUMBNAIL_MIME_TYPE)) {
				fileData.thumbnailMimeType = o.getString(KEY_THUMBNAIL_MIME_TYPE);
			}

			//optional field
			if(o.has(KEY_FILE_NAME)) {
				fileData.fileName = o.getString(KEY_FILE_NAME);
			}

			try {
				fileData.fileSize = o.getInt(KEY_FILE_SIZE);
			}
			catch (IllegalArgumentException e) {
				throw new BadMessageException("TM042");
			}

			if (o.has(KEY_RENDERING_TYPE)) {
				fileData.renderingType = o.getInt(KEY_RENDERING_TYPE);
				if (fileData.renderingType > RENDERING_STICKER) {
					fileData.renderingType = RENDERING_DEFAULT;
				}
			} else {
				try {
					fileData.renderingType = o.getInt(KEY_RENDERING_TYPE_DEPRECATED);
				} catch (IllegalArgumentException | JSONException e) {
					fileData.renderingType = RENDERING_DEFAULT;
				}
			}

			//optional field
			if(o.has(KEY_DESCRIPTION)) {
				try {
					fileData.description = o.getString(KEY_DESCRIPTION);
				}
				catch (IllegalArgumentException e) {
					//ignore, optional field
				}
			}

			if (o.has(KEY_CORRELATION_ID)) {
				try {
					fileData.correlationId = o.getString(KEY_CORRELATION_ID);
				}
				catch (IllegalArgumentException e) {
					//ignore, optional field
				}
			}

			if (o.has(KEY_META_DATA)) {
				try {
					final JSONObject metaData = o.getJSONObject(KEY_META_DATA);

					Iterator<String> keys = metaData.keys();
					fileData.metaData = new HashMap<>();
					while(keys.hasNext()) {
						String key = keys.next();
						fileData.metaData.put(key, metaData.get(key));
					}
				}
				catch (IllegalArgumentException e) {
					//ignore, optional field
				}
			}
			return fileData;
		} catch (JSONException e) {
			throw new BadMessageException("TM037");
		}
	}

	public void write(ByteArrayOutputStream bos) throws Exception {
		bos.write(this.generateString().getBytes(StandardCharsets.UTF_8));
	}

	protected String generateString() throws BadMessageException {
		JSONObject o = new JSONObject();
		try {
			o.put(KEY_BLOB_ID, Utils.byteArrayToHexString(this.fileBlobId));
			o.put(KEY_THUMBNAIL_BLOB_ID, Utils.byteArrayToHexString(this.thumbnailBlobId));
			o.put(KEY_ENCRYPTION_KEY, Utils.byteArrayToHexString(this.encryptionKey));
			o.put(KEY_MIME_TYPE, this.mimeType);
			o.put(KEY_THUMBNAIL_MIME_TYPE, this.thumbnailMimeType);
			o.put(KEY_FILE_NAME, this.fileName);
			o.put(KEY_FILE_SIZE, this.fileSize);
			o.put(KEY_RENDERING_TYPE_DEPRECATED, this.renderingType == RENDERING_MEDIA ? RENDERING_MEDIA : RENDERING_DEFAULT);
			if (this.description != null) {
				o.put(KEY_DESCRIPTION, this.description);
			}

			if (this.correlationId != null) {
				o.put(KEY_CORRELATION_ID, this.correlationId);
			}

			if (this.metaData != null) {
				JSONObject metaDataJsonObject = new JSONObject();

				for (Map.Entry<String, Object> metaValue : this.metaData.entrySet()) {
					metaDataJsonObject.put(metaValue.getKey(), metaValue.getValue());
				}
				o.put(KEY_META_DATA, metaDataJsonObject);
			}
			o.put(KEY_RENDERING_TYPE, this.renderingType);
		}
		catch (Exception e) {
			throw new BadMessageException("TM037");
		}

		return o.toString();
	}
}

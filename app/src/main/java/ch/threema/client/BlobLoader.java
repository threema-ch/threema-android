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

package ch.threema.client;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Helper class that loads blobs (images, videos etc.) from the blob server given a blob ID. No
 * processing is done on the loaded data; any decryption etc. must be done separately.
 */
public class BlobLoader {

	private static final Logger logger = LoggerFactory.getLogger(BlobLoader.class);

	private static final int BUFFER_SIZE = 8192;

	private final @NonNull SSLSocketFactoryFactory factory;
	private final byte[] blobId;
	private ProgressListener progressListener;
	private volatile boolean cancel;
	private Version version;
	private String blobUrlPattern; /* "https://%s.blob.threema.ch/%s" */
	private String blobDoneUrlPattern; /* "https://%s.blob.threema.ch/%s/done" */

	public BlobLoader(@NonNull SSLSocketFactoryFactory factory, byte[] blobId) {
		this(factory, blobId, null);
	}

	public BlobLoader(@NonNull SSLSocketFactoryFactory factory, byte[] blobId, ProgressListener progressListener) {
		this(factory, blobId, false, progressListener);
	}

	public BlobLoader(@NonNull SSLSocketFactoryFactory factory, byte[] blobId, boolean ipv6, ProgressListener progressListener) {
		this.factory = factory;
		this.blobId = blobId;
		this.progressListener = progressListener;
		this.version = new Version();
		this.setServerUrls(ipv6);
	}

	/**
	 * Attempt to load the given blob.
	 *
	 * @param markAsDone if true, the server is informed of successful download and will delete the
	 * blob. Do not use for group messages.
	 * @return blob data or null if download was cancelled
	 * @throws IOException
	 */
	public @Nullable byte[] load(boolean markAsDone) throws IOException {

		cancel = false;

		InputStreamLength isl = getInputStream();

		int read;
		byte[] blob;
		byte[] buffer = new byte[BUFFER_SIZE];
		ByteArrayOutputStream bos = new ByteArrayOutputStream();

		/* Content length known? */
		if (isl.length != -1) {
			logger.debug("Blob content length is {}", isl.length);

			int offset = 0;
			while ((read = isl.inputStream.read(buffer)) != -1 && !cancel) {
				offset += read;

				try {
					bos.write(buffer, 0, read);
				} catch (OutOfMemoryError e) {
					throw new IOException("Out of memory on write");
				}

				if (progressListener != null) {
					progressListener.updateProgress((int) ((float) 100 * offset / isl.length));
				}
			}

			if (cancel) {
				logger.info("Blob load cancelled");
				if (progressListener != null) {
					progressListener.onFinished(false);
				}
				return null;
			}

			if (offset != isl.length) {
				if (progressListener != null) {
					progressListener.onFinished(false);
				}
				throw new IOException("Unexpected read size. current: " + offset + ", excepted: " + isl.length);
			}

			blob = bos.toByteArray();
		} else {
			/* Content length is unknown - need to read until EOF */
			logger.debug("Blob content length is unknown");

			while ((read = isl.inputStream.read(buffer)) != -1 && !cancel) {
				bos.write(buffer, 0, read);
			}

			if (cancel) {
				logger.info("Blob load cancelled");
				if (progressListener != null) {
					progressListener.onFinished(false);
				}
				return null;
			}

			blob = bos.toByteArray();
		}

		logger.info("Blob load complete ({} bytes received)", blob.length);

		if (progressListener != null) {
			progressListener.onFinished(true);
		}

		if (markAsDone) {
			if (blob.length > 0) {
				this.markAsDown(blobId);
			}
		}

		return blob;
	}

	public InputStreamLength getInputStream() throws IOException {

		String blobIdHex = Utils.byteArrayToHexString(blobId);
		String blobIdPrefix = blobIdHex.substring(0, 2);
		URL blobUrl = new URL(String.format(blobUrlPattern, blobIdPrefix, blobIdHex));

		logger.info("Loading blob from {}", blobUrl.getHost());
		HttpsURLConnection connection = (HttpsURLConnection)blobUrl.openConnection();
		connection.setSSLSocketFactory(this.factory.makeFactory(blobUrl.getHost()));
		connection.setConnectTimeout(ProtocolDefines.BLOB_CONNECT_TIMEOUT * 1000);
		connection.setReadTimeout(ProtocolDefines.BLOB_LOAD_TIMEOUT * 1000);
		connection.setRequestProperty("User-Agent", ProtocolStrings.USER_AGENT + "/" + version.getVersion());
		connection.setDoOutput(false);

		BufferedInputStream inputStream = new BufferedInputStream(connection.getInputStream());
		int contentLength = connection.getContentLength();
		return new InputStreamLength(inputStream, contentLength);
	}

	public boolean markAsDown(byte[] blobId) {
		String blobIdHex = Utils.byteArrayToHexString(blobId);
		String blobIdPrefix = blobIdHex.substring(0, 2);

		try {
			URL blobDoneUrl = new URL(String.format(blobDoneUrlPattern, blobIdPrefix, blobIdHex));
			HttpsURLConnection doneConnection = (HttpsURLConnection) blobDoneUrl.openConnection();
			doneConnection.setSSLSocketFactory(this.factory.makeFactory(blobDoneUrl.getHost()));
			doneConnection.setRequestProperty("User-Agent", ProtocolStrings.USER_AGENT + "/" + version.getVersion());
			doneConnection.setDoOutput(false);
			doneConnection.setDoInput(true);
			doneConnection.setRequestMethod("POST");
			IOUtils.toByteArray(doneConnection.getInputStream());

			return true;

		} catch (IOException e) {
			logger.warn("Marking blob as done failed", e);
		}

		return false;
	}

	/**
	 * Cancel a download in progress. load() will return null.
	 */
	public void cancel() {
		cancel = true;
	}

	public void setProgressListener(ProgressListener progressListener) {
		this.progressListener = progressListener;
	}

	public void setVersion(Version version) {
		this.version = version;
	}

	public void setServerUrls(boolean ipv6) {
		blobUrlPattern = ipv6 ? ProtocolStrings.BLOB_URL_PATTERN_IPV6 : ProtocolStrings.BLOB_URL_PATTERN;
		blobDoneUrlPattern = ipv6 ? ProtocolStrings.BLOB_DONE_PATTERN_IPV6 : ProtocolStrings.BLOB_DONE_PATTERN;
	}

	public class InputStreamLength {
		public final BufferedInputStream inputStream;
		public final int length;

		public InputStreamLength(BufferedInputStream inputStream, int length) {
			this.inputStream = inputStream;
			this.length = length;
		}
	}
}

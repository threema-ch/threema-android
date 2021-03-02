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

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ProtocolException;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

import androidx.annotation.NonNull;
import ch.threema.base.ThreemaException;

/**
 * Helper class that uploads a blob (image, video) to the blob server and returns the assigned blob
 * ID. No processing is done on the data; any encryption must happen separately.
 */
public class BlobUploader {

	private static final Logger logger = LoggerFactory.getLogger(BlobUploader.class);

	private static final int CHUNK_SIZE = 16384;

	private final @NonNull SSLSocketFactoryFactory factory;
	private final InputStream blobInputStream;
	private final int blobLength;
	private ProgressListener progressListener;
	private volatile boolean cancel;
	private Version version;
	private String blobUploadUrlHttps /* "https://upload.blob.threema.ch/upload" */;

	public BlobUploader(@NonNull SSLSocketFactoryFactory factory, byte[] blobData) {
		this(factory, blobData, null);
	}

	public BlobUploader(@NonNull SSLSocketFactoryFactory factory, byte[] blobData, ProgressListener progressListener) {
		this(factory, new ByteArrayInputStream(blobData), blobData.length, progressListener);
	}

	public BlobUploader(@NonNull SSLSocketFactoryFactory factory, InputStream blobInputStream, int blobLength) {
		this(factory, blobInputStream, blobLength, null);
	}

	public BlobUploader(@NonNull SSLSocketFactoryFactory factory, InputStream blobInputStream, int blobLength, ProgressListener progressListener) {
		this(factory, blobInputStream, blobLength, false, progressListener);
	}

	public BlobUploader(
		@NonNull SSLSocketFactoryFactory factory,
		InputStream blobInputStream,
		int blobLength,
		boolean ipv6,
		ProgressListener progressListener
	) {
		this.factory = factory;
		this.blobInputStream = blobInputStream;
		this.blobLength = blobLength;
		this.progressListener = progressListener;
		this.version = new Version();
		this.setServerUrls(ipv6);
	}

	/**
	 * Upload the given blob and return the blob ID on success.
	 *
	 * @return blob ID
	 * @throws IOException if a network error occurs
	 * @throws ThreemaException if the server response is invalid
	 */
	public byte[] upload() throws IOException, ThreemaException {
		cancel = false;

		URL url = new URL(blobUploadUrlHttps);
		String boundary = "---------------------------Boundary_Line";

		logger.info("Uploading blob ({} bytes)", blobLength);

		HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
		urlConnection.setSSLSocketFactory(this.factory.makeFactory(url.getHost()));
		urlConnection.setConnectTimeout(ProtocolDefines.CONNECT_TIMEOUT * 1000);
		urlConnection.setReadTimeout(ProtocolDefines.BLOB_LOAD_TIMEOUT * 1000);
		urlConnection.setRequestMethod("POST");
		urlConnection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
		urlConnection.setRequestProperty("User-Agent", ProtocolStrings.USER_AGENT + "/" + version.getVersion());
		String header = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"blob\"; filename=\"blob.bin\"\r\n" + "Content-Type: application/octet-stream\r\n\r\n";
		byte[] headerBytes = header.getBytes();

		String footer = "\r\n--" + boundary + "--\r\n";
		byte[] footerBytes = footer.getBytes();

		urlConnection.setFixedLengthStreamingMode(headerBytes.length + blobLength + footerBytes.length);
		urlConnection.setDoOutput(true);
		urlConnection.setDoInput(true);

		try (BufferedOutputStream bos = new BufferedOutputStream(urlConnection.getOutputStream())) {
			bos.write(headerBytes);

			int ndone = 0;
			int nread;
			byte[] buf = new byte[CHUNK_SIZE];
			while ((nread = blobInputStream.read(buf)) > 0 && !cancel) {
				bos.write(buf, 0, nread);
				ndone += nread;

				if (progressListener != null && blobLength > 0)
					progressListener.updateProgress(100 * ndone / blobLength);
			}
			blobInputStream.close();

			if (cancel) {
				try {
					bos.close();
				} catch (ProtocolException x) {
					//ignore this exception, the upload was canceled
				}
				logger.info("Blob upload cancelled");
				if (progressListener != null) {
					progressListener.onFinished(false);
				}
				return null;
			}

			bos.write(footerBytes);
			bos.close();

			String blobIdHex;
			try (InputStream blobIdInputStream = urlConnection.getInputStream()) {
				blobIdHex = IOUtils.toString(blobIdInputStream);
			}

			if (blobIdHex == null) {
				if (progressListener != null) {
					progressListener.onFinished(false);
				}
				throw new ThreemaException("TB001");    /* Invalid blob ID received from server */
			}

			byte[] blobId = Utils.hexStringToByteArray(blobIdHex);
			if (blobId.length != ProtocolDefines.BLOB_ID_LEN) {
				if (progressListener != null) {
					progressListener.onFinished(false);
				}
				throw new ThreemaException("TB001");    /* Invalid blob ID received from server */
			}

			if (progressListener != null) {
				progressListener.onFinished(true);
			}

			logger.info("Blob upload completed; ID = {}", blobIdHex);

			return blobId;
		} finally {
			urlConnection.disconnect();
			if (blobInputStream != null) {
				try {
					blobInputStream.close();
				} catch (IOException ignored) {}
			}
		}
	}

	/**
	 * Cancel an upload in progress. upload() will return null.
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
		this.blobUploadUrlHttps = ipv6 ? ProtocolStrings.BLOB_UPLOAD_URL_IPV6 : ProtocolStrings.BLOB_UPLOAD_URL;
	}
}

/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2022 Threema GmbH
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

package ch.threema.app.services;

import android.content.Context;
import android.os.PowerManager;

import com.neilalexander.jnacl.NaCl;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import ch.threema.app.BuildConfig;
import ch.threema.app.utils.FileUtil;
import ch.threema.base.ProgressListener;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.base.utils.Utils;
import ch.threema.domain.protocol.blob.BlobLoader;

public class DownloadServiceImpl implements DownloadService {
	private static final Logger logger = LoggingUtil.getThreemaLogger("DownloadServiceImpl");

	private static final String TAG = "DownloadService";
	private static final String WAKELOCK_TAG = BuildConfig.APPLICATION_ID + ":" + TAG;
	private static final int DOWNLOAD_WAKELOCK_TIMEOUT = 10 * 1000;
	private final ArrayList<Download> downloads = new ArrayList<>();
	private final FileService fileService;
	private final ApiService apiService;
	private final PowerManager powerManager;

	private final static class Download {
		int messageModelId;
		byte[] blobId;
		BlobLoader blobLoader;

		public Download(int messageModelId, byte[] blobId, BlobLoader blobLoader) {
			this.messageModelId = messageModelId;
			this.blobId = blobId;
			this.blobLoader = blobLoader;
		}
	}

	private @Nullable ArrayList<Download> getDownloadsByMessageModelId(int messageModelId) {
		ArrayList<Download> matchingDownloads = new ArrayList<>();
		for (Download download: this.downloads) {
			if (download.messageModelId == messageModelId) {
				matchingDownloads.add(download);
			}
		}
		return matchingDownloads.size() > 0 ? matchingDownloads : null;
	}

	private @Nullable Download getDownloadByBlobId(@NonNull byte[] blobId) {
		for (Download download: this.downloads) {
			if (Arrays.equals(blobId, download.blobId)) {
				return download;
			}
		}
		return null;
	}

	private void removeDownloadByBlobId(@NonNull byte[] blobId) {
		synchronized (this.downloads) {
			Download download = getDownloadByBlobId(blobId);
			if (download != null) {
				logger.info("Blob {} remove downloader", Utils.byteArrayToHexString(blobId));
				downloads.remove(download);
			}
		}
	}

	private boolean removeDownloadByMessageModelId(int messageModelId, boolean cancel) {
		synchronized (this.downloads) {
			ArrayList<Download> matchingDownloads = getDownloadsByMessageModelId(messageModelId);
			if (matchingDownloads != null) {
				for (Download download: matchingDownloads) {
					logger.info("Blob {} remove downloader for message {}. Cancel = {}",
						Utils.byteArrayToHexString(download.blobId),
						messageModelId,
						cancel);
					if (cancel) {
						download.blobLoader.cancel();
					}
					this.downloads.remove(download);
				}
				return true;
			}
			return false;
		}
	}

	public DownloadServiceImpl(Context context, FileService fileService, ApiService apiService) {
		this.fileService = fileService;
		this.apiService = apiService;
		this.powerManager = (PowerManager) context.getApplicationContext().getSystemService(Context.POWER_SERVICE);
	}

	@Override
	@WorkerThread
	public @Nullable byte[] download(int messageModelId, final byte[] blobId, boolean markAsDown, @Nullable ProgressListener progressListener) {
		PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG);
		try {
			if (wakeLock != null) {
				wakeLock.acquire(DOWNLOAD_WAKELOCK_TIMEOUT);
				logger.info("Acquire download wakelock");
			}

			if (blobId == null) {
				logger.warn("Blob ID is null");
				return null;
			}

			final String blobIdHex = Utils.byteArrayToHexString(blobId);
			logger.info("Blob {} for message {} download requested", blobIdHex, messageModelId);

			byte[] blobBytes = null;
			File downloadFile = this.getTemporaryDownloadFile(blobId);
			boolean downloadSuccess = false;

			try {
				//check if a temporary file exist
				if (downloadFile.exists()) {
					if (downloadFile.length() >= NaCl.BOXOVERHEAD) {
						logger.warn("Blob {} download file already exists", blobIdHex);
						try (FileInputStream fileInputStream = new FileInputStream(downloadFile)) {
							return IOUtils.toByteArray(fileInputStream);
						}
					} else {
						// invalid download file - try again
						FileUtil.deleteFileOrWarn(downloadFile, "Download File", logger);
					}
				}

				BlobLoader blobLoader;
				synchronized (this.downloads) {
					if (getDownloadByBlobId(blobId) == null) {
						blobLoader = this.apiService.createLoader(blobId);
						this.downloads.add(new Download(messageModelId, blobId, blobLoader));
						logger.info("Blob {} downloader created", blobIdHex);
					} else {
						logger.info("Blob {} downloader already exists. Not adding again", blobIdHex);
						return null;
					}
				}

				if (progressListener != null) {
					blobLoader.setProgressListener(progressListener);
				}

				// Load blob from server
				logger.info("Blob {} now fetching", blobIdHex);
				blobBytes = blobLoader.load(false);

				if (blobBytes != null) {
					synchronized (this.downloads) {
						//check if loader already existing in array (otherwise its canceled)
						if (getDownloadByBlobId(blobId) != null) {
							logger.info("Blob {} now saving", blobIdHex);
							//write to temporary file
							FileUtil.createNewFileOrLog(downloadFile, logger);
							if (downloadFile.isFile()) {
								try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(downloadFile))) {
									bos.write(blobBytes);
									bos.flush();
								}

								if (downloadFile.length() == blobBytes.length) {
									downloadSuccess = true;

									//ok download saved, set as down if set
									if (markAsDown) {
										logger.info("Blob {} scheduled for marking as downloaded", blobIdHex);
										try {
											new Thread(() -> {
												synchronized (this.downloads) {
													Download download = getDownloadByBlobId(blobId);
													if (download != null) {
														if (download.blobLoader != null) {
															download.blobLoader.markAsDone(download.blobId);
														}
														logger.info("Blob {} marked as downloaded", blobIdHex);
													}
												}
											}, "MarkAsDownThread").start();
										} catch (Exception ignored) {
											// markAsDown thread failed
											// catch java.lang.InternalError: Thread starting during runtime shutdown
										}
									}
								} else {
									logger.warn("Blob and file size don't match.");
									return null;
								}
							} else {
								logger.warn("Blob file is a directory");
							}
						} else {
							logger.debug("No blob loaders, canceled?");
						}
					}
				}
			} catch (Exception x) {
				logger.error("Exception during blob download", x);
			}

			if (downloadSuccess) {
				logger.info("Blob {} successfully downloaded. Size = {}", blobIdHex, blobBytes.length);
			} else {
				logger.warn("Blob {} download failed.", blobIdHex);
			}

			if (blobBytes == null) {
				synchronized (this.downloads) {
					// download failed. remove loader
					Download download = getDownloadByBlobId(blobId);
					if (download != null) {
						logger.info("Blob {} remove downloader. Download failed.", blobIdHex);
						this.downloads.remove(download);
					}
				}
			}
			return blobBytes;
		} finally {
			if (wakeLock != null && wakeLock.isHeld()) {
				logger.info("Release download wakelock");
				wakeLock.release();
			}
		}
	}

	@Override
	public void complete(int messageModelId, byte[] blobId) {
		// success has been signalled. remove loader
		removeDownloadByBlobId(blobId);

		// remove temp file
		File f = this.getTemporaryDownloadFile(blobId);
		if(f.exists()) {
			FileUtil.deleteFileOrWarn(f, "remove temporary blob file", logger);
		}
	}

	@Override
	public boolean cancel(int messageModelId) {
		return removeDownloadByMessageModelId(messageModelId, true);
	}

	@Override
	public boolean isDownloading(int messageModelId) {
		synchronized (this.downloads) {
			return getDownloadsByMessageModelId(messageModelId) != null;
		}
	}

	@Override
	public boolean isDownloading() {
		synchronized (this.downloads) {
			return this.downloads.size() > 0;
		}
	}

	@Override
	public void error(int messageModelId) {
		// error has been signalled. remove loaders for this MessageModel
		removeDownloadByMessageModelId(messageModelId, false);
	}

	private File getTemporaryDownloadFile(byte[] blobId) {
		File path = this.fileService.getBlobDownloadPath();
		return new File(path.getPath() + "/" + Utils.byteArrayToHexString(blobId));
	}
}

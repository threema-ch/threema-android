/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2021 Threema GmbH
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
import android.util.SparseArray;

import com.neilalexander.jnacl.NaCl;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import ch.threema.app.BuildConfig;
import ch.threema.app.utils.FileUtil;
import ch.threema.client.BlobLoader;
import ch.threema.client.ProgressListener;
import ch.threema.client.Utils;

public class DownloadServiceImpl implements DownloadService {
	private static final Logger logger = LoggerFactory.getLogger(DownloadServiceImpl.class);

	private static final String TAG = "DownloadService";
	private static final String WAKELOCK_TAG = BuildConfig.APPLICATION_ID + ":" + TAG;
	private static final int DOWNLOAD_WAKELOCK_TIMEOUT = 10 * 1000;
	private final SparseArray<BlobLoader> blobLoaders = new SparseArray<>();
	private final FileService fileService;
	private final ApiService apiService;
	private PowerManager powerManager;


	public DownloadServiceImpl(Context context, FileService fileService, ApiService apiService) {
		this.fileService = fileService;
		this.apiService = apiService;
		this.powerManager = (PowerManager) context.getApplicationContext().getSystemService(Context.POWER_SERVICE);
	}

	@Override
	public byte[] download(int id, byte[] blobId, boolean markAsDown, ProgressListener progressListener) {
		PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG);
		try {
			if (wakeLock != null) {
				wakeLock.acquire(DOWNLOAD_WAKELOCK_TIMEOUT);
				logger.info("Acquire download wakelock");
			};
			logger.info("Download blob for message {}", id);

			if (blobId == null) {
				logger.warn("Blob ID is null");
				return null;
			}

			byte[] imageBlob = null;
			File downloadFile = this.getTemporaryDownloadFile(blobId);
			boolean downloadSuccess = false;

			try {
				//check if a temporary file exist
				if (downloadFile.exists()) {
					if (downloadFile.length() >= NaCl.BOXOVERHEAD) {
						logger.warn("Blob download file for message {} already exists", id);
						try (FileInputStream fileInputStream = new FileInputStream(downloadFile)) {
							return IOUtils.toByteArray(fileInputStream);
						}
					} else {
						// invalid download file - try again
						FileUtil.deleteFileOrWarn(downloadFile, "Download File", logger);
					}
				}

				BlobLoader blobLoader = null;
				synchronized (this.blobLoaders) {
					if (this.blobLoaders.get(id) == null) {
						blobLoader = this.apiService.createLoader(blobId);
						this.blobLoaders.append(id, blobLoader);
					} else {
						logger.info("Loader for message {} already exists. Not adding again.", id);
						return null;
					}
				}

				if (progressListener != null) {
					blobLoader.setProgressListener(progressListener);
				}


				// load image from server
				logger.info("Fetching blob for message {}", id);
				imageBlob = blobLoader.load(false);

				if (imageBlob != null) {
					synchronized (this.blobLoaders) {
						//check if loader already existing in array (otherwise its canceled)
						if (this.blobLoaders.get(id) != null) {
							logger.debug("Write blob to file");
							//write to temporary file
							FileUtil.createNewFileOrLog(downloadFile, logger);
							if (downloadFile.isFile()) {
								try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(downloadFile))) {
									bos.write(imageBlob);
									bos.flush();
								}

								if (downloadFile.length() == imageBlob.length) {
									downloadSuccess = true;

									//ok download saved, set as down if set
									if (markAsDown) {
										logger.info("Marking message {} as downloaded", id);
										final BlobLoader loader = this.blobLoaders.get(id);
										try {
											new Thread(() -> {
												if (loader != null) {
													loader.markAsDown(blobId);
												}
												logger.info("Marked message {} as downloaded", id);
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
				logger.info("Blob for message {} successfully downloaded. Size = {}", id, imageBlob.length);
			} else {
				logger.warn("Blob download for message {} failed.", id);
			}

			if (imageBlob == null) {
				synchronized (this.blobLoaders) {
					// download failed. remove loader
					this.blobLoaders.remove(id);
				}
			}
			return imageBlob;
		} finally {
			if (wakeLock != null && wakeLock.isHeld()) {
				logger.info("Release download wakelock");
				wakeLock.release();
			}
		}
	}

	@Override
	public void complete(int id, byte[] blobId) {
		synchronized (this.blobLoaders) {
			// success has been signalled. remove loader
			this.blobLoaders.remove(id);
		}

		// remove temp file
		File f = this.getTemporaryDownloadFile(blobId);
		if(f.exists()) {
			FileUtil.deleteFileOrWarn(f, "remove temporary blob file", logger);
		}
	}

	@Override
	public boolean cancel(int id) {
		synchronized (this.blobLoaders) {
			BlobLoader l = this.blobLoaders.get(id);
			if(l != null) {
				logger.debug("cancel blob loader");
				l.cancel();
				this.blobLoaders.remove(id);
				return true;
			}
		}

		return false;
	}

	@Override
	public boolean isDownloading(int messageModelId) {
		synchronized (this.blobLoaders) {
			return this.blobLoaders.get(messageModelId) != null;
		}
	}

	@Override
	public boolean isDownloading() {
		synchronized (this.blobLoaders) {
			return this.blobLoaders.size() > 0;
		}
	}

	@Override
	public void error(int id) {
		synchronized (this.blobLoaders) {
			// error has been signalled. remove loader
			this.blobLoaders.remove(id);
		}
	}

	private File getTemporaryDownloadFile(byte[] blobId) {
		File path = this.fileService.getBlobDownloadPath();
		return new File(path.getPath() + "/" + Utils.byteArrayToHexString(blobId));
	}
}

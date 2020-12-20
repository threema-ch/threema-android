/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020 Threema GmbH
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

package ch.threema.app.mediaattacher.labeling;

import android.Manifest;
import android.app.Notification;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.SystemClock;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;

import net.sqlcipher.database.SQLiteException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.mediaattacher.MediaAttachItem;
import ch.threema.app.mediaattacher.MediaRepository;
import ch.threema.app.mediaattacher.data.MediaItemsRoomDatabase;
import ch.threema.app.mediaattacher.data.PersistentMediaItem;
import ch.threema.app.mediaattacher.data.PersistentMediaItemsDAO;
import ch.threema.app.services.NotificationService;
import ch.threema.app.ui.MediaItem;
import ch.threema.app.utils.RandomUtil;
import ch.threema.localcrypto.MasterKeyLockedException;
import ch.threema.logging.ThreemaLogger;

/**
 * The ImageLabelingWorker fetches all media from the device and labels all images.
 * Results are written to the media {@link MediaItemsRoomDatabase}.
 *
 * Note: This worker requires Google Play Services due to the reliance on ML Kit!
 */
public class ImageLabelingWorker extends Worker {
	// Non-static logger (so that a prefix can be set)
	private final Logger logger = LoggerFactory.getLogger(ImageLabelingWorker.class);

	// Threema services
	private final NotificationService notificationService;

	// Media on device
	private final MediaRepository repository;

	// Database
	private final PersistentMediaItemsDAO mediaItemsDAO;

	// Image labeling
	private final ImageLabeler labeler;

	// Unique work name
	public static final String UNIQUE_WORK_NAME = "ImageLabelsOneTime";

	// Progress
	private volatile boolean cancelled = true;
	private int mediaCount;
	private int progress;

	// Executor
	private final ThreadPoolExecutor executor;

	// Global mutex
	private static final Object globalLock = new Object();

	/**
	 * Constructor for the ImageLabelingWorker.
	 *
	 * Note: This constructor is called by the WorkManager, so don't add additional parameters!
	 */
	public ImageLabelingWorker(@NonNull Context appContext, @NonNull WorkerParameters workerParams) {
		super(appContext, workerParams);

		// Set a log prefix to be able to differentiate multiple workers
		if (logger instanceof ThreemaLogger) {
			((ThreemaLogger)logger).setPrefix("id=" + RandomUtil.generateInsecureRandomAsciiString(6));
		}

		final ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager == null) {
			logger.error("Could not get service manager");
			onStopped();
			throw new IllegalStateException("Could not get service manager");
		}
		this.notificationService = serviceManager.getNotificationService();
		if (this.notificationService == null) {
			logger.error("Could not get notification service");
			onStopped();
			throw new IllegalStateException("Could not get notification service");
		}

		// Get database reference
		try {
			this.mediaItemsDAO = MediaItemsRoomDatabase.getDatabase(ThreemaApplication.getAppContext()).mediaItemsDAO();
		} catch (MasterKeyLockedException e) {
			logger.error("Could not get media items database, master key locked", e);
			onStopped();
			throw new IllegalStateException("Could not get media items database, master key locked");
		} catch (SQLiteException e) {
			logger.error("Could not get media items database, SQLite Exception", e);
			onStopped();
			throw new IllegalStateException("Could not get media items database, SQLite Exception");
		}

		// Initialize media repository
		this.repository = new MediaRepository(appContext);

		// Create labeler
		final ImageLabelerOptions options = new ImageLabelerOptions.Builder()
			.setConfidenceThreshold(0.8f)
			.build();
		this.labeler = ImageLabeling.getClient(options);

		// Create executor for database I/O
		final int numCores = Runtime.getRuntime().availableProcessors();
		final int minThreads = Math.min(numCores, 2);
		final int maxThreads = Math.min(numCores, 4);
		this.logger.debug("Starting thread pool");
		this.executor = new ThreadPoolExecutor(
			minThreads,
			maxThreads,
			5L, TimeUnit.SECONDS,
			new LinkedBlockingQueue<>()
		);

		logger.info("Created");
	}

	/**
	 * Return whether this media can be labelled.
	 */
	public static boolean mediaCanBeLabeled(@NonNull MediaAttachItem mediaItem) {
		switch (mediaItem.getType()) {
			case MediaItem.TYPE_IMAGE:
			case MediaItem.TYPE_GIF:
				return true;
			default:
				return false;
		}
	}

	/**
	 * Main work will be done here.
	 *
	 * Note: The worker may be cancelled at any time, so care should be taken that
	 * no inconsistent state can result from this.
	 */
	@Override
	@NonNull
	@WorkerThread
	public Result doWork() {
		if (!(ContextCompat.checkSelfPermission(ThreemaApplication.getAppContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)) {
			// we do not currently have permission to read storage - get out of here
			logger.info("Unable to label images. Permission denied.");
			this.cancelled = false;
			this.onFinish();

			// signal successful execution
			return Result.success();
		}

		// Synchronize on a global static lock, because a cancelled task may still
		// be running when a replacing task starts.
		synchronized (globalLock) {
			logger.info("Starting...");
			long startTime = SystemClock.elapsedRealtime();

			// Make this a foreground service with a progress notification
			final Notification notification = this.notificationService.createImageLabelingProgressNotification().build();
			if (notification == null) {
				logger.error("Could not create notification");
				return Result.failure();
			}
			this.setForegroundAsync(new ForegroundInfo(
				ThreemaApplication.IMAGE_LABELING_NOTIFICATION_ID,
				notification
			));

			// Fetch media from device
			final List<MediaAttachItem> allMediaCache = repository.getMediaFromMediaStore();
			this.mediaCount = allMediaCache.size();
			logger.info("Found {} media items", this.mediaCount);
			this.notificationService.updateImageLabelingProgressNotification(0, this.mediaCount);

			// Label images without labels
			int imageCounter = 0;
			int unlabeledCounter = 0;
			for (MediaAttachItem mediaItem : allMediaCache) {
				// Check whether we were stopped
				if (this.isStopped()) {
					logger.info("Work was cancelled");
					break;
				}

				// Update notification
				this.notificationService.updateImageLabelingProgressNotification(this.progress, this.mediaCount);

				// Update progress
				this.progress++;

				// We're only interested in image media
				if (mediaCanBeLabeled(mediaItem)) {
					imageCounter++;
				} else {
					continue;
				}

				// Query the media items database, maybe we already have labels for this item?
				final List<String> savedLabels = mediaItemsDAO.getMediaItemLabels(mediaItem.getId());
				if (savedLabels.isEmpty()) {
					unlabeledCounter++;

					// Load image from filesystem
					InputImage image;
					try {
						image = InputImage.fromFilePath(ThreemaApplication.getAppContext(), mediaItem.getUri());
					} catch (Exception e) {
						logger.warn("Exception, could not generate input image from file path: {}", e.getMessage());
						if (e.getCause() != null) {
							logger.warn("  Caused by: {}", e.getCause().getMessage());
						}
						continue;
					}

					// Launch a labeling task
					//
					// Note: By default, the success/failure listeners run on the UI thread.
					// to prevent this, we pass in an executor.
					final Task<?> task = labeler.process(image)
						.addOnSuccessListener(executor, labels -> {
							ArrayList<String> labelsListIndexes = new ArrayList<>();
							for (ImageLabel label : labels) {
								labelsListIndexes.add(String.valueOf(label.getIndex()));
							}
							logger.debug("Found {} labels for {}", labelsListIndexes.size(), mediaItem.getDisplayName());
							mediaItemsDAO.insert(new PersistentMediaItem(mediaItem.getId(), labelsListIndexes));
						});

					// We're waiting for the task to complete, because processing multiple images in parallel
					// would fill up the memory with their data.
					try {
						Tasks.await(task);
					} catch (ExecutionException e) {
						logger.error("Could not get image labels for image", e);
						return Result.failure();
					} catch (InterruptedException e) {
						// An interrupt occurred while waiting for the task to complete.
						// Restore interrupted state...
						Thread.currentThread().interrupt();
						// ...and abort the worker.
						return Result.failure();
					}

					if (unlabeledCounter % 20 == 0) {
						logger.info("Processed {} files…", unlabeledCounter);
					}
				}
			}

			// Update notification
			this.notificationService.updateImageLabelingProgressNotification(this.progress, this.mediaCount);

			final long secondsElapsedLabeling = (SystemClock.elapsedRealtime() - startTime) / 1000;
			if (this.isStopped()) {
				logger.info("Aborting now after {}s, because work was cancelled", secondsElapsedLabeling);
				return Result.failure();
			} else {
				logger.info("Processed {} unlabeled images among {} total images", unlabeledCounter, imageCounter);
				logger.info("Labeling work done after {}s, starting cleanup", secondsElapsedLabeling);
			}

			// Delete labels from database that belong to nonexisting media items
			List<PersistentMediaItem> currentlyStoredLabels = mediaItemsDAO.getAllItemsByAscIdOrder();
			if (currentlyStoredLabels != null) {
				Collections.sort(allMediaCache, (o1, o2) -> Double.compare(o1.getId(), o2.getId()));
				int indexStoredLabelsList = 0;
				int indexStoredMediaItemsList = 0;
				while (indexStoredLabelsList < currentlyStoredLabels.size() && indexStoredMediaItemsList < allMediaCache.size()) {
					int storedItemIDCurrent = currentlyStoredLabels.get(indexStoredLabelsList).getId();
					int retrievedItemIDCurrent = allMediaCache.get(indexStoredMediaItemsList).getId();
					if (storedItemIDCurrent == retrievedItemIDCurrent) {
						// Found match!
						indexStoredLabelsList++;
						indexStoredMediaItemsList++;
					} else if (storedItemIDCurrent < retrievedItemIDCurrent) {
						// No match, discard entry in labels database
						logger.info("deleting media labels for id {}", currentlyStoredLabels.get(indexStoredLabelsList).getId());
						mediaItemsDAO.deleteMediaItemById(currentlyStoredLabels.get(indexStoredLabelsList).getId());
						indexStoredLabelsList++;
					} else {
						// No match, discard first entry in long list
						indexStoredMediaItemsList++;
					}
				}
			}

			final long secondsElapsedTotal = (SystemClock.elapsedRealtime() - startTime) / 1000;
			logger.info("Processing done, total duration was {}s", secondsElapsedTotal);

			this.cancelled = false;
			this.onFinish();
			return Result.success();
		}
	}

	private void onFinish() {
		// Shut down executor thread pool
		if (!this.executor.isShutdown()) {
			this.logger.debug("Shut down thread pool");
			this.executor.shutdown();
		}

		if (this.cancelled) {
			logger.info("Cancelled after processing {}/{} media files", this.progress, this.mediaCount);
		} else {
			logger.info("Stopped after processing {}/{} media files", this.progress, this.mediaCount);
		}
	}

	@Override
	public void onStopped() {
		super.onStopped();
		this.onFinish();
	}
}

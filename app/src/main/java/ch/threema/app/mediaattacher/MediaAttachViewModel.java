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

package ch.threema.app.mediaattacher;

import android.Manifest;
import android.app.Application;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;

import net.sqlcipher.database.SQLiteException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.preference.PreferenceManager;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.mediaattacher.data.ImageLabelListConverter;
import ch.threema.app.mediaattacher.data.MediaItemsRoomDatabase;
import ch.threema.app.mediaattacher.data.PersistentMediaItemsDAO;
import ch.threema.app.mediaattacher.labeling.ImageLabelingWorker;
import ch.threema.app.mediaattacher.labeling.ImageLabelsIndexHashMap;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.localcrypto.MasterKeyLockedException;
import java8.util.concurrent.CompletableFuture;
import java8.util.stream.Collectors;
import java8.util.stream.StreamSupport;

/**
 * The view model used by the media attacher.
 *
 * When initialized, the list of available media files is fetched from Android.
 * There are to {@link MediaAttachItem} lists stored internally: The {@link #allMedia}
 * which holds a list of all media that were found, and the {@link #currentMedia} list,
 * which holds the media filtered by the current filter. Both are LiveData and thus observable.
 */
public class MediaAttachViewModel extends AndroidViewModel {
	// Logger
	private static final Logger logger = LoggerFactory.getLogger(MediaAttachViewModel.class);

	// Application context object
	private final Application application;

	// Media
	private final @NonNull MutableLiveData<List<MediaAttachItem>> allMedia = new MutableLiveData<>(Collections.emptyList());
	private final @NonNull MutableLiveData<List<MediaAttachItem>> currentMedia = new MutableLiveData<>(Collections.emptyList());

	private final MutableLiveData<LinkedHashMap<Integer, MediaAttachItem>> selectedItems;
	private final MutableLiveData<List<String>> suggestionLabels = new MutableLiveData<>();
	private final MediaRepository repository;
	private final SavedStateHandle savedState;

	// This future resolves once the initial media load is done
	private final CompletableFuture<Void> initialLoadDone = new CompletableFuture<>();

	private final String KEY_SELECTED_MEDIA = "suggestion_labels";
	private final String KEY_TOOLBAR_TITLE = "toolbar_title";
	private final String KEY_LABEL_QUERY = "label_query";

	public MediaAttachViewModel(@NonNull Application application, @NonNull SavedStateHandle savedState) {
		super(application);
		this.savedState = savedState;
		this.application = application;
		this.repository = new MediaRepository(application.getApplicationContext());
		this.selectedItems = savedState.getLiveData(KEY_SELECTED_MEDIA, new LinkedHashMap<>());
		savedState.set(KEY_SELECTED_MEDIA, selectedItems.getValue());

		// Fetch initial data
		if (ContextCompat.checkSelfPermission(ThreemaApplication.getAppContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
			this.fetchAllMediaFromRepository();
			this.initialLoadDone.thenRunAsync(() -> {
				// Update current media
				currentMedia.postValue(this.allMedia.getValue());

				SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(application);

				// Check whether search can be shown
				if (ConfigUtils.isPlayServicesInstalled(this.application) &&
					sharedPreferences != null &&
					sharedPreferences.getBoolean(application.getString(R.string.preferences__image_attach_previews), false)) {
					this.checkLabelingComplete();
				}
			});
		}
	}

	public @NonNull MutableLiveData<List<MediaAttachItem>> getCurrentMedia() {
		return currentMedia;
	}

	public @NonNull MutableLiveData<List<MediaAttachItem>> getAllMedia() {
		return allMedia;
	}

	/**
	 * Asynchronously fetch all media from the {@link MediaRepository}.
	 * Once this is done, the `allMedia` LiveData object will be updated.
	 */
	@AnyThread
	public void fetchAllMediaFromRepository() {
		new Thread(() -> {
			final List<MediaAttachItem> mediaAttachItems = repository.getMediaFromMediaStore();
			RuntimeUtil.runOnUiThread(() -> {
				allMedia.setValue(mediaAttachItems);
				initialLoadDone.complete(null);
			});
		}).start();
	}

	/**
	 * Asynchronously check whether the labels are already processed.
	 * If this is the case, the `suggestionLabels` LiveData object will be updated.
	 *
	 * This should only be called if play services are installed.
	 */
	@AnyThread
	private void checkLabelingComplete() {
		new Thread(() -> {
			// Open database
			final PersistentMediaItemsDAO mediaItemsDAO;
			try {
				mediaItemsDAO = MediaItemsRoomDatabase.getDatabase(application).mediaItemsDAO();
			} catch (MasterKeyLockedException e) {
				logger.error("Could not access database", e);
				return;
			} catch (SQLiteException e) {
				logger.error("Could not get media items database, SQLite Exception", e);
				return;
			}

			// Get label count from database
			final int labeledMediaCount = mediaItemsDAO.getRowCount();

			// Get the media count (by this time, it should be ready because
			// this method is called after `initialLoadDone` fires)
			final List<MediaAttachItem> allMediaValue = Objects.requireNonNull(this.allMedia.getValue());
			final int totalMediaSize = Functional.filter(allMediaValue, (IPredicateNonNull<MediaAttachItem>) ImageLabelingWorker::mediaCanBeLabeled).size();

			final float labeledRatio = (float) labeledMediaCount / (float) totalMediaSize;
			if (labeledRatio > 0.9) {
				// More than 90% labeled. Good enough, but kick off the labeller anyways if we're not at 100%.
				if (labeledMediaCount < totalMediaSize) {
					this.startImageLabeler();
				}

				// Get hashmap for label mapping
				final ImageLabelsIndexHashMap labelsIndexHashMap = new ImageLabelsIndexHashMap(this.application);

				// Iterate over all media items, translate and set labels
				final Set<String> translatedLabels = new HashSet<>();
				for (MediaAttachItem mediaItem : allMediaValue) {
					final List<String> translatedItemLabels = StreamSupport
						.stream(mediaItemsDAO.getMediaItemLabels(mediaItem.getId()))
						.map(ImageLabelListConverter::fromString) // TODO: Find out how to do this with the TypeConverter directly
						.flatMap(StreamSupport::stream)
						.map(labelsIndexHashMap::mapIdToName)
						.distinct()
						.collect(Collectors.toList());
					translatedLabels.addAll(translatedItemLabels);
					mediaItem.setLabels(translatedItemLabels);
				}

				final List<String> sortedLabels = StreamSupport.stream(translatedLabels)
					.sorted()
					.collect(Collectors.toList());
				logger.info("Found {} distinct labels in database", translatedLabels.size());
				suggestionLabels.postValue(sortedLabels);
			} else {
				logger.info("Less than 90% labeled, considering labels incomplete");
				this.startImageLabeler();
				suggestionLabels.postValue(Collections.emptyList());
			}
		}).start();
	}

	/**
	 * Write all media to {@link #currentMedia} list.
	 */
	@UiThread
	public void setAllMedia() {
		currentMedia.setValue(this.allMedia.getValue());
	}

	/**
	 * Write selected media to {@link #currentMedia} list.
	 */
	@UiThread
	public void setSelectedMedia() {
		currentMedia.setValue(new ArrayList<>(Objects.requireNonNull(selectedItems.getValue()).values()));
	}

	/**
	 * Filter media by bucket, update the {@link #currentMedia} list.
	 */
	@UiThread
	public void setMediaByBucket(@NonNull String bucket) {
		ArrayList<MediaAttachItem> filteredMedia = new ArrayList<>();
		final List<MediaAttachItem> items = Objects.requireNonNull(this.allMedia.getValue());
		for (MediaAttachItem mediaItem : items) {
			if (mediaItem.getBucketName().equals(bucket)) {
				filteredMedia.add(mediaItem);
			}
		}
		currentMedia.setValue(filteredMedia);
	}

	/**
	 * Filter media by MIME type, update the {@link #currentMedia} list.
	 */
	@UiThread
	public void setMediaByType(int mimeType) {
		ArrayList<MediaAttachItem> filteredMedia = new ArrayList<>();
		final List<MediaAttachItem> items = Objects.requireNonNull(this.allMedia.getValue());
		for (MediaAttachItem mediaItem : items) {
			if (mediaItem.getType() == mimeType) {
				filteredMedia.add(mediaItem);
			}
		}
		currentMedia.setValue(filteredMedia);
	}

	/**
	 * Filter media by label, update the {@link #currentMedia} list.
	 */
	@UiThread
	public void setMediaByLabel(@NonNull String filterLabel) {
		ArrayList<MediaAttachItem> filteredMedia = new ArrayList<>();
		final List<MediaAttachItem> items = Objects.requireNonNull(this.allMedia.getValue());
		for (MediaAttachItem mediaItem : items) {
			if (mediaItem.getLabels() != null) {
				for (String mediaItemLabel : mediaItem.getLabels()) {
					if (mediaItemLabel.toLowerCase().trim().equals(filterLabel.toLowerCase().trim())) {
						filteredMedia.add(mediaItem);
					}
				}
			}
		}
		currentMedia.setValue(filteredMedia);
	}

	/**
	 * Start image labeling in a background task.
	 *
	 * Note: Should only be called if play services are installed!
	 */
	@AnyThread
	private void startImageLabeler() {
		logger.debug("startImageLabeler");
		final WorkManager workManager = WorkManager.getInstance(application);
		final OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(ImageLabelingWorker.class)
			.addTag(ImageLabelingWorker.UNIQUE_WORK_NAME)
			.build();
		workManager.enqueueUniqueWork(ImageLabelingWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, workRequest);
		logger.info("Started OneTimeWorkRequest for ImageLabelingWorker");
	}

	public ArrayList<Uri> getSelectedMediaUris() {
		ArrayList<Uri> selectedUris = new ArrayList<>();
		if (selectedItems.getValue() != null) {
			for (Map.Entry<Integer, MediaAttachItem> entry : selectedItems.getValue().entrySet()) {
				selectedUris.add(entry.getValue().getUri());
			}
		}
		return selectedUris;
	}

	public HashMap<Integer, MediaAttachItem> getSelectedMediaItemsHashMap() {
		return savedState.get(KEY_SELECTED_MEDIA);
	}

	public void removeSelectedMediaItem(int id) {
		if (selectedItems.getValue() != null) {
			selectedItems.getValue().remove(id);
			savedState.set(KEY_SELECTED_MEDIA, selectedItems.getValue());
		}
	}

	public void addSelectedMediaItem(int id, MediaAttachItem mediaAttachItem) {
		if (selectedItems.getValue() != null) {
			selectedItems.getValue().put(id, mediaAttachItem);
			savedState.set(KEY_SELECTED_MEDIA, selectedItems.getValue());
		}
	}

	public void clearSelection() {
		if (selectedItems.getValue() != null) {
			selectedItems.getValue().clear();
			savedState.set(KEY_SELECTED_MEDIA, selectedItems.getValue());
		}
	}

	/**
	 * Return a LiveData object that will eventually resolve to a list of sorted and translated labels.
	 */
	public MutableLiveData<List<String>> getSuggestionLabels() {
		return suggestionLabels;
	}

	public String getToolBarTitle() {
		return savedState.get(KEY_TOOLBAR_TITLE);
	}

	public void setToolBarTitle(String toolBarTitle) {
		savedState.set(KEY_TOOLBAR_TITLE, toolBarTitle);
	}

	public String getLabelQuery() {
		return savedState.get(KEY_LABEL_QUERY);
	}

	public void setLabelQuery(String labelQuery) {
		savedState.set(KEY_LABEL_QUERY, labelQuery);
	}
}

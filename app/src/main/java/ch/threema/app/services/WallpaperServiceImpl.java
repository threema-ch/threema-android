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

package ch.threema.app.services;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Parcel;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.Toast;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

import javax.crypto.CipherInputStream;

import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.fragment.app.Fragment;
import ch.threema.app.R;
import ch.threema.app.activities.CropImageActivity;
import ch.threema.app.activities.ThreemaActivity;
import ch.threema.app.dialogs.BottomSheetAbstractDialog;
import ch.threema.app.dialogs.BottomSheetListDialog;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.ui.BottomSheetItem;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.LogUtil;
import ch.threema.app.utils.MimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.localcrypto.MasterKey;
import java8.util.concurrent.CompletableFuture;
import java8.util.function.Supplier;

import static android.provider.MediaStore.MEDIA_IGNORE_FILENAME;

public class WallpaperServiceImpl implements WallpaperService {
	private static final Logger logger = LoggerFactory.getLogger(WallpaperServiceImpl.class);

	private static final String DIALOG_TAG_LOADING_IMAGE = "lit";
	private static final String SELECTOR_TAG_WALLPAPER_DEFAULT = "def";
	private static final String SELECTOR_TAG_WALLPAPER_GALLERY = "gal";
	private static final String SELECTOR_TAG_WALLPAPER_NONE = "none";
	private static final String DIALOG_TAG_SELECT_WALLPAPER = "selwal";

	private final Context context;
	private final PreferenceService preferenceService;
	private final FileService fileService;
	private final MasterKey masterKey;
	private File wallpaperCropFile;

	public WallpaperServiceImpl(Context context, FileService fileService, PreferenceService preferenceService, MasterKey masterKey) {
		this.context = context;
		this.preferenceService = preferenceService;
		this.fileService = fileService;
		this.masterKey = masterKey;
	}

	@Override
	public boolean removeWallpaper(MessageReceiver messageReceiver) {
		String path = fileService.getWallpaperFilePath(messageReceiver);
		if (path != null) {
			File wallpaperFile = new File(path);

			if (wallpaperFile.exists()) {
				FileUtil.deleteFileOrWarn(wallpaperFile, "removeWallpaper", logger);
				return true;
			}
		}
		return false;
	}

	@Override
	public void removeWallpaper(String uniqueIdString) {
		String path = fileService.getWallpaperFilePath(uniqueIdString);
		if (path != null) {
			File wallpaperFile = new File(path);

			if (wallpaperFile.exists()) {
				FileUtil.deleteFileOrWarn(wallpaperFile, "removeWallpaper", logger);
			}
		}
	}

	@AnyThread
	private CompletableFuture<Bitmap> getWallpaperBitmap(MessageReceiver messageReceiver, boolean landscape) {
		return CompletableFuture.supplyAsync(new Supplier<Bitmap>() {
			@Override
			public Bitmap get() {
				Bitmap bitmap = null;

				// we can't rewind an inputstream - so we assume an inSampleSize of
				// 2 to avoid oom on crappy devices
				final BitmapFactory.Options options = new BitmapFactory.Options();
				options.inSampleSize = 1;
				options.inPreferredConfig = Bitmap.Config.RGB_565;

				String path = fileService.getWallpaperFilePath(messageReceiver);
				if (path != null) {
					File wallpaperFile = new File(path);
					if (wallpaperFile.exists()) {
						// decode file
						try (FileInputStream fis = new FileInputStream(wallpaperFile); CipherInputStream cis = masterKey.getCipherInputStream(fis)) {
							bitmap = BitmapFactory.decodeStream(cis, null, options);
						} catch (Exception e) {
							logger.error("Exception", e);
						}
					}
				}

				if (bitmap == null && preferenceService.isCustomWallpaperEnabled()) {
					path = fileService.getGlobalWallpaperFilePath();
					if (!TestUtil.empty(path)) {
						try (FileInputStream fis = new FileInputStream(new File(path)); CipherInputStream cis = masterKey.getCipherInputStream(fis)) {
							bitmap = BitmapFactory.decodeStream(cis, null, options);
						} catch (Exception e) {
							//
						}
						if (bitmap == null) {
							// fallback to unencrypted bitmap
							bitmap = BitmapFactory.decodeFile(path, options);
						}
					}
				}

				if (bitmap == null && !hasGlobalEmptyWallpaper() && !ConfigUtils.isWorkBuild()) {
					final BitmapFactory.Options noptions = new BitmapFactory.Options();
					noptions.inPreferredConfig = Bitmap.Config.ALPHA_8;
					noptions.inSampleSize = 1;

					int resource = ConfigUtils.getAppTheme(context) == ConfigUtils.THEME_DARK ?
						R.drawable.wallpaper_dark : R.drawable.wallpaper_light;
					try {
						bitmap = BitmapFactory.decodeResource(context.getResources(), resource, noptions);
					} catch (Exception e) {
						logger.error("Exception", e);
					}

					if (bitmap != null && landscape) {
						return BitmapUtil.rotateBitmap(bitmap, 90);
					}
				}

				return bitmap;
			}
		});
	}

	@UiThread
	private boolean setImageView(ImageView wallpaperView, @Nullable Bitmap bitmap) {
		if (wallpaperView != null) {
			if (bitmap != null) {
				wallpaperView.setImageBitmap(bitmap);
				wallpaperView.setVisibility(View.VISIBLE);
				return true;
			}

			wallpaperView.setBackgroundDrawable(null);
			wallpaperView.setVisibility(View.INVISIBLE);
		}
		return false;
	}

	@Override
	@UiThread
	public boolean setupWallpaperBitmap(MessageReceiver messageReceiver, ImageView wallpaperView, boolean landscape) {
		if (TestUtil.required(messageReceiver, wallpaperView)) {
			Bitmap bitmap = null;
			try {
				if (!hasEmptyWallpaper(messageReceiver).get()) {
					bitmap = getWallpaperBitmap(messageReceiver, landscape).get();
				}
				return setImageView(wallpaperView, bitmap);
			} catch (InterruptedException e) {
				logger.error("Exception", e);
				// Restore interrupted state...
				Thread.currentThread().interrupt();
			} catch (ExecutionException e) {
				logger.error("Exception", e);
			}
		}
		return false;
	}

	@Override
	public void selectWallpaper(final Fragment fragment, final MessageReceiver messageReceiver, Runnable onSuccess) {
		ArrayList<BottomSheetItem> items = new ArrayList<>();

		if (!ConfigUtils.isWorkBuild() || messageReceiver != null) {
			items.add(new BottomSheetItem(
					messageReceiver == null ?
						R.drawable.ic_notification_small :
						R.drawable.ic_check,
					messageReceiver == null ?
						context.getString(R.string.wallpaper_threema, context.getString(R.string.app_name)) :
						context.getString(R.string.wallpaper_default),
					SELECTOR_TAG_WALLPAPER_DEFAULT
				)
			);
		}

		items.add(new BottomSheetItem(
				R.drawable.ic_image_outline,
				context.getString(R.string.wallpaper_gallery),
				SELECTOR_TAG_WALLPAPER_GALLERY
			)
		);

		items.add(new BottomSheetItem(
				R.drawable.ic_delete_outline,
				context.getString(R.string.wallpaper_none),
				SELECTOR_TAG_WALLPAPER_NONE
			)
		);

		int defaultEntry = 0;
		if (messageReceiver == null) {
			// global
			if (hasGlobalEmptyWallpaper()) {
				defaultEntry = ConfigUtils.isWorkBuild() ? 1 : 2;
			} else if (hasGlobalGalleryWallpaper()) {
				defaultEntry = ConfigUtils.isWorkBuild() ? 0 : 1;
			}
		} else {
			// individual
			try {
				if (hasEmptyWallpaper(messageReceiver).get()) {
					defaultEntry = 2;
				} else if (hasGalleryWallpaper(messageReceiver)) {
					defaultEntry = 1;
				}
			} catch (InterruptedException e) {
				logger.error("Exception", e);
				// Restore interrupted state...
				Thread.currentThread().interrupt();
			} catch (ExecutionException e) {
				logger.error("Exception", e);
			}
		}

		BottomSheetListDialog dialog = BottomSheetListDialog.newInstance(R.string.prefs_title_wallpaper_switch, items, defaultEntry, new BottomSheetAbstractDialog.BottomSheetDialogInlineClickListener() {
			@Override
			public int describeContents() {
				return 0;
			}

			@Override
			public void writeToParcel(Parcel dest, int flags) {
			}

			@Override
			public void onSelected(String tag) {
				if (fragment.isAdded()) {
					switch (tag) {
						case SELECTOR_TAG_WALLPAPER_DEFAULT:
							setDefaultWallpaper(messageReceiver);
							if (onSuccess != null) {
								onSuccess.run();
							}
							break;
						case SELECTOR_TAG_WALLPAPER_GALLERY:
							selectWallpaperFromGallery(fragment);
							break;
						case SELECTOR_TAG_WALLPAPER_NONE:
							setEmptyWallpaper(messageReceiver);
							if (onSuccess != null) {
								onSuccess.run();
							}
							break;
					}
				}
			}

			@Override
			public void onCancel(String tag) {
			}
		});
		dialog.show(fragment.getFragmentManager(), DIALOG_TAG_SELECT_WALLPAPER);
	}

	private File deleteWallpaperFile(MessageReceiver messageReceiver) {
		File file;
		if (messageReceiver == null) {
			preferenceService.setCustomWallpaperEnabled(true);
			file = new File(fileService.getGlobalWallpaperFilePath());
		} else {
			file = new File(fileService.getWallpaperFilePath(messageReceiver));
		}
		FileUtil.deleteFileOrWarn(file, "deleteWallpaperFile", logger);

		return file;
	}

	private void setDefaultWallpaper(MessageReceiver messageReceiver) {
		deleteWallpaperFile(messageReceiver);

		if (messageReceiver == null) {
			preferenceService.setCustomWallpaperEnabled(false);
		}
	}

	private void setEmptyWallpaper(MessageReceiver messageReceiver) {
		File file = deleteWallpaperFile(messageReceiver);

		if (messageReceiver == null) {
			preferenceService.setCustomWallpaperEnabled(true);
		}

		// create an empty file
		try {
			FileUtil.createNewFileOrLog(file, logger);
		} catch (IOException e) {
			logger.error("Exception", e);
		}
	}

	private void selectWallpaperFromGallery(Fragment fragment) {
		FileUtil.selectFile(null, fragment, new String[]{MimeUtil.MIME_TYPE_IMAGE}, ThreemaActivity.ACTIVITY_ID_SETTINGS, false, 0, null);
	}

	@Override
	public boolean hasGalleryWallpaper(MessageReceiver messageReceiver) {
		if (messageReceiver != null) {
			String path = fileService.getWallpaperFilePath(messageReceiver);
			if (path != null) {
				File file = new File(path);

				return file.exists() && file.length() > 0;
			}
		}
		return false;
	}

	private CompletableFuture<Boolean> hasEmptyWallpaper(MessageReceiver messageReceiver) {
		return CompletableFuture.supplyAsync(new Supplier<Boolean>() {
			@Override
			public Boolean get() {
				if (messageReceiver != null) {

					String path = fileService.getWallpaperFilePath(messageReceiver);
					if (path != null) {
						File file = new File(path);
						return file.exists() && file.length() == 0;
					}
				}
				return false;
			}
		});
	}

	@Override
	public boolean hasGlobalGalleryWallpaper() {
		File wallpaperFile = new File(fileService.getGlobalWallpaperFilePath());
		return wallpaperFile.exists() && wallpaperFile.length() > 0;
	}

	@Override
	public boolean hasGlobalEmptyWallpaper() {
		File wallpaperFile = new File(fileService.getGlobalWallpaperFilePath());
		return wallpaperFile.exists() && wallpaperFile.length() == 0;
	}

	@SuppressLint("StaticFieldLeak")
	@Override
	public boolean handleActivityResult(final Fragment fragment, int requestCode, int resultCode, final Intent data, MessageReceiver messageReceiver) {
		if (data == null) {
			return false;
		}

		final File tempWallpaperFile = new File(fileService.getTempPath() + "/.wp" + MEDIA_IGNORE_FILENAME);

		switch (requestCode) {
			case ThreemaActivity.ACTIVITY_ID_SETTINGS:
				if (resultCode == Activity.RESULT_OK) {
					try {
						this.wallpaperCropFile = this.fileService.createWallpaperFile(messageReceiver);
					} catch (IOException e) {
						LogUtil.exception(e, fragment.getActivity());
						break;
					}

					if (TestUtil.required(this.wallpaperCropFile)) {
						// input stream might be a cloud file, so put loading into an asynctask
						new AsyncTask<Void, Void, Boolean>() {
							@Override
							protected void onPreExecute() {
								super.onPreExecute();
								GenericProgressDialog.newInstance(R.string.download, R.string.please_wait).show(fragment.getFragmentManager(), DIALOG_TAG_LOADING_IMAGE);
							}

							@Override
							protected Boolean doInBackground(Void... params) {
								Activity activity = fragment.getActivity();
								if (activity != null) {
									try (InputStream inputStream = activity.getContentResolver().openInputStream(data.getData()); FileOutputStream fos = new FileOutputStream(tempWallpaperFile)) {
										if (inputStream != null) {
											IOUtils.copy(inputStream, fos);
											return true;
										}
									} catch (Exception e) {
										logger.error("Exception", e);
									}
								}
								return false;
							}

							@Override
							protected void onPostExecute(Boolean success) {
								super.onPostExecute(success);
								DialogUtil.dismissDialog(fragment.getFragmentManager(), DIALOG_TAG_LOADING_IMAGE, true);
								if (success) {
									doCrop(fragment, Uri.fromFile(tempWallpaperFile));
								}
							}
						}.execute();
						return true;
					}
				}
				break;
			case CropImageActivity.REQUEST_CROP:
				/* return from wallpaper crop */
				boolean result;

				if (resultCode == Activity.RESULT_OK &&
					fileService.decryptFileToFile(tempWallpaperFile, this.wallpaperCropFile)) {
					preferenceService.setCustomWallpaperEnabled(true);
					result = true;
				} else {
					/*
					if (this.wallpaperCropFile != null && this.wallpaperCropFile.exists()) {
						this.wallpaperCropFile.delete();
					}
					*/
					result = false;
				}
				FileUtil.deleteFileOrWarn(tempWallpaperFile, "deleteCropFile", logger);
				return result;
			default:
				break;
		}
		return false;
	}

	@Override
	public void removeAll(Context context, boolean silent) {
		try {
			FileUtils.cleanDirectory(fileService.getWallpaperDirPath());
			if (!silent) {
				Toast.makeText(context, context.getString(R.string.wallpapers_removed), Toast.LENGTH_SHORT).show();
			}
		} catch (IOException e) {
			logger.debug("Unable to empty wallpaper dir");
		}
	}

	private void doCrop(Fragment fragment, Uri imageUri) {
		Activity activity = fragment.getActivity();

		DisplayMetrics metrics = new DisplayMetrics();
		activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
		Rect rectgle = new Rect();
		Window window = activity.getWindow();
		window.getDecorView().getWindowVisibleDisplayFrame(rectgle);

		int width = metrics.widthPixels;
		int height = metrics.heightPixels;

		if (height > width) {
			// portrait
			height -= rectgle.top;
		} else {
			// landscape
			width -= rectgle.top;
		}

		int y = Math.max(width, height);
		int x = Math.min(width, height);

		Intent intent = new Intent(activity, CropImageActivity.class);
		intent.setData(imageUri);
		intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
		intent.putExtra(CropImageActivity.EXTRA_MAX_X, x);
		intent.putExtra(CropImageActivity.EXTRA_MAX_Y, y);
		intent.putExtra(CropImageActivity.EXTRA_ASPECT_X, x);
		intent.putExtra(CropImageActivity.EXTRA_ASPECT_Y, y);
		fragment.startActivityForResult(intent, CropImageActivity.REQUEST_CROP);
	}
}

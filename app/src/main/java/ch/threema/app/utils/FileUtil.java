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

package ch.threema.app.utils;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.Fragment;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.camera.CameraActivity;
import ch.threema.app.filepicker.FilePickerActivity;
import ch.threema.app.services.FileService;
import ch.threema.app.ui.MediaItem;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.data.media.FileDataModel;

import static ch.threema.app.filepicker.FilePickerActivity.INTENT_DATA_DEFAULT_PATH;

public class FileUtil {
	private static final Logger logger = LoggerFactory.getLogger(FileUtil.class);

	private FileUtil() {

	}

	public static boolean isFilePresent(File filename) {
		return filename != null && filename.exists() && filename.length() > 0;
	}

	public static void selectFile(Activity activity, Fragment fragment, String[] mimeTypes, int ID, boolean multi, int sizeLimit, String initialPath) {
		Intent intent;
		Context context;

		if (fragment != null) {
			context = fragment.getActivity();
		} else {
			context = activity;
		}

		if ((isMediaProviderSupported(context) && initialPath == null) || ConfigUtils.hasScopedStorage()) {
			intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
			intent.addCategory(Intent.CATEGORY_OPENABLE);
			if (mimeTypes.length > 1) {
				intent.setType("*/*");
				intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
			} else {
				intent.setType(mimeTypes[0]);
			}
			// undocumented APIs according to https://issuetracker.google.com/issues/72053350
			intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
			intent.putExtra("android.content.extra.FANCY", true);
			intent.putExtra("android.content.extra.SHOW_FILESIZE", true);
		}
		else {
			intent = new Intent();
			if (MimeUtil.isVideoFile(mimeTypes[0]) || MimeUtil.isImageFile(mimeTypes[0])) {
				intent.setAction(Intent.ACTION_GET_CONTENT);
			} else {
				intent = new Intent(context, FilePickerActivity.class);
				if (initialPath != null) {
					intent.putExtra(INTENT_DATA_DEFAULT_PATH, initialPath);
				}
			}
			intent.setType(mimeTypes[0]);
		}

		if (multi) {
			intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
		}

		if (sizeLimit > 0) {
			intent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, (long) sizeLimit);
		}

		try {
			if (fragment != null) {
				fragment.startActivityForResult(intent, ID);
			} else {
				activity.startActivityForResult(intent, ID);
			}
 		} catch (ActivityNotFoundException e) {
			Toast.makeText(context, R.string.no_activity_for_mime_type, Toast.LENGTH_LONG).show();
		}
	}

	public static boolean getCameraFile(Activity activity, Fragment fragment, File cameraFile, int requestCode, FileService fileService, boolean preferInternal) {
		try {
			Intent cameraIntent;

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && preferInternal) {
				cameraIntent = new Intent(fragment != null ? fragment.getActivity() : activity, CameraActivity.class);
				cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraFile.getCanonicalPath());
				cameraIntent.putExtra(CameraActivity.EXTRA_NO_VIDEO, true);
			} else {
				cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
				cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, fileService.getShareFileUri(cameraFile, null));
				cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
			}

			if (fragment != null) {
				fragment.startActivityForResult(cameraIntent, requestCode);
			} else {
				activity.startActivityForResult(cameraIntent, requestCode);
			}
			return true;
		} catch (Exception e) {
			logger.error("Exception", e);
		}
		return false;
	}

	public static void forwardMessages(Context context, Class<?> targetActivity, List<AbstractMessageModel> messageModels) {
		Intent intent = new Intent(context, targetActivity);

		intent.setAction(ThreemaApplication.INTENT_ACTION_FORWARD);
		intent.putExtra(ThreemaApplication.INTENT_DATA_IS_FORWARD, true);
		IntentDataUtil.appendMultiple(messageModels, intent);

		context.startActivity(intent);
	}

	public static @NonNull ArrayList<Uri> getUrisFromResult(@NonNull Intent intent, ContentResolver contentResolver) {
		Uri returnData = intent.getData();
		ClipData clipData = null;
		ArrayList<Uri> uriList = new ArrayList<>();

		clipData = intent.getClipData();

		if (clipData != null && clipData.getItemCount() > 0) {
			for (int i = 0; i < clipData.getItemCount(); i++) {
				ClipData.Item clipItem = clipData.getItemAt(i);
				if (clipItem != null) {
					Uri uri = clipItem.getUri();
					if (uri != null) {
						if (ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(uri.getScheme())) {
							try {
								contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
							} catch (Exception e) {
								logger.error("Exception", e);
							}
						}
						uriList.add(uri);
					}
				}
			}
		} else {
			if (returnData != null) {
				if (ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(returnData.getScheme())) {
					try {
						contentResolver.takePersistableUriPermission(returnData, Intent.FLAG_GRANT_READ_URI_PERMISSION);
					} catch (Exception e) {
						logger.error("Exception", e);
					}
				}
				uriList.add(returnData);
			}
		}
		return validateUriList(uriList);
	}

	/**
	 * Check if selected files are located within the app's private directory
	 * @param uris Uris to check
	 * @return List of Uris not located in the private directory
	 */
	private static @NonNull ArrayList<Uri> validateUriList(ArrayList<Uri> uris) {
		String dataDir = Environment.getDataDirectory().toString();
		ArrayList<Uri> validatedUris = new ArrayList<>();

		if (uris != null && uris.size() > 0) {
			for (Uri uri : uris) {
				try {
					if (uri != null) {
						if (ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
							// Files from /data may not be sent if coming from a Picker
							final File f = new File(uri.getPath());
							final String filePath = f.getCanonicalPath();
							if (filePath.startsWith(dataDir)) {
								continue;
							}
						}
						validatedUris.add(uri);
					}
				} catch (Exception e) {
					//
				}
			}

			if (uris.size() != validatedUris.size()) {
				logger.debug("Error adding attachment");
				Toast.makeText(ThreemaApplication.getAppContext(), R.string.error_attaching_files, Toast.LENGTH_LONG).show();
			}
		}
		return validatedUris;
	}

	/**
	 * Get the mime type by looking at the filename's extension
	 * @param path filename or complete path of the file
	 * @return Mime Type or application/octet-stream if a mime type could not be determined from the extension
	 */
	@NonNull
	public static String getMimeTypeFromPath(@Nullable String path) {
		String mimeType = null;

		if (path != null) {
			String extension = MimeTypeMap.getFileExtensionFromUrl(path);
			if (TextUtils.isEmpty(extension)) {
				// getMimeTypeFromExtension() doesn't handle spaces in filenames nor can it handle
				// urlEncoded strings. Let's try one last time at finding the extension.
				int dotPos = path.lastIndexOf('.');
				if (0 <= dotPos) {
					extension = path.substring(dotPos + 1);
				}
			}
			if (!TextUtils.isEmpty(extension)) {
				mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
			}
			if (extension.equalsIgnoreCase("opus")) {
				// whatsapp ogg files
				mimeType = "audio/ogg";
			} else if (extension.equalsIgnoreCase("gpx")) {
				// https://issuetracker.google.com/issues/37120151
				mimeType = "application/gpx+xml";
			} else if (extension.equalsIgnoreCase("pkpass")) {
				mimeType = "application/vnd.apple.pkpass";
			}
		}
		if (TestUtil.empty(mimeType)) {
			return MimeUtil.MIME_TYPE_DEFAULT;
		}
		return mimeType;
	}

	@Nullable
	public static String getMimeTypeFromUri(@NonNull Context context, @Nullable Uri uri) {
		if (uri != null) {
			ContentResolver contentResolver = context.getContentResolver();
			String type = contentResolver.getType(uri);

			if (TestUtil.empty(type) || MimeUtil.MIME_TYPE_DEFAULT.equals(type)) {
//				path = FileUtil.getRealPathFromURI(context, uri);
				String filename = FileUtil.getFilenameFromUri(contentResolver, uri);

				return getMimeTypeFromPath(filename);
			}
			return type;
		}
		return null;
	}

	/*
	 * Check if Storage Access Framework is really available
	 */

	private static boolean isMediaProviderSupported(Context context) {
		final PackageManager pm = context.getPackageManager();
		// Pick up provider with action string
		final Intent i = new Intent(DocumentsContract.PROVIDER_INTERFACE);
		final List<ResolveInfo> providers = pm.queryIntentContentProviders(i, 0);
		for (ResolveInfo info : providers)
		{
			if(info != null && info.providerInfo != null)
			{
				final String authority = info.providerInfo.authority;
				if(isMediaDocument(Uri.parse(ContentResolver.SCHEME_CONTENT + "://" + authority)))
					return true;
			}
		}
		return false;
	}

	/*
	* Some content uri returned by systemUI file picker create intermittent permission problems
	* To fix this, we convert it in a file uri
	 */
	public static Uri getFixedContentUri(Context context, Uri inUri) {
		if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) {
			if (ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(inUri.getScheme()) && inUri.toString().toUpperCase().contains("%3A")) {
				String path = getRealPathFromURI(context, inUri);

				if (!TestUtil.empty(path)) {
					File file = new File(path);

					if (file.exists()) {
						return Uri.fromFile(file);
					}
				}
			}
		}
		return inUri;
	}

	public static String getRealPathFromURI(final Context context, final Uri uri) {
		// DocumentProvider
		if (DocumentsContract.isDocumentUri(context, uri)) {
			// ExternalStorageProvider
			if (isExternalStorageDocument(uri)) {
				final String docId = DocumentsContract.getDocumentId(uri);
				final String[] split = docId.split(":");
				final String type = split[0];

				if ("primary".equalsIgnoreCase(type)) {
					return Environment.getExternalStorageDirectory() + "/" + split[1];
				}
			}
			// DownloadsProvider
			else if (isDownloadsDocument(uri)) {
				final String id = DocumentsContract.getDocumentId(uri);

				if (id != null) {
					if (id.startsWith("raw:/")) {
						return id.substring(4);
					} else {
						try {
							final Uri contentUri = ContentUris.withAppendedId(
								Uri.parse(ContentResolver.SCHEME_CONTENT + "://downloads/public_downloads"), Long.parseLong(id));
							return getDataColumn(context, contentUri, null, null);
						} catch (NumberFormatException e) {
							logger.info("Unable to extract document ID. Giving up.");
						}
					}
				} else {
					logger.info("No document ID. Giving up.");
				}
			}
			// MediaProvider
			else if (isMediaDocument(uri)) {
				final String docId = DocumentsContract.getDocumentId(uri);
				final String[] split = docId.split(":");
				final String type = split[0];

				Uri contentUri = null;
				if ("image".equals(type)) {
					contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
				} else if ("video".equals(type)) {
					contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
				} else if ("audio".equals(type)) {
					contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
				}

				final String selection = "_id=?";
				final String[] selectionArgs = new String[]{
						split[1]
				};

				return getDataColumn(context, contentUri, selection, selectionArgs);
			}
			// MediaStore (and general)
		} else if (ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(uri.getScheme())) {
			// Return the remote address
			if (isGooglePhotosUri(uri))
				return uri.getLastPathSegment();

			return getDataColumn(context, uri, null, null);
		}
		// File
		else if (ContentResolver.SCHEME_FILE.equalsIgnoreCase(uri.getScheme())) {
			return uri.getPath();
		}
		return null;
	}

	private static boolean isExternalStorageDocument(Uri uri) {
		return "com.android.externalstorage.documents".equals(uri.getAuthority());
	}

	private static boolean isDownloadsDocument(Uri uri) {
		return "com.android.providers.downloads.documents".equals(uri.getAuthority());
	}

	private static boolean isMediaDocument(final Uri uri) {
		return "com.android.providers.media.documents".equals(uri.getAuthority());
	}

	private static boolean isGooglePhotosUri(Uri uri) {
		return "com.google.android.apps.photos.content".equals(uri.getAuthority());
	}

	private static String getDataColumn(Context context, Uri uri, String selection,
								 String[] selectionArgs) {

		String data = null;
		Cursor cursor = null;
		final String column = "_data";
		final String[] projection = {
				column
		};

		try {
			cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
					null);
			if (cursor != null && cursor.moveToFirst()) {
				final int column_index = cursor.getColumnIndexOrThrow(column);
				data = cursor.getString(column_index);
			}
		} catch (Exception e) {
			//
		} finally {
			if (cursor != null)
				cursor.close();
		}
		return data;
	}

	public static boolean isAnimGif(ContentResolver contentResolver, Uri uri) {
		if (uri == null) {
			return false;
		}

		byte[] buffer = new byte[4];
		try (InputStream is = contentResolver.openInputStream(uri)) {
			is.read(buffer);
			return isAnimGif(buffer);
		} catch (Exception x) {
			logger.error("Exception", x);
			return false;
		}
	}

	private static boolean isAnimGif(byte[] buffer) {

		return	buffer != null
				&& buffer.length >= 4
				&& (buffer[0] == 0x47 && buffer[1] == 0x49 &&
				buffer[2] == 0x46 && buffer[3] == 0x38);
	}

	public static boolean isImageFile(FileDataModel fileDataModel) {
		return fileDataModel != null && (MimeUtil.isImageFile(fileDataModel.getMimeType()));
	}

	public static boolean isVideoFile(FileDataModel fileDataModel) {
		return fileDataModel != null && (MimeUtil.isVideoFile(fileDataModel.getMimeType()));
	}

	public static boolean isAudioFile(FileDataModel fileDataModel) {
		return fileDataModel != null && (MimeUtil.isAudioFile(fileDataModel.getMimeType()));
	}

	public static String getFileMessageDatePrefix(Context context, AbstractMessageModel messageModel, String fileType) {
		if (messageModel.getFileData() == null || messageModel.getFileData().getFileSize() == 0) {
			return "";
		}

		if (messageModel.getFileData().isDownloaded()) {
			return "";
		}

		if (fileType != null) {
			String datePrefixString = Formatter.formatShortFileSize(context, messageModel.getFileData().getFileSize());

			if (messageModel.isOutbox()) {
				datePrefixString = fileType + " | " + datePrefixString;
			} else {
				datePrefixString += " | " + fileType;
			}
			return datePrefixString;
		} else {
			return Formatter.formatShortFileSize(context, messageModel.getFileData().getFileSize());
		}
	}

	public static @NonNull String getMediaFilenamePrefix(@NonNull AbstractMessageModel messageModel) {
		SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault());
		return "threema-" + format.format(messageModel.getCreatedAt()) + "-" + messageModel.getApiMessageId();
	}

	public static @NonNull String getMediaFilenamePrefix() {
		SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmssSSS", Locale.getDefault());
		return "threema-" + format.format(System.currentTimeMillis());
	}

	/**
	 * Return a default filename keeping in account specified mime type
	 * @param mimeType the mime type to generate a filename for
	 * @return a filename with an extension
	 */
	public static @NonNull String getDefaultFilename(@Nullable String mimeType) {
		if (TestUtil.empty(mimeType)) {
			mimeType = MimeUtil.MIME_TYPE_DEFAULT;
		}

		String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
		return getMediaFilenamePrefix() + "." + extension;
	}

	public static String sanitizeFileName(String filename) {
		if (!TestUtil.empty(filename)) {
			return filename.replaceAll("[:/*\"?|<>' ]", "_");
		}
		return null;
	}

	@WorkerThread
	public static boolean copyFile(@NonNull File source, @NonNull File dest) {
		try (InputStream  inputStream = new FileInputStream(source);
		     OutputStream outputStream = new FileOutputStream(dest))
		{
			IOUtils.copy(inputStream, outputStream);
			return true;
		} catch (Exception e) {
			logger.error("Exception", e);
		}
		return false;
	}

	@WorkerThread
	public static boolean copyFile(@NonNull Uri source, @NonNull File dest, @NonNull ContentResolver contentResolver) {
		try (InputStream  inputStream = contentResolver.openInputStream(source);
		     OutputStream outputStream = new FileOutputStream(dest))
		{
			if (inputStream != null) {
				IOUtils.copy(inputStream, outputStream);
				return true;
			}
		} catch (Exception e) {
			logger.error("Exception", e);
		}
		return false;
	}

	/**
	 * Attempt to delete a file. If deleting fails, log a warning using the specified logger.
	 *
	 * Note: Do not use this if error recovery is important!
	 *
	 * @param file The file that should be deleted
	 * @param description The description of the file (e.g. "message queue database")
	 * @param logger The logger to use
	 */
	public static void deleteFileOrWarn(
		@NonNull File file,
		@Nullable String description,
		@NonNull Logger logger
	) {
		if (!file.delete()) {
			logger.warn("Could not delete {}", description);
		}
	}

	/**
	 * See {@link #deleteFileOrWarn(File, String, Logger)}
	 */
	public static void deleteFileOrWarn(
		@NonNull String path,
		@Nullable String description,
		@NonNull Logger logger
	) {
		FileUtil.deleteFileOrWarn(new File(path), description, logger);
	}

	/**
	 * Create a new file or re-use existing file. Log if file already exists.
	 * @param file The file that should be created or re-used
	 * @param logger The logger facility to use
	 */
	public static void createNewFileOrLog(
		@NonNull File file,
		@NonNull Logger logger
	) throws IOException {
		if (!file.createNewFile()) {
			logger.debug("File {} already exists", file.getAbsolutePath());
		}
	}

	/**
	 * Try to generated a File with the given filename in the given path
	 * If a file of the same name exists, add a number to the filename (possibly between name and extension)
	 * @param destPath Destination path
	 * @param destFilename Desired filename
	 * @return File object
	 */
	public static File getUniqueFile(String destPath, String destFilename) {
		File destFile = new File(destPath, destFilename);

		String extension = MimeTypeMap.getFileExtensionFromUrl(destFilename);
		if (!TestUtil.empty(extension)) {
			extension = "." + extension;
		}
		String filePart = destFilename.substring(0, destFilename.length() - extension.length());

		int i = 0;
		while (destFile.exists()) {
			i++;
			destFile = new File(destPath, filePart + " (" + i + ")" + extension);
			if (!destFile.exists()) {
				break;
			}
		}

		return destFile;
	}

	/**
	 * Returns the filename of the object referred to by mediaItem. If no filename can be found, generate one
	 * @param contentResolver ContentResolver
	 * @param mediaItem MediaItem representing the source file
	 * @return A filename
	 */
	public static @NonNull String getFilenameFromUri(@NonNull ContentResolver contentResolver, @NonNull MediaItem mediaItem) {
		String filename = getFilenameFromUri(contentResolver, mediaItem.getUri());

		if (TextUtils.isEmpty(filename)) {
			filename = getDefaultFilename(mediaItem.getMimeType());
		}
		return filename;
	}

	/**
	 * Returns the filename of the object referred to by uri by querying the content resolver
	 * @param contentResolver ContentResolver
	 * @param uri Uri pointing at the object
	 * @return A filename or null if none is found
	 */
	@Nullable
	public static String getFilenameFromUri(ContentResolver contentResolver, Uri uri) {
		String filename = null;

		if (ContentResolver.SCHEME_FILE.equalsIgnoreCase(uri.getScheme())) {
			filename = uri.getLastPathSegment();
		} else {
			try (final Cursor cursor = contentResolver.query(uri, null, null, null, null)) {
				if (cursor != null && cursor.moveToNext()) {
					filename = cursor.getString(cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME));
				}
			} catch (IllegalStateException | SecurityException e) {
				logger.error("Unable to query Content Resolver", e);
			}
		}
		return filename;
	}
}

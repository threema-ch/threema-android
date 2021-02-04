/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
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

package ch.threema.app.services;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.AgeFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.preference.PreferenceManager;
import ch.threema.app.BuildConfig;
import ch.threema.app.NamedFileProvider;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.cache.ThumbnailCache;
import ch.threema.app.dialogs.CancelableHorizontalProgressDialog;
import ch.threema.app.listeners.AppIconListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.ui.SingleToast;
import ch.threema.app.utils.AndroidContactUtil;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ConfigUtils.AppTheme;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.IconUtil;
import ch.threema.app.utils.LogUtil;
import ch.threema.app.utils.MessageUtil;
import ch.threema.app.utils.MimeUtil;
import ch.threema.app.utils.RingtoneUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.SecureDeleteUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.ThreemaException;
import ch.threema.client.Base32;
import ch.threema.localcrypto.MasterKey;
import ch.threema.localcrypto.MasterKeyLockedException;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.MessageType;

import static android.provider.MediaStore.MEDIA_IGNORE_FILENAME;
import static ch.threema.app.services.MessageServiceImpl.THUMBNAIL_SIZE_PX;

public class FileServiceImpl implements FileService {
	private static final Logger logger = LoggerFactory.getLogger(FileServiceImpl.class);

	private final static String JPEG_EXTENSION = ".jpg";
	private final static String MPEG_EXTENSION = ".mp4";
	public final static String VOICEMESSAGE_EXTENSION = ".aac";
	private final static String THUMBNAIL_EXTENSION = "_T";
	private final static String WALLPAPER_FILENAME = "/wallpaper" + JPEG_EXTENSION;

	private static final String DIALOG_TAG_SAVING_MEDIA = "savingToGallery";

	private final Context context;
	private final MasterKey masterKey;
	private final PreferenceService preferenceService;
	private final File imagePath;
	private final File videoPath;
	private final File audioPath;
	private final File downloadsPath;
	private final File appDataPath;
	private final File backupPath;

	public FileServiceImpl(Context c, MasterKey masterKey, PreferenceService preferenceService) {
		this.context = c;
		this.preferenceService = preferenceService;
		this.masterKey = masterKey;

		String mediaPathPrefix = Environment.getExternalStorageDirectory() + "/" + BuildConfig.MEDIA_PATH + "/";

		// secondary storage directory for files that do not need any security enforced such as encrypted media
		this.appDataPath = new File(context.getExternalFilesDir(null), "data");
		getAppDataPath();

		// temporary file path used for sharing media from / with external applications (i.e. system camera) on older Android versions
		createNomediaFile(getExtTmpPath());

		this.imagePath = new File(mediaPathPrefix, "Threema Pictures");
		getImagePath();

		this.videoPath = new File(mediaPathPrefix + "Threema Videos");
		getVideoPath();

		this.audioPath = new File(mediaPathPrefix, "Threema Audio");
		getAudioPath();

		this.backupPath = new File(mediaPathPrefix, "Backups");
		getBackupPath();

		this.downloadsPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
		getDownloadsPath();

		// initialize ringtone
		if (needRingtonePreferencesUpdate(context.getContentResolver())) {
			preferenceService.setVoiceCallSound(RingtoneUtil.THREEMA_CALL_RINGTONE_URI);
		}
	}

	/*
	 * Check if current ringtone prefs point to a valid ringtone or if an update is needed
	 */
	private boolean needRingtonePreferencesUpdate(ContentResolver contentResolver) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.context);
		String uriString = sharedPreferences.getString(this.context.getString(R.string.preferences__voip_ringtone), null);

		// check if we need to update preferences to point to new file
		if (TestUtil.empty(uriString)) {
			// silent ringtone -> OK
			return false;
		} else if (!"null".equals(uriString)) {
			Uri oldUri = Uri.parse(uriString);
			if (oldUri.toString().equals("content://settings/system/ringtone")) {
				// default system ringtone -> OK
				return false;
			}

			String[] projection = {MediaStore.MediaColumns.DATA, MediaStore.Audio.Media.IS_RINGTONE};
			try (Cursor cursor = contentResolver.query(oldUri, projection, null, null, null)) {
				if (cursor != null) {
					if (cursor.moveToFirst()) {
						String path = cursor.getString(0);
						int isRingtone = cursor.getInt(1);
						// if preferences point to a valid file -> OK
						if (path != null && new File(path).exists() && isRingtone == 1) {
							return false;
						}
					}
				}
			} catch (Exception e) {
				// continue by resetting ringtone prefs
			}
		}
		return true;
	}

	@Override
	public File getBackupPath() {
		if (!this.backupPath.exists()) {
			this.backupPath.mkdirs();
		}
		return this.backupPath;
	}

	@Override
	public @NonNull Uri getBackupUri() {
		// check if backup path is overridden by user
		Uri backupUri = preferenceService.getDataBackupUri();
		if (backupUri != null) {
			return backupUri;
		}
		return Uri.fromFile(getBackupPath());
	}

	@Override
	public File getBlobDownloadPath() {
		File blobDownloadPath = new File(getAppDataPathAbsolute(), ".blob");

		if (blobDownloadPath.exists() && !blobDownloadPath.isDirectory()) {
			try {
				FileUtil.deleteFileOrWarn(blobDownloadPath, "Blob File", logger);
			} catch (SecurityException e) {
				logger.error("Exception", e);
			}
		}

		if (!blobDownloadPath.exists()) {
			try {
				blobDownloadPath.mkdirs();
			} catch (SecurityException e) {
				logger.error("Exception", e);
			}
		}
		return blobDownloadPath;
	}

	/**
	 * Get path where persistent app-specific data may be stored, that does not need any security enforced
	 * @return path
	 */
	@Override
	public File getAppDataPath() {
		if (!this.appDataPath.exists()) {
			this.appDataPath.mkdirs();
		}
		return this.appDataPath;
	}

	// TODO: Is this really necessary. According to documentation, paths returned by getExternalFilesDir() are already absolute
	private String getAppDataPathAbsolute() {
		return getAppDataPath().getAbsolutePath();
	}

	private File getImagePath() {
		if (!this.imagePath.exists()) {
			this.imagePath.mkdirs();
		}
		return this.imagePath;
	}

	private File getVideoPath() {
		if (!this.videoPath.exists()) {
			this.videoPath.mkdirs();
		}
		return this.videoPath;
	}

	private File getAudioPath() {
		if (!this.audioPath.exists()) {
			this.audioPath.mkdirs();
		}
		return this.audioPath;
	}

	private File getDownloadsPath() {
		try {
			if (!this.downloadsPath.exists()) {
				this.downloadsPath.mkdirs();
			} else if (!downloadsPath.isDirectory()) {
				FileUtil.deleteFileOrWarn(this.downloadsPath, "Download Path", logger);
				this.downloadsPath.mkdirs();
			}
		} catch (SecurityException e) {
			logger.error("Exception", e);
		}
		return this.downloadsPath;
	}

	@Override
	public File getWallpaperDirPath() {
		File wallpaperPath = new File(getAppDataPathAbsolute(), ".wallpaper");

		if (!wallpaperPath.exists()) {
			wallpaperPath.mkdirs();
		}
		return wallpaperPath;
	}

	@Override
	public File getAvatarDirPath() {
		File avatarPath = new File(getAppDataPathAbsolute(), ".avatar");

		if (!avatarPath.exists()) {
			avatarPath.mkdirs();
		}
		return avatarPath;
	}

	@Override
	public File getGroupAvatarDirPath() {
		File grpAvatarPath = new File(getAppDataPathAbsolute(), ".grp-avatar");

		if (!grpAvatarPath.exists()) {
			grpAvatarPath.mkdirs();
		}
		return grpAvatarPath;
	}

	@Override
	public String getGlobalWallpaperFilePath() {
		return getAppDataPathAbsolute() + WALLPAPER_FILENAME;
	}

	@Override
	public File getTempPath() {
		return context.getCacheDir();
	}

	@Override
	public File getExtTmpPath() {
		File extTmpPath = new File(context.getExternalFilesDir(null), "tmp");

		if (!extTmpPath.exists()) {
			extTmpPath.mkdirs();
		}
		return extTmpPath;
	}

	private void createNomediaFile(File directory) {
		if (directory.exists()) {
			File nomedia = new File(directory, MEDIA_IGNORE_FILENAME);
			if (!nomedia.exists()) {
				try {
					FileUtil.createNewFileOrLog(nomedia, logger);
				} catch (IOException e) {
					logger.error("Exception", e);
				}
			}
		}
	}

	@Override
	public File createTempFile(String prefix, String suffix) throws IOException {
		return createTempFile(prefix, suffix, false);
	}

	@Override
	public File createTempFile(String prefix, String suffix, boolean isPublic) throws IOException {
		return File.createTempFile(prefix, suffix, isPublic ? getExtTmpPath() : getTempPath());
	}

	@WorkerThread
	private void cleanDirectory(File path, final Runnable runAfter) {
		if (!path.isDirectory()) {
			if (path.delete()) {
				path.mkdirs();
			}
			return;
		}

		Date thresholdDate = new Date(System.currentTimeMillis() - (15 * DateUtils.MINUTE_IN_MILLIS));

		// this will crash if path is not a directory
		try {
			final Iterator<File> filesToDelete =
				FileUtils.iterateFiles(path, new AgeFileFilter(thresholdDate), TrueFileFilter.INSTANCE);

			if (filesToDelete != null && filesToDelete.hasNext()) {
				new Thread() {
					@Override
					public void run() {
						while (filesToDelete.hasNext()) {
							File file = filesToDelete.next();
							try {
								SecureDeleteUtil.secureDelete(file);
							} catch (IOException e) {
								logger.error("Exception", e);
								FileUtils.deleteQuietly(file);
							}
						}
						if (runAfter != null) {
							runAfter.run();
						}
					}
				}.start();
			}
		} catch (IllegalArgumentException e) {
			logger.error("Exception", e);
		}
	}

	@Override
	public void cleanTempDirs() {
		logger.debug("Cleaning temp files");

		cleanDirectory(getTempPath(), null);
		cleanDirectory(getExtTmpPath(), new Runnable() {
			@Override
			public void run() {
				createNomediaFile(getExtTmpPath());
			}
		});
	}

	@Override
	public String getWallpaperFilePath(MessageReceiver messageReceiver) {
		if (messageReceiver != null) {
			return getWallpaperFilePath(messageReceiver.getUniqueIdString());
		}
		return null;
	}

	@Override
	public String getWallpaperFilePath(String uniqueIdString) {
		if (!TextUtils.isEmpty(uniqueIdString)) {
			return getWallpaperDirPath() + "/.w-" + uniqueIdString + MEDIA_IGNORE_FILENAME;
		}
		return null;
	}

	@Override
	public File createWallpaperFile(MessageReceiver messageReceiver) throws IOException {
		File wallpaperFile;

		if (messageReceiver != null) {
			wallpaperFile = new File(getWallpaperFilePath(messageReceiver));
		} else {
			wallpaperFile = new File(getGlobalWallpaperFilePath());
		}

		if (!wallpaperFile.exists()) {
			FileUtil.createNewFileOrLog(wallpaperFile, logger);
		}
		return wallpaperFile;
	}

	@Override
	public boolean hasContactAvatarFile(ContactModel contactModel) {
		File avatar = getContactAvatarFile(contactModel);

		return avatar != null && avatar.exists();
	}

	@Override
	public boolean hasContactPhotoFile(ContactModel contactModel) {
		File avatar = getContactPhotoFile(contactModel);

		return avatar != null && avatar.exists();
	}

	private File getPictureFile(File path, String prefix, String identity) {
		try {
			MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
			messageDigest.update(("c-" + identity).getBytes());
			String filename = prefix + Base32.encode(messageDigest.digest()) + MEDIA_IGNORE_FILENAME;
			return new File(path, filename);
		} catch (NoSuchAlgorithmException e) {
			//
		}
		return null;
	}

	private File getContactAvatarFile(ContactModel contactModel) {
		return getPictureFile(getAvatarDirPath(), ".c-", contactModel.getIdentity());
	}

	private File getContactPhotoFile(ContactModel contactModel) {
		return getPictureFile(getAvatarDirPath(), ".p-", contactModel.getIdentity());
	}

	private File getAndroidContactAvatarFile(ContactModel contactModel) {
		return getPictureFile(getAvatarDirPath(), ".a-", contactModel.getIdentity());
	}

	@Override
	public boolean decryptFileToFile(File from, File to) {
		try (InputStream is = new FileInputStream(from); FileOutputStream fos = new FileOutputStream(to)) {
			int result = 0;

			try (CipherOutputStream cos = masterKey.getCipherOutputStream(fos)) {
				if (cos != null) {
					result = IOUtils.copy(is, cos);
				}
			}

			return (result > 0);
		} catch (Exception e) {
			logger.error("Exception", e);
		}
		return false;
	}

	public boolean removeMessageFiles(AbstractMessageModel messageModel, boolean withThumbnails) {
		boolean success = false;

		File messageFile = this.getMessageFile(messageModel);
		if(messageFile != null && messageFile.exists()) {
			if(messageFile.delete()) {
				success = true;
			}
		}

		if (withThumbnails) {
			File thumbnailFile = this.getMessageThumbnail(messageModel);
			if (thumbnailFile != null && thumbnailFile.exists() && thumbnailFile.delete()) {
				logger.debug("Thumbnail deleted");
			} else {
				logger.debug("No thumbnail to delete");
			}
		}
		return success;
	}

	public File getDecryptedMessageFile(AbstractMessageModel messageModel) throws Exception {
		String ext = getMediaFileExtension(messageModel);

		CipherInputStream is = null;
		FileOutputStream fos = null;
		try {
			is = getDecryptedMessageStream(messageModel);
			if (is != null) {
				File decoded = this.createTempFile(messageModel.getId() + "" + messageModel.getCreatedAt().getTime(), ext, !ConfigUtils.useContentUris());
				fos = new FileOutputStream(decoded);

				IOUtils.copy(is, fos);

				return decoded;
			}
		} finally {
			if (fos != null) {
				try {
					fos.close();
				} catch (IOException e) { /**/ }
			}
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) { /**/ }
			}
		}
		return null;
	}

	public File getDecryptedMessageFile(@NonNull AbstractMessageModel messageModel, @Nullable String filename) throws Exception {
		if (filename == null) {
			return getDecryptedMessageFile(messageModel);
		}

		InputStream is = getDecryptedMessageStream(messageModel);
		if (is != null) {
			FileOutputStream fos = null;
			try {
				File decrypted = new File(ConfigUtils.useContentUris() ? this.getTempPath() : this.getExtTmpPath(), messageModel.getApiMessageId() + "-" + filename);
				fos = new FileOutputStream(decrypted);

				IOUtils.copy(is, fos);
				return decrypted;
			} finally {
				if (fos != null) {
					try {
						fos.close();
					} catch (IOException e) { /**/ }
				}
				try {
					is.close();
				} catch (IOException e) { /**/ }
			}
		}
		return null;
	}

	@Override
	public CipherInputStream getDecryptedMessageStream(AbstractMessageModel messageModel) throws Exception {
		File file = this.getMessageFile(messageModel);
		if(file != null && file.exists()) {
			return masterKey.getCipherInputStream(new FileInputStream(file));
		}
		return null;
	}

	@Override
	public CipherInputStream getDecryptedMessageThumbnailStream(AbstractMessageModel messageModel) throws Exception {
		File thumbnailFile = this.getMessageThumbnail(messageModel);
		if (thumbnailFile != null && thumbnailFile.exists()) {
			return masterKey.getCipherInputStream(new FileInputStream(thumbnailFile));
		}
		return null;
	}

	/**
	 * return the file of a message file saved in the gallery (if exist)
	 */
	private File constructGalleryMediaFilename(AbstractMessageModel messageModel) {
		String title = FileUtil.getMediaFilenamePrefix(messageModel);

		switch (messageModel.getType()) {
			case IMAGE:
				return new File(getImagePath(), title + JPEG_EXTENSION);
			case VIDEO:
				return new File(getVideoPath(), title + MPEG_EXTENSION);
			case VOICEMESSAGE:
				return new File(getAudioPath(), title + VOICEMESSAGE_EXTENSION);
			case FILE:
				String filename = messageModel.getFileData().getFileName();
				if (TestUtil.empty(filename)) {
					filename = title + getMediaFileExtension(messageModel);
				}

				if (FileUtil.isImageFile(messageModel.getFileData())) {
					return new File(getImagePath(), filename);
				} if (FileUtil.isVideoFile(messageModel.getFileData())) {
					return new File(getVideoPath(), filename);
				} if (FileUtil.isAudioFile(messageModel.getFileData())) {
					return new File(getAudioPath(), filename);
				} else {
					return new File(getDownloadsPath(), filename);
				}
		}
		return null;
	}

	/**
	 * Returns the file name "extension" matching the provided message model
	 * In case of file messages, the provided mime type is used to guess a valid extension.
	 * If no mime type is found, as a last resort, the extension provided in the file's file name is used.
	 * @param messageModel
	 * @return The extension including a leading "." or null if no extension could be guessed
	 */
	private String getMediaFileExtension(AbstractMessageModel messageModel) {
		if (messageModel == null) {
			return null;
		}

		switch (messageModel.getType()) {
			case IMAGE:
				return JPEG_EXTENSION;
			case VIDEO:
				return MPEG_EXTENSION;
			case VOICEMESSAGE:
				return VOICEMESSAGE_EXTENSION;
			case FILE:
				String extension = null;
				try {
					extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(messageModel.getFileData().getMimeType());
				} catch (Exception e) {
					logger.error("Exception", e);
				}
				if (!TestUtil.empty(extension)) {
					return "." + extension;
				} else {
					if (messageModel.getFileData().getFileName() != null) {
						extension = MimeTypeMap.getFileExtensionFromUrl(messageModel.getFileData().getFileName());
						if (!TestUtil.empty(extension)) {
							return "." + extension;
						}
					}
					return null;
				}
			default:
				return null;
		}
	}

	private void copyMediaFileIntoPublicDirectory(InputStream inputStream, File destFile, String mimeType) throws Exception {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			String relativePath;
			Uri contentUri;
			if (MimeUtil.isAudioFile(mimeType)) {
				relativePath = Environment.DIRECTORY_MUSIC;
				contentUri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
			} else if (MimeUtil.isVideoFile(mimeType)) {
				relativePath = Environment.DIRECTORY_MOVIES;
				contentUri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
			} else if (MimeUtil.isImageFile(mimeType)) {
				relativePath = Environment.DIRECTORY_PICTURES;
				contentUri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
			} else if (MimeUtil.isPdfFile(mimeType)) {
				relativePath = Environment.DIRECTORY_DOCUMENTS;
				contentUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
			} else {
				relativePath = Environment.DIRECTORY_DOWNLOADS;
				contentUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
			}

			final ContentValues contentValues = new ContentValues();
			contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, destFile.getName());
			contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
			contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath + "/" + BuildConfig.MEDIA_PATH);
			contentValues.put(MediaStore.MediaColumns.IS_PENDING, true);

			Uri fileUri = context.getContentResolver().insert(contentUri, contentValues);

			if (fileUri != null) {
				try (OutputStream outputStream = context.getContentResolver().openOutputStream(fileUri)) {
					IOUtils.copy(inputStream, outputStream);
				}
				contentValues.clear();
				contentValues.put(MediaStore.MediaColumns.IS_PENDING, false);
				context.getContentResolver().update(fileUri, contentValues, null, null);
			} else {
				throw new Exception("Unable to open file");
			}
		} else {
			destFile = FileUtil.getUniqueFile(destFile.getParent(), destFile.getName());
			try (FileOutputStream outputStream = new FileOutputStream(destFile)) {
				IOUtils.copy(inputStream, outputStream);
				// let the system know, media store has changed
				context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(destFile)));
			}
		}
	}

	/**
	 * save the data of a message model into the gallery
	 */
	private void insertMessageIntoGallery(AbstractMessageModel messageModel) throws Exception {
		File mediaFile = this.constructGalleryMediaFilename(messageModel);
		if (mediaFile == null) {
			return;
		}

		File messageFile = this.getMessageFile(messageModel);
		if (!FileUtil.isFilePresent(messageFile)) {
			// fall back to thumbnail
			messageFile = this.getMessageThumbnail(messageModel);
		}

		if (FileUtil.isFilePresent(messageFile)) {
			try (CipherInputStream cis = masterKey.getCipherInputStream(new FileInputStream(messageFile))) {
				copyMediaFileIntoPublicDirectory(cis, mediaFile, MimeUtil.getMimeTypeFromMessageModel(messageModel));
			}
		} else {
			throw new ThreemaException("File not found.");
		}
	}

	@Override
	public void copyDecryptedFileIntoGallery(Uri sourceUri, AbstractMessageModel messageModel) throws Exception {
		InputStream inputStream;
		File mediaFile = this.constructGalleryMediaFilename(messageModel);
		if (mediaFile == null) {
			return;
		}

		ContentResolver cr = context.getContentResolver();
		inputStream = cr.openInputStream(sourceUri);
		if (inputStream != null) {
			copyMediaFileIntoPublicDirectory(inputStream, mediaFile, MimeUtil.getMimeTypeFromMessageModel(messageModel));
			inputStream.close();
		}
	}

	private String convert(String uid) {
		if (TestUtil.empty(uid)) {
			return uid;
		}
		return uid.replaceAll("[^a-zA-Z0-9\\\\s]", "");
	}

	private String getGroupAvatarFileName(GroupModel groupModel) {
		return ".grp-avatar-" + groupModel.getId();
	}

	private File getGroupAvatarFile(GroupModel groupModel) {
		// new in 3.0 - save group avatars in separate directory
		File avatarFile = new File(getGroupAvatarDirPath(), getGroupAvatarFileName(groupModel));
		if (avatarFile.exists() && avatarFile.isFile() && avatarFile.canRead()) {
			return avatarFile;
		}
		return new File(getAppDataPathAbsolute(), getGroupAvatarFileName(groupModel));
	}

	@Override
	public File getMessageFile(AbstractMessageModel messageModel) {
		String uid = this.convert(messageModel.getUid());
		if (TestUtil.empty(uid)) {
			return null;
		}
		return new File(getAppDataPathAbsolute(), "." + uid);
	}

	private File getMessageThumbnail(AbstractMessageModel messageModel) {
		// locations do not have a file, do not check for existing!
		if (messageModel == null) {
			return null;
		}

		String uid = this.convert(messageModel.getUid());
		if (TestUtil.empty(uid)) {
			return null;
		}

		return new File(getAppDataPathAbsolute(), "." + uid + THUMBNAIL_EXTENSION);
	}

	@Override
	public boolean writeConversationMedia(AbstractMessageModel messageModel, byte[] data) {
		return this.writeConversationMedia(messageModel, data, 0, data.length);
	}

	@Override
	public boolean writeConversationMedia(AbstractMessageModel messageModel, byte[] data, int pos, int length) {
		return this.writeConversationMedia(messageModel, data, pos, length, false);
	}

	@Override
	public boolean writeConversationMedia(AbstractMessageModel messageModel, byte[] data, int pos, int length, boolean overwrite) {
		boolean success = false;

		if (this.masterKey.isLocked()) {
			return false;
		}

		File messageFile = this.getMessageFile(messageModel);

		if(messageFile == null) {
			return false;
		}

		if (messageFile.exists()) {
			if(overwrite) {
				FileUtil.deleteFileOrWarn(messageFile, "writeConversationMedia", logger);
			}
			else {
				return false;
			}
		}

		try {
			if (messageFile.createNewFile()) {
				success = this.writeFile(data, pos, length, messageFile);
			}
		} catch (Exception e) {
			logger.error("Exception", e);
		}

		if (success) {
			//try to generate a thumbnail
			if (MessageUtil.autoGenerateThumbnail(messageModel)) {
				File f = this.getMessageThumbnail(messageModel);
				if (f != null && !f.exists()) {
					//load the data
					try {
						this.generateConversationMediaThumbnail(messageModel, data, pos, length);
					} catch (Exception e) {
						// unable to create thumbnail - ignore this
						logger.error("Exception", e);
					}
				}
			}
		}

		return success;
	}

	@Override
	public boolean writeGroupAvatar(GroupModel groupModel, byte[] photoData) throws Exception {
		return this.writeFile(photoData, new File(getGroupAvatarDirPath(), getGroupAvatarFileName(groupModel)));
	}

	@Override
	public InputStream getGroupAvatarStream(GroupModel groupModel) throws Exception {
		File f = this.getGroupAvatarFile(groupModel);
		if (f.exists()) {
			return masterKey.getCipherInputStream(new FileInputStream(f));
		}

		return null;
	}

	@Override
	public Bitmap getGroupAvatar(GroupModel groupModel) throws Exception {
		if (this.masterKey.isLocked()) {
			throw new Exception("no masterkey or locked");
		}

		return decryptBitmapFromFile(this.getGroupAvatarFile(groupModel));
	}

	@Override
	public void removeGroupAvatar(GroupModel groupModel) {
		File f = this.getGroupAvatarFile(groupModel);
		if (f.exists()) {
			FileUtil.deleteFileOrWarn(f, "removeGroupAvatar", logger);
		}
	}

	@Override
	public boolean hasGroupAvatarFile(GroupModel groupModel) {
		File f = this.getGroupAvatarFile(groupModel);

		return f.exists();
	}

	@Override
	public boolean writeContactAvatar(ContactModel contactModel, File file) throws Exception {
		return this.decryptFileToFile(file, this.getContactAvatarFile(contactModel));
	}

	@Override
	public boolean writeContactAvatar(ContactModel contactModel, byte[] avatarFile) throws Exception {
		return this.writeFile(avatarFile, this.getContactAvatarFile(contactModel));
	}

	@Override
	public boolean writeContactPhoto(ContactModel contactModel, byte[] encryptedBlob) throws Exception {
		return this.writeFile(encryptedBlob, this.getContactPhotoFile(contactModel));
	}

	@Override
	public boolean writeAndroidContactAvatar(ContactModel contactModel, byte[] avatarFile) throws Exception {
		return this.writeFile(avatarFile, this.getAndroidContactAvatarFile(contactModel));
	}

	@Override
	public Bitmap getContactAvatar(ContactModel contactModel) throws Exception {
		if (this.masterKey.isLocked()) {
			throw new Exception("no masterkey or locked");
		}

		return decryptBitmapFromFile(this.getContactAvatarFile(contactModel));
	}

	@Override
	public Bitmap getAndroidContactAvatar(ContactModel contactModel) throws Exception {
		if (this.masterKey.isLocked()) {
			throw new Exception("no masterkey or locked");
		}

		long now = System.currentTimeMillis();
		long expiration = contactModel.getAvatarExpires() != null ? contactModel.getAvatarExpires().getTime() : 0;
		if (expiration < now) {
			ServiceManager serviceManager = ThreemaApplication.getServiceManager();
			if (serviceManager != null) {
					if (AndroidContactUtil.getInstance().updateAvatarByAndroidContact(contactModel)) {
						ContactService contactService = serviceManager.getContactService();
						if (contactService != null) {
							contactService.save(contactModel);
						}
					}
			}
		}

		return decryptBitmapFromFile(this.getAndroidContactAvatarFile(contactModel));
	}

	@Override
	public InputStream getContactAvatarStream(ContactModel contactModel) throws IOException, MasterKeyLockedException {
		if (contactModel != null) {
			File f = this.getContactAvatarFile(contactModel);
			if (f != null && f.exists() && f.length() > 0) {
				return masterKey.getCipherInputStream(new FileInputStream(f));
			}
		}

		return null;
	}

	@Override
	public InputStream getContactPhotoStream(ContactModel contactModel) throws IOException, MasterKeyLockedException {
		if (contactModel != null) {
			File f = this.getContactPhotoFile(contactModel);
			if (f != null && f.exists() && f.length() > 0) {
				return masterKey.getCipherInputStream(new FileInputStream(f));
			}
		}
		return null;
	}

	@Override
	public Bitmap getContactPhoto(ContactModel contactModel) throws Exception {
		if (this.masterKey.isLocked()) {
			throw new Exception("no masterkey or locked");
		}

		if (this.preferenceService.getProfilePicReceive()) {
			return decryptBitmapFromFile(this.getContactPhotoFile(contactModel));
		}
		return null;
	}

	private Bitmap decryptBitmapFromFile(File file) throws Exception {
		if (file.exists()) {
			InputStream inputStream = masterKey.getCipherInputStream(new FileInputStream(file));
			if (inputStream != null) {
				try {
					return BitmapFactory.decodeStream(inputStream);
				} catch (Exception e) {
					logger.error("Exception", e);
				} finally {
					inputStream.close();
				}
			}
		}
		return null;
	}

	@Override
	public boolean removeContactAvatar(ContactModel contactModel) {
		File f = this.getContactAvatarFile(contactModel);
		return f != null && f.exists() && f.delete();
	}

	@Override
	public boolean removeContactPhoto(ContactModel contactModel) {
		File f = this.getContactPhotoFile(contactModel);
		return f != null && f.exists() && f.delete();
	}

	@Override
	public boolean removeAndroidContactAvatar(ContactModel contactModel) {
		File f = this.getAndroidContactAvatarFile(contactModel);
		return f != null && f.exists() && f.delete();
	}

	@Override
	public void removeAllAvatars() {
		try {
			FileUtils.cleanDirectory(getAvatarDirPath());
		} catch (IOException e) {
			logger.debug("Unable to empty avatar dir");
		}
	}

	private boolean writeFile(byte[] data, File file) throws Exception {
		if (data != null && data.length > 0) {
			try (FileOutputStream fileOutputStream = new FileOutputStream(file); CipherOutputStream cipherOutputStream = this.masterKey.getCipherOutputStream(fileOutputStream)) {
				cipherOutputStream.write(data);
				return true;
			} catch (FileNotFoundException e) {
				logger.error("Unable to save file to " + file.getAbsolutePath(), e);
				throw new FileNotFoundException(e.getMessage());
			}
		}
		return false;
	}

	private boolean writeFile(byte[] data, int pos, int length, File file) throws Exception {
		if (data != null && data.length > 0) {
			try (FileOutputStream fileOutputStream = new FileOutputStream(file); CipherOutputStream cipherOutputStream = this.masterKey.getCipherOutputStream(fileOutputStream)) {
				cipherOutputStream.write(data, pos, length);
				return true;
			} catch (OutOfMemoryError e) {
				throw new IOException("Out of memory");
			} catch (FileNotFoundException e) {
				logger.error("Unable to save file to " + file.getAbsolutePath(), e);
				throw new FileNotFoundException(e.getMessage());
			}
		}
		return false;
	}

	private void generateConversationMediaThumbnail(AbstractMessageModel messageModel, byte[] originalPicture, int pos, int length) throws Exception {
		if (this.masterKey.isLocked()) {
			throw new Exception("no masterkey or locked");
		}

		int preferredThumbnailWidth = ConfigUtils.getPreferredThumbnailWidth(context, false);
		int maxWidth = THUMBNAIL_SIZE_PX << 1;
		byte[] resizedThumbnailBytes = BitmapUtil.resizeBitmapByteArrayToMaxWidth(originalPicture, preferredThumbnailWidth > maxWidth ? maxWidth : preferredThumbnailWidth , pos, length);
		File thumbnailFile = this.getMessageThumbnail(messageModel);
		if (thumbnailFile != null) {
			FileUtil.createNewFileOrLog(thumbnailFile, logger);
			logger.info("Writing thumbnail...");
			this.writeFile(resizedThumbnailBytes, thumbnailFile);
		}
	}

	@Override
	public void writeConversationMediaThumbnail(AbstractMessageModel messageModel, byte[] originalPicture) throws Exception {
		if (originalPicture != null) {
			generateConversationMediaThumbnail(messageModel, originalPicture, 0, originalPicture.length);
		}
	}

	/**
	 * Return whether a thumbnail file exists for the specified message model.
	 */
	@Override
	public boolean hasMessageThumbnail(AbstractMessageModel messageModel) {
		return this.getMessageThumbnail(messageModel).exists();
	}

	@Override
	public Bitmap getMessageThumbnailBitmap(AbstractMessageModel messageModel,
	                                        @Nullable ThumbnailCache thumbnailCache) throws Exception {
		if (thumbnailCache != null) {
			Bitmap cached = thumbnailCache.get(messageModel.getId());
			if (cached != null && !cached.isRecycled()) {
				return cached;
			}
		}

		if (this.masterKey.isLocked()) {
			throw new Exception("no masterkey or locked");
		}

		// Open thumbnail file
		final File f = this.getMessageThumbnail(messageModel);

		Bitmap thumbnailBitmap = null;

		FileInputStream fis = null;
		try {
			try {
				fis = new FileInputStream(f);
			} catch (FileNotFoundException e) {
				return null;
			}

			// Get cipher input streams
			BufferedInputStream bis = null;
			try {
				CipherInputStream cis = masterKey.getCipherInputStream(fis);

				bis = new BufferedInputStream(cis);
				BitmapFactory.Options options = new BitmapFactory.Options();
				options.inJustDecodeBounds = false;

				Bitmap originalBitmap = null;
				try {
					originalBitmap = BitmapFactory.decodeStream(bis, null, options);
				} catch (OutOfMemoryError e) {
					logger.error("Exception", e);
				}

				if (originalBitmap != null) {
					try {
						thumbnailBitmap = BitmapUtil.resizeBitmapExactlyToMaxWidth(originalBitmap, THUMBNAIL_SIZE_PX);
					} catch (OutOfMemoryError e) {
						logger.error("Exception", e);
					}
				}
			} catch (Exception e) {
				throw e;
			} finally {
				if (bis != null) {
					try {
						bis.close();
					} catch (IOException e) { /**/ }
				}
			}
		} finally {
			if (fis != null) {
				try {
					fis.close();
				} catch (IOException e) { /**/ }
			}
		}

		if (thumbnailCache != null && thumbnailBitmap != null) {
			thumbnailCache.set(messageModel.getId(), thumbnailBitmap);
		}

		return thumbnailBitmap;
	}

	@Override
	public Bitmap getDefaultMessageThumbnailBitmap(Context context, AbstractMessageModel messageModel, ThumbnailCache thumbnailCache, String mimeType) {
		if (thumbnailCache != null) {
			Bitmap cached = thumbnailCache.get(messageModel.getId());
			if (cached != null && !cached.isRecycled()) {
				return cached;
			}
		}

		// supply fallback thumbnail based on mime type
		int icon = IconUtil.getMimeIcon(mimeType);

		Bitmap thumbnailBitmap = null;
		if (icon != 0) {
			thumbnailBitmap = BitmapUtil.getBitmapFromVectorDrawable(AppCompatResources.getDrawable(context, icon), Color.WHITE);
		}

		if (thumbnailBitmap != null && thumbnailCache != null) {
			thumbnailCache.set(messageModel.getId(), thumbnailBitmap);
		}
		return thumbnailBitmap;
	}

	@Override
	public void clearDirectory(File directory, boolean recursive) throws IOException, ThreemaException {
		// use SecureDeleteUtil.secureDelete() for a secure version of this
		if (directory.isDirectory()) {
			File[] children = directory.listFiles();
			if (children != null) {
				for (int i = 0; i < children.length; i++) {
					if (children[i].isDirectory()) {
						if (recursive) {
							this.clearDirectory(children[i], recursive);
						}
					} else {
						FileUtil.deleteFileOrWarn(children[i], "clearDirectory", logger);
					}
				}
			}
		}
	}

	@Override
	public boolean remove(File directory, boolean removeWithContent) throws IOException, ThreemaException {
		if (directory.isDirectory()) {
			if (!removeWithContent && directory.list().length > 0) {
				throw new ThreemaException("cannot remove directory. directory contains files");
			}

			for (File file : directory.listFiles()) {
				this.remove(file, removeWithContent);
			}
		}

		return directory.delete();
	}

	@Override
	@WorkerThread
	public File copyUriToTempFile(Uri uri, String prefix, String suffix, boolean isPublic) {
		try {
			File outputFile = createTempFile(prefix, suffix, isPublic);
			if (FileUtil.copyFile(uri, outputFile, context.getContentResolver())) {
				return outputFile;
			}
		} catch (Exception e) {
			logger.error("Exception", e);
		}
		return null;
	}

	@Override
	public Uri copyToShareFile(AbstractMessageModel messageModel, File srcFile) {
		// copy file to public dir
		if (messageModel != null) {
			if (srcFile != null && srcFile.exists()) {
				String destFilePrefix = FileUtil.getMediaFilenamePrefix(messageModel);
				String destFileExtension = getMediaFileExtension(messageModel);
				File destFile = copyUriToTempFile(Uri.fromFile(srcFile), destFilePrefix, destFileExtension, !ConfigUtils.useContentUris());

				String filename = null;
				if (messageModel.getType() == MessageType.FILE) {
					filename = messageModel.getFileData().getFileName();
				}

				return getShareFileUri(destFile, filename);
			}
		}
		return null;
	}

	/**
	 * Get an Uri for the destination file that can be shared to other apps. On Android 5+ our own content provider will be used to serve the file.
	 * @param destFile File to get an Uri for
	 * @param filename Desired filename for this file. Can be different from the filename of destFile
	 * @return Uri (Content Uri on Android 5+, File Uri otherwise)
	 */
	@Override
	public Uri getShareFileUri(@NonNull File destFile, @Nullable String filename) {
		if (destFile != null) {
			// see https://code.google.com/p/android/issues/detail?id=76683
			if (ConfigUtils.useContentUris()) {
				/* content uri */
				return NamedFileProvider.getUriForFile(ThreemaApplication.getAppContext(), ThreemaApplication.getAppContext().getPackageName() + ".fileprovider", destFile, filename);
			} else {
				/* file uri */
				return Uri.fromFile(destFile);
			}
		}
		return null;
	}

	@Override
	public long getInternalStorageUsage() {
		return getFolderSize(getAppDataPath());
	}

	private static long getFolderSize(File folderPath) {
		long totalSize = 0;

		if (folderPath == null) {
			return 0;
		}

		if (!folderPath.isDirectory()) {
			return 0;
		}

		File[] files = folderPath.listFiles();
		if(files != null){
			for (File file : files) {
				if (file.isFile()) {
					totalSize += file.length();
				} else if (file.isDirectory()) {
					totalSize += file.length();
					totalSize += getFolderSize(file);
				}
			}
		}
		return totalSize;
	}

	@Override
	public long getInternalStorageSize() {
		return getAppDataPath().getTotalSpace();
	}

	@Override
	public long getInternalStorageFree() {
		return getAppDataPath().getUsableSpace();
	}

	@WorkerThread
	public void loadDecryptedMessageFiles(final List<AbstractMessageModel> models, final OnDecryptedFilesComplete onDecryptedFilesComplete) {
		final ArrayList<Uri> shareFileUris = new ArrayList<>();

		int errorCount = 0;

		for (AbstractMessageModel model : models) {

			try {
				File file;
				if (model.getType() == MessageType.FILE && model.getFileData() != null) {
					file = getDecryptedMessageFile(model, model.getFileData().getFileName());
				} else {
					file = getDecryptedMessageFile(model);
				}

				if (file != null) {
					shareFileUris.add(getShareFileUri(file, null));
					continue;
				}
			} catch (Exception ignore) {
			}
			errorCount++;
			shareFileUris.add(null);
		}

		if (onDecryptedFilesComplete != null) {
			if (errorCount >= models.size()) {
				onDecryptedFilesComplete.error(context.getString(R.string.media_file_not_found));
			} else {
				// at least some of the provided media could be decrypted. we consider this a success
				onDecryptedFilesComplete.complete(shareFileUris);
			}
		}
	}

	/*
	 * Load encrypted media file, decrypt it and save it to a temporary file
	 */
	@MainThread
	public void loadDecryptedMessageFile(final AbstractMessageModel model, final OnDecryptedFileComplete onDecryptedFileComplete) {
		if (model.getType() == MessageType.TEXT || model.getType() == MessageType.BALLOT || model.getType() == MessageType.LOCATION) {
			onDecryptedFileComplete.complete(null);
			return;
		}

		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					//HACK: save to a readable temporary filename
					final File file;
					if(model.getType() == MessageType.FILE && model.getFileData() != null) {
						file = getDecryptedMessageFile(model, model.getFileData().getFileName());
					}
					else {
						file = getDecryptedMessageFile(model);
					}

					if (file != null) {
						if (onDecryptedFileComplete != null) {
							onDecryptedFileComplete.complete(file);
						}
					} else {
						throw new FileNotFoundException(context.getString(R.string.media_file_not_found));
					}
				} catch (Exception e) {
					if (onDecryptedFileComplete != null) {
						String message = e.getMessage();
						if (message != null && message.contains("ENOENT")) {
							message = context.getString(R.string.media_file_not_found);
						}
						onDecryptedFileComplete.error(message);
					}
				}
			}
		}).start();
	}

	@SuppressLint("StaticFieldLeak")
	@Override
	public void saveMedia(final AppCompatActivity activity, final View feedbackView, final CopyOnWriteArrayList<AbstractMessageModel> selectedMessages, final boolean quiet) {
		new AsyncTask<Void, Integer, Integer>() {
			boolean cancelled = false;

			@Override
			protected void onPreExecute() {
				if (activity != null && selectedMessages.size() > 3) {
					CancelableHorizontalProgressDialog dialog = CancelableHorizontalProgressDialog.newInstance(R.string.saving_media, 0, R.string.cancel, selectedMessages.size());
					dialog.setOnCancelListener(new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							cancelled = true;
						}
					});
					dialog.show(activity.getSupportFragmentManager(), DIALOG_TAG_SAVING_MEDIA);
				}
			}

			@Override
			protected Integer doInBackground(Void... params) {
				int i = 0, saved = 0;
				Iterator<AbstractMessageModel> checkedItemsIterator = selectedMessages.iterator();
				while (checkedItemsIterator.hasNext() && !cancelled) {
					publishProgress(i++);
					AbstractMessageModel messageModel = checkedItemsIterator.next();
					try {
						insertMessageIntoGallery(messageModel);
						saved++;
						logger.debug("Saved message " + messageModel.getUid());
					} catch (Exception e) {
						if (activity != null) {
							if (quiet) {
								RuntimeUtil.runOnUiThread(new Runnable() {
									@Override
									public void run() {
										SingleToast.getInstance().showShortText(activity.getString(R.string.error_saving_file));
									}
								});
							} else {
								LogUtil.exception(e, activity);
							}
						}
						logger.error("Exception", e);
					}
				}
				return saved;
			}

			@Override
			protected void onPostExecute(Integer saved) {
				if (activity != null) {
					DialogUtil.dismissDialog(activity.getSupportFragmentManager(), DIALOG_TAG_SAVING_MEDIA, true);
					if (feedbackView != null) {
						Snackbar.make(feedbackView, String.format(activity.getString(R.string.file_saved), saved), Snackbar.LENGTH_SHORT).show();
					} else {
						Toast.makeText(activity, String.format(activity.getString(R.string.file_saved), saved), Toast.LENGTH_LONG).show();
					}
				}
			}

			@Override
			protected void onProgressUpdate(Integer... index) {
				if (activity != null) {
					DialogUtil.updateProgress(activity.getSupportFragmentManager(), DIALOG_TAG_SAVING_MEDIA, index[0] + 1);
				}
			}
		}.execute();
	}

	@Override
	public void saveAppLogo(File logo, @AppTheme int theme) {
		File existingLogo = this.getAppLogo(theme);
		if(logo == null || !logo.exists()) {
			//remove existing icon
			if(existingLogo != null && existingLogo.exists()) {
				FileUtil.deleteFileOrWarn(existingLogo, "saveAppLogo", logger);
			}
		}
		else {
			FileUtil.copyFile(logo, existingLogo);
		}

		//call listener
		ListenerManager.appIconListeners.handle(AppIconListener::onChanged);
	}

	@Override
	public File getAppLogo(@AppTheme int theme) {
		String key = "light";

		if(theme == ConfigUtils.THEME_DARK) {
			key = "dark";
		}
		return new File(getAppDataPathAbsolute(),"appicon_" + key + ".png");
	}

}

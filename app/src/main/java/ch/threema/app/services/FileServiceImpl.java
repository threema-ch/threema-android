/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.AgeFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.slf4j.Logger;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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

import androidx.annotation.ColorInt;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
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
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.ui.SingleToast;
import ch.threema.app.utils.AndroidContactUtil;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.IconUtil;
import ch.threema.app.utils.LogUtil;
import ch.threema.app.utils.MessageUtil;
import ch.threema.app.utils.MimeUtil;
import ch.threema.app.utils.ResettableInputStream;
import ch.threema.app.utils.RingtoneChecker;
import ch.threema.app.utils.RingtoneUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.SecureDeleteUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.Base32;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.data.models.ContactModel;
import ch.threema.data.models.ContactModelData;
import ch.threema.data.models.GroupModel;
import ch.threema.localcrypto.MasterKey;
import ch.threema.localcrypto.exceptions.MasterKeyLockedException;
import ch.threema.localcrypto.MasterKeyProvider;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.MessageType;

import static android.provider.MediaStore.MEDIA_IGNORE_FILENAME;
import static ch.threema.app.services.MessageServiceImpl.THUMBNAIL_SIZE_PX;
import static ch.threema.app.utils.StreamUtilKt.orEmpty;

public class FileServiceImpl implements FileService {
    private static final Logger logger = LoggingUtil.getThreemaLogger("FileServiceImpl");

    private final static String JPEG_EXTENSION = ".jpg";
    public final static String MPEG_EXTENSION = ".mp4";
    public final static String VOICEMESSAGE_EXTENSION = ".aac";
    private final static String THUMBNAIL_EXTENSION = "_T";
    private final static String WALLPAPER_FILENAME = "/wallpaper" + JPEG_EXTENSION;

    private static final String DIALOG_TAG_SAVING_MEDIA = "savingToGallery";

    @NonNull
    private final Context context;
    @NonNull
    private final AppDirectoryProvider appDirectoryProvider;
    @NonNull
    private final MasterKeyProvider masterKeyProvider;
    @NonNull
    private final PreferenceService preferenceService;
    private final File imagePath;
    private final File videoPath;
    private final File audioPath;
    private final File downloadsPath;
    private final File backupPath;

    @NonNull
    private final AvatarCacheService avatarCacheService;

    public FileServiceImpl(
        @NonNull Context context,
        @NonNull AppDirectoryProvider appDirectoryProvider,
        @NonNull MasterKeyProvider masterKeyProvider,
        @NonNull PreferenceService preferenceService,
        @NonNull NotificationPreferenceService notificationPreferenceService,
        @NonNull AvatarCacheService avatarCacheService
    ) {
        this.context = context;
        this.appDirectoryProvider = appDirectoryProvider;
        this.preferenceService = preferenceService;
        this.masterKeyProvider = masterKeyProvider;
        this.avatarCacheService = avatarCacheService;

        String mediaPathPrefix = Environment.getExternalStorageDirectory() + "/" + BuildConfig.MEDIA_PATH + "/";

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

        if (!ConfigUtils.supportsNotificationChannels()) {
            updateLegacyRingtoneIfInvalid(context.getContentResolver(), notificationPreferenceService);
        }
    }

    @NonNull
    private MasterKey getMasterKey() throws MasterKeyLockedException {
        return masterKeyProvider.getMasterKey();
    }

    private static void updateLegacyRingtoneIfInvalid(
            @NonNull ContentResolver contentResolver,
            @NonNull NotificationPreferenceService notificationPreferenceService
    ) {
        @Nullable final Uri ringtone = notificationPreferenceService.getLegacyVoipCallRingtone();
        @Nullable final String uriString = ringtone != null ? ringtone.toString() : null;
        final RingtoneChecker ringtoneChecker = new RingtoneChecker(contentResolver);
        if (!ringtoneChecker.isValidRingtoneUri(uriString)) {
            notificationPreferenceService.setLegacyVoipCallRingtone(RingtoneUtil.THREEMA_CALL_RINGTONE_URI);
        }
    }

    @Deprecated
    @Override
    public File getBackupPath() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (!this.backupPath.exists()) {
                this.backupPath.mkdirs();
            }
        }
        return this.backupPath;
    }

    @Override
    public @Nullable Uri getBackupUri() {
        // check if backup path is overridden by user
        Uri backupUri = preferenceService.getDataBackupUri();
        if (backupUri != null) {
            return backupUri;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return null;
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
     *
     * @return path
     */
    @Override
    @NonNull
    public File getAppDataPath() {
        return appDirectoryProvider.getAppDataDirectory();
    }

    private String getAppDataPathAbsolute() {
        return getAppDataPath().getAbsolutePath();
    }

    @Deprecated
    private File getImagePath() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (!this.imagePath.exists()) {
                this.imagePath.mkdirs();
            }
        }
        return this.imagePath;
    }

    @Deprecated
    private File getVideoPath() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (!this.videoPath.exists()) {
                this.videoPath.mkdirs();
            }
        }
        return this.videoPath;
    }

    @Deprecated
    private File getAudioPath() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (!this.audioPath.exists()) {
                this.audioPath.mkdirs();
            }
        }
        return this.audioPath;
    }

    @Deprecated
    private File getDownloadsPath() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
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
    public File getIntTmpPath() {
        return appDirectoryProvider.getInternalTempDirectory();
    }

    @Override
    @NonNull
    public File getExtTmpPath() {
        return appDirectoryProvider.getExternalTempDirectory();
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
    private void cleanDirectory(@NonNull File path, @NonNull Date thresholdDate) {
        if (!path.isDirectory()) {
            if (path.delete()) {
                path.mkdirs();
            }
            return;
        }

        // this will crash if path is not a directory
        try {
            final Iterator<File> filesToDelete = FileUtils.iterateFiles(path, new AgeFileFilter(thresholdDate), TrueFileFilter.INSTANCE);

            if (filesToDelete != null && filesToDelete.hasNext()) {
                while (filesToDelete.hasNext()) {
                    File file = filesToDelete.next();
                    try {
                        SecureDeleteUtil.secureDelete(file);
                    } catch (IOException e) {
                        logger.error("Failed to delete file", e);
                        FileUtils.deleteQuietly(file);
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            logger.error("Exception", e);
        }
    }

    @Override
    @WorkerThread
    public void cleanTempDirs(long ageThresholdMillis) {
        logger.debug("Cleaning temp files");

        var thresholdDate = new Date(System.currentTimeMillis() - ageThresholdMillis);
        cleanDirectory(getTempPath(), thresholdDate);
        cleanDirectory(getIntTmpPath(), thresholdDate);
        cleanDirectory(getExtTmpPath(), thresholdDate);
        createNomediaFile(getExtTmpPath());
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
    public boolean hasUserDefinedProfilePicture(@NonNull String identity) {
        File profilePictureFile = getContactAvatarFile(identity);

        return profilePictureFile != null && profilePictureFile.exists();
    }

    @Override
    public boolean hasContactDefinedProfilePicture(@NonNull String identity) {
        File profilePictureFile = getContactPhotoFile(identity);

        return profilePictureFile != null && profilePictureFile.exists();
    }

    @Override
    public boolean hasAndroidDefinedProfilePicture(@NonNull String identity) {
        File profilePictureFile = getAndroidContactAvatarFile(identity);
        return profilePictureFile != null && profilePictureFile.exists();
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

    private File getContactAvatarFile(@NonNull String identity) {
        return getPictureFile(getAvatarDirPath(), ".c-", identity);
    }

    private File getContactPhotoFile(@NonNull String identity) {
        return getPictureFile(getAvatarDirPath(), ".p-", identity);
    }

    @Nullable
    private File getAndroidContactAvatarFile(@NonNull String identity) {
        return getPictureFile(getAvatarDirPath(), ".a-", identity);
    }

    @Override
    public boolean decryptFileToFile(File from, File to) {
        try (InputStream is = new FileInputStream(from); FileOutputStream fos = new FileOutputStream(to)) {
            try (CipherOutputStream cos = getMasterKey().getCipherOutputStream(fos)) {
                int result = IOUtils.copy(is, cos);
                return result > 0;
            }
        } catch (Exception e) {
            logger.error("Exception", e);
        }
        return false;
    }

    @Override
    public boolean removeMessageFiles(AbstractMessageModel messageModel, boolean withThumbnails) {
        boolean success = false;

        File messageFile = this.getMessageFile(messageModel);
        if (messageFile != null && messageFile.exists()) {
            if (messageFile.delete()) {
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

    @Override
    public File getDecryptedMessageFile(AbstractMessageModel messageModel) throws Exception {
        String ext = getMediaFileExtension(messageModel);

        CipherInputStream is = null;
        FileOutputStream fos = null;
        try {
            is = getDecryptedMessageStream(messageModel);
            if (is != null) {
                File decrypted = this.createTempFile(messageModel.getId() + "" + messageModel.getCreatedAt().getTime(), ext, false);
                fos = new FileOutputStream(decrypted);

                IOUtils.copy(is, fos);

                return decrypted;
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

    @Override
    public File getDecryptedMessageFile(@NonNull AbstractMessageModel messageModel, @Nullable String filename) throws Exception {
        if (filename == null) {
            return getDecryptedMessageFile(messageModel);
        }

        InputStream is = getDecryptedMessageStream(messageModel);
        if (is != null) {
            FileOutputStream fos = null;
            try {
                File decrypted = new File(this.getTempPath(), messageModel.getApiMessageId() + "-" + filename);
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
    @Nullable
    public CipherInputStream getDecryptedMessageStream(AbstractMessageModel messageModel) throws Exception {
        File file = this.getMessageFile(messageModel);
        if (file != null && file.exists()) {
            return getMasterKey().getCipherInputStream(new FileInputStream(file));
        }
        return null;
    }

    @Override
    public CipherInputStream getDecryptedMessageThumbnailStream(AbstractMessageModel messageModel) throws Exception {
        File thumbnailFile = this.getMessageThumbnail(messageModel);
        if (thumbnailFile != null && thumbnailFile.exists()) {
            return getMasterKey().getCipherInputStream(new FileInputStream(thumbnailFile));
        }
        return null;
    }

    /**
     * return the filename of a message file saved in the gallery (if exist)
     */
    @Nullable
    private String constructGalleryMediaFilename(AbstractMessageModel messageModel) {
        String title = FileUtil.getMediaFilenamePrefix(messageModel);

        switch (messageModel.getType()) {
            case IMAGE:
                return title + JPEG_EXTENSION;
            case VIDEO:
                return title + MPEG_EXTENSION;
            case VOICEMESSAGE:
                return title + VOICEMESSAGE_EXTENSION;
            case FILE:
                String filename = messageModel.getFileData().getFileName();
                if (TestUtil.isEmptyOrNull(filename)) {
                    filename = title + getMediaFileExtension(messageModel);
                }
                return filename;
            default:
                break;
        }
        return null;
    }

    /**
     * Returns the file name "extension" matching the provided message model
     * In case of file messages, the provided mime type is used to guess a valid extension.
     * If no mime type is found, as a last resort, the extension provided in the file's file name is used.
     *
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
                if (!TestUtil.isEmptyOrNull(extension) && !"bin".equals(extension)) {
                    return "." + extension;
                } else {
                    if (messageModel.getFileData().getFileName() != null) {
                        String guessedExtension = MimeTypeMap.getFileExtensionFromUrl(messageModel.getFileData().getFileName());
                        if (!TestUtil.isEmptyOrNull(guessedExtension)) {
                            return "." + guessedExtension;
                        }
                    }
                    if (!TestUtil.isEmptyOrNull(extension)) {
                        return "." + extension;
                    }
                    return null;
                }
            default:
                return null;
        }
    }

    private void copyMediaFileIntoPublicDirectory(InputStream inputStream, String filename, String mimeType) throws Exception {
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
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
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
                logger.error("Cannot open file '{}' with mime type '{}' at '{}/{}' for content uri '{}'",
                    filename,
                    mimeType,
                    relativePath,
                    BuildConfig.MEDIA_PATH,
                    contentUri
                );
                throw new Exception("Unable to open file");
            }
        } else {
            File destPath;
            if (MimeUtil.isAudioFile(mimeType)) {
                destPath = getAudioPath();
            } else if (MimeUtil.isVideoFile(mimeType)) {
                destPath = getVideoPath();
            } else if (MimeUtil.isImageFile(mimeType)) {
                destPath = getImagePath();
            } else if (MimeUtil.isPdfFile(mimeType)) {
                destPath = getDownloadsPath();
            } else {
                destPath = getDownloadsPath();
            }

            File destFile = new File(destPath, filename);
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
        String mediaFilename = this.constructGalleryMediaFilename(messageModel);
        if (mediaFilename == null) {
            return;
        }

        File messageFile = this.getMessageFile(messageModel);
        if (!FileUtil.isFilePresent(messageFile)) {
            // fall back to thumbnail
            messageFile = this.getMessageThumbnail(messageModel);
        }

        if (FileUtil.isFilePresent(messageFile)) {
            try (CipherInputStream cis = getMasterKey().getCipherInputStream(new FileInputStream(messageFile))) {
                copyMediaFileIntoPublicDirectory(cis, mediaFilename, MimeUtil.getMimeTypeFromMessageModel(messageModel));
            }
        } else {
            throw new ThreemaException("File not found.");
        }
    }

    @Override
    public void copyDecryptedFileIntoGallery(Uri sourceUri, AbstractMessageModel messageModel) throws Exception {
        String mediaFilename = this.constructGalleryMediaFilename(messageModel);
        if (mediaFilename == null) {
            return;
        }

        ContentResolver cr = context.getContentResolver();
        try (final InputStream inputStream = cr.openInputStream(sourceUri)) {
            if (inputStream != null) {
                copyMediaFileIntoPublicDirectory(inputStream, mediaFilename, MimeUtil.getMimeTypeFromMessageModel(messageModel));
            }
        }
    }

    private String convert(String uid) {
        if (TestUtil.isEmptyOrNull(uid)) {
            return uid;
        }
        return uid.replaceAll("[^a-zA-Z0-9\\\\s]", "");
    }

    private String getGroupAvatarFileName(long databaseId) {
        return ".grp-avatar-" + databaseId;
    }

    private File getGroupAvatarFile(long groupDatabaseId) {
        String fileName = getGroupAvatarFileName(groupDatabaseId);
        // new in 3.0 - save group avatars in separate directory
        File avatarFile = new File(getGroupAvatarDirPath(), fileName);
        if (avatarFile.exists() && avatarFile.isFile() && avatarFile.canRead()) {
            return avatarFile;
        }
        return new File(getAppDataPathAbsolute(), fileName);
    }

    @Override
    public File getMessageFile(AbstractMessageModel messageModel) {
        String uid = this.convert(messageModel.getUid());
        if (TestUtil.isEmptyOrNull(uid)) {
            return null;
        }
        return new File(getAppDataPathAbsolute(), "." + uid);
    }

    @Nullable
    private File getMessageThumbnail(@Nullable AbstractMessageModel messageModel) {
        // locations do not have a file, do not check for existing!
        if (messageModel == null) {
            return null;
        }

        String uid = this.convert(messageModel.getUid());
        if (TestUtil.isEmptyOrNull(uid)) {
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
        return writeConversationMedia(messageModel, new ByteArrayInputStream(data, pos, length), overwrite);
    }

    @Override
    public boolean writeConversationMedia(@NonNull AbstractMessageModel messageModel, @NonNull InputStream inputStream) {
        return writeConversationMedia(messageModel, inputStream, false);
    }

    @Override
    public boolean writeConversationMedia(
            @NonNull AbstractMessageModel messageModel,
            @NonNull InputStream inputStream,
            boolean overwrite
    ) {
        boolean success = false;

        if (masterKeyProvider.isLocked()) {
            return false;
        }

        File messageFile = this.getMessageFile(messageModel);

        if (messageFile == null) {
            return false;
        }

        if (messageFile.exists()) {
            if (overwrite) {
                FileUtil.deleteFileOrWarn(messageFile, "writeConversationMedia", logger);
            } else {
                return false;
            }
        }

        try {
            if (messageFile.createNewFile()) {
                success = writeFile(inputStream, messageFile);
            }
        } catch (Exception e) {
            logger.error("Exception while writing conversation media", e);
        }

        if (success) {
            // try to generate a thumbnail
            if (MessageUtil.autoGenerateThumbnail(messageModel)) {
                File thumbnailFile = this.getMessageThumbnail(messageModel);
                if (thumbnailFile != null && !thumbnailFile.exists()) {
                    try (
                        ResettableInputStream fileInputStream = new ResettableInputStream(
                            () -> {
                                try {
                                    return orEmpty(getDecryptedMessageStream(messageModel));
                                } catch (Exception e) {
                                    throw new IOException("Failed to create stream for thumbnail generation", e);
                                }
                            }
                        )
                    ) {
                        writeConversationMediaThumbnail(messageModel, fileInputStream);
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
    public boolean writeGroupAvatar(GroupModel groupModel, byte[] photoData) throws IOException, MasterKeyLockedException {
        return writeGroupAvatar(groupModel, new ByteArrayInputStream(photoData));
    }

    @Override
    public boolean writeGroupAvatar(GroupModel groupModel, InputStream photoData) throws IOException, MasterKeyLockedException {
        boolean success = this.writeFile(photoData, new File(getGroupAvatarDirPath(), getGroupAvatarFileName(groupModel.getDatabaseId())));
        if (success) {
            avatarCacheService.reset(groupModel.getGroupIdentity());
        }
        return success;
    }

    @Override
    @Deprecated
    public boolean writeGroupAvatar(ch.threema.storage.models.GroupModel groupModel, byte[] photoData) throws IOException, MasterKeyLockedException {
        boolean success = this.writeFile(photoData, new File(getGroupAvatarDirPath(), getGroupAvatarFileName(groupModel.getId())));
        if (success) {
            avatarCacheService.reset(groupModel);
        }
        return success;
    }

    @Override
    @Nullable
    public InputStream getGroupAvatarStream(GroupModel groupModel) throws IOException, MasterKeyLockedException {
        return getGroupAvatarStream(groupModel.getDatabaseId());
    }

    @Override
    @Nullable
    @Deprecated
    public InputStream getGroupAvatarStream(ch.threema.storage.models.GroupModel groupModel) throws Exception {
        return getGroupAvatarStream(groupModel.getId());
    }

    private InputStream getGroupAvatarStream(long groupDatabaseId) throws IOException, MasterKeyLockedException {
        File f = this.getGroupAvatarFile(groupDatabaseId);
        if (f.exists()) {
            return getMasterKey().getCipherInputStream(new FileInputStream(f));
        }

        return null;
    }

    @Override
    @Nullable
    public byte[] getGroupAvatarBytes(@NonNull GroupModel groupModel) throws Exception {
        InputStream inputStream = getGroupAvatarStream(groupModel);
        if (inputStream != null) {
            return IOUtils.toByteArray(inputStream);
        } else {
            return null;
        }
    }

    @Override
    @Nullable
    public Bitmap getGroupAvatar(GroupModel groupModel) throws IOException, MasterKeyLockedException {
        return getGroupAvatar(groupModel.getDatabaseId());
    }

    @Override
    @Nullable
    @Deprecated
    public Bitmap getGroupAvatar(ch.threema.storage.models.GroupModel groupModel) throws IOException, MasterKeyLockedException {
        return getGroupAvatar(groupModel.getId());
    }

    @Nullable
    private Bitmap getGroupAvatar(long groupDatabaseId) {
        return decryptBitmapFromFile(this.getGroupAvatarFile(groupDatabaseId));
    }

    @Override
    public void removeGroupAvatar(@NonNull GroupModel groupModel) {
        removeGroupAvatar(groupModel.getDatabaseId());
        avatarCacheService.reset(groupModel.getGroupIdentity());
    }

    @Override
    @Deprecated
    public void removeGroupAvatar(ch.threema.storage.models.GroupModel groupModel) {
        removeGroupAvatar(groupModel.getId());
        avatarCacheService.reset(groupModel);
    }

    private void removeGroupAvatar(long groupDatabaseId) {
        File f = this.getGroupAvatarFile(groupDatabaseId);
        if (f.exists()) {
            FileUtil.deleteFileOrWarn(f, "removeGroupAvatar", logger);
        }
    }

    @Override
    public boolean hasGroupAvatarFile(@NonNull GroupModel groupModel) {
        return getGroupAvatarFile(groupModel.getDatabaseId()).exists();
    }

    @Override
    @Deprecated
    public boolean hasGroupAvatarFile(@NonNull ch.threema.storage.models.GroupModel groupModel) {
        return getGroupAvatarFile(groupModel.getId()).exists();
    }

    @Override
    public boolean writeUserDefinedProfilePicture(@NonNull String identity, File file) {
        boolean success = this.decryptFileToFile(file, this.getContactAvatarFile(identity));
        if (success) {
            avatarCacheService.reset(identity);
        }
        return success;
    }

    @Override
    public boolean writeUserDefinedProfilePicture(@NonNull String identity, byte[] avatarFile) throws IOException, MasterKeyLockedException {
        return writeUserDefinedProfilePicture(identity, new ByteArrayInputStream(avatarFile));
    }

    @Override
    public boolean writeUserDefinedProfilePicture(@NonNull String identity, @NonNull InputStream avatar) throws IOException, MasterKeyLockedException {
        boolean success = this.writeFile(avatar, this.getContactAvatarFile(identity));
        if (success) {
            avatarCacheService.reset(identity);
        }
        return success;
    }

    @Override
    public boolean writeContactDefinedProfilePicture(@NonNull String identity, byte[] encryptedBlob) throws IOException, MasterKeyLockedException {
        return writeContactDefinedProfilePicture(identity, new ByteArrayInputStream(encryptedBlob));
    }

    @Override
    public boolean writeContactDefinedProfilePicture(@NonNull String identity, @NonNull InputStream encryptedBlob) throws IOException, MasterKeyLockedException {
        boolean success = this.writeFile(encryptedBlob, this.getContactPhotoFile(identity));
        if (success) {
            avatarCacheService.reset(identity);
        }
        return success;
    }

    @Override
    public void writeAndroidDefinedProfilePicture(@NonNull String identity, byte[] avatarFile) throws IOException, MasterKeyLockedException {
        boolean success = this.writeFile(avatarFile, this.getAndroidContactAvatarFile(identity));
        if (success) {
            avatarCacheService.reset(identity);
        }
    }

    @Override
    @Nullable
    public Bitmap getUserDefinedProfilePicture(@NonNull String identity) throws IOException, MasterKeyLockedException {
        return decryptBitmapFromFile(this.getContactAvatarFile(identity));
    }

    @Override
    @Nullable
    public Bitmap getAndroidDefinedProfilePicture(@NonNull ContactModel contactModel) {
        ContactModelData contactModelData = contactModel.getData();
        if (contactModelData == null) {
            logger.error("Contact model data is null");
            return null;
        }

        long now = System.currentTimeMillis();
        long expiration = contactModelData.localAvatarExpires != null ? contactModelData.localAvatarExpires.getTime() : 0;
        if (expiration < now) {
            ServiceManager serviceManager = ThreemaApplication.getServiceManager();
            if (serviceManager != null) {
                try {
                    AndroidContactUtil.getInstance().updateAvatarByAndroidContact(contactModel, context);
                } catch (SecurityException e) {
                    logger.error("Could not update avatar by android contact", e);
                }
            }
        }

        return decryptBitmapFromFile(this.getAndroidContactAvatarFile(contactModel.getIdentity()));
    }

    @Override
    @Nullable
    public InputStream getUserDefinedProfilePictureStream(@NonNull String identity) throws IOException, MasterKeyLockedException {
        File f = this.getContactAvatarFile(identity);
        if (f != null && f.exists() && f.length() > 0) {
            return getMasterKey().getCipherInputStream(new FileInputStream(f));
        }

        return null;
    }

    @Override
    @Nullable
    public InputStream getContactDefinedProfilePictureStream(@NonNull String identity) throws IOException, MasterKeyLockedException {
        File f = this.getContactPhotoFile(identity);
        if (f != null && f.exists() && f.length() > 0) {
            return getMasterKey().getCipherInputStream(new FileInputStream(f));
        }
        return null;
    }

    @Override
    @Nullable
    public Bitmap getContactDefinedProfilePicture(@NonNull String identity) {
        return decryptBitmapFromFile(this.getContactPhotoFile(identity));
    }

    @Nullable
    private Bitmap decryptBitmapFromFile(@Nullable File file) {
        if (file != null && file.exists()) {
            try (InputStream inputStream = getMasterKey().getCipherInputStream(new FileInputStream(file)))  {
                return BitmapFactory.decodeStream(inputStream);
            } catch (Exception e) {
                logger.error("Exception", e);
            }
        }
        return null;
    }

    @Override
    public boolean removeUserDefinedProfilePicture(@NonNull String identity) {
        File f = this.getContactAvatarFile(identity);
        boolean success = f != null && f.exists() && f.delete();
        if (success) {
            avatarCacheService.reset(identity);
        }
        return success;
    }

    @Override
    public boolean removeContactDefinedProfilePicture(@NonNull String identity) {
        File f = this.getContactPhotoFile(identity);
        boolean success = f != null && f.exists() && f.delete();
        if (success) {
            avatarCacheService.reset(identity);
        }
        return success;
    }

    @Override
    public boolean removeAndroidDefinedProfilePicture(@NonNull String identity) {
        File f = this.getAndroidContactAvatarFile(identity);
        boolean success = f != null && f.exists() && f.delete();
        if (success) {
            avatarCacheService.reset(identity);
        }
        return success;
    }

    @Override
    public void removeAllAvatars() {
        try {
            FileUtils.cleanDirectory(getAvatarDirPath());
        } catch (IOException e) {
            logger.debug("Unable to empty avatar dir");
        }
    }

    private boolean writeFile(@Nullable byte[] data, @Nullable File file) throws IOException, MasterKeyLockedException {
        if (data == null || data.length == 0 || file == null) {
            return false;
        }
        return writeFile(new ByteArrayInputStream(data), file);
    }

    private boolean writeFile(@NonNull InputStream inputStream, @NonNull File file) throws IOException, MasterKeyLockedException {
        try (
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            CipherOutputStream cipherOutputStream = getMasterKey().getCipherOutputStream(fileOutputStream)
        ) {
            IOUtils.copy(inputStream, cipherOutputStream);
            return true;
        } catch (OutOfMemoryError e) {
            throw new IOException("Out of memory", e);
        } catch (IOException e) {
            FileUtil.logExternalStorageState(file);
            throw e;
        }
    }

    @Override
    public void writeConversationMediaThumbnail(AbstractMessageModel messageModel, @NonNull byte[] originalPicture) throws Exception {
        writeConversationMediaThumbnail(messageModel, new ResettableInputStream(() -> new ByteArrayInputStream(originalPicture)));
    }

    @Override
    public void writeConversationMediaThumbnail(AbstractMessageModel messageModel, @NonNull ResettableInputStream thumbnail) throws Exception {
        if (masterKeyProvider.isLocked()) {
            throw new Exception("no masterkey or locked");
        }

        int preferredThumbnailWidth = ConfigUtils.getPreferredThumbnailWidth(context, false);
        int maxWidth = THUMBNAIL_SIZE_PX << 1;
        byte[] resizedThumbnailBytes = BitmapUtil.resizeImageToMaxWidth(thumbnail, Math.min(preferredThumbnailWidth, maxWidth));
        if (resizedThumbnailBytes == null) {
            throw new Exception("Unable to scale thumbnail");
        }

        saveThumbnail(messageModel, resizedThumbnailBytes);
    }

    @Override
    public void saveThumbnail(AbstractMessageModel messageModel, byte[] thumbnailBytes) throws Exception {
        saveThumbnail(messageModel, new ByteArrayInputStream(thumbnailBytes));
    }

    @Override
    public void saveThumbnail(AbstractMessageModel messageModel, @NonNull InputStream thumbnail) throws Exception {
        File thumbnailFile = this.getMessageThumbnail(messageModel);
        if (thumbnailFile != null) {
            FileUtil.createNewFileOrLog(thumbnailFile, logger);
            logger.info("Writing thumbnail...");
            this.writeFile(thumbnail, thumbnailFile);
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
    public @Nullable Bitmap getMessageThumbnailBitmap(
        AbstractMessageModel messageModel,
        @Nullable ThumbnailCache thumbnailCache
    ) throws Exception {
        if (thumbnailCache != null) {
            Bitmap cached = thumbnailCache.get(messageModel.getId());
            if (cached != null && !cached.isRecycled()) {
                return cached;
            }
        }

        if (masterKeyProvider.isLocked()) {
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
                CipherInputStream cis = getMasterKey().getCipherInputStream(fis);

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
    @WorkerThread
    public Bitmap getDefaultMessageThumbnailBitmap(Context context, AbstractMessageModel messageModel, ThumbnailCache thumbnailCache, String mimeType, boolean returnNullIfNotCached, @ColorInt int tintColor) {
        if (thumbnailCache != null) {
            Bitmap cached = thumbnailCache.get(messageModel.getId());
            if (cached != null && !cached.isRecycled()) {
                return cached;
            }
        }

        if (returnNullIfNotCached) {
            return null;
        }

        // supply fallback thumbnail based on mime type
        int icon = IconUtil.getMimeIcon(mimeType);

        Bitmap thumbnailBitmap = null;
        if (icon != 0) {
            thumbnailBitmap = BitmapUtil.getBitmapFromVectorDrawable(AppCompatResources.getDrawable(context, icon), tintColor);
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
                File destFile = copyUriToTempFile(Uri.fromFile(srcFile), destFilePrefix, destFileExtension, false);

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
     * Get an Uri for the destination file that can be shared to other apps. Our own content provider will be used to serve the file.
     *
     * @param destFile File to get an Uri for
     * @param filename Desired filename for this file. Can be different from the filename of destFile
     * @return Uri (Content Uri)
     */
    @Override
    public Uri getShareFileUri(@Nullable File destFile, @Nullable String filename) {
        if (destFile != null) {
            return NamedFileProvider.getShareFileUri(context, destFile, filename);
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
        if (files != null) {
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
    @Override
    public void loadDecryptedMessageFiles(final List<AbstractMessageModel> models, final OnDecryptedFilesComplete onDecryptedFilesComplete) {
        final ArrayList<Uri> shareFileUris = new ArrayList<>();

        int errorCount = 0;

        for (AbstractMessageModel model : models) {

            try {
                File file;
                if (model.getType() == MessageType.FILE) {
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
    @Override
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
                    if (model.getType() == MessageType.FILE && model.getFileData() != null) {
                        file = getDecryptedMessageFile(model, model.getFileData().getFileName());
                    } else {
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
    @RequiresPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    @Override
    public void saveMedia(final AppCompatActivity activity, final View feedbackView, final CopyOnWriteArrayList<AbstractMessageModel> selectedMessages, final boolean quiet) {
        new AsyncTask<Void, Integer, Integer>() {
            boolean cancelled = false;

            @Override
            protected void onPreExecute() {
                int selectedMessagesCount = selectedMessages.size();
                if (activity != null && selectedMessagesCount > 3) {
                    String title = String.format(ConfigUtils.getSafeQuantityString(activity, R.plurals.saving_media, selectedMessagesCount, selectedMessagesCount));
                    String cancel = activity.getString(R.string.cancel);
                    CancelableHorizontalProgressDialog dialog = CancelableHorizontalProgressDialog.newInstance(title, cancel, selectedMessagesCount);
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
                        Snackbar.make(feedbackView, String.format(ConfigUtils.getSafeQuantityString(activity, R.plurals.file_saved, saved, saved)), Snackbar.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(activity, String.format(ConfigUtils.getSafeQuantityString(activity, R.plurals.file_saved, saved, saved)), Toast.LENGTH_SHORT).show();
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
    public void saveAppLogo(@Nullable File logo, @ConfigUtils.AppThemeSetting String theme) {
        File existingLogo = this.getAppLogo(theme);
        if (logo == null || !logo.exists()) {
            //remove existing icon
            if (existingLogo.exists()) {
                FileUtil.deleteFileOrWarn(existingLogo, "saveAppLogo", logger);
            }
        } else {
            FileUtil.copyFile(logo, existingLogo);
        }

        //call listener
        ListenerManager.appIconListeners.handle(AppIconListener::onChanged);
    }

    @Override
    @NonNull
    public File getAppLogo(@ConfigUtils.AppThemeSetting String theme) {
        String key = "light";

        if (ConfigUtils.THEME_DARK.equals(theme)) {
            key = "dark";
        }
        return new File(getAppDataPath(), "appicon_" + key + ".png");
    }

    @Override
    @NonNull
    public Uri getTempShareFileUri(@NonNull Bitmap bitmap) throws IOException {
        File tempQrCodeFile = createTempFile(FileUtil.getMediaFilenamePrefix(), ".png");
        try (FileOutputStream fos = new FileOutputStream(tempQrCodeFile)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 0, bos);
            byte[] bitmapdata = bos.toByteArray();
            fos.write(bitmapdata);
        }
        return getShareFileUri(tempQrCodeFile, null);
    }

    @Override
    @Nullable
    @WorkerThread
    public Uri getThumbnailShareFileUri(AbstractMessageModel messageModel, int maxSize) {
        try {
            final File inputFile = getMessageThumbnail(messageModel);
            if (inputFile != null && inputFile.exists()) {
                String thumbnailMimeType = messageModel.getFileData().getThumbnailMimeType();
                if (thumbnailMimeType != null) {
                    String prefix = FileUtil.getMediaFilenamePrefix(messageModel);
                    final File outputFile = createTempFile(prefix, MimeUtil.MIME_TYPE_IMAGE_PNG.equals(thumbnailMimeType) ? ".png" : ".jpg", false);

                    try (CipherInputStream inputStream = getDecryptedMessageThumbnailStream(messageModel)) {
                        if (inputStream != null) {
                            try (OutputStream outputStream = context.getContentResolver().openOutputStream(Uri.fromFile(outputFile))) {
                                int numBytes = IOUtils.copy(inputStream, outputStream);
                                if (numBytes > 0 && numBytes <= maxSize) {
                                    return getShareFileUri(outputFile, messageModel.getFileData().getFileName());
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Exception fetching thumbnail", e);
        }

        logger.debug("Could not fetch thumbnail");
        return null;
    }
}

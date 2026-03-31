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
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import androidx.annotation.ColorInt;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.annotation.WorkerThread;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.fragment.app.FragmentActivity;
import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.cache.ThumbnailCache;
import ch.threema.app.dialogs.CancelableHorizontalProgressDialog;
import ch.threema.app.dialogs.SimpleStringAlertDialog;
import ch.threema.app.files.AppDirectoryProvider;
import ch.threema.app.files.AppLogoFileHandleProvider;
import ch.threema.app.files.MessageFileHandleProvider;
import ch.threema.app.services.avatarcache.AvatarCacheService;
import ch.threema.app.utils.ShareUtil;
import ch.threema.common.files.FileHandle;
import ch.threema.app.files.GroupProfilePictureFileHandleProvider;
import ch.threema.app.files.ProfilePictureFileHandleProvider;
import ch.threema.app.listeners.AppIconListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.ui.SingleToast;
import ch.threema.app.utils.AndroidContactUtil;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.IconUtil;
import ch.threema.app.utils.MessageUtil;
import ch.threema.app.utils.MimeUtil;
import ch.threema.app.utils.ResettableInputStream;
import ch.threema.app.utils.RingtoneChecker;
import ch.threema.app.utils.RingtoneUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.ThreemaException;

import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.data.models.ContactModel;
import ch.threema.data.models.ContactModelData;
import ch.threema.data.models.GroupIdentity;
import ch.threema.data.models.GroupModel;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.MessageType;

import static ch.threema.app.services.MessageServiceImpl.THUMBNAIL_SIZE_PX;
import static ch.threema.common.FileExtensionsKt.clearDirectoryNonRecursively;
import static ch.threema.common.FileExtensionsKt.clearDirectoryRecursively;
import static ch.threema.common.FileExtensionsKt.copyTo;
import static ch.threema.common.InputStreamExtensionsKt.orEmpty;
import static ch.threema.common.JavaCompat.copyStream;
import static kotlin.io.ByteStreamsKt.readBytes;

public class FileServiceImpl implements FileService {
    private static final Logger logger = getThreemaLogger("FileServiceImpl");

    private final static String JPEG_EXTENSION = ".jpg";
    public final static String MPEG_EXTENSION = ".mp4";
    public final static String VOICEMESSAGE_EXTENSION = ".aac";

    private static final String DIALOG_TAG_SAVING_MEDIA = "savingToGallery";

    @NonNull
    private final Context appContext;
    @NonNull
    private final AppDirectoryProvider appDirectoryProvider;
    @NonNull
    private final PreferenceService preferenceService;
    @NonNull
    private final AvatarCacheService avatarCacheService;
    @NonNull
    private final AppLogoFileHandleProvider appLogoFileHandleProvider;
    @NonNull
    private final MessageFileHandleProvider messageFileHandleProvider;
    @NonNull
    private final ProfilePictureFileHandleProvider profilePictureFileHandleProvider;
    @NonNull
    private final GroupProfilePictureFileHandleProvider groupProfilePictureFileHandleProvider;

    private final File imagePath;
    private final File videoPath;
    private final File audioPath;
    private final File downloadsPath;
    private final File backupPath;

    public FileServiceImpl(
        @NonNull Context appContext,
        @NonNull AppDirectoryProvider appDirectoryProvider,
        @NonNull PreferenceService preferenceService,
        @NonNull NotificationPreferenceService notificationPreferenceService,
        @NonNull AvatarCacheService avatarCacheService,
        @NonNull AppLogoFileHandleProvider appLogoFileHandleProvider,
        @NonNull MessageFileHandleProvider messageFileHandleProvider,
        @NonNull ProfilePictureFileHandleProvider profilePictureFileHandleProvider,
        @NonNull GroupProfilePictureFileHandleProvider groupProfilePictureFileHandleProvider
    ) {
        this.appContext = appContext;
        this.appDirectoryProvider = appDirectoryProvider;
        this.preferenceService = preferenceService;
        this.avatarCacheService = avatarCacheService;
        this.appLogoFileHandleProvider = appLogoFileHandleProvider;
        this.messageFileHandleProvider = messageFileHandleProvider;
        this.profilePictureFileHandleProvider = profilePictureFileHandleProvider;
        this.groupProfilePictureFileHandleProvider = groupProfilePictureFileHandleProvider;

        String mediaPathPrefix = Environment.getExternalStorageDirectory() + "/" + BuildConfig.MEDIA_PATH + "/";

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
            updateLegacyRingtoneIfInvalid(appContext.getContentResolver(), notificationPreferenceService);
        }
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
    public File getTempPath() {
        return appContext.getCacheDir();
    }

    @Override
    public File createTempFile(String prefix, String suffix) throws IOException {
        return File.createTempFile(prefix, suffix, getTempPath());
    }

    @Override
    public boolean hasUserDefinedProfilePicture(@NonNull String identity) {
        var fileHandle = profilePictureFileHandleProvider.getUserDefinedProfilePicture(identity);
        return fileHandle.exists();
    }

    @Override
    public boolean hasContactDefinedProfilePicture(@NonNull String identity) {
        var fileHandle = profilePictureFileHandleProvider.getContactDefinedProfilePicture(identity);
        return fileHandle.exists();
    }

    @Override
    public boolean hasAndroidDefinedProfilePicture(@NonNull String identity) {
        var fileHandle = profilePictureFileHandleProvider.getAndroidDefinedProfilePicture(identity);
        return fileHandle.exists();
    }

    @Override
    public boolean removeMessageFiles(@NonNull String messageUid, boolean withThumbnails) {
        boolean success = false;

        var fileHandle = messageFileHandleProvider.get(messageUid);
        if (fileHandle.exists()) {
            try {
                fileHandle.delete();
                success = true;
            } catch (IOException e) {
                logger.error("Failed to delete message file", e);
            }
        }

        if (withThumbnails) {
            var thumbnailFileHandle = messageFileHandleProvider.getThumbnail(messageUid);
            try {
                thumbnailFileHandle.delete();
            } catch (IOException e) {
                logger.error("Failed to delete message file thumbnail", e);
            }
        }
        return success;
    }

    @Override
    public File getDecryptedMessageFile(AbstractMessageModel messageModel) throws Exception {
        String ext = getMediaFileExtension(messageModel);

        InputStream is = null;
        FileOutputStream fos = null;
        try {
            is = getDecryptedMessageStream(messageModel);
            if (is != null) {
                File decrypted = this.createTempFile(messageModel.getId() + "" + messageModel.getCreatedAt().getTime(), ext);
                fos = new FileOutputStream(decrypted);

                copyStream(is, fos);

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

                copyStream(is, fos);
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
    public InputStream getDecryptedMessageStream(@NonNull String messageUid) throws Exception {
        var fileHandle = messageFileHandleProvider.get(messageUid);
        return fileHandle.read();
    }

    @Override
    public InputStream getDecryptedMessageThumbnailStream(@NonNull String messageUid) throws Exception {
        var fileHandle = messageFileHandleProvider.getThumbnail(messageUid);
        return fileHandle.read();
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

            Uri fileUri = appContext.getContentResolver().insert(contentUri, contentValues);

            if (fileUri != null) {
                try (OutputStream outputStream = appContext.getContentResolver().openOutputStream(fileUri)) {
                    copyStream(inputStream, outputStream);
                }
                contentValues.clear();
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, false);
                appContext.getContentResolver().update(fileUri, contentValues, null, null);
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
                copyStream(inputStream, outputStream);
                // let the system know, media store has changed
                appContext.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(destFile)));
            }
        }
    }

    private void insertMessageIntoGallery(AbstractMessageModel messageModel) throws Exception {
        String mediaFilename = this.constructGalleryMediaFilename(messageModel);
        if (mediaFilename == null) {
            return;
        }

        var messageUid = messageModel.getUid();
        if (messageUid == null) {
            return;
        }

        var fileHandle = messageFileHandleProvider.get(messageUid);
        if (!fileHandle.exists()) {
            // fall back to the thumbnail if the file itself does not exist
            fileHandle = messageFileHandleProvider.getThumbnail(messageUid);
        }

        if (!fileHandle.exists()) {
            throw new ThreemaException("File not found.");
        }

        try (InputStream inputStream = fileHandle.read()) {
            copyMediaFileIntoPublicDirectory(inputStream, mediaFilename, MimeUtil.getMimeTypeFromMessageModel(messageModel));
        }
    }

    @Override
    public void copyDecryptedFileIntoGallery(Uri sourceUri, AbstractMessageModel messageModel) throws Exception {
        String mediaFilename = this.constructGalleryMediaFilename(messageModel);
        if (mediaFilename == null) {
            return;
        }

        ContentResolver cr = appContext.getContentResolver();
        try (final InputStream inputStream = cr.openInputStream(sourceUri)) {
            if (inputStream != null) {
                copyMediaFileIntoPublicDirectory(inputStream, mediaFilename, MimeUtil.getMimeTypeFromMessageModel(messageModel));
            }
        }
    }

    @Override
    public boolean hasMessageFile(@NonNull String messageUid) {
        var fileHandle = messageFileHandleProvider.get(messageUid);
        return fileHandle.exists();
    }

    @Override
    public boolean writeConversationMedia(@NonNull AbstractMessageModel messageModel, @NonNull byte[] data) {
        return this.writeConversationMedia(messageModel, data, 0, data.length);
    }

    @Override
    public boolean writeConversationMedia(@NonNull AbstractMessageModel messageModel, @NonNull byte[] data, int pos, int length) {
        return this.writeConversationMedia(messageModel, data, pos, length, false);
    }

    @Override
    public boolean writeConversationMedia(@NonNull AbstractMessageModel messageModel, @NonNull byte[] data, int pos, int length, boolean overwrite) {
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
        var messageUid = messageModel.getUid();
        if (messageUid == null) {
            return false;
        }

        var fileHandle = messageFileHandleProvider.get(messageUid);

        if (fileHandle.exists()) {
            if (overwrite) {
                try {
                    fileHandle.delete();
                } catch (IOException e) {
                    logger.warn("Failed to delete message file before writing", e);
                }
            } else {
                return false;
            }
        }

        try {
            copyTo(inputStream, fileHandle);
        } catch (Exception e) {
            logger.error("Exception while writing conversation media", e);
            return false;
        }

        if (MessageUtil.autoGenerateThumbnail(messageModel)) {
            var thumbnailFileHandle = messageFileHandleProvider.getThumbnail(messageUid);
            if (!thumbnailFileHandle.exists()) {
                try (
                    ResettableInputStream imageInputStream = new ResettableInputStream(
                        () -> {
                            try {
                                return orEmpty(fileHandle.read());
                            } catch (Exception e) {
                                throw new IOException("Failed to create stream for thumbnail generation", e);
                            }
                        }
                    )
                ) {
                    writeConversationMediaThumbnail(messageModel, imageInputStream);
                } catch (Exception e) {
                    logger.error("Failed to generate thumbnail", e);
                }
            }
        }

        return true;
    }

    @Override
    public void writeGroupProfilePicture(@NonNull GroupIdentity groupIdentity, long databaseId, @NonNull InputStream data) throws IOException {
        var fileHandle = groupProfilePictureFileHandleProvider.get(databaseId);
        copyTo(data, fileHandle);
        avatarCacheService.reset(groupIdentity);
    }

    @Override
    @Nullable
    public InputStream getGroupProfilePictureStream(long groupDatabaseId) throws IOException {
        return groupProfilePictureFileHandleProvider.get(groupDatabaseId).read();
    }

    @Override
    @Nullable
    public byte[] getGroupProfilePictureBytes(@NonNull GroupModel groupModel) throws Exception {
        try (InputStream inputStream = getGroupProfilePictureStream(groupModel)) {
            if (inputStream != null) {
                return readBytes(inputStream);
            } else {
                return null;
            }
        }
    }

    @Override
    @Nullable
    public Bitmap getGroupProfilePictureBitmap(long databaseId) {
        var fileHandle = groupProfilePictureFileHandleProvider.get(databaseId);
        return decodeBitmap(fileHandle);
    }

    @Override
    public void removeGroupProfilePicture(GroupIdentity groupIdentity, long databaseId) {
        var fileHandle = groupProfilePictureFileHandleProvider.get(databaseId);
        try {
            fileHandle.delete();
            avatarCacheService.reset(groupIdentity);
        } catch (IOException e) {
            logger.error("Failed to delete group profile picture", e);
        }
    }

    @Override
    public boolean hasGroupProfilePicture(@NonNull GroupModel groupModel) {
        return hasGroupProfilePicture(groupModel.getDatabaseId());
    }

    @Override
    public boolean hasGroupProfilePicture(long databaseId) {
        var fileHandle = groupProfilePictureFileHandleProvider.get(databaseId);
        return fileHandle.exists();
    }

    @Override
    public boolean writeUserDefinedProfilePicture(@NonNull String identity, File file) {
        var fileHandle = profilePictureFileHandleProvider.getUserDefinedProfilePicture(identity);
        try {
            copyTo(file, fileHandle);
        } catch (Exception e) {
            logger.error("Failed to write user defined profile picture", e);
            return false;
        }
        avatarCacheService.reset(identity);
        return true;
    }

    @Override
    public void writeUserDefinedProfilePicture(@NonNull String identity, @NonNull InputStream avatar) throws IOException {
        var fileHandle = profilePictureFileHandleProvider.getUserDefinedProfilePicture(identity);
        copyTo(avatar, fileHandle);
        avatarCacheService.reset(identity);
    }

    @Override
    public void writeContactDefinedProfilePicture(@NonNull String identity, @NonNull InputStream imageData) throws IOException {
        var fileHandle = profilePictureFileHandleProvider.getContactDefinedProfilePicture(identity);
        copyTo(imageData, fileHandle);
        avatarCacheService.reset(identity);
    }

    @Override
    public void writeAndroidDefinedProfilePicture(@NonNull String identity, byte[] imageData) throws IOException {
        var fileHandle = profilePictureFileHandleProvider.getAndroidDefinedProfilePicture(identity);
        copyTo(new ByteArrayInputStream(imageData), fileHandle);
        avatarCacheService.reset(identity);
    }

    @Override
    @Nullable
    public Bitmap getUserDefinedProfilePicture(@NonNull String identity) {
        var fileHandle = profilePictureFileHandleProvider.getUserDefinedProfilePicture(identity);
        return decodeBitmap(fileHandle);
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
                    AndroidContactUtil.getInstance().updateAvatarByAndroidContact(contactModel, appContext);
                } catch (SecurityException e) {
                    logger.error("Could not update avatar by android contact", e);
                }
            }
        }

        var fileHandle = profilePictureFileHandleProvider.getAndroidDefinedProfilePicture(contactModel.getIdentity());
        return decodeBitmap(fileHandle);
    }

    @Override
    @Nullable
    public InputStream getUserDefinedProfilePictureStream(@NonNull String identity) throws IOException {
        var fileHandle = profilePictureFileHandleProvider.getUserDefinedProfilePicture(identity);
        return fileHandle.read();
    }

    @Override
    @Nullable
    public InputStream getContactDefinedProfilePictureStream(@NonNull String identity) throws IOException {
        var fileHandle = profilePictureFileHandleProvider.getContactDefinedProfilePicture(identity);
        return fileHandle.read();
    }

    @Override
    @Nullable
    public Bitmap getContactDefinedProfilePicture(@NonNull String identity) {
        var fileHandle = profilePictureFileHandleProvider.getContactDefinedProfilePicture(identity);
        return decodeBitmap(fileHandle);
    }

    @Nullable
    private Bitmap decodeBitmap(@NonNull FileHandle fileHandle) {
        try (InputStream inputStream = fileHandle.read()) {
            if (inputStream == null) {
                return null;
            }
            return BitmapFactory.decodeStream(inputStream);
        } catch (Exception e) {
            logger.error("Failed to decode bitmap", e);
        }
        return null;
    }

    @Override
    public boolean removeUserDefinedProfilePicture(@NonNull String identity) {
        var fileHandle = profilePictureFileHandleProvider.getUserDefinedProfilePicture(identity);
        try {
            fileHandle.delete();
            avatarCacheService.reset(identity);
            return true;
        } catch (IOException e) {
            logger.error("Failed to delete user defined profile picture", e);
            return false;
        }
    }

    @Override
    public void removeContactDefinedProfilePicture(@NonNull String identity) {
        var fileHandle = profilePictureFileHandleProvider.getContactDefinedProfilePicture(identity);
        try {
            fileHandle.delete();
            avatarCacheService.reset(identity);
        } catch (IOException e) {
            logger.error("Failed to delete contact defined profile picture", e);
        }
    }

    @Override
    public boolean removeAndroidDefinedProfilePicture(@NonNull String identity) {
        var fileHandle = profilePictureFileHandleProvider.getAndroidDefinedProfilePicture(identity);
        if (!fileHandle.exists()) {
            return false;
        }
        try {
            fileHandle.delete();
            avatarCacheService.reset(identity);
            return true;
        } catch (IOException e) {
            logger.error("Failed to delete android defined profile picture", e);
            return false;
        }
    }

    @Override
    public void removeAllAvatars() {
        try {
            profilePictureFileHandleProvider.deleteAll();
        } catch (IOException e) {
            logger.debug("Failed to delete group profile pictures", e);
        }
    }

    @Override
    public void writeConversationMediaThumbnail(AbstractMessageModel messageModel, @NonNull byte[] originalPicture) throws Exception {
        writeConversationMediaThumbnail(messageModel, new ResettableInputStream(() -> new ByteArrayInputStream(originalPicture)));
    }

    @Override
    public void writeConversationMediaThumbnail(AbstractMessageModel messageModel, @NonNull ResettableInputStream thumbnail) throws Exception {
        int preferredThumbnailWidth = ConfigUtils.getPreferredThumbnailWidth(appContext, false);
        int maxWidth = THUMBNAIL_SIZE_PX << 1;
        byte[] resizedThumbnailBytes = BitmapUtil.resizeImageToMaxWidth(thumbnail, Math.min(preferredThumbnailWidth, maxWidth));
        if (resizedThumbnailBytes == null) {
            throw new Exception("Unable to scale thumbnail");
        }

        saveThumbnail(messageModel, resizedThumbnailBytes);
    }

    @Override
    public void saveThumbnail(@NonNull String messageUid, @NonNull InputStream thumbnail) throws Exception {
        var fileHandle = messageFileHandleProvider.getThumbnail(messageUid);
        copyTo(thumbnail, fileHandle);
    }

    /**
     * Return whether a thumbnail file exists for the specified message model.
     */
    @Override
    public boolean hasMessageThumbnail(@NonNull String messageUid) {
        return messageFileHandleProvider.getThumbnail(messageUid).exists();
    }

    @Override
    public @Nullable Bitmap getMessageThumbnailBitmap(
        @Nullable AbstractMessageModel messageModel,
        @Nullable ThumbnailCache thumbnailCache
    ) throws Exception {
        if (messageModel == null) {
            return null;
        }

        if (thumbnailCache != null) {
            Bitmap cached = thumbnailCache.get(messageModel.getId());
            if (cached != null && !cached.isRecycled()) {
                return cached;
            }
        }

        var messageUid = messageModel.getUid();
        if (messageUid == null) {
            return null;
        }

        var fileHandle = messageFileHandleProvider.getThumbnail(messageUid);
        var originalBitmap = decodeBitmap(fileHandle);
        if (originalBitmap == null) {
            return null;
        }

        try {
            var thumbnailBitmap = BitmapUtil.resizeBitmapExactlyToMaxWidth(originalBitmap, THUMBNAIL_SIZE_PX);
            try {
                if (thumbnailCache != null) {
                    thumbnailCache.set(messageModel.getId(), thumbnailBitmap);
                }
            } finally {
                if (originalBitmap != thumbnailBitmap) {
                    originalBitmap.recycle();
                }
            }
            return thumbnailBitmap;
        } catch (Exception e) {
            logger.error("Failed to resize thumbnail", e);
        }
        return null;
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
    public void deleteMediaFiles() {
        clearDirectoryRecursively(appDirectoryProvider.getUserFilesDirectory());
        clearDirectoryNonRecursively(appDirectoryProvider.getLegacyUserFilesDirectory());
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
    public File copyUriToTempFile(Uri uri, String prefix, String suffix) {
        try {
            File outputFile = createTempFile(prefix, suffix);
            if (FileUtil.copyFile(uri, outputFile, appContext.getContentResolver())) {
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
                File destFile = copyUriToTempFile(Uri.fromFile(srcFile), destFilePrefix, destFileExtension);

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
            return ShareUtil.getShareFileUri(appContext, destFile, filename);
        }
        return null;
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
                onDecryptedFilesComplete.error(appContext.getString(R.string.media_file_not_found));
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
                    if (model.getType() == MessageType.FILE) {
                        file = getDecryptedMessageFile(model, model.getFileData().getFileName());
                    } else {
                        file = getDecryptedMessageFile(model);
                    }

                    if (file != null) {
                        if (onDecryptedFileComplete != null) {
                            onDecryptedFileComplete.complete(file);
                        }
                    } else {
                        throw new FileNotFoundException(appContext.getString(R.string.media_file_not_found));
                    }
                } catch (Exception e) {
                    if (onDecryptedFileComplete != null) {
                        // TODO(ANDR-4472): The `error` method should always receive an exception, never a localized or hard-coded string
                        String message = e.getMessage();
                        if (message != null && message.contains("ENOENT")) {
                            message = appContext.getString(R.string.media_file_not_found);
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
    public void saveMedia(final FragmentActivity activity, final View feedbackView, final CopyOnWriteArrayList<AbstractMessageModel> selectedMessages, final boolean quiet) {
        var activityWeakReference = new WeakReference<>(activity);
        new AsyncTask<Void, Integer, Integer>() {
            boolean cancelled = false;

            @Override
            protected void onPreExecute() {
                int selectedMessagesCount = selectedMessages.size();
                var activity = activityWeakReference.get();
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
                        logger.debug("Saved message {}", messageModel.getUid());
                    } catch (Exception e) {
                        var activity = activityWeakReference.get();
                        if (activity != null) {
                            var message = activity.getString(R.string.error_saving_file);
                            if (quiet) {
                                RuntimeUtil.runOnUiThread(() -> SingleToast.getInstance().showShortText(message));
                            } else if (!activity.isFinishing()) {
                                SimpleStringAlertDialog.newInstance(R.string.whoaaa, message)
                                    .show(activity.getSupportFragmentManager(), "tex");
                            }
                        }
                        logger.error("Failed to save media", e);
                    }
                }
                return saved;
            }

            @Override
            protected void onPostExecute(Integer saved) {
                var activity = activityWeakReference.get();
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
        var fileHandle = getAppLogoFileHandle(theme);
        if (logo == null) {
            try {
                fileHandle.delete();
            } catch (IOException e) {
                logger.error("Failed to delete app logo", e);
            }
        } else {
            try {
                copyTo(logo, fileHandle);
            } catch (IOException e) {
                logger.error("Failed to store app logo", e);
            }
        }
        ListenerManager.appIconListeners.handle(AppIconListener::onChanged);
    }

    @Override
    @Nullable
    public Bitmap getAppLogo(@ConfigUtils.AppThemeSetting String theme) {
        var fileHandle = getAppLogoFileHandle(theme);
        return decodeBitmap(fileHandle);
    }

    @NonNull
    private FileHandle getAppLogoFileHandle(@ConfigUtils.AppThemeSetting String theme) {
        var logoTheme = ConfigUtils.THEME_DARK.equals(theme)
            ? AppLogoFileHandleProvider.Theme.DARK
            : AppLogoFileHandleProvider.Theme.LIGHT;
        return appLogoFileHandleProvider.get(logoTheme);
    }

    @Override
    @Nullable
    @WorkerThread
    public Uri getThumbnailShareFileUri(@NonNull AbstractMessageModel messageModel, int maxSize) {
        var messageUid = messageModel.getUid();
        if (messageUid == null) {
            return null;
        }
        var fileHandle = messageFileHandleProvider.getThumbnail(messageUid);
        if (!fileHandle.exists()) {
            return null;
        }
        try {
            String thumbnailMimeType = messageModel.getFileData().getThumbnailMimeType();
            if (thumbnailMimeType != null) {
                String prefix = FileUtil.getMediaFilenamePrefix(messageModel);
                final File outputFile = createTempFile(prefix, MimeUtil.MIME_TYPE_IMAGE_PNG.equals(thumbnailMimeType) ? ".png" : ".jpg");

                try (InputStream inputStream = fileHandle.read()) {
                    if (inputStream != null) {
                        try (OutputStream outputStream = appContext.getContentResolver().openOutputStream(Uri.fromFile(outputFile))) {
                            long numBytes = outputStream != null ? copyStream(inputStream, outputStream) : 0L;
                            if (numBytes > 0 && numBytes <= maxSize) {
                                return getShareFileUri(outputFile, messageModel.getFileData().getFileName());
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

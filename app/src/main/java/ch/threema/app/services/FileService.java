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

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.crypto.CipherInputStream;

import ch.threema.app.cache.ThumbnailCache;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ResettableInputStream;
import ch.threema.base.ThreemaException;
import ch.threema.data.models.ContactModel;
import ch.threema.localcrypto.MasterKeyLockedException;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.data.models.GroupModel;

public interface FileService {

    /**
     * get the default path for data backup files
     */
    File getBackupPath();

    /**
     * Get the Uri for data backup files
     *
     * @return Uri of data backup path or null if not yet selected by user
     */
    Uri getBackupUri();

    /**
     * get the "default" path for blob downloads
     */
    File getBlobDownloadPath();

    /**
     * get the path of the application
     */
    @NonNull
    File getAppDataPath();

    /**
     *
     */
    String getGlobalWallpaperFilePath();

    /**
     * get the file path of wallpapers
     */
    File getWallpaperDirPath();

    /**
     * get the file path of avatar
     */
    File getAvatarDirPath();

    /**
     * get the file path of group avatars
     */
    File getGroupAvatarDirPath();

    /**
     * get the temporary file path
     */
    File getTempPath();

    File getIntTmpPath();

    @NonNull
    File getExtTmpPath();

    /**
     * create a temporary file
     */
    File createTempFile(String prefix, String suffix) throws IOException;

    File createTempFile(String prefix, String suffix, boolean isPublic) throws IOException;

    /**
     * cleanup temporary directory
     */
    @WorkerThread
    default void cleanTempDirs() {
        cleanTempDirs(0);
    }

    @WorkerThread
    void cleanTempDirs(long ageThresholdMillis);

    String getWallpaperFilePath(MessageReceiver messageReceiver);

    String getWallpaperFilePath(String uniqueIdString);

    /**
     * create a file object for the wallpaper
     */
    File createWallpaperFile(MessageReceiver messageReceiver) throws IOException;

    boolean hasUserDefinedProfilePicture(@NonNull String identity);

    boolean hasContactDefinedProfilePicture(@NonNull String identity);

    boolean hasAndroidDefinedProfilePicture(@NonNull String identity);

    /**
     * decrypt a file and save into a new one
     */
    boolean decryptFileToFile(File from, File to);

    /**
     * remove all files (content file, thumbnail) of a message
     */
    boolean removeMessageFiles(AbstractMessageModel messageModel, boolean withThumbnails);

    /**
     * return a decrypted file from a message
     * null if the message or file does not exist
     */
    @Nullable
    File getDecryptedMessageFile(AbstractMessageModel messageModel) throws Exception;

    /**
     * return a decrypted file from a message
     * null if the message or file does not exist
     */
    @Nullable
    File getDecryptedMessageFile(AbstractMessageModel messageModel, String filename) throws Exception;

    /**
     * return a cipher input stream of a message
     * return null if the file is missing
     */
    @Nullable
    CipherInputStream getDecryptedMessageStream(AbstractMessageModel messageModel) throws Exception;

    /**
     * return the cipher input stream of a thumbnail
     * return null if the thumbnail missing
     */
    @Nullable
    CipherInputStream getDecryptedMessageThumbnailStream(AbstractMessageModel messageModel) throws Exception;

    /**
     * copy a decrypted message file into "gallery" folder
     */
    void copyDecryptedFileIntoGallery(Uri sourceUri, AbstractMessageModel messageModel) throws Exception;

    File getMessageFile(AbstractMessageModel messageModel);

    /**
     * Create a media file for a specific message using the provided data, and return true on success.
     * Will fail if the file already exists.
     */
    boolean writeConversationMedia(AbstractMessageModel messageModel, byte[] data) throws Exception;

    /**
     * Create a media file for a specific message using the provided data, and return true on success.
     * Will fail if the file already exists.
     */
    boolean writeConversationMedia(AbstractMessageModel messageModel, byte[] data, int pos, int length) throws Exception;

    /**
     * Create a media file for a specific message using the provided data, and return true on success.
     * If the file already exists, it will be overwritten if [overwrite] is true, otherwise it will fail.
     */
    boolean writeConversationMedia(AbstractMessageModel messageModel, byte[] data, int pos, int length, boolean overwrite) throws Exception;

    /**
     * Create a media file for a specific message using the provided input stream, and return true on success.
     * Will fail if the file already exists.
     */
    boolean writeConversationMedia(@NonNull AbstractMessageModel messageModel, @NonNull InputStream inputStream) throws Exception;

    /**
     * Create a media file for a specific message using the provided input stream, and return true on success.
     * If the file already exists, it will be overwritten if [overwrite] is true, otherwise it will fail.
     */
    boolean writeConversationMedia(@NonNull AbstractMessageModel messageModel, @NonNull InputStream inputStream, boolean overwrite) throws Exception;

    /**
     * Save a group avatar (resize if needed) and return true on success. Additionally, this resets
     * the avatar cache for this group.
     */
    boolean writeGroupAvatar(GroupModel groupModel, byte[] photoData) throws IOException, MasterKeyLockedException;

    /**
     * Save a group avatar (resize if needed) and return true on success. Additionally, this resets
     * the avatar cache for this group.
     */
    boolean writeGroupAvatar(GroupModel groupModel, InputStream photoData) throws IOException, MasterKeyLockedException;
    /**
     * Save a group avatar (resize if needed) and return true on success. Additionally, this resets
     * the avatar cache for this group.
     */
    @Deprecated
    boolean writeGroupAvatar(ch.threema.storage.models.GroupModel groupModel, byte[] photoData) throws Exception;

    /**
     * get the group avatar as InputStream
     *
     * @return null if there is no group avatar
     */
    @Nullable
    InputStream getGroupAvatarStream(GroupModel groupModel) throws IOException, MasterKeyLockedException;

    /**
     * get the group avatar as InputStream
     *
     * @return null if there is no group avatar
     */
    @Deprecated
    InputStream getGroupAvatarStream(ch.threema.storage.models.GroupModel groupModel) throws Exception;

    /**
     * Get the group avatar as byte array.
     */
    @Nullable
    byte[] getGroupAvatarBytes(@NonNull GroupModel groupModel) throws Exception;

    /**
     * get the group avatar if the file exists
     *
     * @return null if there is no group avatar
     */
    @Nullable
    Bitmap getGroupAvatar(GroupModel groupModel) throws IOException, MasterKeyLockedException;

    /**
     * get the group avatar if the file exists
     *
     * @return null if there is no group avatar
     */
    @Deprecated
    @Nullable
    Bitmap getGroupAvatar(ch.threema.storage.models.GroupModel groupModel) throws Exception;

    /**
     * Remove the group avatar. Additionally, this resets the avatar cache for this group.
     */
    void removeGroupAvatar(@NonNull GroupModel groupModel);

    /**
     * remove the group avatar
     */
    @Deprecated
    void removeGroupAvatar(ch.threema.storage.models.GroupModel groupModel);

    boolean hasGroupAvatarFile(@NonNull GroupModel groupModel);

    @Deprecated
    boolean hasGroupAvatarFile(@NonNull ch.threema.storage.models.GroupModel groupModel);

    /**
     * Write the contact profile picture set by the user. Additionally, this resets the avatar cache
     * for this contact.
     */
    boolean writeUserDefinedProfilePicture(@NonNull String identity, File file);

    /**
     * Write the contact profile picture set by the user. Additionally, this resets the avatar cache
     * for this contact.
     */
    boolean writeUserDefinedProfilePicture(@NonNull String identity, byte[] avatarFile) throws IOException, MasterKeyLockedException;

    /**
     * Write the contact profile picture set by the user. Additionally, this resets the avatar cache
     * for this contact.
     */
    boolean writeUserDefinedProfilePicture(@NonNull String identity, @NonNull InputStream avatar) throws IOException, MasterKeyLockedException;

    /**
     * Write the contact profile picture received by the contact. Additionally, this resets the
     * avatar cache for this contact.
     */
    boolean writeContactDefinedProfilePicture(@NonNull String identity, byte[] encryptedBlob) throws IOException, MasterKeyLockedException;

    /**
     * Write the contact profile picture received by the contact. Additionally, this resets the
     * avatar cache for this contact.
     */
    boolean writeContactDefinedProfilePicture(
        @NonNull String identity,
        @NonNull InputStream encryptedBlob
    ) throws IOException, MasterKeyLockedException;

    /**
     * Write the contact profile picture from Android's address book. Additionally, this resets the
     * avatar cache for this contact.
     */
    boolean writeAndroidDefinedProfilePicture(@NonNull String identity, byte[] avatarFile) throws Exception;

    /**
     * return the decrypted bitmap of a contact avatar
     * if no file exists, null will be returned
     */
    @Nullable
    Bitmap getUserDefinedProfilePicture(@NonNull String identity) throws Exception;

    @Nullable
    Bitmap getAndroidDefinedProfilePicture(@NonNull ContactModel contactModel) throws Exception;

    /**
     * Return a input stream of a local saved contact avatar
     */
    @Nullable
    InputStream getUserDefinedProfilePictureStream(@NonNull String identity) throws IOException, MasterKeyLockedException;

    /**
     * Return a input stream of a contact photo
     *
     * @return null if there is no contact defined profile picture
     */
    @Nullable
    InputStream getContactDefinedProfilePictureStream(@NonNull String identity) throws IOException, MasterKeyLockedException;

    /**
     * return the decrypted bitmap of a contact-provided profile picture
     *
     * @return null if no contact defined profile picture exists
     */
    @Nullable
    Bitmap getContactDefinedProfilePicture(@NonNull String identity) throws Exception;

    /**
     * Remove the user defined profile picture for the contact with the given identity.
     * Additionally, this resets the avatar cache for this contact.
     *
     * @param identity the identity of the contact
     * @return true if the avatar was deleted, false if the remove failed or no avatar file exists
     */
    boolean removeUserDefinedProfilePicture(@NonNull String identity);

    /**
     * Remove the contact defined profile picture for the contact with the given identity.
     * Additionally, this resets the avatar cache for this contact.
     *
     * @param identity the identity of the contact
     * @return true if avatar was deleted, false if the remove failed or no avatar file exists
     */
    boolean removeContactDefinedProfilePicture(@NonNull String identity);

    /**
     * Remove the profile picture from Android's address book. Additionally, this resets the avatar
     * cache for this contact.
     *
     * @param identity the identity of the contact
     * @return true if the avatar was deleted, false if the remove failed or no avatar file exists
     */
    boolean removeAndroidDefinedProfilePicture(@NonNull String identity);

    /**
     * Remove all avatars in the respective directory. Note that this does *not* reset the avatar
     * caches.
     */
    void removeAllAvatars();

    /**
     * Save the thumbnail bytes to disk using the file name specified in the supplied AbstractMessageModel
     *
     * @param messageModel   Message Model used as the source for the file name
     * @param thumbnailBytes Byte Array of the thumbnail bitmap
     */
    void saveThumbnail(AbstractMessageModel messageModel, byte[] thumbnailBytes) throws Exception;

    /**
     * Save the thumbnail to disk using the file name specified in the supplied AbstractMessageModel
     *
     * @param messageModel   Message Model used as the source for the file name
     * @param thumbnail      InputStream to read the thumbnail's image data from
     */
    void saveThumbnail(AbstractMessageModel messageModel, @NonNull InputStream thumbnail) throws Exception;

    /**
     * write a thumbnail to disk
     */
    void writeConversationMediaThumbnail(AbstractMessageModel messageModel, @NonNull byte[] thumbnail) throws Exception;


    /**
     * write a thumbnail to disk
     */
    void writeConversationMediaThumbnail(AbstractMessageModel messageModel, @NonNull ResettableInputStream thumbnail) throws Exception;

    /**
     * return whether a thumbnail file exists for the specified message model
     */
    boolean hasMessageThumbnail(AbstractMessageModel messageModel);

    /**
     * return the decrypted thumbnail as bitmap
     */
    @Nullable
    Bitmap getMessageThumbnailBitmap(AbstractMessageModel messageModel, @Nullable ThumbnailCache thumbnailCache) throws Exception;

    /**
     * return the "default" thumbnail
     */
    Bitmap getDefaultMessageThumbnailBitmap(Context context, AbstractMessageModel messageModel, ThumbnailCache thumbnailCache, String mimeType, boolean returnNullIfNotCached, @ColorInt int tintColor);

    /**
     * clear directory
     */
    void clearDirectory(File directory, boolean recursive) throws IOException, ThreemaException;

    /**
     * remove the directory
     */
    boolean remove(File directory, boolean removeWithContent) throws IOException, ThreemaException;

    /**
     * copy the content of a uri into a temporary file
     */
    File copyUriToTempFile(Uri uri, String prefix, String suffix, boolean isPublic);

    /**
     * export the message file to the "share file"
     */
    Uri copyToShareFile(AbstractMessageModel currentModel, File decodedFile);

    Uri getShareFileUri(File destFile, String filename);

    long getInternalStorageUsage();

    long getInternalStorageSize();

    long getInternalStorageFree();

    /**
     * Decrypt messages specified by the 'models' parameter and return a list of URIs of the temporary files
     * When receiving the URIs in OnDecryptedFilesComplete.onComplete, they will have the same order as passed in by `models` list.
     * It is possible, that this URI list contains null entries.
     * Note that you have to ensure that only image, video or file messages are provided
     *
     * @param models                   List of AbstractMessageModels to be decrypted
     * @param onDecryptedFilesComplete Callback
     */
    void loadDecryptedMessageFiles(final List<AbstractMessageModel> models, final OnDecryptedFilesComplete onDecryptedFilesComplete);

    void loadDecryptedMessageFile(final AbstractMessageModel model, final OnDecryptedFileComplete onDecryptedFileCompleted);

    void saveMedia(final AppCompatActivity activity, final View feedbackView, final CopyOnWriteArrayList<AbstractMessageModel> selectedMessages, boolean quiet);

    void saveAppLogo(@Nullable File logo, @ConfigUtils.AppThemeSetting String theme);

    @NonNull
    File getAppLogo(@ConfigUtils.AppThemeSetting String theme);

    @NonNull
    Uri getTempShareFileUri(@NonNull Bitmap bitmap) throws IOException;

    /**
     * Copy the decrypted thumbnail to a temporary file accessible through our FileProvider and return the Uri of the temporary file
     *
     * @param messageModel Message Model used as the source for the thumbnail
     * @param maxSize      Maximum size of the thumbnail in bytes. Set to Integer.MAX_VALUE if no limit
     * @return Uri of the temporary file or null if the thumbnail does not exist, is too large or an error occurred
     */
    @WorkerThread
    @Nullable
    Uri getThumbnailShareFileUri(AbstractMessageModel messageModel, int maxSize);

    interface OnDecryptedFileComplete {
        void complete(File decryptedFile);

        void error(String message);
    }

    interface OnDecryptedFilesComplete {
        void complete(ArrayList<Uri> uriList);

        void error(String message);
    }
}

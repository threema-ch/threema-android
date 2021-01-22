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

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.View;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.crypto.CipherInputStream;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;
import ch.threema.app.cache.ThumbnailCache;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.utils.ConfigUtils.AppTheme;
import ch.threema.base.ThreemaException;
import ch.threema.localcrypto.MasterKeyLockedException;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupModel;

public interface FileService {

	/**
	 * get the default path for data backup files
	 */
	File getBackupPath();

	/**
	 * get the Uri for data backup files
	 */
	Uri getBackupUri();

	/**
	 * get the "default" path for blob downloads
	 */
	File getBlobDownloadPath();

	/**
	 * get the path of the application
	 */
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

	File getExtTmpPath();

	/**
	 * create a temporary file
	 * @throws IOException
	 */
	File createTempFile(String prefix, String suffix) throws IOException;

	File createTempFile(String prefix, String suffix, boolean isPublic) throws IOException;

	/**
	 * cleanup temporary directory
	 */
	@WorkerThread
	void cleanTempDirs();

	/**
	 */
	String getWallpaperFilePath(MessageReceiver messageReceiver);

	String getWallpaperFilePath(String uniqueIdString);

	/**
	 * create a file object for the wallpaper
	 * @throws IOException
	 */
	File createWallpaperFile(MessageReceiver messageReceiver) throws IOException;

	/**
	 *
	 * @param contactModel
	 * @return true if avatar file exists
	 */
	boolean hasContactAvatarFile(ContactModel contactModel);

	boolean hasContactPhotoFile(ContactModel contactModel);

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
	 * @throws Exception
	 */
	File getDecryptedMessageFile(AbstractMessageModel messageModel) throws Exception;

	/**
	 * return a decrypted file from a message
	 * null if the message or file does not exist
	 * @throws Exception
	 */
	File getDecryptedMessageFile(AbstractMessageModel messageModel, String filename) throws Exception;

	/**
	 * return a cipher input stream of a message
	 * return null if the file is missing
	 * @throws Exception
	 */
	CipherInputStream getDecryptedMessageStream(AbstractMessageModel messageModel) throws Exception;

	/**
	 * return the cipher input stream of a thumbnail
	 * return null if the thumbnail missing
	 * @throws Exception
	 */
	CipherInputStream getDecryptedMessageThumbnailStream(AbstractMessageModel messageModel) throws Exception;

	/**
	 * copy a decrypted message file into "gallery" folder
	 *
	 * TODO: move to another service
	 */
	void copyDecryptedFileIntoGallery(Uri sourceUri, AbstractMessageModel messageModel) throws Exception;

	File getMessageFile(AbstractMessageModel messageModel);

	/**
	 * write a message (modify if needed) and return the original or modified file as byte
	 */
	boolean writeConversationMedia(AbstractMessageModel messageModel, byte[] data) throws Exception;

	/**
	 * write a message (modify if needed) and return the original or modified file as byte
	 */
	boolean writeConversationMedia(AbstractMessageModel messageModel, byte[] data, int pos, int length) throws Exception;

	/**
	 * write a message (modify if needed) and return the original or modified file as byte
	 */
	boolean writeConversationMedia(AbstractMessageModel messageModel, byte[] data, int pos, int length, boolean overwrite) throws Exception;

	/**
	 * save a group avatar (resize if needed) and return the original or modified avatar
	 */
	boolean writeGroupAvatar(GroupModel groupModel, byte[] photoData) throws Exception;

	/**
	 * get the gropu avatar as InputStream
	 */
	InputStream getGroupAvatarStream(GroupModel groupModel) throws Exception;

	/**
	 * get the group avatar if the file exists
	 */
	Bitmap getGroupAvatar(GroupModel groupModel) throws Exception;

	/**
	 * remove the group avatar
	 */
	void removeGroupAvatar(GroupModel groupModel);

	boolean hasGroupAvatarFile(GroupModel groupModel);

	/**
	 * write the contact avatar
	 */
	boolean writeContactAvatar(ContactModel contactModel, File file) throws Exception;

	/**
	 * write the contact avatar
	 */
	boolean writeContactAvatar(ContactModel contactModel, byte[] avatarFile) throws Exception;

	/**
	 * write the contact photo received by the contact
	 */
	boolean writeContactPhoto(ContactModel contactModel, byte[] encryptedBlob) throws Exception;

	/**
	 * write the contact avatar from Android's address book
	 */
	boolean writeAndroidContactAvatar(ContactModel contactModel, byte[] avatarFile) throws Exception;

	/**
	 * return the decrypted bitmap of a contact avatar
	 * if no file exists, null will be returned
	 */
	Bitmap getContactAvatar(ContactModel contactModel) throws Exception;

	Bitmap getAndroidContactAvatar(ContactModel contactModel) throws Exception;

	/**
	 * Return a input stream of a local saved contact avatar
	 */
	InputStream getContactAvatarStream(ContactModel contactModel) throws IOException, MasterKeyLockedException;

	/**
	 * Return a input stream of a contact photo
	 */
	InputStream getContactPhotoStream(ContactModel contactModel) throws IOException, MasterKeyLockedException;

	/**
	 * return the decrypted bitmap of a contact-provided profile picture
	 * returns null if no file exists
	 */
	Bitmap getContactPhoto(ContactModel contactModel) throws Exception;

	/**
	 * remove the saved avatar
	 * return true if the avatar was deleted, false if the remove failed or no avatar file exists
	 */
	boolean removeContactAvatar(ContactModel contactModel);

	/**
	 * remove the saved profile pic for this contact
	 * @param contactModel
	 * @return true if avatar was deleted, false if the remove failed or no avatar file exists
	 */
	boolean removeContactPhoto(ContactModel contactModel);

	/**
	 * remove the saved avatar from Android's address book
	 * return true if the avatar was deleted, false if the remove failed or no avatar file exists
	 */
	boolean removeAndroidContactAvatar(ContactModel contactModel);

	/**
	 * remove all avatars in the respective directory
	 */
	void removeAllAvatars();

	/**
	 * write a thumbnail to disk
	 */
	void writeConversationMediaThumbnail(AbstractMessageModel messageModel, byte[] thumbnail) throws Exception;

	/**
	 * return whether a thumbnail file exists for the specified message model
	 */
	boolean hasMessageThumbnail(AbstractMessageModel messageModel);

	/**
	 * return the decrypted thumbnail as bitmap
	 */
	Bitmap getMessageThumbnailBitmap(AbstractMessageModel messageModel, @Nullable ThumbnailCache thumbnailCache) throws Exception;

	/**
	 * return the "default" thumbnail
	 */
	Bitmap getDefaultMessageThumbnailBitmap(Context context, AbstractMessageModel messageModel, ThumbnailCache thumbnailCache, String mimeType);

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
	 * Note that you have to ensure that only image, video or file messages are provided
	 * @param models List of AbstractMessageModels to be decrypted
	 * @param onDecryptedFilesComplete Callback
	 */
	void loadDecryptedMessageFiles(final List<AbstractMessageModel> models, final OnDecryptedFilesComplete onDecryptedFilesComplete);

	void loadDecryptedMessageFile(final AbstractMessageModel model, final OnDecryptedFileComplete onDecryptedFileCompleted);

	void saveMedia(final AppCompatActivity activity, final View feedbackView, final CopyOnWriteArrayList<AbstractMessageModel> selectedMessages, boolean quiet);

	void saveAppLogo(File logo, @AppTheme int theme);
	File getAppLogo(@AppTheme int theme);

	interface OnDecryptedFileComplete {
		void complete(File decryptedFile);
		void error(String message);
	}

	interface OnDecryptedFilesComplete {
		void complete(ArrayList<Uri> uriList);
		void error(String message);
	}
}

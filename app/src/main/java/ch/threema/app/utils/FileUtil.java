/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import ch.threema.app.AppConstants;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.camera.CameraActivity;
import ch.threema.app.filepicker.FilePickerActivity;
import ch.threema.app.services.FileService;
import ch.threema.app.ui.MediaItem;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.data.media.FileDataModel;

import static ch.threema.app.AppConstants.MAX_BLOB_SIZE;
import static ch.threema.app.filepicker.FilePickerActivity.INTENT_DATA_DEFAULT_PATH;
import static ch.threema.app.utils.StreamUtilKt.getFromUri;

public class FileUtil {
    private static final Logger logger = getThreemaLogger("FileUtil");

    private FileUtil() {

    }

    @Deprecated
    public static void selectFile(Activity activity, Fragment fragment, String[] mimeTypes, int ID, boolean multi, int sizeLimit, String initialPath) {
        Intent intent;
        final Context context;

        if (fragment != null) {
            context = fragment.getActivity();
        } else {
            context = activity;
        }

        final boolean useOpenDocument = (isMediaProviderSupported(context) && initialPath == null) || ConfigUtils.hasScopedStorage();

        if (useOpenDocument) {
            intent = getOpenDocumentIntent(mimeTypes);
        } else {
            intent = getGetContentIntent(context, mimeTypes, initialPath);
        }

        addExtras(intent, multi, sizeLimit);

        try {
            startAction(activity, fragment, ID, intent);
        } catch (ActivityNotFoundException e) {
            if (useOpenDocument) {
                if (!ConfigUtils.hasScopedStorage()) {
                    // fallback to ACTION_GET_CONTENT on broken devices
                    intent = getGetContentIntent(context, mimeTypes, initialPath);
                    addExtras(intent, multi, sizeLimit);
                    try {
                        startAction(activity, fragment, ID, intent);
                    } catch (ActivityNotFoundException ignored) {
                    }
                } else {
                    // device is missing DocumentsUI - impossible to get access to the backup file
                    Toast.makeText(context, String.format("Broken device. DocumentsUI is disabled or missing. Please fix or contact %s.", Build.MANUFACTURER), Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(context, R.string.no_activity_for_mime_type, Toast.LENGTH_LONG).show();
            }
        }
    }

    public static void selectFile(@NonNull Context context, @NonNull ActivityResultLauncher<Intent> launcher, @NonNull String[] mimeTypes, boolean multi, int sizeLimit, String initialPath) {
        Intent intent;

        final boolean useOpenDocument = (isMediaProviderSupported(context) && initialPath == null) || ConfigUtils.hasScopedStorage();

        if (useOpenDocument) {
            intent = getOpenDocumentIntent(mimeTypes);
        } else {
            intent = getGetContentIntent(context, mimeTypes, initialPath);
        }

        addExtras(intent, multi, sizeLimit);

        try {
            launcher.launch(intent);
        } catch (ActivityNotFoundException e) {
            if (useOpenDocument) {
                if (!ConfigUtils.hasScopedStorage()) {
                    // fallback to ACTION_GET_CONTENT on broken devices
                    intent = getGetContentIntent(context, mimeTypes, initialPath);
                    addExtras(intent, multi, sizeLimit);
                    try {
                        launcher.launch(intent);
                    } catch (ActivityNotFoundException ignored) {
                    }
                } else {
                    // device is missing DocumentsUI - impossible to get access to the backup file
                    Toast.makeText(context, String.format("Broken device. DocumentsUI is disabled or missing. Please fix or contact %s.", Build.MANUFACTURER), Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(context, R.string.no_activity_for_mime_type, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Deprecated
    private static void startAction(Activity activity, Fragment fragment, int ID, Intent intent) throws ActivityNotFoundException {
        if (fragment != null) {
            fragment.startActivityForResult(intent, ID);
        } else {
            activity.startActivityForResult(intent, ID);
        }
    }

    private static @NonNull Intent getOpenDocumentIntent(String[] mimeTypes) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
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

        return intent;
    }

    private static @NonNull Intent getGetContentIntent(Context context, String[] mimeTypes, String initialPath) {
        Intent intent = new Intent();
        if (MimeUtil.isVideoFile(mimeTypes[0]) || MimeUtil.isSupportedImageFile(mimeTypes[0])) {
            intent.setAction(Intent.ACTION_GET_CONTENT);
        } else {
            intent = new Intent(context, FilePickerActivity.class);
            if (initialPath != null) {
                intent.putExtra(INTENT_DATA_DEFAULT_PATH, initialPath);
            }
        }
        intent.setType(mimeTypes[0]);

        return intent;
    }

    private static void addExtras(Intent intent, boolean multi, int sizeLimit) {
        if (multi) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        if (sizeLimit > 0) {
            intent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, (long) sizeLimit);
        }
    }

    public static boolean getCameraFile(Activity activity, Fragment fragment, File cameraFile, int requestCode, FileService fileService, boolean preferInternal) {
        try {
            Intent cameraIntent;

            if (preferInternal) {
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

        intent.setAction(AppConstants.INTENT_ACTION_FORWARD);
        intent.putExtra(AppConstants.INTENT_DATA_IS_FORWARD, true);
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
     *
     * @param uris Uris to check
     * @return List of Uris not located in the private directory
     */
    private static @NonNull ArrayList<Uri> validateUriList(ArrayList<Uri> uris) {
        String dataDir = Environment.getDataDirectory().toString();
        ArrayList<Uri> validatedUris = new ArrayList<>();

        if (uris != null && !uris.isEmpty()) {
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
     *
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
                mimeType = MimeUtil.MIME_TYPE_AUDIO_OGG;
            } else if (extension.equalsIgnoreCase("gpx")) {
                // https://issuetracker.google.com/issues/37120151
                mimeType = MimeUtil.MIME_TYPE_GPX;
            } else if (extension.equalsIgnoreCase("pkpass")) {
                mimeType = MimeUtil.MIME_TYPE_APPLE_PKPASS;
            }
        }
        if (TestUtil.isEmptyOrNull(mimeType)) {
            return MimeUtil.MIME_TYPE_DEFAULT;
        }
        return mimeType;
    }

    @Nullable
    public static String getMimeTypeFromUri(@NonNull Context context, @Nullable Uri uri) {
        if (uri != null) {
            ContentResolver contentResolver = context.getContentResolver();
            String type = contentResolver.getType(uri);

            if (TestUtil.isEmptyOrNull(type) || MimeUtil.MIME_TYPE_DEFAULT.equals(type)) {
                String filename = FileUtil.getFilenameFromUri(contentResolver, uri);

                return getMimeTypeFromPath(filename);
            }
            return type;
        }
        return null;
    }

    /**
     * Check if Storage Access Framework is really available
     */
    private static boolean isMediaProviderSupported(Context context) {
        final PackageManager pm = context.getPackageManager();
        // Pick up provider with action string
        final Intent i = new Intent(DocumentsContract.PROVIDER_INTERFACE);
        final List<ResolveInfo> providers = pm.queryIntentContentProviders(i, 0);
        for (ResolveInfo info : providers) {
            if (info != null && info.providerInfo != null) {
                final String authority = info.providerInfo.authority;
                if (isMediaDocument(Uri.parse(ContentResolver.SCHEME_CONTENT + "://" + authority)))
                    return true;
            }
        }
        return false;
    }

    @Nullable
    public static String getRealPathFromURI(@NonNull final Context context, @NonNull final Uri uri) {
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
            if (isGooglePhotosUri(uri)) {
                return uri.getLastPathSegment();
            }
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

    @Nullable
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

    public static boolean isImageFile(FileDataModel fileDataModel) {
        return fileDataModel != null && (MimeUtil.isImageFile(fileDataModel.getMimeType()));
    }

    public static boolean isVideoFile(FileDataModel fileDataModel) {
        return fileDataModel != null && (MimeUtil.isVideoFile(fileDataModel.getMimeType()));
    }

    public static boolean isImageOrVideoFile(FileDataModel fileDataModel) {
        return isImageFile(fileDataModel) || isVideoFile(fileDataModel);
    }

    public static boolean isAudioFile(FileDataModel fileDataModel) {
        return fileDataModel != null && (MimeUtil.isAudioFile(fileDataModel.getMimeType()));
    }

    public static String getFileMessageDatePrefix(Context context, AbstractMessageModel messageModel, String fileType) {
        if (messageModel.getFileData().getFileSize() == 0) {
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
     *
     * @param mimeType the mime type to generate a filename for
     * @return a filename with an extension
     */
    public static @NonNull String getDefaultFilename(@Nullable String mimeType) {
        if (TestUtil.isEmptyOrNull(mimeType)) {
            mimeType = MimeUtil.MIME_TYPE_DEFAULT;
        }

        String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        return getMediaFilenamePrefix() + "." + extension;
    }

    public static String sanitizeFileName(String filename) {
        if (!TestUtil.isEmptyOrNull(filename)) {
            return filename.replaceAll("[:/*\"?|<>' ]|\\.{2}", "_");
        }
        return null;
    }

    @WorkerThread
    public static boolean copyFile(@NonNull Uri source, @NonNull File dest, @NonNull ContentResolver contentResolver) {
        try (InputStream inputStream = contentResolver.openInputStream(source);
             OutputStream outputStream = new FileOutputStream(dest)) {
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
     * <p>
     * Note: Do not use this if error recovery is important!
     *
     * @param file        The file that should be deleted
     * @param description The description of the file (e.g. "message queue database")
     * @param logger      The logger to use
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
     *
     * @param file   The file that should be created or re-used
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
     *
     * @param destPath     Destination path
     * @param destFilename Desired filename
     * @return File object
     */
    public static File getUniqueFile(String destPath, String destFilename) {
        File destFile = new File(destPath, destFilename);

        String extension = MimeTypeMap.getFileExtensionFromUrl(destFilename);
        if (!TestUtil.isEmptyOrNull(extension)) {
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
     *
     * @param contentResolver ContentResolver
     * @param mediaItem       MediaItem representing the source file
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
     *
     * @param contentResolver ContentResolver
     * @param uri             Uri pointing at the object
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
                    filename = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME));
                }
            } catch (Exception e) {
                logger.error("Unable to query Content Resolver", e);
                // guess filename from last path segment of content URI. Accept only filenames that contain one "." character
                String lastPathSegment = uri.getLastPathSegment();
                if (lastPathSegment != null) {
                    if (lastPathSegment.indexOf(".") == lastPathSegment.lastIndexOf(".")) {
                        filename = lastPathSegment;
                    }
                }
            }
        }
        return filename;
    }

    /**
     * Try to get a file uri from a content uri to maintain access to a file across two activities.
     * This method does not sanitize the uri and allows converting the uri to a file uri that can
     * refer internal files.
     * NOTE: This hack will probably stop working in API 30
     *
     * @param uri content uri to resolve
     * @return file uri, if a file path could be resolved
     */
    @NonNull
    public static Uri getFileUri(@NonNull Uri uri) {
        String path = FileUtil.getRealPathFromURI(ThreemaApplication.getAppContext(), uri);

        if (path != null) {
            File file = new File(path);
            if (file.canRead()) {
                return Uri.fromFile(file);
            }
        }
        return uri;
    }

    /**
     * Select a file from a gallery app. Shows a selector first to allow for choosing the desired gallery app or SystemUIs file picker.
     * Does not necessarily need file permissions as a modern gallery app will return a content Uri with a temporary permission to access the file
     *
     * @param activity     Activity where the result of the selection should end up
     * @param fragment     Fragment where the result of the selection should end up
     * @param requestCode  Request code to use for result
     * @param includeVideo Whether to include the possibility to select video files (if supported by app)
     */
    public static void selectFromGallery(@Nullable Activity activity, @Nullable Fragment fragment, int requestCode, boolean includeVideo) {
        if (activity == null) {
            activity = fragment.getActivity();
        }

        final String imageMimeTypes = String.join(",", MimeUtil.getSupportedImageMimeTypes());

        try {
            final Intent startIntent;
            final Intent getContentIntent = new Intent();

            getContentIntent.setAction(Intent.ACTION_GET_CONTENT);
            getContentIntent.addCategory(Intent.CATEGORY_OPENABLE);
            getContentIntent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            getContentIntent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, MAX_BLOB_SIZE);

            if (includeVideo && (
                ConfigUtils.isXiaomiDevice() ||
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)) {
                getContentIntent.setType(MimeUtil.MIME_TYPE_IMAGE + "," + MimeUtil.MIME_TYPE_VIDEO);
                String[] mimetypes = Stream.concat(Arrays.stream(MimeUtil.getSupportedImageMimeTypes()), Arrays.stream(new String[]{MimeUtil.MIME_TYPE_VIDEO})).toArray(
                    size -> (String[]) Array.newInstance(String.class, size)
                );
                getContentIntent.putExtra(Intent.EXTRA_MIME_TYPES, mimetypes);
            } else {
                getContentIntent.setType(MimeUtil.MIME_TYPE_IMAGE);
                getContentIntent.putExtra(Intent.EXTRA_MIME_TYPES, imageMimeTypes);
            }

            if (includeVideo) {
                if (ConfigUtils.isXiaomiDevice()) {
                    startIntent = getContentIntent;
                } else {
                    Intent pickIntent = new Intent(Intent.ACTION_PICK);
                    pickIntent.setType(imageMimeTypes);
                    startIntent = Intent.createChooser(pickIntent, activity.getString(R.string.select_from_gallery));
                    startIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{getContentIntent});
                }
            } else {
                startIntent = getContentIntent;
            }
            if (fragment != null) {
                fragment.startActivityForResult(startIntent, requestCode);
            } else {
                activity.startActivityForResult(startIntent, requestCode);
            }
        } catch (Exception e) {
            logger.debug("Exception", e);
            Toast.makeText(activity, R.string.no_activity_for_mime_type, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Check if the file at the provided Uri is an animated WebP file by looking at the file header
     *
     * @param uri A File Uri pointing to an image file
     * @return true if the file is an animated WebP file, false if it is not animated, in another format, corrupt or not readable
     */
    private static boolean isAnimatedWebPFile(@NonNull Uri uri) {
        try (InputStream inputStream = getFromUri(ThreemaApplication.getAppContext(), uri)) {
            byte[] buffer = new byte[34];
            return inputStream != null
                && inputStream.read(buffer) == 34
                && Arrays.equals(Arrays.copyOfRange(buffer, 0, 4), new byte[]{'R', 'I', 'F', 'F'})
                && Arrays.equals(Arrays.copyOfRange(buffer, 8, 12), new byte[]{'W', 'E', 'B', 'P'})
                && Arrays.equals(Arrays.copyOfRange(buffer, 12, 15), new byte[]{'V', 'P', '8'})
                && Arrays.equals(Arrays.copyOfRange(buffer, 30, 34), new byte[]{'A', 'N', 'I', 'M'});
        } catch (IOException ignore) {
            return false;
        }
    }

    /**
     * Check if the file at the provided Uri is an animation. Currently, only animated WebP and (possibly static) GIFs are supported
     *
     * @param uri A File Uri pointing to an image file
     * @return true if the file an animated image
     */
    public static boolean isAnimatedImageFile(@NonNull Uri uri, String mimeType) {
        return isAnimatedWebPFile(uri) || MimeUtil.isGifFile(mimeType);
    }

    /**
     * Checks that the file path is not inside the app's internal data directory after resolving
     * path traversals.
     *
     * @param context the context
     * @param path    the path that will be checked
     * @return {@code false} if the provided path is null, contains unresolvable path traversals, or
     * resides inside the internal data directory
     */
    public static boolean isSanePath(@NonNull Context context, @Nullable String path) {
        if (path == null) {
            // We are rather restrictive here. In general it is safer to assume that null paths are
            // not safe.
            return false;
        }

        String canonicalPath;
        try {
            canonicalPath = kotlin.io.FilesKt.normalize(new File(path)).getCanonicalPath();
        } catch (IOException exception) {
            logger.error("Cannot get canonical path", exception);
            return false;
        }

        // Check for path traversals
        if (canonicalPath.contains("..")) {
            logger.warn("Path traversal attempted");
            return false;
        }

        File dataDir = ContextCompat.getDataDir(context);
        if (dataDir == null) {
            logger.error("Could not determine data directory");
            return false;
        }

        String canonicalDataDir;
        try {
            canonicalDataDir = dataDir.getCanonicalPath();
        } catch (IOException exception) {
            logger.warn("Cannot get canonical path of data dir");
            return false;
        }

        // Check that the path lies not inside the private files directory
        if (canonicalPath.startsWith(canonicalDataDir)) {
            logger.warn("Access denied to data dir");
            return false;
        }

        return true;
    }
}

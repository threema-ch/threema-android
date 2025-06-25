/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2025 Threema GmbH
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

package ch.threema.app.systemupdates.updates;

import android.content.Context;
import android.database.Cursor;

import org.slf4j.Logger;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import androidx.annotation.NonNull;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.Base32;
import ch.threema.base.utils.LoggingUtil;

import static android.provider.MediaStore.MEDIA_IGNORE_FILENAME;

/**
 * add profile pic field to normal, group and distribution list message models
 */
public class SystemUpdateToVersion42 implements SystemUpdate {
    private static final Logger logger = LoggingUtil.getThreemaLogger("SystemUpdateToVersion42");

    private @NonNull final Context context;
    private @NonNull final ServiceManager serviceManager;

    public SystemUpdateToVersion42(@NonNull Context context, @NonNull ServiceManager serviceManager) {
        this.context = context;
        this.serviceManager = serviceManager;
    }

    @Override
    public void run() {
        final File appPath = new File(context.getExternalFilesDir(null), "data");
        final File avatarPath = new File(appPath, "/.avatar");
        final File wallpaperPath = new File(appPath.getPath() + "/.wallpaper");

        var database = serviceManager.getDatabaseService().getReadableDatabase();

        Cursor contacts = database.rawQuery("SELECT identity FROM contacts", null);
        if (contacts != null) {
            while (contacts.moveToNext()) {
                final String identity = contacts.getString(0);

                if (!TestUtil.isEmptyOrNull(identity)) {
                    migratePictureFile(avatarPath, ".c-", null, "c-" + identity, identity);
                    migratePictureFile(avatarPath, ".p-", null, "c-" + identity, identity);
                    migratePictureFile(wallpaperPath, ".w-", ".w", "c-" + identity, null);
                }
            }
            contacts.close();
        }

        Cursor groups = database.rawQuery("SELECT id FROM m_group", null);
        if (groups != null) {
            while (groups.moveToNext()) {
                final int id = groups.getInt(0);

                if (id >= 0) {
                    migratePictureFile(wallpaperPath, ".w-", ".w", "g-" + String.valueOf(id), null);
                }
            }
            groups.close();
        }

        // delete obsolete distribution list wallpaper
        File distributionListWallpaper = new File(wallpaperPath, ".w0" + MEDIA_IGNORE_FILENAME);
        try {
            FileUtil.deleteFileOrWarn(distributionListWallpaper, "", logger);
        } catch (Exception e) {
            //
        }
    }

    private boolean migratePictureFile(File path, String filePrefix, String oldFilePrefix, String rawUniqueId, String oldRawUniqueId) {
        String filename_old = (oldFilePrefix != null ? oldFilePrefix : filePrefix) + String.valueOf(oldRawUniqueId != null ? oldRawUniqueId : rawUniqueId).hashCode() + MEDIA_IGNORE_FILENAME;
        String filename_new;

        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            messageDigest.update(rawUniqueId.getBytes());
            filename_new = filePrefix + Base32.encode(messageDigest.digest()) + MEDIA_IGNORE_FILENAME;
        } catch (NoSuchAlgorithmException e) {
            logger.error("Exception", e);
            return false;
        }

        File oldFile = new File(path, filename_old);
        File newFile = new File(path, filename_new);

        if (oldFile.exists()) {
            try {
                if (newFile.exists()) {
                    FileUtil.deleteFileOrWarn(oldFile, "", logger);
                } else {
                    if (!oldFile.renameTo(newFile)) {
                        logger.debug("Failed to rename file");
                    }
                }
            } catch (Exception e) {
                //
            }
        }
        return true;
    }

    @Override
    public int getVersion() {
        return 42;
    }
}

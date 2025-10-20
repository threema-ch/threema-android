/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2025 Threema GmbH
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

import android.content.Context;
import android.os.Environment;
import android.text.TextUtils;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Helper class for getting all storages directories in an Android device
 * <a href="https://stackoverflow.com/a/40582634/3940133">Solution of this problem</a>
 * Consider to use
 * <a href="https://developer.android.com/guide/topics/providers/document-provider">StorageAccessFramework(SAF)</>
 * if your min SDK version is 19 and your requirement is just for browse and open documents, images, and other files
 *
 * @author Dmitriy Lozenko
 * @author HendraWD
 */
public class StorageUtil {

    // All Secondary SD-CARDs (all exclude primary) separated by File.pathSeparator, i.e: ":", ";"
    private static final String RAW_SECONDARY_STORAGES = System.getenv("SECONDARY_STORAGE");

    // Primary emulated SD-CARD
    private static final String RAW_EMULATED_STORAGE_TARGET = System.getenv("EMULATED_STORAGE_TARGET");

    /**
     * Returns all available SD-Cards in the system (include emulated)
     * <p/>
     * Warning: Hack! Based on Android source code of version 4.3 (API 18)
     * Because there is no standard way to get it.
     *
     * @return paths to all available SD-Cards in the system (include emulated)
     */
    public static String[] getStorageDirectories(Context context) {
        // Final set of paths
        final Set<String> finalAvailableDirectoriesSet = new LinkedHashSet<>();

        // add default external storage path (usually internal) first
        finalAvailableDirectoriesSet.add(Environment.getExternalStorageDirectory().getAbsolutePath());

        if (TextUtils.isEmpty(RAW_EMULATED_STORAGE_TARGET)) {
            File[] files = context.getExternalFilesDirs(null);
            for (File file : files) {
                if (file != null) {
                    // get root path of external files dir
                    String applicationSpecificAbsolutePath = file.getAbsolutePath();
                    String emulatedRootPath = applicationSpecificAbsolutePath.substring(
                        0, applicationSpecificAbsolutePath.indexOf("Android/data")
                    );
                    // strip trailing slash
                    finalAvailableDirectoriesSet.add(emulatedRootPath.replaceAll("/+$", ""));
                }
            }
        } else {
            // Device has emulated storage; external storage paths should have id in the last segment
            String rawStorageId = "";
            final String path = Environment.getExternalStorageDirectory().getAbsolutePath();
            final String[] folders = File.separator.split(path);
            final String lastSegment = folders[folders.length - 1];
            if (!TextUtils.isEmpty(lastSegment) && TextUtils.isDigitsOnly(lastSegment)) {
                rawStorageId = lastSegment;
            }
            // i.e: "/storage/emulated/storageId" where storageId is 0, 1, 2, ...
            if (TextUtils.isEmpty(rawStorageId)) {
                finalAvailableDirectoriesSet.add(RAW_EMULATED_STORAGE_TARGET);
            } else {
                finalAvailableDirectoriesSet.add(RAW_EMULATED_STORAGE_TARGET + File.separator + rawStorageId);
            }
        }
        // Add all secondary storages
        if (!TextUtils.isEmpty(RAW_SECONDARY_STORAGES)) {
            // All Secondary SD-CARDs split into array
            final String[] rawSecondaryStorages = RAW_SECONDARY_STORAGES.split(File.pathSeparator);
            Collections.addAll(finalAvailableDirectoriesSet, rawSecondaryStorages);
        }

        return finalAvailableDirectoriesSet.toArray(new String[finalAvailableDirectoriesSet.size()]);
    }
}

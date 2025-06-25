/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2025 Threema GmbH
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

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;

import java.io.File;

import androidx.annotation.NonNull;
import ch.threema.base.utils.LoggingUtil;

public class SystemUpdateToVersion63 implements SystemUpdate {
    private static final Logger logger = LoggingUtil.getThreemaLogger("SystemUpdateToVersion63");
    private @NonNull final Context context;

    public SystemUpdateToVersion63(@NonNull Context context) {
        this.context = context;
    }

    @Override
    public void run() {
        deleteDir(new File(context.getFilesDir(), "tmp"));
        deleteDir(new File(context.getExternalFilesDir(null), "data.blob"));
        deleteDir(new File(context.getExternalFilesDir(null), "tmp"));
    }

    private void deleteDir(File tmpPath) {
        if (tmpPath.exists()) {
            try {
                FileUtils.deleteDirectory(tmpPath);
            } catch (Exception e) {
                logger.error("Exception", e);
            }
        }
    }

    @Override
    public String getDescription() {
        return "delete obsolete temp directories";
    }

    @Override
    public int getVersion() {
        return 63;
    }
}

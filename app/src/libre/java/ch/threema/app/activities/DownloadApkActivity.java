/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2025 Threema GmbH
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

package ch.threema.app.activities;

import android.os.Bundle;

import org.slf4j.Logger;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import ch.threema.base.utils.LoggingUtil;

public class DownloadApkActivity extends AppCompatActivity {
    public static final String EXTRA_FORCE_UPDATE_DIALOG = "";
    // stub, download happens through f-droid store

    private static final Logger logger = LoggingUtil.getThreemaLogger("DownloadApkActivity");

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        logger.error("This activity may not be used in this build variant");

        finish();
    }
}

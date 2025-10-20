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

package ch.threema.app.utils;

import org.slf4j.Logger;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentActivity;
import ch.threema.app.R;
import ch.threema.app.dialogs.SimpleStringAlertDialog;
import ch.threema.base.utils.LoggingUtil;

public class LogUtil {
    private static final Logger logger = LoggingUtil.getThreemaLogger("LogUtil");

    private LogUtil() {
    }

    /**
     * Log an exception. Additionally, show an error message to the user.
     */
    public static void exception(Throwable e, FragmentActivity showInActivity) {
        exception(e, (AppCompatActivity) showInActivity);
    }

    /**
     * Log an exception. Additionally, show an error message to the user.
     */
    public static void exception(Throwable e, AppCompatActivity showInActivity) {
        String message;
        if (showInActivity != null) {
            if (e != null && !TestUtil.isEmptyOrNull(e.getMessage())) {
                message = showInActivity.getString(R.string.an_error_occurred_more, e.getMessage());
            } else {
                message = showInActivity.getString(R.string.an_error_occurred);
            }
        } else {
            message = e.getMessage();
        }
        logger.error("Exception", e);
        RuntimeUtil.runOnUiThread(() -> {
            if (showInActivity != null && !showInActivity.isFinishing()) {
                SimpleStringAlertDialog.newInstance(R.string.whoaaa, message)
                    .show(showInActivity.getSupportFragmentManager(), "tex");
            }
        });
    }

    /**
     * Log an error. Additionally, show an error message to the user.
     */
    public static void error(final String s, final AppCompatActivity showInActivity) {
        logger.error(s);
        RuntimeUtil.runOnUiThread(() -> {
            if (showInActivity != null && !showInActivity.isFinishing()) {
                SimpleStringAlertDialog.newInstance(R.string.whoaaa, s)
                    .show(showInActivity.getSupportFragmentManager(), "ter");
            }
        });
    }
}

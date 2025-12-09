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

import androidx.fragment.app.FragmentActivity;
import ch.threema.app.R;
import ch.threema.app.dialogs.SimpleStringAlertDialog;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

@Deprecated
public class LogUtil {
    private static final Logger logger = getThreemaLogger("LogUtil");

    private LogUtil() {
    }

    /**
     * Log an exception. Additionally, show an error message to the user.
     * <p>
     * Using this is discouraged, as it conflates the two unrelated concepts of logging and displaying UI,
     * while not allowing configuration of either.
     */
    @Deprecated
    public static void exception(Throwable e, FragmentActivity showInActivity) {
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
}

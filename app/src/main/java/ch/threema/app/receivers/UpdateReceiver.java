/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2024 Threema GmbH
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

package ch.threema.app.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.slf4j.Logger;

import ch.threema.app.utils.DownloadUtil;
import ch.threema.app.utils.PushUtil;
import ch.threema.base.utils.LoggingUtil;

public class UpdateReceiver extends BroadcastReceiver {
    private static final Logger logger = LoggingUtil.getThreemaLogger("UpdateReceiver");

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && intent.getAction() != null && intent.getAction().equals(Intent.ACTION_MY_PACKAGE_REPLACED)) {
            logger.info("*** App was updated ***");

            DownloadUtil.deleteOldAPKs(context);

            // force token register
            PushUtil.clearPushTokenSentDate(context);
        }
    }


}

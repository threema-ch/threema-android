/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2022 Threema GmbH
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
import android.os.PowerManager;

import org.slf4j.Logger;

import ch.threema.base.utils.LoggingUtil;

public class PowerSaveModeReceiver extends BroadcastReceiver {
	private static final Logger logger = LoggingUtil.getThreemaLogger("PowerSaveModeReceiver");

	@Override
	public void onReceive(final Context context, final Intent intent) {
		if (intent != null && intent.getAction() != null && intent.getAction().equals(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)) {
			final PowerManager pm = (PowerManager) context.getApplicationContext().getSystemService(Context.POWER_SERVICE);
			if (pm != null && pm.isPowerSaveMode()) {
				logger.info("Power Save Mode enabled");
			} else {
				logger.info("Power Save Mode disabled");
			}
		}
	}
}

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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.util.List;

public class  DeviceServiceImpl implements DeviceService {
	private Context context;
	private boolean isCanMakeCalls;
	private boolean isCanMakeCallsSet = false;

	public DeviceServiceImpl(Context context) {
		this.context = context;
	}

	public boolean isOnline() {
		ConnectivityManager cm = (ConnectivityManager) this.context.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (cm != null) {
			NetworkInfo netInfo = cm.getActiveNetworkInfo();
			return netInfo != null
					&& netInfo.isConnectedOrConnecting();
		}
		return false;
	}

	public boolean canMakeCalls() {
		if (!this.isCanMakeCallsSet) {
			if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
				Intent intent = new Intent(Intent.ACTION_DIAL);

				PackageManager manager = context.getPackageManager();
				List<ResolveInfo> list = manager.queryIntentActivities(intent, 0);

				this.isCanMakeCalls = list != null && list.size() > 0;
			} else {
				this.isCanMakeCalls = false;
			}
			this.isCanMakeCallsSet = true;
		}
		return this.isCanMakeCalls;
	}
}

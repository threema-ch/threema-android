/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2024 Threema GmbH
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
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.UiThread;
import ch.threema.app.R;
import ch.threema.domain.protocol.connection.ConnectionState;

public class ConnectionIndicatorUtil {
	private static ConnectionIndicatorUtil ourInstance;
	private final @ColorInt	int red, orange, transparent;

	public static ConnectionIndicatorUtil getInstance() {
		return ourInstance;
	}

	public static void init(Context context) {
		ConnectionIndicatorUtil.ourInstance = new ConnectionIndicatorUtil(context);
	}

	private ConnectionIndicatorUtil(Context context) {
		this.red = context.getResources().getColor(R.color.material_red);
		this.orange = context.getResources().getColor(R.color.material_orange);
		this.transparent = context.getResources().getColor(android.R.color.transparent);
	}

	@UiThread
	public void updateConnectionIndicator(View connectionIndicator, ConnectionState connectionState) {
		if (TestUtil.required(connectionIndicator)) {
			if (connectionState == ConnectionState.CONNECTED) {
				connectionIndicator.setBackgroundColor(this.orange);
			} else if (connectionState == ConnectionState.LOGGEDIN) {
				connectionIndicator.setBackgroundColor(this.transparent);
			} else {
				connectionIndicator.setBackgroundColor(this.red);
			}
			connectionIndicator.invalidate();
		}
	}
}

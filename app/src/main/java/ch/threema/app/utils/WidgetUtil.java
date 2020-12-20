/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2020 Threema GmbH
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

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.threema.app.R;
import ch.threema.app.receivers.WidgetProvider;

public class WidgetUtil {
	private static final Logger logger = LoggerFactory.getLogger(WidgetUtil.class);

	public static void updateWidgets(Context context) {
		logger.debug("Update Widgets");

		AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
		final int[] widgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context, WidgetProvider.class));

		for (int widgetId : widgetIds) {
			appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_list);
		}
	}
}

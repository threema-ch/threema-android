/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2021 Threema GmbH
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

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.widget.RemoteViews;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.threema.app.R;
import ch.threema.app.activities.ComposeMessageActivity;
import ch.threema.app.activities.HomeActivity;
import ch.threema.app.activities.RecipientListBaseActivity;
import ch.threema.app.services.WidgetService;

public class WidgetProvider extends AppWidgetProvider {
	private static final Logger logger = LoggerFactory.getLogger(WidgetProvider.class);

	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
		logger.debug("onUpdate");

		final String ACTION_OPEN = context.getPackageName() + ".ACTION_OPEN";

		// Perform this loop procedure for each App Widget that belongs to this provider
		for (int i = 0; i < appWidgetIds.length; i++) {
			int appWidgetId = appWidgetIds[i];

			Intent intent = new Intent(context, RecipientListBaseActivity.class);
			PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);

			Intent titleIntent = new Intent(context, HomeActivity.class);
			PendingIntent titlePendingIntent = PendingIntent.getActivity(context, 0, titleIntent, 0);

			// Get the layout for the App Widget and attach an on-click listener
			// to the button
			RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_messages);
			views.setOnClickPendingIntent(R.id.widget_edit, pendingIntent);
			views.setOnClickPendingIntent(R.id.widget_title, titlePendingIntent);

			// Set up the RemoteViews object to use a RemoteViews adapter.
			// This adapter connects
			// to a RemoteViewsService  through the specified intent.
			// This is how you populate the data.
			Intent svcIntent = new Intent(context, WidgetService.class);
			svcIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
			svcIntent.setData(Uri.parse(svcIntent.toUri(Intent.URI_INTENT_SCHEME)));

			logger.debug("setRemoteAdapter");
			views.setRemoteAdapter(R.id.widget_list, svcIntent);

			// The empty view is displayed when the collection has no items.
			// It should be in the same layout used to instantiate the RemoteViews
			// object above.
			views.setEmptyView(R.id.widget_list, R.id.empty_view);

			Intent itemIntent = new Intent(context, ComposeMessageActivity.class);
			itemIntent.setAction(ACTION_OPEN);
			itemIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
			itemIntent.setData((Uri.parse("foobar://" + SystemClock.elapsedRealtime())));
			itemIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
			PendingIntent itemPendingIntent = PendingIntent.getActivity(context, 0, itemIntent, PendingIntent.FLAG_UPDATE_CURRENT);
			views.setPendingIntentTemplate(R.id.widget_list, itemPendingIntent);

			// Tell the AppWidgetManager to perform an update on the current app widget
			appWidgetManager.updateAppWidget(appWidgetId, views);
		}

		super.onUpdate(context, appWidgetManager, appWidgetIds);
	}

	/*
	 * This is called when an instance the App Widget is created for the first time. For example,
	 * if the user adds two instances of your App Widget, this is only called the first time.
	 * If you need to open a new database or perform other setup that only needs to occur once for
	 * all App Widget instances, then this is a good place to do it.
	 */

	@Override
	public void onEnabled(Context context) {
		logger.debug("onEnabled");

		super.onEnabled(context);
	}
}

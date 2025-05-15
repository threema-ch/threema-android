/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2025 Threema GmbH
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

import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import ch.threema.app.adapters.WidgetViewsFactory;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;

public class WidgetService extends RemoteViewsService {
    private static final Logger logger = LoggingUtil.getThreemaLogger("WidgetService");

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        logger.debug("onGetViewFactory");
        try {
            return new WidgetViewsFactory(this.getApplicationContext());
        } catch (ThreemaException e) {
            logger.error("Could not create widget views factory");
            // We use an empty views factory as we cannot do anything if the actual views factory
            // cannot be created.
            return getEmptyViewsFactory();
        }
    }

    @NonNull
    private RemoteViewsFactory getEmptyViewsFactory() {
        return new RemoteViewsFactory() {
            @Override
            public void onCreate() {
                // Nothing to do
            }

            @Override
            public void onDataSetChanged() {
                // Nothing to do
            }

            @Override
            public void onDestroy() {
                // Nothing to do
            }

            @Override
            public int getCount() {
                return 0;
            }

            @Override
            public RemoteViews getViewAt(int i) {
                return null;
            }

            @Override
            public RemoteViews getLoadingView() {
                return null;
            }

            @Override
            public int getViewTypeCount() {
                return 0;
            }

            @Override
            public long getItemId(int i) {
                return 0;
            }

            @Override
            public boolean hasStableIds() {
                return false;
            }
        };
    }
}

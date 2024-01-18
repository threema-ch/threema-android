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

package ch.threema.app.services;

import android.content.Intent;
import android.widget.RemoteViewsService;

import org.slf4j.Logger;

import ch.threema.app.adapters.WidgetViewsFactory;
import ch.threema.base.utils.LoggingUtil;

public class WidgetService extends RemoteViewsService {
	private static final Logger logger = LoggingUtil.getThreemaLogger("WidgetService");

	@Override
	public RemoteViewsFactory onGetViewFactory(Intent intent) {
		logger.debug("onGetViewFactory");

		return(new WidgetViewsFactory(this.getApplicationContext(),
				intent));
	}
}

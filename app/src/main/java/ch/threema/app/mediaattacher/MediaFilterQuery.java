/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2023 Threema GmbH
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

package ch.threema.app.mediaattacher;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import java.lang.annotation.Retention;


public class MediaFilterQuery {

	@Retention(SOURCE)
	@IntDef({FILTER_MEDIA_TYPE, FILTER_MEDIA_BUCKET, FILTER_MEDIA_LABEL, FILTER_MEDIA_SELECTED, FILTER_MEDIA_DATE})
	public @interface FilterType {}
	public static final int FILTER_MEDIA_TYPE = 0;
	public static final int FILTER_MEDIA_BUCKET = 1;
	public static final int FILTER_MEDIA_LABEL = 2;
	public static final int FILTER_MEDIA_SELECTED = 3;
	public static final int FILTER_MEDIA_DATE = 4;

	public final String query;
	@FilterType
	public final int type;

	public MediaFilterQuery(@NonNull String query, @FilterType int type) {
		this.query = query;
		this.type = type;
	}

	public String getQuery() {
		return query;
	}

	@FilterType
	public int getType() {
		return type;
	}

}

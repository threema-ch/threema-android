/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2024 Threema GmbH
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

package ch.threema.storage.models;

import androidx.annotation.ColorInt;
import androidx.annotation.StringRes;

/**
 * Important: not stored in the db now
 * Only for conversation at this time
 */
public class TagModel {
    private final String tag;
    @ColorInt
    private final int primaryColor;
    @ColorInt
    private final int accentColor;
    @StringRes
    final int nameRes;

    public TagModel(String tag, @ColorInt int primaryColor, @ColorInt int accentColor, @StringRes int nameRes) {
        this.tag = tag;
        this.primaryColor = primaryColor;
        this.accentColor = accentColor;
        this.nameRes = nameRes;
    }

    public String getTag() {
        return this.tag;
    }

    public @ColorInt int getPrimaryColor() {
        return this.primaryColor;
    }

    public @ColorInt int getAccentColor() {
        return this.accentColor;
    }

    public @StringRes int getNameRes() {
        return this.nameRes;
    }
}

/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2020 Threema GmbH
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

/**
 * Important: not stored in the db now
 * Only for conversation at this time
 */
public class TagModel {
	private final String tag;
	private final int primaryColor;
	private final int accentColor;
	private final String name;

	public TagModel(String tag, int primaryColor, int accentColor, String name){
		this.tag = tag;
		this.primaryColor = primaryColor;
		this.accentColor = accentColor;
		this.name = name;
	}

	public String getTag() {
		return this.tag;
	}

	public int getPrimaryColor() {
		return this.primaryColor;
	}

	public int getAccentColor() {
		return this.accentColor;
	}

	public String getName() {
		return this.name;
	}
}

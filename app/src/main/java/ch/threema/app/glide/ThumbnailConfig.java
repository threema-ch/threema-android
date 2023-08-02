/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023 Threema GmbH
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

package ch.threema.app.glide;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ch.threema.storage.models.MessageModel;

public class ThumbnailConfig {
	final MessageModel model;

	public ThumbnailConfig(@NonNull MessageModel model) {
		this.model = model;
	}

	@Override
	public boolean equals(@Nullable Object obj) {
		return obj instanceof  MessageModel && ((MessageModel) obj).getId() == model.getId();
	}

	@Override
	public int hashCode() {
		return model.getId();
	}
}

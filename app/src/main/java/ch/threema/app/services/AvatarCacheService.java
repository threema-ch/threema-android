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

import android.graphics.Bitmap;

import java.util.Collection;

import androidx.annotation.NonNull;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.GroupModel;

public interface AvatarCacheService {

	int CONTACT_AVATAR = 0;
	int AVATAR_GROUP = 1;
	int AVATAR_BUSINESS = 2;

	Bitmap getContactAvatarHigh(ContactModel contactModel);
	Bitmap getContactAvatarLow(ContactModel contactModel);
	Bitmap getContactAvatarLowFromCache(ContactModel contactModel);
	void reset(ContactModel contactModel);

	Bitmap getGroupAvatarHigh(@NonNull GroupModel groupModel, Collection<Integer> contactColors, boolean defaultOnly);
	Bitmap getGroupAvatarLow(@NonNull GroupModel groupModel, Collection<Integer> contactColors, boolean defaultOnly);
	Bitmap getGroupAvatarLowFromCache(GroupModel groupModel);
	Bitmap getGroupAvatarNeutral(boolean highResolution);

	void reset(GroupModel groupModel);

	Bitmap getDistributionListAvatarLow(DistributionListModel distributionListModel, int[] contactColors);
	Bitmap getDistributionListAvatarLowFromCache(DistributionListModel distributionListModel);

	void clear();

	Bitmap buildHiresDefaultAvatar(int color, int avatarType);

	boolean getDefaultAvatarColored();
}

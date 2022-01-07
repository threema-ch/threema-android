/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2022 Threema GmbH
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

package ch.threema.app.services.group;

import android.content.Context;
import android.net.Uri;

import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.exceptions.PolicyViolationException;
import ch.threema.domain.protocol.csp.messages.group.GroupInviteData;
import ch.threema.domain.protocol.csp.messages.group.GroupInviteToken;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.group.GroupInviteModel;
import java8.util.Optional;

public interface GroupInviteService {

	@NonNull GroupInviteModel createGroupInvite(@NonNull GroupModel groupModel,boolean isDefault) throws
		PolicyViolationException,
		GroupInviteToken.InvalidGroupInviteTokenException,
		IOException, GroupInviteModel.MissingRequiredArgumentsException;

	@Nullable
	Optional<GroupInviteModel> getDefaultGroupInvite(@NonNull GroupModel groupModel);

	int getCustomLinksCount();

	void deleteDefaultLink(GroupModel groupModel);

	GroupInviteModel resetDefaultGroupInvite(@NonNull GroupModel groupModel) throws IOException, GroupInviteToken.InvalidGroupInviteTokenException, GroupInviteModel.MissingRequiredArgumentsException;

	GroupInviteModel createOrEnableDefaultLink(GroupModel groupModel) throws IOException, GroupInviteToken.InvalidGroupInviteTokenException, GroupInviteModel.MissingRequiredArgumentsException;

	@NonNull Uri encodeGroupInviteLink(@NonNull GroupInviteModel groupInviteModel);

	@NonNull
	GroupInviteData decodeGroupInviteLink(@NonNull String encodedString) throws IOException, IllegalStateException, GroupInviteToken.InvalidGroupInviteTokenException;

	void shareGroupLink(@NonNull Context context, @NonNull GroupInviteModel groupInviteModel);
}

/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2024 Threema GmbH
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

import androidx.annotation.NonNull;
import ch.threema.base.ThreemaException;
import ch.threema.domain.protocol.csp.messages.group.GroupJoinRequestMessage;
import ch.threema.storage.models.group.IncomingGroupJoinRequestModel;

public interface IncomingGroupJoinRequestService {
	// TODO(ANDR-2607): include into tasks
	@NonNull boolean process(@NonNull GroupJoinRequestMessage message);

	void accept(@NonNull IncomingGroupJoinRequestModel model) throws Exception;

	void reject(@NonNull IncomingGroupJoinRequestModel model) throws ThreemaException;
}

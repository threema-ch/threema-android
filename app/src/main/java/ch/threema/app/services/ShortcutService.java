/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2022 Threema GmbH
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

import android.os.BaseBundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.storage.models.AbstractMessageModel;

public interface ShortcutService {
	int TYPE_NONE = 0;
	int TYPE_CHAT = 1;
	int TYPE_CALL = 2;

	/* pinned shortcuts */
	@WorkerThread void createPinnedShortcut(MessageReceiver<? extends AbstractMessageModel> messageReceiver, int type);
	@WorkerThread void updatePinnedShortcut(MessageReceiver<? extends AbstractMessageModel> messageReceiver);
	@WorkerThread void deletePinnedShortcut(MessageReceiver<? extends AbstractMessageModel> messageReceiver);

	/* dynamic shortcuts (share targets) */
	@WorkerThread void publishRecentChatsAsShareTargets();
	@WorkerThread void deleteAllShareTargetShortcuts();
	@Nullable BaseBundle getShareTargetExtrasFromShortcutId(@NonNull String id);
}

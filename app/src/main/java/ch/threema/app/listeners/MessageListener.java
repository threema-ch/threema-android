/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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

package ch.threema.app.listeners;

import java.util.List;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import ch.threema.storage.models.AbstractMessageModel;

/**
 * Listen for new, changed or removed messages.
 */
public interface MessageListener {
    @AnyThread
    void onNew(AbstractMessageModel newMessage);

    @AnyThread
    void onModified(List<AbstractMessageModel> modifiedMessageModel);

    @AnyThread
    void onRemoved(AbstractMessageModel removedMessageModel);

    @AnyThread
    void onRemoved(List<AbstractMessageModel> removedMessageModels);

    @AnyThread
    void onProgressChanged(AbstractMessageModel messageModel, int newProgress);

    @AnyThread
    void onResendDismissed(@NonNull AbstractMessageModel messageModel);
}

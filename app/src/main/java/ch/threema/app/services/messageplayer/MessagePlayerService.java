/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2023 Threema GmbH
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

package ch.threema.app.services.messageplayer;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.storage.models.AbstractMessageModel;

public interface MessagePlayerService {
	MessagePlayer createPlayer(AbstractMessageModel m, Activity activity, MessageReceiver messageReceiver);

	void release();
	void stopAll();
	void pauseAll(int source);
	void resumeAll(Activity activity, MessageReceiver messageReceiver, int source);

	void setTranscodeProgress(@NonNull AbstractMessageModel messageModel, int progress);
	void setTranscodeStart(@NonNull AbstractMessageModel messageModel);
	void setTranscodeFinished(@NonNull AbstractMessageModel messageModel, boolean success, @Nullable String message);
}

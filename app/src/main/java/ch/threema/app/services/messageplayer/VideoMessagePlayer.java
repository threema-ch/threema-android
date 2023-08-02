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

import android.content.Context;
import android.content.Intent;

import java.io.File;

import ch.threema.app.activities.MediaViewerActivity;
import ch.threema.app.activities.ThreemaActivity;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.FileService;
import ch.threema.app.services.MessageService;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.data.media.MediaMessageDataInterface;
import ch.threema.storage.models.data.media.VideoDataModel;

public class VideoMessagePlayer extends MessagePlayer {

	protected VideoMessagePlayer(Context context, MessageService messageService, FileService fileService, MessageReceiver messageReceiver, AbstractMessageModel messageModel) {
		super(context, messageService, fileService, messageReceiver, messageModel);
	}

	@Override
	public MediaMessageDataInterface getData() {
		return this.getMessageModel().getVideoData();
	}

	@Override
	protected AbstractMessageModel setData(MediaMessageDataInterface data) {
		AbstractMessageModel messageModel =  this.getMessageModel();
		messageModel.setVideoData((VideoDataModel)data);
		return messageModel;
	}

	@Override
	protected void play(boolean autoPlay) {
		if (this.isReceiverMatch(this.currentMessageReceiver)) {
			RuntimeUtil.runOnUiThread(() -> {
				if (currentActivityRef != null && currentActivityRef.get() != null) {
					Intent intent = new Intent(getContext(), MediaViewerActivity.class);
					IntentDataUtil.append(getMessageModel(), intent);
					intent.putExtra(MediaViewerActivity.EXTRA_ID_IMMEDIATE_PLAY, true);
					intent.putExtra(MediaViewerActivity.EXTRA_ID_REVERSE_ORDER, true);
					currentActivityRef.get().startActivityForResult(intent, ThreemaActivity.ACTIVITY_ID_MEDIA_VIEWER);
				}
			});
		}
	}

	@Override
	protected void open(final File decryptedFile) {
		// not implemented - the gallery will handle the decryption
	}

	@Override
	protected void makePause(int source) {
		//not implemented
	}

	@Override
	protected void makeResume(int source) {
		//not implemented
	}

	@Override
	public void seekTo(int pos) {

	}

	@Override
	public int getDuration() {
		return 0;
	}

	@Override
	public int getPosition() {
		return 0;
	}
}

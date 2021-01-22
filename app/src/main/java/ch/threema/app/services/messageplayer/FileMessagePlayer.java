/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2021 Threema GmbH
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
import ch.threema.app.utils.AnimationUtil;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.data.media.FileDataModel;
import ch.threema.storage.models.data.media.MediaMessageDataInterface;

public class FileMessagePlayer extends MessagePlayer {
	protected FileService fileService;
	protected MessageService messageService;

	protected FileMessagePlayer(Context context, MessageService messageService, FileService fileService, MessageReceiver messageReceiver, AbstractMessageModel messageModel) {
		super(context, messageService, fileService, messageReceiver, messageModel);
		this.fileService = fileService;
		this.messageService = messageService;
	}

	@Override
	public MediaMessageDataInterface getData() {
		return this.getMessageModel().getFileData();
	}

	@Override
	protected AbstractMessageModel setData(MediaMessageDataInterface data) {
		AbstractMessageModel messageModel =  this.getMessageModel();
		messageModel.setFileData((FileDataModel) data);
		return messageModel;
	}

	@Override
	protected void play(boolean autoPlay) {
		if (!autoPlay && this.isReceiverMatch(this.currentMessageReceiver)) {
			if (FileUtil.isImageFile(getMessageModel().getFileData()) || FileUtil.isAudioFile(getMessageModel().getFileData()) || FileUtil.isVideoFile(getMessageModel().getFileData())) {
				RuntimeUtil.runOnUiThread(() -> {
					if (currentActivityRef != null && currentActivityRef.get() != null) {
						Intent intent = new Intent(getContext(), MediaViewerActivity.class);
						IntentDataUtil.append(getMessageModel(), intent);
						intent.putExtra(MediaViewerActivity.EXTRA_ID_IMMEDIATE_PLAY, true);
						intent.putExtra(MediaViewerActivity.EXTRA_ID_REVERSE_ORDER, true);
						AnimationUtil.startActivityForResult(currentActivityRef.get(), null, intent, ThreemaActivity.ACTIVITY_ID_MEDIA_VIEWER);
					}
				});
				// don't call super - the gallery will handle the decryption
				return;
			}
		}
		super.play(autoPlay);
	}

	@Override
	protected void open(final File decryptedFile) {
		if (this.isReceiverMatch(this.currentMessageReceiver)) {
			RuntimeUtil.runOnUiThread(() -> {
				final String mimeType = getMessageModel().getFileData().getMimeType();

				if (!TestUtil.empty(mimeType) && decryptedFile.exists()) {
					if (!FileUtil.isImageFile(getMessageModel().getFileData()) && !FileUtil.isVideoFile(getMessageModel().getFileData())) {
						messageService.viewMediaMessage(getContext(), getMessageModel(), fileService.getShareFileUri(decryptedFile, null));
					}
				}
			});
		}
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

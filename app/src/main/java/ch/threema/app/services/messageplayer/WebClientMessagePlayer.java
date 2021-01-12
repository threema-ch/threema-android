/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2021 Threema GmbH
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

import java.io.File;

import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.FileService;
import ch.threema.app.services.MessageService;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.data.media.MediaMessageDataInterface;

/**
 * A subclass of the MessagePlayer made for downloading files and sending them to Threema Web.
 */
public class WebClientMessagePlayer extends MessagePlayer {
	public WebClientMessagePlayer(Context context,
	                                 MessageService messageService,
	                                 FileService fileService,
	                                 MessageReceiver messageReceiver,
	                                 AbstractMessageModel messageModel) {
		super(context, messageService, fileService, messageReceiver, messageModel);
	}

	@Override
	protected MediaMessageDataInterface getData() {
		switch (getMessageModel().getType()) {
			case VOICEMESSAGE:
				return this.getMessageModel().getAudioData();
			case FILE:
				return this.getMessageModel().getFileData();
			case VIDEO:
				return this.getMessageModel().getVideoData();
			case IMAGE:
				return new MediaMessageDataInterface() {
					@Override
					public byte[] getEncryptionKey() {
						return new byte[0];
					}

					@Override
					public byte[] getBlobId() {
						return new byte[0];
					}

					@Override
					public boolean isDownloaded() {
						return true;
					}

					@Override
					public void isDownloaded(boolean isDownloaded) { }

					@Override
					public byte[] getNonce() { return new byte[0]; }
				};
		}
		return null;
	}

	@Override
	protected AbstractMessageModel setData(MediaMessageDataInterface data) {
		return null;
	}

	@Override
	protected void open(File decryptedFile) { }

	@Override
	protected void makePause(int source) { }

	@Override
	protected void makeResume(int source) { }

	@Override
	public void seekTo(int pos) { }

	@Override
	public int getDuration() {
		return 0;
	}

	@Override
	public int getPosition() {
		return 0;
	}
}

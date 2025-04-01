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

package ch.threema.app.services.messageplayer;

import android.content.Context;

import java.io.File;

import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.FileService;
import ch.threema.app.services.MessageService;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.data.media.ImageDataModel;
import ch.threema.storage.models.data.media.MediaMessageDataInterface;

public class ImageMessagePlayer extends MessagePlayer {

    protected ImageMessagePlayer(Context context, MessageService messageService, FileService fileService, MessageReceiver messageReceiver, AbstractMessageModel messageModel) {
        super(context, messageService, fileService, messageReceiver, messageModel);
    }

    @Override
    public MediaMessageDataInterface getData() {
        return this.getMessageModel().getImageData();
    }

    @Override
    protected AbstractMessageModel setData(MediaMessageDataInterface data) {
        AbstractMessageModel messageModel = this.getMessageModel();
        messageModel.setImageData((ImageDataModel) data);
        return messageModel;
    }

    @Override
    protected void play(boolean autoPlay) {
        // do not play after downloading
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

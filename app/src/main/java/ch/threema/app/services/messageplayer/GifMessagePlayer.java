/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2022 Threema GmbH
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
import android.net.Uri;
import android.widget.ImageView;

import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;

import ch.threema.app.activities.MediaViewerActivity;
import ch.threema.app.activities.ThreemaActivity;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.FileService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.utils.AnimationUtil;
import ch.threema.app.utils.ImageViewUtil;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.data.media.FileDataModel;
import ch.threema.storage.models.data.media.MediaMessageDataInterface;
import pl.droidsonroids.gif.GifDrawable;

public class GifMessagePlayer extends MessagePlayer {
	private static final Logger logger = LoggingUtil.getThreemaLogger("GifMessagePlayer");

	private final PreferenceService preferenceService;
	private GifDrawable gifDrawable;
	private ImageView imageContainer;

	protected GifMessagePlayer(Context context,
							   MessageService messageService,
							   FileService fileService,
							   PreferenceService preferenceService,
							   MessageReceiver messageReceiver,
							   AbstractMessageModel messageModel) {
		super(context, messageService, fileService, messageReceiver, messageModel);
		this.preferenceService = preferenceService;
	}

	public GifMessagePlayer attachContainer(ImageView container) {
		this.imageContainer = container;
		return this;
	}

	@Override
	public MediaMessageDataInterface getData() {
		return this.getMessageModel().getFileData();
	}

	@Override
	protected AbstractMessageModel setData(MediaMessageDataInterface data) {
		AbstractMessageModel messageModel = this.getMessageModel();
		messageModel.setFileData((FileDataModel) data);
		return messageModel;
	}

	@Override
	protected void open(final File decryptedFile) {
		logger.debug("open(decryptedFile)");
		if (this.currentActivityRef != null && this.currentActivityRef.get() != null && this.isReceiverMatch(this.currentMessageReceiver)) {
			final String mimeType = getMessageModel().getFileData().getMimeType();

			if (!TestUtil.empty(mimeType) && decryptedFile.exists()) {
				if (preferenceService.isGifAutoplay()) {
					autoPlay(decryptedFile);
				} else {
					openInExternalPlayer(decryptedFile);
				}
			}
		}
	}

	public void autoPlay(final File decryptedFile) {
		logger.debug("autoPlay(decryptedFile)");

		if (this.imageContainer != null && this.currentActivityRef != null && this.currentActivityRef.get() != null) {
			if (this.gifDrawable != null && !gifDrawable.isRecycled()) {
				this.gifDrawable.stop();
			}

			final Uri uri = Uri.parse(decryptedFile.getPath());
			try {
				this.gifDrawable = new GifDrawable(uri.getPath());
				this.gifDrawable.setCornerRadius(ImageViewUtil.getCornerRadius(getContext()));
			} catch (IOException e) {
				logger.error("I/O Exception", e);
				return;
			}

			RuntimeUtil.runOnUiThread(() -> {
				if (gifDrawable != null && !gifDrawable.isRecycled()) {
					imageContainer.setImageDrawable(gifDrawable);
					if (preferenceService.isGifAutoplay()) {
						gifDrawable.start();
					}
				}
			});
		}
	}

	@Override
	public boolean open() {
		logger.debug("open");

		return super.open();
	}

	public boolean autoPlay() {
		logger.debug("autoPlay");

		return super.open(true);
	}

	public void openInExternalPlayer(File decryptedFile) {
		RuntimeUtil.runOnUiThread(() -> {
			if (currentActivityRef != null && currentActivityRef.get() != null && this.isReceiverMatch(currentMessageReceiver)) {
				Intent intent = new Intent(getContext(), MediaViewerActivity.class);
				IntentDataUtil.append(getMessageModel(), intent);
				intent.putExtra(MediaViewerActivity.EXTRA_ID_REVERSE_ORDER, true);
				AnimationUtil.startActivityForResult(currentActivityRef.get(), null, intent, ThreemaActivity.ACTIVITY_ID_MEDIA_VIEWER);
			}
		});
	}

	@Override
	protected void makePause(int source) {
		logger.debug("makePause");
		if (this.imageContainer != null) {
			if(this.gifDrawable != null && this.gifDrawable.isPlaying() && ! gifDrawable.isRecycled()) {
				this.gifDrawable.pause();
			}
		}
	}

	@Override
	protected void makeResume(int source) {
		logger.debug("makeResume: " + getMessageModel().getId());
		if (this.imageContainer != null) {
			if(this.gifDrawable != null && !this.gifDrawable.isPlaying() && !gifDrawable.isRecycled()) {
				this.gifDrawable.start();
			}
		}
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

	@Override
	public void removeListeners() {
		super.removeListeners();
		logger.debug("removeListeners");

		// release animgif players if item comes out of view
		if (this.gifDrawable != null && !this.gifDrawable.isRecycled()) {
			this.gifDrawable.stop();
			this.gifDrawable.recycle();
			this.gifDrawable = null;
		}
	}
}

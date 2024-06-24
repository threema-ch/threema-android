/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2024 Threema GmbH
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
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.session.MediaController;

import com.google.common.util.concurrent.ListenableFuture;

import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.DeadlineListService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.MimeUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.csp.messages.file.FileData;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.MessageType;

public class MessagePlayerServiceImpl implements MessagePlayerService {
	private static final Logger logger = LoggingUtil.getThreemaLogger("MessagePlayerServiceImpl");

	private final Map<Integer, MessagePlayer> messagePlayers = new HashMap<>();
	private final Context context;
	private final MessageService messageService;
	private final FileService fileService;
	private final PreferenceService preferenceService;
	private final DeadlineListService hiddenChatsListService;

	public MessagePlayerServiceImpl(Context context, MessageService messageService, FileService fileService, PreferenceService preferenceService, DeadlineListService hiddenChatsListService) {
		this.context = context;
		this.messageService = messageService;
		this.fileService = fileService;
		this.preferenceService = preferenceService;
		this.hiddenChatsListService = hiddenChatsListService;
	}

	@Override
	public MessagePlayer createPlayer(AbstractMessageModel messageModel, Activity activity, MessageReceiver<?> messageReceiver, ListenableFuture<MediaController> mediaControllerFuture) {
		int key = messageModel.getId();
		MessagePlayer o = null;

		synchronized (this.messagePlayers) {
			o = this.messagePlayers.get(key);

			if (o == null) {
				if (messageModel.getType() == MessageType.IMAGE) {
					o = new ImageMessagePlayer(
							this.context,
							this.messageService,
							this.fileService,
							messageReceiver,
							messageModel
					);
				} else if (messageModel.getType() == MessageType.VOICEMESSAGE) {
					o = new AudioMessagePlayer(
							this.context,
							this.messageService,
							this.fileService,
							this.preferenceService,
							this.hiddenChatsListService,
							messageReceiver,
							mediaControllerFuture,
							messageModel
					);
				} else if (messageModel.getType() == MessageType.VIDEO) {
					o = new VideoMessagePlayer(
							this.context,
							this.messageService,
							this.fileService,
							messageReceiver,
							messageModel
					);
				} else if (messageModel.getType() == MessageType.FILE) {
					if (MimeUtil.isAudioFile(messageModel.getFileData().getMimeType())
							&& messageModel.getFileData().getRenderingType() == FileData.RENDERING_MEDIA) {
						o = new AudioMessagePlayer(
							this.context,
							this.messageService,
							this.fileService,
							this.preferenceService,
							this.hiddenChatsListService,
							messageReceiver,
							mediaControllerFuture,
							messageModel
						);
					} else if (MimeUtil.isAnimatedImageFormat(messageModel.getFileData().getMimeType())
							&& (messageModel.getFileData().getRenderingType() == FileData.RENDERING_MEDIA
							|| messageModel.getFileData().getRenderingType() == FileData.RENDERING_STICKER)) {
						o = new AnimatedImageDrawableMessagePlayer(
							this.context,
							this.messageService,
							this.fileService,
							this.preferenceService,
							messageReceiver,
							messageModel
						);
					} else {
						o = new FileMessagePlayer(
								this.context,
								this.messageService,
								this.fileService,
								messageReceiver,
								messageModel
						);
					}
				}
				logger.debug("creating new player " + key);
			} else {
				// make sure data model is updated as its status may have changed after the player has been created
				if (messageModel.getType() == MessageType.VOICEMESSAGE) {
					o.setData(messageModel.getAudioData());
				}
				if (messageModel.getType() == MessageType.FILE &&
					MimeUtil.isAudioFile(messageModel.getFileData().getMimeType())	&&
					messageModel.getFileData().getRenderingType() == FileData.RENDERING_MEDIA) {
					o.setData(messageModel.getFileData());
				}
				logger.debug("recycling existing player {}", key);
			}
			if (o != null) {
				if (activity != null) {
					if (o.isReceiverMatch(messageReceiver)) {
						o.setCurrentActivity(activity, messageReceiver);
					} else {
						o.release();
					}
				}
				this.messagePlayers.put(key, o);
			}
		}
		if (o != null) {
			o.addListener("service", new MessagePlayer.PlaybackListener() {
				@Override
				public void onPlay(AbstractMessageModel messageModel, boolean autoPlay) {
					//call stop other players first!
					logger.debug("onPlay autoPlay = " + autoPlay);

					if (!autoPlay) {
						stopOtherPlayers(messageModel);
					}
				}

				@Override
				public void onPause(AbstractMessageModel messageModel) {
				}

				@Override
				public void onStatusUpdate(AbstractMessageModel messageModel, int position) {
				}

				@Override
				public void onStop(AbstractMessageModel messageModel) {
					logger.debug("onStop");
				}
			});
		}
		return o;
	}

	private void stopOtherPlayers(AbstractMessageModel messageModel) {
		logger.debug("stopOtherPlayers");
		synchronized (this.messagePlayers) {
			for (Map.Entry<Integer, MessagePlayer> entry : messagePlayers.entrySet()) {
				if (!entry.getKey().equals(messageModel.getId())) {
					logger.debug("maybe stopping player {} if not running ", entry.getKey());
					entry.getValue().stop();
				}
			}
		}
		logger.debug("otherPlayers stopped");
	}

	@Override
	public void release() {
		logger.debug("release all players");
		synchronized (this.messagePlayers) {
			Iterator<Map.Entry<Integer, MessagePlayer>> iterator = messagePlayers.entrySet().iterator();
			while (iterator.hasNext()) {
				Map.Entry<Integer, MessagePlayer> pair = iterator.next();
				MessagePlayer mp = pair.getValue();
				mp.stop();
				if (mp.release()) {
					iterator.remove();
					logger.debug("Releasing player " + pair.getKey());
				} else {
					// remove ties to activity
					mp.setCurrentActivity(null, null);
					mp.removeListeners();
					logger.debug("Keep downloading player " + pair.getKey());
				}
			}
		}
	}

	@Override
	public void stopAll() {
		logger.debug("stop all players");
		synchronized (this.messagePlayers) {
			for (Map.Entry<Integer, MessagePlayer> entry : messagePlayers.entrySet()) {
				entry.getValue().stop();
			}
		}
	}

	@Override
	public void pauseAll(int source) {
		logger.debug("pause all players");
		synchronized (this.messagePlayers) {
			for (Map.Entry<Integer, MessagePlayer> entry : messagePlayers.entrySet()) {
				entry.getValue().pause(source);
			}
		}
	}

	@Override
	public void resumeAll(Activity activity, MessageReceiver messageReceiver, int source) {
		logger.debug("resume all players");
		synchronized (this.messagePlayers) {
			for (Map.Entry<Integer, MessagePlayer> entry : messagePlayers.entrySet()) {
				// re-attach message players to current activity
				if (entry.getValue().isReceiverMatch(messageReceiver)) {
					entry.getValue().setCurrentActivity(activity, messageReceiver);
					entry.getValue().resume(source);
				} else {
					entry.getValue().release();
				}
			}
		}
	}

	@Override
	public void setTranscodeProgress(@NonNull AbstractMessageModel messageModel, int progress) {
		synchronized (this.messagePlayers) {
			for (Map.Entry<Integer, MessagePlayer> entry : messagePlayers.entrySet()) {
				if (entry.getKey().equals(messageModel.getId())) {
					entry.getValue().setTranscodeProgress(progress);
					return;
				}
			}
		}
	}

	@Override
	public void setTranscodeStart(@NonNull AbstractMessageModel messageModel) {
		synchronized (this.messagePlayers) {
			for (Map.Entry<Integer, MessagePlayer> entry : messagePlayers.entrySet()) {
				if (entry.getKey().equals(messageModel.getId())) {
					entry.getValue().setTranscodeStart();
					return;
				}
			}
		}
	}

	@Override
	public void setTranscodeFinished(@NonNull AbstractMessageModel messageModel, boolean success, @Nullable String message) {
		synchronized (this.messagePlayers) {
			for (Map.Entry<Integer, MessagePlayer> entry : messagePlayers.entrySet()) {
				if (entry.getKey().equals(messageModel.getId())) {
					entry.getValue().setTranscodeFinished(success, message);
					return;
				}
			}
		}
	}
}

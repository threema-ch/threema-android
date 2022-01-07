/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2022 Threema GmbH
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.FileService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.utils.MimeUtil;
import ch.threema.domain.protocol.csp.messages.file.FileData;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.MessageType;

public class MessagePlayerServiceImpl implements MessagePlayerService {
	private static final Logger logger = LoggerFactory.getLogger(MessagePlayerServiceImpl.class);

	private final Map<String, MessagePlayer> messagePlayers = new HashMap<>();
	private final Context context;
	private final MessageService messageService;
	private final FileService fileService;
	private final PreferenceService preferenceService;

	public MessagePlayerServiceImpl(Context context, MessageService messageService, FileService fileService, PreferenceService preferenceService) {
		this.context = context;
		this.messageService = messageService;
		this.fileService = fileService;
		this.preferenceService = preferenceService;
	}

	@Override
	public MessagePlayer createPlayer(AbstractMessageModel m, Activity activity, MessageReceiver messageReceiver) {
		String key = m.getUid();
		MessagePlayer o = null;

		synchronized (this.messagePlayers) {
			o = this.messagePlayers.get(key);

			if (o == null) {
				if (m.getType() == MessageType.IMAGE) {
					o = new ImageMessagePlayer(
							this.context,
							this.messageService,
							this.fileService,
							messageReceiver,
							m
					);
				} else if (m.getType() == MessageType.VOICEMESSAGE) {
					o = new AudioMessagePlayer(
							this.context,
							this.messageService,
							this.fileService,
							this.preferenceService,
							messageReceiver,
							m
					);
				} else if (m.getType() == MessageType.VIDEO) {
					o = new VideoMessagePlayer(
							this.context,
							this.messageService,
							this.fileService,
							messageReceiver,
							m
					);
				} else if (m.getType() == MessageType.FILE) {
					if (MimeUtil.isGifFile(m.getFileData().getMimeType())) {
						o = new GifMessagePlayer(
							this.context,
							this.messageService,
							this.fileService,
							this.preferenceService,
							messageReceiver,
							m
						);
					} else if (MimeUtil.isAudioFile(m.getFileData().getMimeType())
							&& m.getFileData().getRenderingType() == FileData.RENDERING_MEDIA) {
						o = new AudioMessagePlayer(
							this.context,
							this.messageService,
							this.fileService,
							this.preferenceService,
							messageReceiver,
							m
						);
					} else {
						o = new FileMessagePlayer(
								this.context,
								this.messageService,
								this.fileService,
								messageReceiver,
								m
						);
					}
				}
				logger.debug("creating new player " + key);
			} else {
				// make sure data model is updated as its status may have changed after the player has been created
				if (m.getType() == MessageType.VOICEMESSAGE) {
					o.setData(m.getAudioData());
				}
				if (m.getType() == MessageType.FILE &&
					MimeUtil.isAudioFile(m.getFileData().getMimeType())	&&
					m.getFileData().getRenderingType() == FileData.RENDERING_MEDIA) {
					o.setData(m.getFileData());
				}
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
			for (Map.Entry<String, MessagePlayer> entry : messagePlayers.entrySet()) {
				if (!entry.getKey().equals(messageModel.getUid())) {
					if (!(entry.getValue() instanceof GifMessagePlayer)) {
						logger.debug("stopping player " + entry.getKey());

						entry.getValue().stop();
					}
				}
			}
		}
		logger.debug("otherPlayers stopped");
	}

	@Override
	public void release() {
		logger.debug("release all players");
		synchronized (this.messagePlayers) {
			Iterator iterator = messagePlayers.entrySet().iterator();
			while (iterator.hasNext()) {
				Map.Entry pair = (Map.Entry) iterator.next();
				MessagePlayer mp = (MessagePlayer) pair.getValue();
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
			for (Map.Entry<String, MessagePlayer> entry : messagePlayers.entrySet()) {
				entry.getValue().stop();
			}
		}
	}

	@Override
	public void pauseAll(int source) {
		logger.debug("pause all players");
		synchronized (this.messagePlayers) {
			for (Map.Entry<String, MessagePlayer> entry : messagePlayers.entrySet()) {
				entry.getValue().pause(true, source);
			}
		}
	}

	@Override
	public void resumeAll(Activity activity, MessageReceiver messageReceiver, int source) {
		logger.debug("resume all players");
		synchronized (this.messagePlayers) {
			for (Map.Entry<String, MessagePlayer> entry : messagePlayers.entrySet()) {
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
			for (Map.Entry<String, MessagePlayer> entry : messagePlayers.entrySet()) {
				if (entry.getKey().equals(messageModel.getUid())) {
					entry.getValue().setTranscodeProgress(progress);
					return;
				}
			}
		}
	}

	@Override
	public void setTranscodeStart(@NonNull AbstractMessageModel messageModel) {
		synchronized (this.messagePlayers) {
			for (Map.Entry<String, MessagePlayer> entry : messagePlayers.entrySet()) {
				if (entry.getKey().equals(messageModel.getUid())) {
					entry.getValue().setTranscodeStart();
					return;
				}
			}
		}
	}

	@Override
	public void setTranscodeFinished(@NonNull AbstractMessageModel messageModel, boolean success, @Nullable String message) {
		synchronized (this.messagePlayers) {
			for (Map.Entry<String, MessagePlayer> entry : messagePlayers.entrySet()) {
				if (entry.getKey().equals(messageModel.getUid())) {
					entry.getValue().setTranscodeFinished(success, message);
					return;
				}
			}
		}
	}
}

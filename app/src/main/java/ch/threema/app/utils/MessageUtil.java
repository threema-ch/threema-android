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

package ch.threema.app.utils;

import android.content.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.R;
import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.client.ProtocolDefines;
import ch.threema.client.file.FileData;
import ch.threema.client.voip.VoipCallAnswerData;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.MessageModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.data.status.VoipStatusDataModel;

public class MessageUtil {
	private static final Logger logger = LoggerFactory.getLogger(MessageUtil.class);

	private final static java.util.Set<MessageType> fileMessageModelTypes = EnumSet.of(
			MessageType.IMAGE,
			MessageType.VOICEMESSAGE,
			MessageType.VIDEO,
			MessageType.FILE);

	private final static java.util.Set<MessageType> thumbnailFileMessageModelTypes = EnumSet.of(
			MessageType.IMAGE,
			MessageType.VIDEO,
			MessageType.FILE);

	private final static java.util.Set<MessageType> lowProfileMessageModelTypes = EnumSet.of(
			MessageType.IMAGE,
			MessageType.VOICEMESSAGE);

	public static String getDisplayDate(Context context, AbstractMessageModel messageModel, boolean full) {
		if (messageModel == null) {
			return "";
		}

		Date d = messageModel.getPostedAt();

		if(messageModel.isOutbox()) {
			if(messageModel.getModifiedAt() != null) {
				d = messageModel.getModifiedAt();
			}
		}

		if(d != null) {
			return LocaleUtil.formatTimeStampString(context, d.getTime(), full);
		}
		else {
			return "";
		}
	}

	/**
	 * @param messageModel
	 * @return
	 */
	public static boolean hasDataFile(AbstractMessageModel messageModel) {
		return messageModel != null && fileMessageModelTypes.contains(messageModel.getType());
	}

	/**
	 * This method indicates whether the message is a type that can have a thumbnail.
	 * Note that it's still possible that a message does not (yet) have a thumbnail stored,
	 * even though this method returns true.
	 */
	public static boolean canHaveThumbnailFile(AbstractMessageModel messageModel) {
		return messageModel != null && thumbnailFileMessageModelTypes.contains(messageModel.getType());
	}

	public static Set<MessageType> getFileTypes() {
		return fileMessageModelTypes;
	}

	public static Set<MessageType> getLowProfileMessageModelTypes() {
		return lowProfileMessageModelTypes;
	}

	public static boolean canSendDeliveryReceipt(AbstractMessageModel message) {
		return message != null
				&& message instanceof MessageModel
				&& !message.isOutbox()
				&& !message.isRead()
				&& !message.isStatusMessage()
				&& message.getType() != MessageType.VOIP_STATUS
				&& !((message.getMessageFlags() & ProtocolDefines.MESSAGE_FLAG_NO_DELIVERY_RECEIPTS) == ProtocolDefines.MESSAGE_FLAG_NO_DELIVERY_RECEIPTS);
	}

	/**
	 * return true if the message model can mark as read
	 * @param message
	 * @return
	 */
	public static boolean canMarkAsRead(AbstractMessageModel message) {
		return message != null
				&& !message.isOutbox()
				&& !message.isRead();
	}

	/**
	 * return true, if the user-acknowledge flag can be set
	 * @param messageModel
	 * @return
	 */
	public static boolean canSendUserAcknowledge(AbstractMessageModel messageModel) {
		return
				messageModel != null
						&& !messageModel.isOutbox()
						&& messageModel.getState() != MessageState.USERACK
						&& messageModel.getType() != MessageType.VOIP_STATUS
						&& !(messageModel instanceof DistributionListMessageModel)
						&& !(messageModel instanceof GroupMessageModel);
	}
	/**
	 * return true, if the user-decline flag can be set
	 * @param messageModel
	 * @return
	 */
	public static boolean canSendUserDecline(AbstractMessageModel messageModel) {
		return
				messageModel != null
						&& !messageModel.isOutbox()
						&& messageModel.getState() != MessageState.USERDEC
						&& messageModel.getType() != MessageType.VOIP_STATUS
						&& !(messageModel instanceof DistributionListMessageModel)
						&& !(messageModel instanceof GroupMessageModel);
	}

	/**
	 * return true if the user-acknowledge flag visible
	 * @param messageModel
	 * @return
	 */
	public static boolean showStatusIcon(AbstractMessageModel messageModel) {
		boolean showState = false;
		if(messageModel != null) {
			if (messageModel.getType() == MessageType.VOIP_STATUS) {
				return false;
			}

			MessageState messageState = messageModel.getState();

			//group message/distribution list message icons only on pending or failing states
			if(messageModel instanceof GroupMessageModel) {
				showState = messageState != null
						&& ((messageModel.isOutbox() && messageState == MessageState.SENDFAILED)
							|| (messageModel.isOutbox() && messageState == MessageState.SENDING)
							|| (messageModel.isOutbox() && messageState == MessageState.PENDING && messageModel.getType() != MessageType.BALLOT));
			} else if (messageModel instanceof DistributionListMessageModel) {
				showState = false;
			}
			else if (messageModel instanceof MessageModel) {
				if(!messageModel.isOutbox()) {
					//inbox show icon only on acknowledged/declined
					showState = messageState != null
							&& (messageModel.getState() == MessageState.USERACK
							|| messageModel.getState() == MessageState.USERDEC);
				}
				else {
					//on outgoing message
					if (ContactUtil.isChannelContact(messageModel.getIdentity())) {
						showState = messageState != null
								&& (messageState == MessageState.SENDFAILED
								|| messageState == MessageState.PENDING
								|| messageState == MessageState.SENDING);
					} else {
						showState = true;
					}
				}
			}
		}

		return showState;
	}

	public static boolean isUnread(@Nullable AbstractMessageModel messageModel) {
		return messageModel != null
				&& !messageModel.isStatusMessage()
				&& !messageModel.isOutbox()
				&& !messageModel.isRead();
	}

	/**
	 * return true, if the "system" automatically can generate a thumbnail file
	 * @param messageModel
	 * @return
	 */
	public static boolean autoGenerateThumbnail(AbstractMessageModel messageModel) {
		return messageModel != null
				&& messageModel.getType() == MessageType.IMAGE;
	}

	/**
	 * Returns all affected receivers of a distribution list (including myself)
	 * @param messageReceiver
	 * @return ArrayList of all MessageReceivers
	 */
	public static ArrayList<MessageReceiver> getAllReceivers(final MessageReceiver messageReceiver) {

		ArrayList<MessageReceiver> allReceivers = new ArrayList<>();
		allReceivers.add(messageReceiver);

		List<MessageReceiver> affectedReceivers = messageReceiver.getAffectedMessageReceivers();
		if (affectedReceivers != null && affectedReceivers.size() > 0) {
			allReceivers.addAll(Functional.filter(affectedReceivers, new IPredicateNonNull<MessageReceiver>() {
				@Override
				public boolean apply(@NonNull MessageReceiver type) {
					return type != null && !type.isEqual(messageReceiver);
				}
			}));
		}
		return allReceivers;
	}

	/**
	 * Expand list of MessageReceivers to contain distribution list receivers as single recipients
	 * @param allReceivers - list of MessageReceivers including distrubtion lists
	 * @return - expanded list of receivers with duplicates removed
	 */
	public static MessageReceiver[] addDistributionListReceivers(MessageReceiver[] allReceivers) {
		Set<MessageReceiver> resolvedReceivers = new HashSet<>();
		for (MessageReceiver receiver: allReceivers) {
			if (receiver.getType() == MessageReceiver.Type_DISTRIBUTION_LIST) {
				resolvedReceivers.addAll(MessageUtil.getAllReceivers(receiver));
			} else {
				resolvedReceivers.add(receiver);
			}
		}
		return resolvedReceivers.toArray(new MessageReceiver[resolvedReceivers.size()]);
	}

	public static boolean canChangeToState(MessageState fromState, MessageState toState, boolean isOutbox) {
		if (fromState == null || toState == null) {
			//invalid data
			return false;
		}

		if(fromState == toState) {
			return false;
		}

		switch (toState) {
			case DELIVERED:
				return fromState == MessageState.SENDING
						|| fromState == MessageState.SENDFAILED
						|| fromState == MessageState.PENDING
						|| fromState == MessageState.SENT;
			case READ:
				return fromState == MessageState.SENDING
						|| fromState == MessageState.SENDFAILED
						|| fromState == MessageState.PENDING
						|| fromState == MessageState.SENT
						|| fromState == MessageState.DELIVERED;
			case SENDFAILED:
				return fromState == MessageState.SENDING
						|| fromState == MessageState.PENDING
						|| fromState == MessageState.TRANSCODING;
			case SENT:
				return fromState == MessageState.SENDING
						|| fromState == MessageState.SENDFAILED
						|| fromState == MessageState.PENDING
						|| fromState == MessageState.TRANSCODING;
			case USERACK:
				return true;
			case USERDEC:
				return true;
			case PENDING:
				return fromState == MessageState.SENDFAILED;
			case SENDING:
				return fromState == MessageState.SENDFAILED
						|| fromState == MessageState.PENDING
						|| fromState == MessageState.TRANSCODING;
			default:
				logger.debug("message state " + toState.toString() + " not handled");
				return false;
		}
	}

	public static String getCaption(List<String> captionList, int index) {
		String captionText = null;

		if (captionList != null && !captionList.isEmpty() && index < captionList.size() && captionList.get(index) != null) {
			captionText = captionList.get(index);
		}

		if (captionText != null) {
			return captionText.trim();
		}

		return null;
	}

	public static ArrayList<String> getCaptionList(String captionText) {
		ArrayList<String> captions = null;

		if (!TestUtil.empty(captionText)) {
			captions = new ArrayList<>();
			captions.add(captionText);
		}
		return captions;
	}

	public static String getCaptionText(AbstractMessageModel messageModel) {
		if (messageModel != null) {
			switch (messageModel.getType()) {
				case FILE:
					return messageModel.getFileData().getCaption();
				case IMAGE:
					return messageModel.getCaption();
				default:
					break;
			}
		}
		return null;
	}

	public static class MessageViewElement {
		public final @DrawableRes Integer icon;
		public final String placeholder;
		public final Integer color;
		public final String text;
		public final String contentDescription;
		protected MessageViewElement(@DrawableRes Integer icon, String placeholder, String text, String contentDescription, Integer color) {
			this.icon = icon;
			this.placeholder = placeholder;
			this.color = color;
			this.text = text;
			this.contentDescription = contentDescription;
		}
	}

	@NonNull
	public static MessageViewElement getViewElement(Context context, AbstractMessageModel messageModel) {
		if (messageModel != null) {
			switch (messageModel.getType()) {
				case TEXT:
					return new MessageViewElement(null,
							null,
							QuoteUtil.getMessageBody(messageModel, false),
							null,
							null);
				case IMAGE:
					return new MessageViewElement(R.drawable.ic_photo_filled,
							context.getString(R.string.image_placeholder),
							TestUtil.empty(messageModel.getCaption()) ? null : messageModel.getCaption(),
							null,
							null);
				case VIDEO:
					return new MessageViewElement(R.drawable.ic_movie_filled,
							context.getString(R.string.video_placeholder),
							messageModel.getVideoData().getDurationString(),
							null,
							null);
				case LOCATION:
					String locationText = null;
					if (!TestUtil.empty(messageModel.getLocationData().getPoi())) {
						locationText = messageModel.getLocationData().getPoi();
					} else if (!TestUtil.empty(messageModel.getLocationData().getAddress())) {
						locationText = messageModel.getLocationData().getAddress();
					}

					return new MessageViewElement(R.drawable.ic_location_on_filled,
							context.getString(R.string.location_placeholder),
							locationText,
							null,
							null);
				case VOICEMESSAGE:
					return new MessageViewElement(R.drawable.ic_mic_filled,
							context.getString(R.string.audio_placeholder),
							StringConversionUtil.secondsToString(messageModel.getAudioData().getDuration(), false),
							". " + context.getString(R.string.duration) + " " + StringConversionUtil.getDurationStringHuman(context, messageModel.getAudioData().getDuration()) + ". ",
							null);
				case FILE:
					String durationString = messageModel.getFileData().getDurationString();

					if (MimeUtil.isImageFile(messageModel.getFileData().getMimeType())) {
						return new MessageViewElement(R.drawable.ic_photo_filled,
								context.getString(R.string.image_placeholder),
								TestUtil.empty(messageModel.getFileData().getCaption()) ?
										null :
										messageModel.getFileData().getCaption(),
								null,
								null);
					}

					if (MimeUtil.isVideoFile(messageModel.getFileData().getMimeType())) {
						return new MessageViewElement(R.drawable.ic_movie_filled,
								context.getString(R.string.video_placeholder),
								TestUtil.empty(messageModel.getFileData().getCaption()) ?
										durationString :
										messageModel.getFileData().getCaption(),
								null,
								null);
					}

					if (MimeUtil.isAudioFile(messageModel.getFileData().getMimeType())) {
						if (messageModel.getFileData().getRenderingType() == FileData.RENDERING_MEDIA) {
							return new MessageViewElement(R.drawable.ic_mic_filled,
								context.getString(R.string.audio_placeholder),
								StringConversionUtil.secondsToString(messageModel.getFileData().getDuration(), false),
								". " + context.getString(R.string.duration) + " " + StringConversionUtil.getDurationStringHuman(context, messageModel.getFileData().getDuration()) + ". ",
								null);
						} else {
							return new MessageViewElement(R.drawable.ic_doc_audio,
								context.getString(R.string.audio_placeholder),
								TestUtil.empty(messageModel.getFileData().getCaption()) ?
									durationString :
									messageModel.getFileData().getCaption(),
								null,
								null);
						}
					}

					return new MessageViewElement(R.drawable.ic_file_filled,
							context.getString(R.string.file_placeholder),
							TestUtil.empty(messageModel.getFileData().getCaption()) ?
									messageModel.getFileData().getFileName() :
									messageModel.getFileData().getCaption(),
							null,
							null);

				case BALLOT:
					String messageString = BallotUtil.getNotificationString(context, messageModel);
					return new MessageViewElement(R.drawable.ic_baseline_rule,
							context.getString(R.string.ballot_placeholder),
							TestUtil.empty(messageString) ? null: messageString,
							null,
							null);
				case VOIP_STATUS:
					if (messageModel.getVoipStatusData() != null) {

						switch (messageModel.getVoipStatusData().getStatus()) {
							case VoipStatusDataModel.REJECTED:
								// Determine reject reason
								final Byte reasonCodeByte = messageModel.getVoipStatusData().getReason();
								final byte reasonCode = reasonCodeByte == null
									? VoipCallAnswerData.RejectReason.UNKNOWN
									: reasonCodeByte;

								// Default values
								int rejectColor = R.color.material_red;
								String rejectPlaceholder = messageModel.isOutbox()
									? context.getString(R.string.voip_call_status_rejected)
									: context.getString(R.string.voip_call_status_missed);

								// Provide more details for certain reject reasons
								//noinspection NestedSwitchStatement
								switch (reasonCode) {
									case VoipCallAnswerData.RejectReason.BUSY:
										rejectPlaceholder = messageModel.isOutbox()
											? context.getString(R.string.voip_call_status_busy)
											: context.getString(R.string.voip_call_status_missed) + " (" + context.getString(R.string.voip_call_status_busy_short) + ")";
										break;
									case VoipCallAnswerData.RejectReason.TIMEOUT:
										rejectPlaceholder = messageModel.isOutbox()
											? context.getString(R.string.voip_call_status_unavailable)
											: context.getString(R.string.voip_call_status_missed);
										break;
									case VoipCallAnswerData.RejectReason.REJECTED:
										rejectPlaceholder = context.getString(R.string.voip_call_status_rejected);
										rejectColor = messageModel.isOutbox()
											? R.color.material_red
											: R.color.material_orange;
										break;
									case VoipCallAnswerData.RejectReason.DISABLED:
										rejectPlaceholder = messageModel.isOutbox()
											? context.getString(R.string.voip_call_status_disabled)
											: context.getString(R.string.voip_call_status_rejected);
										rejectColor = messageModel.isOutbox()
											? R.color.material_red
											: R.color.material_orange;
										break;
									case VoipCallAnswerData.RejectReason.OFF_HOURS:
										rejectPlaceholder = context.getString(R.string.voip_call_status_off_hours);
										rejectColor = messageModel.isOutbox()
											? R.color.material_red
											: R.color.material_orange;
										break;
								}
								return new MessageViewElement(
										messageModel.isOutbox() ?
												R.drawable.ic_call_missed_outgoing_black_24dp :
												R.drawable.ic_call_missed_black_24dp,
										rejectPlaceholder,
										rejectPlaceholder,
										null,
										rejectColor);
							case VoipStatusDataModel.ABORTED:
								return new MessageViewElement(R.drawable.ic_call_missed_outgoing_black_24dp,
										context.getString(R.string.voip_call_status_aborted),
										context.getString(R.string.voip_call_status_aborted),
										null,
										R.color.material_orange);
							case VoipStatusDataModel.MISSED:
								return new MessageViewElement(
										messageModel.isOutbox() ?
												R.drawable.ic_call_missed_outgoing_black_24dp :
												R.drawable.ic_call_missed_black_24dp,
										context.getString(R.string.voip_call_status_missed),
										context.getString(R.string.voip_call_status_missed),
										null,
										R.color.material_red);
							case VoipStatusDataModel.FINISHED:
								return new MessageViewElement(
										messageModel.isOutbox() ?
												R.drawable.ic_call_made_black_24dp :
												R.drawable.ic_call_received_black_24dp,
										context.getString(messageModel.isOutbox() ?
												R.string.voip_call_finished_outbox :
												R.string.voip_call_finished_inbox),
										context.getString(messageModel.isOutbox() ?
												R.string.voip_call_finished_outbox :
												R.string.voip_call_finished_inbox),
										null,
										R.color.material_green);
						}
					}
					break;
			}
		}
		return new MessageViewElement(null, null, null, null, null);
	}

}

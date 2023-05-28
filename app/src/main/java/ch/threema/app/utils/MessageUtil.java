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

package ch.threema.app.utils;

import android.content.Context;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.ContactService;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.domain.protocol.csp.messages.file.FileData;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerData;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.MessageModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.data.MessageContentsType;
import ch.threema.storage.models.data.status.ForwardSecurityStatusDataModel;
import ch.threema.storage.models.data.status.GroupCallStatusDataModel;
import ch.threema.storage.models.data.status.VoipStatusDataModel;

public class MessageUtil {
	private static final Logger logger = LoggingUtil.getThreemaLogger("MessageUtil");

	private final static Set<MessageType> fileMessageModelTypes = EnumSet.of(
			MessageType.IMAGE,
			MessageType.VOICEMESSAGE,
			MessageType.VIDEO,
			MessageType.FILE);

	private final static Set<MessageType> thumbnailFileMessageModelTypes = EnumSet.of(
			MessageType.IMAGE,
			MessageType.VIDEO,
			MessageType.FILE);

	private final static Set<MessageType> lowProfileMessageModelTypes = EnumSet.of(
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

	public static boolean canSendDeliveryReceipt(AbstractMessageModel message, int receiptType) {
		if (ConfigUtils.isGroupAckEnabled() && (receiptType == ProtocolDefines.DELIVERYRECEIPT_MSGUSERACK || receiptType == ProtocolDefines.DELIVERYRECEIPT_MSGUSERDEC)) {
			return (message instanceof MessageModel || message instanceof GroupMessageModel)
				&& !message.isOutbox()
				&& !message.isRead()
				&& !message.isStatusMessage()
				&& message.getType() != MessageType.VOIP_STATUS
				&& !((message.getMessageFlags() & ProtocolDefines.MESSAGE_FLAG_NO_DELIVERY_RECEIPTS) == ProtocolDefines.MESSAGE_FLAG_NO_DELIVERY_RECEIPTS);
		} else {
			return message instanceof MessageModel
				&& !message.isOutbox()
				&& !message.isRead()
				&& !message.isStatusMessage()
				&& message.getType() != MessageType.VOIP_STATUS
				&& !((message.getMessageFlags() & ProtocolDefines.MESSAGE_FLAG_NO_DELIVERY_RECEIPTS) == ProtocolDefines.MESSAGE_FLAG_NO_DELIVERY_RECEIPTS);
		}
	}

	/**
	 * @return true if the message model can mark as read
	 */
	public static boolean canMarkAsRead(AbstractMessageModel message) {
		return message != null
				&& !message.isOutbox()
				&& !message.isRead();
	}

	/**
	 * @return true if the message model can mark as consumed
	 */
	public static boolean canMarkAsConsumed(@Nullable AbstractMessageModel message) {
		return
			(message instanceof MessageModel || message instanceof GroupMessageModel)
			&& !message.isStatusMessage()
			&& !message.isOutbox()
			&& message.getState() != MessageState.CONSUMED
			&& (message.getMessageContentsType() == MessageContentsType.VOICE_MESSAGE ||
				message.getMessageContentsType() == MessageContentsType.AUDIO )
			&& (message.getState() == null || canChangeToState(message.getState(), MessageState.CONSUMED, message.isOutbox()));
	}

	/**
	 * @return true, if the user-acknowledge flag can be set
	 */
	public static boolean canSendUserAcknowledge(AbstractMessageModel messageModel) {
		if (ConfigUtils.isGroupAckEnabled()) {
			return
				messageModel != null
					&& (!messageModel.isOutbox() || messageModel instanceof GroupMessageModel)
					&& messageModel.getState() != MessageState.USERACK
					&& messageModel.getType() != MessageType.VOIP_STATUS
					&& messageModel.getType() != MessageType.GROUP_CALL_STATUS
					&& !messageModel.isStatusMessage()
					&& !(messageModel instanceof DistributionListMessageModel);
		} else {
			return
				messageModel != null
					&& !messageModel.isOutbox()
					&& messageModel.getState() != MessageState.USERACK
					&& messageModel.getType() != MessageType.VOIP_STATUS
					&& messageModel.getType() != MessageType.GROUP_CALL_STATUS
					&& !messageModel.isStatusMessage()
					&& !(messageModel instanceof DistributionListMessageModel)
					&& !(messageModel instanceof GroupMessageModel);
		}
	}
	/**
	 * @return true, if the user-decline flag can be set
	 */
	public static boolean canSendUserDecline(AbstractMessageModel messageModel) {
		if (ConfigUtils.isGroupAckEnabled()) {
			return
				messageModel != null
					&& (!messageModel.isOutbox() || messageModel instanceof GroupMessageModel)
					&& messageModel.getState() != MessageState.USERDEC
					&& messageModel.getType() != MessageType.VOIP_STATUS
					&& messageModel.getType() != MessageType.GROUP_CALL_STATUS
					&& !messageModel.isStatusMessage()
					&& !(messageModel instanceof DistributionListMessageModel);
		} else {
			return
				messageModel != null
					&& !messageModel.isOutbox()
					&& messageModel.getState() != MessageState.USERDEC
					&& messageModel.getType() != MessageType.VOIP_STATUS
					&& messageModel.getType() != MessageType.GROUP_CALL_STATUS
					&& !messageModel.isStatusMessage()
					&& !(messageModel instanceof DistributionListMessageModel)
					&& !(messageModel instanceof GroupMessageModel);
		}
	}

	public static boolean canSendImageReply(@Nullable AbstractMessageModel messageModel) {
		if (messageModel == null || messageModel.getMessageContentsType() != MessageContentsType.IMAGE) {
			return false;
		}
		try {
			return messageModel.getFileData().isDownloaded();
		} catch (ClassCastException exception) {
			// No file data
			logger.warn("No file data available");
		}
		try {
			return messageModel.getImageData().isDownloaded();
		} catch (ClassCastException ignored) {
			// No image data
			logger.warn("No image data available");
			return false;
		}
	}

	/**
	 * @return true if the user-acknowledge flag visible
	 */
	public static boolean showStatusIcon(AbstractMessageModel messageModel) {
		boolean showState = false;
		if (messageModel != null) {
			if (messageModel.getType() == MessageType.VOIP_STATUS) {
				return false;
			}

			MessageState messageState = messageModel.getState();

			//group message/distribution list message icons only on pending or failing states
			if (messageModel instanceof GroupMessageModel) {
				if (messageState != null) {
					if (messageModel.isOutbox()) {
						showState = messageState == MessageState.SENDFAILED
							|| messageState == MessageState.FS_KEY_MISMATCH
							|| messageState == MessageState.SENDING
							|| (messageState == MessageState.PENDING && messageModel.getType() != MessageType.BALLOT);
					} else {
						showState = messageModel.getState() == MessageState.CONSUMED
							|| messageModel.getState() == MessageState.USERACK
							|| messageModel.getState() == MessageState.USERDEC;
					}
				}
			} else if (messageModel instanceof MessageModel) {
				if (!messageModel.isOutbox()) {
					// inbox show icon only on acknowledged/declined or consumed
					showState = messageState != null
							&& (messageModel.getState() == MessageState.USERACK
							|| messageModel.getState() == MessageState.USERDEC
							|| messageModel.getState() == MessageState.CONSUMED);
				}
				else {
					// on outgoing message
					if (ContactUtil.isChannelContact(messageModel.getIdentity())) {
						showState = messageState == MessageState.SENDFAILED
							|| messageState == MessageState.FS_KEY_MISMATCH
							|| messageState == MessageState.PENDING
							|| messageState == MessageState.SENDING;
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
	 * @return true, if the "system" automatically can generate a thumbnail file
	 */
	public static boolean autoGenerateThumbnail(AbstractMessageModel messageModel) {
		return messageModel != null
				&& messageModel.getType() == MessageType.IMAGE;
	}

	/**
	 * Returns all affected receivers of a distribution list (including myself)
	 * @return ArrayList of all MessageReceivers
	 */
	public static ArrayList<MessageReceiver> getAllReceivers(final MessageReceiver messageReceiver) {

		ArrayList<MessageReceiver> allReceivers = new ArrayList<>();
		allReceivers.add(messageReceiver);

		List<MessageReceiver> affectedReceivers = messageReceiver.getAffectedMessageReceivers();
		if (affectedReceivers != null && affectedReceivers.size() > 0) {
			allReceivers.addAll(Functional.filter(affectedReceivers, new IPredicateNonNull<>() {
				@Override
				public boolean apply(@NonNull MessageReceiver type) {
					return !type.isEqual(messageReceiver);
				}
			}));
		}
		return allReceivers;
	}

	/**
	 * Expand list of MessageReceivers to contain distribution list receivers as single recipients
	 * @param allReceivers - list of MessageReceivers including distribution lists
	 * @return - expanded list of receivers with duplicates removed
	 */
	public static MessageReceiver[] addDistributionListReceivers(MessageReceiver[] allReceivers) {
		// Use LinkedHashSet in order to preserve insertion order.
		// If the order is not preserved sending of files to distribution lists is likely to fail
		Set<MessageReceiver> resolvedReceivers = new LinkedHashSet<>();
		for (MessageReceiver receiver: allReceivers) {
			if (receiver.getType() == MessageReceiver.Type_DISTRIBUTION_LIST) {
				resolvedReceivers.addAll(MessageUtil.getAllReceivers(receiver));
			} else {
				resolvedReceivers.add(receiver);
			}
		}
		return resolvedReceivers.toArray(new MessageReceiver[0]);
	}

	/**
	 * Check if a MessageState change from fromState to toState is allowed
	 * @param fromState State from which a state change is requested
	 * @param toState State to which a state change is requested
	 * @param isOutbox true, if it's an outgoing message
	 * @return true if a state change is allowed, false otherwise
	 */
	public static boolean canChangeToState(@Nullable MessageState fromState, @Nullable MessageState toState, boolean isOutbox) {
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
						|| fromState == MessageState.FS_KEY_MISMATCH
						|| fromState == MessageState.PENDING
						|| fromState == MessageState.SENT;
			case READ:
				return fromState == MessageState.SENDING
						|| fromState == MessageState.SENDFAILED
						|| fromState == MessageState.FS_KEY_MISMATCH
						|| fromState == MessageState.PENDING
						|| fromState == MessageState.SENT
						|| fromState == MessageState.DELIVERED;
			case SENDFAILED:
				return fromState == MessageState.SENDING
						|| fromState == MessageState.PENDING
						|| fromState == MessageState.TRANSCODING;
			case FS_KEY_MISMATCH:
				return fromState == MessageState.SENDING
						|| fromState == MessageState.PENDING
						|| fromState == MessageState.TRANSCODING
						|| fromState == MessageState.SENT;
			case SENT:
				return fromState == MessageState.SENDING
						|| fromState == MessageState.SENDFAILED
						|| fromState == MessageState.FS_KEY_MISMATCH
						|| fromState == MessageState.PENDING
						|| fromState == MessageState.TRANSCODING;
			case USERACK:
				return true;
			case USERDEC:
				return true;
			case CONSUMED:
				return fromState != MessageState.USERACK
					&& fromState != MessageState.USERDEC;
			case PENDING:
				return fromState == MessageState.SENDFAILED
						|| fromState == MessageState.FS_KEY_MISMATCH;
			case SENDING:
				return fromState == MessageState.SENDFAILED
						|| fromState == MessageState.FS_KEY_MISMATCH
						|| fromState == MessageState.PENDING
						|| fromState == MessageState.TRANSCODING;
			default:
				logger.debug("message state {} not handled", toState);
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

	/**
	 * Check if the provided MessageState is acceptable as a group message state and should be saved to the database
	 * @param state MessageState
	 * @return true if MessageState is acceptable, false otherwise
	 */
	public static boolean isAllowedGroupMessageState(MessageState state) {
		return state == MessageState.USERACK || state == MessageState.USERDEC;
	}

	public static class MessageViewElement {
		public final @Nullable @DrawableRes Integer icon;
		public final @Nullable String placeholder;
		public final @Nullable Integer color;
		public final @Nullable String text;
		public final @Nullable String contentDescription;
		protected MessageViewElement(@Nullable @DrawableRes Integer icon, @Nullable String placeholder, @Nullable String text, @Nullable String contentDescription, @Nullable Integer color) {
			this.icon = icon;
			this.placeholder = placeholder;
			this.color = color;
			this.text = text;
			this.contentDescription = contentDescription;
		}
	}

	@NonNull
	public static MessageViewElement getViewElement(Context context, AbstractMessageModel messageModel) {
		if (messageModel != null && messageModel.getType() != null) {
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
						String durationString = messageModel.getFileData().getDurationString();

						return new MessageViewElement(R.drawable.ic_movie_filled,
								context.getString(R.string.video_placeholder),
								TestUtil.empty(messageModel.getFileData().getCaption()) ?
										durationString :
										messageModel.getFileData().getCaption(),
								null,
								null);
					}

					if (MimeUtil.isAudioFile(messageModel.getFileData().getMimeType())) {
						String durationString = messageModel.getFileData().getDurationString();

						if (messageModel.getFileData().getRenderingType() == FileData.RENDERING_MEDIA) {
							return new MessageViewElement(R.drawable.ic_mic_filled,
								context.getString(R.string.audio_placeholder),
								durationString,
								". " + context.getString(R.string.duration) + " " + StringConversionUtil.getDurationStringHuman(context, messageModel.getFileData().getDurationSeconds()) + ". ",
								null);
						} else {
							return new MessageViewElement(R.drawable.ic_doc_audio,
								context.getString(R.string.audio_placeholder),
								TestUtil.empty(messageModel.getFileData().getCaption()) ?
									("00:00".equals(durationString) ? null : durationString) :
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
					VoipStatusDataModel voipStatusDataModel = messageModel.getVoipStatusData();
					if (voipStatusDataModel != null) {
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
				case GROUP_CALL_STATUS:
					GroupCallStatusDataModel groupCallStatusDataModel = messageModel.getGroupCallStatusData();
					if (groupCallStatusDataModel != null) {
						switch (groupCallStatusDataModel.getStatus()) {
							case GroupCallStatusDataModel.STATUS_STARTED:
								String body = context.getString(R.string.voip_gc_call_started);
								if (groupCallStatusDataModel.getCallerIdentity() != null) {
									try {
										ContactService contactService = ThreemaApplication.getServiceManager().getContactService();
										body = String.format(
											context.getString(messageModel.isOutbox() ?
												R.string.voip_gc_notification_call_started_generic_outbox :
												R.string.voip_gc_notification_call_started_generic),
											NameUtil.getShortName(groupCallStatusDataModel.getCallerIdentity(), contactService));
									} catch (Exception e) {
										logger.debug("Contact service unavailable");
									}
								}

								return new MessageViewElement(
									R.drawable.ic_group_call,
									context.getString(R.string.voip_gc_call_started),
									body,
									null,
									R.color.group_call_accent
								);
							case GroupCallStatusDataModel.STATUS_ENDED:
								return new MessageViewElement(
									R.drawable.ic_group_call,
									context.getString(R.string.voip_gc_call_ended),
									context.getString(R.string.voip_gc_call_ended),
									null,
									R.color.group_call_accent
								);
						}
						break;
					}
				case FORWARD_SECURITY_STATUS:
					ForwardSecurityStatusDataModel forwardSecurityStatusDataModel = messageModel.getForwardSecurityStatusData();
					if (forwardSecurityStatusDataModel != null) {
						switch (forwardSecurityStatusDataModel.getStatus()) {
							case ForwardSecurityStatusDataModel.ForwardSecurityStatusType.FORWARD_SECURITY_RESET:
								return new MessageViewElement(
									R.drawable.ic_baseline_key_off_24,
									context.getString(R.string.forward_security_reset_simple),
									context.getString(R.string.forward_security_reset_simple),
									null,
									R.color.material_red
								);
							case ForwardSecurityStatusDataModel.ForwardSecurityStatusType.FORWARD_SECURITY_ESTABLISHED:
								return new MessageViewElement(
									R.drawable.ic_baseline_key_24,
									context.getString(R.string.forward_security_established),
									context.getString(R.string.forward_security_established),
									null,
									R.color.material_green
								);
							case ForwardSecurityStatusDataModel.ForwardSecurityStatusType.FORWARD_SECURITY_ESTABLISHED_RX:
								return new MessageViewElement(
									R.drawable.ic_baseline_key_24,
									context.getString(R.string.forward_security_established_rx),
									context.getString(R.string.forward_security_established_rx),
									null,
									R.color.material_green
								);
							case ForwardSecurityStatusDataModel.ForwardSecurityStatusType.FORWARD_SECURITY_MESSAGES_SKIPPED:
								String body = ConfigUtils.getSafeQuantityString(context, R.plurals.forward_security_messages_skipped, forwardSecurityStatusDataModel.getQuantity(), forwardSecurityStatusDataModel.getQuantity());
								return new MessageViewElement(
									R.drawable.ic_baseline_key_24,
									body,
									body,
									null,
									null
								);
							case ForwardSecurityStatusDataModel.ForwardSecurityStatusType.FORWARD_SECURITY_MESSAGE_OUT_OF_ORDER:
								return new MessageViewElement(
									R.drawable.ic_baseline_key_24,
									context.getString(R.string.forward_security_message_out_of_order),
									context.getString(R.string.forward_security_message_out_of_order),
									null,
									null
								);
							case ForwardSecurityStatusDataModel.ForwardSecurityStatusType.MESSAGE_WITHOUT_FORWARD_SECURITY:
								return new MessageViewElement(
									R.drawable.ic_baseline_key_off_24,
									context.getString(R.string.message_without_forward_security),
									context.getString(R.string.message_without_forward_security),
									null,
									null
								);
							case ForwardSecurityStatusDataModel.ForwardSecurityStatusType.FORWARD_SECURITY_UNAVAILABLE_DOWNGRADE:
								return new MessageViewElement(
									R.drawable.ic_baseline_key_24,
									context.getString(R.string.forward_security_downgraded_status_message),
									context.getString(R.string.forward_security_downgraded_status_message),
									null,
									null
								);
							default:
								return new MessageViewElement(
									R.drawable.ic_baseline_key_24,
									forwardSecurityStatusDataModel.getStaticText(),
									forwardSecurityStatusDataModel.getStaticText(),
									null,
									null
								);
						}
					}
					break;
			}
		}
		return new MessageViewElement(null, null, null, null, null);
	}
}

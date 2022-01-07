/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2022 Threema GmbH
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

package ch.threema.app.processors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.IdListService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.NotificationService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.ballot.BallotService;
import ch.threema.app.services.ballot.BallotVoteResult;
import ch.threema.app.services.group.GroupJoinResponseService;
import ch.threema.app.services.group.IncomingGroupJoinRequestService;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.MessageDiskSizeUtil;
import ch.threema.app.voip.services.VoipStateService;
import ch.threema.base.utils.Utils;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.domain.protocol.csp.coders.MessageBox;
import ch.threema.domain.protocol.csp.coders.MessageCoder;
import ch.threema.domain.protocol.csp.connection.MessageProcessorInterface;
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage;
import ch.threema.domain.protocol.csp.messages.AbstractMessage;
import ch.threema.domain.protocol.csp.messages.BadMessageException;
import ch.threema.domain.protocol.csp.messages.BoxTextMessage;
import ch.threema.domain.protocol.csp.messages.ContactDeletePhotoMessage;
import ch.threema.domain.protocol.csp.messages.ContactRequestPhotoMessage;
import ch.threema.domain.protocol.csp.messages.ContactSetPhotoMessage;
import ch.threema.domain.protocol.csp.messages.DeliveryReceiptMessage;
import ch.threema.domain.protocol.csp.messages.GroupCreateMessage;
import ch.threema.domain.protocol.csp.messages.GroupDeletePhotoMessage;
import ch.threema.domain.protocol.csp.messages.GroupLeaveMessage;
import ch.threema.domain.protocol.csp.messages.GroupRenameMessage;
import ch.threema.domain.protocol.csp.messages.GroupRequestSyncMessage;
import ch.threema.domain.protocol.csp.messages.GroupSetPhotoMessage;
import ch.threema.domain.protocol.csp.messages.GroupTextMessage;
import ch.threema.domain.protocol.csp.messages.MissingPublicKeyException;
import ch.threema.domain.protocol.csp.messages.TypingIndicatorMessage;
import ch.threema.domain.protocol.csp.messages.ballot.BallotVoteInterface;
import ch.threema.domain.protocol.csp.messages.group.GroupJoinRequestMessage;
import ch.threema.domain.protocol.csp.messages.group.GroupJoinResponseMessage;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerMessage;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallHangupMessage;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallOfferMessage;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallRingingMessage;
import ch.threema.domain.protocol.csp.messages.voip.VoipICECandidatesMessage;
import ch.threema.domain.protocol.csp.messages.voip.VoipMessage;
import ch.threema.domain.stores.ContactStore;
import ch.threema.domain.stores.IdentityStoreInterface;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.ServerMessageModel;

public class MessageProcessor implements MessageProcessorInterface {
	private static final Logger logger = LoggerFactory.getLogger(MessageProcessor.class);
	private static final Logger validationLogger = LoggerFactory.getLogger("Validation");

	private final MessageService messageService;
	private final ContactService contactService;
	private final IdentityStoreInterface identityStore;
	private final ContactStore contactStore;
	private final PreferenceService preferenceService;
	private final GroupService groupService;
	private final GroupJoinResponseService groupJoinResponseService;
	private final IncomingGroupJoinRequestService incomingGroupJoinRequestService;
	private final IdListService blackListService;
	private final BallotService ballotService;
	private final VoipStateService voipStateService;
	private final FileService fileService;
	private final NotificationService notificationService;

	private final List<AbstractMessage> pendingMessages = new ArrayList<>();

	public MessageProcessor(
			MessageService messageService,
			ContactService contactService,
			IdentityStoreInterface identityStore,
			ContactStore contactStore,
			PreferenceService preferenceService,
			GroupService groupService,
			GroupJoinResponseService groupJoinResponseService,
			IncomingGroupJoinRequestService incomingGroupJoinRequestService,
			IdListService blackListService,
			BallotService ballotService,
			FileService fileService,
			NotificationService notificationService,
			VoipStateService voipStateService) {

		this.messageService = messageService;
		this.contactService = contactService;
		this.identityStore = identityStore;
		this.contactStore = contactStore;
		this.preferenceService = preferenceService;
		this.groupService = groupService;
		this.groupJoinResponseService = groupJoinResponseService;
		this.incomingGroupJoinRequestService = incomingGroupJoinRequestService;
		this.blackListService = blackListService;
		this.ballotService = ballotService;
		this.fileService = fileService;
		this.notificationService = notificationService;
		this.voipStateService = voipStateService;
	}

	@Override
	@WorkerThread
	public ProcessIncomingResult processIncomingMessage(MessageBox boxmsg) {
		AbstractMessage msg;
		try {
			if (ConfigUtils.isWorkBuild() && preferenceService.isBlockUnknown()) {
				contactService.createWorkContact(boxmsg.getFromIdentity());
			}

			// Check first, if contact of incoming message is a already known.
			// This will throw MissingPublicKeyException if contact is blocked or fetching failed.
			final boolean fetchKeyIfMissing = true;
			MessageCoder messageCoder = new MessageCoder( this.contactStore, this.identityStore);
			msg = messageCoder.decode(boxmsg, fetchKeyIfMissing);

			if (msg == null) {
				logger.warn("Message {} from {} error: decodeFromBox failed",
					boxmsg.getMessageId(), boxmsg.getFromIdentity());
				return ProcessIncomingResult.failed();
			}

			if (logger.isInfoEnabled()) {
				logger.info(
					"Incoming message {} from {} to {} (type {})",
					boxmsg.getMessageId(),
					boxmsg.getFromIdentity(),
					boxmsg.getToIdentity(),
					Utils.byteToHex((byte) msg.getType(), true, true)
				);
			}

			/* validation logging (for text messages only) */
			if (msg instanceof BoxTextMessage || msg instanceof GroupTextMessage) {
				if (validationLogger.isInfoEnabled()) {
					validationLogger.info("< Nonce: {}", Utils.byteArrayToHexString(boxmsg.getNonce()));
					validationLogger.info("< Data: {}", Utils.byteArrayToHexString(boxmsg.getBox()));

					byte[] publicKey = contactStore.getPublicKeyForIdentity(boxmsg.getFromIdentity(), true);
					if (publicKey != null) {
						validationLogger.info("< Public key ({}): {}",
							boxmsg.getFromIdentity(), Utils.byteArrayToHexString(publicKey));
					}
				}
			}

			//check if sender is on blacklist
			if (!(msg instanceof AbstractGroupMessage)) {
				if (this.blackListService != null && this.blackListService.has(msg.getFromIdentity())) {
					logger.debug("Direct message from {}: Contact blacklisted. Ignoring", msg.getFromIdentity());
					//ignore message of blacklisted member
					return ProcessIncomingResult.ignore();
				}
			}

			this.contactService.setActive(msg.getFromIdentity());

			if (msg instanceof TypingIndicatorMessage) {
				if (this.contactService.getByIdentity(boxmsg.getFromIdentity()) != null) {
					this.contactService.setIsTyping(boxmsg.getFromIdentity(), ((TypingIndicatorMessage) msg).isTyping());
					return ProcessIncomingResult.ok(msg);
				} else {
					logger.debug("Ignoring typing indicator message from unknown identity {}", boxmsg.getFromIdentity());
					return ProcessIncomingResult.ignore();
				}
			}

			if (msg instanceof DeliveryReceiptMessage) {
				final @Nullable MessageState state;
				switch (((DeliveryReceiptMessage) msg).getReceiptType()) {
					case ProtocolDefines.DELIVERYRECEIPT_MSGRECEIVED:
						state = MessageState.DELIVERED;
						break;
					case ProtocolDefines.DELIVERYRECEIPT_MSGREAD:
						state = MessageState.READ;
						break;
					case ProtocolDefines.DELIVERYRECEIPT_MSGUSERACK:
						state = MessageState.USERACK;
						break;
					case ProtocolDefines.DELIVERYRECEIPT_MSGUSERDEC:
						state = MessageState.USERDEC;
						break;
					case ProtocolDefines.DELIVERYRECEIPT_MSGCONSUMED:
						state = MessageState.CONSUMED;
						break;
					default:
						state = null;
						break;
				}
				if (state != null) {
					for (MessageId msgId : ((DeliveryReceiptMessage) msg).getReceiptMessageIds()) {
						logger.info("Message {}: delivery receipt for {} (state = {})", boxmsg.getMessageId(), msgId, state);
						this.messageService.updateMessageState(msgId, msg.getFromIdentity(), state, msg.getDate());
					}
					return ProcessIncomingResult.ok(msg);
				} else {
					logger.warn("Message {} error: unknown delivery receipt type", boxmsg.getMessageId());
				}
				return ProcessIncomingResult.ignore();
			}

			/* send delivery receipt (but not for immediate messages or delivery receipts) */
			if (!msg.isImmediate()) {
				/* throw away messages from hidden contacts if block unknown is enabled - except for group messages */
				if (
					this.preferenceService.isBlockUnknown()
					&& this.contactService.getIsHidden(msg.getFromIdentity())
					&& !(
						msg instanceof AbstractGroupMessage
						|| msg instanceof GroupJoinRequestMessage
						|| msg instanceof GroupJoinResponseMessage
					)
				) {
					logger.info("Message {} discarded - from hidden contact with block unknown enabled", boxmsg.getMessageId());
					return ProcessIncomingResult.ignore();
				}

				switch(this.processAbstractMessage(msg)) {
					case SUCCESS:
						// default behaviour: return ok.
						break;
					case FAILED:
						return ProcessIncomingResult.failed();
					case IGNORED:
						return ProcessIncomingResult.ignore();
				}
			}

			return ProcessIncomingResult.ok(msg);

		} catch (MissingPublicKeyException e) {
			if(this.preferenceService.isBlockUnknown()) {
				//its ok, return true and save nothing
				return ProcessIncomingResult.ignore();
			}

			if(this.blackListService != null && this.blackListService.has(boxmsg.getFromIdentity())) {
				//its ok, a black listed identity, save NOTHING
				return ProcessIncomingResult.ignore();
			}

			logger.error("Missing public key", e);
			return ProcessIncomingResult.failed();

		} catch (BadMessageException e) {
			logger.error("Bad message", e);
			if (e.shouldDrop()) {
				logger.warn("Message {} error: invalid - dropping msg.", boxmsg.getMessageId());
				return ProcessIncomingResult.ignore();
			}
			return ProcessIncomingResult.failed();

		} catch (Exception e) {
			logger.error("Unknown exception while processing BoxedMessage", e);
			return ProcessIncomingResult.failed();
		}
	}

	/**
	 *
	 * @param msg incoming message
	 * @return true if message has been properly processed, false if unsuccessful (e.g. network error) and processing/download should be attempted again later
	 */
	@WorkerThread
	private @NonNull ProcessingResult processAbstractMessage(AbstractMessage msg) {
		try {
			logger.trace("processAbstractMessage {}", msg.getMessageId());
			//try to update public nickname
			this.contactService.updatePublicNickName(msg);

			//check available size on device..
			long useableSpace = this.fileService.getInternalStorageFree();
			long requiredSpace = MessageDiskSizeUtil.getSize(msg);

			if(useableSpace < requiredSpace) {
				//show notification and do not try to save the message
				this.notificationService.showNotEnoughDiskSpace(useableSpace, requiredSpace);
				logger.error("Abstract Message {}: error - out of disk space {}/{}",
					msg.getMessageId(), requiredSpace, useableSpace);
				return ProcessingResult.FAILED;
			}

			boolean processingSuccessful = false;

			if(msg instanceof BallotVoteInterface) {
				BallotVoteResult r = this.ballotService.vote((BallotVoteInterface) msg);
				return (r != null && r.isSuccess()) ? ProcessingResult.SUCCESS : ProcessingResult.FAILED;
			}

			if(msg instanceof AbstractGroupMessage) {
				if(msg instanceof GroupCreateMessage) {
					//new group or sync it!
					GroupService.GroupCreateMessageResult result = this.groupService.processGroupCreateMessage(
							(GroupCreateMessage) msg);

					if(result.success() && result.getGroupModel() != null) {
						//process unprocessed message
						synchronized (this.pendingMessages) {
							Iterator<AbstractMessage> i = this.pendingMessages.iterator();
							while (i.hasNext()) {
								AbstractMessage s = i.next();
								if(s instanceof AbstractGroupMessage
									&& !(s instanceof GroupCreateMessage)) {

									AbstractGroupMessage as = (AbstractGroupMessage)s;
									if(
											as.getGroupCreator().equals(((GroupCreateMessage) msg).getGroupCreator())
											&& as.getGroupId().toString().equals(((GroupCreateMessage) msg).getGroupId().toString())) {

										this.processAbstractMessage(s);
										i.remove();
									}
								}
							}
						}
					}

					processingSuccessful = result.success();
				}
				else if(msg instanceof GroupRenameMessage) {
					processingSuccessful = this.groupService.renameGroup((GroupRenameMessage)msg);
				}
				else if(msg instanceof GroupSetPhotoMessage) {
					processingSuccessful = this.groupService.updateGroupPhoto((GroupSetPhotoMessage) msg);
				}
				else if(msg instanceof GroupDeletePhotoMessage) {
					processingSuccessful = this.groupService.deleteGroupPhoto((GroupDeletePhotoMessage) msg);
				}
				else if(msg instanceof GroupLeaveMessage) {
					processingSuccessful = this.groupService.removeMemberFromGroup((GroupLeaveMessage) msg);
				}
				else if(msg instanceof GroupRequestSyncMessage) {
					processingSuccessful = this.groupService.processRequestSync((GroupRequestSyncMessage) msg);
				}
				else {
					GroupModel groupModel = this.groupService.getGroup((AbstractGroupMessage)msg);

					//group model not found
					if(groupModel == null
							//or i am not a member of this group
							|| !this.groupService.isGroupMember(groupModel)) {
						//if the request sync process ok, ack the message
						if(this.groupService.requestSync((AbstractGroupMessage)msg, true)) {
							//save message in cache
							synchronized (this.pendingMessages) {
								this.pendingMessages.add(msg);
							}

							return ProcessingResult.SUCCESS;
						}
						return ProcessingResult.FAILED;
					}
					else if(groupModel.isDeleted()) {
						//send leave message
						this.groupService.sendLeave((AbstractGroupMessage)msg);

						//ack every time!
						return ProcessingResult.SUCCESS;
					}
					else {
						processingSuccessful = this.messageService.processIncomingGroupMessage((AbstractGroupMessage) msg);
					}
				}
			}
			else if (msg instanceof GroupJoinRequestMessage) {
				return this.incomingGroupJoinRequestService.process((GroupJoinRequestMessage) msg);
			}
			else if (msg instanceof GroupJoinResponseMessage) {
				return this.groupJoinResponseService.process((GroupJoinResponseMessage) msg);
			}
			else if (msg instanceof ContactSetPhotoMessage) {
				processingSuccessful = this.contactService.updateContactPhoto((ContactSetPhotoMessage) msg);
			}
			else if (msg instanceof ContactDeletePhotoMessage) {
				processingSuccessful = this.contactService.deleteContactPhoto((ContactDeletePhotoMessage) msg);
			}
			else if (msg instanceof ContactRequestPhotoMessage) {
				processingSuccessful = this.contactService.requestContactPhoto((ContactRequestPhotoMessage) msg);
			} else if (msg instanceof VoipMessage) {
				if (this.preferenceService.isVoipEnabled()) {
					/* as soon as we get a voip message, unhide the contact */
					this.contactService.setIsHidden(msg.getFromIdentity(), false);
					if (msg instanceof VoipCallOfferMessage) {
						processingSuccessful = this.voipStateService.handleCallOffer((VoipCallOfferMessage) msg);
					} else if (msg instanceof VoipCallAnswerMessage) {
						processingSuccessful = this.voipStateService.handleCallAnswer((VoipCallAnswerMessage) msg);
					} else if (msg instanceof VoipICECandidatesMessage) {
						processingSuccessful = this.voipStateService.handleICECandidates((VoipICECandidatesMessage) msg);
					} else if (msg instanceof VoipCallRingingMessage) {
						processingSuccessful = this.voipStateService.handleCallRinging((VoipCallRingingMessage) msg);
					} else if (msg instanceof VoipCallHangupMessage) {
						processingSuccessful = this.voipStateService.handleRemoteCallHangup((VoipCallHangupMessage) msg);
					}
				} else if (msg instanceof VoipCallOfferMessage) {
					// If calls are disabled, only react to offers.
					processingSuccessful = this.voipStateService.handleCallOffer((VoipCallOfferMessage) msg);
				} else {
					// ignore other VoIP related messages
					logger.debug("Ignoring VoIP related message, since calls are disabled");
					return ProcessingResult.SUCCESS;
				}
			} else {
				processingSuccessful = this.messageService.processIncomingContactMessage(msg);
			}

			return processingSuccessful ? ProcessingResult.SUCCESS : ProcessingResult.FAILED;
		}
		catch (FileNotFoundException f) {
			//do nothing
			logger.error("File not found", f);
			return ProcessingResult.SUCCESS;
		}
		catch (BadMessageException e) {
			logger.warn("Bad message exception", e);
			return e.shouldDrop() ? ProcessingResult.IGNORED : ProcessingResult.FAILED;
		}
		catch (Exception e) {
			logger.error("Unknown exception", e);
			return ProcessingResult.FAILED;
		}
	}

	@Override
	public void processServerAlert(String s) {
		ServerMessageModel msg = new ServerMessageModel(s, ServerMessageModel.Type.ALERT);
		this.messageService.saveIncomingServerMessage(msg);
	}

	@Override
	public void processServerError(String s, boolean b) {
		ServerMessageModel msg = new ServerMessageModel(s, ServerMessageModel.Type.ERROR);
		this.messageService.saveIncomingServerMessage(msg);
	}


	/**
	 * Result of a message processing
	 */
	public enum ProcessingResult {
		/**
		 * Message has successfully been processed.
		 */
		SUCCESS,

		/**
		 * Message processing failed (e.g. due to network error) and should be retried later
		 */
		FAILED,

		/**
		 * Message has been ignored due to being blocked or invalid.
		 */
		IGNORED;
	}
}

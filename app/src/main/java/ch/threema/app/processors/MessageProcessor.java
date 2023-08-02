/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2023 Threema GmbH
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.slf4j.Logger;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import ch.threema.app.exceptions.NotAllowedException;
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
import ch.threema.app.voip.groupcall.GroupCallManager;
import ch.threema.app.voip.services.VoipStateService;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.base.utils.Utils;
import ch.threema.domain.models.Contact;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.domain.protocol.csp.coders.MessageBox;
import ch.threema.domain.protocol.csp.coders.MessageCoder;
import ch.threema.domain.protocol.csp.connection.MessageProcessorInterface;
import ch.threema.domain.protocol.csp.fs.ForwardSecurityMessageProcessor;
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage;
import ch.threema.domain.protocol.csp.messages.AbstractMessage;
import ch.threema.domain.protocol.csp.messages.BadMessageException;
import ch.threema.domain.protocol.csp.messages.ContactDeleteProfilePictureMessage;
import ch.threema.domain.protocol.csp.messages.ContactRequestProfilePictureMessage;
import ch.threema.domain.protocol.csp.messages.ContactSetProfilePictureMessage;
import ch.threema.domain.protocol.csp.messages.DeliveryReceiptMessage;
import ch.threema.domain.protocol.csp.messages.GroupCreateMessage;
import ch.threema.domain.protocol.csp.messages.GroupDeletePhotoMessage;
import ch.threema.domain.protocol.csp.messages.GroupDeliveryReceiptMessage;
import ch.threema.domain.protocol.csp.messages.GroupLeaveMessage;
import ch.threema.domain.protocol.csp.messages.GroupRenameMessage;
import ch.threema.domain.protocol.csp.messages.GroupRequestSyncMessage;
import ch.threema.domain.protocol.csp.messages.GroupSetPhotoMessage;
import ch.threema.domain.protocol.csp.messages.MissingPublicKeyException;
import ch.threema.domain.protocol.csp.messages.TypingIndicatorMessage;
import ch.threema.domain.protocol.csp.messages.ballot.BallotVoteInterface;
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityEnvelopeMessage;
import ch.threema.domain.protocol.csp.messages.group.GroupJoinRequestMessage;
import ch.threema.domain.protocol.csp.messages.group.GroupJoinResponseMessage;
import ch.threema.domain.protocol.csp.messages.groupcall.GroupCallControlMessage;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerMessage;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallHangupMessage;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallOfferMessage;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallRingingMessage;
import ch.threema.domain.protocol.csp.messages.voip.VoipICECandidatesMessage;
import ch.threema.domain.protocol.csp.messages.voip.VoipMessage;
import ch.threema.domain.stores.ContactStore;
import ch.threema.domain.stores.IdentityStoreInterface;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.ServerMessageModel;

import static ch.threema.app.services.GroupService.CommonGroupReceiveStepsResult.SUCCESS;
import static ch.threema.app.services.GroupService.CommonGroupReceiveStepsResult.SYNC_REQUEST_SENT;

public class MessageProcessor implements MessageProcessorInterface {
	private static final Logger logger = LoggingUtil.getThreemaLogger("MessageProcessor");

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
	private final ForwardSecurityMessageProcessor forwardSecurityMessageProcessor;
	private final GroupCallManager groupCallManager;

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
		VoipStateService voipStateService,
		ForwardSecurityMessageProcessor forwardSecurityMessageProcessor,
		GroupCallManager groupCallManager) {

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
		this.forwardSecurityMessageProcessor = forwardSecurityMessageProcessor;
		this.groupCallManager = groupCallManager;
	}

	@NonNull
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

			if (logger.isInfoEnabled()) {
				logger.info(
					"Incoming message {} from {} to {} (type {})",
					boxmsg.getMessageId(),
					boxmsg.getFromIdentity(),
					boxmsg.getToIdentity(),
					Utils.byteToHex((byte) msg.getType(), true, true)
				);
			}

			//check if sender is on blacklist
			if (!(msg instanceof AbstractGroupMessage)) {
				if (this.blackListService != null && this.blackListService.has(msg.getFromIdentity())) {
					logger.debug("Direct message from {}: Contact blacklisted. Ignoring", msg.getFromIdentity());
					//ignore message of blacklisted member
					return ProcessIncomingResult.processed(msg.getType());
				}
			}

			this.contactService.setActive(msg.getFromIdentity());

			if (msg instanceof ForwardSecurityEnvelopeMessage) {
				if (!ConfigUtils.isForwardSecurityEnabled()) {
					logger.debug("PFS is disabled in build");
					return ProcessIncomingResult.processed(msg.getType());
				}

				// Decapsulate PFS message
				final Contact contact = this.contactService.getByIdentity(msg.getFromIdentity());
				if (contact == null) {
					logger.debug("Ignoring FS message from unknown identity {}", msg.getFromIdentity());
					return ProcessIncomingResult.processed();
				}
				AbstractMessage decapMessage = forwardSecurityMessageProcessor.processEnvelopeMessage(contact, (ForwardSecurityEnvelopeMessage) msg);
				if (decapMessage != null) {
					// Replace current abstract message with decapsulated version
					msg = decapMessage;
				} else {
					// Control message processed; nothing left to do
					return ProcessIncomingResult.processed();
				}
			} else {
				forwardSecurityMessageProcessor.warnIfMessageWithoutForwardSecurityReceived(msg);
			}

			if (msg instanceof TypingIndicatorMessage) {
				return processTypingIndicatorMessage((TypingIndicatorMessage) msg);
			} else if (msg instanceof DeliveryReceiptMessage) {
				return processDeliveryReceiptMessage((DeliveryReceiptMessage)msg);
			} else if (msg instanceof GroupDeliveryReceiptMessage) {
				return processGroupDeliveryReceiptMessage((GroupDeliveryReceiptMessage)msg);
			}

			/* send delivery receipt (but not for non-queued messages or delivery receipts) */
			if (!msg.flagNoServerQueuing()) {
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
					return ProcessIncomingResult.processed(msg.getType());
				}

				switch(this.processAbstractMessage(msg)) {
					case SUCCESS:
						// default behaviour: return ok.
						break;
					case FAILED:
						return ProcessIncomingResult.failed();
					case IGNORED:
						return ProcessIncomingResult.processed(msg.getType());
				}
			}

			return ProcessIncomingResult.processed(msg.getType());

		} catch (MissingPublicKeyException e) {
			if(this.preferenceService.isBlockUnknown()) {
				//its ok, return true and save nothing
				return ProcessIncomingResult.processed();
			}

			if(this.blackListService != null && this.blackListService.has(boxmsg.getFromIdentity())) {
				//its ok, a black listed identity, save NOTHING
				return ProcessIncomingResult.processed();
			}

			logger.error("Missing public key", e);
			return ProcessIncomingResult.failed();

		} catch (BadMessageException e) {
			logger.error("Bad message", e);
			logger.warn("Message {} error: invalid - dropping msg.", boxmsg.getMessageId());
			return ProcessIncomingResult.processed();

		} catch (Exception e) {
			logger.error("Unknown exception while processing BoxedMessage", e);
			return ProcessIncomingResult.failed();
		}
	}

	private ProcessIncomingResult processTypingIndicatorMessage(@NonNull TypingIndicatorMessage msg) {
		if (this.contactService.getByIdentity(msg.getFromIdentity()) != null) {
			this.contactService.setIsTyping(msg.getFromIdentity(), msg.isTyping());
		} else {
			logger.debug("Ignoring typing indicator message from unknown identity {}", msg.getFromIdentity());
		}
		return ProcessIncomingResult.processed(msg.getType());
	}

	private ProcessIncomingResult processDeliveryReceiptMessage(@NonNull DeliveryReceiptMessage msg) {
		final @Nullable MessageState state;
		switch (msg.getReceiptType()) {
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
			for (MessageId msgId : msg.getReceiptMessageIds()) {
				logger.info("Message {}: delivery receipt for {} (state = {})", msg.getMessageId(), msgId, state);
				this.messageService.updateMessageState(msgId, state, msg);
			}
		} else {
			logger.warn("Message {} error: unknown delivery receipt type", msg.getMessageId());
		}
		return ProcessIncomingResult.processed(msg.getType());
	}

	private ProcessIncomingResult processGroupDeliveryReceiptMessage(@NonNull GroupDeliveryReceiptMessage msg) {
		final @Nullable MessageState state;
		switch (msg.getReceiptType()) {
			case ProtocolDefines.DELIVERYRECEIPT_MSGUSERACK:
				state = MessageState.USERACK;
				break;
			case ProtocolDefines.DELIVERYRECEIPT_MSGUSERDEC:
				state = MessageState.USERDEC;
				break;
			default:
				state = null;
				break;
		}
		if (state != null) {
			if (groupService.runCommonGroupReceiveSteps(msg) != SUCCESS) {
				// If the common group receive steps did not succeed, ignore this delivery receipt
				return ProcessIncomingResult.processed(msg.getType());
			}
			for (MessageId msgId : msg.getReceiptMessageIds()) {
				logger.info("Message {}: group delivery receipt for {} (state = {})", msg.getMessageId(), msgId, state);
				this.messageService.updateGroupMessageState(msgId, state, msg);
			}
		} else {
			logger.warn("Message {} error: unknown or unsupported delivery receipt type", msg.getMessageId());
		}
		return ProcessIncomingResult.processed(msg.getType());
	}

	private boolean processBallotVoteInterface(BallotVoteInterface msg) throws NotAllowedException {
		BallotVoteResult r = this.ballotService.vote(msg);
		return r != null && r.isSuccess();
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
			long usableSpace = this.fileService.getInternalStorageFree();
			long requiredSpace = MessageDiskSizeUtil.getSize(msg);

			if(usableSpace < requiredSpace) {
				//show notification and do not try to save the message
				this.notificationService.showNotEnoughDiskSpace(usableSpace, requiredSpace);
				logger.error("Abstract Message {}: error - out of disk space {}/{}",
					msg.getMessageId(), requiredSpace, usableSpace);
				return ProcessingResult.FAILED;
			}

			boolean processingSuccessful = false;

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
											&& as.getApiGroupId().toString().equals(((GroupCreateMessage) msg).getApiGroupId().toString())) {

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
					if (groupService.runCommonGroupReceiveSteps((AbstractGroupMessage) msg) == SUCCESS) {
						processingSuccessful = this.groupService.renameGroup((GroupRenameMessage)msg);
					} else {
						return ProcessingResult.IGNORED;
					}
				}
				else if(msg instanceof GroupSetPhotoMessage) {
					if (groupService.runCommonGroupReceiveSteps((AbstractGroupMessage) msg) == SUCCESS) {
						processingSuccessful = this.groupService.updateGroupPhoto((GroupSetPhotoMessage) msg);
					} else {
						return ProcessingResult.IGNORED;
					}
				}
				else if(msg instanceof GroupDeletePhotoMessage) {
					if (groupService.runCommonGroupReceiveSteps((AbstractGroupMessage) msg) == SUCCESS) {
						processingSuccessful = this.groupService.deleteGroupPhoto((GroupDeletePhotoMessage) msg);
					} else {
						return ProcessingResult.IGNORED;
					}
				}
				else if(msg instanceof GroupLeaveMessage) {
					processingSuccessful = this.groupService.removeMemberFromGroup((GroupLeaveMessage) msg);
				}
				else if(msg instanceof GroupRequestSyncMessage) {
					processingSuccessful = this.groupService.processRequestSync((GroupRequestSyncMessage) msg);
				}
				else if (msg instanceof GroupCallControlMessage) {
					if (groupService.runCommonGroupReceiveSteps((AbstractGroupMessage) msg) == SUCCESS) {
						processingSuccessful = groupCallManager.handleControlMessage((GroupCallControlMessage) msg);
					} else {
						return ProcessingResult.IGNORED;
					}
				}
				else if (msg instanceof BallotVoteInterface) {
					if (groupService.runCommonGroupReceiveSteps((AbstractGroupMessage) msg) == SUCCESS) {
						processingSuccessful = processBallotVoteInterface((BallotVoteInterface) msg);
					} else {
						return ProcessingResult.IGNORED;
					}
				}
				else {
					GroupService.CommonGroupReceiveStepsResult result = groupService.runCommonGroupReceiveSteps((AbstractGroupMessage) msg);
					if (result == SUCCESS) {
						processingSuccessful = this.messageService.processIncomingGroupMessage((AbstractGroupMessage) msg);
					} else if (result == SYNC_REQUEST_SENT) {
						synchronized (pendingMessages) {
							pendingMessages.add(msg);
						}
						processingSuccessful = true;
					} else {
						return ProcessingResult.IGNORED;
					}
				}
			}
			else if (msg instanceof GroupJoinRequestMessage) {
				return this.incomingGroupJoinRequestService.process((GroupJoinRequestMessage) msg);
			}
			else if (msg instanceof GroupJoinResponseMessage) {
				return this.groupJoinResponseService.process((GroupJoinResponseMessage) msg);
			}
			else if (msg instanceof ContactSetProfilePictureMessage) {
				processingSuccessful = this.contactService.updateProfilePicture((ContactSetProfilePictureMessage) msg);
			}
			else if (msg instanceof ContactDeleteProfilePictureMessage) {
				processingSuccessful = this.contactService.deleteProfilePicture((ContactDeleteProfilePictureMessage) msg);
			}
			else if (msg instanceof ContactRequestProfilePictureMessage) {
				processingSuccessful = this.contactService.requestProfilePicture((ContactRequestProfilePictureMessage) msg);
			} else if (msg instanceof VoipMessage) {
				if (ConfigUtils.isCallsEnabled()) {
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
			} else if (msg instanceof BallotVoteInterface) {
				processingSuccessful = processBallotVoteInterface((BallotVoteInterface) msg);
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
			return ProcessingResult.IGNORED;
		}
		catch (Exception e) {
			logger.error("Unknown exception", e);
			return ProcessingResult.FAILED;
		}
	}

	@Override
	public void processServerAlert(String s) {
		ServerMessageModel msg = new ServerMessageModel(s, ServerMessageModel.TYPE_ALERT);
		this.messageService.saveIncomingServerMessage(msg);
	}

	@Override
	public void processServerError(String s, boolean b) {
		ServerMessageModel msg = new ServerMessageModel(s, ServerMessageModel.TYPE_ERROR);
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
		IGNORED
	}
}

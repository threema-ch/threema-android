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

import static ch.threema.app.services.GroupService.CommonGroupReceiveStepsResult.SUCCESS;
import static ch.threema.app.services.GroupService.CommonGroupReceiveStepsResult.SYNC_REQUEST_SENT;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import ch.threema.app.exceptions.NotAllowedException;
import ch.threema.app.groupcontrol.csp.IncomingGroupDeleteProfilePictureTask;
import ch.threema.app.groupcontrol.csp.IncomingGroupLeaveTask;
import ch.threema.app.groupcontrol.csp.IncomingGroupNameTask;
import ch.threema.app.groupcontrol.csp.IncomingGroupSetProfilePictureTask;
import ch.threema.app.groupcontrol.csp.IncomingGroupSetupTask;
import ch.threema.app.groupcontrol.csp.IncomingGroupSyncRequestTask;
import ch.threema.app.managers.ServiceManager;
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
import ch.threema.app.utils.PushUtil;
import ch.threema.app.voip.groupcall.GroupCallManager;
import ch.threema.app.voip.services.VoipStateService;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.base.utils.Utils;
import ch.threema.domain.models.Contact;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.ServerAddressProvider;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.domain.protocol.csp.coders.MessageBox;
import ch.threema.domain.protocol.csp.coders.MessageCoder;
import ch.threema.domain.protocol.csp.connection.MessageProcessorInterface;
import ch.threema.domain.protocol.csp.fs.ForwardSecurityMessageProcessor;
import ch.threema.domain.protocol.csp.fs.ForwardSecurityMessageProcessor.ForwardSecurityDecryptionResult;
import ch.threema.domain.protocol.csp.fs.ForwardSecurityMessageProcessor.PeerRatchetIdentifier;
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

public class MessageProcessor implements MessageProcessorInterface {
	private static final Logger logger = LoggingUtil.getThreemaLogger("MessageProcessor");

	@NonNull
	private final ServiceManager serviceManager;
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
	private final ServerAddressProvider serverAddressProvider;

	private final List<AbstractMessage> pendingMessages = new ArrayList<>();

	public MessageProcessor(
		@NonNull ServiceManager serviceManager,
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
		GroupCallManager groupCallManager,
		ServerAddressProvider serverAddressProvider
	) {
		this.serviceManager = serviceManager;
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
		this.serverAddressProvider = serverAddressProvider;
	}

	@NonNull
	@Override
	@WorkerThread
	public ProcessIncomingResult processIncomingMessage(MessageBox boxmsg) {
		AbstractMessage msg;
		try {
			// Special case: Web Client wakeup
			//
			// Note: Yes, this is nastily hacked in but the Web Client will die, so this can be removed completely.
			if (boxmsg.getFromIdentity().equals("*3MAPUSH")) {
				return processWebPushMessage(boxmsg);
			}

			// First, we need to ensure we have the public key of the sender
			contactService.fetchAndCacheContact(boxmsg.getFromIdentity());

			// Try to decode the message. At this point we have the public key of the sender either
			// stored or cached in the contact store.
			MessageCoder messageCoder = new MessageCoder(this.contactStore, this.identityStore);
			msg = messageCoder.decode(boxmsg);

			if (logger.isInfoEnabled()) {
				logger.info(
					"Incoming message {} from {} to {} (type {})",
					boxmsg.getMessageId(),
					boxmsg.getFromIdentity(),
					boxmsg.getToIdentity(),
					Utils.byteToHex((byte) msg.getType(), true, true)
				);
			}

			PeerRatchetIdentifier peerRatchet = null;

			if (msg instanceof ForwardSecurityEnvelopeMessage) {
				if (!ConfigUtils.isForwardSecurityEnabled()) {
					logger.debug("PFS is disabled in build");
					return ProcessIncomingResult.processed(msg.getType(), null);
				}

				// Decapsulate PFS message
				final Contact contact = contactStore.getContactForIdentityIncludingCache(msg.getFromIdentity());
				if (contact == null) {
					logger.debug("Ignoring FS message from unknown identity {}", msg.getFromIdentity());
					return ProcessIncomingResult.processed(null);
				}
				ForwardSecurityDecryptionResult result = forwardSecurityMessageProcessor.processEnvelopeMessage(contact, (ForwardSecurityEnvelopeMessage) msg);
				peerRatchet = result.peerRatchetIdentifier;
				if (result.message != null) {
					// Replace current abstract message with decapsulated version
					msg = result.message;
				} else {
					// Control message processed; nothing left to do
					return ProcessIncomingResult.processed(null, peerRatchet);
				}

				logger.info(
					"Processing decrypted message {} from {} to {} (type {})",
					msg.getMessageId(),
					msg.getFromIdentity(),
					msg.getToIdentity(),
					Utils.byteToHex((byte) msg.getType(), true, true)
				);
			} else {
				forwardSecurityMessageProcessor.warnIfMessageWithoutForwardSecurityReceived(msg);
			}

			// Abort processing the message when the sender is blocked and the message is not
			// exempted from blocking.
			if (isBlocked(msg.getFromIdentity()) && !msg.exemptFromBlocking()) {
				logger.info(
					"Message {} from {} will be discarded: Contact is implicitly or explicitly blocked.",
					msg.getMessageId(),
					msg.getFromIdentity()
				);
				// Ignore message of blacklisted member
				return ProcessIncomingResult.processed(msg.getType(), null);
			}

			if (msg instanceof TypingIndicatorMessage) {
				return processTypingIndicatorMessage((TypingIndicatorMessage) msg, peerRatchet);
			} else if (msg instanceof DeliveryReceiptMessage) {
				return processDeliveryReceiptMessage((DeliveryReceiptMessage)msg, peerRatchet);
			} else if (msg instanceof GroupDeliveryReceiptMessage) {
				return processGroupDeliveryReceiptMessage((GroupDeliveryReceiptMessage)msg, peerRatchet);
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
					return ProcessIncomingResult.processed(msg.getType(), peerRatchet);
				}

				switch(this.processAbstractMessage(msg)) {
					case SUCCESS:
						// default behaviour: return ok.
						break;
					case FAILED:
						return ProcessIncomingResult.failed();
					case IGNORED:
						return ProcessIncomingResult.processed(msg.getType(), peerRatchet);
				}
			}

			return ProcessIncomingResult.processed(msg.getType(), peerRatchet);

		} catch (MissingPublicKeyException e) {
			logger.error("Missing public key", e);
			return ProcessIncomingResult.processed(null);
		} catch (BadMessageException e) {
			logger.error("Bad message", e);
			logger.warn("Message {} error: invalid - dropping msg.", boxmsg.getMessageId());
			return ProcessIncomingResult.processed(null);
		} catch (APIConnector.NetworkException e) {
			logger.error("Could not process message {} due to an network error", boxmsg.getMessageId());
			// As we have a network error, we do not mark the message as processed to restart the
			// connection.
			return ProcessIncomingResult.failed();
		} catch (APIConnector.HttpConnectionException e) {
			logger.error("Could not process message {} due to http error {}", boxmsg.getMessageId(), e.getErrorCode());
			// As we have a network error, we do not mark the message as processed to restart the
			// connection.
			return ProcessIncomingResult.failed();
		} catch (Exception e) {
			logger.error("Unknown exception while processing BoxedMessage", e);
			// Mark the message as processed, as retrying to process it later does not make sense
			// due to forward security. In this case we may lose a message, but we would lose it
			// anyways due to forward security. Returning failed instead of processed would restart
			// the connection and we would potentially run into the same problem.
			return ProcessIncomingResult.processed(null);
		}
	}

	private ProcessIncomingResult processWebPushMessage(@NonNull MessageBox boxmsg) throws BadMessageException, ThreemaException {
		// This code is mostly a copy of the essential parts of the MessageCoder. It's ugly and
		// should be removed when the web client is gone.

		final byte[] publicKey = serverAddressProvider.getThreemaPushPublicKey();
		if (publicKey == null) {
			throw new BadMessageException("Cannot handle message from *3MAPUSH, public key not configured");
		}

		if (!boxmsg.getToIdentity().equals(identityStore.getIdentity())) {
			throw new BadMessageException("Message is not for own identity, cannot decode");
		}

		// Decrypt. We use hard-coded public key here and the sender is never added as a contact.
		byte[] payload = identityStore.decryptData(boxmsg.getBox(), boxmsg.getNonce(), publicKey);
		if (payload == null) {
			throw new BadMessageException("Decryption of message from " + boxmsg.getFromIdentity() + " failed");
		}
		if (payload.length == 1) {
			throw new BadMessageException("Empty message received");
		}
		int padbytes = payload[payload.length - 1] & 0xFF;
		int realDataLength = payload.length - padbytes;
		if (realDataLength < 1) {
			throw new BadMessageException("Bad message padding");
		}

		// Ensure the message is `web-session-resume`
		int type = (payload[0] & 0xff);
		if (type != ProtocolDefines.MSGTYPE_WEB_SESSION_RESUME) {
			throw new BadMessageException("Bad type for web push message: " + type);
		}

		// Decode the payload, assumed to be in the format described here:
		// https://github.com/threema-ch/push-relay/tree/master#push-payload
		Map<String, String> data = new HashMap<>();
		try {
			JSONObject object = new JSONObject(new String(payload, 1, realDataLength - 1, StandardCharsets.UTF_8));
			data.put("wcs", object.getString("wcs"));
			data.put("wct", String.valueOf(object.getLong("wct")));
			data.put("wcv", String.valueOf(object.getInt("wcv")));
			if (object.has("wca")) {
				data.put("wca", object.getString("wca"));
			}
		} catch (JSONException e) {
			throw new BadMessageException("Bad web push message payload: " + e.getMessage());
		}

		// Forward it to the push util and we're done.
		//
		// IMPORTANT: This assumes that the PushUtil recognises this as a web push (i.e. by checking for the "wcs" key).
		PushUtil.processRemoteMessage(data);
		return ProcessIncomingResult.processed(null);
	}

	private ProcessIncomingResult processTypingIndicatorMessage(@NonNull TypingIndicatorMessage msg, @Nullable PeerRatchetIdentifier peerRatchet) {
		if (this.contactService.getByIdentity(msg.getFromIdentity()) != null) {
			this.contactService.setIsTyping(msg.getFromIdentity(), msg.isTyping());
		} else {
			logger.debug("Ignoring typing indicator message from unknown identity {}", msg.getFromIdentity());
		}
		return ProcessIncomingResult.processed(msg.getType(), peerRatchet);
	}

	private ProcessIncomingResult processDeliveryReceiptMessage(@NonNull DeliveryReceiptMessage msg, @Nullable PeerRatchetIdentifier peerRatchet) {
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
		return ProcessIncomingResult.processed(msg.getType(), peerRatchet);
	}

	private ProcessIncomingResult processGroupDeliveryReceiptMessage(@NonNull GroupDeliveryReceiptMessage msg, @Nullable PeerRatchetIdentifier peerRatchet) {
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
				return ProcessIncomingResult.processed(msg.getType(), peerRatchet);
			}
			for (MessageId msgId : msg.getReceiptMessageIds()) {
				logger.info("Message {}: group delivery receipt for {} (state = {})", msg.getMessageId(), msgId, state);
				this.messageService.updateGroupMessageState(msgId, state, msg);
			}
		} else {
			logger.warn("Message {} error: unknown or unsupported delivery receipt type", msg.getMessageId());
		}
		return ProcessIncomingResult.processed(msg.getType(), peerRatchet);
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
			this.contactService.setActive(msg.getFromIdentity());

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

			if (!(msg instanceof AbstractGroupMessage) && contactService.getByIdentity(msg.getFromIdentity()) == null) {
				contactService.createContactByIdentity(msg.getFromIdentity(), true);
				contactService.updatePublicNickName(msg);
			}

			boolean processingSuccessful = false;

			if(msg instanceof AbstractGroupMessage) {
				if(msg instanceof GroupCreateMessage) {
					//new group or sync it!
					ProcessingResult result = new IncomingGroupSetupTask(
						(GroupCreateMessage) msg,
						serviceManager
					).run();

					if (result == ProcessingResult.SUCCESS
						&& groupService.getByGroupMessage((AbstractGroupMessage) msg) != null) {
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
					return result;
				}
				else if(msg instanceof GroupRenameMessage) {
					return new IncomingGroupNameTask(
						(GroupRenameMessage) msg,
						serviceManager
					).run();
				}
				else if(msg instanceof GroupSetPhotoMessage) {
					return new IncomingGroupSetProfilePictureTask(
						(GroupSetPhotoMessage) msg,
						serviceManager
					).run();
				}
				else if(msg instanceof GroupDeletePhotoMessage) {
					return new IncomingGroupDeleteProfilePictureTask(
						(GroupDeletePhotoMessage) msg,
						serviceManager
					).run();
				}
				else if(msg instanceof GroupLeaveMessage) {
					return new IncomingGroupLeaveTask(
						(GroupLeaveMessage) msg,
						serviceManager
					).run();
				}
				else if(msg instanceof GroupRequestSyncMessage) {
					return new IncomingGroupSyncRequestTask(
						(GroupRequestSyncMessage) msg,
						serviceManager
					).run();
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

			return processingSuccessful ? ProcessingResult.SUCCESS : ProcessingResult.IGNORED;
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
			if (ConfigUtils.isTestBuild()) {
				// Restart the connection in test builds. The message will be processed again when
				// the connection has been established again. Note that this may lead to infinite
				// reconnection loops. Therefore we only enable this in test builds.
				return ProcessingResult.FAILED;
			} else {
				// Simply drop the message if processing it fails. With forward security we won't be
				// able to decrypt the message again anyways.
				return ProcessingResult.IGNORED;
			}
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

	private boolean isBlocked(@NonNull String identity) {
		// Return true, if the identity is explicitly blocked
		if (blackListService != null && blackListService.has(identity)) {
			return true;
		}

		// If not explicitly blocked, check if it is implicitly blocked
		return contactService.getByIdentity(identity) == null && preferenceService.isBlockUnknown();
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

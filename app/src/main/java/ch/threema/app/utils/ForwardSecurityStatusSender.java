/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2025 Threema GmbH
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.slf4j.Logger;

import java.util.Collections;

import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.routines.UpdateFeatureLevelRoutine;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.UserService;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.data.repositories.ContactModelRepository;
import ch.threema.domain.fs.DHSession;
import ch.threema.domain.fs.DHSessionId;
import ch.threema.domain.models.Contact;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.ThreemaFeature;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.domain.protocol.csp.fs.ForwardSecurityStatusListener;
import ch.threema.domain.protocol.csp.messages.AbstractMessage;
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataReject;
import ch.threema.protobuf.csp.e2e.fs.Terminate;
import ch.threema.protobuf.csp.e2e.fs.Version;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.data.status.ForwardSecurityStatusDataModel.ForwardSecurityStatusType;

public class ForwardSecurityStatusSender implements ForwardSecurityStatusListener {
    private final static Logger logger = getThreemaLogger("ForwardSecurityStatusSender");
    private final boolean debug;
    @NonNull
    private final ContactService contactService;
    @NonNull
    private final MessageService messageService;
    @NonNull
    private final APIConnector apiConnector;
    @NonNull
    private final UserService userService;
    @NonNull
    private final ContactModelRepository contactModelRepository;

    public ForwardSecurityStatusSender(
        @NonNull ContactService contactService,
        @NonNull MessageService messageService,
        @NonNull APIConnector apiConnector,
        @NonNull UserService userService,
        @NonNull ContactModelRepository contactModelRepository
    ) {
        this.debug = ConfigUtils.isDevBuild();
        this.contactService = contactService;
        this.messageService = messageService;
        this.apiConnector = apiConnector;
        this.userService = userService;
        this.contactModelRepository = contactModelRepository;
    }

    @Override
    public void newSessionInitiated(@Nullable DHSession session, @NonNull Contact contact) {
        postStatusMessageDebug(String.format("New initiator session %s", session), contact);
    }

    @Override
    public void responderSessionEstablished(@NonNull DHSession session, @NonNull Contact contact, boolean existingSessionPreempted) {
        postStatusMessageDebug(String.format("Responder session established %s", session), contact);

        if (existingSessionPreempted) {
            postStatusMessage(contact, ForwardSecurityStatusType.FORWARD_SECURITY_RESET);
        }

        // If a new session has been established with V1.1 or higher, we display the message,
        // that forward security has been enabled (by both participants) immediately.
        //
        // Rationale for local/outgoing applied version: Should be identical to remote/incoming
        // version after initial negotiation.
        if (session.getOutgoingAppliedVersion().getNumber() >= Version.V1_1.getNumber()) {
            postStatusMessage(contact, ForwardSecurityStatusType.FORWARD_SECURITY_ESTABLISHED);
        }
    }

    @Override
    public void initiatorSessionEstablished(@NonNull DHSession session, @NonNull Contact contact) {
        postStatusMessageDebug(String.format("Initiator session established %s", session), contact);

        // Rationale for local/outgoing applied version: Should be identical to remote/incoming
        // version after initial negotiation.
        if (session.getOutgoingAppliedVersion().getNumber() >= Version.V1_1.getNumber()) {
            // If a new session has been established with V1.1 or higher, we display the message,
            // that forward security has been enabled (by both participants) in this chat.
            postStatusMessage(contact, ForwardSecurityStatusType.FORWARD_SECURITY_ESTABLISHED);
        }
    }

    @Override
    public void rejectReceived(@NonNull ForwardSecurityDataReject rejectData, @NonNull Contact contact, @Nullable DHSession session, boolean hasForwardSecuritySupport) {
        postStatusMessageDebug(String.format(
            "Reject received for session %s (session-id=%s, rejected-message-id=%s, cause=%s)",
            session,
            rejectData.getSessionId(),
            rejectData.getRejectedApiMessageId(),
            rejectData.getCause()
        ), contact);

        // Only show status message for sessions that are known. Otherwise, all sent (and rejected) messages produce
        // this status message, which is very verbose.
        if (session != null) {
            if (hasForwardSecuritySupport) {
                postStatusMessage(contact, ForwardSecurityStatusType.FORWARD_SECURITY_RESET);
            } else {
                postStatusMessage(contact, ForwardSecurityStatusType.FORWARD_SECURITY_UNAVAILABLE_DOWNGRADE);
            }
        }
    }

    @Override
    public void sessionNotFound(@Nullable DHSessionId sessionId, @NonNull Contact contact) {
        postStatusMessageDebug(String.format("Session not found (session-id=%s)", sessionId), contact);
    }

    @Override
    public void sessionForMessageNotFound(@Nullable DHSessionId sessionId, @Nullable MessageId messageId, @NonNull Contact contact) {
        postStatusMessageDebug(String.format("Session not found (session-id=%s, message-id=%s)", sessionId, messageId), contact);
    }

    @Override
    public void sessionTerminated(@Nullable DHSessionId sessionId, @NonNull Contact contact, boolean sessionUnknown, boolean hasForwardSecuritySupport) {
        postStatusMessageDebug(String.format("Session terminated (session-id=%s)", sessionId), contact);

        // Only show status message for sessions that are known. It doesn't make sense to report a terminated session
        // we don't even know about.
        if (!sessionUnknown) {
            if (hasForwardSecuritySupport) {
                postStatusMessage(contact, ForwardSecurityStatusType.FORWARD_SECURITY_RESET);
            } else {
                postStatusMessage(contact, ForwardSecurityStatusType.FORWARD_SECURITY_UNAVAILABLE_DOWNGRADE);
            }
        }
    }

    @Override
    public void messagesSkipped(@Nullable DHSessionId sessionId, @NonNull Contact contact, int numSkipped) {
        logger.info("Skipped {} messages from contact {} (session-id={})", numSkipped, contact.getIdentity(), sessionId);
    }

    @Override
    public void messageOutOfOrder(@Nullable DHSessionId sessionId, @NonNull Contact contact, @Nullable MessageId messageId) {
        postStatusMessageDebug(String.format("Message out of order (session-id=%s, message-id=%s). Please report this to the Android Team!", sessionId, messageId), contact);
    }

    @Override
    public void first4DhMessageReceived(@NonNull DHSession session, @NonNull Contact contact) {
        postStatusMessageDebug(String.format("First 4DH message received in session %s", session), contact);

        // If we received a message with forward security in a session of version 1.0, then we
        // inform that forward security has been enabled (by both participants). Note that this is
        // only necessary for version 1.0, as forward security is enabled by default starting in
        // version 1.1 and therefore the status is shown as soon as the session has been established
        // TODO(ANDR-2452): Remove this status message when most of clients support 1.1 anyway
        //
        // Rationale for local/outgoing applied version: We expect both sides to speak the version
        // eventually if it was offered by remote (but not yet applied).
        if (session.getOutgoingAppliedVersion() == Version.V1_0) {
            postStatusMessage(contact, ForwardSecurityStatusType.FORWARD_SECURITY_ESTABLISHED);

            // Set the forward security state to on (only required in version 1.0)
            ContactModel contactModel = contactService.getByIdentity(contact.getIdentity());
            if (contactModel != null) {
                contactModel.setForwardSecurityState(ContactModel.FS_ON);
                contactService.persistForwardSecurityState(contact.getIdentity(), ContactModel.FS_ON);
            }
        }
    }

    @Override
    public void versionsUpdated(@NonNull DHSession session, @NonNull DHSession.UpdatedVersionsSnapshot versionsSnapshot, @NonNull Contact contact) {
        postStatusMessageDebug(String.format("Updated versions %s %s", versionsSnapshot, session), contact);

        ContactModel contactModel = contactService.getByIdentity(contact.getIdentity());

        // If we update a session from version 1.0 to 1.1 (or newer), then we show a status message,
        // that forward security has been enabled (by both participants). Note that this message is
        // only shown, when no 4DH message has been received in the session with version 1.0 because
        // the status message has already been shown at this point.
        // TODO(ANDR-2452): Remove this status message when most of clients support 1.1 anyway
        if (versionsSnapshot.before.local == Version.V1_0
            && versionsSnapshot.after.local.getNumber() >= Version.V1_1.getNumber()
            && contactModel != null
            && contactModel.getForwardSecurityState() == ContactModel.FS_OFF
        ) {
            postStatusMessage(contact, ForwardSecurityStatusType.FORWARD_SECURITY_ESTABLISHED);
        }
    }

    @Override
    public void messageWithoutFSReceived(@NonNull Contact contact, @NonNull DHSession session, @NonNull AbstractMessage message) {
        logger.warn("Received message {} from {} without forward security of type {} despite having a session with remote/incoming minimum applied version {}",
            message.getMessageId(),
            contact.getIdentity(),
            message.getClass().getSimpleName(),
            session.getMinimumIncomingAppliedVersion()
        );

        // Rationale for local/outgoing applied version: We expect both sides to speak the version
        // eventually if it was offered by remote (but not yet applied) and we also use it in
        // `first4DhMessageReceived`.
        if (session.getOutgoingAppliedVersion() == Version.V1_0) {
            // For sessions of version 1.0 show warning only once
            ContactModel contactModel = contactService.getByIdentity(contact.getIdentity());
            if (contactModel != null && contactModel.getForwardSecurityState() == ContactModel.FS_ON) {
                contactModel.setForwardSecurityState(ContactModel.FS_OFF);
                contactService.persistForwardSecurityState(contact.getIdentity(), ContactModel.FS_OFF);
                postStatusMessage(contact, ForwardSecurityStatusType.MESSAGE_WITHOUT_FORWARD_SECURITY);
            }
        } else if (session.getOutgoingAppliedVersion().getNumber() >= Version.V1_1.getNumber()) {
            // TODO(ANDR-2452): Do not distinguish between 1.0 and newer versions when enough
            // clients have updated. Show this status message for every message without FS.

            // For sessions with version 1.1 or newer, inform for every message without fs
            postStatusMessage(contact, ForwardSecurityStatusType.MESSAGE_WITHOUT_FORWARD_SECURITY);
        }
    }

    @Override
    public boolean hasForwardSecuritySupport(@NonNull Contact contact) {
        ContactModel contactModel = contactService.getByIdentity(contact.getIdentity());
        if (contactModel == null) {
            try {
                Long[] fm = apiConnector.checkFeatureMask(new String[]{contact.getIdentity()});
                return fm.length > 0 && fm[0] != null && ThreemaFeature.canForwardSecurity(fm[0]);
            } catch (Exception e) {
                logger.error("Could not get feature mask for contact");
                return false;
            }
        }
        return ThreemaFeature.canForwardSecurity(contactModel.getFeatureMask());
    }

    @Override
    public void updateFeatureMask(@NonNull Contact contact) {
        ContactModel contactModel = contactService.getByIdentity(contact.getIdentity());
        if (contactModel == null) {
            return;
        }

        // Force a feature mask re-fetch
        UpdateFeatureLevelRoutine.removeTimeCache(contactModel.getIdentity());
        new UpdateFeatureLevelRoutine(
            contactModelRepository,
            userService,
            apiConnector,
            Collections.singletonList(contactModel.getIdentity())
        ).run();
    }

    @Override
    public void postIllegalSessionState(@NonNull DHSessionId sessionId, @NonNull Contact contact) {
        ContactModel contactModel = contactService.getByIdentity(contact.getIdentity());
        if (contactModel == null) {
            return;
        }

        postStatusMessage(contact, ForwardSecurityStatusType.FORWARD_SECURITY_ILLEGAL_SESSION_STATE);
    }

    // TODO(ANDR-2519): Remove when md allows fs
    @Override
    public void allSessionsTerminated(@NonNull Contact contact, @NonNull Terminate.Cause cause) {
        if (cause == Terminate.Cause.DISABLED_BY_LOCAL) {
            postStatusMessage(contact, ForwardSecurityStatusType.FORWARD_SECURITY_DISABLED);
        }
    }

    private void postStatusMessageDebug(@NonNull String message, @NonNull Contact contact) {
        if (debug) {
            ContactModel contactModel = contactService.getByIdentity(contact.getIdentity());
            if (contactModel != null) {
                ContactMessageReceiver receiver = contactService.createReceiver(contactModel);
                messageService.createForwardSecurityStatus(receiver, ForwardSecurityStatusType.STATIC_TEXT, 0, "PFS: " + message);
            }
        }
        logger.info("PFS: {}", message);
    }

    private void postStatusMessage(@NonNull Contact contact, @ForwardSecurityStatusType int type) {
        postStatusMessage(contact, type, 0);
    }

    private void postStatusMessage(@NonNull Contact contact, @ForwardSecurityStatusType int type, int quantity) {
        ContactModel contactModel = contactService.getByIdentity(contact.getIdentity());
        if (contactModel != null) {
            ContactMessageReceiver receiver = contactService.createReceiver(contactModel);
            messageService.createForwardSecurityStatus(receiver, type, quantity, null);
        }
    }

}

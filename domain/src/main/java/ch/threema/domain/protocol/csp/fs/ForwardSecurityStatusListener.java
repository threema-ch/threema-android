/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2024 Threema GmbH
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

package ch.threema.domain.protocol.csp.fs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.domain.fs.DHSessionId;
import ch.threema.domain.fs.DHSession;
import ch.threema.domain.models.Contact;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.csp.messages.AbstractMessage;
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataReject;
import ch.threema.protobuf.csp.e2e.fs.Terminate;

/**
 * Interface for classes that receive updates when a forward security session changes status.
 */
public interface ForwardSecurityStatusListener {
    void newSessionInitiated(@NonNull DHSession session, @NonNull Contact contact);

    void responderSessionEstablished(@NonNull DHSession session, @NonNull Contact contact, boolean existingSessionPreempted);

    void initiatorSessionEstablished(@NonNull DHSession session, @NonNull Contact contact);

    void rejectReceived(@NonNull ForwardSecurityDataReject rejectData, @NonNull Contact contact, @Nullable DHSession session, boolean hasForwardSecuritySupport);

    void sessionNotFound(@NonNull DHSessionId sessionId, @NonNull Contact contact);

    void sessionForMessageNotFound(@NonNull DHSessionId sessionId, @Nullable MessageId messageId, @NonNull Contact contact);

    void sessionTerminated(@Nullable DHSessionId sessionId, @NonNull Contact contact, boolean sessionUnknown, boolean hasForwardSecuritySupport);

    void messagesSkipped(@NonNull DHSessionId sessionId, @NonNull Contact contact, int numSkipped);

    void messageOutOfOrder(@NonNull DHSessionId sessionId, @NonNull Contact contact, @Nullable MessageId messageId);

    void first4DhMessageReceived(@NonNull DHSession session, @NonNull Contact contact);

    void versionsUpdated(@NonNull DHSession session, @NonNull DHSession.UpdatedVersionsSnapshot versionsSnapshot, @NonNull Contact contact);

    void messageWithoutFSReceived(@NonNull Contact contact, @NonNull DHSession session, @NonNull AbstractMessage message);

    void postIllegalSessionState(@NonNull DHSessionId sessionId, @NonNull Contact contact);

    /**
     * TODO(ANDR-2519): Remove when md supports fs by default
     * <p>
     * Called when all sessions with a contact have been terminated.
     * This will only be called when there was at least one session that has been terminated and
     * termination was carried out by the delete and terminate fs sessions task or when an init has
     * been received by a contact that does not support forward security.
     */
    void allSessionsTerminated(@NonNull Contact contact, @NonNull Terminate.Cause cause);

    /**
     * Check whether the contact has forward security support based on the feature mask. Note that
     * this does not fetch the latest feature mask.
     *
     * @param contact the contact that is checked for forward security support
     * @return true if the contact's feature mask indicates forward security support
     */
    boolean hasForwardSecuritySupport(@NonNull Contact contact);

    /**
     * Update the feature mask of the given contact and check whether forward security is supported.
     *
     * @param contact the contact that is checked for forward security support
     */
    void updateFeatureMask(@NonNull Contact contact);
}

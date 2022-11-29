/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2022 Threema GmbH
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

package ch.threema.domain.stores;

import androidx.annotation.Nullable;
import ch.threema.domain.fs.DHSession;
import ch.threema.domain.fs.DHSessionId;

public interface DHSessionStoreInterface {
	/**
	 * Get the DH session with the specified contact and session ID.
	 *
	 * @param myIdentity   my Threema identity
	 * @param peerIdentity peer Threema identity
	 * @param sessionId    the desired session ID
	 * @return the DH session, or null if not found
	 */
	@Nullable
	DHSession getDHSession(String myIdentity, String peerIdentity, DHSessionId sessionId) throws DHSessionStoreException;

	/**
	 * Get the "best" DH session with the specified contact, which is the session that has the
	 * lowest session ID, while preferring 4DH sessions.
	 *
	 * @param myIdentity   my Threema identity
	 * @param peerIdentity peer Threema identity
	 * @return the DH session, or null if none found
	 */
	@Nullable
	DHSession getBestDHSession(String myIdentity, String peerIdentity) throws DHSessionStoreException;

	/**
	 * Store a DH session.
	 *
	 * @param session the new or updated session
	 */
	void storeDHSession(DHSession session) throws DHSessionStoreException;

	/**
	 * Delete the DH session for the specified contact and session ID.
	 *
	 * @param myIdentity   my Threema identity
	 * @param peerIdentity peer Threema identity
	 * @param sessionId    the ID of the session to be deleted
	 * @return whether a session was deleted
	 */
	boolean deleteDHSession(String myIdentity, String peerIdentity, DHSessionId sessionId) throws DHSessionStoreException;

	/**
	 * Delete all DH sessions for the specified contact.
	 *
	 * @param myIdentity   my Threema identity
	 * @param peerIdentity peer Threema identity
	 * @return number of deleted sessions
	 */
	int deleteAllDHSessions(String myIdentity, String peerIdentity) throws DHSessionStoreException;

	/**
	 * Delete all sessions for the specified contact, except the one with the specified session ID.
	 * This can be used to prune old sessions that are no longer in use and have been created e.g. due
	 * to race conditions.
	 *
	 * @param myIdentity   my Threema identity
	 * @param peerIdentity peer Threema identity
	 * @param fourDhOnly   if set, only 4DH sessions will be deleted
	 * @param excludeSessionId session ID to exclude
	 * @return number of deleted sessions
	 */
	int deleteAllSessionsExcept(String myIdentity, String peerIdentity, DHSessionId excludeSessionId, boolean fourDhOnly) throws DHSessionStoreException;
}

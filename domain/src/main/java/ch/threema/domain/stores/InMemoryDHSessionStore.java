/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022 Threema GmbH
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

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import androidx.annotation.Nullable;
import ch.threema.domain.fs.DHSession;
import ch.threema.domain.fs.DHSessionId;

/**
 * Dummy DH session store for testing purposes only (not optimized).
 */
public class InMemoryDHSessionStore implements DHSessionStoreInterface {
	final private List<DHSession> dhSessionList;

	public InMemoryDHSessionStore() {
		this.dhSessionList = new LinkedList<>();
	}

	@Nullable
	@Override
	public DHSession getDHSession(String myIdentity, String peerIdentity, DHSessionId sessionId) {
		for (DHSession session : dhSessionList) {
			if (session.getMyIdentity().equals(myIdentity) &&
				session.getPeerIdentity().equals(peerIdentity) &&
				session.getId().equals(sessionId)
			) {
				return session;
			}
		}
		return null;
	}

	@Nullable
	@Override
	public DHSession getBestDHSession(String myIdentity, String peerIdentity) {
		DHSession currentBestSession = null;

		for (DHSession session : dhSessionList) {
			if (!session.getMyIdentity().equals(myIdentity) || !session.getPeerIdentity().equals(peerIdentity)) {
				continue;
			}

			if (currentBestSession == null ||
				(currentBestSession.getMyRatchet4DH() == null && session.getMyRatchet4DH() != null) ||
				(session.getMyRatchet4DH() != null && currentBestSession.getId().compareTo(session.getId()) > 0)) {
				currentBestSession = session;
			}
		}

		return currentBestSession;
	}

	@Override
	public void storeDHSession(DHSession session) {
		deleteDHSession(session.getMyIdentity(), session.getPeerIdentity(), session.getId());
		dhSessionList.add(session);
	}

	@Override
	public boolean deleteDHSession(String myIdentity, String peerIdentity, DHSessionId sessionId) {
		for (Iterator<DHSession> it = this.dhSessionList.iterator(); it.hasNext(); ) {
			DHSession curSession = it.next();
			if (curSession.getMyIdentity().equals(myIdentity) &&
				curSession.getPeerIdentity().equals(peerIdentity) &&
				curSession.getId().equals(sessionId)) {
				it.remove();
				return true;
			}
		}
		return false;
	}

	@Override
	public int deleteAllDHSessions(String myIdentity, String peerIdentity) {
		int numDeleted = 0;
		for (Iterator<DHSession> it = this.dhSessionList.iterator(); it.hasNext(); ) {
			DHSession session = it.next();
			if (session.getMyIdentity().equals(myIdentity) && session.getPeerIdentity().equals(peerIdentity)) {
				it.remove();
				numDeleted++;
			}
		}
		return numDeleted;
	}

	@Override
	public int deleteAllSessionsExcept(String myIdentity, String peerIdentity, DHSessionId exceptSessionId, boolean fourDhOnly) {
		int numDeleted = 0;
		for (Iterator<DHSession> it = this.dhSessionList.iterator(); it.hasNext(); ) {
			DHSession session = it.next();
			if (session.getMyIdentity().equals(myIdentity) &&
				session.getPeerIdentity().equals(peerIdentity) &&
				(!fourDhOnly || session.getMyRatchet4DH() != null) &&
				!exceptSessionId.equals(session.getId())) {
				it.remove();
				numDeleted++;
			}
		}
		return numDeleted;
	}
}

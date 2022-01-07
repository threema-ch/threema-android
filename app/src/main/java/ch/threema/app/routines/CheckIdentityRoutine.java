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

package ch.threema.app.routines;

import ch.threema.app.services.UserService;

/**
 * Check the state of the pending email linkBallot
 */
public class CheckIdentityRoutine implements Runnable {
	private final UserService userService;
	private final OnStatusChanged onStatusChanged;

	public interface OnStatusChanged {
		void onFinished(boolean success);
	}

	public CheckIdentityRoutine(UserService userService, OnStatusChanged onStatusChanged) {
		this.userService = userService;
		this.onStatusChanged = onStatusChanged;
	}

	@Override
	public void run() {
		//check email linking state
		if(this.userService.getEmailLinkingState() == UserService.LinkingState_PENDING) {
			//only if linking state is pending
			this.userService.checkEmailLinkState();
		}

		//check revocation key
		this.userService.checkRevocationKey(false);

		if(this.onStatusChanged != null) {
			this.onStatusChanged.onFinished(true);
		}
	}
}

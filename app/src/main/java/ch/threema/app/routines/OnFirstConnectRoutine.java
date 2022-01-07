/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2022 Threema GmbH
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
 * This routine will be called on the first valid connection to the threema server
 */
public class OnFirstConnectRoutine implements Runnable {

	private final UserService userService;
	private int runCount = 0;

	public OnFirstConnectRoutine(
			UserService userService) {
		this.userService = userService;
	}

	@Override
	public void run() {
		this.runCount++;
		if(this.userService.hasIdentity()) {
			//try to send user flags
			this.userService.sendFlags();
		}

	}

	public int getRunCount() {
		return this.runCount;
	}

}

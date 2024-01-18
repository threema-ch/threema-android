/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2024 Threema GmbH
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

package ch.threema.app.voip;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

/**
 * An immutable snapshot of the call state.
 *
 * Cannot be modified and is therefore tread safe.
 */
@AnyThread
public class CallStateSnapshot {
	private final @CallState.State int state;
	private final long callId;

	@Deprecated
	private final long incomingCallCounter;

	public CallStateSnapshot(int state, long callId, long incomingCallCounter) {
		this.state = state;
		this.callId = callId;
		this.incomingCallCounter = incomingCallCounter;
	}

	@Override
	public String toString() {
		return "CallState{" +
			"state=" + this.getName() +
			", callId=" + this.callId +
			'}';
	}

	public boolean isIdle() {
		return this.state == CallState.IDLE;
	}

	public boolean isRinging() {
		return this.state == CallState.RINGING;
	}

	public boolean isInitializing() {
		return this.state == CallState.INITIALIZING;
	}

	public boolean isCalling() {
		return this.state == CallState.CALLING;
	}

	public boolean isDisconnecting() {
		return this.state == CallState.DISCONNECTING;
	}

	public long getCallId() {
		return this.callId;
	}

	@Deprecated
	public long getIncomingCallCounter() {
		return incomingCallCounter;
	}

	/**
	 * Get the state name.
	 */
	public @NonNull	String getName() {
		return CallState.getStateName(this.state);
	}
}

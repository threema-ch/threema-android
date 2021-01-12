/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2021 Threema GmbH
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import androidx.annotation.AnyThread;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

/**
 * The call state is a combination of the plain state and a call ID.
 *
 * The call state is global, there should not be multiple instances of this.
 *
 * This is a pure data holder, no validation is being done.
 *
 * This class is thread safe.
 */
@AnyThread
public class CallState {
	private static final Logger logger = LoggerFactory.getLogger(CallState.class);

	/**
	 * No call is currently active.
	 */
	static final int IDLE = 0;

	/**
	 * This state only happens on the callee side,
	 * before the call was accepted.
	 */
	static final int RINGING = 1;

	/**
	 * A call was accepted and is being setup.
	 */
	static final int INITIALIZING = 2;

	/**
	 * A call is currently ongoing.
	 */
	static final int CALLING = 3;

	/**
	 * A call is being disconnected.
	 */
	static final int DISCONNECTING = 4;

	@Retention(RetentionPolicy.SOURCE)
	@IntDef({IDLE, RINGING, INITIALIZING, CALLING, DISCONNECTING})
	@interface State {}

	private final AtomicInteger state = new AtomicInteger(IDLE);
	private final AtomicLong callId = new AtomicLong(0);

	/**
	 * The incoming call counter is a transitional variable that is used as long as the call ID has
	 * not yet been fully rolled out. It is being used to avoid problems if two calls are using the
	 * default call ID "0". The counter is only incremented for incoming calls (in setStateRinging).
	 */
	@Deprecated
	private final AtomicLong incomingCallCounter = new AtomicLong(0);

	@Override
	public synchronized String toString() {
		return "CallState{" +
			"state=" + getStateName(this.state.get()) +
			", callId=" + this.callId.get() +
			'}';
	}

	//region Check / get state

	public boolean isIdle() {
		return this.state.get() == IDLE;
	}

	public synchronized boolean isRinging() {
		return this.state.get() == RINGING;
	}

	public synchronized boolean isInitializing() {
		return this.state.get() == INITIALIZING;
	}

	public synchronized boolean isCalling() {
		return this.state.get() == CALLING;
	}

	public synchronized boolean isDisconnecting() {
		return this.state.get() == DISCONNECTING;
	}

	/**
	 * Return the current Call ID.
	 *
	 * Note: Depending on the use case you might want to use {@link #getStateSnapshot()} instead.
	 */
	public long getCallId() {
		return this.callId.get();
	}

	/**
	 * Return the incoming call counter.
	 */
	@Deprecated
	public long getIncomingCallCounter() {
		return this.incomingCallCounter.get();
	}

	/**
	 * Return an immutable snapshot of the current state.
	 * This allows reading the state and the Call ID independently without locking.
	 */
	public synchronized @NonNull CallStateSnapshot getStateSnapshot() {
		return new CallStateSnapshot(
			this.state.get(),
			this.callId.get(),
			this.incomingCallCounter.get()
		);
	}

	/**
	 * Return the state name for the specified state.
	 */
	static @NonNull String getStateName(@State int state) {
		switch (state) {
			case CallState.IDLE:
				return "IDLE";
			case CallState.RINGING:
				return "RINGING";
			case CallState.INITIALIZING:
				return "INITIALIZING";
			case CallState.CALLING:
				return "CALLING";
			case CallState.DISCONNECTING:
				return "DISCONNECTING";
			default:
				return "UNKNOWN";
		}
	}

	//endregion

	//region Set state

	public synchronized void setIdle() {
		this.state.set(IDLE);
		this.callId.set(0);
	}

	public synchronized void setRinging(long callId) {
		final @State int state = this.state.get();
		if (this.state.get() != IDLE) {
			logger.warn("Call state change from {} to RINGING", getStateName(state));
		}
		this.state.set(RINGING);

		if (this.callId.get() != 0) {
			logger.warn("Call ID changed from {} to {}", this.callId, callId);
		}
		this.callId.set(callId);

		this.incomingCallCounter.incrementAndGet();
	}

	public synchronized void setInitializing(long callId) {
		final @State int state = this.state.get();
		if (state != RINGING && state != IDLE) {
			logger.warn("Call state change from {} to INITIALIZING", getStateName(state));
		}
		this.state.set(INITIALIZING);

		final long oldCallId = this.callId.get();
		if (oldCallId != 0 && oldCallId != callId) {
			logger.warn("Call ID changed from {} to {}", oldCallId, callId);
		}
		this.callId.set(callId);
	}

	public synchronized void setCalling(long callId) {
		final @State int state = this.state.get();
		if (state != INITIALIZING) {
			logger.warn("Call state change from {} to CALLING", getStateName(state));
		}
		this.state.set(CALLING);

		final long oldCallId = this.callId.get();
		if (oldCallId != callId) {
			logger.warn("Call ID changed from {} to {}", oldCallId, callId);
		}
		this.callId.set(callId);
	}

	public synchronized void setDisconnecting(long callId) {
		final @State int state = this.state.get();
		if (state != INITIALIZING && state != CALLING) {
			logger.warn("Call state change from {} to DISCONNECTING", getStateName(state));
		}
		this.state.set(DISCONNECTING);

		final long oldCallId = this.callId.get();
		if (oldCallId != callId) {
			logger.warn("Call ID changed from {} to {}", oldCallId, callId);
		}
		this.callId.set(callId);
	}

	//endregion
}

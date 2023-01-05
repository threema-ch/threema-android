/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2023 Threema GmbH
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

package ch.threema.app.voip.groupcall

import ch.threema.app.voip.groupcall.sfu.CallId

class PeekFailedCounter {
	private val failedCounter: MutableMap<CallId, Int> = mutableMapOf()

	/**
	 * Get the current count of failed attempts and increment the counter _after_ reading the current value.
	 */
	fun getAndIncrementCounter(callId: CallId): Int {
		return synchronized(failedCounter) {
			val counter = failedCounter[callId] ?: 0
			failedCounter[callId] = counter + 1
			counter
		}
	}

	/**
	 * Reset the counter associated with a [CallId]
	 *
	 * @return 0 (the reset value)
	 */
	fun resetCounter(callId: CallId): Int {
		synchronized(failedCounter) {
			failedCounter.remove(callId)
		}
		return 0
	}
}

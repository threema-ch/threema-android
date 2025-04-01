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

package ch.threema.app.voip.groupcall.sfu.webrtc

import ch.threema.app.voip.groupcall.gcBlake2b
import ch.threema.app.voip.groupcall.getSecureRandomBytes
import ch.threema.domain.protocol.csp.ProtocolDefines.GC_PCMK_LENGTH

/**
 * State snapshot of a participant's media key (PCMK).
 *
 * All properties are to be considered immutable.
 */
data class ParticipantCallMediaKeyState(
    val epoch: UInt,
    val ratchetCounter: UInt,
    val pcmk: ByteArray,
) {
    override fun toString(): String {
        return "ParticipantCallMediaKeyState(epoch=${epoch}, ratchetCounter=${ratchetCounter})"
    }
}

/**
 * A participant's media key (PCMK) that will be applied soon.
 */
class PendingParticipantCallMediaKeyState(
    private val creator: LocalParticipantCallMediaKey,
    private var wasApplied: Boolean = false,
    internal var stale: Boolean = false,

    val state: ParticipantCallMediaKeyState,
) {
    fun applied() {
        assert(!wasApplied)
        wasApplied = true
        creator.applied(this)
    }
}

class LocalParticipantCallMediaKey {
    private var _current: ParticipantCallMediaKeyState =
        ParticipantCallMediaKeyState(0u, 0u, getSecureRandomBytes(GC_PCMK_LENGTH))
    private var _pending: PendingParticipantCallMediaKeyState? = null

    val current: ParticipantCallMediaKeyState
        get() = _current
    val pending: PendingParticipantCallMediaKeyState?
        get() = _pending

    fun all(): List<ParticipantCallMediaKeyState> {
        return pending.let { if (it === null) listOf(current) else listOf(current, it.state) }
    }

    fun nextEpoch(): PendingParticipantCallMediaKeyState {
        // If another successor state is pending to be applied, mark it as _stale_ and abort.
        _pending?.let {
            it.stale = true
            return it
        }

        // Increase the epoch. Note that it is allowed to wrap.
        return _current.let { currentKeyState ->
            val state = ParticipantCallMediaKeyState(
                epoch = if (currentKeyState.epoch == 255u) 0u else currentKeyState.epoch + 1u,
                ratchetCounter = 0u,
                pcmk = getSecureRandomBytes(GC_PCMK_LENGTH),
            )
            PendingParticipantCallMediaKeyState(
                state = state,
                creator = this,
            ).also {
                _pending = it
            }
        }
    }

    fun nextRatchetCounter(): ParticipantCallMediaKeyState {
        // Note: The ratchet will be applied to the current PCMK, even when a
        // successor state is pending. This is perfectly fine though.
        _current = _current.let {
            // Ensure the ratchet counter does not overflow
            // Note: This is considered unreachable
            if (it.ratchetCounter == 255u) {
                throw Error("Ratchet counter would overflow")
            }

            // Ratchet once
            //
            // PCMK' = BLAKE2b(key=PCMK, salt="m'", personal='3ma-call')
            val pcmk = gcBlake2b(GC_PCMK_LENGTH, it.pcmk, "m'")
            ParticipantCallMediaKeyState(
                epoch = it.epoch,
                ratchetCounter = it.ratchetCounter + 1u,
                pcmk,
            )
        }
        return _current
    }

    internal fun applied(pending: PendingParticipantCallMediaKeyState) {
        // When the sucessor PCMK has been applied, replace it
        assert(pending === this._pending)
        this._pending = null
        _current = pending.state
    }
}

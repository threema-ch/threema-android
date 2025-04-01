/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2024 Threema GmbH
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

package ch.threema.app.voip.listeners;

import androidx.annotation.AnyThread;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerData;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallHangupData;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallOfferData;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallRingingData;

public interface VoipMessageListener {
    @AnyThread
    default void onOffer(final String identity, final VoipCallOfferData data) {
    }

    @AnyThread
    default void onAnswer(final String identity, final VoipCallAnswerData data) {
    }

    @AnyThread
    default void onRinging(final String identity, final VoipCallRingingData data) {
    }

    @AnyThread
    default void onHangup(final String identity, final VoipCallHangupData data) {
    }

    /**
     * Return true if events for this identity should be handled.
     *
     * @param identity The Threema identity
     */
    @AnyThread
    boolean handle(final String identity);
}

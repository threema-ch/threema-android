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

package ch.threema.app.voip.groupcall.sfu.webrtc

import ch.threema.app.voip.groupcall.sfu.MediaKind
import ch.threema.app.voip.groupcall.sfu.ParticipantId
import org.webrtc.RtpTransceiver

typealias Transceivers = MutableMap<MediaKind, RtpTransceiver>

internal class TransceiversCtx(
    var local: Transceivers?,
    var remote: MutableMap<ParticipantId, Transceivers>,
) {
    // Note: We do not need a 'teardown' routine in here since all transceivers will be disposed
    //       by its associated peer connection.
}

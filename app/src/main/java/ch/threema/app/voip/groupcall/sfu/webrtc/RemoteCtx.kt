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

package ch.threema.app.voip.groupcall.sfu.webrtc

import androidx.annotation.UiThread
import ch.threema.app.voip.groupcall.sfu.MediaKind
import ch.threema.app.webrtc.RemoteAudioContext
import ch.threema.app.webrtc.RemoteVideoContext

@UiThread
class RemoteCtx private constructor(
    val microphoneAudioContext: RemoteAudioContext,
    val cameraVideoContext: RemoteVideoContext,
    // Note: This is the place to add a screenshare context when desired
) {
    companion object {
        internal fun fromTransceiverMap(transceivers: Transceivers): RemoteCtx {
            val microphoneAudio = checkNotNull(transceivers[MediaKind.AUDIO]) {
                "Expected remote audio transceiver to be set"
            }
            val cameraVideo = checkNotNull(transceivers[MediaKind.VIDEO]) {
                "Expected remote video transceiver to be set"
            }
            return RemoteCtx(
                microphoneAudioContext = RemoteAudioContext.create(microphoneAudio),
                cameraVideoContext = RemoteVideoContext.create(cameraVideo),
            )
        }
    }
}

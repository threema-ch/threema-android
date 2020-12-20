/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2020 Threema GmbH
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

package ch.threema.app.webclient.utils;

import androidx.annotation.AnyThread;

import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.RtpReceiver;

/**
 * A PeerConnection.Observer that by default does not do anything (default methods everywhere).
 */
@AnyThread
public interface DefaultNoopPeerConnectionObserver extends PeerConnection.Observer {
	@Override
	default void onSignalingChange(PeerConnection.SignalingState signalingState) {}

	@Override
	default void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {}

	@Override
	default void onIceConnectionReceivingChange(boolean b) {}

	@Override
	default void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {}

	@Override
	default void onIceCandidate(IceCandidate iceCandidate) {}

	@Override
	default void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {}

	@Override
	default void onAddStream(MediaStream mediaStream) {}

	@Override
	default void onRemoveStream(MediaStream mediaStream) {}

	@Override
	default void onDataChannel(DataChannel dataChannel) {}

	@Override
	default void onRenegotiationNeeded() {}

	@Override
	default void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {}
}

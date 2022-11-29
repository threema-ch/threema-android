/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2022 Threema GmbH
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

import android.util.Log;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;
import ch.threema.app.voip.util.SdpPatcher;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

/**
 * Ensure the Call SDP does not contain any "funny" easter eggs such as silly header extensions
 * that are not encrypted and contain sensitive information.
 *
 * This may need updating from time to time, so if it breaks, you will have to do some
 * research on what changed and why.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class SdpTest {
	final private static String TAG = "SdpTest";
	final private static long CALL_ID = 123;

	private PeerConnectionClient.PeerConnectionParameters getParameters(boolean videoEnabled) {
		// Return sane default parameters used for calls
		return new PeerConnectionClient.PeerConnectionParameters(
			false,
			false, false, false, false, false,
			videoEnabled, videoEnabled, true, true,
			videoEnabled
				? SdpPatcher.RtpHeaderExtensionConfig.ENABLE_WITH_ONE_AND_TWO_BYTE_HEADER
				: SdpPatcher.RtpHeaderExtensionConfig.DISABLE,
			false, true, true
		);
	}

	abstract class PeerConnectionClientEvents implements PeerConnectionClient.Events {
		@NonNull public final Semaphore done;
		@Nullable
		public SessionDescription localSdp = null;

		public PeerConnectionClientEvents() throws InterruptedException {
			this.done = new Semaphore(1);
			this.done.acquire();
		}

		@Override
		public void onLocalDescription(long callId, SessionDescription sdp) {
			Log.d(TAG,"onLocalDescription");
			this.localSdp = sdp;
		}

		@Override
		public void onRemoteDescriptionSet(long callId) {
			Log.d(TAG,"onRemoteDescriptionSet");
		}

		@Override
		public void onIceCandidate(long callId, IceCandidate candidate) {
			Log.d(TAG,"onIceCandidate");
		}

		@Override
		public void onTransportConnecting(long callId) {
			Log.d(TAG,"onTransportConnecting");
		}

		@Override
		public void onTransportConnected(long callId) {
			Log.d(TAG,"onTransportConnected");
		}

		@Override
		public void onTransportDisconnected(long callId) {
			Log.d(TAG,"onTransportDisconnected");
		}

		@Override
		public void onTransportFailed(long callId) {
			Log.d(TAG,"onTransportFailed");
		}

		@Override
		public void onIceGatheringStateChange(long callId, PeerConnection.IceGatheringState newState) {
			Log.d(TAG,"onIceGatheringStateChange");
		}

		@Override
		public void onPeerConnectionClosed(long callId) {
			Log.d(TAG,"onPeerConnectionClosed");
			this.done.release();
		}

		@Override
		public void onError(long callId, @NonNull String description, boolean abortCall) {
			Log.d(TAG, String.format("onError: %s (abortCall: %s)", description, abortCall));
		}
	}

	@Nullable
	private SessionDescription generateFakeOffer(boolean videoEnabled) {
		if (videoEnabled) {
			return new SessionDescription(SessionDescription.Type.OFFER, "" +
				"v=0\r\n" +
				"o=- 72507000979779968 2 IN IP4 127.0.0.1\r\n" +
				"s=-\r\n" +
				"t=0 0\r\n" +
				"a=group:BUNDLE 0 1 2\r\n" +
				"a=extmap-allow-mixed\r\n" +
				"a=msid-semantic: WMS 3MACALL\r\n" +
				"m=audio 9 UDP/TLS/RTP/SAVPF 111 103 104 9 102 0 8 106 105 13 110 112 113 126\r\n" +
				"c=IN IP4 0.0.0.0\r\n" +
				"a=rtcp:9 IN IP4 0.0.0.0\r\n" +
				"a=ice-ufrag:f30j\r\n" +
				"a=ice-pwd:G9GzFLlk1gthsg9uVhI3OyGv\r\n" +
				"a=ice-options:trickle renomination\r\n" +
				"a=fingerprint:sha-256 AE:86:73:4B:8A:55:BE:F1:2F:A2:8E:AA:98:8D:42:A4:D6:F8:2D:1C:CC:CD:12:C5:8E:14:BD:34:62:DA:35:8E\r\n" +
				"a=setup:actpass\r\n" +
				"a=mid:0\r\n" +
				"a=extmap:10 urn:ietf:params:rtp-hdrext:encrypt urn:ietf:params:rtp-hdrext:ssrc-audio-level\r\n" +
				"a=extmap:1 urn:ietf:params:rtp-hdrext:ssrc-audio-level\r\n" +
				"a=extmap:2 http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time\r\n" +
				"a=extmap:16 urn:ietf:params:rtp-hdrext:encrypt http://www.ietf.org/id/draft-holmer-rmcat-transport-wide-cc-extensions-01\r\n" +
				"a=extmap:3 http://www.ietf.org/id/draft-holmer-rmcat-transport-wide-cc-extensions-01\r\n" +
				"a=extmap:4 urn:ietf:params:rtp-hdrext:sdes:mid\r\n" +
				"a=extmap:5 urn:ietf:params:rtp-hdrext:sdes:rtp-stream-id\r\n" +
				"a=extmap:6 urn:ietf:params:rtp-hdrext:sdes:repaired-rtp-stream-id\r\n" +
				"a=extmap:15 urn:ietf:params:rtp-hdrext:encrypt http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time\r\n" +
				"a=extmap:17 urn:ietf:params:rtp-hdrext:encrypt urn:ietf:params:rtp-hdrext:sdes:mid\r\n" +
				"a=extmap:18 urn:ietf:params:rtp-hdrext:encrypt urn:ietf:params:rtp-hdrext:sdes:rtp-stream-id\r\n" +
				"a=extmap:19 urn:ietf:params:rtp-hdrext:encrypt urn:ietf:params:rtp-hdrext:sdes:repaired-rtp-stream-id\r\n" +
				"a=sendrecv\r\n" +
				"a=msid:3MACALL 3MACALLa0\r\n" +
				"a=rtcp-mux\r\n" +
				"a=rtpmap:111 opus/48000/2\r\n" +
				"a=rtcp-fb:111 transport-cc\r\n" +
				"a=fmtp:111 minptime=10;useinbandfec=1\r\n" +
				"a=rtpmap:103 ISAC/16000\r\n" +
				"a=rtpmap:104 ISAC/32000\r\n" +
				"a=rtpmap:9 G722/8000\r\n" +
				"a=rtpmap:102 ILBC/8000\r\n" +
				"a=rtpmap:0 PCMU/8000\r\n" +
				"a=rtpmap:8 PCMA/8000\r\n" +
				"a=rtpmap:106 CN/32000\r\n" +
				"a=rtpmap:105 CN/16000\r\n" +
				"a=rtpmap:13 CN/8000\r\n" +
				"a=rtpmap:110 telephone-event/48000\r\n" +
				"a=rtpmap:112 telephone-event/32000\r\n" +
				"a=rtpmap:113 telephone-event/16000\r\n" +
				"a=rtpmap:126 telephone-event/8000\r\n" +
				"a=ssrc:3148626149 cname:xmp2nT2LrKeffKAn\r\n" +
				"a=ssrc:3148626149 msid:3MACALL 3MACALLa0\r\n" +
				"a=ssrc:3148626149 mslabel:3MACALL\r\n" +
				"a=ssrc:3148626149 label:3MACALLa0\r\n" +
				"m=video 9 UDP/TLS/RTP/SAVPF 96 97 98 99 100 101 127 123 125\r\n" +
				"c=IN IP4 0.0.0.0\r\n" +
				"a=rtcp:9 IN IP4 0.0.0.0\r\n" +
				"a=ice-ufrag:f30j\r\n" +
				"a=ice-pwd:G9GzFLlk1gthsg9uVhI3OyGv\r\n" +
				"a=ice-options:trickle renomination\r\n" +
				"a=fingerprint:sha-256 AE:86:73:4B:8A:55:BE:F1:2F:A2:8E:AA:98:8D:42:A4:D6:F8:2D:1C:CC:CD:12:C5:8E:14:BD:34:62:DA:35:8E\r\n" +
				"a=setup:actpass\r\n" +
				"a=mid:1\r\n" +
				"a=extmap:25 urn:ietf:params:rtp-hdrext:encrypt http://tools.ietf.org/html/draft-ietf-avtext-framemarking-07\r\n" +
				"a=extmap:26 urn:ietf:params:rtp-hdrext:encrypt http://www.webrtc.org/experiments/rtp-hdrext/color-space\r\n" +
				"a=extmap:14 urn:ietf:params:rtp-hdrext:toffset\r\n" +
				"a=extmap:2 http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time\r\n" +
				"a=extmap:13 urn:3gpp:video-orientation\r\n" +
				"a=extmap:3 http://www.ietf.org/id/draft-holmer-rmcat-transport-wide-cc-extensions-01\r\n" +
				"a=extmap:12 http://www.webrtc.org/experiments/rtp-hdrext/playout-delay\r\n" +
				"a=extmap:7 http://www.webrtc.org/experiments/rtp-hdrext/video-timing\r\n" +
				"a=extmap:17 urn:ietf:params:rtp-hdrext:encrypt urn:ietf:params:rtp-hdrext:sdes:mid\r\n" +
				"a=extmap:8 http://tools.ietf.org/html/draft-ietf-avtext-framemarking-07\r\n" +
				"a=extmap:9 http://www.webrtc.org/experiments/rtp-hdrext/color-space\r\n" +
				"a=extmap:4 urn:ietf:params:rtp-hdrext:sdes:mid\r\n" +
				"a=extmap:5 urn:ietf:params:rtp-hdrext:sdes:rtp-stream-id\r\n" +
				"a=extmap:6 urn:ietf:params:rtp-hdrext:sdes:repaired-rtp-stream-id\r\n" +
				"a=extmap:20 urn:ietf:params:rtp-hdrext:encrypt urn:ietf:params:rtp-hdrext:toffset\r\n" +
				"a=extmap:15 urn:ietf:params:rtp-hdrext:encrypt http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time\r\n" +
				"a=extmap:21 urn:ietf:params:rtp-hdrext:encrypt urn:3gpp:video-orientation\r\n" +
				"a=extmap:16 urn:ietf:params:rtp-hdrext:encrypt http://www.ietf.org/id/draft-holmer-rmcat-transport-wide-cc-extensions-01\r\n" +
				"a=extmap:22 urn:ietf:params:rtp-hdrext:encrypt http://www.webrtc.org/experiments/rtp-hdrext/playout-delay\r\n" +
				"a=extmap:23 urn:ietf:params:rtp-hdrext:encrypt http://www.webrtc.org/experiments/rtp-hdrext/video-content-type\r\n" +
				"a=extmap:24 urn:ietf:params:rtp-hdrext:encrypt http://www.webrtc.org/experiments/rtp-hdrext/video-timing\r\n" +
				"a=extmap:11 http://www.webrtc.org/experiments/rtp-hdrext/video-content-type\r\n" +
				"a=extmap:18 urn:ietf:params:rtp-hdrext:encrypt urn:ietf:params:rtp-hdrext:sdes:rtp-stream-id\r\n" +
				"a=extmap:19 urn:ietf:params:rtp-hdrext:encrypt urn:ietf:params:rtp-hdrext:sdes:repaired-rtp-stream-id\r\n" +
				"a=sendrecv\r\n" +
				"a=msid:3MACALL 3MACALLv0\r\n" +
				"a=rtcp-mux\r\n" +
				"a=rtcp-rsize\r\n" +
				"a=rtpmap:96 VP8/90000\r\n" +
				"a=rtcp-fb:96 goog-remb\r\n" +
				"a=rtcp-fb:96 transport-cc\r\n" +
				"a=rtcp-fb:96 ccm fir\r\n" +
				"a=rtcp-fb:96 nack\r\n" +
				"a=rtcp-fb:96 nack pli\r\n" +
				"a=rtpmap:97 rtx/90000\r\n" +
				"a=fmtp:97 apt=96\r\n" +
				"a=rtpmap:98 VP9/90000\r\n" +
				"a=rtcp-fb:98 goog-remb\r\n" +
				"a=rtcp-fb:98 transport-cc\r\n" +
				"a=rtcp-fb:98 ccm fir\r\n" +
				"a=rtcp-fb:98 nack\r\n" +
				"a=rtcp-fb:98 nack pli\r\n" +
				"a=rtpmap:99 rtx/90000\r\n" +
				"a=fmtp:99 apt=98\r\n" +
				"a=rtpmap:100 H264/90000\r\n" +
				"a=rtcp-fb:100 goog-remb\r\n" +
				"a=rtcp-fb:100 transport-cc\r\n" +
				"a=rtcp-fb:100 ccm fir\r\n" +
				"a=rtcp-fb:100 nack\r\n" +
				"a=rtcp-fb:100 nack pli\r\n" +
				"a=fmtp:100 level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42e01f\r\n" +
				"a=rtpmap:101 rtx/90000\r\n" +
				"a=fmtp:101 apt=100\r\n" +
				"a=rtpmap:127 red/90000\r\n" +
				"a=rtpmap:123 rtx/90000\r\n" +
				"a=fmtp:123 apt=127\r\n" +
				"a=rtpmap:125 ulpfec/90000\r\n" +
				"a=ssrc-group:FID 2961420724 927121398\r\n" +
				"a=ssrc:2961420724 cname:xmp2nT2LrKeffKAn\r\n" +
				"a=ssrc:2961420724 msid:3MACALL 3MACALLv0\r\n" +
				"a=ssrc:2961420724 mslabel:3MACALL\r\n" +
				"a=ssrc:2961420724 label:3MACALLv0\r\n" +
				"a=ssrc:927121398 cname:xmp2nT2LrKeffKAn\r\n" +
				"a=ssrc:927121398 msid:3MACALL 3MACALLv0\r\n" +
				"a=ssrc:927121398 mslabel:3MACALL\r\n" +
				"a=ssrc:927121398 label:3MACALLv0\r\n" +
				"m=application 9 UDP/DTLS/SCTP webrtc-datachannel\r\n" +
				"c=IN IP4 0.0.0.0\r\n" +
				"a=ice-ufrag:f30j\r\n" +
				"a=ice-pwd:G9GzFLlk1gthsg9uVhI3OyGv\r\n" +
				"a=ice-options:trickle renomination\r\n" +
				"a=fingerprint:sha-256 AE:86:73:4B:8A:55:BE:F1:2F:A2:8E:AA:98:8D:42:A4:D6:F8:2D:1C:CC:CD:12:C5:8E:14:BD:34:62:DA:35:8E\r\n" +
				"a=setup:actpass\r\n" +
				"a=mid:2\r\n" +
				"a=sctp-port:5000\r\n" +
				"a=max-message-size:262144\r\n"
			);
		} else {
			return new SessionDescription(SessionDescription.Type.OFFER, "" +
				"v=0\r\n" +
				"o=- 8329341859617817285 2 IN IP4 127.0.0.1\r\n" +
				"s=-\r\n" +
				"t=0 0\r\n" +
				"a=group:BUNDLE 0\r\n" +
				"a=extmap-allow-mixed\r\n" +
				"a=msid-semantic: WMS 3MACALL\r\n" +
				"m=audio 9 UDP/TLS/RTP/SAVPF 111 103 9 102 0 8 105 13 110 113 126\r\n" +
				"c=IN IP4 0.0.0.0\r\n" +
				"a=rtcp:9 IN IP4 0.0.0.0\r\n" +
				"a=ice-ufrag:hFGR\r\n" +
				"a=ice-pwd:HPszOFM6RDZWdhZ3PpPQ7w1H\r\n" +
				"a=ice-options:renomination\r\n" +
				"a=fingerprint:sha-256 F7:3A:7C:0C:A0:1E:EA:C5:2E:33:ED:90:61:55:0E:DF:59:8E:EA:EF:A6:E3:01:6E:A5:9E:34:78:5E:E3:8E:44\r\n" +
				"a=setup:actpass\r\n" +
				"a=mid:0\r\n" +
				"a=extmap:10 urn:ietf:params:rtp-hdrext:encrypt urn:ietf:params:rtp-hdrext:ssrc-audio-level\r\n" +
				"a=extmap:1 urn:ietf:params:rtp-hdrext:ssrc-audio-level\r\n" +
				"a=extmap:2 http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time\r\n" +
				"a=extmap:16 urn:ietf:params:rtp-hdrext:encrypt http://www.ietf.org/id/draft-holmer-rmcat-transport-wide-cc-extensions-01\r\n" +
				"a=extmap:3 http://www.ietf.org/id/draft-holmer-rmcat-transport-wide-cc-extensions-01\r\n" +
				"a=extmap:4 urn:ietf:params:rtp-hdrext:sdes:mid\r\n" +
				"a=extmap:5 urn:ietf:params:rtp-hdrext:sdes:rtp-stream-id\r\n" +
				"a=extmap:6 urn:ietf:params:rtp-hdrext:sdes:repaired-rtp-stream-id\r\n" +
				"a=extmap:15 urn:ietf:params:rtp-hdrext:encrypt http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time\r\n" +
				"a=extmap:17 urn:ietf:params:rtp-hdrext:encrypt urn:ietf:params:rtp-hdrext:sdes:mid\r\n" +
				"a=extmap:18 urn:ietf:params:rtp-hdrext:encrypt urn:ietf:params:rtp-hdrext:sdes:rtp-stream-id\r\n" +
				"a=extmap:19 urn:ietf:params:rtp-hdrext:encrypt urn:ietf:params:rtp-hdrext:sdes:repaired-rtp-stream-id\r\n" +
				"a=sendrecv\r\n" +
				"a=msid:3MACALL 3MACALLa0\r\n" +
				"a=rtcp-mux\r\n" +
				"a=rtpmap:111 opus/48000/2\r\n" +
				"a=rtcp-fb:111 transport-cc\r\n" +
				"a=fmtp:111 minptime=10;useinbandfec=1\r\n" +
				"a=rtpmap:103 ISAC/16000\r\n" +
				"a=rtpmap:9 G722/8000\r\n" +
				"a=rtpmap:102 ILBC/8000\r\n" +
				"a=rtpmap:0 PCMU/8000\r\n" +
				"a=rtpmap:8 PCMA/8000\r\n" +
				"a=rtpmap:105 CN/16000\r\n" +
				"a=rtpmap:13 CN/8000\r\n" +
				"a=rtpmap:110 telephone-event/48000\r\n" +
				"a=rtpmap:113 telephone-event/16000\r\n" +
				"a=rtpmap:126 telephone-event/8000\r\n" +
				"a=ssrc:2080079676 cname:Jb5aR24iJnFDp6OS\r\n" +
				"a=ssrc:2080079676 msid:3MACALL 3MACALLa0\r\n" +
				"a=ssrc:2080079676 mslabel:3MACALL\r\n" +
				"a=ssrc:2080079676 label:3MACALLa0\r\n"
			);
		}
	}

	private void validateDescription(@NonNull SessionDescription sdp, boolean videoEnabled, boolean isOffer) {
		// Part 1: Header, audio, first part of video (without rtpmap)
		final List<String> expectedMatchesPart1 = new ArrayList<>();
		expectedMatchesPart1.add("^v=0$");
		expectedMatchesPart1.add("^o=- \\d+ \\d IN IP4 127.0.0.1$");
		expectedMatchesPart1.add("^s=-$");
		expectedMatchesPart1.add("^t=0 0$");
		expectedMatchesPart1.add("^a=group:BUNDLE( \\d+)+");
		if (videoEnabled) {
			expectedMatchesPart1.add("^a=extmap-allow-mixed$");
		}
		expectedMatchesPart1.add("^a=msid-semantic: WMS 3MACALL$");
		expectedMatchesPart1.add("^m=audio 9 UDP/TLS/RTP/SAVPF \\d+$");
		expectedMatchesPart1.add("^c=IN IP4 0.0.0.0$");
		expectedMatchesPart1.add("^a=rtcp:9 IN IP4 0.0.0.0$");
		expectedMatchesPart1.add("^a=ice-ufrag:[^ ]+$");
		expectedMatchesPart1.add("^a=ice-pwd:[^ ]+$");
		expectedMatchesPart1.add("^a=ice-options:trickle renomination$");
		expectedMatchesPart1.add("^a=fingerprint:sha-256 [^ ]+$");
		expectedMatchesPart1.add("^a=setup:(actpass|active)");
		expectedMatchesPart1.add("^a=mid:0");
		if (videoEnabled) {
			if (isOffer) {
				expectedMatchesPart1.add("^a=extmap:[0-9]+ urn:ietf:params:rtp-hdrext:encrypt http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time$");
				expectedMatchesPart1.add("^a=extmap:[0-9]+ urn:ietf:params:rtp-hdrext:encrypt http://www.ietf.org/id/draft-holmer-rmcat-transport-wide-cc-extensions-01$");
				expectedMatchesPart1.add("^a=extmap:[0-9]+ urn:ietf:params:rtp-hdrext:encrypt urn:ietf:params:rtp-hdrext:sdes:mid$");
			} else {
				expectedMatchesPart1.add("^a=extmap:15 urn:ietf:params:rtp-hdrext:encrypt http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time$");
				expectedMatchesPart1.add("^a=extmap:16 urn:ietf:params:rtp-hdrext:encrypt http://www.ietf.org/id/draft-holmer-rmcat-transport-wide-cc-extensions-01$");
				expectedMatchesPart1.add("^a=extmap:17 urn:ietf:params:rtp-hdrext:encrypt urn:ietf:params:rtp-hdrext:sdes:mid$");
			}
		}
		expectedMatchesPart1.add("^a=sendrecv$");
		expectedMatchesPart1.add("^a=msid:3MACALL 3MACALLa0");
		expectedMatchesPart1.add("^a=rtcp-mux$");
		expectedMatchesPart1.add("^a=rtpmap:\\d+ opus/48000/2$");
		expectedMatchesPart1.add("^a=rtcp-fb:\\d+ transport-cc$");
		expectedMatchesPart1.add("^a=fmtp:\\d+ minptime=10;useinbandfec=1;stereo=0;sprop-stereo=0;cbr=1$");
		expectedMatchesPart1.add("^a=ssrc:\\d+ cname:[^ ]+$");
		if (isOffer) {
			expectedMatchesPart1.add("^a=ssrc:\\d+ msid:3MACALL 3MACALLa0$");
		}
		if (videoEnabled) {
			expectedMatchesPart1.add("^m=video 9 UDP/TLS/RTP/SAVPF( \\d+)+$");
			expectedMatchesPart1.add("^c=IN IP4 0.0.0.0$");
			expectedMatchesPart1.add("^a=rtcp:9 IN IP4 0.0.0.0$");
			expectedMatchesPart1.add("^a=ice-ufrag:[^ ]+$");
			expectedMatchesPart1.add("^a=ice-pwd:[^ ]+$");
			expectedMatchesPart1.add("^a=ice-options:trickle renomination$");
			expectedMatchesPart1.add("^a=fingerprint:sha-256 [^ ]+$");
			expectedMatchesPart1.add("^a=setup:(actpass|active)");
			expectedMatchesPart1.add("^a=mid:1$");
			if (isOffer) {
				expectedMatchesPart1.add("^a=extmap:[0-9]+ urn:ietf:params:rtp-hdrext:encrypt urn:ietf:params:rtp-hdrext:toffset$");
				expectedMatchesPart1.add("^a=extmap:[0-9]+ urn:ietf:params:rtp-hdrext:encrypt http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time$");
				expectedMatchesPart1.add("^a=extmap:[0-9]+ urn:ietf:params:rtp-hdrext:encrypt urn:3gpp:video-orientation$");
				expectedMatchesPart1.add("^a=extmap:[0-9]+ urn:ietf:params:rtp-hdrext:encrypt http://www.ietf.org/id/draft-holmer-rmcat-transport-wide-cc-extensions-01$");
				expectedMatchesPart1.add("^a=extmap:[0-9]+ urn:ietf:params:rtp-hdrext:encrypt http://www.webrtc.org/experiments/rtp-hdrext/playout-delay$");
				expectedMatchesPart1.add("^a=extmap:[0-9]+ urn:ietf:params:rtp-hdrext:encrypt http://www.webrtc.org/experiments/rtp-hdrext/video-content-type$");
				expectedMatchesPart1.add("^a=extmap:[0-9]+ urn:ietf:params:rtp-hdrext:encrypt http://www.webrtc.org/experiments/rtp-hdrext/video-timing$");
				expectedMatchesPart1.add("^a=extmap:[0-9]+ urn:ietf:params:rtp-hdrext:encrypt http://www.webrtc.org/experiments/rtp-hdrext/color-space$");
				expectedMatchesPart1.add("^a=extmap:[0-9]+ urn:ietf:params:rtp-hdrext:encrypt urn:ietf:params:rtp-hdrext:sdes:mid$");
				expectedMatchesPart1.add("^a=extmap:[0-9]+ urn:ietf:params:rtp-hdrext:encrypt urn:ietf:params:rtp-hdrext:sdes:rtp-stream-id$");
				expectedMatchesPart1.add("^a=extmap:[0-9]+ urn:ietf:params:rtp-hdrext:encrypt urn:ietf:params:rtp-hdrext:sdes:repaired-rtp-stream-id$");
			} else {
				expectedMatchesPart1.add("^a=extmap:20 urn:ietf:params:rtp-hdrext:encrypt urn:ietf:params:rtp-hdrext:toffset$");
				expectedMatchesPart1.add("^a=extmap:15 urn:ietf:params:rtp-hdrext:encrypt http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time$");
				expectedMatchesPart1.add("^a=extmap:21 urn:ietf:params:rtp-hdrext:encrypt urn:3gpp:video-orientation$");
				expectedMatchesPart1.add("^a=extmap:16 urn:ietf:params:rtp-hdrext:encrypt http://www.ietf.org/id/draft-holmer-rmcat-transport-wide-cc-extensions-01$");
				expectedMatchesPart1.add("^a=extmap:22 urn:ietf:params:rtp-hdrext:encrypt http://www.webrtc.org/experiments/rtp-hdrext/playout-delay$");
				expectedMatchesPart1.add("^a=extmap:23 urn:ietf:params:rtp-hdrext:encrypt http://www.webrtc.org/experiments/rtp-hdrext/video-content-type$");
				expectedMatchesPart1.add("^a=extmap:24 urn:ietf:params:rtp-hdrext:encrypt http://www.webrtc.org/experiments/rtp-hdrext/video-timing$");
				expectedMatchesPart1.add("^a=extmap:26 urn:ietf:params:rtp-hdrext:encrypt http://www.webrtc.org/experiments/rtp-hdrext/color-space$");
				expectedMatchesPart1.add("^a=extmap:17 urn:ietf:params:rtp-hdrext:encrypt urn:ietf:params:rtp-hdrext:sdes:mid$");
				expectedMatchesPart1.add("^a=extmap:18 urn:ietf:params:rtp-hdrext:encrypt urn:ietf:params:rtp-hdrext:sdes:rtp-stream-id$");
				expectedMatchesPart1.add("^a=extmap:19 urn:ietf:params:rtp-hdrext:encrypt urn:ietf:params:rtp-hdrext:sdes:repaired-rtp-stream-id$");
			}
			// TODO(SE-63): Ehh, dirty hack... it should create a transceiver instead
			expectedMatchesPart1.add("^a=recvonly");
//			expectedMatchesPart1.add("^a=sendrecv");
//			expectedMatchesPart1.add("^a=msid:3MACALL 3MACALLv0");
			expectedMatchesPart1.add("^a=rtcp-mux$");
			expectedMatchesPart1.add("^a=rtcp-rsize$");
		}

		// Part 2: Data channel
		final List<String> expectedMatchesPart2 = new ArrayList<>();
		if (isOffer || videoEnabled) {
			expectedMatchesPart2.add("^m=application 9 UDP/DTLS/SCTP webrtc-datachannel$");
			expectedMatchesPart2.add("^c=IN IP4 0.0.0.0$");
			expectedMatchesPart2.add("^a=ice-ufrag:[^ ]+$");
			expectedMatchesPart2.add("^a=ice-pwd:[^ ]+$");
			expectedMatchesPart2.add("^a=ice-options:trickle renomination$");
			expectedMatchesPart2.add("^a=fingerprint:sha-256 [^ ]+$");
			expectedMatchesPart2.add("^a=setup:(actpass|active)$");
			expectedMatchesPart2.add("^a=mid:[^ ]+$");
			expectedMatchesPart2.add("^a=sctp-port:5000$");
			expectedMatchesPart2.add("^a=max-message-size:262144$");
		}

		final List<String> actualLines = Arrays.asList(sdp.description.split("\r\n"));
		Log.d(TAG, "SDP:\n" + sdp.description);

		// Verify part 1
		int offset = 0;
		matchEachLine(expectedMatchesPart1, actualLines, offset);
		offset += expectedMatchesPart1.size();

		// Skip codec lines
		if (videoEnabled) {
			do {
				Log.d(TAG, "Skipping line \"" + actualLines.get(offset) + "\"");
				offset += 1;
			} while (isRtpmapLine(actualLines.get(offset)));
		}

		// Verify part 2
		matchEachLine(expectedMatchesPart2, actualLines, offset);
		offset += expectedMatchesPart2.size();

		// Lines must be equal
		assertEquals(offset, actualLines.size());
	}

	/**
	 * Helper for validateDescription
	 */
	private boolean isRtpmapLine(String line) {
		return line.startsWith("a=rtpmap:")
			|| line.startsWith("a=rtcp-fb:")
			|| line.startsWith("a=fmtp:");
	}

	/**
	 * Helper for validateDescription
	 */
	private void matchEachLine(List<String> expectedMatches, List<String> actualLines, int offset) {
		for (int i = 0; i < expectedMatches.size(); ++i) {
			final String expected = expectedMatches.get(i);
			final String actual = i < actualLines.size() ? actualLines.get(i + offset) : null;
			Log.d(TAG, "Validating \"" + actual + "\" against \"" + expected + "\"");
			assertNotNull(actual);
			assertTrue("Line \"" + actual + "\" did not match \"" + expected + "\"", actual.matches(expected));
		}
	}

	public void testOffer(boolean videoEnabled) throws InterruptedException, ExecutionException {
		final PeerConnectionClient pc = new PeerConnectionClient(
			ApplicationProvider.getApplicationContext(),
			this.getParameters(videoEnabled),
			null,
			CALL_ID
		);
		pc.setEnableIceServers(false);

		final PeerConnectionClientEvents events = new PeerConnectionClientEvents() {
			@Override
			public void onLocalDescription(long callId, SessionDescription sdp) {
				super.onLocalDescription(callId, sdp);
				pc.close();
			}
		};
		pc.setEventHandler(events);

		// Create peer connection & offer
		final boolean factoryCreateSuccess = pc.createPeerConnectionFactory().get();
		assertTrue(factoryCreateSuccess);
		pc.createPeerConnection();
		pc.createOffer();

		// Wait until local description (offer) available
		assertTrue(events.done.tryAcquire(10, TimeUnit.SECONDS));

		// Compare SDP
		assertNotNull(events.localSdp);
		assertEquals(events.localSdp.type, SessionDescription.Type.OFFER);
		this.validateDescription(events.localSdp, videoEnabled, true);
	}

	@Test
	public void testOfferAudioOnly() throws InterruptedException, ExecutionException {
		this.testOffer(false);
	}

	@Test
	public void testOfferVideo() throws InterruptedException, ExecutionException {
		this.testOffer(true);
	}


	private void testAnswer(boolean videoEnabled) throws InterruptedException, ExecutionException {
		final PeerConnectionClient pc = new PeerConnectionClient(
			ApplicationProvider.getApplicationContext(),
			this.getParameters(videoEnabled),
			null,
			1
		);
		pc.setEnableIceServers(false);

		final PeerConnectionClientEvents events = new PeerConnectionClientEvents() {
			@Override
			public void onLocalDescription(long callId, SessionDescription sdp) {
				super.onLocalDescription(callId, sdp);
				pc.close();
			}

			@Override
			public void onRemoteDescriptionSet(long callId) {
				pc.createAnswer();
			}
		};
		pc.setEventHandler(events);

		// Create factory
		final boolean factoryCreateSuccess = pc.createPeerConnectionFactory().get();
		assertTrue(factoryCreateSuccess);

		// Create fake offer
		final SessionDescription fakeOffer = this.generateFakeOffer(videoEnabled);

		// Create peer connection & set fake offer
		pc.createPeerConnection();
		pc.setRemoteDescription(fakeOffer);

		// Wait until local description (answer) available
		assertTrue(events.done.tryAcquire(10, TimeUnit.SECONDS));

		// Compare SDP
		assertNotNull(events.localSdp);
		assertEquals(events.localSdp.type, SessionDescription.Type.ANSWER);
		this.validateDescription(events.localSdp, videoEnabled, false);
	}

	@Test
	public void testAnswerAudioOnly() throws InterruptedException, ExecutionException {
		this.testAnswer(false);
	}

	@Test
	public void testAnswerVideo() throws InterruptedException, ExecutionException {
		this.testAnswer(true);
	}
}

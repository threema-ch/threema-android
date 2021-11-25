/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2021 Threema GmbH
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

package ch.threema.app.voip.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SdpUtilTest {
	@Test
	public void testIsLoopbackCandidate() {
		assertTrue(SdpUtil.isLoopbackCandidate("candidate:1510613869 1 UDP 2122063615 127.0.0.1 55682 typ host"));
		assertFalse(SdpUtil.isLoopbackCandidate("candidate:1510613869 1 UDP 2122063615 127x0x0x1 55682 typ host"));
		assertTrue(SdpUtil.isLoopbackCandidate("candidate:344579997 1 TCP 1518083839 127.0.0.1 39778 typ host tcptype passive"));
		assertTrue(SdpUtil.isLoopbackCandidate("candidate:559267639 1 UDP 2122136831 ::1 41989 typ host"));
		assertTrue(SdpUtil.isLoopbackCandidate("candidate:1876313031 1 TCP 1518157055 ::1 51083 typ host tcptype passive"));
		assertFalse(SdpUtil.isLoopbackCandidate("candidate:3660013861 1 TCP 1518283007 2a02:200:2c00:2a52:152c:1fd5:46fc:9174 9 typ host tcptype active"));
		assertFalse(SdpUtil.isLoopbackCandidate("candidate:3102198902 1 TCP 1518214911 192.168.12.155 9 typ host tcptype active"));
		assertFalse(SdpUtil.isLoopbackCandidate("candidate:3114996645 1 UDP 16785407 5.148.189.199 52382 typ relay raddr 2a02:200:2c00:2a51:921b:eff:fe8d:43b rport 49712"));
		assertFalse(SdpUtil.isLoopbackCandidate("candidate:4231669940 1 UDP 1677732095 2a02:200:2c00:2a51:921b:eff:fe8d:43b 56435 typ srflx"));
	}

	@Test
	public void testIsIpv6Candidate() {
		assertFalse(SdpUtil.isIpv6Candidate("candidate:1510613869 1 UDP 2122063615 127.0.0.1 55682 typ host"));
		assertFalse(SdpUtil.isIpv6Candidate("candidate:344579997 1 TCP 1518083839 127.0.0.1 39778 typ host tcptype passive"));
		assertTrue(SdpUtil.isIpv6Candidate("candidate:559267639 1 UDP 2122136831 ::1 41989 typ host"));
		assertTrue(SdpUtil.isIpv6Candidate("candidate:1876313031 1 TCP 1518157055 ::1 51083 typ host tcptype passive"));
		assertTrue(SdpUtil.isIpv6Candidate("candidate:3660013861 1 TCP 1518283007 2a02:200:2c00:2a52:152c:1fd5:46fc:9174 9 typ host tcptype active"));
		assertFalse(SdpUtil.isIpv6Candidate("candidate:3102198902 1 TCP 1518214911 192.168.12.155 9 typ host tcptype active"));
		assertTrue(SdpUtil.isIpv6Candidate("candidate:3114996645 1 UDP 16785407 5.148.189.199 52382 typ relay raddr 2a02:200:2c00:2a51:921b:eff:fe8d:43b rport 49712"));
		assertTrue(SdpUtil.isIpv6Candidate("candidate:4231669940 1 UDP 1677732095 2a02:200:2c00:2a51:921b:eff:fe8d:43b 56435 typ srflx"));
	}

}

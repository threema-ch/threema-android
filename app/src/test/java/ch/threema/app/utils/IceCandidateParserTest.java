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

package ch.threema.app.utils;

import org.junit.Test;

import java.util.HashMap;
import java.util.regex.Pattern;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

public class IceCandidateParserTest {

    @Test
    public void parseFoundation() {
        assertTrue(Pattern.matches(IceCandidateParser.FOUNDATION, "373990095"));
        assertTrue(Pattern.matches(IceCandidateParser.FOUNDATION, "37/39asdf90+095"));
        assertFalse(Pattern.matches(IceCandidateParser.FOUNDATION, ""));
        assertTrue(Pattern.matches(IceCandidateParser.FOUNDATION, "01234567890123456789012345678901"));
        assertFalse(Pattern.matches(IceCandidateParser.FOUNDATION, "012345678901234567890123456789012"));
    }

    @Test
    public void parseComponentId() {
        assertTrue(Pattern.matches(IceCandidateParser.COMPONENT_ID, "0"));
        assertTrue(Pattern.matches(IceCandidateParser.COMPONENT_ID, "1"));
        assertTrue(Pattern.matches(IceCandidateParser.COMPONENT_ID, "12345"));
        assertFalse(Pattern.matches(IceCandidateParser.COMPONENT_ID, "1234a"));
        assertFalse(Pattern.matches(IceCandidateParser.COMPONENT_ID, "123456"));
    }

    @Test
    public void parseTransport() {
        assertTrue(Pattern.matches(IceCandidateParser.TRANSPORT, "udp"));
        assertTrue(Pattern.matches(IceCandidateParser.TRANSPORT, "UDP"));
        assertTrue(Pattern.matches(IceCandidateParser.TRANSPORT, "UdP"));
        assertFalse(Pattern.matches(IceCandidateParser.TRANSPORT, "u dp"));
        assertTrue(Pattern.matches(IceCandidateParser.TRANSPORT, "tcp"));
    }

    @Test
    public void parsePriority() {
        assertTrue(Pattern.matches(IceCandidateParser.PRIORITY, "1"));
        assertTrue(Pattern.matches(IceCandidateParser.PRIORITY, "0"));
        assertTrue(Pattern.matches(IceCandidateParser.PRIORITY, "1234567890"));
        assertTrue(Pattern.matches(IceCandidateParser.PRIORITY, "41885439"));
        assertFalse(Pattern.matches(IceCandidateParser.PRIORITY, "12345678901"));
        assertFalse(Pattern.matches(IceCandidateParser.PRIORITY, "123456789a"));
    }

    @Test
    public void parseCandType() {
        assertTrue(Pattern.matches(IceCandidateParser.CAND_TYPE, "typ host"));
        assertTrue(Pattern.matches(IceCandidateParser.CAND_TYPE, "typ srflx"));
        assertTrue(Pattern.matches(IceCandidateParser.CAND_TYPE, "typ prflx"));
        assertTrue(Pattern.matches(IceCandidateParser.CAND_TYPE, "typ relay"));
        assertFalse(Pattern.matches(IceCandidateParser.CAND_TYPE, "typ foo"));
    }

    @Test
    public void parseMinimal() {
        final String candidate = "candidate:373990095 1 udp 41885439 5.148.189.205 63293 typ relay";
        final IceCandidateParser.CandidateData result = IceCandidateParser.parse(candidate);
        assertNotNull(result);
        assertEquals("373990095", result.foundation);
        assertEquals(1, result.componentId);
        assertEquals("udp", result.transport);
        assertEquals(41885439, result.priority);
        assertEquals("5.148.189.205", result.connectionAddress);
        assertEquals(63293, result.port);
        assertEquals("relay", result.candType);
        assertEquals(null, result.relAddr);
        assertEquals(null, result.relPort);
    }

    @Test
    public void parseWithRel() {
        final String candidate = "candidate:373990095 1 udp 41885439 5.148.189.205 63293 typ relay raddr 1.2.3.4 rport 5432";
        final IceCandidateParser.CandidateData result = IceCandidateParser.parse(candidate);
        assertNotNull(result);
        assertEquals("1.2.3.4", result.relAddr);
        assertEquals(Integer.valueOf(5432), result.relPort);
    }

    @Test
    public void parseWithRelAddr() {
        final String candidate = "candidate:373990095 1 udp 41885439 5.148.189.205 63293 typ relay raddr ::1";
        final IceCandidateParser.CandidateData result = IceCandidateParser.parse(candidate);
        assertNotNull(result);
        assertEquals("::1", result.relAddr);
        assertEquals(null, result.relPort);
    }

    @Test
    public void parseWithRelPort() {
        final String candidate = "candidate:373990095 1 udp 41885439 5.148.189.205 63293 typ relay rport 5432";
        final IceCandidateParser.CandidateData result = IceCandidateParser.parse(candidate);
        assertNotNull(result);
        assertEquals(null, result.relAddr);
        assertEquals(Integer.valueOf(5432), result.relPort);
    }

    @Test
    public void parseTcp() {
        final String candidate = "candidate:1876313031 1 tcp 1518222591 ::1 58170 typ host tcptype passive generation 0 ufrag xTeg network-id 2";
        final IceCandidateParser.CandidateData result = IceCandidateParser.parse(candidate);
        assertNotNull(result);
        assertEquals("tcp", result.transport);
        assertEquals("passive", result.tcptype);
        assertEquals(new HashMap<String, String>() {{
            put("generation", "0");
            put("ufrag", "xTeg");
            put("network-id", "2");
            put("tcptype", "passive");
        }}, result.extensions);
    }

    @Test
    public void parseFull() {
        final String candidate = "candidate:842163049 1 udp 1686052607 1.2.3.4 46154 typ srflx raddr 10.0.0.17 rport 46154 generation 0 ufrag EEtu network-id 3 network-cost 10";
        final IceCandidateParser.CandidateData result = IceCandidateParser.parse(candidate);
        assertNotNull(result);
        assertEquals("842163049", result.foundation);
        assertEquals(1, result.componentId);
        assertEquals("udp", result.transport);
        assertNull("udp", result.tcptype);
        assertEquals(1686052607, result.priority);
        assertEquals("1.2.3.4", result.connectionAddress);
        assertEquals(46154, result.port);
        assertEquals("srflx", result.candType);
        assertEquals("10.0.0.17", result.relAddr);
        assertEquals(Integer.valueOf(46154), result.relPort);
        assertEquals(new HashMap<String, String>() {{
            put("generation", "0");
            put("ufrag", "EEtu");
            put("network-id", "3");
            put("network-cost", "10");
        }}, result.extensions);
    }
}

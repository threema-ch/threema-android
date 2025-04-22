/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2025 Threema GmbH
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parse ICE candidates.
 * <p>
 * Grammar (from https://tools.ietf.org/html/rfc5245#section-15.1):
 * <p>
 * candidate-attribute   = "candidate" ":" foundation SP component-id SP
 * transport SP
 * priority SP
 * connection-address SP     ;from RFC 4566
 * port         ;port from RFC 4566
 * SP cand-type
 * [SP rel-addr]
 * [SP rel-port]
 * *(SP extension-att-name SP
 * extension-att-value)
 * <p>
 * foundation            = 1*32ice-char
 * component-id          = 1*5DIGIT
 * transport             = "UDP" / transport-extension
 * transport-extension   = token              ; from RFC 3261
 * priority              = 1*10DIGIT
 * cand-type             = "typ" SP candidate-types
 * candidate-types       = "host" / "srflx" / "prflx" / "relay" / token
 * rel-addr              = "raddr" SP connection-address
 * rel-port              = "rport" SP port
 * extension-att-name    = byte-string    ;from RFC 4566
 * extension-att-value   = byte-string
 * ice-char              = ALPHA / DIGIT / "+" / "/"
 */
public class IceCandidateParser {
    // Grammar
    public final static String SP = "\\s";
    public final static String TOKEN = "[a-zA-Z]+";
    public final static String ICE_CHAR = "[a-zA-Z\\d\\+\\/]";
    public final static String FOUNDATION = ICE_CHAR + "{1,32}";
    public final static String COMPONENT_ID = "\\d{1,5}";
    public final static String TRANSPORT = TOKEN;
    public final static String PRIORITY = "\\d{1,10}";
    public final static String CANDIDATE_TYPES = "(host|srflx|prflx|relay)";
    public final static String CAND_TYPE = "typ" + SP + CANDIDATE_TYPES;
    public final static String CONNECTION_ADDRESS = "\\S+";
    public final static String REL_ADDR = "raddr" + SP + "(" + CONNECTION_ADDRESS + ")";
    public final static String PORT = "\\d{1,5}"; // Lenient implementation
    public final static String REL_PORT = "rport" + SP + "(" + PORT + ")";
    public final static String BYTE_STRING = "\\S+"; // Lenient implementation
    public final static String EXTENSION_ATT_NAME = BYTE_STRING;
    public final static String EXTENSION_ATT_VALUE = BYTE_STRING;
    public final static Pattern CANDIDATE_ATTRIBUTE = Pattern.compile(
        "candidate:" + "(" + FOUNDATION + ")" + SP
            + "(" + COMPONENT_ID + ")" + SP
            + "(" + TRANSPORT + ")" + SP
            + "(" + PRIORITY + ")" + SP
            + "(" + CONNECTION_ADDRESS + ")" + SP
            + "(" + PORT + ")" + SP
            + CAND_TYPE
            + "(" + SP + REL_ADDR + ")?"
            + "(" + SP + REL_PORT + ")?"
            + "((" + SP + EXTENSION_ATT_NAME + SP + EXTENSION_ATT_VALUE + ")*)");

    // Wrapper class
    public static class CandidateData {
        @NonNull
        public final String foundation;
        public final int componentId;
        @NonNull
        public final String transport;
        @Nullable
        public final String tcptype;
        public final int priority;
        @NonNull
        public final String connectionAddress;
        public final int port;
        @NonNull
        public final String candType;
        @Nullable
        public final String relAddr;
        @Nullable
        public final Integer relPort;
        @NonNull
        public final Map<String, String> extensions;

        public CandidateData(@NonNull String foundation,
                             int componentId,
                             @NonNull String transport,
                             @Nullable String tcptype,
                             int priority,
                             @NonNull String connectionAddress,
                             int port,
                             @NonNull String candType,
                             @Nullable String relAddr,
                             @Nullable Integer relPort,
                             @NonNull Map<String, String> extensions) {
            this.foundation = foundation;
            this.componentId = componentId;
            this.transport = transport;
            this.tcptype = tcptype;
            this.priority = priority;
            this.connectionAddress = connectionAddress;
            this.port = port;
            this.candType = candType;
            this.relAddr = relAddr;
            this.relPort = relPort;
            this.extensions = extensions;
        }
    }

    @Nullable
    public static CandidateData parse(@NonNull String candidateString) {
        final Matcher matcher = CANDIDATE_ATTRIBUTE.matcher(candidateString);
        if (!matcher.matches()) {
            return null;
        }

        // Unfortunately the Regex module shipped with Android does not support
        // named capture groups, therefore we need to stick with the brittle and hacky
        // numbered capture group approach. Fortunately there are tests.

        // Extract extensions
        final String extensionsString = matcher.group(12);
        final Map<String, String> extensions = new HashMap<>();
        if (extensionsString != null) {
            String key = null;
            for (String segment : extensionsString.trim().split(" ")) {
                if (key == null) {
                    key = segment;
                } else {
                    extensions.put(key, segment);
                    key = null;
                }
            }
        }

        // Extract rest of data, return data POJO
        return new CandidateData(
            matcher.group(1),
            Integer.parseInt(matcher.group(2)),
            matcher.group(3),
            extensions.get("tcptype"),
            Integer.parseInt(matcher.group(4)),
            matcher.group(5),
            Integer.parseInt(matcher.group(6)),
            matcher.group(7),
            matcher.group(9),
            matcher.group(11) == null ? null : Integer.parseInt(matcher.group(11)),
            extensions
        );
    }
}

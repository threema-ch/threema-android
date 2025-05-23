/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2025 Threema GmbH
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

/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.vending.licensing.util;

import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.Map;
import java.util.Scanner;

public class URIQueryDecoder {
    private static final String TAG = "URIQueryDecoder";

    /**
     * Decodes the query portion of the passed-in URI.
     *
     * @param encodedURI the URI containing the query to decode
     * @param results    a map containing all query parameters. Query parameters that do not have a
     *                   value will map to a null string
     */
    static public void DecodeQuery(URI encodedURI, Map<String, String> results) {
        try (Scanner scanner = new Scanner(encodedURI.getRawQuery())) {
            scanner.useDelimiter("&");
            try {
                while (scanner.hasNext()) {
                    String param = scanner.next();
                    String[] valuePair = param.split("=");
                    String name, value;
                    if (valuePair.length == 1) {
                        value = null;
                    } else if (valuePair.length == 2) {
                        value = URLDecoder.decode(valuePair[1], "UTF-8");
                    } else {
                        throw new IllegalArgumentException("query parameter invalid");
                    }
                    name = URLDecoder.decode(valuePair[0], "UTF-8");
                    results.put(name, value);
                }
            } catch (UnsupportedEncodingException e) {
                // This should never happen.
                Log.e(TAG, "UTF-8 Not Recognized as a charset.  Device configuration Error.");
            }
        }
    }
}

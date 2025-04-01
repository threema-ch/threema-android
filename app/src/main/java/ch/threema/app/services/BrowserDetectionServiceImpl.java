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

package ch.threema.app.services;

final public class BrowserDetectionServiceImpl implements BrowserDetectionService {
    @Override
    public Browser detectBrowser(String userAgent) {
        if (userAgent != null && userAgent.length() > 0) {
            final String desc = userAgent.toLowerCase().trim();
            if (desc.contains("threemadesktop")) {
                return Browser.WEBTOP;
            } else if (desc.contains("mozilla") && desc.contains("applewebkit")
                && desc.contains("chrome") && desc.contains("safari")
                && desc.contains("opr")) {
                return Browser.OPERA;
            } else if (desc.contains("chrome") && desc.contains("webkit") && !desc.contains("edge")
                && !desc.contains("edg")) {
                return Browser.CHROME;
            } else if (desc.contains("mozilla") && desc.contains("firefox")) {
                return Browser.FIREFOX;
            } else if (desc.contains("safari") && desc.contains("applewebkit") && !desc.contains("chrome")) {
                return Browser.SAFARI;
            } else if (desc.contains("edge") || desc.contains("edg")) {
                return Browser.EDGE;
            }
        }
        return Browser.UNKNOWN;
    }
}

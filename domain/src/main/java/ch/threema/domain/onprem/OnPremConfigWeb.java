/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2024 Threema GmbH
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

package ch.threema.domain.onprem;

public class OnPremConfigWeb {
    private final String url;
    private final String overrideSaltyRtcHost;
    private final int overrideSaltyRtcPort;

    public OnPremConfigWeb(String url, String overrideSaltyRtcHost, int overrideSaltyRtcPort) {
        this.url = url;
        this.overrideSaltyRtcHost = overrideSaltyRtcHost;
        this.overrideSaltyRtcPort = overrideSaltyRtcPort;
    }

    public String getUrl() {
        return url;
    }

    public String getOverrideSaltyRtcHost() {
        return overrideSaltyRtcHost;
    }

    public int getOverrideSaltyRtcPort() {
        return overrideSaltyRtcPort;
    }
}

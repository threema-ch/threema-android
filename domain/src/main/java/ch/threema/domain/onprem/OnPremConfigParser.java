/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2024 Threema GmbH
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import ch.threema.base.utils.Base64;
import ch.threema.base.utils.LoggingUtil;

public class OnPremConfigParser {

    private static final Logger logger = LoggingUtil.getThreemaLogger("OnPremConfigParser");

    private static final int MIN_REFRESH_VALUE_S = 1800;

    public OnPremConfig parse(JSONObject obj) throws IOException, JSONException, ParseException, LicenseExpiredException {
        int refreshValue = obj.getInt("refresh");
        if (refreshValue < MIN_REFRESH_VALUE_S) {
            logger.warn("Invalid refresh value provided: {}; using {} as fallback", refreshValue, MIN_REFRESH_VALUE_S);
            refreshValue = MIN_REFRESH_VALUE_S;
        }

        return new OnPremConfig(
            System.currentTimeMillis() + refreshValue * 1000L,
            this.parseLicense(obj.getJSONObject("license")),
            this.parseChatConfig(obj.getJSONObject("chat")),
            this.parseDirectoryConfig(obj.getJSONObject("directory")),
            this.parseBlobConfig(obj.getJSONObject("blob")),
            this.parseWorkConfig(obj.getJSONObject("work")),
            this.parseAvatarConfig(obj.getJSONObject("avatar")),
            this.parseSafeConfig(obj.getJSONObject("safe")),
            this.parseWebConfig(obj.optJSONObject("web")),
            this.parseMediatorConfig(obj.optJSONObject("mediator")));
    }

    private OnPremLicense parseLicense(JSONObject obj) throws JSONException, ParseException, LicenseExpiredException {
        SimpleDateFormat ymdFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date expires = ymdFormat.parse(obj.getString("expires"));

        // Add one day as expiration is defined as "not valid after this date"
        expires.setTime(expires.getTime() + 86400000);

        // Check that license has not expired
        if (expires.before(new Date())) {
            throw new LicenseExpiredException("License has expired");
        }

        return new OnPremLicense(obj.getString("id"), expires, obj.getInt("count"));
    }

    private OnPremConfigChat parseChatConfig(JSONObject obj) throws JSONException, IOException {
        JSONArray portsArray = obj.getJSONArray("ports");
        int[] ports = new int[portsArray.length()];
        for (int i = 0; i < ports.length; i++) {
            ports[i] = portsArray.getInt(i);
        }
        return new OnPremConfigChat(obj.getString("hostname"), ports, Base64.decode(obj.getString("publicKey")));
    }

    private OnPremConfigDirectory parseDirectoryConfig(JSONObject obj) throws JSONException {
        return new OnPremConfigDirectory(obj.getString("url"));
    }

    private OnPremConfigBlob parseBlobConfig(JSONObject obj) throws JSONException {
        return new OnPremConfigBlob(obj.getString("uploadUrl"), obj.getString("downloadUrl"), obj.getString("doneUrl"));
    }

    private OnPremConfigAvatar parseAvatarConfig(JSONObject obj) throws JSONException {
        return new OnPremConfigAvatar(obj.getString("url"));
    }

    private OnPremConfigSafe parseSafeConfig(JSONObject obj) throws JSONException {
        return new OnPremConfigSafe(obj.getString("url"));
    }

    private OnPremConfigWork parseWorkConfig(JSONObject obj) throws JSONException {
        return new OnPremConfigWork(obj.getString("url"));
    }

    private OnPremConfigWeb parseWebConfig(JSONObject obj) throws JSONException {
        if (obj == null) {
            return null;
        }
        return new OnPremConfigWeb(obj.getString("url"), obj.optString("overrideSaltyRtcHost"), obj.optInt("overrideSaltyRtcPort"));
    }

    private OnPremConfigMediator parseMediatorConfig(JSONObject obj) throws JSONException {
        if (obj == null) {
            return null;
        }
        return new OnPremConfigMediator(obj.getString("url"), parseBlobConfig(obj.getJSONObject("blob")));
    }
}

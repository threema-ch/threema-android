/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2024 Threema GmbH
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

package ch.threema.app.threemasafe;

import org.slf4j.Logger;

import java.net.MalformedURLException;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.Base64;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.base.utils.Utils;

import static ch.threema.app.threemasafe.ThreemaSafeService.BACKUP_ID_LENGTH;

public class ThreemaSafeServerInfo {
    private static final Logger logger = LoggingUtil.getThreemaLogger("ThreemaSafeServerInfo");

    private static final String SAFE_URL_PREFIX = "https://";
    private static final String BACKUP_DIRECTORY_NAME = "backups/";

    // TODO(ANDR-2968): Remove the check for the legacy default server name
    private static final String LEGACY_DEFAULT_SERVER_NAME = "safe-%h.threema.ch";

    @Nullable
    private String customServerName;
    @NonNull
    private final String defaultServerName = getDefaultServerNameFromServerAddressProvider();
    private String serverUsername;
    private String serverPassword;

    public ThreemaSafeServerInfo() {
    }

    public ThreemaSafeServerInfo(String customServerName, String serverUsername, String serverPassword) {
        this.serverUsername = serverUsername;
        this.serverPassword = serverPassword;
        this.setCustomServerName(customServerName);
    }

    @Nullable
    public String getCustomServerName() {
        return customServerName;
    }

    public void setCustomServerName(@Nullable String customServerName) {
        if (!TestUtil.isEmptyOrNull(customServerName)) {
            this.customServerName = customServerName.trim().replace(SAFE_URL_PREFIX, "");
            if (defaultServerName.equals(this.customServerName) || LEGACY_DEFAULT_SERVER_NAME.equals(this.customServerName)) {
                this.customServerName = null;
                logger.warn("Tried to set default server as custom server: {}", customServerName);
            }
        } else {
            this.customServerName = null;
        }
    }

    public String getServerUsername() {
        return serverUsername;
    }

    void setServerUsername(String serverUsername) {
        this.serverUsername = serverUsername;
    }

    public String getServerPassword() {
        return serverPassword;
    }

    void setServerPassword(String serverPassword) {
        this.serverPassword = serverPassword;
    }

    public boolean isDefaultServer() {
        return TestUtil.isEmptyOrNull(customServerName);
    }

    URL getBackupUrl(byte[] backupId) throws ThreemaException {
        if (backupId == null || backupId.length != BACKUP_ID_LENGTH) {
            throw new ThreemaException("Invalid Backup ID");
        }

        URL serverUrl = getServerUrl(backupId, BACKUP_DIRECTORY_NAME + Utils.byteArrayToHexString(backupId));
        if (serverUrl == null) {
            throw new ThreemaException("Invalid Server URL");
        }

        return serverUrl;
    }

    URL getConfigUrl(byte[] backupId) throws ThreemaException {
        URL serverUrl = getServerUrl(backupId, "config");
        if (serverUrl == null) {
            throw new ThreemaException("Invalid URL");
        }

        return serverUrl;
    }

    void addAuthorization(HttpsURLConnection urlConnection) throws ThreemaException {
        String username = serverUsername, password = serverPassword;

        if ((TestUtil.isEmptyOrNull(serverUsername) || TestUtil.isEmptyOrNull(serverPassword)) && !TestUtil.isEmptyOrNull(customServerName)) {
            int atPos = customServerName.indexOf("@");
            if (atPos > 0) {
                String userInfo = customServerName.substring(0, atPos);

                int colonPos = userInfo.indexOf(":");
                if (colonPos > 0 && colonPos < userInfo.length() - 1) {
                    username = userInfo.substring(0, colonPos);
                    password = userInfo.substring(colonPos + 1);
                }
            }
        }

        if (!TestUtil.isEmptyOrNull(username) && !TestUtil.isEmptyOrNull(password)) {
            String basicAuth = "Basic " + Base64.encodeBytes((username + ":" + password).getBytes());
            urlConnection.setRequestProperty("Authorization", basicAuth);
        } else if (ConfigUtils.isOnPremBuild()) {
            urlConnection.setRequestProperty("Authorization", "Token " + ThreemaApplication.getServiceManager().getApiService().getAuthToken());
        }
    }

    private String getCustomServerNameOrDefault() {
        if (!TestUtil.isEmptyOrNull(customServerName)) {
            return customServerName;
        } else {
            return defaultServerName;
        }
    }

    private URL getServerUrl(byte[] backupId, String filePart) {
        try {
            String shardHash = getShardHash(backupId);
            String serverUrl = SAFE_URL_PREFIX + getCustomServerNameOrDefault().replace("{backupIdPrefix8}", shardHash);

            if (!serverUrl.endsWith("/")) {
                serverUrl += "/";
            }
            serverUrl += filePart;
            return new URL(serverUrl);
        } catch (MalformedURLException e) {
            return null;
        }
    }

    private String getShardHash(byte[] backupId) {
        if (backupId != null && backupId.length == BACKUP_ID_LENGTH) {
            return Utils.byteArrayToHexString(backupId).substring(0, 2);
        }
        return "xx";
    }

    public String getHostName() {
        try {
            return new URL(SAFE_URL_PREFIX + getCustomServerNameOrDefault()).getHost();
        } catch (MalformedURLException e) {
            logger.error("Exception", e);
        }
        return "";
    }

    @NonNull
    private String getDefaultServerNameFromServerAddressProvider() {
        ServiceManager serviceManager = ThreemaApplication.getServiceManager();
        if (serviceManager == null) {
            logger.error("Cannot retrieve default safe server name as the service manager is null");
            return "";
        }
        try {
            return serviceManager
                .getServerAddressProviderService()
                .getServerAddressProvider()
                .getSafeServerUrl(false)
                .replace(SAFE_URL_PREFIX, "");
        } catch (ThreemaException e) {
            logger.error("Could not get default safe server name", e);
            return "";
        }
    }
}

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

package ch.threema.app.threemasafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

import ch.threema.app.utils.LogUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.ThreemaException;
import ch.threema.client.Base64;
import ch.threema.client.Utils;

import static ch.threema.app.threemasafe.ThreemaSafeService.BACKUP_ID_LENGTH;

public class ThreemaSafeServerInfo {
	private static final Logger logger = LoggerFactory.getLogger(ThreemaSafeServerInfo.class);

	private static final String DEFAULT_THREEMA_SAFE_SERVER_NAME = "safe-%h.threema.ch";
	private static final String SAFE_URL_PREFIX = "https://";
	private static final String BACKUP_DIRECTORY_NAME = "backups/";

	private String serverName;
	private String serverUsername;
	private String serverPassword;

	public ThreemaSafeServerInfo() {
		this.serverName = DEFAULT_THREEMA_SAFE_SERVER_NAME;
	}

	public ThreemaSafeServerInfo(String serverName, String serverUsername, String serverPassword) {
		this.serverUsername = serverUsername;
		this.serverPassword = serverPassword;
		this.setServerName(serverName);
	}

	public String getServerName() {
		return serverName;
	}

	public void setServerName(String serverName) {
		if (!TestUtil.empty(serverName)) {
			serverName = serverName.trim();
			// strip https prefix
			if (serverName.startsWith(SAFE_URL_PREFIX)) {
				serverName = serverName.substring(SAFE_URL_PREFIX.length());
			}
		} else {
			serverName = DEFAULT_THREEMA_SAFE_SERVER_NAME;
		}
		this.serverName = serverName;
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
		return this.serverName == null || this.serverName.equals(DEFAULT_THREEMA_SAFE_SERVER_NAME);
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

	void addAuthorization(HttpsURLConnection urlConnection) {
		String username = serverUsername, password = serverPassword;

		if (TestUtil.empty(serverUsername) || TestUtil.empty(serverPassword)) {
			int atPos = serverName.indexOf("@");
			if (atPos > 0) {
				String userInfo = serverName.substring(0, atPos);

				int colonPos = userInfo.indexOf(":");
				if (colonPos > 0 && colonPos < userInfo.length() - 1) {
					username = userInfo.substring(0, colonPos);
					password = userInfo.substring(colonPos + 1);
				}
			}
		}

		if (!TestUtil.empty(username) && !TestUtil.empty(password)) {
			String basicAuth = "Basic " + Base64.encodeBytes((username + ":" + password).getBytes());
			urlConnection.setRequestProperty("Authorization", basicAuth);
		}
	}

	private URL getServerUrl(byte[] backupId, String filePart) {
		try {
			String serverUrl = "https://" + (isDefaultServer() ? serverName.replaceAll("%h", getShardHash(backupId)) : serverName);
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
			return new URL("https://" + serverName).getHost();
		} catch (MalformedURLException e) {
			logger.error("Exception", e);
		}
		return "";
	}
}

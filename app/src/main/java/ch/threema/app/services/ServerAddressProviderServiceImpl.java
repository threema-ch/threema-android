/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2022 Threema GmbH
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

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;

import ch.threema.app.BuildConfig;
import ch.threema.domain.onprem.OnPremConfigFetcher;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.base.ThreemaException;
import ch.threema.domain.onprem.ServerAddressProviderOnPrem;
import ch.threema.domain.protocol.ServerAddressProvider;

public class ServerAddressProviderServiceImpl implements ServerAddressProviderService {
	private final PreferenceService preferenceService;
	private OnPremConfigFetcher onPremConfigFetcher;
	private URL lastOnPremServer;

	public ServerAddressProviderServiceImpl(PreferenceService preferenceService) {
		this.preferenceService = preferenceService;
	}

	public ServerAddressProvider getServerAddressProvider() {
		if (ConfigUtils.isOnPremBuild()) {
			return getServerAddressProviderOnPrem();
		} else {
			return getServerAddressProviderBuildConfig();
		}
	}

	private ServerAddressProvider getServerAddressProviderOnPrem() {
		return new ServerAddressProviderOnPrem(new ServerAddressProviderOnPrem.FetcherProvider() {
			@Override
			public OnPremConfigFetcher getFetcher() throws ThreemaException {
				return getOnPremConfigFetcher();
			}
		});
	}

	private ServerAddressProvider getServerAddressProviderBuildConfig() {
		return new ServerAddressProvider() {
			@Override
			public String getChatServerNamePrefix(boolean ipv6) {
				return ipv6 ? BuildConfig.CHAT_SERVER_IPV6_PREFIX : BuildConfig.CHAT_SERVER_PREFIX;
			}

			@Override
			public String getChatServerNameSuffix(boolean ipv6) {
				return BuildConfig.CHAT_SERVER_SUFFIX;
			}

			@Override
			public int[] getChatServerPorts() {
				return BuildConfig.CHAT_SERVER_PORTS;
			}

			@Override
			public boolean getChatServerUseServerGroups() {
				return BuildConfig.CHAT_SERVER_GROUPS;
			}

			@Override
			public byte[] getChatServerPublicKey() {
				return BuildConfig.SERVER_PUBKEY;
			}

			@Override
			public byte[] getChatServerPublicKeyAlt() {
				return BuildConfig.SERVER_PUBKEY_ALT;
			}

			@Override
			public String getDirectoryServerUrl(boolean ipv6) {
				return ipv6 ? BuildConfig.DIRECTORY_SERVER_IPV6_URL : BuildConfig.DIRECTORY_SERVER_URL;
			}

			@Override
			public String getWorkServerUrl(boolean ipv6) {
				return ipv6 ? BuildConfig.WORK_SERVER_IPV6_URL : BuildConfig.WORK_SERVER_URL;
			}

			@Override
			public String getBlobServerDownloadUrl(boolean ipv6) {
				return ipv6 ? BuildConfig.BLOB_SERVER_DOWNLOAD_IPV6_URL : BuildConfig.BLOB_SERVER_DOWNLOAD_URL;
			}

			@Override
			public String getBlobServerDoneUrl(boolean ipv6) {
				return ipv6 ? BuildConfig.BLOB_SERVER_DONE_IPV6_URL : BuildConfig.BLOB_SERVER_DONE_URL;
			}

			@Override
			public String getBlobServerUploadUrl(boolean ipv6) {
				return ipv6 ? BuildConfig.BLOB_SERVER_UPLOAD_IPV6_URL : BuildConfig.BLOB_SERVER_UPLOAD_URL;
			}

			@Override
			public String getAvatarServerUrl(boolean ipv6) throws ThreemaException {
				return BuildConfig.AVATAR_FETCH_URL;
			}

			@Override
			public String getSafeServerUrl(boolean ipv6) throws ThreemaException {
				return BuildConfig.SAFE_SERVER_URL;
			}
		};
	}

	private OnPremConfigFetcher getOnPremConfigFetcher() throws ThreemaException {
		try {
			URL curOnPremServer = makeUrlWithUsernamePassword(new URL(preferenceService.getOnPremServer()),
				preferenceService.getLicenseUsername(), preferenceService.getLicensePassword());

			// Note: must use toString when comparing URLs, as Java ignores userInfo in URL.equals()
			if (onPremConfigFetcher == null || !curOnPremServer.toString().equals(lastOnPremServer.toString())) {
				onPremConfigFetcher = new OnPremConfigFetcher(curOnPremServer, BuildConfig.ONPREM_CONFIG_TRUSTED_PUBLIC_KEYS);
				lastOnPremServer = curOnPremServer;
			}
		} catch (MalformedURLException e) {
			throw new ThreemaException("Bad OnPrem server URL", e);
		}

		return onPremConfigFetcher;
	}

	private URL makeUrlWithUsernamePassword(URL url, String username, String password) throws MalformedURLException {
		String urlAuth = null;
		try {
			urlAuth = url.getProtocol() + "://" +
				URLEncoder.encode(username, "UTF-8") + ":" +
				URLEncoder.encode(password, "UTF-8") + "@" +
				url.getHost();
		} catch (UnsupportedEncodingException e) {
			// UTF-8 is always supported
			throw new RuntimeException(e);
		}
		if (url.getPort() > 0) {
			urlAuth += ":" + url.getPort();
		}
		urlAuth += url.getFile();

		return new URL(urlAuth);
	}
}

/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2022 Threema GmbH
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

import org.json.JSONException;

import java.io.IOException;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

import ch.threema.app.utils.ConfigUtils;
import ch.threema.base.ThreemaException;
import ch.threema.domain.models.AppVersion;
import ch.threema.domain.protocol.ServerAddressProvider;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.domain.protocol.blob.BlobLoader;
import ch.threema.domain.protocol.blob.BlobUploader;
import ch.threema.domain.stores.IdentityStoreInterface;
import ch.threema.domain.stores.TokenStoreInterface;

public class ApiServiceImpl implements ApiService {
	private final AppVersion appVersion;
	private final boolean ipv6;
	private final APIConnector apiConnector;
	private final TokenStoreInterface authTokenStore;
	private final ServerAddressProvider serverAddressProvider;

	public ApiServiceImpl(AppVersion appVersion, boolean ipv6, APIConnector apiConnector, TokenStoreInterface authTokenStore, ServerAddressProvider serverAddressProvider) {
		this.appVersion = appVersion;
		this.ipv6 = ipv6;
		this.apiConnector = apiConnector;
		this.authTokenStore = authTokenStore;
		this.serverAddressProvider = serverAddressProvider;
	}

	@Override
	public BlobUploader createUploader(byte[] data) throws ThreemaException {
		BlobUploader uploader = new BlobUploader(ConfigUtils::getSSLSocketFactory, data, ipv6, serverAddressProvider, null);
		uploader.setVersion(this.appVersion);
		if (ConfigUtils.isOnPremBuild()) {
			uploader.setAuthToken(getAuthToken());
		}
		return uploader;
	}

	@Override
	public BlobLoader createLoader(byte[] blobId) {
		BlobLoader loader = new BlobLoader(ConfigUtils::getSSLSocketFactory, blobId, ipv6, serverAddressProvider, null);
		loader.setVersion(this.appVersion);
		return loader;
	}

	@Override
	public String getAuthToken() throws ThreemaException {
		try {
			return apiConnector.obtainAuthToken(authTokenStore, false);
		} catch (IOException | JSONException e) {
			throw new ThreemaException("Cannot obtain authentication token", e);
		}
	}

	@Override
	public HttpsURLConnection createAvatarURLConnection(String identity) throws ThreemaException, IOException {
		URL url = new URL(serverAddressProvider.getAvatarServerUrl(false) + identity);
		HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
		connection.setSSLSocketFactory(ConfigUtils.getSSLSocketFactory(url.getHost()));
		if (ConfigUtils.isOnPremBuild()) {
			connection.setRequestProperty("Authorization", "Token " + getAuthToken());
		}
		return connection;
	}
}

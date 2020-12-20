/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2020 Threema GmbH
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

import ch.threema.app.utils.ConfigUtils;
import ch.threema.client.AppVersion;
import ch.threema.client.BlobLoader;
import ch.threema.client.BlobUploader;

public class ApiServiceImpl implements ApiService {
	private final AppVersion appVersion;
	private final boolean ipv6;

	public ApiServiceImpl(AppVersion appVersion, boolean ipv6) {
		this.appVersion = appVersion;
		this.ipv6 = ipv6;
	}

	@Override
	public BlobUploader createUploader(byte[] data) {
		BlobUploader uploader = new BlobUploader(ConfigUtils::getSSLSocketFactory, data);
		uploader.setVersion(this.appVersion);
		uploader.setServerUrls(ipv6);
		return uploader;
	}

	@Override
	public BlobLoader createLoader(byte[] blobId) {
		BlobLoader loader = new BlobLoader(ConfigUtils::getSSLSocketFactory, blobId);
		loader.setVersion(this.appVersion);
		loader.setServerUrls(ipv6);
		return loader;
	}
}

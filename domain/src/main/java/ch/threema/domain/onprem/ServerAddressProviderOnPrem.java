/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2022 Threema GmbH
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

import ch.threema.base.ThreemaException;
import ch.threema.domain.protocol.ServerAddressProvider;

public class ServerAddressProviderOnPrem implements ServerAddressProvider {

	public interface FetcherProvider {
		OnPremConfigFetcher getFetcher() throws ThreemaException;
	}

	private final FetcherProvider fetcherProvider;

	public ServerAddressProviderOnPrem(FetcherProvider fetcherProvider) {
		this.fetcherProvider = fetcherProvider;
	}

	@Override
	public String getChatServerNamePrefix(boolean ipv6) throws ThreemaException {
		return "";
	}

	@Override
	public String getChatServerNameSuffix(boolean ipv6) throws ThreemaException {
		return getOnPremConfigFetcher().fetch().getChatConfig().getHostname();
	}

	@Override
	public int[] getChatServerPorts() throws ThreemaException {
		return getOnPremConfigFetcher().fetch().getChatConfig().getPorts();
	}

	@Override
	public boolean getChatServerUseServerGroups() {
		return false;
	}

	@Override
	public byte[] getChatServerPublicKey() throws ThreemaException {
		return getOnPremConfigFetcher().fetch().getChatConfig().getPublicKey();
	}

	@Override
	public byte[] getChatServerPublicKeyAlt() throws ThreemaException {
		// No alternate public key for OnPrem, as it can easily be switched in OPPF
		return getOnPremConfigFetcher().fetch().getChatConfig().getPublicKey();
	}

	@Override
	public String getDirectoryServerUrl(boolean ipv6) throws ThreemaException {
		return getOnPremConfigFetcher().fetch().getDirectoryConfig().getUrl();
	}

	@Override
	public String getWorkServerUrl(boolean ipv6) throws ThreemaException {
		return getOnPremConfigFetcher().fetch().getWorkConfig().getUrl();
	}

	@Override
	public String getBlobServerDownloadUrl(boolean ipv6) throws ThreemaException {
		return getOnPremConfigFetcher().fetch().getBlobConfig().getDownloadUrl();
	}

	@Override
	public String getBlobServerDoneUrl(boolean ipv6) throws ThreemaException {
		return getOnPremConfigFetcher().fetch().getBlobConfig().getDoneUrl();
	}

	@Override
	public String getBlobServerUploadUrl(boolean ipv6) throws ThreemaException {
		return getOnPremConfigFetcher().fetch().getBlobConfig().getUploadUrl();
	}

	@Override
	public String getAvatarServerUrl(boolean ipv6) throws ThreemaException {
		return getOnPremConfigFetcher().fetch().getAvatarConfig().getUrl();
	}

	@Override
	public String getSafeServerUrl(boolean ipv6) throws ThreemaException {
		return getOnPremConfigFetcher().fetch().getSafeConfig().getUrl();
	}

	@Override
	public String getWebServerUrl() throws ThreemaException {
		return getOnPremConfigFetcher().fetch().getWebConfig().getUrl();
	}

	private OnPremConfigFetcher getOnPremConfigFetcher() throws ThreemaException {
		return fetcherProvider.getFetcher();
	}
}

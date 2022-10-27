/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2022 Threema GmbH
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

package ch.threema.domain.protocol;

import ch.threema.base.ThreemaException;

public interface ServerAddressProvider {
	String getChatServerNamePrefix(boolean ipv6) throws ThreemaException;
	String getChatServerNameSuffix(boolean ipv6) throws ThreemaException;
	int[] getChatServerPorts() throws ThreemaException;
	boolean getChatServerUseServerGroups() throws ThreemaException;
	byte[] getChatServerPublicKey() throws ThreemaException;
	byte[] getChatServerPublicKeyAlt() throws ThreemaException;

	String getDirectoryServerUrl(boolean ipv6) throws ThreemaException;

	String getWorkServerUrl(boolean ipv6) throws ThreemaException;

	String getBlobServerDownloadUrl(boolean ipv6) throws ThreemaException;
	String getBlobServerDoneUrl(boolean ipv6) throws ThreemaException;
	String getBlobServerUploadUrl(boolean ipv6) throws ThreemaException;

	String getAvatarServerUrl(boolean ipv6) throws ThreemaException;

	String getSafeServerUrl(boolean ipv6) throws ThreemaException;
	String getWebServerUrl() throws ThreemaException;
	String getWebOverrideSaltyRtcHost() throws ThreemaException;
	int getWebOverrideSaltyRtcPort() throws ThreemaException;
}

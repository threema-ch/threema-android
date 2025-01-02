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

package ch.threema.domain.protocol;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.base.ThreemaException;
import ch.threema.domain.protocol.blob.BlobScope;
import ch.threema.domain.protocol.connection.d2m.MultiDevicePropertyProvider;

public interface ServerAddressProvider {
    String getChatServerNamePrefix(boolean ipv6) throws ThreemaException;

    String getChatServerNameSuffix(boolean ipv6) throws ThreemaException;

    int[] getChatServerPorts() throws ThreemaException;

    boolean getChatServerUseServerGroups() throws ThreemaException;

    byte[] getChatServerPublicKey() throws ThreemaException;

    byte[] getChatServerPublicKeyAlt() throws ThreemaException;

    String getDirectoryServerUrl(boolean ipv6) throws ThreemaException;

    String getWorkServerUrl(boolean ipv6) throws ThreemaException;

    @NonNull
    String getBlobBaseUrlMirrorServer(
        @NonNull MultiDevicePropertyProvider multiDevicePropertyProvider
    ) throws ThreemaException;

    @NonNull
    String getBlobServerDownloadUrl(
        boolean useIpV6,
        @NonNull byte[] blobId
    ) throws ThreemaException;

    @NonNull
    String getBlobServerUploadUrl(boolean useIpV6) throws ThreemaException;

    @NonNull
    String getBlobServerDoneUrl(
        boolean useIpV6,
        @NonNull byte[] blobId
    ) throws ThreemaException;

    @NonNull
    String getBlobMirrorServerDownloadUrl(
        @NonNull MultiDevicePropertyProvider multiDevicePropertyProvider,
        @NonNull byte[] blobId
    ) throws ThreemaException, IllegalArgumentException;

    @NonNull
    String getBlobMirrorServerUploadUrl(
        @NonNull MultiDevicePropertyProvider multiDevicePropertyProvider
    ) throws ThreemaException;

    @NonNull
    String getBlobMirrorServerDoneUrl(
        @NonNull MultiDevicePropertyProvider multiDevicePropertyProvider,
        @NonNull byte[] blobId
    ) throws ThreemaException, IllegalArgumentException;

    String getAvatarServerUrl(boolean ipv6) throws ThreemaException;

    String getSafeServerUrl(boolean ipv6) throws ThreemaException;

    String getWebServerUrl() throws ThreemaException;

    String getWebOverrideSaltyRtcHost() throws ThreemaException;

    int getWebOverrideSaltyRtcPort() throws ThreemaException;

    @Nullable
    byte[] getThreemaPushPublicKey() throws ThreemaException;

    @NonNull
    String getMediatorUrl() throws ThreemaException;

    @NonNull
    String getAppRatingUrl() throws ThreemaException;
}

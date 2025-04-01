/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2025 Threema GmbH
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.BuildConfig;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.Utils;
import ch.threema.domain.onprem.OnPremConfigFetcher;
import ch.threema.domain.onprem.ServerAddressProviderOnPrem;
import ch.threema.domain.protocol.ServerAddressProvider;
import ch.threema.domain.protocol.connection.d2m.MultiDevicePropertyProvider;

public class ServerAddressProviderServiceImpl implements ServerAddressProviderService {
    private final PreferenceService preferenceService;
    private OnPremConfigFetcher onPremConfigFetcher;
    private URL lastOnPremServer;

    public ServerAddressProviderServiceImpl(PreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @Override
    @NonNull
    public ServerAddressProvider getServerAddressProvider() {
        if (ConfigUtils.isOnPremBuild()) {
            return getServerAddressProviderOnPrem();
        } else {
            return getServerAddressProviderBuildConfig();
        }
    }

    private ServerAddressProvider getServerAddressProviderOnPrem() {
        return new ServerAddressProviderOnPrem(this::getOnPremConfigFetcher);
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

            /**
             * @throws ThreemaException if the build config field for the correct base url is missing in current build flavor. This
             * is the case if this implementation is incorrectly used in an on-prem build.
             */
            @NonNull
            private String getBlobBaseUrlDefaultServer(boolean useIpV6, @NonNull byte[] blobId) throws ThreemaException {
                final @Nullable String blobIdHexString = Utils.byteArrayToHexString(blobId);
                if (blobIdHexString == null || blobIdHexString.isBlank()) {
                    throw new ThreemaException("Argument blobId is not in correct form");
                }
                final @NonNull String blobIdPrefix8 = blobIdHexString.substring(0, 2);
                final @Nullable String baseUrlRawValue = useIpV6 ? BuildConfig.BLOB_SERVER_IPV6_URL : BuildConfig.BLOB_SERVER_URL;
                // Could actually be null, if the build-config field was explicitly set to value "null" in build.gradle
                //noinspection ConstantValue
                if (baseUrlRawValue == null) {
                    throw new ThreemaException("Missing value for blob server url in current build flavor");
                }
                return baseUrlRawValue.replace("{blobIdPrefix8}", blobIdPrefix8);
            }

            /**
             * @throws ThreemaException if the build config field for the correct base url is missing in current build flavor. This
             * is the case if this implementation is incorrectly used in an on-prem build.
             */
            @NonNull
            @Override
            public String getBlobBaseUrlMirrorServer(@NonNull MultiDevicePropertyProvider multiDevicePropertyProvider) throws ThreemaException {
                final @NonNull byte[] deviceGroupId = multiDevicePropertyProvider.get().getKeys().getDgid$domain();
                final @Nullable String deviceGroupIdHexString = Utils.byteArrayToHexString(deviceGroupId);
                if (deviceGroupIdHexString == null || deviceGroupIdHexString.length() < 2) {
                    throw new ThreemaException("Key deviceGroupId is not in correct form");
                }
                final @NonNull String deviceGroupIdPrefix4 = deviceGroupIdHexString.substring(0, 1);
                final @NonNull String deviceGroupIdPrefix8 = deviceGroupIdHexString.substring(0, 2);
                // Could actually be null, if the build-config field was explicitly set to value "null" in build.gradle
                //noinspection ConstantValue
                if (BuildConfig.BLOB_MIRROR_SERVER_URL == null) {
                    throw new ThreemaException("Missing value for blob server url in current build flavor");
                }
                return BuildConfig.BLOB_MIRROR_SERVER_URL
                    .replace("{deviceGroupIdPrefix4}", deviceGroupIdPrefix4)
                    .replace("{deviceGroupIdPrefix8}", deviceGroupIdPrefix8);
            }

            @NonNull
            @Override
            public String getBlobServerDownloadUrl(boolean useIpV6, @NonNull byte[] blobId) throws ThreemaException {
                final @NonNull String blobBaseUrlDefaultServer = getBlobBaseUrlDefaultServer(useIpV6, blobId);
                final @Nullable String blobIdHexString = Utils.byteArrayToHexString(blobId);
                if (blobIdHexString == null || blobIdHexString.isBlank()) {
                    throw new ThreemaException("Argument blobId is not in correct form");
                }
                return blobBaseUrlDefaultServer + "/" + blobIdHexString;
            }

            @NonNull
            @Override
            public String getBlobServerUploadUrl(boolean useIpV6) {
                return useIpV6 ? BuildConfig.BLOB_SERVER_IPV6_URL_UPLOAD : BuildConfig.BLOB_SERVER_URL_UPLOAD;
            }

            @NonNull
            @Override
            public String getBlobServerDoneUrl(boolean useIpV6, @NonNull byte[] blobId) throws ThreemaException {
                final @NonNull String blobBaseUrlDefaultServer = getBlobBaseUrlDefaultServer(useIpV6, blobId);
                final @Nullable String blobIdHexString = Utils.byteArrayToHexString(blobId);
                if (blobIdHexString == null || blobIdHexString.isBlank()) {
                    throw new ThreemaException("Argument blobId is not in correct form");
                }
                return blobBaseUrlDefaultServer + "/" + blobIdHexString + "/done";
            }

            @NonNull
            @Override
            public String getBlobMirrorServerDownloadUrl(
                @NonNull MultiDevicePropertyProvider multiDevicePropertyProvider,
                @NonNull byte[] blobId
            ) throws ThreemaException {
                final @NonNull String blobBaseUrlMirrorServer = getBlobBaseUrlMirrorServer(multiDevicePropertyProvider);
                final @Nullable String blobIdHexString = Utils.byteArrayToHexString(blobId);
                if (blobIdHexString == null || blobIdHexString.isBlank()) {
                    throw new ThreemaException("Argument blobId is not in correct form");
                }
                return blobBaseUrlMirrorServer + "/" + blobIdHexString;
            }

            @NonNull
            @Override
            public String getBlobMirrorServerUploadUrl(
                @NonNull MultiDevicePropertyProvider multiDevicePropertyProvider
            ) throws ThreemaException {
                final @NonNull String blobBaseUrlMirrorServer = getBlobBaseUrlMirrorServer(multiDevicePropertyProvider);
                return blobBaseUrlMirrorServer + "/upload";
            }

            @NonNull
            @Override
            public String getBlobMirrorServerDoneUrl(
                @NonNull MultiDevicePropertyProvider multiDevicePropertyProvider,
                @NonNull byte[] blobId
            ) throws ThreemaException {
                final @NonNull String blobBaseUrlMirrorServer = getBlobBaseUrlMirrorServer(multiDevicePropertyProvider);
                final @Nullable String blobIdHexString = Utils.byteArrayToHexString(blobId);
                if (blobIdHexString == null || blobIdHexString.isBlank()) {
                    throw new ThreemaException("Argument blobId is not in correct form");
                }
                return blobBaseUrlMirrorServer + "/" + blobIdHexString + "/done";
            }

            @Override
            public String getAvatarServerUrl(boolean ipv6) {
                return BuildConfig.AVATAR_FETCH_URL;
            }

            @Override
            public String getSafeServerUrl(boolean ipv6) {
                return BuildConfig.SAFE_SERVER_URL;
            }

            @Override
            public String getWebServerUrl() {
                return BuildConfig.WEB_SERVER_URL;
            }

            @Override
            public String getWebOverrideSaltyRtcHost() {
                return null;
            }

            @Override
            public int getWebOverrideSaltyRtcPort() {
                return 0;
            }

            @Override
            public byte[] getThreemaPushPublicKey() {
                return BuildConfig.THREEMA_PUSH_PUBLIC_KEY;
            }

            @NonNull
            @Override
            public String getMediatorUrl() {
                return BuildConfig.MEDIATOR_SERVER_URL;
            }

            @NonNull
            @Override
            public String getAppRatingUrl() {
                return BuildConfig.APP_RATING_URL;
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

    private URL makeUrlWithUsernamePassword(@NonNull URL url, String username, String password) throws MalformedURLException {
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

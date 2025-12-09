/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2025 Threema GmbH
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

package ch.threema.app.voip.util;

import com.google.protobuf.ByteString;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.utils.RandomUtil;
import ch.threema.app.voip.signaling.ToSignalingMessage;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.protobuf.Common;
import ch.threema.protobuf.callsignaling.O2OCall;
import ch.threema.protobuf.callsignaling.O2OCall.VideoQualityProfile.QualityProfile;

/**
 * Manage video quality profiles.
 */
public class VoipVideoParams implements ToSignalingMessage {
    private static final Logger logger = getThreemaLogger("VoipVideoParams");

    private final @Nullable QualityProfile profile;
    private final int maxBitrateKbps;
    private final int maxFps;
    private final int maxWidth, maxHeight;

    public static final int MIN_BITRATE = 200;
    public static final int MIN_FPS = 15;
    public static final int MIN_WIDTH = 320;
    public static final int MIN_HEIGHT = 240;

    private VoipVideoParams(
        @Nullable QualityProfile profile,
        int maxBitrateKbps,
        int maxFps,
        int maxWidth,
        int maxHeight
    ) {
        this.profile = profile;
        this.maxBitrateKbps = maxBitrateKbps;
        this.maxFps = maxFps;
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
    }

    //region Getters

    @Override
    public int getType() {
        return O2OCall.Envelope.VIDEO_QUALITY_PROFILE_FIELD_NUMBER;
    }

    /**
     * Return the profile name.
     */
    @Nullable
    public QualityProfile getProfile() {
        return profile;
    }

    /**
     * Return the max allowed bitrate in kbps.
     */
    public int getMaxBitrateKbps() {
        return this.maxBitrateKbps;
    }

    /**
     * Return the max framerate.
     */
    public int getMaxFps() {
        return this.maxFps;
    }

    /**
     * Return the max width.
     */
    public int getMaxWidth() {
        return this.maxWidth;
    }

    /**
     * Return the max width.
     */
    public int getMaxHeight() {
        return this.maxHeight;
    }

    //endregion

    @NonNull
    @Override
    public String toString() {
        return "VoipVideoParams{" +
            "profile=" + profile +
            ", " + maxBitrateKbps + "kbps" +
            ", " + maxFps + "fps" +
            ", " + maxWidth + "x" + maxHeight +
            '}';
    }

    //region Public constructors

    /**
     * Low bitrate profile:
     * <p>
     * - 400 kbps bitrate
     * - 20 FPS
     * - 960x540 px resolution
     */
    public static VoipVideoParams low() {
        return new VoipVideoParams(QualityProfile.LOW, 400, 20, 960, 540);
    }

    /**
     * High bitrate profile:
     * <p>
     * - 2000 kbps bitrate
     * - 25 FPS
     * - 1280x720 px resolution
     */
    public static VoipVideoParams high() {
        return new VoipVideoParams(QualityProfile.HIGH, 2000, 25, 1280, 720);
    }

    /**
     * Highest bitrate profile:
     * <p>
     * - 4000 kbps bitrate
     * - 25 FPS
     * - 1920x1080 px resolution
     */
    public static VoipVideoParams max() {
        return new VoipVideoParams(QualityProfile.MAX, 4000, 25, 1920, 1080);
    }

    /**
     * Create a VoipVideoParams object from a string setting, depending on the current metering state.
     */
    public static @NonNull VoipVideoParams getParamsFromSetting(
        @Nullable String setting,
        @Nullable Boolean isMetered
    ) {
        if (setting != null) {
            switch (setting) {
                case "0": // AUTO
                    if (isMetered != null && isMetered) {
                        return VoipVideoParams.low();
                    } else {
                        return VoipVideoParams.high();
                    }
                case "1": // LOW BANDWIDTH
                    return VoipVideoParams.low();
                case "2": // HIGHEST QUALITY
                    return VoipVideoParams.max();
            }
        }
        // DEFAULT
        return VoipVideoParams.high();
    }

    //endregion

    //region Negotiation

    /**
     * When the peer sends a profile, pick the lower quality settings of the two.
     * <p>
     * Note: If both parameters specify a named profile, then the lower of the two profiles
     * is initialized with the current default values. Only if one or both profiles are
     * non-named (unknown), then the actual values are being considered.
     * <p>
     * When comparing the raw values, these are clamped on the lower end to the LOW profile.
     * <p>
     * The MAX profile is only selected if the network is not relayed.
     * <p>
     * If {@param peerParams} is null, return the current profile.
     *
     * @throws RuntimeException if a common profile could not be determined.
     *                          This indicates a bug in the implementation.
     */
    public @NonNull VoipVideoParams findCommonProfile(
        @Nullable VoipVideoParams peerParams,
        @Nullable Boolean networkIsRelayed
    ) throws RuntimeException {
        if (peerParams == null) {
            return this;
        }
        logger.debug("findCommonProfile: this={} peer={} relayed={}", this.profile, peerParams.profile, networkIsRelayed);
        if (this.profile == null || this.profile == QualityProfile.UNRECOGNIZED ||
            peerParams.getProfile() == null || peerParams.getProfile() == QualityProfile.UNRECOGNIZED) {
            return new VoipVideoParams(
                null,
                Math.max(Math.min(this.maxBitrateKbps, peerParams.getMaxBitrateKbps()), MIN_BITRATE),
                Math.max(Math.min(this.maxFps, peerParams.getMaxFps()), MIN_FPS),
                Math.max(Math.min(this.maxWidth, peerParams.getMaxWidth()), MIN_WIDTH),
                Math.max(Math.min(this.maxHeight, peerParams.getMaxHeight()), MIN_HEIGHT)
            );
        } else if (this.profile == QualityProfile.LOW || peerParams.profile == QualityProfile.LOW) {
            return VoipVideoParams.low();
        } else if (this.profile == QualityProfile.HIGH || peerParams.profile == QualityProfile.HIGH) {
            return VoipVideoParams.high();
        } else if (this.profile == QualityProfile.MAX || peerParams.profile == QualityProfile.MAX) {
            // Prevent MAX profile if relay is being used
            return Boolean.TRUE.equals(networkIsRelayed)
                ? VoipVideoParams.high()
                : VoipVideoParams.max();
        } else {
            throw new RuntimeException("Cannot find common profile");
        }
    }

    //endregion

    //region Protocol buffers

    public static @Nullable VoipVideoParams fromSignalingMessage(
        @NonNull O2OCall.VideoQualityProfile profile
    ) {
        switch (profile.getProfile()) {
            case LOW:
                return VoipVideoParams.low();
            case HIGH:
                return VoipVideoParams.high();
            case MAX:
                return VoipVideoParams.max();
        }
        logger.warn("Unknown video profile: {} ({})", profile.getProfile(), profile.getProfileValue());

        // Fall back to raw values.
        // Validate them.
        final int maxBitrate = profile.getMaxBitrateKbps();
        if (maxBitrate == 0) {
            logger.warn("Received message with 0 maxBitrate");
            return null;
        }
        final int maxFps = profile.getMaxFps();
        if (maxFps == 0) {
            logger.warn("Received message with 0 maxFps");
            return null;
        }
        if (!profile.hasMaxResolution()) {
            logger.warn("Received message without max resolution");
            return null;
        }
        final Common.Resolution resolution = profile.getMaxResolution();
        if (resolution.getWidth() == 0 || resolution.getHeight() == 0) {
            logger.warn("Received message with 0 width or height");
            return null;
        }

        return new VoipVideoParams(
            QualityProfile.UNRECOGNIZED,
            maxBitrate,
            maxFps,
            resolution.getWidth(),
            resolution.getHeight()
        );
    }

    @Override
    public @NonNull O2OCall.Envelope toSignalingMessage() {
        final Common.Resolution.Builder resolution = Common.Resolution.newBuilder()
            .setWidth(this.maxWidth)
            .setHeight(this.maxHeight);
        final O2OCall.VideoQualityProfile.Builder profile = O2OCall.VideoQualityProfile.newBuilder()
            .setProfile(this.profile)
            .setMaxBitrateKbps(this.maxBitrateKbps)
            .setMaxFps(this.maxFps)
            .setMaxResolution(resolution);
        return O2OCall.Envelope.newBuilder()
            .setPadding(ByteString.copyFrom(RandomUtil.generateRandomPadding(0, 255)))
            .setVideoQualityProfile(profile)
            .build();
    }

    //endregion
}

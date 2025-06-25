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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ch.threema.app.utils.LogUtil;

public class SdpPatcher {
    // Regex patterns
    private static final Pattern SDP_MEDIA_AUDIO_ANY_RE =
        Pattern.compile("m=audio ([^ ]+) ([^ ]+) (.+)");
    private static final Pattern SDP_RTPMAP_OPUS_RE =
        Pattern.compile("a=rtpmap:([^ ]+) opus.*");
    private static final Pattern SDP_RTPMAP_ANY_RE =
        Pattern.compile("a=rtpmap:([^ ]+) .*");
    private static final Pattern SDP_FMTP_ANY_RE =
        Pattern.compile("a=fmtp:([^ ]+) ([^ ]+)");
    private static final Pattern SDP_EXTMAP_ANY_RE =
        Pattern.compile("a=extmap:[^ ]+ (.*)");

    /**
     * Whether this SDP is a local offer, a local answer
     * or a remote SDP.
     */
    public enum Type {
        REMOTE_OFFER,
        REMOTE_ANSWER,
        LOCAL_OFFER,
        LOCAL_ANSWER,
    }

    /**
     * RTP header extension configuration.
     */
    public enum RtpHeaderExtensionConfig {
        DISABLE,
        ENABLE_WITH_LEGACY_ONE_BYTE_HEADER_ONLY,
        ENABLE_WITH_ONE_AND_TWO_BYTE_HEADER,
    }

    /**
     * The SDP is invalid (for our cases, at least).
     */
    public static class InvalidSdpException extends Exception {
        InvalidSdpException(@NonNull final String description) {
            super(description);
        }
    }

    // RTP extensions of the remote offer to be replayed in the local answer
    private @NonNull final RtpExtensionReplayer rtpExtensionsInRemoteOffer = new RtpExtensionReplayer();

    // Configuration fields
    private @NonNull Logger logger = LogUtil.NULL_LOGGER;
    private @NonNull RtpHeaderExtensionConfig rtpHeaderExtensionConfig = RtpHeaderExtensionConfig.DISABLE;

    /**
     * Set a logger instance.
     */
    public SdpPatcher withLogger(final Logger logger) {
        this.logger = logger;
        return this;
    }

    /**
     * Set whether RTP header extensions should be enabled and in which mode.
     */
    public SdpPatcher withRtpHeaderExtensions(
        @NonNull final RtpHeaderExtensionConfig config
    ) {
        this.rtpHeaderExtensionConfig = config;
        return this;
    }

    /**
     * Patch an SDP offer / answer with a few things that we want to enforce in Threema:
     * <p>
     * For all media lines:
     * <p>
     * - Remove audio level and frame marking header extensions
     * - Remap extmap IDs (when offering)
     * <p>
     * For audio in specific:
     * <p>
     * - Only support Opus, remove all other codecs
     * - Force CBR
     * <p>
     * The use of CBR (constant bit rate) will also suppress VAD (voice activity detection). For
     * more security considerations regarding codec configuration, see RFC 6562:
     * https://tools.ietf.org/html/rfc6562
     * <p>
     * Return the updated session description.
     */
    public @NonNull String patch(
        @NonNull final Type type,
        @NonNull final String sdp
    ) throws InvalidSdpException, IOException {
        // First, find RTP payload type number for Opus codec
        final Matcher matcher = SDP_RTPMAP_OPUS_RE.matcher(sdp);
        final String payloadTypeOpus;
        if (matcher.find()) {
            payloadTypeOpus = Objects.requireNonNull(matcher.group(1));
        } else {
            throw new SdpPatcher.InvalidSdpException("a=rtpmap: [...] opus not found");
        }

        // Create context
        final SdpPatcherContext context = new SdpPatcherContext(type, this, payloadTypeOpus);

        // Iterate over all lines
        final StringBuilder lines = new StringBuilder();
        final BufferedReader reader = new BufferedReader(new StringReader(sdp));
        String line;
        while ((line = reader.readLine()) != null) {
            SdpPatcher.handleLine(context, reader, lines, new Line(line));
        }

        // Done, return lines
        return lines.toString();
    }

    /**
     * SDP section type.
     */
    private enum SdpSection {
        GLOBAL(false),
        MEDIA_AUDIO(true),
        MEDIA_VIDEO(true),
        MEDIA_DATA_CHANNEL(false),
        MEDIA_UNKNOWN(false);

        public final boolean isRtpSection;

        SdpSection(final boolean isRtpSection) {
            this.isRtpSection = isRtpSection;
        }
    }

    /**
     * Whether the line is to be accepted, rejected or rewritten.
     */
    private enum LineAction {
        ACCEPT,
        REJECT,
        REWRITE,
    }

    /**
     * An SDP line to accept, reject or rewrite.
     */
    private static class Line {
        private @NonNull String line;
        private @Nullable LineAction action;

        Line(@NonNull final String line) {
            this.line = line;
        }

        @NonNull
        LineAction accept() {
            if (this.action != null) {
                throw new IllegalArgumentException("LineAction.action already set");
            }
            this.action = LineAction.ACCEPT;
            return this.action;
        }

        @NonNull LineAction reject() {
            if (this.action != null) {
                throw new IllegalArgumentException("LineAction.action already set");
            }
            this.action = LineAction.REJECT;
            return this.action;
        }

        @NonNull LineAction rewrite(@NonNull final String line) {
            if (this.action != null) {
                throw new IllegalArgumentException("LineAction.action already set");
            }
            this.action = LineAction.REWRITE;
            this.line = line;
            return this.action;
        }

        @NonNull LineAction rewrite(@NonNull final StringBuilder builder) {
            return this.rewrite(builder.toString());
        }
    }


    /**
     * RTP extension replayer. For replaying remote offer extensions in a local answer.
     */
    private static class RtpExtensionReplayer {
        private enum MediaSection {
            AUDIO,
            VIDEO,
        }

        static @Nullable MediaSection fromSdpSection(final @NonNull SdpSection section) {
            switch (section) {
                case MEDIA_AUDIO:
                    return MediaSection.AUDIO;
                case MEDIA_VIDEO:
                    return MediaSection.VIDEO;
                default:
                    return null;
            }
        }

        private final @NonNull Map<MediaSection, List<String>> extensions = new HashMap<>();

        void add(final @NonNull MediaSection section, final @NonNull String line) {
            this.extensions.computeIfAbsent(section, mediaSection -> new ArrayList<>()).add(line);
        }

        @NonNull List<String> replay(final @NonNull MediaSection section) {
            final List<String> lines = this.extensions.remove(section);
            if (lines == null) {
                return new ArrayList<>();
            }
            return lines;
        }
    }

    /**
     * RTP extension ID remapper.
     */
    private static class RtpExtensionIdRemapper {
        private int currentId;
        private final int maxId;
        private final @NonNull Map<String, Integer> extensionIdMap = new HashMap<>();

        private RtpExtensionIdRemapper(@NonNull final SdpPatcher config) {
            // See: RFC 5285 sec 4.2, sec 4.3
            this.currentId = 1;
            switch (config.rtpHeaderExtensionConfig) {
                case ENABLE_WITH_LEGACY_ONE_BYTE_HEADER_ONLY:
                    this.maxId = 14;
                    break;
                case ENABLE_WITH_ONE_AND_TWO_BYTE_HEADER:
                    this.maxId = 255;
                    break;
                default:
                    this.maxId = 0;
                    break;
            }
        }

        int assignId(final String uriAndAttributes) throws InvalidSdpException {
            // It is extremely important that we give extensions with the same URI the same ID
            // across different media sections, otherwise the bundling mechanism will fail and we
            // get all sorts of weird behaviour from the WebRTC stack.
            Integer id = this.extensionIdMap.get(uriAndAttributes);
            if (id == null) {
                // Check if exhausted
                if (this.currentId > this.maxId) {
                    throw new InvalidSdpException("RTP extension IDs exhausted");
                }

                // Assign an ID
                id = this.currentId++;
                // Let's just skip 15 to be safe, see: RFC 5285 sec 4.2
                if (this.currentId == 15) {
                    ++this.currentId;
                }

                // Store URI and assigned ID
                this.extensionIdMap.put(uriAndAttributes, id);
            }
            return id;
        }
    }

    /**
     * SDP patcher context storage.
     */
    private static class SdpPatcherContext {
        private @NonNull final Type type;
        private @NonNull final SdpPatcher config;
        private @NonNull final String payloadTypeOpus;
        private @NonNull final RtpExtensionIdRemapper rtpExtensionsInLocalOfferIdRemapper;
        private @NonNull SdpSection section;

        private SdpPatcherContext(
            @NonNull final Type type,
            @NonNull final SdpPatcher config,
            @NonNull final String payloadTypeOpus
        ) {
            this.type = type;
            this.config = config;
            this.payloadTypeOpus = payloadTypeOpus;
            this.rtpExtensionsInLocalOfferIdRemapper = new RtpExtensionIdRemapper(config);
            this.section = SdpSection.GLOBAL;
        }
    }

    /**
     * Handle an SDP line.
     */
    private static void handleLine(
        @NonNull final SdpPatcherContext context,
        @NonNull final BufferedReader reader,
        @NonNull final StringBuilder lines,
        @NonNull final Line line
    ) throws InvalidSdpException, IOException {
        final SdpSection current = context.section;
        final LineAction action;

        // Introduce a new section or forward depending on the section type
        if (line.line.startsWith("m=")) {
            action = SdpPatcher.handleSectionLine(context, line);
        } else {
            switch (context.section) {
                case GLOBAL:
                    action = SdpPatcher.handleGlobalLine(context, line);
                    break;
                case MEDIA_AUDIO:
                    action = SdpPatcher.handleAudioLine(context, line);
                    break;
                case MEDIA_VIDEO:
                    action = SdpPatcher.handleVideoLine(context, line);
                    break;
                case MEDIA_DATA_CHANNEL:
                    action = SdpPatcher.handleDataChannelLine(line);
                    break;
                default:
                    // Note: This also swallows `MEDIA_UNKNOWN`. Since we reject these lines completely,
                    //       a line within that section should never be parsed.
                    throw new InvalidSdpException(String.format(Locale.US, "Unknown section %s", current));
            }
        }

        // Execute line action
        switch (action) {
            case ACCEPT: // fallthrough
            case REWRITE:
                lines
                    .append(line.line)
                    .append("\r\n");
                break;
            case REJECT:
                // Log
                if (context.config.logger.isDebugEnabled()) {
                    context.config.logger.debug("Rejected line: {}", line.line);
                }
                break;
            default:
                throw new IllegalArgumentException(String.format(Locale.US, "Unknown line action %s", action));
        }

        // If we have switched to another section and the line has been rejected,
        // we need to reject the remainder of the section.
        if (current != context.section && action == LineAction.REJECT) {
            final StringBuilder debug = context.config.logger.isDebugEnabled() ? new StringBuilder() : null;
            String rejectedLine;
            while ((rejectedLine = reader.readLine()) != null && !rejectedLine.startsWith("m=")) {
                if (debug != null) {
                    debug.append(rejectedLine);
                }
            }
            if (debug != null) {
                context.config.logger.debug("Rejected section:\n{}", debug);
            }
            if (rejectedLine != null) {
                // Since we've already read the beginning of the section and can't push it back to
                // the reader, we need to handle it here.
                SdpPatcher.handleLine(context, reader, lines, new Line(rejectedLine));
            }
        }
    }

    /**
     * Handle a section line.
     */
    private static @NonNull LineAction handleSectionLine(
        @NonNull final SdpPatcherContext context,
        @NonNull final Line line
    ) throws InvalidSdpException {
        final Matcher matcher;

        // Audio section
        if ((matcher = SDP_MEDIA_AUDIO_ANY_RE.matcher(line.line)).matches()) {
            // Mark current section
            context.section = SdpSection.MEDIA_AUDIO;

            // Parse media description line
            final String port = Objects.requireNonNull(matcher.group(1));
            final String proto = Objects.requireNonNull(matcher.group(2));
            final String payloadTypes = Objects.requireNonNull(matcher.group(3));

            // Make sure that the Opus payload type is contained here
            if (!Arrays.asList(payloadTypes.split(" ")).contains(context.payloadTypeOpus)) {
                throw new InvalidSdpException(String.format(
                    Locale.US,
                    "Opus payload type (%s) not found in audio media description",
                    context.payloadTypeOpus
                ));
            }

            // Rewrite with only the payload types that we want
            return line.rewrite(String.format(Locale.US, "m=audio %s %s %s", port, proto, context.payloadTypeOpus));
        }

        // Video section
        if (line.line.startsWith("m=video")) {
            // Accept
            context.section = SdpSection.MEDIA_VIDEO;
            return line.accept();
        }

        // Data channel section
        if (line.line.startsWith("m=application") && line.line.contains("DTLS/SCTP")) {
            // Accept
            context.section = SdpSection.MEDIA_DATA_CHANNEL;
            return line.accept();
        }

        // Unknown section (reject)
        context.section = SdpSection.MEDIA_UNKNOWN;
        return line.reject();
    }

    /**
     * Handle global (non-media) section line.
     */
    private static @NonNull LineAction handleGlobalLine(
        @NonNull final SdpPatcherContext context,
        @NonNull final Line line
    ) {
        return SdpPatcher.handleRtpAttributes(context, line);
    }

    /**
     * Handle RTP attributes shared across global (non-media) and media sections.
     */
    private static @NonNull LineAction handleRtpAttributes(
        @NonNull final SdpPatcherContext context,
        @NonNull final Line line
    ) {
        // Reject one-/two-byte RTP header mixed mode, if requested
        if (
            context.config.rtpHeaderExtensionConfig != RtpHeaderExtensionConfig.ENABLE_WITH_ONE_AND_TWO_BYTE_HEADER &&
                line.line.startsWith("a=extmap-allow-mixed")
        ) {
            return line.reject();
        }

        // Accept the rest
        return line.accept();
    }

    /**
     * Handle audio section line.
     */
    private static @NonNull LineAction handleAudioLine(
        @NonNull final SdpPatcherContext context,
        @NonNull final Line line
    ) throws InvalidSdpException {
        Matcher matcher;

        // RTP mappings
        if ((matcher = SDP_RTPMAP_ANY_RE.matcher(line.line)).matches()) {
            final String payloadType = Objects.requireNonNull(matcher.group(1));

            // Accept Opus RTP mappings, reject the rest
            if (payloadType.equals(context.payloadTypeOpus)) {
                return line.accept();
            } else {
                return line.reject();
            }
        }

        // RTP format parameters
        if ((matcher = SDP_FMTP_ANY_RE.matcher(line.line)).matches()) {
            final String payloadType = Objects.requireNonNull(matcher.group(1));
            final String paramString = Objects.requireNonNull(matcher.group(2));
            if (!payloadType.equals(context.payloadTypeOpus)) {
                // Reject non-opus RTP format parameters
                return line.reject();
            }

            // Split parameters
            final String[] params = paramString.split(";");

            // Specify what params we want to change
            final Set<String> paramUpdates = new HashSet<>();
            paramUpdates.add("stereo");
            paramUpdates.add("sprop-stereo");
            paramUpdates.add("cbr");

            // Write unchanged params
            StringBuilder builder = new StringBuilder();
            builder.append("a=fmtp:");
            builder.append(context.payloadTypeOpus);
            builder.append(" ");
            for (String param : params) {
                final String key = param.split("=")[0];
                if (!param.isEmpty() && !paramUpdates.contains(key)) {
                    builder.append(param);
                    builder.append(";");
                }
            }

            // Write our custom params
            builder.append("stereo=0;sprop-stereo=0;cbr=1");

            return line.rewrite(builder);
        }

        // For local answers, inject the accepted remote offer RTP header extensions after the MID
        // in a hacky ugly AF way
        if (context.type == Type.LOCAL_ANSWER && line.line.startsWith("a=mid:")) {
            return SdpPatcher.handleRtpHeaderExtensionInjectionAfterMid(context, line);
        }

        // Handle RTP header extensions
        if ((matcher = SDP_EXTMAP_ANY_RE.matcher(line.line)).matches()) {
            final String uriAndAttributes = Objects.requireNonNull(matcher.group(1));
            return SdpPatcher.handleRtpHeaderExtensionLine(context, line, uriAndAttributes);
        }

        // Handle further common cases
        return SdpPatcher.handleRtpAttributes(context, line);
    }

    /**
     * Handle video section line.
     */
    private static @NonNull LineAction handleVideoLine(
        @NonNull final SdpPatcherContext context,
        @NonNull final Line line
    ) throws InvalidSdpException {
        Matcher matcher;

        // For local answers, inject the accepted remote offer RTP header extensions after the MID
        // in a hacky ugly AF way
        if (context.type == Type.LOCAL_ANSWER && line.line.startsWith("a=mid:")) {
            return SdpPatcher.handleRtpHeaderExtensionInjectionAfterMid(context, line);
        }

        // Handle RTP header extensions
        if ((matcher = SDP_EXTMAP_ANY_RE.matcher(line.line)).matches()) {
            final String uriAndAttributes = Objects.requireNonNull(matcher.group(1));
            return SdpPatcher.handleRtpHeaderExtensionLine(context, line, uriAndAttributes);
        }

        // Handle further common cases
        return SdpPatcher.handleRtpAttributes(context, line);
    }

    /**
     * Handle data channel section line.
     */
    private static @NonNull LineAction handleDataChannelLine(
        @NonNull final Line line
    ) {
        // Data channel <3
        return line.accept();
    }

    private static @NonNull LineAction handleRtpHeaderExtensionLine(
        @NonNull final SdpPatcherContext context,
        @NonNull final Line line,
        @NonNull final String uriAndAttributes
    ) throws InvalidSdpException {
        // Always reject if disabled
        if (context.config.rtpHeaderExtensionConfig == RtpHeaderExtensionConfig.DISABLE) {
            return line.reject();
        }

        // Always reject some of the header extensions
        if (
            // Audio level, only useful for SFU use cases, remove
            uriAndAttributes.contains("urn:ietf:params:rtp-hdrext:ssrc-audio-level") ||
                uriAndAttributes.contains("urn:ietf:params:rtp-hdrext:csrc-audio-level") ||
                // Frame marking, only useful for SFU use cases, remove
                uriAndAttributes.contains("http://tools.ietf.org/html/draft-ietf-avtext-framemarking-07")
        ) {
            return line.reject();
        }

        switch (context.type) {
            // Only allow encrypted extensions and remember them so they can be copied into the local answer
            case REMOTE_OFFER:
                if (!uriAndAttributes.startsWith("urn:ietf:params:rtp-hdrext:encrypt")) {
                    return line.reject();
                }
                final RtpExtensionReplayer.MediaSection section = RtpExtensionReplayer.fromSdpSection(context.section);
                if (section != null) {
                    context.config.rtpExtensionsInRemoteOffer.add(section, line.line);
                }
                return line.accept();

            // Only allow encrypted extensions
            case REMOTE_ANSWER:
                if (!uriAndAttributes.startsWith("urn:ietf:params:rtp-hdrext:encrypt")) {
                    return line.reject();
                }
                return line.accept();

            // Remap and enforce encrypted extensions
            case LOCAL_OFFER:
                if (uriAndAttributes.startsWith("urn:ietf:params:rtp-hdrext:encrypt")) {
                    // We don't expect any encrypted extensions since they are no longer generated
                    // by default. Not having to handle duplicates makes this code simpler.
                    return line.reject();
                }
                return line.rewrite(String.format(
                    Locale.US,
                    "a=extmap:%d urn:ietf:params:rtp-hdrext:encrypt %s",
                    context.rtpExtensionsInLocalOfferIdRemapper.assignId(uriAndAttributes),
                    uriAndAttributes
                ));

            // We don't expect any extensions since all encrypted header extensions are stripped
            // by the SDP engine, because Google decided so.
            case LOCAL_ANSWER:
                return line.reject();

            default:
                return line.reject();
        }
    }

    private static LineAction handleRtpHeaderExtensionInjectionAfterMid(
        @NonNull SdpPatcherContext context,
        @NonNull Line line
    ) {
        final RtpExtensionReplayer.MediaSection section = RtpExtensionReplayer.fromSdpSection(context.section);
        if (section == null) {
            return line.accept();
        }
        final List<String> replayedRtpExtensions = context.config.rtpExtensionsInRemoteOffer.replay(section);
        replayedRtpExtensions.add(0, line.line);
        return line.rewrite(String.join("\r\n", replayedRtpExtensions));
    }
}

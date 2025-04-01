/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2024 Threema GmbH
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

import android.annotation.SuppressLint;

import org.webrtc.MediaStreamTrack;
import org.webrtc.RTCStats;
import org.webrtc.RTCStatsReport;
import org.webrtc.RtpParameters;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java8.util.stream.Collectors;
import java8.util.stream.StreamSupport;

/**
 * Parse the StatsReport returned by the WebRTC peer connection.
 */
public class VoipStats {
    /**
     * State for comparing with a subsequent stats iteration.
     */
    public static class State {
        double timestampUs;
        @Nullable
        BigInteger videoBytesSent;
        @Nullable
        BigInteger videoBytesReceived;

        /**
         * Create a new state.
         *
         * @param timestampUs    Report timestamp in microseconds
         * @param videoBytesSent Number of total bytes sent
         */
        public State(
            double timestampUs,
            @Nullable BigInteger videoBytesSent,
            @Nullable BigInteger videoBytesReceived
        ) {
            this.timestampUs = timestampUs;
            this.videoBytesSent = videoBytesSent;
            this.videoBytesReceived = videoBytesReceived;
        }
    }

    /**
     * State context passed around for comparing the current with a previous
     * state.
     */
    private static class StateContext {
        @Nullable
        final State previousState;
        double timestampUs;

        StateContext(@Nullable State previousState, double timestampUs) {
            this.previousState = previousState;
            this.timestampUs = timestampUs;
        }
    }

    public static class CandidatePairVariant {
        public static final int NONE = 0x00;
        public static final int OVERVIEW = 0x01;
        public static final int DETAILED = 0x02;
        public static final int OVERVIEW_AND_DETAILED = OVERVIEW | DETAILED;

        @IntDef({NONE, OVERVIEW, DETAILED, OVERVIEW_AND_DETAILED})
        public @interface Type {
        }
    }

    public static class Builder {
        private boolean selectedCandidatePair = false;
        private boolean transport = false;
        private boolean crypto = false;
        private boolean rtp = false;
        private boolean tracks = false;
        private boolean codecs = false;
        private @CandidatePairVariant.Type int candidatePairsFlag = CandidatePairVariant.NONE;

        public @NonNull Builder withSelectedCandidatePair(boolean on) {
            this.selectedCandidatePair = on;
            return this;
        }

        public @NonNull Builder withTransport(boolean on) {
            this.transport = on;
            return this;
        }

        public @NonNull Builder withCrypto(boolean on) {
            this.crypto = on;
            return this;
        }

        /**
         * Enable stats for both inbound and outbound RTP.
         */
        public @NonNull Builder withRtp(boolean on) {
            this.rtp = on;
            return this;
        }

        /**
         * Enable stats for both inbound and outbound tracks.
         */
        public @NonNull Builder withTracks(boolean on) {
            this.tracks = on;
            return this;
        }

        public @NonNull Builder withCodecs(boolean on) {
            this.codecs = on;
            return this;
        }

        public @NonNull Builder withCandidatePairs(@CandidatePairVariant.Type int variant) {
            this.candidatePairsFlag = variant;
            return this;
        }

        public @NonNull Extractor extractor() {
            return new Extractor(this);
        }
    }

    public static class Extractor {
        private @NonNull
        final Builder builder;
        private @Nullable List<org.webrtc.RtpTransceiver> rtpTransceivers;
        private @Nullable State previousState;

        private Extractor(@NonNull Builder builder) {
            this.builder = builder;
        }

        /**
         * Enable printing RTP transceiver data.
         */
        public @NonNull Extractor withRtpTransceivers(@NonNull List<org.webrtc.RtpTransceiver> rtpTransceivers) {
            this.rtpTransceivers = rtpTransceivers;
            return this;
        }

        /**
         * Show the output compared to a previous state.
         */
        public @NonNull Extractor comparedTo(@NonNull State previousState) {
            this.previousState = previousState;
            return this;
        }

        public @NonNull VoipStats extract(@NonNull RTCStatsReport report) {
            return new VoipStats(this, report);
        }
    }

    //region Representation classes

    private interface StatsRepresentation {
        void addShortRepresentation(@NonNull StringBuilder builder);

        void addRepresentation(@NonNull StringBuilder builder);
    }

    public static class BytesTransferred implements VoipStats.StatsRepresentation {
        @Nullable
        final public BigInteger sent;
        @Nullable
        final public BigInteger received;

        private BytesTransferred(@NonNull Map<String, Object> members) {
            this.sent = VoipStats.tryGetBigInt(members, "bytesSent", null);
            this.received = VoipStats.tryGetBigInt(members, "bytesReceived", null);
        }

        @Override
        public void addShortRepresentation(@NonNull StringBuilder builder) {
            builder.append("tx=");
            builder.append(humanReadableByteCount(this.sent));
            builder.append(" rx=");
            builder.append(humanReadableByteCount(this.received));
        }

        @Override
        public void addRepresentation(@NonNull StringBuilder builder) {
            builder.append("tx=");
            builder.append(humanReadableByteCount(this.sent));
            builder.append(", rx=");
            builder.append(humanReadableByteCount(this.received));
        }
    }

    public static class RoundTripTime implements VoipStats.StatsRepresentation {
        @Nullable
        final public Double latest;
        @Nullable
        final public Double average;

        private RoundTripTime(@NonNull Map<String, Object> members) {
            // Total RTT
            this.latest = VoipStats.tryGetDouble(members, "currentRoundTripTime", null);

            // Calculate average RTT (if possible)
            Double average = null;
            final Double totalRoundTripTime = VoipStats.tryGetDouble(members, "totalRoundTripTime", null);
            final BigInteger responsesReceivedBigInt = VoipStats.tryGetBigInt(members, "responsesReceived", null);
            if (totalRoundTripTime != null && responsesReceivedBigInt != null && responsesReceivedBigInt.signum() == 1) {
                final BigDecimal responsesReceivedBigDec = new BigDecimal(responsesReceivedBigInt);
                average = BigDecimal.valueOf(totalRoundTripTime)
                    .divide(responsesReceivedBigDec, RoundingMode.HALF_UP)
                    .doubleValue();
            }
            this.average = average;
        }

        @Override
        public void addShortRepresentation(@NonNull StringBuilder builder) {
            builder.append("rtt-latest=");
            builder.append(this.latest != null ? this.latest : "n/a");
            builder.append(" rtt-avg=");
            builder.append(this.average != null ? this.average : "n/a");
        }

        @Override
        public void addRepresentation(@NonNull StringBuilder builder) {
            builder.append("rtt-latest=");
            builder.append(this.latest != null ? this.latest : "n/a");
            builder.append(", rtt-average=");
            builder.append(this.average != null ? this.average : "n/a");
        }
    }

    public static class Candidate implements VoipStats.StatsRepresentation {
        @Nullable
        final public String address;
        @Nullable
        final public String type;
        @Nullable
        final public String protocol;
        @Nullable
        final public String network;

        private Candidate(@NonNull Map<String, Object> members) {
            this.address = VoipStats.tryGetString(members, "ip", null);
            this.type = VoipStats.tryGetString(members, "candidateType", null);
            this.protocol = VoipStats.tryGetString(members, "protocol", null);
            this.network = VoipStats.tryGetString(members, "networkType", null);
        }

        @Override
        public void addShortRepresentation(@NonNull StringBuilder builder) {
            builder.append(this.address);
            builder.append(" ");
            builder.append(this.type);
            builder.append(" ");
            builder.append(this.protocol);
            if (this.network != null) {
                builder.append(" ");
                builder.append(this.network);
            }
        }

        @Override
        public void addRepresentation(@NonNull StringBuilder builder) {
            builder.append("address=");
            builder.append(this.address);
            builder.append(", type=");
            builder.append(this.type);
            builder.append(", protocol=");
            builder.append(this.protocol);
            if (this.network != null) {
                builder.append(", network=");
                builder.append(this.network);
            }
        }
    }

    public static class CandidatePair implements VoipStats.StatsRepresentation {
        private static final BigInteger CANDIDATE_PAIR_DEFAULT_PRIORITY = new BigInteger("0");

        @NonNull
        final public String id;
        @NonNull
        final public BigInteger priority;
        @Nullable
        final public Candidate local;
        @Nullable
        final public Candidate remote;
        @Nullable
        final public Boolean nominated;
        @Nullable
        final public String state;
        @NonNull
        final public VoipStats.BytesTransferred bytesTransferred;
        @NonNull
        final public VoipStats.RoundTripTime roundTripTime;
        @Nullable
        final public Double availableOutgoingBitrate;
        final public boolean usesRelay;

        private CandidatePair(@NonNull Map<String, RTCStats> stats, @NonNull RTCStats entry) {
            this(stats, entry, entry.getMembers());
        }

        private CandidatePair(@NonNull Map<String, RTCStats> stats, @NonNull RTCStats entry, @NonNull Map<String, Object> members) {
            // Id
            this.id = VoipStats.getCandidatePairId(entry.getId());

            // Priority
            this.priority = Objects.requireNonNull(VoipStats.tryGetBigInt(members, "priority", CANDIDATE_PAIR_DEFAULT_PRIORITY));

            // Candidates
            final RTCStats localCandidateStats = stats.get(VoipStats.tryGetString(members, "localCandidateId", null));
            if (localCandidateStats != null) {
                final Map<String, Object> localCandidateStatsMembers = localCandidateStats.getMembers();
                this.local = new VoipStats.Candidate(localCandidateStatsMembers);
            } else {
                this.local = null;
            }
            final RTCStats remoteCandidateStats = stats.get(VoipStats.tryGetString(members, "remoteCandidateId", null));
            if (remoteCandidateStats != null) {
                final Map<String, Object> remoteCandidateStatsMembers = remoteCandidateStats.getMembers();
                this.remote = new VoipStats.Candidate(remoteCandidateStatsMembers);
            } else {
                this.remote = null;
            }

            // Nominated
            this.nominated = VoipStats.tryGetBool(members, "nominated", null);
            // State
            this.state = VoipStats.tryGetString(members, "state", "n/a");
            // Bytes transferred
            this.bytesTransferred = new VoipStats.BytesTransferred(members);
            // RTT
            this.roundTripTime = new VoipStats.RoundTripTime(members);
            // Available bitrate
            this.availableOutgoingBitrate = VoipStats.tryGetDouble(members, "availableOutgoingBitrate", null);

            // Uses relay?
            this.usesRelay = (this.local != null && "relay".equals(this.local.type))
                || (this.remote != null && "relay".equals(this.remote.type));
        }

        @Override
        public void addShortRepresentation(@NonNull StringBuilder builder) {
            builder.append("pair=");
            builder.append(this.state);
            if (this.nominated != null && this.nominated) {
                builder.append(" nominated");
            }
            builder.append("\n");

            builder.append("local=");
            if (this.local != null) {
                this.local.addShortRepresentation(builder);
            } else {
                builder.append("n/a");
            }
            builder.append("\n");

            builder.append("remote=");
            if (this.remote != null) {
                this.remote.addShortRepresentation(builder);
            } else {
                builder.append("n/a");
            }
            builder.append("\n");

            builder.append("relayed=").append(this.usesRelay).append("\n");

            this.bytesTransferred.addShortRepresentation(builder);
            if (this.availableOutgoingBitrate != null) {
                builder
                    .append(" bitrate=")
                    .append(String.format(Locale.US, "%.0fkbps", this.availableOutgoingBitrate / 1000));
            }
            builder.append("\n");
            this.roundTripTime.addShortRepresentation(builder);
        }

        @Override
        public void addRepresentation(@NonNull StringBuilder builder) {
            builder.append("id=");
            builder.append(this.id);
            builder.append(", state=");
            builder.append(this.state);
            builder.append(", priority=");
            builder.append(this.priority);
            builder.append(", nominated=");
            builder.append(this.nominated != null && this.nominated ? "yes" : "no");
            builder.append(", ");
            this.roundTripTime.addRepresentation(builder);
            builder.append(", ");
            this.bytesTransferred.addRepresentation(builder);

            builder.append("\n  Local: ");
            if (this.local != null) {
                this.local.addRepresentation(builder);
            } else {
                builder.append("n/a");
            }

            builder.append("\n  Remote: ");
            if (this.remote != null) {
                this.remote.addRepresentation(builder);
            } else {
                builder.append("n/a");
            }
        }

        public void addStatusChar(@NonNull StringBuilder builder) {
            if (this.state == null) {
                builder.append('?');
                return;
            }

            /*
             * '-' -> frozen: The pair has been held back due to another pair with the same
             *        foundation that is currently in the waiting state.
             * '.' -> waiting: Pair checking has not started, yet.
             * '+' -> in-progress: Pair checking is in progress. In the webrtc.org implementation,
             *        this is also being used for pairs that (temporarily) have no connection.
             * 'o' -> succeeded: A connection could be established via this pair.
             * 'x' -> failed: No connection could be established via this pair and no further
             *        attempts will be made.
             */
            switch (this.state) {
                case "frozen":
                    builder.append('-');
                    break;
                case "waiting":
                    builder.append('.');
                    break;
                case "in-progress":
                    builder.append('+');
                    break;
                case "succeeded":
                    builder.append('o');
                    break;
                case "failed":
                    builder.append('x');
                    break;
                default:
                    builder.append('?');
                    break;
            }
        }
    }

    public static class Transport implements VoipStats.StatsRepresentation {
        @NonNull
        final public VoipStats.BytesTransferred bytesTransferred;
        @Nullable
        final public String dtlsState;
        @NonNull
        final public String selectedCandidatePairId;

        private Transport(@NonNull Map<String, Object> members) {
            this.bytesTransferred = new VoipStats.BytesTransferred(members);
            this.dtlsState = VoipStats.tryGetString(members, "dtlsState", "n/a");
            final String candidatePairId = VoipStats.tryGetString(members, "selectedCandidatePairId", null);
            this.selectedCandidatePairId = VoipStats.getCandidatePairId(candidatePairId);
        }

        @Override
        public void addShortRepresentation(@NonNull StringBuilder builder) {
            builder.append("dtls=");
            builder.append(this.dtlsState);
            builder.append(" ");
            this.bytesTransferred.addShortRepresentation(builder);
        }

        @Override
        public void addRepresentation(@NonNull StringBuilder builder) {
            builder.append("dtls-state=");
            builder.append(this.dtlsState);
            builder.append(", selected-candidate-pair-id=");
            builder.append(this.selectedCandidatePairId);
            builder.append(", ");
            this.bytesTransferred.addRepresentation(builder);
        }
    }

    public static class Crypto implements VoipStats.StatsRepresentation {
        @NonNull
        final public String dtlsVersion;
        @Nullable
        final public String dtlsCipher;
        @Nullable
        final public String srtpCipher;

        private Crypto(@NonNull Map<String, Object> members) {
            this.dtlsVersion = this.getDtlsVersionString(VoipStats.tryGetString(members, "tlsVersion", null));
            this.dtlsCipher = VoipStats.tryGetString(members, "dtlsCipher", "?");
            this.srtpCipher = VoipStats.tryGetString(members, "srtpCipher", "?");
        }

        @Override
        public void addShortRepresentation(@NonNull StringBuilder builder) {
            builder.append("dtls=v");
            builder.append(this.dtlsVersion);
            builder.append(":");
            builder.append(this.dtlsCipher);
            builder.append(" srtp=");
            builder.append(this.srtpCipher);
        }

        @Override
        public void addRepresentation(@NonNull StringBuilder builder) {
            builder.append("dtls-version=");
            builder.append(this.dtlsVersion);
            builder.append(", dtls-cipher=");
            builder.append(this.dtlsCipher);
            builder.append(", srtp-cipher=");
            builder.append(this.srtpCipher);
        }

        private @NonNull String getDtlsVersionString(@Nullable String dtlsVersionBytes) {
            if (dtlsVersionBytes == null) {
                return "?";
            }
            switch (dtlsVersionBytes) {
                case "FEFF":
                    return "1.0";
                case "FEFD":
                    return "1.2";
                default:
                    return "?";
            }
        }
    }

    public abstract static class Rtp implements VoipStats.StatsRepresentation {
        @NonNull
        final private SortedMap<String, Codec> codecs;
        @Nullable
        final public String codecId;
        @Nullable
        final public String kind;
        @Nullable
        public Double jitter;
        @Nullable
        public Long packetsTotal;
        @Nullable
        public BigInteger bytesTotal;
        @Nullable
        public Integer packetsLost;
        @Nullable
        public Float packetLossPercent;
        @Nullable
        public String qualityLimitationReason;
        @Nullable
        public Long qualityLimitationResolutionChanges;
        @Nullable
        public String implementation;
        @Nullable
        public Float averageFps;
        @Nullable
        public Double bitrate;

        private Rtp(@NonNull Map<String, Object> members, @NonNull SortedMap<String, Codec> codecs) {
            this.codecs = codecs;
            this.codecId = VoipStats.tryGetString(members, "codecId", null);
            this.kind = VoipStats.tryGetString(members, "kind", "?");
        }

        @Override
        public void addShortRepresentation(@NonNull StringBuilder builder) {
            builder.append(this.kind);

            if (this.packetsTotal != null && this.packetsLost != null) {
                builder.append(" packets-lost=").append(this.packetsLost)
                    .append("/").append(this.packetsTotal)
                    .append(String.format(Locale.US, "(%.1f%%)", this.packetLossPercent));
            } else if (this.packetsTotal != null) {
                builder.append(" packets=").append(this.packetsTotal);
            }

            if (this.jitter != null) {
                builder.append(" jitter=").append(this.jitter);
            }

            if (this.bitrate != null) {
                builder.append(String.format(Locale.US, " bitrate=%.0fkbps", this.bitrate / 1000));
            }

            if (this.averageFps != null) {
                builder.append(String.format(Locale.US, " avfps=%.1f", this.averageFps));
            }

            builder.append(" c=");
            Codec codec;
            if (this.codecId != null && (codec = this.codecs.get(this.codecId)) != null) {
                codec.addShortRepresentation(builder);
            } else {
                builder.append("?");
            }
            if (this.implementation != null) {
                switch (this.implementation) {
                    case "HWEncoder":
                        builder.append(" (hw)");
                        break;
                    case "SWEncoder":
                        builder.append(" (sw)");
                        break;
                    case "unknown":
                        break;
                    default:
                        builder.append(" (").append(this.implementation).append(")");
                        break;
                }
            }

            if (this.qualityLimitationReason != null) {
                builder.append(" limit=");
                builder.append(this.qualityLimitationReason.replace("bandwidth", "bw"));
                if (this.qualityLimitationResolutionChanges != null) {
                    builder.append("/").append(this.qualityLimitationResolutionChanges);
                }
            }
        }

        @Override
        public void addRepresentation(@NonNull StringBuilder builder) {
            this.addShortRepresentation(builder);
        }

        /**
         * Calculate and return the video bitrate in bps (bits per second).
         * <p>
         * Note that calculations should be at least 100 milliseconds apart.
         * Otherwise a {@link RuntimeException} is thrown.
         */
        static double calculateVideoBitrate(
            double previousTimestamp,
            @NonNull BigInteger previousBytes,
            double currentTimestamp,
            @NonNull BigInteger currentBytes
        ) throws RuntimeException {
            final int bytesSent = (currentBytes.subtract(previousBytes)).intValue();
            final double microSecondsElapsed = currentTimestamp - previousTimestamp;
            if (microSecondsElapsed < 0) {
                throw new RuntimeException("Previous state must not have a higher timestamp than current state");
            }
            if (microSecondsElapsed < 100000) {
                throw new RuntimeException("State timestamps should be at least 100ms apart");
            }
            return (double) (8 * bytesSent) / (microSecondsElapsed / 1000 / 1000);
        }
    }

    public static class InboundRtp extends Rtp {
        private InboundRtp(
            @NonNull Map<String, Object> members,
            @NonNull SortedMap<String, Codec> codecs,
            @Nullable StateContext context
        ) {
            super(members, codecs);
            this.jitter = VoipStats.tryGetDouble(members, "jitter", null);
            this.packetsTotal = VoipStats.tryGetLong(members, "packetsReceived", null);
            this.bytesTotal = VoipStats.tryGetBigInt(members, "bytesReceived", null);
            this.packetsLost = VoipStats.tryGetInt(members, "packetsLost", null);
            this.packetLossPercent = this.calculatePacketLoss();
            this.implementation = VoipStats.tryGetString(members, "decoderImplementation", null);
            final Double totalInterFrameDelay = VoipStats.tryGetDouble(members, "totalInterFrameDelay", null);
            final Long framesDecoded = VoipStats.tryGetLong(members, "framesDecoded", null);
            if (totalInterFrameDelay != null && framesDecoded != null) {
                this.averageFps = this.calculateAverageFps(totalInterFrameDelay, framesDecoded);
            }
            if (context != null && context.previousState != null && context.previousState.videoBytesReceived != null && this.bytesTotal != null) {
                try {
                    this.bitrate = Rtp.calculateVideoBitrate(
                        context.previousState.timestampUs,
                        context.previousState.videoBytesReceived,
                        context.timestampUs,
                        bytesTotal
                    );
                } catch (RuntimeException e) {
                    // Not enough time elapsed, ignore
                }
            }
        }

        /**
         * Calculate packet loss in percent.
         */
        private @Nullable Float calculatePacketLoss() {
            if (this.packetsTotal == null || this.packetsLost == null) {
                return null;
            }
            if (this.packetsLost > 0) {
                return ((float) this.packetsLost) / this.packetsTotal * 100f;
            } else {
                return 0f;
            }
        }

        /**
         * Calculate average FPS.
         * <p>
         * Note: I'm not 100% sure if this is correct :)
         */
        private float calculateAverageFps(@NonNull Double totalInterFrameDelay, @NonNull Long framesDecoded) {
            if (framesDecoded == 0) {
                return 0f;
            }
            return (float) (1.0 / (totalInterFrameDelay / framesDecoded));
        }
    }

    public static class OutboundRtp extends Rtp {
        private OutboundRtp(
            @NonNull Map<String, Object> members,
            @NonNull SortedMap<String, Codec> codecs,
            @Nullable StateContext context
        ) {
            super(members, codecs);
            this.packetsTotal = VoipStats.tryGetLong(members, "packetsSent", null);
            this.bytesTotal = VoipStats.tryGetBigInt(members, "bytesSent", null);
            this.qualityLimitationReason = VoipStats.tryGetString(members, "qualityLimitationReason", null);
            this.qualityLimitationResolutionChanges = VoipStats.tryGetLong(members, "qualityLimitationResolutionChanges", null);
            this.implementation = VoipStats.tryGetString(members, "encoderImplementation", null);
            if (context != null && context.previousState != null && context.previousState.videoBytesSent != null && this.bytesTotal != null) {
                try {
                    this.bitrate = Rtp.calculateVideoBitrate(
                        context.previousState.timestampUs,
                        context.previousState.videoBytesSent,
                        context.timestampUs,
                        this.bytesTotal
                    );
                } catch (RuntimeException e) {
                    // Not enough time elapsed, ignore
                }
            }
        }
    }

    public static class Track implements VoipStats.StatsRepresentation {
        @Nullable
        final public String kind;
        @Nullable
        final public Long frameWidth;
        @Nullable
        final public Long frameHeight;
        @Nullable
        final public Long freezeCount;
        @Nullable
        final public Long pauseCount;

        private Track(@NonNull Map<String, Object> members) {
            this.kind = VoipStats.tryGetString(members, "kind", "?");
            this.frameWidth = VoipStats.tryGetLong(members, "frameWidth", null);
            this.frameHeight = VoipStats.tryGetLong(members, "frameHeight", null);
            this.freezeCount = VoipStats.tryGetLong(members, "freezeCount", null);
            this.pauseCount = VoipStats.tryGetLong(members, "pauseCount", null);
        }

        @Override
        public void addShortRepresentation(@NonNull StringBuilder builder) {
            builder.append(this.kind);

            if (this.frameWidth != null && this.frameHeight != null) {
                builder.append(" res=").append(this.frameWidth).append("x").append(this.frameHeight);
            }

            if (this.freezeCount != null) {
                builder.append(" freeze=").append(this.freezeCount);
            }

            if (this.pauseCount != null) {
                builder.append(" pause=").append(this.pauseCount);
            }
        }

        @Override
        public void addRepresentation(@NonNull StringBuilder builder) {
            this.addShortRepresentation(builder);
        }
    }

    public enum Direction {
        INBOUND,
        OUTBOUND,
    }

    public enum CodecMimeTypePrimary {
        UNKNOWN,
        AUDIO,
        VIDEO;

        @NonNull
        public static CodecMimeTypePrimary fromRepresentation(@Nullable String string) {
            if (string == null) {
                return UNKNOWN;
            }
            switch (string) {
                case "audio":
                    return AUDIO;
                case "video":
                    return VIDEO;
            }
            return UNKNOWN;
        }

        @NonNull
        public String toShortRepresentation() {
            switch (this) {
                case AUDIO:
                    return "a";
                case VIDEO:
                    return "v";
            }
            return "?";
        }

        @NonNull
        public String toRepresentation() {
            switch (this) {
                case AUDIO:
                    return "audio";
                case VIDEO:
                    return "video";
            }
            return "?";
        }
    }

    public static class CodecMimeType {
        @NonNull
        final public CodecMimeTypePrimary primary;
        @NonNull
        final public String secondary;

        CodecMimeType(@NonNull CodecMimeTypePrimary primary, @NonNull String secondary) {
            this.primary = primary;
            this.secondary = secondary;
        }
    }

    public static class Codec implements VoipStats.StatsRepresentation {
        @NonNull
        final public String id;
        @NonNull
        final public Direction direction;
        @NonNull
        final public CodecMimeType mimeType;
        @Nullable
        final public Long clockRate;

        private Codec(@NonNull RTCStats entry, @NonNull Map<String, Object> members) {
            this.id = entry.getId();
            this.direction = this.id.contains("Inbound") ? Direction.INBOUND : Direction.OUTBOUND;
            this.mimeType = this.getMimeType(VoipStats.tryGetString(members, "mimeType", null));
            this.clockRate = VoipStats.tryGetLong(members, "clockRate", null);
        }

        @Override
        public void addShortRepresentation(@NonNull StringBuilder builder) {
            builder.append(this.mimeType.primary.toShortRepresentation());
            builder.append("/");
            builder.append(this.mimeType.secondary);
            if (this.clockRate == null) {
                return;
            }
            builder.append("@");
            final long clockRateK = this.clockRate / 1000;
            if (clockRateK >= 1) {
                builder.append(clockRateK);
                builder.append("k");
            } else {
                builder.append(this.clockRate);
            }
        }

        @Override
        public void addRepresentation(@NonNull StringBuilder builder) {
            builder.append("mime-type=");
            builder.append(this.mimeType.primary.toRepresentation());
            builder.append("/");
            builder.append(this.mimeType.secondary);
            builder.append(", clock-rate=");
            builder.append(this.clockRate);
        }

        @NonNull
        CodecMimeType getMimeType(@Nullable String mimeTypeString) {
            if (mimeTypeString == null) {
                return new CodecMimeType(CodecMimeTypePrimary.UNKNOWN, "?");
            }
            final String[] mimeType = mimeTypeString.split("/", 2);
            if (mimeType.length != 2) {
                return new CodecMimeType(CodecMimeTypePrimary.UNKNOWN, "?");
            }
            return new CodecMimeType(
                CodecMimeTypePrimary.fromRepresentation(mimeType[0]),
                mimeType[1]
            );
        }
    }

    public static class RtpTransceiver implements VoipStats.StatsRepresentation {
        @NonNull
        final private org.webrtc.RtpTransceiver transceiver;

        private RtpTransceiver(@NonNull org.webrtc.RtpTransceiver rtpTransceiver) {
            this.transceiver = rtpTransceiver;
        }

        @Override
        public void addShortRepresentation(@NonNull StringBuilder builder) {
            builder
                .append("kind=")
                .append(this.getMediaType(this.transceiver.getMediaType()))
                .append(", mid=")
                .append(this.transceiver.getMid())
                .append(", cur-dir=")
                .append(this.getDirection(this.transceiver.getCurrentDirection()));

            final RtpSender sender = this.transceiver.getSender();
            if (sender != null) {
                builder.append("\n  sender: ");
                this.addParametersShortRepresentation(builder, sender.getParameters());
            }

            final RtpReceiver receiver = this.transceiver.getReceiver();
            if (receiver != null) {
                builder.append("\n  receiver: ");
                this.addParametersShortRepresentation(builder, receiver.getParameters());
            }
        }

        private void addParametersShortRepresentation(
            @NonNull StringBuilder builder,
            @NonNull RtpParameters parameters
        ) {
            // Add amount of encrypted vs. non-encrypted header extensions
            int plain = 0;
            int encrypted = 0;
            for (final RtpParameters.HeaderExtension extension : parameters.getHeaderExtensions()) {
                if (extension.getEncrypted()) {
                    ++encrypted;
                } else {
                    ++plain;
                }
            }
            builder
                .append("#exts=")
                .append(encrypted)
                .append("e/")
                .append(plain)
                .append("p, ");

            // Add codecs
            builder.append("cs=");
            if (parameters.codecs.size() > 0) {
                for (final RtpParameters.Codec codec : parameters.codecs) {
                    // Add codec
                    builder
                        .append(codec.name)
                        .append("/")
                        .append(this.getShortClockRate(codec));
                    if (codec.numChannels != null) {
                        builder
                            .append("/")
                            .append(codec.numChannels);
                    }

                    // Add codec attributes
                    // Note: We only care about Opus CBR attributes
                    if (codec.name.equals("opus")) {
                        builder.append("[");
                        Map<String, String> attributes = StreamSupport
                            .stream(codec.parameters.entrySet())
                            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
                        final String cbr = attributes.get("cbr");
                        builder
                            .append("cbr=")
                            .append(cbr != null ? cbr : "?");
                        builder.append("]");
                    }
                    builder.append(" ");
                }
            } else {
                builder.append("?");
            }
        }

        @Override
        public void addRepresentation(@NonNull StringBuilder builder) {
            builder
                .append("kind=")
                .append(this.getMediaType(this.transceiver.getMediaType()))
                .append(", mid=")
                .append(this.transceiver.getMid())
                .append(", direction=")
                .append(this.getDirection(this.transceiver.getDirection()))
                .append(", current-direction=")
                .append(this.getDirection(this.transceiver.getCurrentDirection()));

            final RtpSender sender = this.transceiver.getSender();
            if (sender != null) {
                builder.append("\n  Sender: ");
                this.addParametersRepresentation(builder, sender.getParameters());
            }

            final RtpReceiver receiver = this.transceiver.getReceiver();
            if (receiver != null) {
                builder.append("\n  Receiver: ");
                this.addParametersRepresentation(builder, receiver.getParameters());
            }
        }

        private void addParametersRepresentation(
            @NonNull StringBuilder builder,
            @NonNull RtpParameters parameters
        ) {
            // Add codecs
            builder
                .append("\n    Codecs (")
                .append(parameters.codecs.size())
                .append(")");
            if (parameters.codecs.size() > 0) {
                for (final RtpParameters.Codec codec : parameters.codecs) {
                    // Add codec
                    builder
                        .append("\n    - name=")
                        .append(codec.name)
                        .append(", clock-rate=")
                        .append(this.getShortClockRate(codec));
                    if (codec.numChannels != null) {
                        builder
                            .append(", #channels=")
                            .append(codec.numChannels);
                    }

                    // Add codec attributes
                    builder.append(", attributes=");
                    builder.append(StreamSupport
                        .stream(codec.parameters.entrySet())
                        .map(attribute -> String.format("%s=%s", attribute.getKey(), attribute.getValue()))
                        .collect(Collectors.joining(" "))
                    );
                }
            }

            // Add header extensions
            StringBuilder extensionsBuilder = new StringBuilder();
            int plain = 0;
            int encrypted = 0;
            for (final RtpParameters.HeaderExtension extension : parameters.getHeaderExtensions()) {
                extensionsBuilder
                    .append("\n    - id=")
                    .append(extension.getId())
                    .append(", encrypted=")
                    .append(extension.getEncrypted() ? "yes" : "no")
                    .append(", uri=")
                    .append(extension.getUri());
                if (extension.getEncrypted()) {
                    ++encrypted;
                } else {
                    ++plain;
                }
            }
            builder
                .append("\n    Header Extensions (")
                .append(encrypted)
                .append("e/")
                .append(plain)
                .append("p)")
                .append(extensionsBuilder);
        }

        private @NonNull String getMediaType(@NonNull MediaStreamTrack.MediaType type) {
            switch (type) {
                case MEDIA_TYPE_AUDIO:
                    return "audio";
                case MEDIA_TYPE_VIDEO:
                    return "video";
                default:
                    return "?";
            }
        }

        private @NonNull String getDirection(@NonNull org.webrtc.RtpTransceiver.RtpTransceiverDirection direction) {
            switch (direction) {
                case SEND_RECV:
                    return "send/recv";
                case SEND_ONLY:
                    return "send";
                case RECV_ONLY:
                    return "recv";
                case INACTIVE:
                    return "inactive";
                default:
                    return "?";
            }
        }

        private @NonNull String getShortClockRate(@NonNull RtpParameters.Codec codec) {
            final long clockRateK = codec.clockRate / 1000;
            if (clockRateK >= 1) {
                return clockRateK + "k";
            } else {
                return codec.clockRate.toString();
            }
        }
    }

    //endregion

    @NonNull
    private final Extractor extractor;
    @NonNull
    private final Builder builder;
    @NonNull
    private final StateContext context;
    @NonNull
    private final Map<String, RTCStats> stats;

    @Nullable
    public VoipStats.CandidatePair selectedCandidatePair = null;
    @Nullable
    public VoipStats.Transport transport = null;
    @Nullable
    public VoipStats.Crypto crypto = null;
    @Nullable
    public VoipStats.InboundRtp inboundRtpAudio = null;
    @Nullable
    public VoipStats.InboundRtp inboundRtpVideo = null;
    @Nullable
    public VoipStats.OutboundRtp outboundRtpAudio = null;
    @Nullable
    public VoipStats.OutboundRtp outboundRtpVideo = null;
    @Nullable
    public VoipStats.Track inboundTrackVideo = null;
    @Nullable
    public VoipStats.Track outboundTrackVideo = null;
    @Nullable
    public SortedMap<String, Codec> inboundCodecs = null;
    @Nullable
    public SortedMap<String, VoipStats.Codec> outboundCodecs = null;
    @Nullable
    public List<RtpTransceiver> rtpTransceivers = null;
    @Nullable
    public List<VoipStats.CandidatePair> candidatePairs = null;


    private VoipStats(
        @NonNull VoipStats.Extractor extractor,
        @NonNull RTCStatsReport report
    ) {
        this.extractor = extractor;
        this.builder = extractor.builder;
        this.context = new StateContext(this.extractor.previousState, report.getTimestampUs());
        this.stats = report.getStatsMap();
        this.extract();
    }

    private void extract() {
        this.inboundCodecs = new TreeMap<>();
        this.outboundCodecs = new TreeMap<>();
        if (this.builder.candidatePairsFlag != CandidatePairVariant.NONE) {
            this.candidatePairs = new ArrayList<>();
        }

        // Extract values
        for (final RTCStats entry : this.stats.values()) {
            final Map<String, Object> members = entry.getMembers();
            switch (entry.getType()) {
                case "codec":
                    final VoipStats.Codec codec = new VoipStats.Codec(entry, members);
                    if (codec.direction == Direction.INBOUND) {
                        this.inboundCodecs.put(codec.id, codec);
                    } else {
                        this.outboundCodecs.put(codec.id, codec);
                    }
                    break;

                case "candidate-pair":
                    if (this.builder.candidatePairsFlag != CandidatePairVariant.NONE) {
                        this.candidatePairs.add(new VoipStats.CandidatePair(this.stats, entry, members));
                    }
                    break;

                case "inbound-rtp":
                    if (this.builder.rtp) {
                        final String kind = VoipStats.tryGetString(members, "kind", null);
                        if ("audio".equals(kind)) {
                            this.inboundRtpAudio = new VoipStats.InboundRtp(members, inboundCodecs, null);
                        } else if ("video".equals(kind)) {
                            this.inboundRtpVideo = new VoipStats.InboundRtp(members, inboundCodecs, this.context);
                        }
                    }
                    break;

                case "outbound-rtp":
                    if (this.builder.rtp) {
                        final String kind = VoipStats.tryGetString(members, "kind", null);
                        if ("audio".equals(kind)) {
                            this.outboundRtpAudio = new VoipStats.OutboundRtp(members, outboundCodecs, null);
                        } else if ("video".equals(kind)) {
                            this.outboundRtpVideo = new VoipStats.OutboundRtp(members, outboundCodecs, this.context);
                        }
                    }
                    break;

                case "track":
                    if (this.builder.tracks) {
                        final String kind = VoipStats.tryGetString(members, "kind", null);
                        final Boolean inbound = VoipStats.tryGetBool(members, "remoteSource", null);
                        if ("video".equals(kind) && inbound != null) {
                            if (inbound) {
                                this.inboundTrackVideo = new VoipStats.Track(members);
                            } else {
                                this.outboundTrackVideo = new VoipStats.Track(members);
                            }
                        }
                    }
                    break;

                case "transport":
                    if (this.builder.transport) {
                        this.transport = new VoipStats.Transport(members);
                    }
                    if (this.builder.crypto) {
                        this.crypto = new VoipStats.Crypto(members);
                    }
                    if (!this.builder.selectedCandidatePair) {
                        break;
                    }
                    final RTCStats pairEntry = this.stats.get(tryGetString(members, "selectedCandidatePairId", null));
                    if (pairEntry == null) {
                        break;
                    }
                    this.selectedCandidatePair = new VoipStats.CandidatePair(this.stats, pairEntry);
                    break;
            }
        }

        // Sort candidate pairs by priority (if any)
        if (this.candidatePairs != null) {
            Collections.sort(this.candidatePairs, (final VoipStats.CandidatePair left, final VoipStats.CandidatePair right) ->
                right.priority.compareTo(left.priority));
        }

        // Add transceivers (if any)
        if (this.extractor.rtpTransceivers != null) {
            this.rtpTransceivers = StreamSupport.stream(this.extractor.rtpTransceivers)
                .map(RtpTransceiver::new)
                .collect(Collectors.toUnmodifiableList());
        }
    }

    //region Extraction helpers

    private static @Nullable String tryGetString(@NonNull Map<String, Object> map, @NonNull String key, @Nullable String defaultValue) {
        final Object obj = map.get(key);
        return (obj instanceof String) ? (String) obj : defaultValue;
    }

    private static @Nullable Long tryGetLong(@NonNull Map<String, Object> map, @NonNull String key, @Nullable Long defaultValue) {
        final Object obj = map.get(key);
        return (obj instanceof Long) ? (Long) obj : defaultValue;
    }

    private static @Nullable Boolean tryGetBool(@NonNull Map<String, Object> map, @NonNull String key, @Nullable Boolean defaultValue) {
        final Object obj = map.get(key);
        return (obj instanceof Boolean) ? (Boolean) obj : defaultValue;
    }

    private static @Nullable Integer tryGetInt(@NonNull Map<String, Object> map, @NonNull String key, @Nullable Integer defaultValue) {
        final Object obj = map.get(key);
        return (obj instanceof Integer) ? (Integer) obj : defaultValue;
    }

    private static @Nullable BigInteger tryGetBigInt(@NonNull Map<String, Object> map, @NonNull String key, @Nullable BigInteger defaultValue) {
        final Object obj = map.get(key);
        return (obj instanceof BigInteger) ? (BigInteger) obj : defaultValue;
    }

    private static @Nullable Double tryGetDouble(@NonNull Map<String, Object> map, @NonNull String key, @Nullable Double defaultValue) {
        final Object obj = map.get(key);
        return (obj instanceof Double) ? (Double) obj : defaultValue;
    }

    //endregion

    //region Other helpers

    private static @NonNull String getCandidatePairId(@Nullable String statsId) {
        if (statsId == null) {
            return "???";
        }
        try {
            return statsId.substring(20);
        } catch (StringIndexOutOfBoundsException e) {
            return "???";
        }
    }

    /**
     * Convert byte count into human readable number.
     * <p>
     * Source: https://stackoverflow.com/a/3758880
     */
    @SuppressLint("DefaultLocale")
    private static @NonNull String humanReadableByteCount(BigInteger bytesBigInt) {
        if (bytesBigInt == null) {
            return "n/a";
        }
        final long bytes = bytesBigInt.longValue();
        int unit = 1024;
        if (bytes < unit) return bytes + "B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = "KMGTPE".charAt(exp - 1) + "i";
        return String.format("%.1f%sB", bytes / Math.pow(unit, exp), pre);
    }

    //endregion

    //region Public data access methods

    public void addShortRepresentation(@NonNull StringBuilder builder) {
        // Add short representations of each instance (if parsed)
        if (this.candidatePairs != null && (this.builder.candidatePairsFlag & CandidatePairVariant.OVERVIEW) != 0) {
            builder.append("pairs(");
            builder.append(this.candidatePairs.size());
            builder.append(")=");
            for (final VoipStats.CandidatePair candidatePair : this.candidatePairs) {
                candidatePair.addStatusChar(builder);
            }
            builder.append("\n");
        }
        if (this.selectedCandidatePair != null) {
            this.selectedCandidatePair.addShortRepresentation(builder);
            builder.append("\n");
        }

        builder.append("\n");
        if (this.transport != null) {
            this.transport.addShortRepresentation(builder);
            builder.append("\n");
        }
        if (this.crypto != null) {
            this.crypto.addShortRepresentation(builder);
            builder.append("\n");
        }

        builder.append("\n");
        if (this.inboundRtpAudio != null) {
            builder.append("in/");
            this.inboundRtpAudio.addShortRepresentation(builder);
            builder.append("\n");
        }
        if (this.inboundRtpVideo != null) {
            builder.append("in/");
            this.inboundRtpVideo.addShortRepresentation(builder);
            builder.append("\n");
        }
        if (this.outboundRtpAudio != null) {
            builder.append("out/");
            this.outboundRtpAudio.addShortRepresentation(builder);
            builder.append("\n");
        }
        if (this.outboundRtpVideo != null) {
            builder.append("out/");
            this.outboundRtpVideo.addShortRepresentation(builder);
            builder.append("\n");
        }

        builder.append("\n");
        if (this.inboundTrackVideo != null) {
            builder.append("in/track-");
            this.inboundTrackVideo.addShortRepresentation(builder);
            builder.append("\n");
        }
        if (this.outboundTrackVideo != null) {
            builder.append("out/track-");
            this.outboundTrackVideo.addShortRepresentation(builder);
            builder.append("\n");
        }
        if (this.builder.codecs && this.inboundCodecs != null) {
            builder.append("in/codecs ");
            for (final VoipStats.Codec codec : this.inboundCodecs.values()) {
                codec.addShortRepresentation(builder);
                builder.append(" ");
            }
            builder.append("\n");
        }
        if (this.builder.codecs && this.outboundCodecs != null) {
            builder.append("out/codecs ");
            for (final VoipStats.Codec codec : this.outboundCodecs.values()) {
                codec.addShortRepresentation(builder);
                builder.append(" ");
            }
            builder.append("\n");
        }

        if (this.rtpTransceivers != null) {
            builder.append("\n");
            for (final RtpTransceiver transceiver : this.rtpTransceivers) {
                builder.append("transceiver ");
                try {
                    transceiver.addShortRepresentation(builder);
                } catch (NullPointerException e) {
                    // Accesses on the internal transceiver API can fail with a null pointer
                    // which is not under our control.
                    builder.append("???");
                }
                builder.append("\n");
            }
        }

        if (this.candidatePairs != null && (this.builder.candidatePairsFlag & CandidatePairVariant.DETAILED) != 0) {
            builder.append("\n");
            for (final VoipStats.CandidatePair candidatePair : this.candidatePairs) {
                candidatePair.addShortRepresentation(builder);
                builder.append("\n");
            }
        }

        // Strip newline (if any)
        final int length = builder.length();
        if (length > 0) {
            builder.setLength(length - 1);
        }
    }

    public void addRepresentation(@NonNull StringBuilder builder) {
        // Add long representations of each instance (if parsed)
        if (this.candidatePairs != null && (this.builder.candidatePairsFlag & CandidatePairVariant.OVERVIEW) != 0) {
            builder.append("Candidate Pairs Overview (");
            builder.append(this.candidatePairs.size());
            builder.append("): ");
            for (final VoipStats.CandidatePair candidatePair : this.candidatePairs) {
                candidatePair.addStatusChar(builder);
            }
            builder.append("\n");
        }
        if (this.selectedCandidatePair != null) {
            builder.append("Selected Candidate Pair: ");
            this.selectedCandidatePair.addRepresentation(builder);
            builder.append("\n");
        }
        if (this.transport != null) {
            builder.append("Transport: ");
            this.transport.addRepresentation(builder);
            builder.append("\n");
        }
        if (this.crypto != null) {
            builder.append("Crypto: ");
            this.crypto.addRepresentation(builder);
            builder.append("\n");
        }
        if (this.inboundRtpAudio != null) {
            builder.append("Inbound RTP (Audio): ");
            this.inboundRtpAudio.addRepresentation(builder);
            builder.append("\n");
        }
        if (this.inboundRtpVideo != null) {
            builder.append("Inbound RTP (Video): ");
            this.inboundRtpVideo.addRepresentation(builder);
            builder.append("\n");
        }
        if (this.outboundRtpAudio != null) {
            builder.append("Outbound RTP (Audio): ");
            this.outboundRtpAudio.addRepresentation(builder);
            builder.append("\n");
        }
        if (this.outboundRtpVideo != null) {
            builder.append("Outbound RTP (Video): ");
            this.outboundRtpVideo.addRepresentation(builder);
            builder.append("\n");
        }
        if (this.builder.codecs && this.inboundCodecs != null) {
            builder.append("Inbound Codecs (");
            builder.append(this.inboundCodecs.size());
            builder.append(")\n");
            for (final VoipStats.Codec codec : this.inboundCodecs.values()) {
                builder.append("- ");
                codec.addRepresentation(builder);
                builder.append("\n");
            }
        }
        if (this.builder.codecs && this.outboundCodecs != null) {
            builder.append("Outbound Codecs (");
            builder.append(this.outboundCodecs.size());
            builder.append(")\n");
            for (final VoipStats.Codec codec : this.outboundCodecs.values()) {
                builder.append("- ");
                codec.addRepresentation(builder);
                builder.append("\n");
            }
        }
        if (this.rtpTransceivers != null) {
            builder
                .append("Transceivers (")
                .append(this.rtpTransceivers.size())
                .append(")\n");
            for (final RtpTransceiver transceiver : this.rtpTransceivers) {
                builder.append("- ");
                try {
                    transceiver.addRepresentation(builder);
                } catch (NullPointerException e) {
                    // Accesses on the internal transceiver API can fail with a null pointer
                    // which is not under our control.
                    builder.append("???");
                }
            }
            builder.append("\n");
        }
        if (this.candidatePairs != null && (this.builder.candidatePairsFlag & CandidatePairVariant.DETAILED) != 0) {
            builder.append("Candidate Pairs (");
            builder.append(this.candidatePairs.size());
            builder.append(")\n");
            for (final VoipStats.CandidatePair candidatePair : this.candidatePairs) {
                builder.append("- ");
                candidatePair.addRepresentation(builder);
                builder.append("\n");
            }
        }

        // Strip newline
        builder.setLength(builder.length() - 1);
    }

    /**
     * Return true if the local or remote candidate in the selected candidate pair
     * is of type "relay". Return false otherwise.
     */
    public boolean usesRelay() {
        return this.selectedCandidatePair != null
            && this.selectedCandidatePair.usesRelay;
    }

    //endregion

    /**
     * Return the state information.
     */
    public @Nullable
    State getState() {
        return new State(
            this.context.timestampUs,
            this.outboundRtpVideo != null ? this.outboundRtpVideo.bytesTotal : null,
            this.inboundRtpVideo != null ? this.inboundRtpVideo.bytesTotal : null
        );
    }
}

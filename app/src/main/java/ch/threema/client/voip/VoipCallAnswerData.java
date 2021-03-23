/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema Java Client
 * Copyright (c) 2017-2021 Threema GmbH
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

package ch.threema.client.voip;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.client.BadMessageException;
import ch.threema.client.JSONUtil;
import ch.threema.client.voip.features.CallFeature;
import ch.threema.client.voip.features.FeatureList;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

public class VoipCallAnswerData extends VoipCallData<VoipCallAnswerData> {
	private static final Logger logger = LoggerFactory.getLogger(VoipCallAnswerData.class);

	// Keys
	private final static String KEY_ACTION = "action";
	private final static String KEY_ANSWER = "answer";
	private final static String KEY_FEATURES = "features";
	private final static String KEY_REJECT_REASON = "rejectReason";

	// Fields
	private @Nullable Byte action;
	private @Nullable Byte rejectReason = null;
	private @Nullable AnswerData answerData = null;
	private @NonNull FeatureList features = new FeatureList();

	//region Action

	public static class Action {
		public static final byte REJECT = 0;
		public static final byte ACCEPT = 1;
	}

	public @Nullable Byte getAction() {
		return action;
	}

	public @NonNull VoipCallAnswerData setAction(byte action) {
		this.action = action;
		return this;
	}

	//endregion

	//region Answer data

	public static class AnswerData {
		private final static String KEY_SDP_TYPE = "sdpType";
		private final static String KEY_SDP = "sdp";

		@Nullable String sdpType;
		@Nullable String sdp;

		public @Nullable String getSdp() {
			return sdp;
		}

		public @Nullable String getSdpType() {
			return sdpType;
		}

		public @NonNull AnswerData setSdp(@Nullable String sdp) {
			this.sdp = sdp;
			return this;
		}

		public @NonNull AnswerData setSdpType(@NonNull String sdpType) {
			this.sdpType = sdpType;
			return this;
		}

		@Override
		public String toString() {
			return "AnswerData{" +
				"sdpType='" + sdpType + '\'' +
				", sdp='" + sdp + '\'' +
				'}';
		}

		public static @NonNull AnswerData parse(@NonNull JSONObject o) throws BadMessageException {
			try {
				final AnswerData answerData = new AnswerData();

				answerData.sdpType = JSONUtil.getStringOrNull(o, KEY_SDP_TYPE);
				if (answerData.sdpType == null) {
					logger.error("Bad VoipCallAnswerData: " + KEY_SDP_TYPE + " must be defined");
					throw new BadMessageException("TM061", true);
				} else if (answerData.sdpType.equals("offer")) {
					logger.error("Bad VoipCallAnswerData: " + KEY_SDP_TYPE + " may not be \"offer\"");
					throw new BadMessageException("TM061", true);
				}

				answerData.sdp = JSONUtil.getStringOrNull(o, KEY_SDP);
				if (answerData.sdp == null && !answerData.sdpType.equals("rollback")) {
					logger.error("Bad VoipCallAnswerData: " + KEY_SDP + " may only be null if " + KEY_SDP_TYPE + "=rollback");
					throw new BadMessageException("TM061", true);
				}

				return answerData;
			} catch (Exception e) {
				throw new BadMessageException("TM061", true);
			}
		}

		/**
		 * Return AnswerData as JSONObject.
		 */
		public @NonNull JSONObject toJSON() throws JSONException {
			final JSONObject o = new JSONObject();
			o.put("sdpType", this.sdpType);
			o.put("sdp", this.sdp == null ? JSONObject.NULL : this.sdp);
			return o;
		}

	}

	public @Nullable AnswerData getAnswerData() {
		return this.answerData;
	}

	public @NonNull VoipCallAnswerData setAnswerData(@Nullable AnswerData answerData) {
		this.answerData = answerData;
		return this;
	}

	//endregion

	//region Features

	public @NonNull VoipCallAnswerData addFeature(@NonNull CallFeature feature) {
		this.features.addFeature(feature);
		return this;
	}

	public @NonNull FeatureList getFeatures() {
		return this.features;
	}

	//endregion

	//region Reject reason

	/**
	 * Collection of reject reasons.
	 *
	 * Note: Unfortunately we cannot use @IntDef here,
	 * because the type is byte and there's no @ByteDef...
	 */
	public static class RejectReason {
		// Reason not known
		public static final byte UNKNOWN = 0;

		// Called party is busy (another call is active)
		public static final byte BUSY = 1;

		// Ringing timeout was reached
		public static final byte TIMEOUT = 2;

		// Called party rejected the call
		public static final byte REJECTED = 3;

		// Called party disabled calls or denied the mic permission
		public static final byte DISABLED = 4;

		// Called party enabled an off-hours policy in Threema Work
		public static final byte OFF_HOURS = 5;
	}

	public @Nullable Byte getRejectReason() {
		return this.rejectReason;
	}

	/**
	 * Return a string representation of the reject reason.
	 *
	 * This should only be used for debugging, do not match on this value!
	 */
	public @NonNull String getRejectReasonName() {
		if (this.rejectReason == null) {
			return "null";
		}
		switch (this.rejectReason) {
			case RejectReason.UNKNOWN:
				return "unknown";
			case RejectReason.BUSY:
				return "busy";
			case RejectReason.TIMEOUT:
				return "timeout";
			case RejectReason.REJECTED:
				return "rejected";
			case RejectReason.DISABLED:
				return "disabled";
			case RejectReason.OFF_HOURS:
				return "off_hours";
			default:
				return this.rejectReason.toString();
		}
	}

	public @NonNull VoipCallAnswerData setRejectReason(byte rejectReason) {
		this.rejectReason = rejectReason;
		return this;
	}

	//endregion

	//region Serialization

	public static @NonNull VoipCallAnswerData parse(@NonNull String jsonObjectString) throws BadMessageException {
		final JSONObject o;
		try {
			o = new JSONObject(jsonObjectString);
		} catch (JSONException e) {
			logger.error("Bad VoipCallAnswerData: Invalid JSON string", e);
			throw new BadMessageException("TM061", true);
		}

		final VoipCallAnswerData callAnswerData = new VoipCallAnswerData();

		try {
			final Long callId = JSONUtil.getLongOrThrow(o, KEY_CALL_ID);
			if (callId != null) {
				callAnswerData.setCallId(callId);
			}
		} catch (Exception e) {
			logger.error("Bad VoipCallAnswerData: Invalid Call ID", e);
			throw new BadMessageException("TM061", true);
		}

		try {
			callAnswerData.action = (byte) o.getInt(KEY_ACTION);
		} catch (Exception e) {
			logger.error("Bad VoipCallAnswerData: Action must be a valid integer");
			throw new BadMessageException("TM061", true);
		}

		if (callAnswerData.action == Action.ACCEPT) {
			try {
				final JSONObject answerObj = o.getJSONObject(KEY_ANSWER);
				callAnswerData.answerData = AnswerData.parse(answerObj);
			} catch (Exception e) {
				logger.error("Bad VoipCallAnswerData: Answer could not be parsed");
				throw new BadMessageException("TM061", true);
			}
		} else if (callAnswerData.action == Action.REJECT) {
			try {
				callAnswerData.rejectReason = (byte) o.getInt(KEY_REJECT_REASON);
			} catch (Exception e) {
				logger.error("Bad VoipCallAnswerData: Reject reason could not be parsed");
				throw new BadMessageException("TM061", true);
			}
		}

		try {
			final JSONObject featureObj = o.optJSONObject(KEY_FEATURES);
			if (featureObj != null) {
				callAnswerData.features = FeatureList.parse(featureObj);
			}
		} catch (Exception e) {
			throw new BadMessageException("TM061", true);
		}

		return callAnswerData;
	}

	public void write(@NonNull ByteArrayOutputStream bos) throws Exception {
		bos.write(this.generateString().getBytes(UTF_8));
	}

	private @NonNull String generateString() throws BadMessageException {
		// Validate data
		if (this.action == null) {
			logger.error("Bad VoipCallAnswerData: No action set");
			throw new BadMessageException("TM061", true);
		}
		switch (this.action) {
			case Action.ACCEPT:
				if (this.answerData == null) {
					logger.error("Bad VoipCallAnswerData: Accept message must contain answer data");
					throw new BadMessageException("TM061", true);
				} else if (this.rejectReason != null) {
					logger.error("Bad VoipCallAnswerData: Accept message must not contain reject reason");
					throw new BadMessageException("TM061", true);
				}
				break;
			case Action.REJECT:
				if (this.rejectReason == null) {
					logger.error("Bad VoipCallAnswerData: Reject message must contain reject reason");
					throw new BadMessageException("TM061", true);
				} else if (this.answerData != null) {
					logger.error("Bad VoipCallAnswerData: Accept message must not contain answer data");
					throw new BadMessageException("TM061", true);
				}
				break;
			default:
				logger.error("Bad VoipCallAnswerData: Invalid action");
				throw new BadMessageException("TM061", true);
		}

		final JSONObject o = this.buildJsonObject();

		// Add answer data
		try {
			o.put(KEY_ACTION, this.action);
			if (this.action == Action.ACCEPT) {
				o.put(KEY_ANSWER, this.answerData.toJSON());
			} else if (this.action == Action.REJECT) {
				o.put(KEY_REJECT_REASON, this.rejectReason);
			}
		} catch (JSONException e) {
			logger.error("Could not add answer data", e);
			throw new BadMessageException("TM061", true);
		}

		// Add feature list
		if (!this.features.isEmpty()) {
			try {
				o.put("features", this.features.toJSON());
			} catch (JSONException e) {
				logger.error("Could not add features", e);
				throw new BadMessageException("TM061", true);
			}
		}

		return o.toString();
	}

	//endregion
}

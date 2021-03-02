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
import ch.threema.client.voip.features.CallFeature;
import ch.threema.client.voip.features.FeatureList;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;

import ch.threema.client.BadMessageException;
import ch.threema.client.JSONUtil;

import static java.nio.charset.StandardCharsets.*;

public class VoipCallOfferData extends VoipCallData<VoipCallOfferData> {
	private static final Logger logger = LoggerFactory.getLogger(VoipCallOfferData.class);

	// Keys
	private final static String KEY_OFFER = "offer";
	private final static String KEY_FEATURES = "features";

	// Fields
	private @Nullable OfferData offerData;
	private FeatureList features = new FeatureList();

	// region Offer data

	public static class OfferData {
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

		public @NonNull OfferData setSdp(@NonNull String sdp) {
			this.sdp = sdp;
			return this;
		}

		public @NonNull OfferData setSdpType(@NonNull String sdpType) {
			this.sdpType = sdpType;
			return this;
		}

		@Override
		public String toString() {
			return "OfferData{" +
					"sdpType='" + sdpType + '\'' +
					", sdp='" + sdp + '\'' +
					'}';
		}

		public static @NonNull OfferData parse(@NonNull JSONObject o) throws BadMessageException {
			try {
				final OfferData offerData = new OfferData();

				offerData.sdpType = JSONUtil.getStringOrNull(o, KEY_SDP_TYPE);
				if (offerData.sdpType == null) {
					logger.error("Bad VoipCallOfferData: " + KEY_SDP_TYPE + " must be defined");
					throw new BadMessageException("TM060", true);
				} else if (offerData.sdpType.equals("answer") || offerData.sdpType.equals("pranswer")) {
					logger.error("Bad VoipCallOfferData: " + KEY_SDP_TYPE + " may not be \"answer\" or \"pranswer\"");
					throw new BadMessageException("TM060", true);
				}

				offerData.sdp = JSONUtil.getStringOrNull(o, KEY_SDP);
				if (offerData.sdp == null && !offerData.sdpType.equals("rollback")) {
					logger.error("Bad VoipCallOfferData: " + KEY_SDP + " may only be null if " + KEY_SDP_TYPE + "=rollback");
					throw new BadMessageException("TM060", true);
				}

				return offerData;
			} catch (Exception e) {
				throw new BadMessageException("TM060", true);
			}
		}

		/**
		 * Return OfferData as JSONObject.
		 */
		public @NonNull JSONObject toJSON() throws JSONException {
			final JSONObject o = new JSONObject();
			o.put("sdpType", this.sdpType);
			o.put("sdp", this.sdp == null ? JSONObject.NULL : this.sdp);
			return o;
		}
	}

	public @Nullable OfferData getOfferData() {
		return this.offerData;
	}

	public VoipCallOfferData setOfferData(@NonNull OfferData offerData) {
		this.offerData = offerData;
		return this;
	}

	//endregion

	//region Features

	public @NonNull VoipCallOfferData addFeature(@NonNull CallFeature feature) {
		this.features.addFeature(feature);
		return this;
	}

	public @NonNull FeatureList getFeatures() {
		return this.features;
	}

	//endregion

	//region Serialization

	public static @NonNull VoipCallOfferData parse(@NonNull String jsonObjectString) throws BadMessageException {
		final JSONObject o;
		try {
			o = new JSONObject(jsonObjectString);
		} catch (JSONException e) {
			logger.error("Bad VoipCallOfferData: Invalid JSON string", e);
			throw new BadMessageException("TM060", true);
		}

		final VoipCallOfferData callOfferData = new VoipCallOfferData();

		try {
			final Long callId = JSONUtil.getLongOrThrow(o, KEY_CALL_ID);
			if (callId != null) {
				callOfferData.setCallId(callId);
			}
		} catch (Exception e) {
			logger.error("Bad VoipCallOfferData: Invalid Call ID", e);
			throw new BadMessageException("TM060", true);
		}

		try {
			final JSONObject offerObj = o.getJSONObject(KEY_OFFER);
			callOfferData.offerData = OfferData.parse(offerObj);
		} catch (Exception e) {
			logger.error("Bad VoipCallOfferData: Offer could not be parsed", e);
			throw new BadMessageException("TM060", true);
		}

		try {
			final JSONObject featureObj = o.optJSONObject(KEY_FEATURES);
			if (featureObj != null) {
				callOfferData.features = FeatureList.parse(featureObj);
			}
		} catch (Exception e) {
			logger.error("Bad VoipCallOfferData: Feature list could not be parsed", e);
			throw new BadMessageException("TM060", true);
		}

		return callOfferData;
	}

	public void write(@NonNull ByteArrayOutputStream bos) throws Exception {
		bos.write(this.generateString().getBytes(UTF_8));
	}

	private @NonNull String generateString() throws BadMessageException {
		final JSONObject o = this.buildJsonObject();

		// Add offer data
		try {
			if (this.offerData == null) {
				logger.error("Bad VoipCallOfferData: Missing offer data");
				throw new BadMessageException("TM060", true);
			}
			o.put(KEY_OFFER, this.offerData.toJSON());
		} catch (Exception e) {
			throw new BadMessageException("TM060", true);
		}

		// Add feature list
		if (!this.features.isEmpty()) {
			try {
				o.put("features", this.features.toJSON());
			} catch (JSONException e) {
				logger.error("Could not add features", e);
				throw new BadMessageException("TM060", true);
			}
		}

		return o.toString();
	}

	//endregion
}

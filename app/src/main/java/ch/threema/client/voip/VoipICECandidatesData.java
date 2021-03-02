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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import ch.threema.client.BadMessageException;
import ch.threema.client.JSONUtil;

import static java.nio.charset.StandardCharsets.*;

public class VoipICECandidatesData extends VoipCallData<VoipICECandidatesData> implements Serializable {
	private static final Logger logger = LoggerFactory.getLogger(VoipICECandidatesData.class);

	// Keys
	private final static String KEY_REMOVED = "removed";
	private final static String KEY_CANDIDATES = "candidates";

	// Fields
	private boolean removed = false;
	private @Nullable Candidate[] candidates;

	//region Removed

	@Deprecated // ANDR-1145
	public boolean isRemoved() {
		return this.removed;
	}

	//endregion

	//region Candidates

	public interface CandidateFilter {
		boolean keep(Candidate candidate);
	}

	public static class Candidate implements Serializable {
		private final static String KEY_CANDIDATE = "candidate";
		private final static String KEY_SDP_MID = "sdpMid";
		private final static String KEY_SDP_M_LINE_INDEX = "sdpMLineIndex";
		private final static String KEY_UFRAG = "ufrag";

		@Nullable String candidate;
		@Nullable String sdpMid;
		@Nullable Integer sdpMLineIndex;
		@Nullable String ufrag;

		public Candidate() {}

		public Candidate(
			@NonNull String candidate,
			@NonNull String sdpMid,
			@NonNull Integer sdpMLineIndex,
			@NonNull String ufrag
		) {
			this.candidate = candidate;
			this.sdpMid = sdpMid;
			this.sdpMLineIndex = sdpMLineIndex;
			this.ufrag = ufrag;
		}

		public @Nullable String getCandidate() {
			return candidate;
		}

		public Candidate setCandidate(@NonNull String candidate) {
			this.candidate = candidate;
			return this;
		}

		public @Nullable String getSdpMid() {
			return sdpMid;
		}

		public Candidate setSdpMid(@NonNull String sdpMid) {
			this.sdpMid = sdpMid;
			return this;
		}

		public @Nullable Integer getSdpMLineIndex() {
			return sdpMLineIndex;
		}

		public Candidate setSdpMLineIndex(@NonNull Integer sdpMLineIndex) {
			this.sdpMLineIndex = sdpMLineIndex;
			return this;
		}

		public @Nullable String getUfrag() {
			return ufrag;
		}

		public Candidate setUfrag(@NonNull String ufrag) {
			this.ufrag = ufrag;
			return this;
		}

		@Override
		public String toString() {
			return "Candidate{" +
					"candidate='" + candidate + '\'' +
					", sdpMid='" + sdpMid + '\'' +
					", sdpMLineIndex=" + sdpMLineIndex +
					", ufrag='" + ufrag + '\'' +
					'}';
		}

		public static @NonNull Candidate parse(@NonNull JSONObject o) throws BadMessageException {
			try {
				final Candidate candidate = new Candidate();

				final String candidateString = JSONUtil.getStringOrNull(o, KEY_CANDIDATE);
				if (candidateString == null) {
					logger.error("Bad Candidate: " + KEY_CANDIDATE + " must be defined");
					throw new BadMessageException("TM062", true);
				} else {
					candidate.candidate = candidateString;
				}

				candidate.sdpMid = JSONUtil.getStringOrNull(o, KEY_SDP_MID);
				candidate.sdpMLineIndex = JSONUtil.getIntegerOrNull(o, KEY_SDP_M_LINE_INDEX);
				candidate.ufrag = JSONUtil.getStringOrNull(o, KEY_UFRAG);

				return candidate;
			} catch (Exception e) {
				throw new BadMessageException("TM062", true);
			}
		}

		/**
		 * Return Candidate as JSONObject.
		 */
		public @NonNull JSONObject toJSON() throws JSONException {
			final JSONObject o = new JSONObject();
			o.put(KEY_CANDIDATE, this.candidate);
			o.put(KEY_SDP_MID, this.sdpMid == null ? JSONObject.NULL : this.sdpMid);
			o.put(KEY_SDP_M_LINE_INDEX, this.sdpMLineIndex == null ? JSONObject.NULL : this.sdpMLineIndex);
			o.put(KEY_UFRAG, this.ufrag == null ? JSONObject.NULL : this.ufrag);
			return o;
		}
	}

	public @Nullable Candidate[] getCandidates() {
		return this.candidates;
	}

	public VoipICECandidatesData setCandidates(@NonNull Candidate[] candidates) {
		this.candidates = candidates;
		return this;
	}

	/**
	 * Filter the list of candidates. Only entries where CandidateFilter.keep returns `true` are kept.
	 */
	public void filter(@NonNull CandidateFilter filter) {
		if (this.candidates != null) {
			List<Candidate> result = new ArrayList<>();
			for (Candidate c : this.candidates) {
				if (filter.keep(c)) {
					result.add(c);
				}
			}
			this.candidates = result.toArray(new Candidate[result.size()]);
		}
	}

	//endregion

	//region Serialization

	public static @NonNull VoipICECandidatesData parse(@NonNull String jsonObjectString) throws BadMessageException {
		final JSONObject o;
		try {
			o = new JSONObject(jsonObjectString);
		} catch (JSONException e) {
			logger.error("Bad VoipICECandidatesData: Invalid JSON string", e);
			throw new BadMessageException("TM062", true);
		}

		final VoipICECandidatesData candidatesData = new VoipICECandidatesData();

		try {
			final Long callId = JSONUtil.getLongOrThrow(o, KEY_CALL_ID);
			if (callId != null) {
				candidatesData.setCallId(callId);
			}
		} catch (Exception e) {
			logger.error("Bad VoipICECandidatesData: Invalid Call ID", e);
			throw new BadMessageException("TM062", true);
		}

		try {
			candidatesData.removed = o.getBoolean(KEY_REMOVED);

			final JSONArray candidates = o.getJSONArray(KEY_CANDIDATES);
			if (candidates.length() == 0) {
				logger.error("Bad VoipICECandidatesData: " + KEY_CANDIDATES + " may not be empty");
				throw new BadMessageException("TM062", true);
			}
			candidatesData.candidates = new Candidate[candidates.length()];
			for (int i = 0; i < candidates.length(); i++) {
				final JSONObject c = candidates.getJSONObject(i);
				candidatesData.candidates[i] = Candidate.parse(c);
			}
		} catch (Exception e) {
			logger.error("Bad VoipICECandidatesData", e);
			throw new BadMessageException("TM062", true);
		}

		return candidatesData;
	}

	public void write(@NonNull ByteArrayOutputStream bos) throws Exception {
		bos.write(this.generateString().getBytes(UTF_8));
	}

	private @NonNull String generateString() throws Exception {
		final JSONObject o = this.buildJsonObject();
		try {
			o.put(KEY_REMOVED, this.removed); // Deprecated, see ANDR-1145
			final JSONArray candidateArray = new JSONArray();
			for (Candidate candidate : this.candidates) {
				candidateArray.put(candidate.toJSON());
			}
			o.put(KEY_CANDIDATES, candidateArray);
		} catch (Exception e) {
			throw new BadMessageException("TM062", true);
		}

		return o.toString();
	}

	//endregion
}

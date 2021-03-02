/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema Java Client
 * Copyright (c) 2020-2021 Threema GmbH
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

package ch.threema.client.voip.features;

import androidx.annotation.NonNull;
import java8.util.stream.Collectors;
import java8.util.stream.StreamSupport;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Wraps a list of call features, used by both the offer and the answer.
 */
public class FeatureList {
	private final @NonNull List<CallFeature> features;

	public FeatureList() {
		this(new ArrayList<>());
	}

	public FeatureList(@NonNull List<CallFeature> features) {
		this.features = features;
	}

	/**
	 * Parse a JSON feature list.
	 */
	public static @NonNull FeatureList parse(@NonNull JSONObject obj) throws JSONException {
		final List<CallFeature> features = new ArrayList<>();
		for (Iterator<String> it = obj.keys(); it.hasNext(); ) {
			final String key = it.next();
			//noinspection SwitchStatementWithTooFewBranches
			switch (key) {
				case "video":
					features.add(new VideoFeature());
					break;
				default:
					features.add(new UnknownCallFeature(key, obj.optJSONObject(key)));
					break;
			}
		}
		return new FeatureList(features);
	}

	/**
	 * Add a feature to the feature list.
	 */
	public synchronized @NonNull FeatureList addFeature(@NonNull CallFeature feature) {
		this.features.add(feature);
		return this;
	}

	/**
	 * Return whether a feature with the specified name exists.
	 */
	public synchronized boolean hasFeature(@NonNull String name) {
		return StreamSupport.stream(this.features)
			.anyMatch(feature -> feature.getName().equals(name));
	}

	public boolean isEmpty() {
		return this.features.isEmpty();
	}

	public int size() {
		return this.features.size();
	}

	/**
	 * Return the feature list.
	 */
	public @NonNull List<CallFeature> getList() {
		return this.features;
	}

	/**
	 * Serialize into a JSON object.
	 */
	public @NonNull JSONObject toJSON() {
		final JSONObject featureMap = new JSONObject();
		for (CallFeature feature : this.features) {
			final JSONObject params = feature.getParams();
			// Java JSON removes a key if the value is `null`…
			try {
				if (params == null) {
					featureMap.put(feature.getName(), JSONObject.NULL);
				} else {
					featureMap.put(feature.getName(), params);
				}
			} catch (JSONException e) {
				// Should never happen™
				throw new RuntimeException("Call to JSONObject.put failed", e);
			}
		}
		return featureMap;
	}

	@Override
	public @NonNull String toString() {
		final String features = StreamSupport.stream(this.features).map(feature -> {
			if (feature.getParams() == null) {
				return feature.getName();
			}
			return String.format("%s(%s)", feature.getName(), feature.getParams().toString());
		}).collect(Collectors.joining(", "));
		return "FeatureList[" + features + "]";
	}
}

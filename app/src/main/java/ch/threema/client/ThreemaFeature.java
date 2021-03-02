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

package ch.threema.client;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import androidx.annotation.IntDef;

public class ThreemaFeature {
	// Feature flags. When adding new flags, also update LATEST_FEATURE!
	public final static int AUDIO = 0x01;
	public final static int GROUP_CHAT = 0x02;
	public final static int BALLOT = 0x04;
	public final static int FILE = 0x08;
	public final static int VOIP = 0x10;
	public final static int VIDEOCALLS = 0x20;

	// Should always point to latest feature
	public final static int LATEST_FEATURE = VIDEOCALLS;

	@Retention(RetentionPolicy.SOURCE)
	@IntDef({ AUDIO, GROUP_CHAT, BALLOT, FILE, VOIP, VIDEOCALLS})
	private @interface Feature {}

	/**
	 * Feature mask builder
	 */
	public static class Builder {
		private int mask = 0;

		public Builder audio(boolean enable) {
			return this.set(ThreemaFeature.AUDIO, enable);
		}

		public Builder group(boolean enable) {
			return this.set(ThreemaFeature.GROUP_CHAT, enable);
		}

		public Builder ballot(boolean enable) {
			return this.set(ThreemaFeature.BALLOT, enable);
		}

		public Builder file(boolean enable) {
			return this.set(ThreemaFeature.FILE, enable);
		}

		public Builder voip(boolean enable) {
			return this.set(ThreemaFeature.VOIP, enable);
		}

		public Builder videocalls(boolean enable) {
			return this.set(ThreemaFeature.VIDEOCALLS, enable);
		}

		public int build() {
			return this.mask;
		}

		private Builder set(@Feature int feature, boolean enable) {
			if (enable) {
				this.mask |= feature;
			} else {
				this.mask &= ~feature;
			}
			return this;
		}
	}

	public static boolean canText(int featureMask) {
		return true;
	}
	public static boolean canImage(int featureMask) {
		return true;
	}
	public static boolean canVideo(int featureMask) {
		return true;
	}
	public static boolean canAudio(int featureMask) {
		return hasFeature(featureMask, AUDIO);
	}
	public static boolean canGroupChat(int featureMask) {
		return hasFeature(featureMask, GROUP_CHAT);
	}
	public static boolean canBallot(int featureMask) {
		return hasFeature(featureMask, BALLOT);
	}
	public static boolean canFile(int featureMask) {
		return hasFeature(featureMask, FILE);
	}
	public static boolean canVoip(int featureMask) {
		return hasFeature(featureMask, VOIP);
	}
	public static boolean canVideocall(int featureMask) {
		return hasFeature(featureMask, VIDEOCALLS);
	}

	public static boolean hasFeature(int featureMask, @Feature int feature) {
		return (featureMask & feature) != 0;
	}

	public static boolean hasLatestFeature(int featureMask) {
		return hasFeature(featureMask, ThreemaFeature.LATEST_FEATURE);
	}

	/**
	 * Convert a feature mask to a classic feature level.
	 */
	public static int featureMaskToLevel(int featureMask) {
		if ((featureMask & FILE) > 0) {
			return 3;
		}
		if ((featureMask & BALLOT) > 0) {
			return 2;
		}
		if ((featureMask & GROUP_CHAT) > 0) {
			return 1;
		}
		return 0;
	}
}

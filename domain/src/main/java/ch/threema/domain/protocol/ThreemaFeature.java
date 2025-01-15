/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2024 Threema GmbH
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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import androidx.annotation.LongDef;
import ch.threema.protobuf.Common;

public class ThreemaFeature {
	// Feature flags. When adding new flags, also update LATEST_FEATURE!
	public final static long AUDIO = Common.CspFeatureMaskFlag.VOICE_MESSAGE_SUPPORT_VALUE;
	public final static long GROUP_CHAT = Common.CspFeatureMaskFlag.GROUP_SUPPORT_VALUE;
	public final static long BALLOT = Common.CspFeatureMaskFlag.POLL_SUPPORT_VALUE;
	public final static long FILE = Common.CspFeatureMaskFlag.FILE_MESSAGE_SUPPORT_VALUE;
	public final static long VOIP = Common.CspFeatureMaskFlag.O2O_AUDIO_CALL_SUPPORT_VALUE;
	public final static long VIDEOCALLS = Common.CspFeatureMaskFlag.O2O_VIDEO_CALL_SUPPORT_VALUE;
	public final static long FORWARD_SECURITY = Common.CspFeatureMaskFlag.FORWARD_SECURITY_SUPPORT_VALUE;
	public final static long GROUP_CALLS = Common.CspFeatureMaskFlag.GROUP_CALL_SUPPORT_VALUE;
	public final static long EDIT_MESSAGES = Common.CspFeatureMaskFlag.EDIT_MESSAGE_SUPPORT_VALUE;
	public final static long DELETE_MESSAGES = Common.CspFeatureMaskFlag.DELETE_MESSAGE_SUPPORT_VALUE;
	public final static long EMOJI_REACTIONS = Common.CspFeatureMaskFlag.REACTION_SUPPORT_VALUE;

	@Retention(RetentionPolicy.SOURCE)
	@LongDef({ AUDIO, GROUP_CHAT, BALLOT, FILE, VOIP, VIDEOCALLS, FORWARD_SECURITY, GROUP_CALLS, EDIT_MESSAGES, DELETE_MESSAGES, EMOJI_REACTIONS })
	public @interface Feature {}

	/**
	 * Feature mask builder
	 */
	public static class Builder {
		private long mask = 0;

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

		public Builder forwardSecurity(boolean enable) {
			return this.set(ThreemaFeature.FORWARD_SECURITY, enable);
		}

		public Builder groupCalls(boolean enable) {
			return this.set(ThreemaFeature.GROUP_CALLS, enable);
		}

		public Builder editMessages(boolean enable) {
			return this.set(ThreemaFeature.EDIT_MESSAGES, enable);
		}

		public Builder deleteMessages(boolean enable) {
			return this.set(ThreemaFeature.DELETE_MESSAGES, enable);
		}

		public Builder emojiReactions(boolean enable) {
			return this.set(ThreemaFeature.EMOJI_REACTIONS, enable);
		}

		public long build() {
			return this.mask;
		}

		private Builder set(@Feature long feature, boolean enable) {
			if (enable) {
				this.mask |= feature;
			} else {
				this.mask &= ~feature;
			}
			return this;
		}
	}

	public static boolean canText(long featureMask) {
		return true;
	}
	public static boolean canImage(long featureMask) {
		return true;
	}
	public static boolean canVideo(long featureMask) {
		return true;
	}
	public static boolean canAudio(long featureMask) {
		return hasFeature(featureMask, AUDIO);
	}
	public static boolean canGroupChat(long featureMask) {
		return hasFeature(featureMask, GROUP_CHAT);
	}
	public static boolean canBallot(long featureMask) {
		return hasFeature(featureMask, BALLOT);
	}
	public static boolean canFile(long featureMask) {
		return hasFeature(featureMask, FILE);
	}
	public static boolean canVoip(long featureMask) {
		return hasFeature(featureMask, VOIP);
	}
	public static boolean canVideocall(long featureMask) {
		return hasFeature(featureMask, VIDEOCALLS);
	}
	public static boolean canForwardSecurity(long featureMask) {
		return hasFeature(featureMask, FORWARD_SECURITY);
	}
	public static boolean canGroupCalls(long featureMask) {
		return hasFeature(featureMask, GROUP_CALLS);
	}
	public static boolean canEditMessages(long featureMask) {
		return hasFeature(featureMask, EDIT_MESSAGES);
	}
	public static boolean canDeleteMessages(long featureMask) {
		return hasFeature(featureMask, DELETE_MESSAGES);
	}
	public static boolean canEmojiReactions(long featureMask) {
		return hasFeature(featureMask, EMOJI_REACTIONS);
	}

	public static boolean hasFeature(long featureMask, @Feature long feature) {
		return (featureMask & feature) != 0;
	}

	/**
	 * Convert a feature mask to a classic feature level.
	 */
	// TODO(ANDR-2708): Remove
	public static long featureMaskToLevel(long featureMask) {
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

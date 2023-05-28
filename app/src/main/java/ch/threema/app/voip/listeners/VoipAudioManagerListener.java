/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2023 Threema GmbH
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

package ch.threema.app.voip.listeners;

import java.util.HashSet;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.utils.AudioDevice;
import ch.threema.app.voip.VoipAudioManager;

/**
 * Events related to the audio device management.
 */
public interface VoipAudioManagerListener {
	/**
	 * Audio device changed, or list of available audio devices changed.
	 */
	@AnyThread default void onAudioDeviceChanged(
		@Nullable AudioDevice selectedAudioDevice,
	    @NonNull HashSet<AudioDevice> availableAudioDevices
	) { }

	/**
	 * Audio focus was lost.
	 */
	@AnyThread default void onAudioFocusLost(boolean temporary) { }

	/**
	 * Audio focus was gained.
	 */
	@AnyThread default void onAudioFocusGained() { }

	/**
	 * Mic was enabled or disabled.
	 */
	default void onMicEnabledChanged(boolean micEnabled) { }
}

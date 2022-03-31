/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2022 Threema GmbH
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

package ch.threema.app.utils;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;

import org.slf4j.Logger;

import androidx.annotation.MainThread;
import ch.threema.app.ThreemaApplication;
import ch.threema.base.utils.LoggingUtil;

public class SoundUtil {
	private static final Logger logger = LoggingUtil.getThreemaLogger("SoundUtil");

	private SoundUtil() {
		throw new IllegalStateException("Utility class");
	}

	@MainThread
	public static void play(final int resId) {
		final AudioManager audioManager = (AudioManager) ThreemaApplication.getAppContext().getSystemService(Context.AUDIO_SERVICE);

		int ringerMode = audioManager.getRingerMode();
		boolean isSilent = (ringerMode == AudioManager.RINGER_MODE_SILENT
			|| ringerMode == AudioManager.RINGER_MODE_VIBRATE);

		if (!isSilent) {
			MediaPlayerStateWrapper mediaPlayer = new MediaPlayerStateWrapper();
			mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
				.setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
				.setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
				.build());
			mediaPlayer.setVolume(0.3f, 0.3f);
			mediaPlayer.setStateListener(new MediaPlayerStateWrapper.StateListener() {
				@Override
				public void onCompletion(MediaPlayer mp) {
					if (mp.isPlaying()) {
						mp.stop();
					}
					mp.reset();
					mp.release();
				}

				@Override
				public void onPrepared(MediaPlayer mp) {
					// ignore prepared state as we use synchronous prepare() method
				}
			});

			try (AssetFileDescriptor afd = ThreemaApplication.getAppContext().getResources().openRawResourceFd(resId)) {
				mediaPlayer.setDataSource(afd);
				mediaPlayer.prepare();
				mediaPlayer.start();
			} catch (Exception e) {
				logger.debug("could not play in-app sound.");
				mediaPlayer.release();
			}
		}
	}

	public static AudioAttributes getAudioAttributesForUsage(int usage) {
		return new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
			.setUsage(usage)
			.build();
	}
}

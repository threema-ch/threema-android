/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2025 Threema GmbH
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
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaPlayer;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import org.slf4j.Logger;

import ch.threema.app.ThreemaApplication;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import static android.media.AudioDeviceInfo.TYPE_BLE_HEADSET;
import static android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER;
import static android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP;
import static android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO;
import static android.media.AudioDeviceInfo.TYPE_USB_HEADSET;
import static android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES;
import static android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET;

public class SoundUtil {
    private static final Logger logger = getThreemaLogger("SoundUtil");

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
            mediaPlayer.setVolume(0.1f, 0.1f);
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

    /**
     * Check if device is currently connected to a headset or external speaker, either wired or via bluetooth
     *
     * @param audioManager An instance of AudioManager
     * @return true if some audio device is connected, false otherwise
     */
    public static boolean isHeadsetOn(@NonNull AudioManager audioManager) {
        AudioDeviceInfo[] audioDeviceInfos = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        if (audioDeviceInfos != null) {
            for (AudioDeviceInfo audioDeviceInfo : audioDeviceInfos) {
                if (audioDeviceInfo.getType() == TYPE_BLUETOOTH_SCO ||
                    audioDeviceInfo.getType() == TYPE_BLE_HEADSET ||
                    audioDeviceInfo.getType() == TYPE_BLE_SPEAKER ||
                    audioDeviceInfo.getType() == TYPE_BLUETOOTH_A2DP ||
                    audioDeviceInfo.getType() == TYPE_WIRED_HEADPHONES ||
                    audioDeviceInfo.getType() == TYPE_WIRED_HEADSET ||
                    audioDeviceInfo.getType() == TYPE_USB_HEADSET) {
                    logger.info("Headphones are connected.");
                    return true;
                }
            }
        }
        return false;
    }
}

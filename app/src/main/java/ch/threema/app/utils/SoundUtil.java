/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2024 Threema GmbH
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
import android.os.Build;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import org.slf4j.Logger;

import ch.threema.app.ThreemaApplication;
import ch.threema.base.utils.LoggingUtil;

import static android.media.AudioDeviceInfo.TYPE_BLE_HEADSET;
import static android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER;
import static android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP;
import static android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO;
import static android.media.AudioDeviceInfo.TYPE_USB_HEADSET;
import static android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES;
import static android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET;

public class SoundUtil {
    private static final Logger logger = LoggingUtil.getThreemaLogger("SoundUtil");
    private static final int FLAG_BYPASS_INTERRUPTION_POLICY = 0x1 << 6;

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

    public static AudioAttributes getAudioAttributesForUsage(int usage) {
        return new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
            .setUsage(usage)
            .build();
    }

    /**
     * Get audio attributes for playing a ringtone accompanying a call notification
     * Android 12+ will always mute the sound when DND is on. In order to be able to play a ringtone for incoming messages from a "starred" contact when INTERRUPTION_FILTER_PRIORITY is set,
     * we use the private FLAG_BYPASS_INTERRUPTION_POLICY flag.
     *
     * @return AudioAttributes
     */
    public static AudioAttributes getAudioAttributesForCallNotification() {
        return new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setFlags(FLAG_BYPASS_INTERRUPTION_POLICY)
            .build();
    }

    /**
     * Check if device is currently connected to a headset or external speaker, either wired or via bluetooth
     *
     * @param audioManager An instance of AudioManager
     * @return true if some audio device is connected, false otherwise
     */
    public static boolean isHeadsetOn(@NonNull AudioManager audioManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
        } else {
            if (audioManager.isWiredHeadsetOn()) {
                logger.info("Wired headset is connected.");
                return true;
            }

            if (audioManager.isBluetoothScoOn() || audioManager.isBluetoothA2dpOn()) {
                logger.info("Bluetooth headset is connected.");
                return true;
            }
        }
        return false;
    }
}

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

package ch.threema.app.voip;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.LayerDrawable;
import android.util.AttributeSet;
import android.view.View;

import org.slf4j.Logger;

import java.util.Collections;
import java.util.HashSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import ch.threema.app.R;
import ch.threema.app.utils.AudioDevice;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.voip.listeners.VoipAudioManagerListener;
import ch.threema.app.voip.managers.VoipListenerManager;
import ch.threema.app.voip.services.VoipCallService;
import ch.threema.base.utils.LoggingUtil;

public class AudioSelectorButton extends AppCompatImageView implements View.OnClickListener {
    private static final Logger logger = LoggingUtil.getThreemaLogger("AudioSelectorButton");

    // Constants for Drawable.setAlpha()
    private static final int HIDDEN = 0;
    private static final int VISIBLE = 255;

    private AudioDevice selectedAudioDevice;
    private HashSet<AudioDevice> availableAudioDevices;
    private AudioDeviceMultiSelectListener selectionListener;

    public interface AudioDeviceMultiSelectListener {
        void onShowSelected(HashSet<AudioDevice> audioDevices, AudioDevice selectedDevice);
    }

    public AudioSelectorButton(Context context) {
        super(context);
        init();
    }

    public AudioSelectorButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AudioSelectorButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    // Listeners
    private VoipAudioManagerListener audioManagerListener = new VoipAudioManagerListener() {
        @Override
        public void onAudioDeviceChanged(@Nullable final AudioDevice audioDevice,
                                         @NonNull final HashSet<AudioDevice> availableAudioDevices) {
            logger.debug("Audio devices changed, selected=" + selectedAudioDevice
                + ", available=" + availableAudioDevices);

            selectedAudioDevice = audioDevice;

            RuntimeUtil.runOnUiThread(() -> updateAudioSelectorButton(audioDevice, availableAudioDevices));
        }
    };

    private void updateAudioSelectorButton(AudioDevice audioDevice, HashSet<AudioDevice> availableAudioDevices) {
        this.selectedAudioDevice = audioDevice;
        this.availableAudioDevices = availableAudioDevices;

        final LayerDrawable layers = (LayerDrawable) getBackground();

        layers.findDrawableByLayerId(R.id.moreIndicatorItem)
            .setAlpha(availableAudioDevices.size() > 2 ? VISIBLE : HIDDEN);

        layers.findDrawableByLayerId(R.id.bluetoothItem)
            .setAlpha(selectedAudioDevice.equals(AudioDevice.BLUETOOTH) ? VISIBLE : HIDDEN);

        layers.findDrawableByLayerId(R.id.handsetItem)
            .setAlpha(selectedAudioDevice.equals(AudioDevice.EARPIECE) ? VISIBLE : HIDDEN);

        layers.findDrawableByLayerId(R.id.headsetItem)
            .setAlpha(selectedAudioDevice.equals(AudioDevice.WIRED_HEADSET) ? VISIBLE : HIDDEN);

        layers.findDrawableByLayerId(R.id.speakerphoneItem)
            .setAlpha(selectedAudioDevice.equals(AudioDevice.SPEAKER_PHONE) ? VISIBLE : HIDDEN);

        if (!RuntimeUtil.isInTest()) {
            setClickable(availableAudioDevices.size() > 1);
            setEnabled(availableAudioDevices.size() > 1);
        }
    }

    private void init() {
        setOnClickListener(this);

        AudioDevice initialAudioDevice = !RuntimeUtil.isInTest()
            ? AudioDevice.NONE
            : AudioDevice.SPEAKER_PHONE;

        updateAudioSelectorButton(initialAudioDevice, new HashSet<>(Collections.singletonList(initialAudioDevice)));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        VoipListenerManager.audioManagerListener.add(this.audioManagerListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        VoipListenerManager.audioManagerListener.remove(this.audioManagerListener);

        super.onDetachedFromWindow();
    }

    @Override
    public void onClick(View v) {
        if (this.availableAudioDevices != null) {
            if (this.availableAudioDevices.size() > 2) {
                if (selectionListener != null) {
                    selectionListener.onShowSelected(availableAudioDevices, selectedAudioDevice);
                }
            } else {
                AudioDevice newAudioDevice = AudioDevice.EARPIECE;

                for (AudioDevice device : availableAudioDevices) {
                    if (!device.equals(selectedAudioDevice)) {
                        newAudioDevice = device;
                    }
                }

                sendBroadcastToService(newAudioDevice);
            }
        }
    }

    public void setAudioDeviceMultiSelectListener(AudioDeviceMultiSelectListener listener) {
        this.selectionListener = listener;
    }

    private void sendBroadcastToService(AudioDevice device) {
        Intent intent = new Intent();
        intent.setAction(VoipCallService.ACTION_SET_AUDIO_DEVICE);
        intent.putExtra(VoipCallService.EXTRA_AUDIO_DEVICE, device);
        LocalBroadcastManager.getInstance(getContext()).sendBroadcast(intent);
    }
}

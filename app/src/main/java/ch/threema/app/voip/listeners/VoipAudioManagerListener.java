package ch.threema.app.voip.listeners;

import java.util.HashSet;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.utils.AudioDevice;

/**
 * Events related to the audio device management.
 */
public interface VoipAudioManagerListener {
    /**
     * Audio device changed, or list of available audio devices changed.
     */
    @AnyThread
    default void onAudioDeviceChanged(
        @Nullable AudioDevice selectedAudioDevice,
        @NonNull HashSet<AudioDevice> availableAudioDevices
    ) {
    }

    /**
     * Audio focus was lost.
     */
    @AnyThread
    default void onAudioFocusLost(boolean temporary) {
    }

    /**
     * Audio focus was gained.
     */
    @AnyThread
    default void onAudioFocusGained() {
    }

    /**
     * Mic was enabled or disabled.
     */
    default void onMicEnabledChanged(boolean micEnabled) {
    }
}

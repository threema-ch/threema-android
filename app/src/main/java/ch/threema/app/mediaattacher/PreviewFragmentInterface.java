package ch.threema.app.mediaattacher;

public interface PreviewFragmentInterface {
    interface AudioFocusActions {
        default void resumeAudio() {
        }

        default void pauseAudio() {
        }

        default void stopAudio() {
        }

        default void setVolume(float volume) {
        }
    }
}

package ch.threema.base;

public interface ProgressListener {
    /**
     * Update the progress of an upload/download process.
     *
     * @param progress in percent (0..100)
     */
    void updateProgress(int progress);

    /**
     * Indicate that no progress will be communicated as the overall goal is not known.
     */
    default void noProgressAvailable() {
        // Nothing to do
    }

    /**
     * Mark upload/download as finished.
     *
     * @param success if upload/download finished successfully
     */
    void onFinished(boolean success);
}

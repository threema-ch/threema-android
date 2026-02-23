package ch.threema.app.camera;

class CameraConfig {
    private final static int DEFAULT_IMAGE_SIZE = 2592;
    private final static int DEFAULT_AUDIO_BITRATE = 128000;
    private final static int DEFAULT_VIDEO_BITRATE = 2000000;

    static int getDefaultImageSize() {
        return DEFAULT_IMAGE_SIZE;
    }

    /**
     * Get the default audio bitrate. Note that this value is only used to guess the estimated file
     * size and therefore to limit the duration of the recording. The actual audio bitrate depends
     * on the device.
     */
    static int getDefaultAudioBitrate() {
        return DEFAULT_AUDIO_BITRATE;
    }

    /**
     * Get the default video bitrate. Note that this value is only used to guess the estimated file
     * size and therefore to limit the duration of the recording. The actual video bitrate depend on
     * the device.
     */
    static int getDefaultVideoBitrate() {
        return DEFAULT_VIDEO_BITRATE;
    }
}

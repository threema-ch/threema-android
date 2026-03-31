package ch.threema.app.video.transcoder;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import androidx.annotation.Nullable;

import java.io.IOException;

import ch.threema.app.utils.MimeUtil;

/**
 * Extracts Media or Audio Components from a file.
 */
public class MediaComponent {
    public static final int COMPONENT_TYPE_AUDIO = 0;
    public static final int COMPONENT_TYPE_VIDEO = 1;

    public static final int NO_TRACK_AVAILABLE = -1;

    private Context mContext;
    private final Uri mSrcUri;
    private final int mType;
    private String mimeType;
    private long durationUs;

    private MediaExtractor mMediaExtractor;
    private MediaFormat mTrackFormat;
    private int mSelectedTrackIndex;

    public MediaComponent(Context context, Uri srcUri, int type) throws IOException {
        mContext = context;
        mSrcUri = srcUri;
        mType = type;

        if (type != COMPONENT_TYPE_AUDIO && type != COMPONENT_TYPE_VIDEO) {
            throw new IllegalArgumentException("Invalid component type. " +
                "Must be one of COMPONENT_TYPE_AUDIO or COMPONENT_TYPE_VIDEO");
        }

        init();
    }

    /**
     * The MediaExtractor instance to use to for this component.
     */
    public MediaExtractor getMediaExtractor() {
        return mMediaExtractor;
    }

    /**
     * The MediaFormat for the selected track of this component.
     */
    public @Nullable MediaFormat getTrackFormat() {
        return mTrackFormat;
    }

    /**
     * The index of the selected track for this component.
     */
    public int getSelectedTrackIndex() {
        return mSelectedTrackIndex;
    }

    /**
     * The component type.
     *
     * @return COMPONENT_TYPE_AUDIO or COMPONENT_TYPE_VIDEO
     */
    public int getType() {
        return mType;
    }

    /**
     * Get mime type of selected track for this component.
     */
    public @Nullable String getMimeType() {
        return mimeType;
    }

    public long getDurationUs() {
        return durationUs;
    }

    public void release() {
        mContext = null;
        mMediaExtractor.release();
        mMediaExtractor = null;
    }

    /**
     * create me!
     */
    private void init() throws IOException {
        createExtractor();
        selectTrackIndex();
    }

    /**
     * Creates an extractor that reads its frames from {@link #mSrcUri}
     */
    private void createExtractor() throws IOException {
        mMediaExtractor = new MediaExtractor();
        mMediaExtractor.setDataSource(mContext, mSrcUri, null);
    }

    /**
     * Searches for and selects the track for the extractor to work on.
     */
    private void selectTrackIndex() {
        for (int index = 0; index < mMediaExtractor.getTrackCount(); ++index) {
            MediaFormat trackFormat = mMediaExtractor.getTrackFormat(index);
            String mimeType = trackFormat.getString(MediaFormat.KEY_MIME);

            if (mType == COMPONENT_TYPE_VIDEO && MimeUtil.isVideoFile(mimeType) ||
                mType == COMPONENT_TYPE_AUDIO && MimeUtil.isAudioFile(mimeType)) {

                mMediaExtractor.selectTrack(index);
                mSelectedTrackIndex = index;
                mTrackFormat = trackFormat;
                this.mimeType = mimeType;

                if (trackFormat.containsKey(MediaFormat.KEY_DURATION)) {
                    this.durationUs = trackFormat.getLong(MediaFormat.KEY_DURATION);
                } else {
                    this.durationUs = 0L;
                }
                return;
            }
        }

        mSelectedTrackIndex = -1;
        mTrackFormat = null;
        mimeType = null;
    }
}

/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2021 Threema GmbH
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

package ch.threema.app.video.transcoder;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

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

    private MediaExtractor mMediaExtractor;
    private MediaFormat mTrackFormat;
    private int mSelectedTrackIndex;

    /**
     *
     * @param context
     * @param srcUri
     * @param type
     * @throws IOException
     */
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
     * @return
     */
    public MediaExtractor getMediaExtractor() {
        return mMediaExtractor;
    }

    /**
     * The MediaFormat for the selected track of this component.
     * @return
     */
    public MediaFormat getTrackFormat() {
        return mTrackFormat;
    }

    /**
     * The index of the selected track for this component.
     * @return
     */
    public int getSelectedTrackIndex() {
        return mSelectedTrackIndex;
    }

    /**
     * The component type.
     * @return COMPONENT_TYPE_AUDIO or COMPONENT_TYPE_VIDEO
     */
    public int getType() {
        return mType;
    }

    public void release() {
        mContext = null;
        mMediaExtractor.release();
        mMediaExtractor = null;
    }

    /**
     * create me!
     * @throws IOException
     */
    private void init() throws IOException {
        createExtractor();
        selectTrackIndex();
    }

    /**
     * Creates an extractor that reads its frames from {@link #mSrcUri}
     *
     * @throws IOException
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
                return;
            }
        }

        mSelectedTrackIndex = -1;
        mTrackFormat = null;
    }
}

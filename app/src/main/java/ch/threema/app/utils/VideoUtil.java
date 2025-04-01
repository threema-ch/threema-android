/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2025 Threema GmbH
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;

import org.slf4j.Logger;

import java.io.IOException;

import ch.threema.base.utils.LoggingUtil;

public class VideoUtil {
    private static final Logger logger = LoggingUtil.getThreemaLogger("VideoUtil");

    /**
     * Get duration of a video represented by uri in Milliseconds
     *
     * @param context The context
     * @param uri     Uri of the video
     * @return Duration in ms or 0 if duration could not be determined
     */
    @SuppressLint("InlinedApi")
    public static long getVideoDuration(Context context, Uri uri) {
        long duration = 0;

        // handle file uris if file is not under control of the media store - e.g. selected with a file manager
        // do not use automatic resource management on MediaMetadataRetriever
        final MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            final String durationAsString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (durationAsString != null) {
                duration = Long.parseLong(durationAsString);
            }
        } catch (Exception e) {
            //do not show the exception!
            logger.error("Exception", e);
        } finally {
            try {
                retriever.release();
            } catch (IOException e) {
                logger.debug("Failed to release MediaMetadataRetriever");
            }
        }

        //duration fallback
        if (duration == 0) {
            try (Cursor durationCursor = MediaStore.Video.query(
                context.getContentResolver(),
                uri,
                new String[]{MediaStore.Video.VideoColumns.DURATION})) {

                if (durationCursor != null) {
                    if (durationCursor.moveToFirst()) {
                        duration = durationCursor.getLong(0);
                    }
                }
            } catch (Exception e) {
                logger.error("Exception", e);
            }
        }
        return duration;
    }

    @OptIn(markerClass = UnstableApi.class)
    public static ExoPlayer getExoPlayer(@NonNull Context context) {
        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(context);
        renderersFactory.setEnableDecoderFallback(true);
        if (ConfigUtils.hasAsyncMediaCodecBug()) {
            renderersFactory.forceDisableMediaCodecAsynchronousQueueing();
        }
        return new ExoPlayer.Builder(context, renderersFactory)
            .setSeekBackIncrementMs(5000)
            .setSeekForwardIncrementMs(5000)
            .build();
    }
}

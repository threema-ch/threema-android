/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2024 Threema GmbH
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
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.slf4j.Logger;

import java.io.IOException;

import ch.threema.base.utils.LoggingUtil;

public class VideoTranscoderUtil {
	private static final Logger logger = LoggingUtil.getThreemaLogger("VideoTranscoderUtil");
    private static final String KEY_ROTATION = "rotation"; // not to be confused with MediaFormat.KEY_ROTATION

	public static class OutputDimensions {
		public int width, height;
	}

	public static int getRoundedSize(float ratio, int size) {
		// width/height need to be a multiple of 2 otherwise mediacodec encoder will crash
		// with android.media.MediaCodec$CodecException: Error 0xfffffc0e
		return 16 * Math.round(size * ratio / 16);
	}

	public static int getOrientationHint(Context context, MediaComponent mediaComponent, @NonNull Uri srcUri) {
		MediaFormat trackFormat = mediaComponent.getTrackFormat();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && trackFormat != null && trackFormat.containsKey(KEY_ROTATION)) {
			return trackFormat.getInteger(KEY_ROTATION);
		} else {
			// do not use automatic resource management on MediaMetadataRetriever
			final MediaMetadataRetriever retriever = new MediaMetadataRetriever();
			try {
				retriever.setDataSource(context, srcUri);
				String orientation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
				if (!TextUtils.isEmpty(orientation)) {
					return Integer.parseInt(orientation);
				}
			} finally {
				try {
					retriever.release();
				} catch (IOException e) {
					logger.debug("Failed to release MediaMetadataRetriever");
				}
			}
		}
		return 0;
	}

	// smallest width should be equal or slightly larger than targetSize
	@Nullable
	public static OutputDimensions calculateOutputDimensions(@NonNull MediaComponent mediaComponent, int targetWidth, int targetHeight) {
		OutputDimensions outputDimensions = new OutputDimensions();
		MediaFormat trackFormat = mediaComponent.getTrackFormat();

		if (trackFormat != null) {
			int inputWidth = trackFormat.getInteger(MediaFormat.KEY_WIDTH);
			int inputHeight = trackFormat.getInteger(MediaFormat.KEY_HEIGHT);

			if (inputWidth > targetWidth || inputHeight > targetHeight) {
				float ratio = Math.max(targetWidth / (float) inputWidth, targetHeight / (float) inputHeight);
				outputDimensions.height = Math.round(inputHeight * ratio);
				outputDimensions.width = Math.round(inputWidth * ratio);
			} else {
				outputDimensions.height = inputHeight;
				outputDimensions.width = inputWidth;
			}

			logger.info("Target size: {}x{}, Input dimensions: {}x{}, Output dimensions: {}x{}", targetWidth, targetHeight, inputWidth, inputHeight, outputDimensions.width, outputDimensions.height);

			return outputDimensions;
		}
		return null;
	}
}

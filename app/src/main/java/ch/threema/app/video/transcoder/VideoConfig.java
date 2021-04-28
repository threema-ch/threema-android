/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2021 Threema GmbH
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
import android.media.MediaMetadataRetriever;
import android.text.format.DateUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.threema.app.services.PreferenceService;
import ch.threema.app.ui.MediaItem;
import ch.threema.base.ThreemaException;

import static ch.threema.app.ThreemaApplication.MAX_BLOB_SIZE;
import static ch.threema.app.utils.MimeUtil.MIME_AUDIO;
import static ch.threema.app.utils.MimeUtil.MIME_VIDEO;

public class VideoConfig {
	private static final Logger logger = LoggerFactory.getLogger(VideoConfig.class);

	public static final int BITRATE_LOW = 384000;
	public static final int BITRATE_MEDIUM = 1500000;
	public static final int BITRATE_DEFAULT = 2000000;

	public static final int AUDIO_BITRATE_LOW = 32000;
	public static final int AUDIO_BITRATE_MEDIUM = 64000;
	public static final int AUDIO_BITRATE_DEFAULT = 128000;

	// longest edge of video
	public static final int VIDEO_SIZE_MEDIUM = 848;
	public static final int VIDEO_SIZE_SMALL = 480;

	private static final int FILE_OVERHEAD = 48 * 1024;

	public static int getPreferredVideoBitrate(int videoSizeId) {
		switch (videoSizeId) {
			case PreferenceService.VideoSize_MEDIUM:
				return BITRATE_MEDIUM;
			case PreferenceService.VideoSize_SMALL:
				return BITRATE_LOW;
		}
		return BITRATE_DEFAULT;
	}

	public static int getPreferredVideoDimensions(int videoSizeId) {
		int maxSize = 0;
		switch (videoSizeId) {
			case PreferenceService.VideoSize_SMALL:
				maxSize = VIDEO_SIZE_SMALL;
				break;
			case PreferenceService.VideoSize_MEDIUM:
				maxSize = VIDEO_SIZE_MEDIUM;
				break;
			case PreferenceService.VideoSize_ORIGINAL:
				maxSize = 65535;
				break;
		}
		return maxSize;
	}

	public static int getPreferredAudioBitrate(int videoSizeId) {
		switch (videoSizeId) {
			case PreferenceService.VideoSize_MEDIUM:
				return AUDIO_BITRATE_MEDIUM;
			case PreferenceService.VideoSize_SMALL:
				return AUDIO_BITRATE_LOW;
		}
		return AUDIO_BITRATE_DEFAULT;
	}

	public static int getMaxSizeFromBitrate(int bitrate) {
		switch (bitrate) {
			case BITRATE_MEDIUM:
				return VIDEO_SIZE_MEDIUM;
			case BITRATE_LOW:
				return VIDEO_SIZE_SMALL;
		}
		return 0;
	}

	/**
	 * Returns the ID of the first track we find for the given mime type
	 * @param extractor
	 * @param mimeType
	 * @return ID of first matching track or -1 if none was found
	 */
	private static int findTrack(MediaExtractor extractor, String mimeType) {
		// Select the first video track we find, ignore the rest.
		int numTracks = extractor.getTrackCount();
		for (int i = 0; i < numTracks; i++) {
			MediaFormat format = extractor.getTrackFormat(i);
			String mime = format.getString(MediaFormat.KEY_MIME);
			logger.info("Found track " + i + " of format " + mime);
			if (mime != null && mime.startsWith(mimeType)) {
				logger.debug("Extractor selected track " + i + " (" + mime + "): " + format);
				return i;
			}
		}
		return -1;
	}

	/**
	 * Return ideal video bitrate from a set of predefined values keeping in account user setting and MAX_BLOB_SIZE restriction
	 * @param mediaItem Media Item representing this video
	 * @return target bitrate, -1 if the resulting file would not fit regardless of bitrate, or 0 if no change of bitrate is necessary
	 * @throws ThreemaException
	 */
	public static int getTargetVideoBitrate(Context context, MediaItem mediaItem, int videoSize) throws ThreemaException {
		int originalBitrate, targetBitrate;
		int preferredBitrate = getPreferredVideoBitrate(videoSize);

		// do not use automatic resource management on MediaMetadataRetriever
		MediaMetadataRetriever metaRetriever = new MediaMetadataRetriever();
		try {
			metaRetriever.setDataSource(context, mediaItem.getUri());
			originalBitrate = Integer.parseInt(metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE));
		} catch (Exception e) {
			logger.error("Exception querying MediaMetaDataRetriever", e);
			throw new ThreemaException(e.getMessage());
		} finally {
			metaRetriever.release();
		}

		int calculatedAudioSize = 0;
		MediaExtractor extractor = null;

		try {
			extractor = new MediaExtractor();
			extractor.setDataSource(context, mediaItem.getUri(), null);
			int srcAudioTrack = findTrack(extractor, MIME_AUDIO);
			if (srcAudioTrack >= 0) {
				MediaFormat srcAudioFormat = extractor.getTrackFormat(srcAudioTrack);

				float durationS = 0, bitrate = 0;
				if (srcAudioFormat.containsKey(MediaFormat.KEY_DURATION)) {
					durationS = (float) srcAudioFormat.getLong(MediaFormat.KEY_DURATION) / 1000000;
				}

				if (srcAudioFormat.containsKey(MediaFormat.KEY_BIT_RATE)) {
					bitrate = srcAudioFormat.getInteger(MediaFormat.KEY_BIT_RATE);
				}

				calculatedAudioSize = (int) (durationS * bitrate / 8);
				logger.info("Estimated audio size (bytes): " + calculatedAudioSize);
			}

			int srcVideoTrack = findTrack(extractor, MIME_VIDEO);
			if (srcVideoTrack >= 0) {
				float durationS = (float) mediaItem.getDurationMs() / (float) DateUtils.SECOND_IN_MILLIS;
				int calculatedFileSize = ((int) (durationS * originalBitrate / 8)) + calculatedAudioSize + FILE_OVERHEAD;
				if (calculatedFileSize > MAX_BLOB_SIZE) {
					calculatedFileSize = ((int) (durationS * BITRATE_MEDIUM / 8)) + calculatedAudioSize + FILE_OVERHEAD;
					if (calculatedFileSize > MAX_BLOB_SIZE) {
						calculatedFileSize = ((int) (durationS * BITRATE_LOW / 8)) + calculatedAudioSize + FILE_OVERHEAD;
						if (calculatedFileSize > MAX_BLOB_SIZE) {
							return -1;
						} else {
							targetBitrate = BITRATE_LOW;
						}
					} else {
						targetBitrate = BITRATE_MEDIUM;
					}
				} else {
					targetBitrate = originalBitrate;
				}
			} else {
				throw new ThreemaException("No video track found in this file");
			}
		} catch (Exception e) {
			logger.error("Exception", e);
			throw new ThreemaException(e.getMessage());
		} finally {
			if (extractor != null) {
				extractor.release();
			}
		}

		if (targetBitrate < preferredBitrate) {
			logger.info("Preferred bit rate is {}. Falling back to bit rate {} due to size", preferredBitrate, targetBitrate);
		}

		if (targetBitrate > preferredBitrate && preferredBitrate != BITRATE_DEFAULT) {
			return preferredBitrate;
		}

		if (targetBitrate != originalBitrate) {
			return targetBitrate;
		}

		return 0; // no change necessary
	}
}

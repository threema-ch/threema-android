/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2021 Threema GmbH
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

package ch.threema.app.voicemessage;

import android.content.Context;
import android.media.MediaRecorder;
import android.net.Uri;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import ch.threema.app.utils.FileUtil;

import static ch.threema.app.voicemessage.VoiceRecorderActivity.DEFAULT_SAMPLING_RATE_HZ;

public class AudioRecorder implements MediaRecorder.OnErrorListener, MediaRecorder.OnInfoListener {
	private static final Logger logger = LoggerFactory.getLogger(AudioRecorder.class);

	private Context context;
	private MediaRecorder mediaRecorder;
	private OnStopListener onStopListener;

	public AudioRecorder(Context context) {
		this.context = context;
	}

	public MediaRecorder prepare(Uri uri, int maxDuration, int samplingRate) {
		logger.info("Preparing MediaRecorder with sampling rate {}", samplingRate);
		mediaRecorder = new MediaRecorder();

		mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
		mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
		mediaRecorder.setOutputFile(FileUtil.getRealPathFromURI(context, uri));
		mediaRecorder.setAudioChannels(1);
		mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
		mediaRecorder.setAudioEncodingBitRate(32000);
		mediaRecorder.setAudioSamplingRate(samplingRate != 0 ? samplingRate : DEFAULT_SAMPLING_RATE_HZ);
		mediaRecorder.setMaxFileSize(20L*1024*1024);

		mediaRecorder.setOnErrorListener(this);
		mediaRecorder.setOnInfoListener(this);

		try {
			mediaRecorder.prepare();
		} catch (IllegalStateException e) {
			logger.info("IllegalStateException preparing MediaRecorder: {}", e.getMessage());
			return null;
		} catch (IOException e) {
			logger.info("IOException preparing MediaRecorder: {}", e.getMessage());
			return null;
		}
		return mediaRecorder;
	}

	@Override
	public void onInfo(MediaRecorder mr, int what, int extra) {
		switch (what) {
			case MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED:
				logger.info("Max recording duration reached. ({})", extra);
				onStopListener.onRecordingStop();
				break;
			case MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED:
				logger.info("Max recording filesize reached. ({})", extra);
				onStopListener.onRecordingStop();
				break;
			case MediaRecorder.MEDIA_RECORDER_INFO_UNKNOWN:
				logger.info("Unknown media recorder info (What: {} / Extra: {})", what, extra);
				onStopListener.onRecordingCancel();
				break;
			default:
				logger.info("Undefined media recorder info type (What: {} / Extra: {})", what, extra);
				break;
		}
	}

	@Override
	public void onError(MediaRecorder mr, int what, int extra) {
		if (what == MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN) {
			logger.info("Unknown media recorder error (What: {}, Extra: {})", what, extra);
			onStopListener.onRecordingCancel();
		} else {
			logger.info("Undefined media recorder error type (What: {}, Extra: {})", what, extra);
		}
	}

	public interface OnStopListener {
		public void onRecordingStop();

		public void onRecordingCancel();
	}

	public void setOnStopListener(OnStopListener listener) {
		this.onStopListener = listener;
	}
}

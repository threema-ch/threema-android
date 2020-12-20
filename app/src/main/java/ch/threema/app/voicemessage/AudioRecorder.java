/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2020 Threema GmbH
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
		mediaRecorder = new MediaRecorder();

		mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
		mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
		mediaRecorder.setOutputFile(FileUtil.getRealPathFromURI(context, uri));
		mediaRecorder.setAudioChannels(1);
		mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
		mediaRecorder.setAudioEncodingBitRate(32000);
		mediaRecorder.setAudioSamplingRate(samplingRate != 0 ? samplingRate : DEFAULT_SAMPLING_RATE_HZ);
		mediaRecorder.setMaxFileSize(20*1024*1024);
		//trial to mitigate instant onInfo -> maxDurationReached triggers on some devices
//		mediaRecorder.setMaxDuration(maxDuration);

		mediaRecorder.setOnErrorListener(this);
		mediaRecorder.setOnInfoListener(this);

		try {
			mediaRecorder.prepare();
		} catch (IllegalStateException e) {
			logger.info("IllegalStateException preparing MediaRecorder: " + e.getMessage());
			return null;
		} catch (IOException e) {
			logger.info("IOException preparing MediaRecorder: " + e.getMessage());
			return null;
		}
		return mediaRecorder;
	}

	@Override
	public void onInfo(MediaRecorder mr, int what, int extra) {
		switch (what) {
			case MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED:
				logger.info("Max recording duration reached.");
				onStopListener.onRecordingStop();
				break;
			case MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED:
				logger.info("Max recording filesize reached.");
				onStopListener.onRecordingStop();
				break;
			case MediaRecorder.MEDIA_RECORDER_INFO_UNKNOWN:
				logger.info("Unknown media recorder info");
				onStopListener.onRecordingCancel();
				break;
		}
	}

	@Override
	public void onError(MediaRecorder mr, int what, int extra) {
		switch (what) {
			case MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN:
				logger.info("Unkown media recorder error");
				onStopListener.onRecordingCancel();
				break;
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

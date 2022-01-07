/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2022 Threema GmbH
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

package ch.threema.app.fragments.mediaviews;

import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.exoplayer2.ui.DefaultTimeBar;
import com.google.android.exoplayer2.ui.TimeBar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;

import androidx.core.view.ViewCompat;
import ch.threema.app.R;
import ch.threema.app.activities.MediaViewerActivity;
import ch.threema.app.utils.MediaPlayerStateWrapper;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import pl.droidsonroids.gif.GifImageView;

import static ch.threema.app.R.id.position_container;
import static ch.threema.app.utils.StringConversionUtil.getDurationString;

public class MediaPlayerViewFragment extends AudioFocusSupportingMediaViewFragment implements TimeBar.OnScrubListener, MediaPlayerStateWrapper.StateListener {
	private static final Logger logger = LoggerFactory.getLogger(MediaPlayerViewFragment.class);

	private WeakReference<GifImageView> imageViewRef;
	private WeakReference<ImageView> previewViewRef;
	private WeakReference<TextView> filenameViewRef, positionRef, durationRef;
	private WeakReference<DefaultTimeBar> timeBarRef;
	private WeakReference<ProgressBar> progressBarRef;
	private WeakReference<ImageButton> playRef, pauseRef;
	private WeakReference<FrameLayout> playPauseLayoutRef;
	private WeakReference<View> controllerViewRef;

	private MediaPlayerStateWrapper mediaPlayer;
	private boolean isImmediatePlay;

	private Handler progressBarHandler = new Handler();

	public MediaPlayerViewFragment() { super(); }

	@Override
	protected int getFragmentResourceId() {
		return R.layout.fragment_media_viewer_mediaplayer;
	}

	@Override
	public boolean inquireClose() { return true; }

	@Override
	protected void showThumbnail(Bitmap thumbnail, boolean isGeneric, String filename) {
		if (this.imageViewRef.get() != null) {
			this.setOnClickListener(null);

			if (thumbnail != null && !thumbnail.isRecycled()) {
				if (isGeneric) {
					if (!TestUtil.empty(filename)) {
						this.filenameViewRef.get().setText(filename);
						this.filenameViewRef.get().setVisibility(View.VISIBLE);
					}
				}
				this.previewViewRef.get().setImageBitmap(thumbnail);
				this.previewViewRef.get().setVisibility(View.VISIBLE);
			} else {
				this.previewViewRef.get().setVisibility(View.INVISIBLE);
			}
			this.imageViewRef.get().setVisibility(View.INVISIBLE);
		}
	}

	@Override
	protected void hideThumbnail() {
		this.previewViewRef.get().setVisibility(View.INVISIBLE);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		this.isImmediatePlay = getArguments().getBoolean(MediaViewerActivity.EXTRA_ID_IMMEDIATE_PLAY, false);

		this.mediaPlayer = new MediaPlayerStateWrapper();
		this.mediaPlayer.setStateListener(this);

		return super.onCreateView(inflater, container, savedInstanceState);
	}

	@Override
	protected void created(Bundle savedInstanceState) {
		ViewGroup rootView = rootViewReference.get();

		this.imageViewRef = new WeakReference<>(rootView.findViewById(R.id.gif_view));
		this.previewViewRef = new WeakReference<>(rootView.findViewById(R.id.preview_image));
		this.filenameViewRef = new WeakReference<>(rootView.findViewById(R.id.filename_view));
		this.positionRef = new WeakReference<>(rootView.findViewById(R.id.exo_position));
		this.durationRef = new WeakReference<>(rootView.findViewById(R.id.exo_duration));
		this.timeBarRef = new WeakReference<>(rootView.findViewById(R.id.exo_progress));
		this.playRef = new WeakReference<>(rootView.findViewById(R.id.exo_play));
		this.pauseRef = new WeakReference<>(rootView.findViewById(R.id.exo_pause));
		this.playPauseLayoutRef = new WeakReference<>(rootView.findViewById(R.id.play_pause_layout));
		this.progressBarRef = new WeakReference<>(rootView.findViewById(R.id.progress_bar));
		this.controllerViewRef = new WeakReference<>(rootView.findViewById(position_container));
		ViewCompat.setOnApplyWindowInsetsListener(controllerViewRef.get(), (v, insets) -> {
			ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
			params.leftMargin = insets.getSystemWindowInsetLeft();
			params.rightMargin = insets.getSystemWindowInsetRight();
			params.bottomMargin = insets.getSystemWindowInsetBottom();
			return insets;
		});

		this.playRef.get().setVisibility(View.GONE);
		this.pauseRef.get().setVisibility(View.GONE);
		this.playPauseLayoutRef.get().setVisibility(View.GONE);
		this.positionRef.get().setText(getDurationString(0));

		this.playRef.get().setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				resumeAudio();
			}
		});

		this.pauseRef.get().setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				pauseAudio();
			}
		});

		this.timeBarRef.get().addListener(this);
	}

	@Override
	public void onDestroyView() {
		abandonFocus();

		if (mediaPlayer != null) {
			mediaPlayer.setScreenOnWhilePlaying(false);
			mediaPlayer.stop();
			mediaPlayer.reset();
			mediaPlayer.release();
		}
		super.onDestroyView();
	}

	@Override
	public void setUserVisibleHint(boolean isVisibleToUser) {
		logger.debug("setUserVisibleHint = " + isVisibleToUser);

		// stop player if fragment comes out of view
		if (!isVisibleToUser) {
			pauseAudio();
		}
	}

	@Override
	public void onPause() {
		setUserVisibleHint(false);
		super.onPause();
	}

	@Override
	protected void handleDecryptingFile() {
		if (progressBarRef.get() != null) {
			this.playPauseLayoutRef.get().setVisibility(View.GONE);
			this.progressBarRef.get().setVisibility(View.VISIBLE);
		}
	}

	@Override
	protected void handleDecryptFailure() {
		if (this.progressBarRef.get() != null) {
			this.progressBarRef.get().setVisibility(View.GONE);
			this.controllerViewRef.get().setVisibility(View.GONE);
		}
		super.showBrokenImage();
	}

	@Override
	protected void handleDecryptedFile(final File file) {
		if (this.isAdded()) {
			this.progressBarRef.get().setVisibility(View.GONE);
			this.playRef.get().setVisibility(View.VISIBLE);
			this.pauseRef.get().setVisibility(View.GONE);
			this.playPauseLayoutRef.get().setVisibility(View.VISIBLE);

			if (this.mediaPlayer.getState() == MediaPlayerStateWrapper.State.PREPARED) {
				// navigated back to fragment
				if (this.mediaPlayer.getState() == MediaPlayerStateWrapper.State.PAUSED) {
					if (this.isImmediatePlay) {
						resumeAudio();
					}
				}
			} else {
				// new fragment
				if (this.mediaPlayer.getState() != MediaPlayerStateWrapper.State.PREPARING) {
					prepareAudio(Uri.fromFile(file));
					if (this.isImmediatePlay) {
						playAudio();
					}
				}
			}
		} else {
			logger.debug("Fragment no longer added. Get out of here");
		}
	}

	private void prepareAudio(Uri uri) {
		if (this.mediaPlayer != null) {
			this.mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			this.mediaPlayer.setDataSource(getContext(), uri);
			try {
				this.mediaPlayer.prepare();
				this.durationRef.get().setText(getDurationString(this.mediaPlayer.getDuration()));
				this.timeBarRef.get().setDuration(this.mediaPlayer.getDuration());
			} catch (IOException e) {
				logger.error("Exception", e);
			}
		}
	}

	private void playAudio() {
		if (this.mediaPlayer != null) {
			if (requestFocus()) {
				this.mediaPlayer.setScreenOnWhilePlaying(true);
				this.mediaPlayer.start();
				this.pauseRef.get().setVisibility(View.VISIBLE);
				this.playRef.get().setVisibility(View.GONE);
				initProgressListener();
			}
		}
	}

	@Override
	public void stopAudio() {
		if (this.mediaPlayer != null) {
			this.mediaPlayer.setScreenOnWhilePlaying(false);
			this.mediaPlayer.stop();
			this.pauseRef.get().setVisibility(View.GONE);
			this.playRef.get().setVisibility(View.VISIBLE);
			stopProgressListener();
			abandonFocus();
		}
	}

	@Override
	public void pauseAudio() {
		if (this.mediaPlayer != null) {
			this.mediaPlayer.setScreenOnWhilePlaying(false);
			this.mediaPlayer.pause();
			this.pauseRef.get().setVisibility(View.GONE);
			this.playRef.get().setVisibility(View.VISIBLE);
			stopProgressListener();
			abandonFocus();
		}
	}

	@Override
	public void resumeAudio() {
		if (this.mediaPlayer != null) {
			switch (this.mediaPlayer.getState()) {
				case PAUSED:
				case PREPARED:
					if (requestFocus()) {
						playAudio();
					}
					break;
				default:
					break;
			}
		}
	}

	@Override
	public void setVolume(float volume) {
		if (mediaPlayer != null) {
			mediaPlayer.setVolume(volume, volume);
		}
	}

	private void initProgressListener() {
		RuntimeUtil.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (mediaPlayer != null){
					timeBarRef.get().setPosition(mediaPlayer.getCurrentPosition());
					positionRef.get().setText(getDurationString(mediaPlayer.getCurrentPosition()));
				}
				progressBarHandler.postDelayed(this, 1000);
			}
		});
	}

	private void stopProgressListener() {
		progressBarHandler.removeCallbacksAndMessages(null);
	}

	@Override
	public void onScrubStart(TimeBar timeBar, long position) {}

	@Override
	public void onScrubMove(TimeBar timeBar, long position) {}

	@Override
	public void onScrubStop(TimeBar timeBar, long position, boolean canceled) {
		if (!canceled) {
			mediaPlayer.seekTo((int) position);
		}
	}

	@Override
	public void onCompletion(MediaPlayer mp) {
		stopAudio();
	}

	@Override
	public void onPrepared(MediaPlayer mp) {}
}

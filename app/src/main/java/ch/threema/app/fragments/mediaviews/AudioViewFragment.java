/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2020 Threema GmbH
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
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.ref.WeakReference;

import androidx.annotation.UiThread;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import ch.threema.app.R;
import ch.threema.app.activities.MediaViewerActivity;
import ch.threema.app.mediaattacher.PreviewFragmentInterface;

public class AudioViewFragment extends AudioFocusSupportingMediaViewFragment implements Player.EventListener, PreviewFragmentInterface {
	private static final Logger logger = LoggerFactory.getLogger(AudioViewFragment.class);

	private WeakReference<ProgressBar> progressBarRef;
	private WeakReference<PlayerView> audioView;
	private SimpleExoPlayer audioPlayer;
	private boolean isImmediatePlay, isPreparing;

	public AudioViewFragment() {
		super();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		this.isImmediatePlay = getArguments().getBoolean(MediaViewerActivity.EXTRA_ID_IMMEDIATE_PLAY, false);

		try {
			this.audioPlayer = ExoPlayerFactory.newSimpleInstance(getContext());
			this.audioPlayer.addListener(this);
		} catch (OutOfMemoryError e) {
			logger.error("Exception", e);
		}
		return super.onCreateView(inflater, container, savedInstanceState);
	}

	@Override
	protected int getFragmentResourceId() {
		return R.layout.fragment_media_viewer_audio;
	}

	@Override
	public boolean inquireClose() {
		return true;
	}

	@Override
	protected void showThumbnail(Bitmap thumbnail, boolean isGeneric, String filename) {
		if (this.audioView != null && this.audioView.get() != null) {
			this.audioView.get().setDefaultArtwork(new BitmapDrawable(getResources(), thumbnail));
		}
	}

	@Override
	protected void hideThumbnail() {}

	@Override
	protected void handleDecryptingFile() {
		if (progressBarRef.get() != null) {
			this.progressBarRef.get().setVisibility(View.VISIBLE);
		}
	}

	@Override
	protected void handleDecryptFailure() {
		if (this.progressBarRef.get() != null) {
			this.progressBarRef.get().setVisibility(View.GONE);
		}
		if (this.audioView != null && this.audioView.get() != null) {
			this.audioView.get().setUseController(false);
		}
		super.showBrokenImage();
	}

	@Override
	protected void created(Bundle savedInstanceState) {
		PlayerView audioView = rootViewReference.get().findViewById(R.id.audio_view);
		this.audioView = new WeakReference<>(audioView);

		if (this.audioPlayer != null) {
			audioView.setControllerVisibilityListener(new PlayerControlView.VisibilityListener() {
				@Override
				public void onVisibilityChange(int visibility) {
					showUi(visibility == View.VISIBLE);
				}
			});
			audioView.setPlayer(this.audioPlayer);
			audioView.setControllerHideOnTouch(true);
			audioView.setControllerShowTimeoutMs(-1);
			audioView.setControllerAutoShow(true);
			View controllerView = audioView.findViewById(R.id.position_container);
			ViewCompat.setOnApplyWindowInsetsListener(controllerView, new OnApplyWindowInsetsListener() {
				@Override
				public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat insets) {
					ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
					params.leftMargin = insets.getSystemWindowInsetLeft();
					params.rightMargin = insets.getSystemWindowInsetRight();
					params.bottomMargin = insets.getSystemWindowInsetBottom();
					return insets;
				}
			});
		}

		this.progressBarRef = new WeakReference<>(rootViewReference.get().findViewById(R.id.progress_bar));
	}

	@Override
	protected void handleDecryptedFile(final File file) {
		if (this.isAdded()) {
			if (this.audioPlayer != null && this.audioPlayer.getPlaybackState() == ExoPlayer.STATE_READY) {
				// navigated back to fragment
				playAudio(this.isImmediatePlay);
			} else {
				// new fragment
				loadAudio(Uri.fromFile(file));
			}
		}
	}

	@UiThread
	private void playAudio(boolean play) {
		progressBarRef.get().setVisibility(View.GONE);

		if (this.audioPlayer != null) {
			audioPlayer.setPlayWhenReady(play);
		}
	}

	private void loadAudio(Uri audioUri) {
		if (this.audioPlayer != null) {
			DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(getContext(), Util.getUserAgent(getContext(), getContext().getString(R.string.app_name)));
			MediaSource audioSource = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(audioUri);

			this.audioPlayer.setPlayWhenReady(this.isImmediatePlay);
			this.isPreparing = true;
			this.audioPlayer.prepare(audioSource);
		}
	}

	protected void showBrokenImage() {
		if (this.progressBarRef.get() != null) {
			this.progressBarRef.get().setVisibility(View.GONE);
		}
	}

	@Override
	public void onTimelineChanged(Timeline timeline, Object manifest, int reason) {}

	@Override
	public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {}

	@Override
	public void onLoadingChanged(boolean isLoading) {
		if (isLoading) {
			this.progressBarRef.get().setVisibility(View.VISIBLE);
		} else {
			this.progressBarRef.get().setVisibility(View.GONE);
		}
	}

	@Override
	public void onIsPlayingChanged(boolean isPlaying) {
		if (isPlaying) {
			requestFocus();
		} else {
			abandonFocus();
		}
	}

	@Override
	public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
		if (isPreparing && playbackState == Player.STATE_READY) {
			// this is accurate
			isPreparing = false;

			this.progressBarRef.get().setVisibility(View.GONE);
			this.audioView.get().showController();
		}

		if (playbackState == Player.STATE_ENDED) {
			this.audioPlayer.setPlayWhenReady(false);
			this.audioPlayer.seekTo(0);
			this.audioView.get().showController();
		}
	}

	@Override
	public void onDestroyView() {
		abandonFocus();

		if (this.audioPlayer != null) {
			this.audioPlayer.release();
			this.audioPlayer = null;
		}

		super.onDestroyView();
	}

	@Override
	public void onRepeatModeChanged(int repeatMode) {}

	@Override
	public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {}

	@Override
	public void onPlayerError(ExoPlaybackException error) {}

	@Override
	public void onPositionDiscontinuity(int reason) {}

	@Override
	public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {}

	@Override
	public void onSeekProcessed() {}

	@Override
	public void setUserVisibleHint(boolean isVisibleToUser) {
		// stop player if fragment comes out of view
		if (!isVisibleToUser && this.audioPlayer != null &&
				(this.audioPlayer.isLoading() ||
				this.audioPlayer.getPlaybackState() != ExoPlayer.STATE_IDLE)) {
			this.audioPlayer.setPlayWhenReady(false);
		}
	}

	@Override
	public void setVolume(float volume) {
		// ducking
		if (this.audioPlayer != null) {
			this.audioPlayer.setVolume(volume);
		}
	}
}

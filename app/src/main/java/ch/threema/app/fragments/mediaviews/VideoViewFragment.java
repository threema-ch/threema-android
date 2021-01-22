/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2021 Threema GmbH
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
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.ref.WeakReference;

import androidx.annotation.UiThread;
import androidx.core.view.ViewCompat;
import ch.threema.app.R;
import ch.threema.app.activities.MediaViewerActivity;
import ch.threema.app.ui.ZoomableExoPlayerView;
import ch.threema.app.utils.TestUtil;

public class VideoViewFragment extends AudioFocusSupportingMediaViewFragment implements Player.EventListener {
	private static final Logger logger = LoggerFactory.getLogger(VideoViewFragment.class);

	private WeakReference<ImageView> previewImageViewRef;
	private WeakReference<ProgressBar> progressBarRef;
	private WeakReference<ZoomableExoPlayerView> videoViewRef;
	private SimpleExoPlayer videoPlayer;
	private boolean isImmediatePlay, isPreparing;

	public VideoViewFragment() {
		super();
		logger.debug("new instance");
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		logger.debug("onCreateView");

		this.isImmediatePlay = getArguments().getBoolean(MediaViewerActivity.EXTRA_ID_IMMEDIATE_PLAY, false);

		try {
			this.videoPlayer = new SimpleExoPlayer.Builder(getContext()).build();
			this.videoPlayer.addListener(this);
		} catch (OutOfMemoryError e) {
			logger.error("Exception", e);
		}
		return super.onCreateView(inflater, container, savedInstanceState);
	}

	@Override
	protected int getFragmentResourceId() {
		return R.layout.fragment_media_viewer_video;
	}

	@Override
	public boolean inquireClose() {
		logger.debug("inquireClose");

		return true;
	}

	@Override
	protected void showThumbnail(Bitmap thumbnail, boolean isGeneric, String filename) {
		logger.debug("showThumbnail");

		if(TestUtil.required(this.previewImageViewRef, this.previewImageViewRef.get(), thumbnail)) {
			if (!thumbnail.isRecycled()) {
				this.previewImageViewRef.get().setImageBitmap(thumbnail);
			}
		}
	}

	@Override
	protected void hideThumbnail() {
		logger.debug("hideThumbnail");
	}

	@Override
	protected void handleDecryptingFile() {
		logger.debug("handleDecryptingFile");

		if (progressBarRef.get() != null) {
			this.progressBarRef.get().setVisibility(View.VISIBLE);
		}
	}

	@Override
	protected void handleDecryptFailure() {
		if (progressBarRef.get() != null) {
			this.progressBarRef.get().setVisibility(View.GONE);
		}
	}

	@Override
	protected void created(Bundle savedInstanceState) {
		logger.debug("created");

		if (rootViewReference.get() != null && this.videoPlayer != null) {
			this.previewImageViewRef = new WeakReference<>(rootViewReference.get().findViewById(R.id.image));

			this.videoViewRef = new WeakReference<>(rootViewReference.get().findViewById(R.id.video_view));
			this.videoViewRef.get().setControllerVisibilityListener(new PlayerControlView.VisibilityListener() {
				@Override
				public void onVisibilityChange(int visibility) {
					VideoViewFragment.this.showUi(visibility == View.VISIBLE);
				}
			});
			this.videoViewRef.get().setVisibility(View.GONE);
			this.videoViewRef.get().setPlayer(this.videoPlayer);
			this.videoViewRef.get().setControllerHideOnTouch(true);
			this.videoViewRef.get().setControllerShowTimeoutMs(MediaViewerActivity.ACTIONBAR_TIMEOUT);
			this.videoViewRef.get().setControllerAutoShow(true);

			logger.debug("View Type: " + (this.videoViewRef.get().getVideoSurfaceView() instanceof TextureView ? "Texture" : "Surface"));

			View controllerView = this.videoViewRef.get().findViewById(R.id.position_container);
			ViewCompat.setOnApplyWindowInsetsListener(controllerView, (v, insets) -> {
				ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
				params.leftMargin = insets.getSystemWindowInsetLeft();
				params.rightMargin = insets.getSystemWindowInsetRight();
				params.bottomMargin = insets.getSystemWindowInsetBottom();
				return insets;
			});

			this.progressBarRef = new WeakReference<>(rootViewReference.get().findViewById(R.id.progress_bar));
		}
	}

	@Override
	protected void handleDecryptedFile(final File file) {
		logger.debug("handleDecryptedFile");

		if (this.isAdded()) {
			if (this.videoPlayer != null && this.videoPlayer.getPlaybackState() == Player.STATE_READY) {
				// navigated back to fragment
				playVideo(this.isImmediatePlay);
			} else {
				// new fragment
				loadVideo(Uri.fromFile(file));
			}
		} else {
			logger.debug("Fragment no longer added. Get out of here");
		}
	}

	@UiThread
	private void playVideo(boolean play) {
		logger.debug("playVideo");

		videoViewRef.get().setVisibility(View.VISIBLE);
		previewImageViewRef.get().setVisibility(View.GONE);
		progressBarRef.get().setVisibility(View.GONE);

		videoPlayer.setPlayWhenReady(play);
	}

	private void loadVideo(Uri videoUri) {
		logger.debug("loadVideo");

		if (this.videoPlayer != null) {
			DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(getContext(), Util.getUserAgent(getContext(), getContext().getString(R.string.app_name)));
			MediaSource videoSource = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(videoUri);

			this.videoPlayer.setPlayWhenReady(this.isImmediatePlay);

			this.isPreparing = true;
			this.videoPlayer.prepare(videoSource);

			this.progressBarRef.get().setVisibility(View.VISIBLE);

			this.videoViewRef.get().setVisibility(View.GONE);
			this.previewImageViewRef.get().setVisibility(View.VISIBLE);
		}
	}

	protected void showBrokenImage() {
		logger.debug("showBrokenImage");

		if (this.progressBarRef.get() != null) {
			this.progressBarRef.get().setVisibility(View.GONE);
		}
		super.showBrokenImage();
	}

	@Override
	public void onDestroyView() {
		logger.debug("onDestroyView");

		abandonFocus();

		if (this.videoPlayer != null) {
			this.videoPlayer.release();
			this.videoPlayer = null;
		}

		super.onDestroyView();
	}

	@Override
	public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
		logger.debug("onTracksChanged");
	}

	@Override
	public void onLoadingChanged(boolean isLoading) {
		logger.debug("onLoadingChanged = " + isLoading);
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
		logger.debug("onPlayerStateChanged = " + playbackState);

		if (isPreparing && playbackState == Player.STATE_READY) {
			isPreparing = false;

			this.progressBarRef.get().setVisibility(View.GONE);
			this.videoViewRef.get().setVisibility(View.VISIBLE);
			this.previewImageViewRef.get().setVisibility(View.GONE);
		}
		if (playbackState == Player.STATE_ENDED) {
			this.videoPlayer.setPlayWhenReady(false);
			this.videoPlayer.seekTo(0);
			this.videoViewRef.get().showController();
		}

		keepScreenOn(playbackState != Player.STATE_IDLE);
	}

	@Override
	public void onRepeatModeChanged(int repeatMode) {
		logger.debug("onRepeatModeChanged");
	}

	@Override
	public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {}

	@Override
	public void onPlayerError(ExoPlaybackException error) {
		logger.info("ExoPlaybackException = " + error.getMessage());

		this.progressBarRef.get().setVisibility(View.GONE);

		Toast.makeText(getContext(), R.string.unable_to_play_video, Toast.LENGTH_SHORT).show();
	}

	@Override
	public void onPositionDiscontinuity(int reason) {
		logger.debug("onPositionDiscontinuity");
	}

	@Override
	public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
		logger.debug("onPlaybackParametersChanged");
	}

	@Override
	public void onSeekProcessed() {}

	@Override
	public void setUserVisibleHint(boolean isVisibleToUser) {
		logger.debug("setUserVisibleHint = " + isVisibleToUser);

		// stop player if fragment comes out of view
		if (!isVisibleToUser && this.videoPlayer != null &&
				(this.videoPlayer.isLoading() ||
				this.videoPlayer.getPlaybackState() != Player.STATE_IDLE)) {
			this.videoPlayer.setPlayWhenReady(false);
		}
	}

	@Override
	public void onPause() {
		setUserVisibleHint(false);
		super.onPause();
	}

	@Override
	public void setVolume(float volume) {
		// ducking
		if (this.videoPlayer != null) {
			this.videoPlayer.setVolume(volume);
		}
	}
}

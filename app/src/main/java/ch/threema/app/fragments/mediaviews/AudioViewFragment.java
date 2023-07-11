/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2023 Threema GmbH
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.core.content.res.ResourcesCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.PlayerControlView;
import androidx.media3.ui.PlayerView;

import com.google.android.material.progressindicator.CircularProgressIndicator;

import org.slf4j.Logger;

import java.io.File;
import java.lang.ref.WeakReference;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.MediaViewerActivity;
import ch.threema.app.mediaattacher.PreviewFragmentInterface;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.IconUtil;
import ch.threema.app.utils.MimeUtil;
import ch.threema.app.utils.VideoUtil;
import ch.threema.base.utils.LoggingUtil;

@SuppressLint("UnsafeOptInUsageError")
public class AudioViewFragment extends MediaViewFragment implements Player.Listener, PreviewFragmentInterface {
	private static final Logger logger = LoggingUtil.getThreemaLogger("AudioViewFragment");

	private WeakReference<CircularProgressIndicator> progressBarRef;
	private WeakReference<PlayerView> audioView;
	private ExoPlayer audioPlayer;
	private boolean isImmediatePlay, isPreparing;

	public AudioViewFragment() {
		super();
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		this.isImmediatePlay = getArguments().getBoolean(MediaViewerActivity.EXTRA_ID_IMMEDIATE_PLAY, false);
		boolean isVoiceMessage = getArguments().getBoolean(MediaViewerActivity.EXTRA_IS_VOICEMESSAGE, false);

		AudioAttributes audioAttributes = new AudioAttributes.Builder()
			.setUsage(C.USAGE_MEDIA)
			.setContentType(isVoiceMessage ? C.AUDIO_CONTENT_TYPE_SPEECH : C.AUDIO_CONTENT_TYPE_MUSIC)
			.setAllowedCapturePolicy(C.ALLOW_CAPTURE_BY_NONE)
			.build();

		try {
			this.audioPlayer = VideoUtil.getExoPlayer(getContext());
			this.audioPlayer.setAudioAttributes(audioAttributes, true);
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
			audioView.setDefaultArtwork(ResourcesCompat.getDrawable(getResources(), IconUtil.getMimeCategoryIcon(MimeUtil.MimeCategory.AUDIO), ThreemaApplication.getAppContext().getTheme()));
			ConfigUtils.adjustExoPlayerControllerMargins(getContext(), audioView);
		}

		this.progressBarRef = new WeakReference<>(rootViewReference.get().findViewById(R.id.progress_bar));
	}

	@Override
	protected void showThumbnail(@NonNull Drawable thumbnail) {
		if (this.audioView != null && this.audioView.get() != null) {
			this.audioView.get().setDefaultArtwork(thumbnail);
		}
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
		Context context = getContext();
		if (this.audioPlayer != null && context != null) {
			DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(context, new DefaultHttpDataSource.Factory().setUserAgent(Util.getUserAgent(context, getContext().getString(R.string.app_name))));
			MediaSource audioSource = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(audioUri));

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
	public void onLoadingChanged(boolean isLoading) {
		if (isLoading) {
			this.progressBarRef.get().setVisibility(View.VISIBLE);
		} else {
			this.progressBarRef.get().setVisibility(View.GONE);
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
		if (this.audioPlayer != null) {
			this.audioPlayer.release();
			this.audioPlayer = null;
		}

		super.onDestroyView();
	}

	@Override
	public void setUserVisibleHint(boolean isVisibleToUser) {
		// stop player if fragment comes out of view
		if (!isVisibleToUser && this.audioPlayer != null &&
				(this.audioPlayer.isLoading() || this.audioPlayer.isPlaying())) {
			this.audioPlayer.setPlayWhenReady(false);
			this.audioPlayer.pause();
		}
	}
}

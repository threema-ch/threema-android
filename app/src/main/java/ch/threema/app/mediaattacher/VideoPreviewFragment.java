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

package ch.threema.app.mediaattacher;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import ch.threema.app.R;
import ch.threema.app.ui.ZoomableExoPlayerView;
import ch.threema.app.utils.RuntimeUtil;

public class VideoPreviewFragment extends PreviewFragment implements DefaultLifecycleObserver, Player.EventListener, PreviewFragmentInterface {
	private static final Logger logger = LoggerFactory.getLogger(VideoPreviewFragment.class);

	private ZoomableExoPlayerView videoView;
	private SimpleExoPlayer videoPlayer;

	VideoPreviewFragment(MediaAttachItem mediaItem, MediaAttachViewModel mediaAttachViewModel){
		super(mediaItem, mediaAttachViewModel);
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		this.rootView = inflater.inflate(R.layout.fragment_video_preview, container, false);

		this.getViewLifecycleOwner().getLifecycle().addObserver(this);

		return this.rootView;
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		if (rootView != null) {
			this.videoView = rootView.findViewById(R.id.video_view);

			ImageButton play = rootView.findViewById(R.id.exo_play);
			ImageButton pause = rootView.findViewById(R.id.exo_pause);
			play.setImageResource(R.drawable.ic_play);
			pause.setImageResource(R.drawable.ic_pause);
		}
	}

	@Override
	public void onCreate(@NonNull LifecycleOwner owner) {}

	@Override
	public void onResume(@NonNull LifecycleOwner owner) {
		if (this.videoPlayer == null)  {
			initializePlayer(true);
		} else if (!this.videoPlayer.isPlaying()) {
			this.videoPlayer.play();
		}
	}

	@Override
	public void onPause(@NonNull LifecycleOwner owner) {
		if (this.videoPlayer != null) {
			this.videoPlayer.pause();
		}
	}

	@Override
	public void onDestroy(@NonNull LifecycleOwner owner) {
		releasePlayer();
	}

	@Override
	public void setVolume(float volume) {
		// ducking
		if (this.videoPlayer != null) {
			this.videoPlayer.setVolume(volume);
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
	public void onPlayerError(ExoPlaybackException error) {
		if (error.type == ExoPlaybackException.TYPE_UNEXPECTED) {
			RuntimeUtil.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(getContext(), "Exoplayer error: " + error.getUnexpectedException(), Toast.LENGTH_LONG).show();
				}
			});

			releasePlayer();
			initializePlayer(false);
		}
	}

	public void initializePlayer(boolean playWhenReady) {
		try {
			this.videoPlayer = new SimpleExoPlayer.Builder(getContext()).build();
			this.videoPlayer.addListener(this);

			this.videoView.setPlayer(videoPlayer);
			this.videoView.setControllerHideOnTouch(true);
			this.videoView.showController();

			this.videoPlayer.setMediaItem(MediaItem.fromUri(this.mediaItem.getUri()));
			this.videoPlayer.setPlayWhenReady(playWhenReady);
			this.videoPlayer.prepare();
		} catch (OutOfMemoryError e) {
			logger.error("Exception", e);
		}
	}

	public void releasePlayer() {
		if (videoPlayer != null) {
			videoPlayer.stop();
			videoPlayer.release();
			videoPlayer = null;
		}
	}
}

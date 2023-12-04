/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2023 Threema GmbH
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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.alexvasilkov.gestures.GestureController;
import com.alexvasilkov.gestures.State;
import com.alexvasilkov.gestures.views.GestureFrameLayout;

import org.slf4j.Logger;

import ch.threema.app.R;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.VideoUtil;
import ch.threema.base.utils.LoggingUtil;

@UnstableApi
public class VideoPreviewFragment extends PreviewFragment implements DefaultLifecycleObserver, Player.Listener, PreviewFragmentInterface {
	private static final Logger logger = LoggingUtil.getThreemaLogger("VideoPreviewFragment");

	private PlayerView videoView;
	private ExoPlayer videoPlayer;
	private GestureFrameLayout gestureFrameLayout;
	private final GestureController.OnStateChangeListener onGestureStateChangeListener = new GestureController.OnStateChangeListener() {
		@Override
		public void onStateChanged(State state) {
			if (state.getZoom() > 1.05f || state.getZoom() < 0.95f) {
				if (videoView != null && videoView.isControllerFullyVisible()) {
					videoView.hideController();
				}
			}
		}

		@Override
		public void onStateReset(State oldState, State newState) {}
	};

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
			this.gestureFrameLayout = rootView.findViewById(R.id.video_gesture_frame);
			this.gestureFrameLayout.getController().getSettings().setMaxZoom(2.5f);
			this.gestureFrameLayout.getController().addOnStateChangeListener(onGestureStateChangeListener);
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
	public void setUserVisibleHint(boolean isVisibleToUser) {
		super.setUserVisibleHint(isVisibleToUser);

		if (isVisibleToUser) {
			onResume(this);
		} else {
			onPause(this);
		}
	}

	@Override
	public void onDestroy(@NonNull LifecycleOwner owner) {
		releasePlayer();
		if (gestureFrameLayout != null) {
			gestureFrameLayout.getController().removeOnStateChangeListener(onGestureStateChangeListener);
		}
	}

	@Override
	public void onPlayerError(@NonNull PlaybackException error) {
		RuntimeUtil.runOnUiThread(() -> Toast.makeText(getContext(), "Exoplayer error: " + error.getErrorCodeName(), Toast.LENGTH_LONG).show());

		releasePlayer();
		initializePlayer(false);
	}

	public void initializePlayer(boolean playWhenReady) {
		try {
			AudioAttributes audioAttributes = new AudioAttributes.Builder()
				.setUsage(C.USAGE_MEDIA)
				.setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
				.setAllowedCapturePolicy(C.ALLOW_CAPTURE_BY_NONE)
				.build();

			this.videoPlayer = VideoUtil.getExoPlayer(getContext());
			this.videoPlayer.setAudioAttributes(audioAttributes, true);
			this.videoPlayer.addListener(this);

			this.videoView.setPlayer(videoPlayer);
			this.videoView.setControllerHideOnTouch(true);
			this.videoView.setControllerShowTimeoutMs(1500);
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

/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSourceFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.PlayerControlView;
import androidx.media3.ui.PlayerView;

import com.alexvasilkov.gestures.GestureController;
import com.alexvasilkov.gestures.State;
import com.alexvasilkov.gestures.views.GestureFrameLayout;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import org.slf4j.Logger;

import java.io.File;
import java.lang.ref.WeakReference;

import ch.threema.app.R;
import ch.threema.app.activities.MediaViewerActivity;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.VideoUtil;
import ch.threema.base.utils.LoggingUtil;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

@SuppressLint("UnsafeOptInUsageError")
public class VideoViewFragment extends MediaViewFragment implements Player.Listener {
    private static final Logger logger = LoggingUtil.getThreemaLogger("VideoViewFragment");

    private WeakReference<ImageView> previewImageViewRef;
    private WeakReference<CircularProgressIndicator> progressBarRef;
    private WeakReference<PlayerView> videoViewRef;
    private WeakReference<GestureFrameLayout> gestureFrameLayoutRef;
    private ExoPlayer videoPlayer;
    private boolean isImmediatePlay, isPreparing;

    private final GestureController.OnStateChangeListener onGestureStateChangeListener = new GestureController.OnStateChangeListener() {
        @Override
        public void onStateChanged(State state) {
            if (state.getZoom() > 1.05f || state.getZoom() < 0.95f) {
                PlayerView playerView = videoViewRef.get();
                if (playerView != null && playerView.isControllerFullyVisible()) {
                    playerView.hideController();
                }
            }
        }

        @Override
        public void onStateReset(State oldState, State newState) {
        }
    };

    public VideoViewFragment() {
        super();
        logger.debug("new instance");
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        logger.debug("onCreateView");

        this.isImmediatePlay = getArguments().getBoolean(MediaViewerActivity.EXTRA_ID_IMMEDIATE_PLAY, false);

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .setAllowedCapturePolicy(C.ALLOW_CAPTURE_BY_NONE)
            .build();

        try {
            this.videoPlayer = VideoUtil.getExoPlayer(requireContext());
            this.videoPlayer.setAudioAttributes(audioAttributes, true);
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
    protected void showThumbnail(@NonNull Drawable thumbnail) {
        logger.debug("showThumbnail");

        if (TestUtil.required(this.previewImageViewRef, this.previewImageViewRef.get(), thumbnail)) {
            this.previewImageViewRef.get().setImageDrawable(thumbnail);
        }
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
            gestureFrameLayoutRef = new WeakReference<>(rootViewReference.get().findViewById(R.id.video_gesture_frame));
            gestureFrameLayoutRef.get().getController().getSettings().setMaxZoom(2.5f);
            gestureFrameLayoutRef.get().getController().addOnStateChangeListener(onGestureStateChangeListener);

            this.previewImageViewRef = new WeakReference<>(rootViewReference.get().findViewById(R.id.image));

            this.videoViewRef = new WeakReference<>(rootViewReference.get().findViewById(R.id.video_view));
            this.videoViewRef.get().setControllerVisibilityListener((PlayerControlView.VisibilityListener) visibility -> VideoViewFragment.this.showUi(visibility == View.VISIBLE));
            this.videoViewRef.get().setVisibility(View.GONE);
            this.videoViewRef.get().setPlayer(this.videoPlayer);
            this.videoViewRef.get().setControllerHideOnTouch(true);
            this.videoViewRef.get().setControllerShowTimeoutMs(MediaViewerActivity.ACTIONBAR_TIMEOUT);
            this.videoViewRef.get().setControllerAutoShow(true);

            logger.debug("View Type: " + (this.videoViewRef.get().getVideoSurfaceView() instanceof TextureView ? "Texture" : "Surface"));

            ConfigUtils.adjustExoPlayerControllerMargins(getContext(), this.videoViewRef.get());

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
            MediaSource videoSource = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(videoUri));

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

        if (this.videoPlayer != null) {
            this.videoPlayer.release();
            this.videoPlayer = null;
        }

        if (this.gestureFrameLayoutRef != null && this.gestureFrameLayoutRef.get() != null) {
            this.gestureFrameLayoutRef.get().getController().removeOnStateChangeListener(onGestureStateChangeListener);
        }

        super.onDestroyView();
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        keepScreenOn(isPlaying);
    }

    @Override
    public void onPlaybackStateChanged(int playbackState) {
        logger.debug("onPlaybackStateChanged = " + playbackState);

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
    }

    @Override
    public void onPlayerError(PlaybackException error) {
        logger.info("ExoPlaybackException = " + error.getMessage());

        this.progressBarRef.get().setVisibility(View.GONE);

        Toast.makeText(getContext(), R.string.unable_to_play_video, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        logger.debug("setUserVisibleHint = " + isVisibleToUser);

        // stop player if fragment comes out of view
        if (!isVisibleToUser && this.videoPlayer != null &&
            (this.videoPlayer.isLoading() || this.videoPlayer.isPlaying())) {
            this.videoPlayer.setPlayWhenReady(false);
            this.videoPlayer.pause();
        }
    }


}

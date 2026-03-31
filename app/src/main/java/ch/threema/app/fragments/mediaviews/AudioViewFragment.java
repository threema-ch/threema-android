package ch.threema.app.fragments.mediaviews;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.core.content.res.ResourcesCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaController;
import androidx.media3.ui.PlayerView;

import com.google.android.material.progressindicator.CircularProgressIndicator;

import org.slf4j.Logger;

import java.io.File;
import java.lang.ref.WeakReference;

import ch.threema.app.R;
import ch.threema.app.activities.MediaViewerActivity;
import ch.threema.app.mediaattacher.PreviewFragmentInterface;
import ch.threema.app.services.AudioPlayerService;
import ch.threema.app.ui.InsetSides;
import ch.threema.app.ui.ViewExtensionsKt;
import ch.threema.app.utils.IconUtil;
import ch.threema.app.utils.MimeUtil;
import ch.threema.storage.models.AbstractMessageModel;

import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

@OptIn(markerClass = UnstableApi.class)
public class AudioViewFragment extends MediaViewFragment implements Player.Listener, PreviewFragmentInterface {
    private static final Logger logger = getThreemaLogger("AudioViewFragment");

    private @Nullable WeakReference<ImageView> mimeCategoryImageViewRef;
    private @Nullable WeakReference<TextView> fileNameTextViewRef;
    private @Nullable WeakReference<TextView> mimeCategoryLabelTextViewRef;
    private @Nullable WeakReference<CircularProgressIndicator> progressIndicatorViewRef = null;
    private @Nullable WeakReference<PlayerView> playerViewRef = null;
    private boolean isVoiceMessage = false;
    private boolean isPrivateChat = false;
    private boolean isImmediatePlay = false;
    private volatile boolean isCurrentlyPreparingPlayer = false;
    private @Nullable MediaItem audioMediaItem = null;

    public AudioViewFragment() {
        super();
    }

    @Override
    protected int getFragmentResourceId() {
        return R.layout.fragment_media_viewer_audio;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (getArguments() != null) {
            isVoiceMessage = requireArguments().getBoolean(MediaViewerActivity.EXTRA_IS_VOICE_MESSAGE, false);
            isPrivateChat = requireArguments().getBoolean(MediaViewerActivity.EXTRA_IS_PRIVATE_CHAT, false);
            isImmediatePlay = requireArguments().getBoolean(MediaViewerActivity.EXTRA_ID_IMMEDIATE_PLAY, false);
        }
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    protected void created(@Nullable Bundle savedInstanceState, @NonNull ViewGroup rootView) {
        ViewExtensionsKt.applyDeviceInsetsAsPadding(
            rootView,
            InsetSides.all()
        );

        final @NonNull TextView filenameTextView = rootView.findViewById(R.id.filename);
        this.fileNameTextViewRef = new WeakReference<>(filenameTextView);

        final @NonNull CircularProgressIndicator progressIndicator = rootView.findViewById(R.id.progress_bar);
        this.progressIndicatorViewRef = new WeakReference<>(progressIndicator);

        final @NonNull PlayerView playerView = rootView.findViewById(R.id.audio_view);
        this.playerViewRef = new WeakReference<>(playerView);

        final @NonNull ImageView mimeCategoryImageView = rootView.findViewById(R.id.mime_category_image);
        this.mimeCategoryImageViewRef = new WeakReference<>(mimeCategoryImageView);

        final @NonNull TextView mimeCategoryLabelTextView = rootView.findViewById(R.id.mime_category_label);
        this.mimeCategoryLabelTextViewRef = new WeakReference<>(mimeCategoryLabelTextView);

        setUpAudioPlayerView(playerView);
    }

    private void setUpAudioPlayerView(@NonNull PlayerView playerView) {
        playerView.setControllerVisibilityListener(
            (PlayerView.ControllerVisibilityListener) visibility -> showUi(visibility == View.VISIBLE)
        );
        playerView.setControllerHideOnTouch(true);
        playerView.setControllerShowTimeoutMs(-1);
        playerView.setControllerAutoShow(true);
    }

    @Override
    protected void handleFileName(@Nullable String fileName) {
        final @Nullable TextView fileNameTextView = (fileNameTextViewRef != null)
            ? fileNameTextViewRef.get()
            : null;
        if (fileNameTextView == null) {
            return;
        }
        fileNameTextView.setVisibility(
            (fileName != null && !fileName.isBlank()) ? View.VISIBLE : View.GONE
        );
        fileNameTextView.setText(fileName);
    }

    @Override
    protected void handleMimeCategory(@NonNull MimeUtil.MimeCategory category) {
        final @Nullable ImageView mimeCategoryImageView = (mimeCategoryImageViewRef != null)
            ? mimeCategoryImageViewRef.get()
            : null;
        final @Nullable TextView mimeCategoryLabelTextView = (mimeCategoryLabelTextViewRef != null)
            ? mimeCategoryLabelTextViewRef.get()
            : null;
        if (mimeCategoryImageView == null || mimeCategoryLabelTextView == null) {
            return;
        }
        final @DrawableRes int mimeTypeImageRes = isVoiceMessage
            ? R.drawable.ic_keyboard_voice_outline
            : IconUtil.getMimeCategoryIcon(category);
        mimeCategoryImageView.setImageDrawable(
            ResourcesCompat.getDrawable(
                getResources(),
                mimeTypeImageRes,
                requireActivity().getTheme()
            )
        );
        final @Nullable @StringRes Integer mimeCategoryDescriptionRes = isVoiceMessage
            ? Integer.valueOf(R.string.voice_message)
            : MimeUtil.getMimeDescriptionRes(category);
        if (mimeCategoryDescriptionRes != null) {
            mimeCategoryLabelTextView.setText(mimeCategoryDescriptionRes);
        } else {
            mimeCategoryLabelTextView.setText(null);
        }
        mimeCategoryLabelTextView.setVisibility(
            mimeCategoryDescriptionRes != null ? View.VISIBLE : View.GONE
        );
    }

    @Override
    protected void handleDecryptingFile() {
        if (progressIndicatorViewRef != null && progressIndicatorViewRef.get() != null) {
            progressIndicatorViewRef.get().setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void handleDecryptFailure() {
        if (progressIndicatorViewRef != null && progressIndicatorViewRef.get() != null) {
            progressIndicatorViewRef.get().setVisibility(View.GONE);
        }
        if (playerViewRef != null && playerViewRef.get() != null) {
            playerViewRef.get().setUseController(false);
        }
        super.showFileNotFoundContent();
    }

    @Override
    protected void handleDecryptedFile(final @NonNull File file) throws IllegalStateException {
        if (!isAdded() || !isCurrentlyInFocus || isCurrentlyPreparingPlayer) {
            return;
        }
        logger.info("Handling decrypted audio file: {}", file.getName());
        // If the audioMediaItem is null this means this fragment was never in focus before and never loaded the audio media item.
        // But if it is present, this means the user navigated back to this fragment that was previously focused at least once.
        if (audioMediaItem == null) {
            audioMediaItem = createAudioMediaItem(file);
            logger.info("Created audio media item {}", audioMediaItem.mediaId);
        }
        preparePlayer(audioMediaItem, isImmediatePlay);
    }

    @NonNull
    private MediaItem createAudioMediaItem(final @NonNull File file) throws IllegalStateException {
        final @Nullable AbstractMessageModel messageModel = super.getMessageModel();
        if (messageModel == null || messageModel.getUid() == null) {
            throw new IllegalStateException("Can not determine message uid");
        }
        @Nullable String mediaTitle;
        if (isVoiceMessage) {
            mediaTitle = getString(R.string.notification_channel_voice_message_player);
        } else if (!isPrivateChat && notificationPreferenceService.getValue().isShowMessagePreview()) {
            mediaTitle = messageModel.getFileData().getFileName();
        } else {
            mediaTitle = getString(R.string.mime_audio);
        }
        return new MediaItem.Builder()
            .setMediaId(messageModel.getUid())
            .setUri(Uri.fromFile(file))
            .setMediaMetadata(
                new MediaMetadata.Builder()
                    .setTitle(mediaTitle)
                    .setArtist(getString(R.string.app_name))
                    .build()
            )
            .build();
    }

    @UiThread
    private void preparePlayer(final @NonNull MediaItem audioMediaItem, final boolean playImmediately) {
        final @Nullable Activity activity = getActivity();
        if (!(activity instanceof MediaViewerActivity)) {
            return;
        }
        isCurrentlyPreparingPlayer = true;
        ((MediaViewerActivity) activity).awaitAudioMediaController(
            mediaController -> {
                if (!isAdded() || !isCurrentlyInFocus || !isCurrentlyPreparingPlayer) {
                    logger.debug("Cancel audio player preparation");
                    return;
                }
                if (mediaController == null) {
                    logger.error("Failed to prepare player because the audio media controller is missing");
                    isCurrentlyPreparingPlayer = false;
                    return;
                }
                @Nullable PlayerView playerControlsView = null;
                if (playerViewRef != null) {
                    playerControlsView = playerViewRef.get();
                }
                if (playerControlsView == null) {
                    logger.error("Missing audio player controls view while setting up player");
                    isCurrentlyPreparingPlayer = false;
                    return;
                }
                playerControlsView.setPlayer(mediaController);
                mediaController.addListener(this);
                mediaController.setAudioAttributes(
                    AudioPlayerService.createAudioAttributes(
                        isVoiceMessage ? C.AUDIO_CONTENT_TYPE_SPEECH : C.AUDIO_CONTENT_TYPE_MUSIC
                    ),
                    AudioPlayerService.HANDLE_AUDIO_FOCUS
                );
                mediaController.setPlayWhenReady(playImmediately);
                mediaController.setMediaItem(audioMediaItem);
                mediaController.prepare();
                logger.debug("Preparing audio player for audio media item {}", audioMediaItem.mediaId);
            }
        );
    }

    @Override
    protected void onFocusLost() {
        logger.debug("Fragment lost focus");
        isCurrentlyPreparingPlayer = false;
        final @Nullable MediaController mediaController = getAudioMediaController();
        if (mediaController != null) {
            mediaController.removeListener(this);
            mediaController.stop();
            mediaController.clearMediaItems();
        }
    }

    @Override
    public void onIsLoadingChanged(boolean isLoading) {
        logger.debug("Loading changed to {}", isLoading);
        if (progressIndicatorViewRef != null && progressIndicatorViewRef.get() != null) {
            progressIndicatorViewRef.get().setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onPlaybackStateChanged(int playbackState) {
        logger.debug("Playback state changed to {}", playbackState);
        final @Nullable MediaController mediaController = getAudioMediaController();
        if (mediaController == null) {
            logger.warn("A media controller should be available at this point - ignoring state change");
            return;
        }
        if (isCurrentlyPreparingPlayer && playbackState == Player.STATE_READY) {
            isCurrentlyPreparingPlayer = false;

            @Nullable String currentMediaItemId = null;
            final @Nullable MediaItem currentMediaItem = mediaController.getCurrentMediaItem();
            if (currentMediaItem != null) {
                currentMediaItemId = currentMediaItem.mediaId;
            }
            logger.info("Prepared audio media player successfully with media item {}", currentMediaItemId);

            if (progressIndicatorViewRef != null && progressIndicatorViewRef.get() != null) {
                progressIndicatorViewRef.get().setVisibility(View.GONE);
            }
            if (playerViewRef != null && playerViewRef.get() != null) {
                playerViewRef.get().showController();
            }
        }

        if (playbackState == Player.STATE_ENDED) {
            mediaController.setPlayWhenReady(false);
            mediaController.seekTo(0);
            if (playerViewRef != null && playerViewRef.get() != null) {
                playerViewRef.get().showController();
            }
        }
    }

    @Nullable
    private MediaController getAudioMediaController() {
        final @Nullable Activity activity = getActivity();
        if (!(activity instanceof MediaViewerActivity)) {
            return null;
        }
        return ((MediaViewerActivity) activity).getAudioMediaController();
    }

    @Override
    public void onDestroyView() {
        final @Nullable MediaController mediaController = getAudioMediaController();
        if (mediaController != null) {
            mediaController.removeListener(this);
        }
        super.onDestroyView();
    }
}

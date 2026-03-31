package ch.threema.app.adapters.decorators;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.Toast;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import ch.threema.app.R;
import ch.threema.app.services.messageplayer.MessagePlayer;
import ch.threema.app.ui.ControllerView;
import ch.threema.app.ui.DebouncedOnClickListener;
import ch.threema.app.ui.listitemholder.ComposeMessageHolder;
import ch.threema.app.utils.ImageViewUtil;
import ch.threema.app.utils.LinkifyUtil;
import ch.threema.app.utils.MessageUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.data.media.ImageDataModel;

import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import static ch.threema.storage.models.MessageState.FS_KEY_MISMATCH;
import static ch.threema.storage.models.MessageState.SENDFAILED;

public class ImageChatAdapterDecorator extends ChatAdapterDecorator {
    private static final Logger logger = getThreemaLogger("ImageChatAdapterDecorator");

    private static final String LISTENER_TAG = "ImageDecorator";

    public interface ImageListener {
        void viewImage(AbstractMessageModel model);
    }

    @NonNull
    private final MessagePlayerFactory messagePlayerFactory;

    @NonNull
    private final ImageListener imageListener;

    public ImageChatAdapterDecorator(
        AbstractMessageModel messageModel,
        @NonNull ChatAdapterDecoratorListener chatAdapterDecoratorListener,
        @NonNull LinkifyUtil.LinkifyListener linkifyListener,
        @NonNull MessagePlayerFactory messagePlayerFactory,
        @NonNull ImageListener imageListener,
        Helper helper
    ) {
        super(messageModel, chatAdapterDecoratorListener, linkifyListener, helper);
        this.messagePlayerFactory = messagePlayerFactory;
        this.imageListener = imageListener;
    }

    @Override
    protected void configureChatMessage(final ComposeMessageHolder holder, Context context, final int position) {
        super.configureChatMessage(holder, context, position);

        final MessagePlayer imageMessagePlayer = messagePlayerFactory.create(getMessageModel(), null);

        logger.debug("configureChatMessage Image");

        holder.messagePlayer = imageMessagePlayer;

        setOnClickListener(view -> {
            AbstractMessageModel model = getMessageModel();
            MessageState state = model.getState();
            if (state != FS_KEY_MISMATCH && state != SENDFAILED && model.isAvailable()) {
                imageListener.viewImage(model);
            }
        }, holder.messageBlockView);

        setControllerClickListener(holder, imageMessagePlayer);

        configureThumbnail(holder);

        if (holder.attachmentImage != null) {
            holder.attachmentImage.setContentDescription(context.getString(R.string.image_placeholder));
        }

        RuntimeUtil.runOnUiThread(() -> {
            setupResendStatus(holder);
            setControllerState(holder, getMessageModel().getImageData());
        });

        configureBodyText(holder, getMessageModel().getCaption());

        configureMessagePlayer(holder, imageMessagePlayer, context.getApplicationContext());
    }

    private void configureMessagePlayer(
        @NonNull ComposeMessageHolder holder,
        @NonNull MessagePlayer imageMessagePlayer,
        @NonNull Context applicationContext
    ) {
        imageMessagePlayer
            // download listener
            .addListener(LISTENER_TAG, new MessagePlayer.DownloadListener() {
                @Override
                public void onStart(AbstractMessageModel messageModel) {
                    RuntimeUtil.runOnUiThread(() -> holder.controller.setProgressing(false));
                }

                @Override
                public void onStatusUpdate(AbstractMessageModel messageModel, final int progress) {
                }

                @Override
                public void onUnknownProgress(AbstractMessageModel messageModel) {
                    RuntimeUtil.runOnUiThread(() -> holder.controller.setProgressing());
                }

                @Override
                public void onEnd(AbstractMessageModel messageModel, final boolean success, final String message) {
                    //hide progressbar
                    RuntimeUtil.runOnUiThread(() -> {
                        if (success) {
                            holder.controller.setHidden();
                        } else {
                            holder.controller.setReadyToDownload();
                            if (!TestUtil.isEmptyOrNull(message)) {
                                Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                }
            });
    }

    private void setControllerClickListener(@NonNull ComposeMessageHolder holder, @NonNull MessagePlayer imageMessagePlayer) {
        if (holder.controller != null) {
            holder.controller.setOnClickListener(new DebouncedOnClickListener(500) {
                @Override
                public void onDebouncedClick(View v) {
                    int status = holder.controller.getStatus();

                    switch (status) {
                        case ControllerView.STATUS_READY_TO_RETRY:
                            propagateControllerRetryClickToParent();
                            break;
                        case ControllerView.STATUS_PROGRESSING:
                            if (MessageUtil.isFileMessageBeingSent(getMessageModel())) {
                                getMessageService().cancelMessageUpload(getMessageModel());
                            } else {
                                imageMessagePlayer.cancel();
                            }
                            break;
                        case ControllerView.STATUS_READY_TO_DOWNLOAD:
                            imageMessagePlayer.open();
                            break;
                        default:
                            AbstractMessageModel model = getMessageModel();
                            if (model.isAvailable()) {
                                imageListener.viewImage(model);
                            }
                    }
                }
            });
        }
    }

    private void configureThumbnail(@NonNull ComposeMessageHolder holder) {
        Bitmap thumbnail;
        try {
            thumbnail = getFileService().getMessageThumbnailBitmap(getMessageModel(),
                getThumbnailCache());
        } catch (Exception e) {
            logger.error("Exception", e);
            thumbnail = null;
        }

        ImageViewUtil.showBitmapOrImagePlaceholder(
            holder.contentView,
            holder.attachmentImage,
            thumbnail,
            getThumbnailWidth()
        );
        holder.bodyTextView.setWidth(getThumbnailWidth());

        if (thumbnail == null) {
            holder.controller.setHidden();
        } else {
            showHide(holder.controller, false);
        }
    }

    private void setControllerState(@NonNull ComposeMessageHolder holder, @NonNull ImageDataModel imageDataModel) {
        if (holder.controller == null) {
            return;
        }
        AbstractMessageModel messageModel = getMessageModel();
        if (messageModel != null) {
            if (messageModel.isOutbox() && !(messageModel instanceof DistributionListMessageModel)) {
                setControllerStateOutgoingMessage(holder, messageModel);
            } else {
                // incoming message
                setControllerStateIncomingMessage(holder, imageDataModel, messageModel);
            }
        } else {
            holder.controller.setHidden();
        }
    }

    private void setControllerStateIncomingMessage(
        @NonNull ComposeMessageHolder holder,
        @NonNull ImageDataModel imageDataModel,
        @NonNull AbstractMessageModel messageModel
    ) {
        if (imageDataModel.isDownloaded()) {
            holder.controller.setHidden();
        } else {
            if (holder.messagePlayer.getState() == MessagePlayer.State_DOWNLOADING) {
                // set correct state if re-entering this chat
                holder.controller.setProgressing(false);
            } else {
                if (helper.getDownloadService().isDownloading(messageModel.getId())) {
                    holder.controller.setProgressing(false);
                } else {
                    holder.controller.setReadyToDownload();
                }
            }
        }
    }

    private void setControllerStateOutgoingMessage(@NonNull ComposeMessageHolder holder, @NonNull AbstractMessageModel messageModel) {
        switch (messageModel.getState()) {
            case TRANSCODING:
                holder.controller.setTranscoding();
                break;
            case PENDING:
            case SENDING:
                holder.controller.setProgressing();
                break;
            case SENDFAILED:
            case FS_KEY_MISMATCH:
                holder.controller.setRetry();
                break;
            default:
                holder.controller.setHidden();
        }
    }
}

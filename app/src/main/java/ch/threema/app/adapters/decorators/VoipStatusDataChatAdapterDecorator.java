package ch.threema.app.adapters.decorators;

import android.content.Context;
import android.graphics.PorterDuff;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import ch.threema.app.R;
import ch.threema.app.ui.SingleToast;
import ch.threema.app.ui.listitemholder.ComposeMessageHolder;
import ch.threema.app.ui.models.MessageViewElement;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ElapsedTimeFormatter;
import ch.threema.app.utils.LinkifyUtil;
import ch.threema.app.utils.MessageUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.ViewUtil;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.data.status.VoipStatusDataModel;

public class VoipStatusDataChatAdapterDecorator extends ChatAdapterDecorator {

    public interface VoipStatusDataChatListener {
        void showDialog(String name);
    }

    @NonNull
    private final VoipStatusDataChatListener listener;

    public VoipStatusDataChatAdapterDecorator(
        AbstractMessageModel messageModel,
        @NonNull ChatAdapterDecoratorListener chatAdapterDecoratorListener,
        @NonNull LinkifyUtil.LinkifyListener linkifyListener,
        Helper helper,
        @NonNull VoipStatusDataChatListener listener
    ) {
        super(messageModel, chatAdapterDecoratorListener, linkifyListener, helper);
        this.listener = listener;
    }

    @Override
    protected void configureChatMessage(@NonNull final ComposeMessageHolder holder, @NonNull Context context, final int position) {
        if (holder.controller != null) {
            holder.controller.setClickable(false);
            holder.controller.setIconResource(R.drawable.ic_phone_locked_outline);
        }

        if (holder.bodyTextView != null) {
            final @NonNull MessageViewElement viewElement = MessageUtil.getViewElement(
                context,
                this.getMessageModel(),
                this.helper.getPreferenceService().getContactNameFormat()
            );

            if (viewElement.placeholder != null) {
                holder.bodyTextView.setText(viewElement.placeholder);
            }

            VoipStatusDataModel status = this.getMessageModel().getVoipStatusData();
            if (status != null && status.getStatus() == VoipStatusDataModel.FINISHED) {
                // Show duration
                if (holder.dateView != null) {
                    this.setDatePrefix(ElapsedTimeFormatter.secondsToString(status.getDuration()));
                    this.setDuration(status.getDuration());
                }
            }

            // Set and tint the phone image
            if (viewElement.icon != null && ViewUtil.showAndSet(holder.attachmentImage, viewElement.icon)) {
                if (viewElement.color != null) {
                    holder.attachmentImage.setColorFilter(
                        ContextCompat.getColor(context, viewElement.color),
                        PorterDuff.Mode.SRC_IN
                    );
                }
            }
        }

        this.setOnClickListener(
            view -> {
                // load the the contact
                if (ConfigUtils.isCallsEnabled()) {
                    ContactModel contactModel = helper.getContactService().getByIdentity(getMessageModel().getIdentity());
                    if (contactModel != null) {
                        String name = NameUtil.getContactDisplayNameOrNickname(
                            contactModel,
                            false,
                            helper.getPreferenceService().getContactNameFormat()
                        );
                        listener.showDialog(name);
                    }
                } else {
                    SingleToast.getInstance().showLongText(view.getContext().getString(R.string.voip_disabled));
                }
            },
            holder.messageBlockView
        );
    }
}

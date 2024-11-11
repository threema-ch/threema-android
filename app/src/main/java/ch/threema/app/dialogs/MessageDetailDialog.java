/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2024 Threema GmbH
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

package ch.threema.app.dialogs;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatDialog;
import androidx.compose.ui.platform.ComposeView;
import androidx.core.app.ActivityCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipDrawable;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.divider.MaterialDivider;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.ContactDetailActivity;
import ch.threema.app.activities.MessageDetailsUiModel;
import ch.threema.app.activities.MessageDetailsViewModelKt;
import ch.threema.app.activities.MessageTimestampsUiModel;
import ch.threema.app.activities.ThreemaActivity;
import ch.threema.app.compose.common.interop.ComposeJavaBridge;
import ch.threema.app.listeners.MessageListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.MessageService;
import ch.threema.app.stores.IdentityStore;
import ch.threema.app.utils.AvatarConverterUtil;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.MessageType;

public class MessageDetailDialog extends ThreemaDialogFragment implements View.OnClickListener {

    private static final Logger logger = LoggingUtil.getThreemaLogger("MessageDetailDialog");

    private static final String BUNDLE_KEY_TITLE_RES_ID = "title";
    private static final String BUNDLE_KEY_MESSAGE_ID = "messageId";
    private static final String BUNDLE_KEY_MESSAGE_TYPE = "messageType";

    private View dialogView;

    private @Nullable ContactService contactService = null;
    private @Nullable IdentityStore identityStore = null;
    private @Nullable AbstractMessageModel messageModel = null;

    private final MessageListener messageListener = new MessageListener() {
        @Override
        public void onNew(AbstractMessageModel newMessage) {
        }

        @Override
        public void onModified(List<AbstractMessageModel> modifiedMessageModels) {
            if (messageModel != null) {
                for (AbstractMessageModel modifiedMessageModel : modifiedMessageModels) {
                    if (modifiedMessageModel.getId() == messageModel.getId()) {
                        RuntimeUtil.runOnUiThread(() -> updateAckDisplay(modifiedMessageModel));
                        break;
                    }
                }
            }
        }

        @Override
        public void onRemoved(AbstractMessageModel removedMessageModel) {
        }

        @Override
        public void onRemoved(List<AbstractMessageModel> removedMessageModels) {
        }

        @Override
        public void onProgressChanged(AbstractMessageModel messageModel, int newProgress) {
        }

        @Override
        public void onResendDismissed(@NonNull AbstractMessageModel messageModel) {
            // Ignore
        }
    };

    @NonNull
    public static MessageDetailDialog newInstance(
        @StringRes int titleResId,
        int messageId,
        @Nullable String messageType
    ) {
        MessageDetailDialog dialog = new MessageDetailDialog();
        Bundle args = new Bundle();
        args.putInt(BUNDLE_KEY_TITLE_RES_ID, titleResId);
        args.putInt(BUNDLE_KEY_MESSAGE_ID, messageId);
        args.putString(BUNDLE_KEY_MESSAGE_TYPE, messageType);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ListenerManager.messageListeners.add(this.messageListener);
    }

    @Override
    public void onDestroy() {
        ListenerManager.messageListeners.remove(this.messageListener);

        super.onDestroy();
    }

    @NonNull
    @Override
    public AppCompatDialog onCreateDialog(Bundle savedInstanceState) {

        final @Nullable ServiceManager serviceManager = ThreemaApplication.getServiceManager();
        @Nullable MessageService messageService = null;
        if (serviceManager != null) {
            try {
                messageService = serviceManager.getMessageService();
                identityStore = serviceManager.getIdentityStore();
                contactService = serviceManager.getContactService();
            } catch (ThreemaException threemaException) {
                logger.error("Required services are not available", threemaException);
            }
        }

        final @StringRes int titleResId = requireArguments().getInt(BUNDLE_KEY_TITLE_RES_ID);
        final int messageId = requireArguments().getInt(BUNDLE_KEY_MESSAGE_ID);
        final @Nullable String messageType = requireArguments().getString(BUNDLE_KEY_MESSAGE_TYPE);

        if (messageService != null) {
            messageModel = messageService.getMessageModelFromId(messageId, messageType);
        } else {
            messageModel = null;
        }

        dialogView = requireActivity().getLayoutInflater().inflate(R.layout.dialog_message_detail, null);

        if (messageModel != null) {
            MessageTimestampsUiModel timestampsUiModel = MessageDetailsViewModelKt.toMessageTimestampsUiModel(messageModel);
            MessageDetailsUiModel detailsUiModel = MessageDetailsViewModelKt.toMessageDetailsUiModel(messageModel);
            final ComposeView messageDetailComposeView = dialogView.findViewById(R.id.message_detail_compose_view);
            ComposeJavaBridge.INSTANCE.setContentMessageDetails(messageDetailComposeView, timestampsUiModel, detailsUiModel);
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity(), getTheme());
        builder.setView(dialogView);

        if (titleResId != -1) {
            builder.setTitle(titleResId);
        }

        builder.setPositiveButton(getString(R.string.ok), null);

        if (messageModel != null && !messageModel.isStatusMessage() && messageModel.getType() != MessageType.GROUP_CALL_STATUS) {
            updateAckDisplay(messageModel);
        }

        return builder.create();
    }

    private synchronized void updateAckDisplay(AbstractMessageModel messageModel) {

        if (!ConfigUtils.isGroupAckEnabled()) {
            return;
        }

        if (dialogView == null) {
            return;
        }

        if (!isAdded()) {
            return;
        }

        if (messageModel == null) {
            return;
        }

        if (contactService == null) {
            return;
        }

        final MaterialDivider groupAckDivider = dialogView.findViewById(R.id.groupack_divider);
        final MaterialCardView ackCard = dialogView.findViewById(R.id.ack_card);
        final ImageView ackIcon = dialogView.findViewById(R.id.ack_icon);
        final ChipGroup ackData = dialogView.findViewById(R.id.ack_data);
        final MaterialButton ackCountView = dialogView.findViewById(R.id.ack_count);
        final MaterialCardView decCard = dialogView.findViewById(R.id.dec_card);
        final ImageView decIcon = dialogView.findViewById(R.id.dec_icon);
        final ChipGroup decData = dialogView.findViewById(R.id.dec_data);
        final MaterialButton decCountView = dialogView.findViewById(R.id.dec_count);

        if (messageModel instanceof GroupMessageModel) {
            Map<String, Object> messageStates = ((GroupMessageModel) messageModel).getGroupMessageStates();
            if (messageStates != null && !messageStates.isEmpty()) {
                int ackCount = 0, decCount = 0;
                ackData.removeAllViews();
                decData.removeAllViews();

                for (Map.Entry<String, Object> entry : messageStates.entrySet()) {
                    ContactModel contactModel = contactService.getByIdentity(entry.getKey());
                    if (contactModel == null) {
                        continue;
                    }

                    // an ack or dec state implies "read"
                    if (MessageState.USERACK.toString().equals(entry.getValue())) {
                        appendChip(ackData, contactModel);
                        ackCount++;
                    } else if (MessageState.USERDEC.toString().equals(entry.getValue())) {
                        appendChip(decData, contactModel);
                        decCount++;
                    }

                    if (ackCount > 0) {
                        ackCard.setVisibility(View.VISIBLE);
                        ackCountView.setText(String.valueOf(ackCount));
                        if (messageModel.getState() == MessageState.USERACK) {
                            ackIcon.setImageResource(R.drawable.ic_thumb_up_filled);
                        }
                    } else {
                        ackCard.setVisibility(View.GONE);
                    }

                    if (decCount > 0) {
                        decCard.setVisibility(View.VISIBLE);
                        decCountView.setText(String.valueOf(decCount));
                        if (messageModel.getState() == MessageState.USERDEC) {
                            decIcon.setImageResource(R.drawable.ic_thumb_down_filled);
                        }
                    } else {
                        decCard.setVisibility(View.GONE);
                    }

                    if (decCount > 0 || ackCount > 0) {
                        groupAckDivider.setVisibility(View.VISIBLE);
                    }
                }
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    private void appendChip(ChipGroup chipGroup, @NonNull ContactModel contactModel) {
        Chip chip = new Chip(requireContext());
        ChipDrawable chipDrawable = ChipDrawable.createFromAttributes(
            requireContext(),
            null,
            0,
            R.style.Threema_Chip_MessageDetails
        );
        chip.setChipDrawable(chipDrawable);
        chip.setEnsureMinTouchTargetSize(false);
        chip.setTag(contactModel.getIdentity());
        chip.setOnClickListener(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            chip.setTextAppearance(R.style.Threema_TextAppearance_Chip_ChatNotice);
        } else {
            chip.setTextSize(14);
        }

        //noinspection deprecation
        new AsyncTask<Void, Void, Bitmap>() {
            @Override
            protected Bitmap doInBackground(Void... params) {
                if (contactService == null) {
                    return null;
                }
                Bitmap bitmap = contactService.getAvatar(contactModel, false);
                if (bitmap != null) {
                    return BitmapUtil.replaceTransparency(bitmap, Color.WHITE);
                }
                return null;
            }

            @Override
            protected void onPostExecute(Bitmap avatar) {
                if (avatar != null) {
                    chip.setChipIcon(AvatarConverterUtil.convertToRound(getResources(), avatar));
                } else {
                    chip.setChipIconResource(R.drawable.ic_contact);
                }
            }
        }.execute();

        chip.setText(NameUtil.getShortName(contactModel));
        chipGroup.addView(chip);
    }

    @Override
    public void onClick(View view) {
        if (identityStore == null || !(view instanceof Chip)) {
            return;
        }
        final @Nullable String identity = (String) view.getTag();
        if (identity != null && !identity.equals(identityStore.getIdentity())) {
            Intent intent = new Intent(getContext(), ContactDetailActivity.class);
            intent.putExtra(ThreemaApplication.INTENT_DATA_CONTACT, identity);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            ActivityCompat.startActivityForResult(
                requireActivity(),
                intent,
                ThreemaActivity.ACTIVITY_ID_CONTACT_DETAIL,
                null
            );
        }
    }
}

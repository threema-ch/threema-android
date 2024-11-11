/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2024 Threema GmbH
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

package ch.threema.app.ui;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupWindow;

import androidx.annotation.Nullable;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.services.GroupService;
import ch.threema.app.utils.AnimationUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.MessageUtil;
import ch.threema.app.utils.ViewUtil;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.data.DisplayTag;

public class
AckjiPopup extends PopupWindow implements View.OnClickListener {

    private final ImageView ackButton, decButton, imageReplyButton, infoButton, starButton, editButton;
    private final View parentView, imageReplySeparator, infoSeparator;
    private AckDecPopupListener ackDecPopupListener;
    private final int popupHeight, popupHorizontalOffset;

    private static boolean isDismissing = false;

    public static final int ITEM_ACK = 0;
    public static final int ITEM_DEC = 1;
    public static final int ITEM_IMAGE_REPLY = 2;
    public static final int ITEM_INFO = 3;
    public static final int ITEM_STAR = 4;
    public static final int ITEM_EDIT = 5;

    public AckjiPopup(Context context, View parentView) {
        super(context);

        this.parentView = parentView;
        this.popupHeight = 2 * context.getResources().getDimensionPixelSize(R.dimen.ackdec_popup_content_margin) +
            context.getResources().getDimensionPixelSize(R.dimen.emoji_popup_cardview_margin_bottom) +
            context.getResources().getDimensionPixelSize(R.dimen.ackdec_emoji_size);
        this.popupHorizontalOffset = context.getResources().getDimensionPixelSize(R.dimen.ackdec_popup_content_margin_horizontal);

        LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final FrameLayout topLayout = (FrameLayout) layoutInflater.inflate(R.layout.popup_ackji, null, true);

        this.ackButton = topLayout.findViewById(R.id.ack);
        this.decButton = topLayout.findViewById(R.id.dec);
        this.imageReplyButton = topLayout.findViewById(R.id.image_reply);
        this.infoButton = topLayout.findViewById(R.id.info);
        this.starButton = topLayout.findViewById(R.id.star);
        this.editButton = topLayout.findViewById(R.id.edit);

        this.imageReplySeparator = topLayout.findViewById(R.id.image_reply_separator);
        this.infoSeparator = topLayout.findViewById(R.id.info_separator);

        this.ackButton.setOnClickListener(this);
        this.decButton.setOnClickListener(this);
        this.imageReplyButton.setOnClickListener(this);
        this.infoButton.setOnClickListener(this);
        this.starButton.setOnClickListener(this);
        this.editButton.setOnClickListener(this);

        setContentView(topLayout);
        setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        setWidth(FrameLayout.LayoutParams.WRAP_CONTENT);
        setHeight(FrameLayout.LayoutParams.WRAP_CONTENT);

        setBackgroundDrawable(new BitmapDrawable());
        setAnimationStyle(0);
        setOutsideTouchable(true);
        setTouchable(true);
        setFocusable(true);
        ViewUtil.setTouchModal(this, false);
    }

    public void show(final View originView, @Nullable AbstractMessageModel messageModel) {
        if (messageModel == null) {
            return;
        }

        isDismissing = false;

        int horizontalOffset = messageModel.isOutbox() ? 0 : this.popupHorizontalOffset;

        if (this.ackDecPopupListener != null) {
            this.ackDecPopupListener.onOpen();
        }

        this.decButton.setVisibility(MessageUtil.canSendUserDecline(messageModel) ? View.VISIBLE : View.GONE);
        this.ackButton.setVisibility(MessageUtil.canSendUserAcknowledge(messageModel) ? View.VISIBLE : View.GONE);
        this.imageReplyButton.setVisibility(MessageUtil.canSendImageReply(messageModel) ? View.VISIBLE : View.GONE);
        this.editButton.setVisibility(MessageUtil.canEdit(messageModel) ? View.VISIBLE : View.GONE);

        if (messageModel instanceof GroupMessageModel) {
            try {
                GroupService groupService = ThreemaApplication.getServiceManager().getGroupService();
                GroupModel groupModel = groupService.getById(((GroupMessageModel) messageModel).getGroupId());
                if (groupModel != null) {
                    boolean isMember = groupService.isGroupMember(groupModel);
                    if (!isMember) {
                        this.editButton.setVisibility(View.GONE);
                    }
                    if (!isMember || groupService.getOtherMemberCount(groupModel) < 1) {
                        this.decButton.setVisibility(View.GONE);
                        this.ackButton.setVisibility(View.GONE);
                    }
                }
            } catch (Exception e) {
                // ignore - should never happen
            }
        }

        if ((ackButton.getVisibility() == View.VISIBLE || decButton.getVisibility() == View.VISIBLE)
            && imageReplyButton.getVisibility() == View.VISIBLE) {
            this.imageReplySeparator.setVisibility(View.VISIBLE);
        } else {
            this.imageReplySeparator.setVisibility(View.GONE);
        }

        final boolean showStarButton = !(messageModel instanceof DistributionListMessageModel)
            && !messageModel.isStatusMessage()
            && !messageModel.isDeleted();

        this.starButton.setVisibility(showStarButton ? View.VISIBLE : View.GONE);

        this.infoSeparator.setVisibility(View.GONE);
        if (
            messageModel.getType().equals(MessageType.TEXT) ||
                messageModel.getType().equals(MessageType.FILE) ||
                messageModel.getType().equals(MessageType.LOCATION) ||
                messageModel.getType().equals(MessageType.BALLOT) ||
                messageModel.getType().equals(MessageType.CONTACT) ||
                messageModel.getType().equals(MessageType.IMAGE) ||
                messageModel.getType().equals(MessageType.VIDEO) ||
                messageModel.getType().equals(MessageType.VOICEMESSAGE)
        ) {
            this.infoButton.setVisibility(View.VISIBLE);

            if (ackButton.getVisibility() == View.VISIBLE || decButton.getVisibility() == View.VISIBLE || imageReplyButton.getVisibility() == View.VISIBLE) {
                this.infoSeparator.setVisibility(View.VISIBLE);
            }

            if (showStarButton) {
                if (messageModel.isStarred()) {
                    this.starButton.setImageResource(R.drawable.star_outline_off_black_24dp);
                    this.starButton.setColorFilter(ConfigUtils.getColorFromAttribute(parentView.getContext(), R.attr.colorOnSurface));
                } else {
                    this.starButton.setImageResource(R.drawable.ic_star_golden_24dp);
                    this.starButton.setColorFilter(null);
                }
            }
        }

        int[] originLocation = {0, 0};
        originView.getLocationInWindow(originLocation);
        showAtLocation(parentView, Gravity.LEFT | Gravity.TOP, originLocation[0] + horizontalOffset, originLocation[1] - this.popupHeight);

        getContentView().getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                getContentView().getViewTreeObserver().removeGlobalOnLayoutListener(this);

                AnimationUtil.popupAnimateIn(getContentView());

                int animationDelay = 10;
                final int animationDelayStep = 100;

                if (ackButton.getVisibility() == View.VISIBLE) {
                    AnimationUtil.bubbleAnimate(ackButton, animationDelay += animationDelayStep);
                }
                if (decButton.getVisibility() == View.VISIBLE) {
                    AnimationUtil.bubbleAnimate(decButton, animationDelay += animationDelayStep);
                }
                if (imageReplyButton.getVisibility() == View.VISIBLE) {
                    AnimationUtil.bubbleAnimate(imageReplyButton, animationDelay += animationDelayStep);
                }
                if (infoButton.getVisibility() == View.VISIBLE) {
                    AnimationUtil.bubbleAnimate(infoButton, animationDelay += animationDelayStep);
                }
                if (editButton.getVisibility() == View.VISIBLE) {
                    AnimationUtil.bubbleAnimate(editButton, animationDelay += animationDelayStep);
                }
                if (starButton.getVisibility() == View.VISIBLE) {
                    AnimationUtil.bubbleAnimate(starButton, animationDelay += animationDelayStep);
                }
            }
        });
    }

    @Override
    public void dismiss() {
        if (isDismissing) {
            return;
        }

        isDismissing = true;

        if (this.ackDecPopupListener != null) {
            this.ackDecPopupListener.onClose();
        }

        AnimationUtil.popupAnimateOut(getContentView(), super::dismiss);
    }

    public void setListener(AckDecPopupListener listener) {
        this.ackDecPopupListener = listener;
    }

    @Override
    public void onClick(View v) {
        if (ackDecPopupListener != null) {
            int clickedItem;
            int id = v.getId();
            if (id == R.id.ack) {
                clickedItem = ITEM_ACK;
            } else if (id == R.id.dec) {
                clickedItem = ITEM_DEC;
            } else if (id == R.id.image_reply) {
                clickedItem = ITEM_IMAGE_REPLY;
            } else if (id == R.id.star) {
                clickedItem = ITEM_STAR;
            } else if (id == R.id.edit) {
                clickedItem = ITEM_EDIT;
            } else {
                clickedItem = ITEM_INFO;
            }

            ackDecPopupListener.onAckjiClicked(clickedItem);
            dismiss();
        }
    }

    public interface AckDecPopupListener {
        void onAckjiClicked(int clickedItem);

        void onOpen();

        void onClose();
    }
}

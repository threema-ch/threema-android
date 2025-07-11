/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2025 Threema GmbH
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

package ch.threema.app.emojis;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupWindow;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.utils.AnimationUtil;

public class DiverseEmojiPopup extends PopupWindow implements View.OnClickListener {

    private final Context context;
    private final ImageView originalImage, type1Image, type3Image, type4Image, type5Image, type6Image;
    private final View parentView;
    private View originView;
    private final EmojiManager emojiManager;
    private final EmojiService emojiService;
    private DiverseEmojiPopupListener diverseEmojiPopupListener;
    private final int popupHeight, popupOffsetLeft;

    final int[] location = new int[2];

    public DiverseEmojiPopup(Context context, View parentView) {
        super(context);

        this.context = context;
        this.parentView = parentView;
        this.emojiManager = EmojiManager.getInstance(context);
        this.emojiService = ThreemaApplication.requireServiceManager().getEmojiService();
        this.popupHeight = 2 * context.getResources().getDimensionPixelSize(R.dimen.emoji_popup_image_margin) +
            context.getResources().getDimensionPixelSize(R.dimen.emoji_popup_cardview_margin_bottom) +
            context.getResources().getDimensionPixelSize(R.dimen.emoji_picker_emoji_size);
        this.popupOffsetLeft = context.getResources().getDimensionPixelSize(R.dimen.emoji_popup_cardview_margin_horizontal);

        LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        FrameLayout topLayout = (FrameLayout) layoutInflater.inflate(R.layout.popup_diverse_emoji, null, true);

        this.originalImage = topLayout.findViewById(R.id.image_original);
        this.type1Image = topLayout.findViewById(R.id.image_type1);
        this.type3Image = topLayout.findViewById(R.id.image_type3);
        this.type4Image = topLayout.findViewById(R.id.image_type4);
        this.type5Image = topLayout.findViewById(R.id.image_type5);
        this.type6Image = topLayout.findViewById(R.id.image_type6);

        setContentView(topLayout);
        setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        setWidth(FrameLayout.LayoutParams.WRAP_CONTENT);
        setHeight(FrameLayout.LayoutParams.WRAP_CONTENT);

        setBackgroundDrawable(new BitmapDrawable());
        setAnimationStyle(0);
        setOutsideTouchable(false);
        setFocusable(true);
    }

    public void show(final View originView, final String originalEmoji) {
        EmojiInfo originalEmojiInfo = EmojiUtil.getEmojiInfo(originalEmoji);

        if (originalEmojiInfo == null || originalEmojiInfo.diversities.length != 5) {
            return;
        }

        this.originView = originView;

        this.originalImage.setImageDrawable(emojiManager.getEmojiDrawableAsync(originalEmoji));
        this.originalImage.setTag(originalEmoji);
        this.originalImage.setOnClickListener(this);

        this.type1Image.setImageDrawable(emojiManager.getEmojiDrawableAsync(originalEmojiInfo.diversities[0]));
        this.type1Image.setTag(originalEmojiInfo.diversities[0]);
        this.type1Image.setOnClickListener(this);

        this.type3Image.setImageDrawable(emojiManager.getEmojiDrawableAsync(originalEmojiInfo.diversities[1]));
        this.type3Image.setTag(originalEmojiInfo.diversities[1]);
        this.type3Image.setOnClickListener(this);

        this.type4Image.setImageDrawable(emojiManager.getEmojiDrawableAsync(originalEmojiInfo.diversities[2]));
        this.type4Image.setTag(originalEmojiInfo.diversities[2]);
        this.type4Image.setOnClickListener(this);

        this.type5Image.setImageDrawable(emojiManager.getEmojiDrawableAsync(originalEmojiInfo.diversities[3]));
        this.type5Image.setTag(originalEmojiInfo.diversities[3]);
        this.type5Image.setOnClickListener(this);

        this.type6Image.setImageDrawable(emojiManager.getEmojiDrawableAsync(originalEmojiInfo.diversities[4]));
        this.type6Image.setTag(originalEmojiInfo.diversities[4]);
        this.type6Image.setOnClickListener(this);

        if (this.diverseEmojiPopupListener != null) {
            this.diverseEmojiPopupListener.onOpen();
        }

        int[] originLocation = {0, 0};
        originView.getLocationInWindow(originLocation);
        showAtLocation(parentView, Gravity.LEFT | Gravity.TOP, originLocation[0] - this.popupOffsetLeft, originLocation[1] - this.popupHeight);

        getContentView().getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                getContentView().getViewTreeObserver().removeGlobalOnLayoutListener(this);

                AnimationUtil.getViewCenter(originView, getContentView(), location);
                AnimationUtil.popupAnimateIn(getContentView());
            }
        });
    }

    @Override
    public void dismiss() {
        if (this.diverseEmojiPopupListener != null) {
            this.diverseEmojiPopupListener.onClose();
        }

        AnimationUtil.popupAnimateOut(getContentView(), super::dismiss);

    }

    public void setListener(DiverseEmojiPopupListener listener) {
        this.diverseEmojiPopupListener = listener;
    }

    @Override
    public void onClick(View v) {
        if (diverseEmojiPopupListener != null) {
            String emojiSequence = (String) v.getTag();
            diverseEmojiPopupListener.onDiverseEmojiClick((String) this.originalImage.getTag(), emojiSequence);
            EmojiItemView emojiView = (EmojiItemView) originView;
            if (emojiView != null) {
                emojiView.setEmoji(emojiService.getPreferredDiversity(emojiSequence), true,
                    context.getResources().getColor(R.color.emoji_picker_hint));
            }
            dismiss();
        }
    }

    public interface DiverseEmojiPopupListener {
        void onDiverseEmojiClick(String originalEmojiSequence, String emojiSequence);

        void onOpen();

        void onClose();
    }
}

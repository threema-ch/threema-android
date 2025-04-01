/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2025 Threema GmbH
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
import ch.threema.app.utils.AnimationUtil;

public class EmojiDetailPopup extends PopupWindow implements View.OnClickListener {

    private final ImageView originalImage;
    private final View parentView;
    private final EmojiManager emojiManager;
    private EmojiDetailPopupListener emojiDetailPopupListener;
    private final int popupHeight;
    private final int popupOffsetLeft;

    public EmojiDetailPopup(final Context context, View parentView) {
        super(context);

        this.parentView = parentView;
        this.emojiManager = EmojiManager.getInstance(context);
        this.popupHeight = 2 * context.getResources().getDimensionPixelSize(R.dimen.emoji_popup_image_margin) +
            context.getResources().getDimensionPixelSize(R.dimen.emoji_popup_cardview_margin_bottom) +
            context.getResources().getDimensionPixelSize(R.dimen.emoji_popup_emoji_size);
        this.popupOffsetLeft = context.getResources().getDimensionPixelSize(R.dimen.emoji_popup_cardview_margin_horizontal);

        LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        FrameLayout topLayout = (FrameLayout) layoutInflater.inflate(R.layout.popup_emoji_detail, null, true);

        this.originalImage = topLayout.findViewById(R.id.image_original);

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
        this.originalImage.setImageDrawable(emojiManager.getEmojiDrawable(originalEmoji));
        this.originalImage.setTag(originalEmoji);
        this.originalImage.setOnClickListener(this);

        int[] originLocation = {0, 0};
        originView.getLocationInWindow(originLocation);

        showAtLocation(parentView, Gravity.LEFT | Gravity.TOP, originLocation[0] - this.popupOffsetLeft, originLocation[1] - this.popupHeight);

        getContentView().getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                getContentView().getViewTreeObserver().removeGlobalOnLayoutListener(this);
                AnimationUtil.popupAnimateIn(getContentView());
            }
        });
    }

    @Override
    public void dismiss() {
        AnimationUtil.popupAnimateOut(getContentView(), new Runnable() {
            @Override
            public void run() {
                EmojiDetailPopup.super.dismiss();
            }
        });

    }

    public void setListener(EmojiDetailPopupListener listener) {
        this.emojiDetailPopupListener = listener;
    }

    @Override
    public void onClick(View v) {
        String emojiSequence = (String) v.getTag();
        if (this.emojiDetailPopupListener != null) {
            this.emojiDetailPopupListener.onClick(emojiSequence);
        }
        dismiss();
    }

    public interface EmojiDetailPopupListener {
        void onClick(String emoijSequence);
    }
}

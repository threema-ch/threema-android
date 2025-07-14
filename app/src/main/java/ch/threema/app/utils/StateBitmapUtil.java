/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

package ch.threema.app.utils;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;

import java.util.EnumMap;
import java.util.Map;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import ch.threema.app.R;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.MessageState;

import static ch.threema.app.utils.MessageUtilKt.getUiContentColor;

/**
 * This class caches bitmaps and resources used for the message states (e.g. sent, read, acked...)
 */
public class StateBitmapUtil {
    // Singleton stuff
    private static StateBitmapUtil instance;

    @Nullable
    public static StateBitmapUtil getInstance() {
        return instance;
    }

    public static synchronized void init(Context context) {
        StateBitmapUtil.instance = new StateBitmapUtil(context.getApplicationContext());
    }

    private final Map<MessageState, Integer> messageStateBitmapResourceIds = new EnumMap<>(MessageState.class);
    private final Map<MessageState, Integer> messageStateDescriptionMap = new EnumMap<>(MessageState.class);
    private final int warningColor;

    private StateBitmapUtil(Context context) {
        this.messageStateBitmapResourceIds.put(MessageState.READ, R.drawable.ic_visibility_filled);
        this.messageStateBitmapResourceIds.put(MessageState.DELIVERED, R.drawable.ic_inbox_filled);
        this.messageStateBitmapResourceIds.put(MessageState.SENT, R.drawable.ic_mail_filled);
        this.messageStateBitmapResourceIds.put(MessageState.SENDFAILED, R.drawable.ic_report_problem_filled);
        this.messageStateBitmapResourceIds.put(MessageState.SENDING, R.drawable.ic_upload_filled);
        this.messageStateBitmapResourceIds.put(MessageState.PENDING, R.drawable.ic_upload_filled);
        this.messageStateBitmapResourceIds.put(MessageState.UPLOADING, R.drawable.ic_upload_filled);
        this.messageStateBitmapResourceIds.put(MessageState.TRANSCODING, R.drawable.ic_outline_hourglass_top_24);
        this.messageStateBitmapResourceIds.put(MessageState.CONSUMED, R.drawable.ic_baseline_hearing_24);
        this.messageStateBitmapResourceIds.put(MessageState.FS_KEY_MISMATCH, R.drawable.ic_baseline_key_off_24);

        this.messageStateDescriptionMap.put(MessageState.READ, R.string.state_read);
        this.messageStateDescriptionMap.put(MessageState.DELIVERED, R.string.state_delivered);
        this.messageStateDescriptionMap.put(MessageState.SENT, R.string.state_sent);
        this.messageStateDescriptionMap.put(MessageState.SENDFAILED, R.string.state_failed);
        this.messageStateDescriptionMap.put(MessageState.SENDING, R.string.state_sending);
        this.messageStateDescriptionMap.put(MessageState.PENDING, R.string.state_pending);
        this.messageStateDescriptionMap.put(MessageState.UPLOADING, R.string.state_sending);
        this.messageStateDescriptionMap.put(MessageState.TRANSCODING, R.string.state_processing);
        this.messageStateDescriptionMap.put(MessageState.CONSUMED, R.string.listened_to);
        this.messageStateDescriptionMap.put(MessageState.FS_KEY_MISMATCH, R.string.fs_key_mismatch);

        this.warningColor = context.getResources().getColor(R.color.material_red);
    }

    @ColorInt
    public int getWarningColor() {
        return warningColor;
    }

    @Nullable
    @DrawableRes
    public Integer getStateDrawable(@Nullable MessageState state) {
        return state != null
            ? messageStateBitmapResourceIds.get(state)
            : null;
    }

    @Nullable
    @StringRes
    public Integer getStateDescription(@Nullable MessageState state) {
        return state != null
            ? messageStateDescriptionMap.get(state)
            : null;
    }

    /**
     * Sets the icon depending on the messages state property into the passed {@code imageView}. When drawing the icon on a chat bubble view,
     * the tint should already be correct. Otherwise use the {@code tintOverride} to set a custom color.
     *
     * @param tintOverride If passed, it will be applied to any message state icon where the state is not either {@code SENDFAILED} or {@code FS_KEY_MISMATCH}
     */
    public void setStateDrawable(
        Context context,
        AbstractMessageModel messageModel,
        @Nullable ImageView imageView,
        @Nullable @ColorInt Integer tintOverride
    ) {
        if (imageView == null) {
            return;
        }

        //set to invisible
        imageView.setVisibility(View.GONE);

        if (MessageUtil.showStatusIcon(messageModel)) {
            MessageState state = messageModel.getState();

            @DrawableRes
            Integer resId = getStateDrawable(state);

            if (resId != null && ViewUtil.showAndSet(imageView, resId)) {

                @StringRes
                Integer stateDescription = getStateDescription(state);
                if (stateDescription != null) {
                    imageView.setContentDescription(context.getString(stateDescription));
                }

                if (state == MessageState.SENDFAILED || state == MessageState.FS_KEY_MISMATCH) {
                    imageView.setImageTintList(null);
                    imageView.setColorFilter(this.warningColor);
                } else {
                    if (tintOverride == null) {
                        imageView.setColorFilter(null);
                        imageView.setImageTintList(getUiContentColor(messageModel, context));
                    } else {
                        imageView.setImageTintList(null);
                        imageView.setColorFilter(tintOverride);
                    }
                }
            }
        }
    }
}

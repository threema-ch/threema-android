/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2024 Threema GmbH
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.EnumMap;
import java.util.Map;

import ch.threema.app.R;
import ch.threema.app.ui.listitemholder.ComposeMessageHolder;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.MessageState;

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
	private final Map<MessageState, Integer> messageStateDescriptionMap = new EnumMap<MessageState, Integer>(MessageState.class);
	private int warningColor;
	private int ackColor;
	private int decColor;

	private StateBitmapUtil(Context context) {
		this.messageStateBitmapResourceIds.put(MessageState.READ, R.drawable.ic_visibility_filled);
		this.messageStateBitmapResourceIds.put(MessageState.DELIVERED, R.drawable.ic_inbox_filled);
		this.messageStateBitmapResourceIds.put(MessageState.SENT, R.drawable.ic_mail_filled);
		this.messageStateBitmapResourceIds.put(MessageState.SENDFAILED, R.drawable.ic_report_problem_filled);
		this.messageStateBitmapResourceIds.put(MessageState.USERACK, R.drawable.ic_thumb_up_filled);
		this.messageStateBitmapResourceIds.put(MessageState.USERDEC, R.drawable.ic_thumb_down_filled);
		this.messageStateBitmapResourceIds.put(MessageState.SENDING, R.drawable.ic_upload_filled);
		this.messageStateBitmapResourceIds.put(MessageState.PENDING, R.drawable.ic_upload_filled);
		this.messageStateBitmapResourceIds.put(MessageState.TRANSCODING, R.drawable.ic_outline_hourglass_top_24);
		this.messageStateBitmapResourceIds.put(MessageState.CONSUMED, R.drawable.ic_baseline_hearing_24);
		this.messageStateBitmapResourceIds.put(MessageState.FS_KEY_MISMATCH, R.drawable.ic_baseline_key_off_24);

		this.messageStateDescriptionMap.put(MessageState.READ, R.string.state_read);
		this.messageStateDescriptionMap.put(MessageState.DELIVERED, R.string.state_delivered);
		this.messageStateDescriptionMap.put(MessageState.SENT, R.string.state_sent);
		this.messageStateDescriptionMap.put(MessageState.SENDFAILED, R.string.state_failed);
		this.messageStateDescriptionMap.put(MessageState.USERACK, R.string.state_ack);
		this.messageStateDescriptionMap.put(MessageState.USERDEC, R.string.state_dec);
		this.messageStateDescriptionMap.put(MessageState.SENDING, R.string.state_sending);
		this.messageStateDescriptionMap.put(MessageState.PENDING, R.string.state_pending);
		this.messageStateDescriptionMap.put(MessageState.TRANSCODING, R.string.state_processing);
		this.messageStateDescriptionMap.put(MessageState.CONSUMED, R.string.listened_to);
		this.messageStateDescriptionMap.put(MessageState.FS_KEY_MISMATCH, R.string.fs_key_mismatch);

		this.ackColor = context.getResources().getColor(R.color.material_green);
		this.decColor = context.getResources().getColor(R.color.material_orange);
		this.warningColor = context.getResources().getColor(R.color.material_red);
	}

	public void setStateDrawable(Context context, AbstractMessageModel messageModel, @Nullable ImageView imageView, boolean useInverseColors) {
		if (imageView == null) {
			return;
		}

		//set to invisible
		imageView.setVisibility(View.GONE);

		if (MessageUtil.showStatusIcon(messageModel)) {
			MessageState state = messageModel.getState();
			Integer resId = this.messageStateBitmapResourceIds.get(state);

			if (resId != null && ViewUtil.showAndSet(imageView, resId)) {
				imageView.setContentDescription(context.getString(this.messageStateDescriptionMap.get(state)));

				if (state == MessageState.SENDFAILED || state == MessageState.FS_KEY_MISMATCH) {
					imageView.setImageTintList(null);
					imageView.setColorFilter(this.warningColor);
				} else if (state == MessageState.USERACK) {
					imageView.setImageTintList(null);
					imageView.setColorFilter(this.ackColor);
				} else if (state == MessageState.USERDEC) {
					imageView.setImageTintList(null);
					imageView.setColorFilter(this.decColor);
				} else {
					imageView.setColorFilter(null);
					imageView.setImageTintList(ContextCompat.getColorStateList(context, R.color.bubble_text_colorstatelist));
				}
			}
		}
	}

	public void setGroupAckCount(AbstractMessageModel messageModel, @NonNull ComposeMessageHolder holder) {
		if (!ConfigUtils.isGroupAckEnabled()) {
			return;
		}

		if (messageModel instanceof GroupMessageModel) {
			GroupMessageModel groupMessageModel = (GroupMessageModel) messageModel;
			if (groupMessageModel.getGroupMessageStates() != null && groupMessageModel.getGroupMessageStates().size() > 0) {
				int ackCount = 0;
				int decCount = 0;

				Map<String, Object> messageStatesMap = groupMessageModel.getGroupMessageStates();
				for(Map.Entry<String, Object> entry: messageStatesMap.entrySet()) {
					if (MessageState.USERACK.toString().equals(entry.getValue())) {
						ackCount++;
					} else if (MessageState.USERDEC.toString().equals(entry.getValue())) {
						decCount++;
					}
				}

				if (ackCount > 0 || decCount > 0) {
					MessageState state = messageModel.getState();

					if (ackCount > 0) {
						holder.groupAckThumbsUpImage.setImageResource(state == MessageState.USERACK ? R.drawable.ic_thumb_up_filled : R.drawable.ic_thumb_up_grey600_24dp);
						holder.groupAckThumbsUpCount.setText(String.valueOf(ackCount));
						holder.groupAckThumbsUpImage.setVisibility(View.VISIBLE);
						holder.groupAckThumbsUpCount.setVisibility(View.VISIBLE);
					} else {
						holder.groupAckThumbsUpImage.setVisibility(View.GONE);
						holder.groupAckThumbsUpCount.setVisibility(View.GONE);
					}

					if (decCount > 0) {
						holder.groupAckThumbsDownImage.setImageResource(state == MessageState.USERDEC ? R.drawable.ic_thumb_down_filled : R.drawable.ic_thumb_down_grey600_24dp);
						holder.groupAckThumbsDownCount.setText(String.valueOf(decCount));
						holder.groupAckThumbsDownImage.setVisibility(View.VISIBLE);
						holder.groupAckThumbsDownCount.setVisibility(View.VISIBLE);
					} else {
						holder.groupAckThumbsDownImage.setVisibility(View.GONE);
						holder.groupAckThumbsDownCount.setVisibility(View.GONE);
					}

					holder.groupAckContainer.setVisibility(View.VISIBLE);
					holder.deliveredIndicator.setVisibility(View.GONE);
					return;
				}
			}
		}
		holder.groupAckContainer.setVisibility(View.GONE);
	}
}

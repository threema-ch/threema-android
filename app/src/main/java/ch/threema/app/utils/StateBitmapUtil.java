/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2020 Threema GmbH
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

import androidx.annotation.Nullable;
import ch.threema.app.R;
import ch.threema.storage.models.AbstractMessageModel;
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

	private final Context context;
	private Map<MessageState, Integer> messageStateBitmapResourceIds = new EnumMap<>(MessageState.class);
	private Map<MessageState, Integer> messageStateDescriptionMap = new EnumMap<MessageState, Integer>(MessageState.class);
	private int regularColor;
	private int warningColor;
	private int ackColor;
	private int decColor;

	private StateBitmapUtil(Context context) {
		this.context = context;
		buildState();
	}

	private void buildState() {
		this.messageStateBitmapResourceIds.put(MessageState.READ, R.drawable.ic_visibility_filled);
		this.messageStateBitmapResourceIds.put(MessageState.DELIVERED, R.drawable.ic_inbox_filled);
		this.messageStateBitmapResourceIds.put(MessageState.SENT, R.drawable.ic_mail_filled);
		this.messageStateBitmapResourceIds.put(MessageState.SENDFAILED, R.drawable.ic_report_problem_filled);
		this.messageStateBitmapResourceIds.put(MessageState.USERACK, R.drawable.ic_thumb_up_filled);
		this.messageStateBitmapResourceIds.put(MessageState.USERDEC, R.drawable.ic_thumb_down_filled);
		this.messageStateBitmapResourceIds.put(MessageState.SENDING, R.drawable.ic_upload_filled);
		this.messageStateBitmapResourceIds.put(MessageState.PENDING, R.drawable.ic_upload_filled);
		this.messageStateBitmapResourceIds.put(MessageState.TRANSCODING, R.drawable.ic_outline_hourglass_top_24);

		this.messageStateDescriptionMap.put(MessageState.READ, R.string.state_read);
		this.messageStateDescriptionMap.put(MessageState.DELIVERED, R.string.state_delivered);
		this.messageStateDescriptionMap.put(MessageState.SENT, R.string.state_sent);
		this.messageStateDescriptionMap.put(MessageState.SENDFAILED, R.string.state_failed);
		this.messageStateDescriptionMap.put(MessageState.USERACK, R.string.state_ack);
		this.messageStateDescriptionMap.put(MessageState.USERDEC, R.string.state_dec);
		this.messageStateDescriptionMap.put(MessageState.SENDING, R.string.state_sending);
		this.messageStateDescriptionMap.put(MessageState.PENDING, R.string.state_pending);
		this.messageStateDescriptionMap.put(MessageState.TRANSCODING, R.string.state_processing);

		this.ackColor = context.getResources().getColor(R.color.material_green);
		this.decColor = context.getResources().getColor(R.color.material_orange);
		this.warningColor = context.getResources().getColor(R.color.material_red);

		this.refresh();
	}

	public void refresh() {
		if (ConfigUtils.getAppTheme(context) != ConfigUtils.THEME_LIGHT) {
			this.regularColor = context.getResources().getColor(R.color.dark_text_color_secondary);
		} else {
			this.regularColor = context.getResources().getColor(R.color.text_color_secondary);
		}
	}

	public void setStateDrawable(AbstractMessageModel messageModel, @Nullable ImageView imageView, boolean useInverseColors) {
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

				if (state == MessageState.SENDFAILED) {
					imageView.setColorFilter(this.warningColor);
				} else if (state == MessageState.USERACK) {
					imageView.setColorFilter(this.ackColor);
				}  else if (state == MessageState.USERDEC) {
					imageView.setColorFilter(this.decColor);
				} else {
					if (useInverseColors) {
						imageView.setColorFilter(this.regularColor);
					}
				}
			}
		}
	}
}

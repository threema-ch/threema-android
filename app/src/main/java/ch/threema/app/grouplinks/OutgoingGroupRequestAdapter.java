/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2022 Threema GmbH
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

package ch.threema.app.grouplinks;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.slf4j.Logger;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.RecyclerView;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.ContactService;
import ch.threema.app.ui.AvatarView;
import ch.threema.app.ui.CheckableRelativeLayout;
import ch.threema.app.utils.NameUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.group.OutgoingGroupJoinRequestModel;

public class OutgoingGroupRequestAdapter extends RecyclerView.Adapter<OutgoingGroupRequestAdapter.OutgoingRequestViewHolder> {
	private static final Logger logger = LoggingUtil.getThreemaLogger("OutgoingGroupRequestAdapter");

	private final Context context;
	private final LayoutInflater inflater;
	private OutgoingGroupRequestAdapter.OnClickItemListener onClickItemListener;
	private List<OutgoingGroupJoinRequestModel> groupRequestModels;
	private final OutgoingGroupRequestViewModel viewModel;
	private final ContactService contactService;
	private final int colorRedId;
	private final int colorGreenId;
	private final int colorGreyId;

	OutgoingGroupRequestAdapter(Context context, OutgoingGroupRequestViewModel viewModel) throws ThreemaException {
		this.context = context;
		this.inflater = LayoutInflater.from(this.context);
		this.viewModel = viewModel;
		ServiceManager serviceManager =ThreemaApplication.getServiceManager();
		if (serviceManager == null) {
			throw new ThreemaException("Missing serviceManager");
		}
		this.contactService = serviceManager.getContactService();
		this.colorRedId = ContextCompat.getColor(context, R.color.material_red);
		this.colorGreenId = ContextCompat.getColor(context, R.color.material_green);
		this.colorGreyId = ContextCompat.getColor(context, R.color.material_grey_400);
	}

	static class OutgoingRequestViewHolder extends RecyclerView.ViewHolder {
		private final AvatarView stateIndicator;
		private final TextView groupName;
		private final TextView state;
		private final TextView groupAdminName;
		private final TextView sentDate;

		public OutgoingRequestViewHolder(@NonNull View itemView) {
			super(itemView);
			this.stateIndicator = itemView.findViewById(R.id.avatar_view);
			this.groupName = itemView.findViewById(R.id.item_title);
			this.state = itemView.findViewById(R.id.state);
			this.groupAdminName = itemView.findViewById(R.id.item_property1);
			this.sentDate = itemView.findViewById(R.id.item_property2);
		}
	}

	@NonNull
	@Override
	public OutgoingRequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		View itemView = this.inflater.inflate(R.layout.item_group_link, parent, false);
		return new OutgoingGroupRequestAdapter.OutgoingRequestViewHolder(itemView);
	}

	@Override
	public void onBindViewHolder(@NonNull OutgoingGroupRequestAdapter.OutgoingRequestViewHolder holder, int position) {
		if (this.groupRequestModels == null) {
			logger.warn("no group requests to show");
			return;
		}

		final OutgoingGroupJoinRequestModel groupJoinRequestModel = this.groupRequestModels.get(position);

		holder.groupName.setText(groupJoinRequestModel.getGroupName());
		holder.groupAdminName.setText(
			String.format(
				context.getString(R.string.sent_to),
				NameUtil.getDisplayNameOrNickname(
					groupJoinRequestModel.getAdminIdentity(), this.contactService
				)
			)
		);
		holder.sentDate.setText(
			String.format(
				context.getString(R.string.sent_on),
				DateUtils.formatDateTime(
					this.context,
					groupJoinRequestModel.getRequestTime().getTime(),
					DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME))
		);
		String stateDescription;
		Drawable stateIndicatorDrawable;
		switch (groupJoinRequestModel.getStatus()) {
			case ACCEPTED:
				stateDescription = context.getString(R.string.group_request_state_accepted);
				stateIndicatorDrawable = DrawableCompat.wrap(AppCompatResources.getDrawable(context, R.drawable.ic_check));
				DrawableCompat.setTint(stateIndicatorDrawable, colorGreenId);
				holder.state.setTextColor(colorGreenId);
				break;
			case REJECTED:
				stateDescription = context.getString(R.string.group_request_state_rejected);
				stateIndicatorDrawable = DrawableCompat.wrap(AppCompatResources.getDrawable(context, R.drawable.ic_hand_up_stop_outline));
				DrawableCompat.setTint(stateIndicatorDrawable, colorRedId);
				holder.state.setTextColor(colorRedId);
				break;
			case GROUP_FULL:
				stateDescription = context.getString(R.string.group_request_state_full);
				stateIndicatorDrawable = DrawableCompat.wrap(AppCompatResources.getDrawable(context, R.drawable.ic_group_off_outline));
				DrawableCompat.setTint(stateIndicatorDrawable, colorRedId);
				holder.state.setTextColor(colorRedId);
				break;
			case EXPIRED:
				stateDescription = context.getString(R.string.group_request_state_expired);
				stateIndicatorDrawable = DrawableCompat.wrap(AppCompatResources.getDrawable(context, R.drawable.ic_timelapse_outline));
				DrawableCompat.setTint(stateIndicatorDrawable, colorRedId);
				holder.state.setTextColor(colorRedId);
				break;
			case UNKNOWN:
			default:
				stateDescription = context.getString(R.string.group_request_state_pending);
				stateIndicatorDrawable = DrawableCompat.wrap(AppCompatResources.getDrawable(context, R.drawable.ic_pending_outline));
				DrawableCompat.setTint(stateIndicatorDrawable, colorGreyId);
				holder.state.setTextColor(colorGreyId);
		}
		holder.state.setText(stateDescription);
		holder.stateIndicator.setImageDrawable(stateIndicatorDrawable);

		((CheckableRelativeLayout) holder.itemView).setChecked(viewModel.isChecked(position));
		if (this.onClickItemListener != null) {
			holder.itemView.setOnClickListener(
				v -> onClickItemListener.onClick(groupJoinRequestModel, holder.itemView, position)
			);
			holder.itemView.setOnLongClickListener(
				v -> onClickItemListener.onLongClick(groupJoinRequestModel, holder.itemView, position)
			);
		}
	}

	@Override
	public int getItemCount() {
		if (groupRequestModels == null) {
			return 0;
		}
		return groupRequestModels.size();
	}

	void setRequestModels(List<OutgoingGroupJoinRequestModel> newRequestModels) {
		this.groupRequestModels = newRequestModels;
		notifyDataSetChanged();
	}

	void setOnClickItemListener(OutgoingGroupRequestAdapter.OnClickItemListener onClickItemListener) {
		this.onClickItemListener = onClickItemListener;
	}

	public interface OnClickItemListener {
		void onClick(OutgoingGroupJoinRequestModel groupJoinRequestModel, View view, int position);
		boolean onLongClick(OutgoingGroupJoinRequestModel groupJoinRequestModel, View itemView, int position);
	}
}

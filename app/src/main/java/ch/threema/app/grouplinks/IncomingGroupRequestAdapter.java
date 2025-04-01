/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2024 Threema GmbH
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
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.slf4j.Logger;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
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
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.models.group.GroupInviteModel;
import ch.threema.storage.models.group.IncomingGroupJoinRequestModel;
import java8.util.Optional;

public class IncomingGroupRequestAdapter extends RecyclerView.Adapter<IncomingGroupRequestAdapter.IncomingGroupRequestViewHolder> {
    private static final Logger logger = LoggingUtil.getThreemaLogger("IncomingGroupRequestAdapter");

    private final Context context;
    private final LayoutInflater inflater;
    private IncomingGroupRequestAdapter.OnClickItemListener onClickItemListener;
    private List<IncomingGroupJoinRequestModel> groupRequestModels;
    private final IncomingGroupRequestViewModel viewModel;
    private final ContactService contactService;
    private final DatabaseServiceNew databaseServiceNew;

    IncomingGroupRequestAdapter(Context context, IncomingGroupRequestViewModel viewModel) throws ThreemaException {
        this.context = context;
        this.inflater = LayoutInflater.from(this.context);
        this.viewModel = viewModel;
        ServiceManager serviceManager = ThreemaApplication.getServiceManager();
        if (serviceManager == null) {
            throw new ThreemaException("Missing serviceManager");
        }
        this.contactService = serviceManager.getContactService();
        this.databaseServiceNew = serviceManager.getDatabaseServiceNew();
    }

    static class IncomingGroupRequestViewHolder extends RecyclerView.ViewHolder {
        private final AvatarView requesterAvatar;
        private final TextView requesterName;
        private final TextView state;
        private final TextView receivedTrough;
        private final TextView receivedDate;

        public IncomingGroupRequestViewHolder(@NonNull View itemView) {
            super(itemView);
            this.requesterAvatar = itemView.findViewById(R.id.avatar_view);
            this.requesterName = itemView.findViewById(R.id.item_title);
            this.state = itemView.findViewById(R.id.state);
            this.receivedTrough = itemView.findViewById(R.id.item_property1);
            this.receivedDate = itemView.findViewById(R.id.item_property2);
        }
    }

    @NonNull
    @Override
    public IncomingGroupRequestAdapter.IncomingGroupRequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = this.inflater.inflate(R.layout.item_group_link, parent, false);
        return new IncomingGroupRequestAdapter.IncomingGroupRequestViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull IncomingGroupRequestViewHolder holder, int position) {
        if (this.groupRequestModels == null) {
            logger.warn("no group requests to show");
            return;
        }

        final IncomingGroupJoinRequestModel groupJoinRequestModel = this.groupRequestModels.get(position);

        holder.requesterAvatar.setImageBitmap(
            contactService.getAvatar(
                contactService.getByIdentity(
                    groupJoinRequestModel.getRequestingIdentity()
                ), false
            )
        );
        holder.requesterName.setText(
            NameUtil.getDisplayName(
                contactService.getByIdentity(
                    groupJoinRequestModel.getRequestingIdentity()
                )
            )
        );

        Optional<GroupInviteModel> groupInviteModel = databaseServiceNew.getGroupInviteModelFactory()
            .getById(groupJoinRequestModel.getGroupInviteId());

        if (groupInviteModel.isPresent()) {
            holder.receivedTrough.setText(
                String.format(
                    context.getString(R.string.group_request_received_through),
                    groupInviteModel.get().getInviteName())
            );
        } else {
            holder.receivedTrough.setText(
                String.format(
                    context.getString(R.string.group_request_received_through),
                    context.getString(R.string.group_request_link_already_deleted)
                )
            );
        }

        holder.receivedDate.setText(
            String.format(
                context.getString(R.string.received_on),
                DateUtils.formatDateTime(
                    this.context,
                    groupJoinRequestModel.getRequestTime().getTime(),
                    DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME))
        );

        switch (groupJoinRequestModel.getResponseStatus()) {
            case ACCEPTED:
                holder.state.setTextColor(ContextCompat.getColor(context, R.color.material_green));
                holder.state.setText(context.getString(R.string.group_request_state_accepted));
                break;
            case REJECTED:
                holder.state.setTextColor(ContextCompat.getColor(context, R.color.material_red));
                holder.state.setText(context.getString(R.string.group_request_state_rejected));
                break;
            case EXPIRED:
                holder.state.setTextColor(ContextCompat.getColor(context, R.color.material_red));
                holder.state.setText(context.getString(R.string.group_request_state_expired));
                break;
            case GROUP_FULL:
                holder.state.setTextColor(ContextCompat.getColor(context, R.color.material_red));
                holder.state.setText(context.getString(R.string.group_request_state_full));
                break;
            case OPEN:
            default:
                holder.state.setTextColor(ContextCompat.getColor(context, R.color.material_grey_400));
                holder.state.setText(context.getString(R.string.group_request_state_pending));
        }

        ((CheckableRelativeLayout) holder.itemView).setChecked(viewModel.isChecked(position));
        if (this.onClickItemListener != null) {
            holder.itemView.setOnClickListener(v -> onClickItemListener.onClick(groupJoinRequestModel, holder.itemView, position));
            holder.itemView.setOnLongClickListener(v -> onClickItemListener.onLongClick(groupJoinRequestModel, holder.itemView, position));
        }
    }

    @Override
    public int getItemCount() {
        if (groupRequestModels == null) {
            return 0;
        }

        return groupRequestModels.size();
    }

    void setRequestModels(List<IncomingGroupJoinRequestModel> newRequestModels) {
        this.groupRequestModels = newRequestModels;
        notifyDataSetChanged();
    }

    void setOnClickItemListener(IncomingGroupRequestAdapter.OnClickItemListener onClickItemListener) {
        this.onClickItemListener = onClickItemListener;
    }

    public interface OnClickItemListener {
        void onClick(IncomingGroupJoinRequestModel groupJoinRequestModel, View view, int position);

        boolean onLongClick(IncomingGroupJoinRequestModel groupJoinRequestModel, View itemView, int position);
    }
}

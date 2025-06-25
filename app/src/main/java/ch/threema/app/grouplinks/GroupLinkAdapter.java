/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2025 Threema GmbH
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

import java.util.Date;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.adapters.AbstractRecyclerAdapter;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.ui.CheckableRelativeLayout;
import ch.threema.app.ui.listitemholder.AvatarListItemHolder;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.group.GroupInviteModel;

public class GroupLinkAdapter extends AbstractRecyclerAdapter<GroupInviteModel, RecyclerView.ViewHolder> {
    private static final Logger logger = LoggingUtil.getThreemaLogger("GroupLinkAdapter");

    private final Context context;
    private final LayoutInflater inflater;
    private GroupLinkAdapter.OnClickItemListener onClickItemListener;
    private List<GroupInviteModel> groupInviteModels;
    private final GroupLinkViewModel viewModel;

    private final int colorIdRed;
    private final int colorIdGreen;

    GroupLinkAdapter(Context context, GroupLinkViewModel viewModel) throws ThreemaException {
        this.context = context;
        this.inflater = LayoutInflater.from(this.context);
        this.viewModel = viewModel;
        ServiceManager serviceManager = ThreemaApplication.getServiceManager();
        if (serviceManager == null) {
            throw new ThreemaException("Missing serviceManager");
        }
        this.colorIdRed = ContextCompat.getColor(context, R.color.material_red);
        this.colorIdGreen = ContextCompat.getColor(context, R.color.material_green);
    }

    static class GroupLinkViewHolder extends RecyclerView.ViewHolder {
        private final TextView linkname;
        private final TextView state;
        private final TextView administered;
        private final TextView expirationDate;
        AvatarListItemHolder avatarListItemHolder;

        public GroupLinkViewHolder(@NonNull View itemView) {
            super(itemView);
            linkname = itemView.findViewById(R.id.item_title);
            state = itemView.findViewById(R.id.state);
            administered = itemView.findViewById(R.id.item_property1);
            expirationDate = itemView.findViewById(R.id.item_property2);
            avatarListItemHolder = new AvatarListItemHolder();
            avatarListItemHolder.avatarView = itemView.findViewById(R.id.avatar_view);
        }
    }

    @NonNull
    @Override
    public GroupLinkViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new GroupLinkViewHolder(this.inflater.inflate(R.layout.item_group_link, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
        if (groupInviteModels == null) {
            logger.warn("no group links to show");
            return;
        }

        GroupLinkViewHolder holder = (GroupLinkViewHolder) h;
        final GroupInviteModel groupInviteModel = this.groupInviteModels.get(position);
        if (groupInviteModel.isDefault()) {
            holder.avatarListItemHolder.avatarView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_link_outline));
        } else {
            holder.avatarListItemHolder.avatarView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_outline_settings_advanced));
        }
        holder.linkname.setText(groupInviteModel.getInviteName());
        holder.administered.setText(
            groupInviteModel.getManualConfirmation() ?
                context.getString(R.string.group_link_administered) :
                context.getString(R.string.group_link_open)
        );
        Date expirationDate = groupInviteModel.getExpirationDate();
        String stateDesc = context.getString(R.string.group_link_valid);
        int textColorId = colorIdGreen;
        if (expirationDate != null) {
            holder.expirationDate.setText(
                DateUtils.formatDateTime(
                    this.context, expirationDate.getTime(),
                    DateUtils.FORMAT_SHOW_DATE)
            );
            if (expirationDate.getTime() < System.currentTimeMillis()) {
                textColorId = colorIdRed;
                stateDesc = context.getString(R.string.group_link_invalid);
            }
        } else {
            holder.expirationDate.setText(context.getString(R.string.group_link_expiration_none));
        }
        holder.state.setText(stateDesc);
        holder.state.setTextColor(textColorId);

        ((CheckableRelativeLayout) holder.itemView).setChecked(viewModel.isChecked(position));

        if (this.onClickItemListener != null) {
            holder.itemView.setOnClickListener(v -> onClickItemListener.onClick(groupInviteModel, holder.itemView, position));
            holder.itemView.setOnLongClickListener(v -> onClickItemListener.onLongClick(position));
        }
    }

    @Override
    public int getItemCount() {
        if (groupInviteModels != null) {
            return groupInviteModels.size();
        } else {
            return 0;
        }
    }

    public List<GroupInviteModel> getAllData() {
        return groupInviteModels;
    }

    public GroupInviteModel getItemAtPosition(int position) {
        return groupInviteModels.get(position);
    }

    public void setGroupInviteModels(List<GroupInviteModel> newGroupInviteModels) {
        this.groupInviteModels = newGroupInviteModels;
        notifyDataSetChanged();
    }

    public void setOnClickItemListener(GroupLinkAdapter.OnClickItemListener onClickItemListener) {
        this.onClickItemListener = onClickItemListener;
    }

    public interface OnClickItemListener {
        void onClick(GroupInviteModel groupInviteModel, View view, int position);

        boolean onLongClick(int position);
    }
}

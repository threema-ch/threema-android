/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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

package ch.threema.app.adapters.ballot;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.bumptech.glide.RequestManager;
import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.R;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.ballot.BallotService;
import ch.threema.app.ui.AvatarListItemUtil;
import ch.threema.app.ui.listitemholder.AvatarListItemHolder;
import ch.threema.app.utils.BallotUtil;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.ViewUtil;
import ch.threema.storage.models.ballot.BallotModel;

public class BallotOverviewListAdapter extends ArrayAdapter<BallotModel> {

    private final Context context;
    private final List<BallotModel> values;
    @Nullable
    private final MessageReceiver<?> messageReceiver;
    private final BallotService ballotService;
    private final ContactService contactService;
    private final @NonNull RequestManager requestManager;

    public BallotOverviewListAdapter(
        Context context,
        List<BallotModel> values,
        @Nullable MessageReceiver<?> messageReceiver,
        BallotService ballotService,
        ContactService contactService,
        @NonNull RequestManager requestManager
    ) {
        super(context, R.layout.item_ballot_overview, values);

        this.context = context;
        this.values = values;
        this.messageReceiver = messageReceiver;
        this.ballotService = ballotService;
        this.contactService = contactService;
        this.requestManager = requestManager;
    }

    private static class BallotOverviewItemHolder extends AvatarListItemHolder {
        public TextView name;
        public TextView state;
        public TextView creator;
        public TextView creationDate;
        public MaterialButton countBoxView;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View itemView = convertView;
        BallotOverviewItemHolder holder;

        if (convertView == null) {
            holder = new BallotOverviewItemHolder();
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            itemView = inflater.inflate(R.layout.item_ballot_overview, parent, false);

            holder.name = itemView.findViewById(R.id.ballot_name);
            holder.state = itemView.findViewById(R.id.ballot_state);
            holder.creationDate = itemView.findViewById(R.id.ballot_creation_date);
            holder.creator = itemView.findViewById(R.id.ballot_creator);
            holder.countBoxView = itemView.findViewById(R.id.ballot_updates);
            holder.avatarView = itemView.findViewById(R.id.avatar_view);

            itemView.setTag(holder);
        } else {
            holder = (BallotOverviewItemHolder) itemView.getTag();
        }
        final BallotModel ballotModel = values.get(position);

        if (ballotModel != null) {
            AvatarListItemUtil.loadAvatar(
                ballotModel.getCreatorIdentity(),
                contactService,
                holder,
                requestManager
            );

            if (holder.name != null) {
                holder.name.setText(ballotModel.getName());
            }

            if (ballotModel.getState() == BallotModel.State.CLOSED) {
                holder.state.setText(R.string.ballot_state_closed);
                holder.state.setVisibility(View.VISIBLE);
            } else if (ballotModel.getState() == BallotModel.State.OPEN) {
                var myIdentity = contactService.getMe().getIdentity();
                if (BallotUtil.canClose(ballotModel, myIdentity, messageReceiver) || BallotUtil.canViewMatrix(ballotModel)) {
                    holder.state.setText(String.format(Locale.US, "%d / %d",
                        ballotService.getVotedParticipants(ballotModel.getId()).size(),
                        ballotService.getParticipants(ballotModel.getId()).length));
                } else if (messageReceiver == null) {
                    holder.state.setText("");
                } else {
                    holder.state.setText(R.string.ballot_secret);
                }
                holder.state.setVisibility(View.VISIBLE);
            } else {
                holder.state.setText("");
                holder.state.setVisibility(View.GONE);
            }

            ViewUtil.show(holder.countBoxView, false);

            if (holder.creationDate != null) {
                holder.creationDate.setText(LocaleUtil.formatTimeStampString(this.getContext(), ballotModel.getCreatedAt().getTime(), true));
            }

            if (holder.creator != null) {
                holder.creator.setText(NameUtil.getDisplayName(this.contactService.getByIdentity(ballotModel.getCreatorIdentity())));
            }
        }

        return itemView;
    }
}

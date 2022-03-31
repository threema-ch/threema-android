/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2022 Threema GmbH
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

package ch.threema.app.adapters;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.slf4j.Logger;

import java.io.IOException;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.SwitchCompat;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;
import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.GroupDetailActivity;
import ch.threema.app.dialogs.ShowOnceDialog;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.group.GroupInviteService;
import ch.threema.app.ui.AvatarView;
import ch.threema.app.ui.SectionHeaderView;
import ch.threema.app.utils.AdapterUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.csp.messages.group.GroupInviteToken;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.group.GroupInviteModel;
import java8.util.Optional;

public class GroupDetailAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
	private static final Logger logger = LoggingUtil.getThreemaLogger("GroupDetailAdapter");

	private static final int TYPE_HEADER = 0;
	private static final int TYPE_ITEM = 1;

	private Context context;
	private ContactService contactService;
	private GroupInviteService groupInviteService;
	private GroupModel groupModel;
	private GroupInviteModel defaultGroupInviteModel;
	private List<ContactModel> contactModels; // Cached copy of group members
	private OnGroupDetailsClickListener onClickListener;
	HeaderHolder headerHolder;
	private boolean warningShown = false;

	public static class ItemHolder extends RecyclerView.ViewHolder {
		public final View view;
		public final TextView nameView, idView;
		public final AvatarView avatarView;

		public ItemHolder(View view) {
			super(view);
			this.view = view;
			this.nameView = itemView.findViewById(R.id.group_name);
			this.avatarView = itemView.findViewById(R.id.avatar_view);
			this.idView = itemView.findViewById(R.id.threemaid);
		}
	}

	public class HeaderHolder extends RecyclerView.ViewHolder {
		private final SectionHeaderView groupMembersTitleView;
		private final LinearLayout groupOwnerContainerView;
		private final AvatarView ownerAvatarView;
		private final TextView ownerName;
		private final TextView ownerThreemaId;
		private final SectionHeaderView ownerNameTitle;
		private final ConstraintLayout linkContainerView;
		private final SectionHeaderView groupLinkTitle;
		private final SwitchCompat linkEnableSwitch;
		private final TextView linkString;
		private final AppCompatImageButton linkResetButton;
		private final AppCompatImageButton linkShareButton;

		public HeaderHolder(View view) {
			super(view);

			// items in object
			this.groupMembersTitleView = itemView.findViewById(R.id.group_members_title);
			this.groupOwnerContainerView = itemView.findViewById(R.id.group_owner_container);
			this.ownerAvatarView = itemView.findViewById(R.id.avatar_view);
			this.ownerName = itemView.findViewById(R.id.group_name);
			this.ownerThreemaId = itemView.findViewById(R.id.threemaid);
			this.ownerNameTitle = itemView.findViewById(R.id.group_owner_title);
			this.linkContainerView = itemView.findViewById(R.id.group_link_container);
			this.groupLinkTitle = itemView.findViewById(R.id.group_link_header);
			this.linkEnableSwitch = itemView.findViewById(R.id.group_link_switch);
			this.linkString = itemView.findViewById(R.id.group_link_string);
			this.linkResetButton = itemView.findViewById(R.id.reset_button);
			this.linkShareButton = itemView.findViewById(R.id.share_button);
		}
	}

	public GroupDetailAdapter(Context context, GroupModel groupModel) {
		this.context = context;
		this.groupModel = groupModel;
		ServiceManager serviceManager = ThreemaApplication.getServiceManager();

		try {
			this.contactService = serviceManager.getContactService();
			this.groupInviteService = serviceManager.getGroupInviteService();
		} catch (Exception e) {
			logger.error("Exception, failed to get required services", e);
		}
	}

	public void setContactModels(List<ContactModel> newContactModels) {
		this.contactModels = newContactModels;
		notifyDataSetChanged();
	}

	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		if (viewType == TYPE_ITEM) {
			View v = LayoutInflater.from(parent.getContext())
					.inflate(R.layout.item_group_detail, parent, false);

			return new ItemHolder(v);
		} else if (viewType == TYPE_HEADER) {
			View v = LayoutInflater.from(parent.getContext())
					.inflate(R.layout.header_group_detail, parent, false);

			return new HeaderHolder(v);
		}
		throw new RuntimeException("no matching item type");
	}

	@Override
	public void onBindViewHolder(RecyclerView.ViewHolder holder, final int position) {
		if (holder instanceof ItemHolder) {
			ItemHolder itemHolder = (ItemHolder) holder;
			final ContactModel contactModel = getItem(position);
			Bitmap avatar = this.contactService.getAvatar(contactModel, false);

			itemHolder.nameView.setText(NameUtil.getDisplayNameOrNickname(contactModel, true));
			itemHolder.idView.setText(contactModel.getIdentity());
			AdapterUtil.styleContact(itemHolder.nameView, contactModel);
			itemHolder.avatarView.setImageBitmap(avatar);
			itemHolder.avatarView.setBadgeVisible(contactService.showBadge(contactModel));
			itemHolder.view.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					onClickListener.onGroupMemberClick(v, contactModel);
				}
			});
		} else {
			this.headerHolder = (HeaderHolder) holder;
			headerHolder.groupOwnerContainerView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					onClickListener.onGroupOwnerClick(v, groupModel.getCreatorIdentity());
				}
			});

			ContactModel ownerContactModel = contactService.getByIdentity(groupModel.getCreatorIdentity());
			if (ownerContactModel != null) {
				Bitmap bitmap = contactService.getAvatar(ownerContactModel, false);

				headerHolder.ownerAvatarView.setImageBitmap(bitmap);
				headerHolder.ownerThreemaId.setText(ownerContactModel.getIdentity());
				headerHolder.ownerName.setText(NameUtil.getDisplayNameOrNickname(ownerContactModel, true));

				if (!ConfigUtils.supportsGroupLinks() || ownerContactModel != contactService.getMe()) {
					headerHolder.linkContainerView.setVisibility(View.GONE);
				}
				else {
					initGroupLinkSection();
				}
			} else {
				// creator is no longer around / has been revoked
				headerHolder.ownerAvatarView.setImageBitmap(contactService.getDefaultAvatar(null, false));
				headerHolder.ownerThreemaId.setText(groupModel.getCreatorIdentity());
				headerHolder.ownerName.setText(R.string.invalid_threema_id);
			}
			headerHolder.ownerNameTitle.setText(context.getString(R.string.add_group_owner) +
					" (" + LocaleUtil.formatTimeStampString(context, groupModel.getCreatedAt().getTime(), false) +
					")");

			if (contactModels != null) {
				headerHolder.groupMembersTitleView.setText(context.getString(R.string.add_group_members_list) +
					" (" + contactModels.size() + "/" + BuildConfig.MAX_GROUP_SIZE + ")");
			}
		}
	}

	private void initGroupLinkSection() {
		Optional<GroupInviteModel> groupInviteModelOptional = groupInviteService.getDefaultGroupInvite(groupModel);
		if (groupInviteModelOptional.isPresent()) {
			this.defaultGroupInviteModel = groupInviteModelOptional.get();
		}
		boolean enableGroupLinkSwitch = defaultGroupInviteModel != null && !defaultGroupInviteModel.isInvalidated();
		headerHolder.linkEnableSwitch.setChecked(enableGroupLinkSwitch);
		setGroupLinkViewsEnabled(enableGroupLinkSwitch);
		if (defaultGroupInviteModel != null) {
			encodeAndDisplayDefaultLink();
		}
		else {
			headerHolder.linkString.setText(R.string.group_link_none);
			headerHolder.linkResetButton.setVisibility(View.INVISIBLE);
			headerHolder.linkShareButton.setVisibility(View.INVISIBLE);
		}

		headerHolder.linkEnableSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
			GroupDetailAdapter.this.setGroupLinkViewsEnabled(isChecked);
			if (isChecked) {
				try {
					GroupDetailAdapter.this.defaultGroupInviteModel = groupInviteService.createOrEnableDefaultLink(groupModel);
					encodeAndDisplayDefaultLink();
					headerHolder.linkResetButton.setVisibility(View.VISIBLE);
					headerHolder.linkShareButton.setVisibility(View.VISIBLE);
				} catch (GroupInviteToken.InvalidGroupInviteTokenException | IOException | GroupInviteModel.MissingRequiredArgumentsException e) {
					logger.error("Exception, failed to create or get default group link", e);
				}
			}
			else {
				groupInviteService.deleteDefaultLink(groupModel);
				GroupDetailAdapter.this.defaultGroupInviteModel = null;
			}
		});
		headerHolder.linkShareButton.setOnClickListener(v -> onClickListener.onShareLinkClick());
		headerHolder.linkResetButton.setOnClickListener(v -> {
			if (!warningShown && !ShowOnceDialog.shouldNotShowAnymore(GroupDetailActivity.DIALOG_SHOW_ONCE_RESET_LINK_INFO)) {
				// show only once dialog
				onClickListener.onResetLinkClick();
				warningShown = true;
				return;
			}
			try {
				this.defaultGroupInviteModel = groupInviteService.resetDefaultGroupInvite(groupModel);
				encodeAndDisplayDefaultLink();
			} catch (IOException | GroupInviteToken.InvalidGroupInviteTokenException | GroupInviteModel.MissingRequiredArgumentsException e) {
				logger.error("Exception, failed to reset default group link", e);
			}
		});

		headerHolder.groupLinkTitle.setText(context.getString(R.string.default_group_link) +
			" (" + groupInviteService.getCustomLinksCount(groupModel.getApiGroupId()) + " " + context.getString(R.string.custom) + ")" );
	}

	private void encodeAndDisplayDefaultLink() {
		headerHolder.linkString.setText(
			groupInviteService.encodeGroupInviteLink(GroupDetailAdapter.this.defaultGroupInviteModel).toString()
		);
	}

	private void setGroupLinkViewsEnabled(boolean enabled) {
		headerHolder.linkContainerView.setEnabled(enabled);
		headerHolder.linkResetButton.setEnabled(enabled);
		headerHolder.linkShareButton.setEnabled(enabled);
		if (enabled) {
			headerHolder.linkString.setAlpha(1F);
			headerHolder.linkResetButton.setAlpha(1F);
			headerHolder.linkShareButton.setAlpha(1F);
		}
		else {
			headerHolder.linkString.setAlpha(0.5F);
			headerHolder.linkResetButton.setAlpha(0.5F);
			headerHolder.linkShareButton.setAlpha(0.5F);
		}
	}

	@Override
	public int getItemCount() {
		if (contactModels != null)
			return contactModels.size() + 1;
		else return 1;
	}

	@Override
	public int getItemViewType(int position) {
		if (isPositionHeader(position))
			return TYPE_HEADER;

		return TYPE_ITEM;
	}

	private boolean isPositionHeader(int position) {
		return position == 0;
	}

	public int getPosition(ContactModel contactModel) {
		return contactModels.indexOf(contactModel) + 1;
	}

	public ContactModel getItem(int position) {
		return contactModels.get(position - 1);
	}

	public void setOnClickListener(OnGroupDetailsClickListener listener) {
		onClickListener = listener;
	}

	public interface OnGroupDetailsClickListener {
		void onGroupOwnerClick(View v, String identity);
		void onGroupMemberClick(View v, @NonNull ContactModel contactModel);
		void onResetLinkClick();
		void onShareLinkClick();
	}
}

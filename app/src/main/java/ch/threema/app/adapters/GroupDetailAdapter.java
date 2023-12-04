/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2023 Threema GmbH
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

import static ch.threema.app.adapters.GroupDetailAdapter.GroupDescState.COLLAPSED;
import static ch.threema.app.adapters.GroupDetailAdapter.GroupDescState.EXPANDED;
import static ch.threema.app.adapters.GroupDetailAdapter.GroupDescState.NONE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.text.Layout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.materialswitch.MaterialSwitch;

import org.slf4j.Logger;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.activities.GroupDetailActivity;
import ch.threema.app.dialogs.ShowOnceDialog;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.group.GroupInviteService;
import ch.threema.app.ui.AvatarView;
import ch.threema.app.ui.GroupDetailViewModel;
import ch.threema.app.ui.SectionHeaderView;
import ch.threema.app.utils.AdapterUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.LinkifyUtil;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.csp.messages.group.GroupInviteToken;
import ch.threema.localcrypto.MasterKeyLockedException;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.group.GroupInviteModel;
import java8.util.Optional;

public class GroupDetailAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
	public enum GroupDescState {NONE, COLLAPSED, EXPANDED}
	private static final Logger logger = LoggingUtil.getThreemaLogger("GroupDetailAdapter");

	private static final int TYPE_HEADER = 0;
	private static final int TYPE_ITEM = 1;

	private boolean isGroupEditable = false;

	private final Context context;
	private final ContactService contactService;
	private final GroupService groupService;
	private final GroupInviteService groupInviteService;
	private final GroupModel groupModel;
	private GroupInviteModel defaultGroupInviteModel;
	private final @Nullable Runnable onCloneGroupRunnable;
	private List<ContactModel> contactModels; // Cached copy of group members
	private OnGroupDetailsClickListener onClickListener;
	private final GroupDetailViewModel groupDetailViewModel;
	HeaderHolder headerHolder;
	private boolean warningShown = false;

	public static class ItemHolder extends RecyclerView.ViewHolder {
		public final View view;
		public final TextView nameView, idView;
		public final AvatarView avatarView;
		public final Chip adminChip;

		public ItemHolder(View view) {
			super(view);
			this.view = view;
			this.nameView = itemView.findViewById(R.id.group_name);
			this.avatarView = itemView.findViewById(R.id.avatar_view);
			this.idView = itemView.findViewById(R.id.threemaid);
			this.adminChip = itemView.findViewById(R.id.admin_chip);
		}
	}

	public class HeaderHolder extends RecyclerView.ViewHolder {
		private final SectionHeaderView groupMembersTitleView;
		private final View addMembersView;
		private final ConstraintLayout linkContainerView;
		private final SectionHeaderView groupLinkTitle;
		private final MaterialSwitch linkEnableSwitch;
		private final TextView linkString;
		private final AppCompatImageButton linkResetButton;
		private final AppCompatImageButton linkShareButton;
		public final ImageView changeGroupDescButton;
		public final SectionHeaderView groupDescTitle;
		private final TextView expandButton;
		public final TextView groupDescText;
		public final SectionHeaderView groupDescChangedDate;
		public final View groupNoticeView;
		public final TextView groupNoticeTextView;
		public final MaterialButton groupNoticeCloneButton;

		public HeaderHolder(View view) {
			super(view);

			// items in object
			this.groupMembersTitleView = itemView.findViewById(R.id.group_members_title);
			this.addMembersView = itemView.findViewById(R.id.add_member);
			this.linkContainerView = itemView.findViewById(R.id.group_link_container);
			this.groupLinkTitle = itemView.findViewById(R.id.group_link_header);
			this.linkEnableSwitch = itemView.findViewById(R.id.group_link_switch);
			this.linkString = itemView.findViewById(R.id.group_link_string);
			this.linkResetButton = itemView.findViewById(R.id.reset_button);
			this.linkShareButton = itemView.findViewById(R.id.share_button);
			this.changeGroupDescButton = itemView.findViewById(R.id.change_group_desc_btn);
			this.groupDescTitle = itemView.findViewById(R.id.group_desc_title);
			this.groupDescText = itemView.findViewById(R.id.group_desc_text);
			this.groupDescText.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
				@Override
				public void onLayoutChange(View view, int i, int i1, int i2, int i3, int i4, int i5, int i6, int i7) {
					if (groupDetailViewModel.getGroupDescState() == NONE || !checkIfTextFitsInCollapsedView()) {
						expandButton.setVisibility(View.VISIBLE);
					} else {
						expandButton.setVisibility(View.GONE);
					}
				}
			});
			this.groupNoticeView = itemView.findViewById(R.id.group_notice_view);
			this.groupNoticeTextView = itemView.findViewById(R.id.group_notice);
			this.groupNoticeCloneButton = itemView.findViewById(R.id.clone_button);

			boolean isOrphanedGroup = groupService.isOrphanedGroup(groupModel);
			boolean isCreator = groupService.isGroupCreator(groupModel);
			boolean isMember = groupService.isGroupMember(groupModel);
			boolean hasOtherMembers = groupService.getOtherMemberCount(groupModel) > 0;

			if (isOrphanedGroup) {
				// Show orphaned group notice
				this.groupNoticeView.setVisibility(View.VISIBLE);
				this.groupNoticeTextView.setText(R.string.group_orphaned_notice);

				// If we have a clone runnable and other members in the group, we also show the
				// clone button.
				if (hasOtherMembers && onCloneGroupRunnable != null) {
					this.groupNoticeCloneButton.setOnClickListener(v -> onCloneGroupRunnable.run());
				} else {
					this.groupNoticeCloneButton.setVisibility(View.GONE);
				}
			} else if (!isCreator && !isMember) {
				// Show empty group notice without the clone button
				this.groupNoticeView.setVisibility(View.VISIBLE);
				this.groupNoticeTextView.setText(R.string.group_not_a_member_notice);
				this.groupNoticeCloneButton.setVisibility(View.GONE);
			} else if (isCreator && !isMember) {
				// Show notice that this group has been dissolved
				this.groupNoticeView.setVisibility(View.VISIBLE);
				this.groupNoticeTextView.setText(R.string.group_dissolved_notice);
				this.groupNoticeCloneButton.setVisibility(View.GONE);
			} else {
				// Don't show any notice
				this.groupNoticeView.setVisibility(View.GONE);
			}

			this.expandButton = itemView.findViewById(R.id.expand_group_desc_text);
			this.groupDescChangedDate = itemView.findViewById(R.id.group_desc_changed_date);
		}


		private boolean checkIfTextFitsInCollapsedView() {
			Layout layout = headerHolder.groupDescText.getLayout();
			if (layout != null) {
				int lines = layout.getLineCount();
				if (lines > 0) {
					int ellipsisCount = layout.getEllipsisCount(lines - 1);
					return ellipsisCount == 0 && lines <= 3;
				}
			}
			return true;
		}


	}

	/**
	 * Create the adapter to display the group details.
	 *
	 * @param context              the context
	 * @param groupModel           the group model of the group
	 * @param groupDetailViewModel the group detail view model
	 * @param serviceManager       the service manager
	 * @param onCloneGroupRunnable the runnable that is called when the group is cloned. Note that
	 *                             this runnable should be set for orphaned groups as it is needed
	 *                             to display the clone button. For non-orphaned groups this has no
	 *                             effect and is not needed.
	 * @throws MasterKeyLockedException      when the master key is locked
	 * @throws FileSystemNotPresentException when the file system is not present
	 */
	public GroupDetailAdapter(
		Context context,
		GroupModel groupModel,
		GroupDetailViewModel groupDetailViewModel,
		@NonNull ServiceManager serviceManager,
		@Nullable Runnable onCloneGroupRunnable
	) throws MasterKeyLockedException, FileSystemNotPresentException {
		this.context = context;
		this.groupModel = groupModel;
		this.groupDetailViewModel = groupDetailViewModel;
		this.onCloneGroupRunnable = onCloneGroupRunnable;
		this.contactService = serviceManager.getContactService();
		this.groupService = serviceManager.getGroupService();
		this.groupInviteService = serviceManager.getGroupInviteService();
	}

	@SuppressLint("NotifyDataSetChanged")
	public void setContactModels(List<ContactModel> newContactModels) {
		// Get the contact models that should be displayed. Note that in groups where the user is
		// the creator but has left the group, the creator should still be shown in the list.
		this.contactModels = getContactModelsFromMembers(newContactModels);
		notifyDataSetChanged();
	}

	@NonNull
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
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
	public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, final int position) {
		if (holder instanceof ItemHolder) {
			ItemHolder itemHolder = (ItemHolder) holder;
			final ContactModel contactModel = getItem(position);
			Bitmap avatar = this.contactService.getAvatar(contactModel, false);

			itemHolder.nameView.setText(NameUtil.getDisplayNameOrNickname(contactModel, true));
			itemHolder.idView.setText(contactModel.getIdentity());
			AdapterUtil.styleContact(itemHolder.nameView, contactModel);
			itemHolder.avatarView.setImageBitmap(avatar);
			itemHolder.avatarView.setBadgeVisible(contactService.showBadge(contactModel));
			itemHolder.view.setOnClickListener(v -> onClickListener.onGroupMemberClick(v, contactModel));

			boolean isAdmin = contactModel.getIdentity().equals(groupModel.getCreatorIdentity());
			itemHolder.adminChip.setVisibility(isAdmin ? View.VISIBLE: View.GONE);
			itemHolder.idView.setVisibility(isAdmin ? View.GONE : View.VISIBLE);
		} else {
			this.headerHolder = (HeaderHolder) holder;
			headerHolder.addMembersView.setOnClickListener(v -> onClickListener.onAddMembersClick(v));

			ContactModel ownerContactModel = contactService.getByIdentity(groupModel.getCreatorIdentity());

			isGroupEditable = groupService.isGroupCreator(groupModel) && groupService.isGroupMember(groupModel);

			if (ConfigUtils.supportGroupDescription()) {
				initGroupDescriptionSection();
			} else {
				disableGroupDescription();
			}

			if (ownerContactModel != null) {
				if (!ConfigUtils.supportsGroupLinks() || ownerContactModel != contactService.getMe()) {
					headerHolder.linkContainerView.setVisibility(View.GONE);
				}
				else {
					initGroupLinkSection();
				}
			} else {
				headerHolder.linkContainerView.setVisibility(View.GONE);
			}

			boolean addMembersViewVisibility = isGroupEditable
				&& contactModels != null && contactModels.size() < BuildConfig.MAX_GROUP_SIZE;

			if (contactModels != null && !contactModels.isEmpty()) {
				headerHolder.groupMembersTitleView.setText(ConfigUtils.getSafeQuantityString(context, R.plurals.number_of_group_members, contactModels.size(), contactModels.size()));
			} else {
				headerHolder.groupMembersTitleView.setVisibility(View.GONE);
			}

			headerHolder.addMembersView.setVisibility(addMembersViewVisibility ? View.VISIBLE : View.GONE);
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
		} else {
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
			} else {
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


	private void initGroupDescriptionSection() {
		updateGroupDescriptionLayout();

		headerHolder.expandButton.setOnClickListener(view -> {
			switch (groupDetailViewModel.getGroupDescState()) {
				case NONE:
					onClickListener.onGroupDescriptionEditClick();
					break;
				case EXPANDED:
					groupDetailViewModel.setGroupDescState(COLLAPSED);
					showCollapsedGroupDescription();
					break;
				case COLLAPSED:
					groupDetailViewModel.setGroupDescState(EXPANDED);
					showExpandedGroupDescription();
					break;
			}
		});

		headerHolder.changeGroupDescButton.setOnClickListener(s -> onClickListener.onGroupDescriptionEditClick());
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

	/**
	 * Updates the layout based on the group description data of the view model
	 */
	public void updateGroupDescriptionLayout() {
		switch (groupDetailViewModel.getGroupDescState()) {
			case NONE:
				showNoGroupDescription();
				break;
			case COLLAPSED:
				showCollapsedGroupDescription();
				showGroupDescTimestamp();
				break;
			case EXPANDED:
				showExpandedGroupDescription();
				showGroupDescTimestamp();
				break;
		}
	}

	@NonNull
	private List<ContactModel> getContactModelsFromMembers(@NonNull List<ContactModel> members) {
		boolean containsCreator = false;
		for (ContactModel member : members) {
			if (groupModel.getCreatorIdentity().equals(member.getIdentity())) {
				containsCreator = true;
				break;
			}
		}
		ContactModel me = contactService.getMe();

		// Show me in group details if I am the creator and the group is left. Only show me, when
		// the group is not empty.
		if (!containsCreator
				&& me.getIdentity().equals(groupModel.getCreatorIdentity())
				&& groupService.countMembers(groupModel) > 0
		) {
			List<ContactModel> creatorWithMembers = new LinkedList<>(members);
			creatorWithMembers.add(0, me);
			return creatorWithMembers;
		}
		return members;
	}

	/**
	 * Display the group desc timestamp
	 */
	private void showGroupDescTimestamp() {
		headerHolder.groupDescChangedDate.setText(context.getString(R.string.changed_group_desc_date)
			+ LocaleUtil.formatTimeStampString(context, groupDetailViewModel.getGroupDescTimestamp().getTime(), false));
		headerHolder.groupDescChangedDate.setVisibility(View.VISIBLE);
	}

	/**
	 * Shows the collapsed group description
	 */
	private void showCollapsedGroupDescription() {
		showGroupDescription();
		headerHolder.groupDescText.setMaxLines(3);
		headerHolder.expandButton.setText(R.string.read_more);
	}

	/**
	 * Shows the expanded group description
	 */
	private void showExpandedGroupDescription() {
		showGroupDescription();
		headerHolder.expandButton.setText(R.string.read_less);
		headerHolder.groupDescText.setMaxLines(Integer.MAX_VALUE);
	}

	/**
	 * Make the group description elements visible and hide
	 */
	private void showGroupDescription() {
		headerHolder.groupDescTitle.setVisibility(View.VISIBLE);
		headerHolder.expandButton.setVisibility(View.VISIBLE);
		headerHolder.groupDescText.setVisibility(View.VISIBLE);
		headerHolder.groupDescText.setText(groupDetailViewModel.getGroupDesc());
		LinkifyUtil.getInstance().linkifyText(headerHolder.groupDescText, true);
		if (isGroupEditable) {
			headerHolder.changeGroupDescButton.setVisibility(View.VISIBLE);
		} else {
			headerHolder.changeGroupDescButton.setVisibility(View.GONE);
		}
	}

	/**
	 * Hide the group description ui elements and shows a button to add a group description
	 */
	private void showNoGroupDescription() {
		groupDetailViewModel.setGroupDescState(NONE);
		headerHolder.groupDescTitle.setVisibility(View.GONE);
		headerHolder.groupDescText.setVisibility(View.GONE);
		headerHolder.groupDescChangedDate.setVisibility(View.GONE);
		headerHolder.changeGroupDescButton.setVisibility(View.GONE);
		headerHolder.expandButton.setText(R.string.add_group_description);
		if (isGroupEditable) {
			headerHolder.expandButton.setVisibility(View.VISIBLE);
		} else {
			headerHolder.expandButton.setVisibility(View.GONE);
		}
	}

	/**
	 * Hides all the group description related ui elements
	 */
	private void disableGroupDescription() {
		headerHolder.groupDescTitle.setVisibility(View.GONE);
		headerHolder.groupDescText.setVisibility(View.GONE);
		headerHolder.groupDescChangedDate.setVisibility(View.GONE);
		headerHolder.changeGroupDescButton.setVisibility(View.GONE);
		headerHolder.expandButton.setVisibility(View.GONE);
	}

	public interface OnGroupDetailsClickListener {
		void onGroupOwnerClick(View v, String identity);
		void onGroupMemberClick(View v, @NonNull ContactModel contactModel);
		void onResetLinkClick();
		void onShareLinkClick();
		void onGroupDescriptionEditClick();
		void onAddMembersClick(View v);
	}
}

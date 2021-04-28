/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2021 Threema GmbH
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
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import androidx.recyclerview.widget.RecyclerView;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.FingerPrintService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.IdListService;
import ch.threema.app.ui.VerificationLevelImageView;
import ch.threema.app.utils.AndroidContactUtil;
import ch.threema.app.utils.ContactUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupModel;

public class ContactDetailAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
	private static final Logger logger = LoggerFactory.getLogger(ContactDetailAdapter.class);

	private static final int TYPE_HEADER = 0;
	private static final int TYPE_ITEM = 1;

	private Context context;
	private GroupService groupService;
	private FingerPrintService fingerprintService;
	private IdListService excludeFromSyncListService;
	private ContactService contactService;
	private IdListService blackListIdentityService;
	private ContactModel contactModel;
	private List<GroupModel> values;
	private OnClickListener onClickListener;

	public static class ItemHolder extends RecyclerView.ViewHolder {
		public final View view;
		public final TextView nameView;
		public final ImageView avatarView, statusView;

		public ItemHolder(View view) {
			super(view);
			this.view = view;
			this.nameView = itemView.findViewById(R.id.contact_name);
			this.avatarView = itemView.findViewById(R.id.contact_avatar);
			this.statusView = itemView.findViewById(R.id.status);
		}
	}

	public class HeaderHolder extends RecyclerView.ViewHolder {
		private final VerificationLevelImageView verificationLevelImageView;
		private final TextView threemaIdView, fingerprintView;
		private final ImageView linkedContactAvatar, verificationLevelIconView;
		private final TextView linkedContactName;
		private final ImageView linkedContactTypeIcon;
		private final CheckBox synchronize;
		private final View nicknameContainer;
		private final TextView publicNickNameView;
		private final LinearLayout groupMembershipTitle;
		private final RelativeLayout linkedContactContainer;

		public HeaderHolder(View view) {
			super(view);

			// items in object
			this.threemaIdView = itemView.findViewById(R.id.threema_id);
			this.fingerprintView = itemView.findViewById(R.id.key_fingerprint);
			this.verificationLevelImageView = itemView.findViewById(R.id.verification_level_image);
			this.verificationLevelIconView = itemView.findViewById(R.id.verification_information_icon);
			this.linkedContactContainer = itemView.findViewById(R.id.linked_contact_container);
			this.linkedContactAvatar = itemView.findViewById(R.id.linked_contact);
			this.linkedContactName = itemView.findViewById(R.id.linked_name);
			this.linkedContactTypeIcon = itemView.findViewById(R.id.linked_type_icon);
			this.synchronize = itemView.findViewById(R.id.synchronize_contact);
			this.nicknameContainer = itemView.findViewById(R.id.nickname_container);
			this.publicNickNameView = itemView.findViewById(R.id.public_nickname);
			this.groupMembershipTitle = itemView.findViewById(R.id.group_members_title_container);

			this.linkedContactContainer.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (onClickListener != null) {
						onClickListener.onLinkedContactClick(v);
					}
				}
			});

			this.verificationLevelIconView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (onClickListener != null) {
						onClickListener.onVerificationInfoClick(v);
					}
				}
			});
		}

	}

	public ContactDetailAdapter(Context context, List<GroupModel> values, ContactModel contactModel) {
		this.context = context;
		this.values = values;
		this.contactModel = contactModel;
		ServiceManager serviceManager = ThreemaApplication.getServiceManager();

		try {
			this.groupService = serviceManager.getGroupService();
			this.fingerprintService = serviceManager.getFingerPrintService();
			this.contactService = serviceManager.getContactService();
			this.excludeFromSyncListService = serviceManager.getExcludedSyncIdentitiesService();
			this.blackListIdentityService = serviceManager.getBlackListService();
		} catch (Exception e) {
			logger.error("Exception", e);
		}
	}

	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		if (viewType == TYPE_ITEM) {
			View v = LayoutInflater.from(parent.getContext())
					.inflate(R.layout.item_contact_detail, parent, false);

			return new ItemHolder(v);
		} else if (viewType == TYPE_HEADER) {
			View v = LayoutInflater.from(parent.getContext())
					.inflate(R.layout.header_contact_detail, parent, false);

			return new HeaderHolder(v);
		}
		throw new RuntimeException("no matching item type");
	}

	@Override
	public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
		if (holder instanceof ItemHolder) {
			ItemHolder itemHolder = (ItemHolder) holder;
			final GroupModel groupModel = getItem(position);
			Bitmap avatar = this.groupService.getAvatar(groupModel, false);

			itemHolder.nameView.setText(groupModel.getName());
			itemHolder.avatarView.setImageBitmap(avatar);
			if (groupService.isGroupOwner(groupModel)) {
				itemHolder.statusView.setImageResource(R.drawable.ic_group_outline);
			} else {
				itemHolder.statusView.setImageResource(R.drawable.ic_group_filled);
			}
			itemHolder.view.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					onClickListener.onItemClick(v, groupModel);
				}
			});
		} else {
			HeaderHolder headerHolder = (HeaderHolder) holder;

			String identityAdditional = null;
			if(this.contactModel.getState() != null) {
				switch (this.contactModel.getState()) {
					case TEMPORARY:
					case ACTIVE:
						if (blackListIdentityService.has(contactModel.getIdentity())) {
							identityAdditional = context.getString(R.string.blocked);
						}
						break;
					case INACTIVE:
						identityAdditional = context.getString(R.string.contact_state_inactive);
						break;
					case INVALID:
						identityAdditional = context.getString(R.string.contact_state_invalid);
						break;
				}
			}
			headerHolder.threemaIdView.setText(contactModel.getIdentity() + (identityAdditional != null ? " (" + identityAdditional + ")" : ""));
			headerHolder.fingerprintView.setText(this.fingerprintService.getFingerPrint(contactModel.getIdentity()));
			headerHolder.verificationLevelImageView.setContactModel(contactModel);
			headerHolder.verificationLevelImageView.setVisibility(View.VISIBLE);

			if (ContactUtil.isSynchronized(contactModel)) {
				headerHolder.synchronize.setVisibility(View.VISIBLE);
				headerHolder.synchronize.setChecked(excludeFromSyncListService.has(contactModel.getIdentity()));
				headerHolder.synchronize.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
					@Override
					public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
						if (isChecked) {
							excludeFromSyncListService.add(contactModel.getIdentity());
						} else {
							excludeFromSyncListService.remove(contactModel.getIdentity());
						}
					}
				});
			} else {
				headerHolder.synchronize.setVisibility(View.GONE);
			}

			String nicknameString = contactModel.getPublicNickName();
			if (nicknameString != null && nicknameString.length() > 0) {
				headerHolder.publicNickNameView.setText(contactModel.getPublicNickName());
			} else {
				headerHolder.nicknameContainer.setVisibility(View.GONE);
			}

			Bitmap avatar = null;
			if (ContactUtil.isLinked(contactModel)) {
				avatar = contactService.getAvatar(contactModel, false);

				headerHolder.linkedContactName.setText(NameUtil.getDisplayName(contactModel));

				if (headerHolder.linkedContactTypeIcon != null) {
					Drawable icon = AndroidContactUtil.getInstance().getAccountIcon(contactModel.getIdentity());
					if (icon != null) {
						headerHolder.linkedContactTypeIcon.setImageDrawable(icon);
						headerHolder.linkedContactTypeIcon.setVisibility(View.VISIBLE);
					} else {
						headerHolder.linkedContactTypeIcon.setVisibility(View.GONE);
					}
				}
			} else {
				headerHolder.linkedContactName.setText(R.string.touch_to_link);
				if (headerHolder.linkedContactTypeIcon != null) {
					headerHolder.linkedContactTypeIcon.setVisibility(View.GONE);
				}
			}
			if (avatar != null) {
				headerHolder.linkedContactAvatar.setImageBitmap(avatar);
			} else {
				headerHolder.linkedContactAvatar.setImageResource(R.drawable.ic_contact);
			}

			if (values.size() > 0) {
				headerHolder.groupMembershipTitle.setVisibility(View.VISIBLE);
			} else {
				headerHolder.groupMembershipTitle.setVisibility(View.GONE);
			}
		}
	}

	@Override
	public int getItemCount() {
		return values.size() + 1;
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

	private GroupModel getItem(int position) {
		return values.get(position - 1);
	}

	public void setOnClickListener(OnClickListener listener) {
		onClickListener = listener;
	}

	public interface OnClickListener {
		void onLinkedContactClick(View v);
		void onItemClick(View v, GroupModel groupModel);
		void onVerificationInfoClick(View v);
	}

}

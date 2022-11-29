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

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import net.sqlcipher.SQLException;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.view.ActionMode;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.ThreemaToolbarActivity;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.SelectorDialog;
import ch.threema.app.dialogs.TextEntryDialog;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.group.GroupInviteService;
import ch.threema.app.ui.EmptyRecyclerView;
import ch.threema.app.ui.EmptyView;
import ch.threema.app.ui.SelectorDialogItem;
import ch.threema.app.ui.ViewModelFactory;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.LogUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.factories.GroupInviteModelFactory;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.group.GroupInviteModel;

public class GroupLinkOverviewActivity extends ThreemaToolbarActivity implements
	GenericAlertDialog.DialogClickListener,
	SelectorDialog.SelectorDialogClickListener,
	TextEntryDialog.TextEntryDialogClickListener {
	private static final Logger logger = LoggingUtil.getThreemaLogger("GroupLinkOverviewActivity");

	private static final String DIALOG_TAG_REALLY_DELETE_INVITE = "delete_invite";
	private static final String DIALOG_TAG_ITEM_MENU = "itemMenu";
	private static final String DIALOG_TAG_EDIT_LABEL = "editLabel";
	private static final String DIALOG_TAG_EDIT_EXPIRATION_DATE = "editDate";

	private static final int MENU_POS_RENAME = 0;
	private static final int MENU_POS_ADJUST_EXPIRATION = 1;
	private static final int MENU_POS_QR_CODE = 2;
	private static final int MENU_POS_SHARE = 3;
	private static final int MENU_POS_DELETE = 4;

	GroupInviteService groupInviteService;
	GroupService groupService;
	GroupInviteModelFactory groupInviteRepository;

	private ActionMode actionMode = null;
	private GroupLinkAdapter groupLinkAdapter;
	private GroupLinkViewModel viewModel;

	private ExtendedFloatingActionButton floatingButtonView;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	protected boolean initActivity(Bundle savedInstanceState) {
		if (!super.initActivity(savedInstanceState)) {
			return false;
		}

		Intent intent = getIntent();
		int groupId = intent.getIntExtra(ThreemaApplication.INTENT_DATA_GROUP, 0);
		if (groupId == 0) {
			logger.error("No group id received to display group links for");
			finish();
			return false;
		}

		initLayout(groupId);
		initListeners(groupId);
		return true;
	}

	@Override
	protected void initServices() {
		super.initServices();
		try {
			this.groupInviteService = serviceManager.getGroupInviteService();
			this.groupService = serviceManager.getGroupService();
			this.groupInviteRepository = serviceManager.getDatabaseServiceNew().getGroupInviteModelFactory();
		} catch (Exception e) {
			logger.error("Exception, required services not available... finishing", e);
			finish();
		}
	}

	private void initLayout(int groupId) {
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
			GroupModel groupModel = groupService.getById(groupId);
			if (groupModel == null) {
				logger.error("Exception: could not get group model by id, finishing...");
				finish();
				return;
			}
			viewModel = new ViewModelProvider(this,
				new ViewModelFactory(groupModel.getApiGroupId()))
				.get(GroupLinkViewModel.class);

			try {
				this.groupLinkAdapter = new GroupLinkAdapter(this, viewModel);
			} catch (ThreemaException e) {
				logger.error("Exception could not create GroupLinkAdapter... finishing", e);
				finish();
				return;
			}
			actionBar.setTitle(String.format(getString(R.string.group_links_overview_title), NameUtil.getDisplayName(groupModel, groupService)));
		}

		this.floatingButtonView = findViewById(R.id.floating);
		floatingButtonView.setVisibility(View.VISIBLE);
		floatingButtonView.setText(R.string.group_link_add);
		floatingButtonView.setContentDescription(getText(R.string.group_link_add));

		EmptyView emptyView = new EmptyView(this, ConfigUtils.getActionBarSize(this));
		emptyView.setup(R.string.no_group_links);

		EmptyRecyclerView recyclerView = this.findViewById(R.id.recycler);
		recyclerView.setHasFixedSize(true);
		recyclerView.setLayoutManager(new LinearLayoutManager(this));
		recyclerView.setItemAnimator(new DefaultItemAnimator());
		((ViewGroup) recyclerView.getParent().getParent()).addView(emptyView);
		recyclerView.setEmptyView(emptyView);
		recyclerView.setAdapter(this.groupLinkAdapter);
	}

	private void initListeners(int groupId) {
		this.floatingButtonView.setOnClickListener(v -> {
			Intent intent = new Intent(GroupLinkOverviewActivity.this, AddGroupLinkBottomSheet.class);
			intent.putExtra(ThreemaApplication.INTENT_DATA_GROUP, groupId);
			startActivityForResult(intent, 2);
		});

		this.groupLinkAdapter.setOnClickItemListener(new GroupLinkAdapter.OnClickItemListener() {
			@Override
			public void onClick(GroupInviteModel groupInviteModel, View view, int position) {
				if (actionMode != null) {
					toggleCheckedItem(position);
				} else {
					ArrayList<SelectorDialogItem> items = new ArrayList<>();
					ArrayList<Integer> values = new ArrayList<>();

					items.add(new SelectorDialogItem(getString(R.string.group_link_share),R.drawable.ic_share_outline));
					values.add(MENU_POS_SHARE);

					items.add(new SelectorDialogItem(getString(R.string.group_link_show_qr), R.drawable.ic_qr_code));
					values.add(MENU_POS_QR_CODE);

					// default link cannot be deleted or edited, that's was separate extra links are intended for
					if (!groupInviteModel.isDefault()) {
						items.add(new SelectorDialogItem(getString(R.string.group_link_rename), R.drawable.ic_pencil_outline) );
						values.add(MENU_POS_RENAME);

						items.add(new SelectorDialogItem(getString(R.string.group_link_edit_expiration_date),R.drawable.ic_timelapse_outline));
						values.add(MENU_POS_ADJUST_EXPIRATION);
					}

					items.add(new SelectorDialogItem(getString(R.string.delete),R.drawable.ic_delete_outline));
					values.add(MENU_POS_DELETE);

					SelectorDialog selectorDialog = SelectorDialog.newInstance(null, items, values, null);
					selectorDialog.setData(groupInviteModel);
					selectorDialog.show(getSupportFragmentManager(), DIALOG_TAG_ITEM_MENU);
				}
			}

			@Override
			public boolean onLongClick(int position) {
				return GroupLinkOverviewActivity.this.onLongClickListItem(position);
			}
		});

		final Observer<List<GroupInviteModel>> groupInvitesObserver = newGroupInvites ->
			groupLinkAdapter.setGroupInviteModels(newGroupInvites);

		viewModel.getGroupInviteModels().observe(this, groupInvitesObserver);
		viewModel.onDataChanged();
	}

	@Override
	public int getLayoutResource() {
		return R.layout.activity_base_recycler_list;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			setResult(RESULT_OK);
			this.finish();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	// start GenericAlertDialog callbacks
	@Override
	public void onYes(String tag, Object data) {
		if (tag.equals(DIALOG_TAG_REALLY_DELETE_INVITE)) {
			reallyDelete((List<GroupInviteModel>) data);
		}
	}

	@Override
	public void onNo(String tag, Object data) {
		// fall through, delete process aborted
	}
	// end GenericAlertDialog callbacks

	// start SelectorDialog callbacks
	@Override
	public void onClick(String tag, int which, Object data) {
		if (DIALOG_TAG_ITEM_MENU.equals(tag) && data instanceof GroupInviteModel) {
			GroupInviteModel groupInviteModel = (GroupInviteModel) data;
			switch (which) {
				case MENU_POS_RENAME:
					this.renameGroupLink(groupInviteModel);
					break;
				case MENU_POS_ADJUST_EXPIRATION:
					this.updateExpirationDate(groupInviteModel);
					break;
				case MENU_POS_QR_CODE:
					this.showQrCode(groupInviteModel);
					break;
				case MENU_POS_SHARE:
					this.shareQrCode(groupInviteModel);
					break;
				case MENU_POS_DELETE:
					this.delete(Collections.singletonList(groupInviteModel));
					break;
				default:
					break;
			}
		}
	}

	@Override
	public void onCancel(String tag) {
		// not used in this case
	}
	// end SelectorDialog callbacks

	// start TextEntryDialog callbacks
	@Override
	public void onYes(String tag, String text) {
		if (tag.startsWith(DIALOG_TAG_EDIT_LABEL)) {
			// The model id is appended to the tag. To get it, strip the prefix.
			int modelId = Integer.parseInt(tag.substring(DIALOG_TAG_EDIT_LABEL.length()));

			//simply search list for this id
			for (GroupInviteModel groupInviteModel : groupLinkAdapter.getAllData()) {
				if (groupInviteModel.getId() == modelId) {
					try {
						viewModel.updateGroupInviteModel(new GroupInviteModel.Builder(groupInviteModel).withInviteName(text).build());
						viewModel.onDataChanged();
						Toast.makeText(getApplicationContext(),
							getString(R.string.group_link_update_success),
							Toast.LENGTH_LONG
						).show();
					} catch (GroupInviteModel.MissingRequiredArgumentsException e) {
						Toast.makeText(getApplicationContext(),
							String.format(getString(R.string.an_error_occurred_more), e),
							Toast.LENGTH_LONG
						).show();
					}
					return;
				}
			}
		}
	}

	@Override
	public void onNo(String tag) {
		// fall through, don't update link name
	}

	@Override
	public void onNeutral(String tag) {
		// not used in this case

	}
	// end TextEntryDialog callbacks

	public class ArchiveAction implements ActionMode.Callback {
		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			logger.debug("onCreateActionMode");
			mode.getMenuInflater().inflate(R.menu.action_group_url, menu);
			ConfigUtils.themeMenu(menu, ConfigUtils.getColorFromAttribute(GroupLinkOverviewActivity.this, R.attr.colorAccent));
			return true;
		}

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			final int checked = viewModel.getCheckedItemsCount();
			if (checked > 0) {
				mode.setTitle(Integer.toString(checked));
				return true;
			}
			return false;
		}

		@SuppressLint("NonConstantResourceId")
		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			switch (item.getItemId()) {
				case R.id.menu_select_all:
					if (viewModel.selectAll()) {
						mode.setTitle(Integer.toString(viewModel.getCheckedItemsCount()));
					}
					else {
						actionMode.finish();
					}
					return true;
				case R.id.menu_delete:
					delete(viewModel.getCheckedItems());
					return true;
				default:
					return false;
			}
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {
			viewModel.clearCheckedItems();
			actionMode = null;
		}
	}

	@Override
	public void onBackPressed() {
		if (actionMode != null) {
			actionMode.finish();
		} else {
			super.onBackPressed();
		}
	}

	private void delete(List<GroupInviteModel> checkedItems) {
		int amountOfDeleteGroupLinks = checkedItems.size();

		final String reallyDeleteGroupLinkTitle = getString(amountOfDeleteGroupLinks > 1 ? R.string.really_delete_multiple_group_links_title : R.string.really_delete_group_link_title);
		String confirmText = ConfigUtils.getSafeQuantityString(this, R.plurals.really_delete_group_link, amountOfDeleteGroupLinks, amountOfDeleteGroupLinks);
		GenericAlertDialog dialog = GenericAlertDialog.newInstance(
			reallyDeleteGroupLinkTitle,
			confirmText,
			R.string.ok,
			R.string.cancel);
		dialog.setData(checkedItems);
		dialog.show(getSupportFragmentManager(), DIALOG_TAG_REALLY_DELETE_INVITE);
	}

	private void reallyDelete(final List<GroupInviteModel> checkedItems) {
		this.viewModel.removeGroupInviteModels(checkedItems);
		// finish action mode if delete was triggered through group select
		if (actionMode !=  null) {
			actionMode.finish();
		}
	}

	private void renameGroupLink(@NonNull final GroupInviteModel groupInviteModel) {
		TextEntryDialog.newInstance(R.string.group_link_rename,
			R.string.group_link_rename_tag,
			R.string.ok,
			R.string.cancel, 2, 64).show(getSupportFragmentManager(), DIALOG_TAG_EDIT_LABEL + groupInviteModel.getId());
	}

	private void updateExpirationDate(@NonNull final GroupInviteModel groupInviteModel) {
		final MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
			.setTitleText(R.string.group_link_edit_expiration_date)
			.setSelection(MaterialDatePicker.todayInUtcMilliseconds())
			.build();
		datePicker.addOnPositiveButtonClickListener((Long selection) -> {
			Long date = datePicker.getSelection();
			if (date != null) {
				try {
					groupInviteRepository.update(new GroupInviteModel.Builder(groupInviteModel)
						.withExpirationDate(new Date(date))
						.build());
					viewModel.onDataChanged();
				} catch (SQLException | GroupInviteModel.MissingRequiredArgumentsException e) {
					LogUtil.error(String.format(getString(R.string.an_error_occurred_more), e.getMessage()), this);
				}
			}
		});
		datePicker.show(getSupportFragmentManager(), DIALOG_TAG_EDIT_EXPIRATION_DATE + groupInviteModel.getId());
	}

	private void showQrCode(@NonNull final GroupInviteModel groupInviteModel) {
		Intent qrIntent = new Intent(this, GroupLinkQrCodeActivity.class);
		IntentDataUtil.append(groupInviteService.encodeGroupInviteLink(groupInviteModel), groupInviteModel.getOriginalGroupName(), qrIntent);
		startActivity(qrIntent);
	}

	private void shareQrCode(GroupInviteModel groupInviteModel) {
		this.groupInviteService.shareGroupLink(
			this,
			groupInviteModel);
	}

	private boolean onLongClickListItem(int position) {
		if (actionMode != null) {
			actionMode.finish();
		}
		viewModel.toggleChecked(position);
		if (viewModel.getCheckedItemsCount() > 0) {
			actionMode = startSupportActionMode(new GroupLinkOverviewActivity.ArchiveAction());
		}
		return true;
	}

	private void toggleCheckedItem(int position) {
		viewModel.toggleChecked(position);
		if (viewModel.getCheckedItemsCount() > 0) {
			if (actionMode != null) {
				actionMode.invalidate();
			}
		} else {
			actionMode.finish();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		// if add new link was aborted before sharing. update list
		if (resultCode == RESULT_CANCELED) {
			viewModel.onDataChanged();
		}
		super.onActivityResult(requestCode, resultCode, data);
	}
}

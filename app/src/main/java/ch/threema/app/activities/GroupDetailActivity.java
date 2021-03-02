/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2021 Threema GmbH
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

package ch.threema.app.activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.adapters.GroupDetailAdapter;
import ch.threema.app.asynctasks.DeleteGroupAsyncTask;
import ch.threema.app.asynctasks.DeleteMyGroupAsyncTask;
import ch.threema.app.asynctasks.LeaveGroupAsyncTask;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.dialogs.SelectorDialog;
import ch.threema.app.dialogs.SimpleStringAlertDialog;
import ch.threema.app.dialogs.TextEntryDialog;
import ch.threema.app.emojis.EmojiEditText;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.listeners.ContactListener;
import ch.threema.app.listeners.ContactSettingsListener;
import ch.threema.app.listeners.GroupListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.services.DeviceService;
import ch.threema.app.services.IdListService;
import ch.threema.app.services.license.LicenseService;
import ch.threema.app.ui.AvatarEditView;
import ch.threema.app.ui.GroupDetailViewModel;
import ch.threema.app.ui.ResumePauseHandler;
import ch.threema.app.utils.AppRestrictionUtil;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ContactUtil;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.LogUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.voip.util.VoipUtil;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupModel;

import static ch.threema.app.dialogs.ContactEditDialog.CONTACT_AVATAR_HEIGHT_PX;

public class GroupDetailActivity extends GroupEditActivity implements SelectorDialog.SelectorDialogClickListener, GenericAlertDialog.DialogClickListener, TextEntryDialog.TextEntryDialogClickListener {
	private static final Logger logger = LoggerFactory.getLogger(GroupDetailActivity.class);

	private final int MODE_EDIT = 1;
	private final int MODE_READONLY = 2;

	private static final String DIALOG_TAG_LEAVE_GROUP = "leaveGroup";
	private static final String DIALOG_TAG_UPDATE_GROUP = "updateGroup";
	private static final String DIALOG_TAG_QUIT = "quit";
	private static final String DIALOG_TAG_CHOOSE_ACTION = "chooseAction";
	private static final String DIALOG_TAG_RESYNC_GROUP = "resyncGroup";
	private static final String DIALOG_TAG_DELETE_GROUP = "delG";
	private static final String DIALOG_TAG_CLONE_GROUP = "cg";
	private static final String DIALOG_TAG_CLONE_GROUP_CONFIRM = "cgc";
	private static final String DIALOG_TAG_CLONING_GROUP = "cgi";
	private static final String RUN_ON_ACTIVE_RELOAD = "reload";

	private static final int SELECTOR_OPTION_CONTACT_DETAIL = 0;
	private static final int SELECTOR_OPTION_CHAT = 1;
	private static final int SELECTOR_OPTION_CALL = 2;
	private static final int SELECTOR_OPTION_REMOVE = 3;

	private int operationMode;
	private int groupId;
	private EmojiEditText groupNameEditText;
	private boolean hasChanges = false;

	private String myIdentity;

	private GroupModel groupModel;
	private RecyclerView groupDetailRecyclerView;
	private GroupDetailAdapter groupDetailAdapter;
	private CollapsingToolbarLayout collapsingToolbar;
	private ResumePauseHandler resumePauseHandler;
	private DeviceService deviceService;
	private IdListService blackListIdentityService;
	private LicenseService licenseService;
	private AvatarEditView avatarEditView;
	private GroupDetailViewModel groupDetailViewModel;
	private ExtendedFloatingActionButton floatingActionButton;

	private final ResumePauseHandler.RunIfActive runIfActiveUpdate = new ResumePauseHandler.RunIfActive() {
		@Override
		public void runOnUiThread() {
			groupDetailViewModel.setGroupName(groupModel.getName());
			setTitle();

			groupDetailViewModel.setGroupIdentities(groupService.getGroupIdentities(groupModel));
			sortGroupMembers();

			setScrimColor();
		}
	};

	private final AvatarEditView.AvatarEditListener avatarEditViewListener = new AvatarEditView.AvatarEditListener() {
		@Override
		public void onAvatarSet(File avatarFile1) {
			groupDetailViewModel.setAvatarFile(avatarFile1);
			groupDetailViewModel.setIsAvatarRemoved(false);
			setScrimColor();
		}

		@Override
		public void onAvatarRemoved() {
			groupDetailViewModel.setAvatarFile(null);
			groupDetailViewModel.setIsAvatarRemoved(true);
			avatarEditView.setDefaultAvatar(null, groupModel);
			setScrimColor();
		}
	};

	private class SelectorInfo {
		public View view;
		public ContactModel contactModel;
		public ArrayList<Integer> optionsMap;
	}

	private ContactSettingsListener contactSettingsListener = new ContactSettingsListener() {
		@Override
		public void onSortingChanged() { }

		@Override
		public void onNameFormatChanged() { }

		@Override
		public void onAvatarSettingChanged() {
			resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD, runIfActiveUpdate);
		}

		@Override
		public void onInactiveContactsSettingChanged() { }

		@Override
		public void onNotificationSettingChanged(String uid) { }
	};

	private ContactListener contactListener = new ContactListener() {
		@Override
		public void onModified(ContactModel modifiedContactModel) {
			resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD, runIfActiveUpdate);
		}

		@Override
		public void onAvatarChanged(ContactModel contactModel) {
			this.onModified(contactModel);
		}

		@Override
		public boolean handle(String identity) {
			return groupDetailViewModel.containsModel(identity);
		}
	};

	private GroupListener groupListener = new GroupListener() {
		@Override
		public void onCreate(GroupModel newGroupModel) {
			resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD, runIfActiveUpdate);
		}

		@Override
		public void onRename(GroupModel groupModel) {
			resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD, runIfActiveUpdate);
		}

		@Override
		public void onUpdatePhoto(GroupModel groupModel) {
			resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD, runIfActiveUpdate);
		}

		@Override
		public void onRemove(GroupModel groupModel) {
			resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD, runIfActiveUpdate);
		}

		@Override
		public void onNewMember(GroupModel group, String newIdentity) {
			resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD, runIfActiveUpdate);
		}

		@Override
		public void onMemberLeave(GroupModel group, String identity) {
			if (identity.equals(myIdentity)) {
				finishUp();
			} else {
				resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD, runIfActiveUpdate);
			}
		}

		@Override
		public void onMemberKicked(GroupModel group, String identity) {
			if (identity.equals(myIdentity)) {
				finishUp();
			} else {
				resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD, runIfActiveUpdate);
			}
		}

		@Override
		public void onUpdate(GroupModel groupModel) {
			//ignore
		}

		@Override
		public void onLeave(GroupModel groupModel) {
			// ignore
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.myIdentity = userService.getIdentity();

		final ActionBar actionBar = getSupportActionBar();
		if (actionBar == null) {
			finishUp();
			return;
		}

		ConfigUtils.configureTransparentStatusBar(this);

		this.resumePauseHandler = ResumePauseHandler.getByActivity(this, this);
		this.groupDetailViewModel = new ViewModelProvider(this).get(GroupDetailViewModel.class);

		final Toolbar toolbar = findViewById(R.id.toolbar);
		LinearLayout doneButton = toolbar.findViewById(R.id.action_done);
		this.avatarEditView = findViewById(R.id.avatar_edit_view);
		this.collapsingToolbar = findViewById(R.id.collapsing_toolbar);
		this.floatingActionButton = findViewById(R.id.floating);
		this.groupDetailRecyclerView = findViewById(R.id.group_members_list);
		this.collapsingToolbar.setTitle(" ");
		this.groupNameEditText = findViewById(R.id.group_title);

		try {
			this.deviceService = serviceManager.getDeviceService();
			this.blackListIdentityService = serviceManager.getBlackListService();
			this.licenseService = serviceManager.getLicenseService();
		} catch (FileSystemNotPresentException e) {
			logger.error("Exception", e);
			finishUp();
			return;
		}

		if (this.deviceService == null || this.blackListIdentityService == null || this.licenseService == null) {
			finishUp();
			return;
		}

		groupId = getIntent().getIntExtra(ThreemaApplication.INTENT_DATA_GROUP, 0);
		if (this.groupId == 0) {
			finishUp();
		}
		this.groupModel = groupService.getById(this.groupId);

		if (savedInstanceState == null) {
			// new instance
			this.groupDetailViewModel.setGroupContacts(this.contactService.getByIdentities(groupService.getGroupIdentities(this.groupModel)));
			this.groupDetailViewModel.setGroupName(this.groupModel.getName());
		}

		this.avatarEditView.setHires(true);
		if (groupDetailViewModel.getIsAvatarRemoved()) {
			this.avatarEditView.setDefaultAvatar(null, groupModel);
		} else {
			if (groupDetailViewModel.getAvatarFile() != null) {
				this.avatarEditView.setAvatarFile(groupDetailViewModel.getAvatarFile());
			} else {
				this.avatarEditView.loadAvatarForModel(null, groupModel);
			}
		}
		this.avatarEditView.setListener(this.avatarEditViewListener);

		((AppBarLayout) findViewById(R.id.appbar)).addOnOffsetChangedListener(new AppBarLayout.OnOffsetChangedListener() {
			@Override
			public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
				logger.debug("Vertical offset: " + verticalOffset);
				if (verticalOffset == 0) {
					if (!floatingActionButton.isExtended()) {
						floatingActionButton.extend();
					}
				}
				else {
					if (floatingActionButton.isExtended()) {
						floatingActionButton.shrink();
					}
				}
			}
		});

		this.sortGroupMembers();
		setTitle();

		if (this.groupService.isGroupOwner(this.groupModel)) {
			operationMode = MODE_EDIT;
			doneButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					saveGroupSettings();
				}
			});
			floatingActionButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (groupService != null && groupService.isGroupOwner(groupModel)) {
						Intent intent = new Intent(GroupDetailActivity.this, GroupAddActivity.class);
						IntentDataUtil.append(groupModel, intent);
						IntentDataUtil.append(groupDetailViewModel.getGroupContacts(), intent);
						startActivityForResult(intent, ThreemaActivity.ACTIVITY_ID_GROUP_ADD);
					}
				}
			});
			groupNameEditText.setMaxByteSize(GroupModel.GROUP_NAME_MAX_LENGTH_BYTES);
		} else {
			operationMode = MODE_READONLY;
			doneButton.setVisibility(View.GONE);

			groupNameEditText.setFocusable(false);
			groupNameEditText.setClickable(false);
			groupNameEditText.setFocusableInTouchMode(false);
			groupNameEditText.setBackground(null);

			floatingActionButton.hide();
			actionBar.setDisplayHomeAsUpEnabled(true);
		}

		this.groupDetailRecyclerView.setLayoutManager(new LinearLayoutManager(this));

		setupAdapter();

		this.groupDetailRecyclerView.setAdapter(this.groupDetailAdapter);

		final Observer<List<ContactModel>> groupMemberObserver = new Observer<List<ContactModel>>() {
			@Override
			public void onChanged(List<ContactModel> groupMembers) {
				// Update the UI
				groupDetailAdapter.setContactModels(groupMembers);
			}
		};

		// Observe the LiveData, passing in this activity as the LifecycleOwner and the observer.
		groupDetailViewModel.getGroupMembers().observe(this, groupMemberObserver);
		groupDetailViewModel.onDataChanged();

		setScrimColor();
		updateFloatingActionButton();

		if (toolbar.getNavigationIcon() != null) {
			toolbar.getNavigationIcon().setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
		}

		ListenerManager.contactSettingsListeners.add(this.contactSettingsListener);
		ListenerManager.groupListeners.add(this.groupListener);
		ListenerManager.contactListeners.add(this.contactListener);
	}

	private void setScrimColor() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				@ColorInt int color = getResources().getColor(R.color.material_grey_600);
				if (groupModel != null) {
					Bitmap bitmap;

					if (groupDetailViewModel.getAvatarFile() != null) {
						bitmap = BitmapUtil.safeGetBitmapFromUri(GroupDetailActivity.this,
							Uri.fromFile(groupDetailViewModel.getAvatarFile()), CONTACT_AVATAR_HEIGHT_PX, true);
					} else {
						bitmap = groupService.getAvatar(groupModel, false);
					}
					if (bitmap != null) {
						Palette palette = Palette.from(bitmap).generate();
						color = palette.getDarkVibrantColor(getResources().getColor(R.color.material_grey_600));
					}
				}
				@ColorInt final int scrimColor = color;
				RuntimeUtil.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if (!isFinishing() && !isDestroyed()) {
							collapsingToolbar.setContentScrimColor(scrimColor);
							collapsingToolbar.setStatusBarScrimColor(scrimColor);
						}
					}
				});
			}
		}).start();
	}

	private void setupAdapter() {
		this.groupDetailAdapter = new GroupDetailAdapter(this, this.groupModel);
		this.groupDetailAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
			@Override
			public void onChanged() {
				super.onChanged();
				updateFloatingActionButton();
			}
		});
		this.groupDetailAdapter.setOnClickListener(new GroupDetailAdapter.OnClickListener() {
			@Override
			public void onItemClick(View v, ContactModel contactModel) {
				if (contactModel != null) {
					String identity = contactModel.getIdentity();
					String shortName = NameUtil.getShortName(contactModel);

					ArrayList<String> items = new ArrayList<>();
					ArrayList<Integer> optionsMap = new ArrayList<>();

					items.add(getString(R.string.show_contact));
					optionsMap.add(SELECTOR_OPTION_CONTACT_DETAIL);

					if (!TestUtil.compare(myIdentity, identity)) {
						items.add(String.format(getString(R.string.chat_with), shortName));
						optionsMap.add(SELECTOR_OPTION_CHAT);

						if (ContactUtil.canReceiveVoipMessages(contactModel, blackListIdentityService)
								&& ConfigUtils.isCallsEnabled(GroupDetailActivity.this, preferenceService, licenseService)
						) {
							items.add(String.format(getString(R.string.call_with), shortName));
							optionsMap.add(SELECTOR_OPTION_CALL);
						}

						if (operationMode == MODE_EDIT) {
							if (groupModel != null && !TestUtil.compare(groupModel.getCreatorIdentity(), identity)) {
								items.add(String.format(getString(R.string.kick_user_from_group), shortName));
								optionsMap.add(SELECTOR_OPTION_REMOVE);
							}
						}
						SelectorDialog selectorDialog = SelectorDialog.newInstance(null, items, null);
						SelectorInfo selectorInfo = new SelectorInfo();
						selectorInfo.contactModel = contactModel;
						selectorInfo.view = v;
						selectorInfo.optionsMap = optionsMap;
						selectorDialog.setData(selectorInfo);
						try {
							selectorDialog.show(getSupportFragmentManager(), DIALOG_TAG_CHOOSE_ACTION);
						} catch (IllegalStateException e) {
							logger.error("Exception", e);
						}
					}
				}
			}
		});
	}

	@Override
	public int getLayoutResource() {
		return R.layout.activity_group_detail;
	}

	private void setTitle() {
		if (TestUtil.empty(groupDetailViewModel.getGroupName())) {
			this.groupNameEditText.setText(groupService.getMembersString(this.groupModel));
		} else {
			this.groupNameEditText.setText(groupDetailViewModel.getGroupName());
		}
	}

	private void launchContactDetail(View view, String identity) {
		if (!this.myIdentity.equals(identity)) {
			Intent intent = new Intent(GroupDetailActivity.this, ContactDetailActivity.class);
			intent.putExtra(ThreemaApplication.INTENT_DATA_CONTACT, identity);
			intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			ActivityOptionsCompat options = ActivityOptionsCompat.makeScaleUpAnimation(view, 0, 0, view.getWidth(), view.getHeight());
			ActivityCompat.startActivityForResult(this, intent, ThreemaActivity.ACTIVITY_ID_CONTACT_DETAIL, options.toBundle());
		}
	}

	private void sortGroupMembers() {
		List<ContactModel> contactModels = groupDetailViewModel.getGroupContacts();
		Collections.sort(contactModels, (model1, model2) -> ContactUtil.getSafeNameString(model1, preferenceService).compareTo(
				ContactUtil.getSafeNameString(model2, preferenceService)
		));
		groupDetailViewModel.setGroupContacts(contactModels);
	}

	private void removeMemberFromGroup(final ContactModel contactModel) {
		if (contactModel != null) {
			this.groupDetailViewModel.removeGroupContact(contactModel);
			this.hasChanges = true;
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		if (this.resumePauseHandler != null) {
			this.resumePauseHandler.onPause();
		}
	}

	@Override
	public void onResume() {
		if (this.resumePauseHandler != null) {
			this.resumePauseHandler.onResume();
		}
		super.onResume();
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		MenuItem groupSyncMenu = menu.findItem(R.id.menu_resync);
		MenuItem leaveGroupMenu = menu.findItem(R.id.menu_leave_group);
		MenuItem deleteGroupMenu = menu.findItem(R.id.menu_delete_group);
		MenuItem mediaGalleryMenu = menu.findItem(R.id.menu_gallery);
		MenuItem cloneMenu = menu.findItem(R.id.menu_clone_group);

		if (AppRestrictionUtil.isCreateGroupDisabled(this)) {
			cloneMenu.setVisible(false);
		}

		if (groupModel != null) {
			leaveGroupMenu.setVisible(true);
			deleteGroupMenu.setVisible(true);

			if (groupService.isGroupOwner(this.groupModel)) {
				// MODE_EDIT
				groupSyncMenu.setVisible(true);
			}
		}

		if (groupModel != null && !hiddenChatsListService.has(groupService.getUniqueIdString(this.groupModel))) {
			mediaGalleryMenu.setVisible(true);
		} else {
			mediaGalleryMenu.setVisible(false);
		}

		if (operationMode != MODE_READONLY) {
			menu.findItem(R.id.action_send_message).setVisible(false);
		}

		return super.onPrepareOptionsMenu(menu);
	}

	@SuppressLint("RestrictedApi")
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.activity_group_detail, menu);

		try {
			MenuBuilder menuBuilder = (MenuBuilder) menu;
			menuBuilder.setOptionalIconsVisible(true);
		} catch (Exception ignored) {}

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				finishUp();
				return true;
			case R.id.action_send_message:
				if (groupModel != null) {
					Intent intent = new Intent(this, ComposeMessageActivity.class);
					intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					intent.putExtra(ThreemaApplication.INTENT_DATA_GROUP, groupId);
					intent.putExtra(ThreemaApplication.INTENT_DATA_EDITFOCUS, Boolean.TRUE);
					startActivity(intent);
					finish();
				}
				break;
			case R.id.menu_resync:
				this.syncGroup();
				break;
			case R.id.menu_leave_group:
				int leaveMessageRes = operationMode == MODE_READONLY ? R.string.really_leave_group_message : R.string.really_leave_group_admin_message;

				GenericAlertDialog.newInstance(
						R.string.action_leave_group,
						Html.fromHtml(getString(leaveMessageRes)),
						R.string.ok,
						R.string.cancel)
						.show(getSupportFragmentManager(), DIALOG_TAG_LEAVE_GROUP);
				break;
			case R.id.menu_delete_group:
				GenericAlertDialog.newInstance(
						R.string.action_delete_group,
						groupService.isGroupOwner(groupModel) ? R.string.delete_my_group_message : R.string.delete_group_message,
						R.string.ok,
						R.string.cancel)
						.show(getSupportFragmentManager(), DIALOG_TAG_DELETE_GROUP);
				break;
			case R.id.menu_gallery:
				if (groupId > 0 && !hiddenChatsListService.has(groupService.getUniqueIdString(this.groupModel))) {
					Intent mediaGalleryIntent = new Intent(this, MediaGalleryActivity.class);
					mediaGalleryIntent.putExtra(ThreemaApplication.INTENT_DATA_GROUP, groupId);
					startActivity(mediaGalleryIntent);
				}
				break;
			case R.id.menu_clone_group:
				GenericAlertDialog.newInstance(
						R.string.action_clone_group,
						R.string.clone_group_message,
						R.string.yes,
						R.string.no)
						.show(getSupportFragmentManager(), DIALOG_TAG_CLONE_GROUP_CONFIRM);
				break;
		}
		return super.onOptionsItemSelected(item);
	}

	private void leaveGroupAndQuit() {
		new LeaveGroupAsyncTask(groupModel, groupService, this, null, this::finishUp).execute();
	}

	private void deleteGroupAndQuit() {
		if (groupService.isGroupOwner(groupModel)) {
			new DeleteMyGroupAsyncTask(groupModel, groupService, this, null, this::navigateHome).execute();
		} else {
			new DeleteGroupAsyncTask(groupModel, groupService, this, null, this::navigateHome).execute();
		}
	}

	@SuppressLint("StaticFieldLeak")
	private void cloneGroup(final String newGroupName) {
		new AsyncTask<Void, Void, GroupModel>() {
			@Override
			protected void onPreExecute() {
				GenericProgressDialog.newInstance(R.string.action_clone_group, R.string.please_wait).show(getSupportFragmentManager(), DIALOG_TAG_CLONING_GROUP);
			}

			@Override
			protected GroupModel doInBackground(Void... params) {
				GroupModel model;

				try {
					Bitmap avatar = groupService.getAvatar(groupModel, true);

					model = groupService.createGroup(
							newGroupName,
							groupService.getGroupIdentities(groupModel),
							avatar
					);
				} catch (Exception e) {
					logger.error("Exception", e);
					return null;
				}

				return model;
			}

			@Override
			protected void onPostExecute(GroupModel newModel) {
				DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_CLONING_GROUP, true);

				if (newModel != null) {
					Intent intent = new Intent(GroupDetailActivity.this, ComposeMessageActivity.class);
					intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					intent.putExtra(ThreemaApplication.INTENT_DATA_GROUP, newModel.getId());
					startActivity(intent);
					finishUp();
				} else {
					Toast.makeText(GroupDetailActivity.this, getString(R.string.error_creating_group) + ": " + getString(R.string.internet_connection_required), Toast.LENGTH_LONG).show();
				}
			}
		}.execute();
	}

	private void showConversation(String identity) {
		Intent intent = new Intent(this, ComposeMessageActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		intent.putExtra(ThreemaApplication.INTENT_DATA_CONTACT, identity);
		startActivity(intent);
	}

	private void syncGroup() {
		if(this.groupService != null) {
			GenericProgressDialog.newInstance(R.string.resync_group, R.string.please_wait).show(getSupportFragmentManager(), DIALOG_TAG_RESYNC_GROUP);

			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						groupService.sendSync(groupModel);
					 	RuntimeUtil.runOnUiThread(() -> Toast.makeText(GroupDetailActivity.this,
								 getString(R.string.group_was_synchronized),
								 Toast.LENGTH_SHORT).show());

					 	RuntimeUtil.runOnUiThread(() -> DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_RESYNC_GROUP, true));
					} catch (Exception x) {

					 	RuntimeUtil.runOnUiThread(() -> DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_RESYNC_GROUP, true));
						LogUtil.exception(x, GroupDetailActivity.this);
					}
				}
			}).start();
		}
	}

	@SuppressLint("StaticFieldLeak")
	private void saveGroupSettings() {
		if (groupNameEditText.getText() != null) {
			this.groupDetailViewModel.setGroupName(groupNameEditText.getText().toString());
		} else {
			this.groupDetailViewModel.setGroupName("");
		}

		new AsyncTask<Void, Void, GroupModel>() {
			@Override
			protected void onPreExecute() {
				GenericProgressDialog.newInstance(R.string.updating_group, R.string.please_wait).show(getSupportFragmentManager(), DIALOG_TAG_UPDATE_GROUP);
			}

			@Override
			protected GroupModel doInBackground(Void... params) {
				GroupModel model;

				if (!deviceService.isOnline()) {
					return null;
				}

				try {
					Bitmap avatar = groupDetailViewModel.getAvatarFile() != null ? BitmapFactory.decodeFile(groupDetailViewModel.getAvatarFile().getPath()) : null;

					model = groupService.updateGroup(
							groupModel,
							groupDetailViewModel.getGroupName(),
							groupDetailViewModel.getGroupIdentities(),
							avatar,
							groupDetailViewModel.getIsAvatarRemoved()
					);
				} catch (Exception x) {
					logger.error("Exception", x);
					return null;
				}

				return model;
			}

			@Override
			protected void onPostExecute(GroupModel newModel) {
				DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_UPDATE_GROUP, true);

				if (newModel != null) {
					finishUp();
				} else {
					SimpleStringAlertDialog.newInstance(R.string.updating_group, getString(R.string.error_creating_group) + ": " + getString(R.string.internet_connection_required)).show(getSupportFragmentManager(), "er");
				}
			}
		}.execute();
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == Activity.RESULT_OK) {
			switch (requestCode) {
				case ThreemaActivity.ACTIVITY_ID_GROUP_ADD:
					// some users were added
					groupDetailViewModel.addGroupContacts(IntentDataUtil.getContactIdentities(data));
					sortGroupMembers();
					this.hasChanges = true;
					break;
				default:
					if (this.avatarEditView != null) {
						this.avatarEditView.onActivityResult(requestCode, resultCode, data);
					}
					super.onActivityResult(requestCode, resultCode, data);
			}
		}

		if (requestCode == ThreemaActivity.ACTIVITY_ID_CONTACT_DETAIL) {
			// contacts may have been edited
			sortGroupMembers();
		}
	}

	@Override
	public void onDestroy() {
		ListenerManager.contactSettingsListeners.remove(this.contactSettingsListener);
		ListenerManager.groupListeners.remove(this.groupListener);
		ListenerManager.contactListeners.remove(this.contactListener);

		if (this.resumePauseHandler != null) {
			this.resumePauseHandler.onDestroy(this);
		}

		super.onDestroy();
	}

	@Override
	public void onClick(String tag, int which, Object object) {
		SelectorInfo selectorInfo = (SelectorInfo) object;

		// click on selector
		if (selectorInfo.contactModel != null) {
			switch (selectorInfo.optionsMap.get(which)) {
				case SELECTOR_OPTION_CONTACT_DETAIL:
					launchContactDetail(selectorInfo.view, selectorInfo.contactModel.getIdentity());
					break;
				case SELECTOR_OPTION_CHAT:
					showConversation(selectorInfo.contactModel.getIdentity());
					finishUp();
					break;
				case SELECTOR_OPTION_REMOVE:
					removeMemberFromGroup(selectorInfo.contactModel);
					break;
				case SELECTOR_OPTION_CALL:
					VoipUtil.initiateCall(this, selectorInfo.contactModel, false, null);
					break;
				default:
					break;
			}
		}
	}

	@Override
	public void onCancel(String tag) {}

	@Override
	public void onYes(String tag, String text) {
		// text entry dialog
		switch(tag) {
			case DIALOG_TAG_CLONE_GROUP:
				cloneGroup(text);
				break;
			default:
				break;
		}
	}

	@Override
	public void onNo(String tag) {}

	@Override
	public void onNeutral(String tag) {}

	@Override
	public void onYes(String tag, Object data) {
		switch(tag) {
			case DIALOG_TAG_LEAVE_GROUP:
				leaveGroupAndQuit();
				break;
			case DIALOG_TAG_DELETE_GROUP:
				deleteGroupAndQuit();
				break;
			case DIALOG_TAG_QUIT:
				saveGroupSettings();
				break;
			case DIALOG_TAG_CLONE_GROUP_CONFIRM:
				TextEntryDialog.newInstance(
						R.string.action_clone_group,
						R.string.name,
						R.string.ok,
						R.string.cancel,
						groupModel.getName(),
						0,
						0)
						.show(getSupportFragmentManager(), DIALOG_TAG_CLONE_GROUP);
				break;
			default:
				break;
		}
	}

	@Override
	public void onBackPressed() {
		if (this.operationMode == MODE_EDIT && this.hasChanges) {
			GenericAlertDialog.newInstance(
					R.string.save_changes,
					R.string.save_group_changes,
					R.string.yes,
					R.string.no,
				false)
					.show(getSupportFragmentManager(), DIALOG_TAG_QUIT);
		} else {
			finishUp();
		}
	}

	@Override
	public void onNo(String tag, Object data) {
		switch(tag) {
			case DIALOG_TAG_QUIT:
				finishUp();
				break;
			default:
				break;
		}
	}

	private void updateFloatingActionButton() {
		if (this.floatingActionButton != null &&
			this.groupService != null &&
			this.groupDetailAdapter != null) {
			if (this.groupService.isGroupOwner(this.groupModel)) {
				if (this.groupDetailAdapter.getItemCount() > getResources().getInteger(R.integer.max_group_size)) {
					this.floatingActionButton.hide();
				} else {
					this.floatingActionButton.show();
				}
			}
		}
	}

	private void finishUp() {
		finish();
	}

	private void navigateHome() {
		Intent intent = new Intent(GroupDetailActivity.this, HomeActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
		startActivity(intent);
		ActivityCompat.finishAffinity(GroupDetailActivity.this);
		overridePendingTransition(0, 0);
	}

	/* callbacks from MyAvatarView */
	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (this.avatarEditView != null) {
			this.avatarEditView.onRequestPermissionsResult(requestCode, permissions, grantResults);
		}
	}
}

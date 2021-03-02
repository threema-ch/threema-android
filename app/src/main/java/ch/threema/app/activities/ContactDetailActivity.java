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

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.adapters.ContactDetailAdapter;
import ch.threema.app.dialogs.ContactEditDialog;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.dialogs.SelectorDialog;
import ch.threema.app.dialogs.SimpleStringAlertDialog;
import ch.threema.app.listeners.ContactListener;
import ch.threema.app.listeners.ContactSettingsListener;
import ch.threema.app.listeners.GroupListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.DeadlineListService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.IdListService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.QRCodeService;
import ch.threema.app.services.license.LicenseService;
import ch.threema.app.ui.AvatarEditView;
import ch.threema.app.ui.ResumePauseHandler;
import ch.threema.app.ui.TooltipPopup;
import ch.threema.app.utils.AppRestrictionUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ContactUtil;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.LogUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.QRScannerUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.ShareUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.ViewUtil;
import ch.threema.app.voip.services.VoipStateService;
import ch.threema.app.voip.util.VoipUtil;
import ch.threema.base.VerificationLevel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupModel;

import static ch.threema.app.utils.QRScannerUtil.REQUEST_CODE_QR_SCANNER;

public class ContactDetailActivity extends ThreemaToolbarActivity
		implements LifecycleOwner,
					GenericAlertDialog.DialogClickListener,
		           ContactEditDialog.ContactEditDialogClickListener,
		           SelectorDialog.SelectorDialogClickListener {
	private static final Logger logger = LoggerFactory.getLogger(ContactDetailActivity.class);

	private static final String DIALOG_TAG_EDIT = "cedit";
	private static final String DIALOG_TAG_LINK_UNLINK_SELECTOR = "lu";
	private static final String DIALOG_TAG_DELETE_CONTACT = "deleteContact";
	private static final String DIALOG_TAG_EXCLUDE_CONTACT = "excludeContact";
	private static final String DIALOG_TAG_UNLINK_CONTACT = "unlinkContact";
	private static final String DIALOG_TAG_DELETING_CONTACT = "dliC";
	private static final String DIALOG_TAG_ADD_CONTACT = "dac";
	private static final String DIALOG_TAG_CONFIRM_BLOCK = "block";

	private static final int PERMISSION_REQUEST_WRITE_CONTACTS = 1;
	private static final int PERMISSION_REQUEST_CAMERA = 2;

	private static final String RUN_ON_ACTIVE_RELOAD = "reload";
	private static final String RUN_ON_ACTIVE_RELOAD_GROUP = "reload_group";

	private ContactModel contact;
	private String identity;
	private ContactService contactService;
	private GroupService groupService;
	private IdListService blackListIdentityService, profilePicRecipientsService;
	private MessageService messageService;
	private DeadlineListService hiddenChatsListService;
	private LicenseService licenseService;
	private VoipStateService voipStateService;
	private MenuItem blockMenuItem = null, profilePicItem = null, profilePicSendItem = null, callItem = null;
	private boolean isReadonly;
	private ResumePauseHandler resumePauseHandler;
	private RecyclerView contactDetailRecyclerView;
	private AvatarEditView avatarEditView;
	private FloatingActionButton floatingActionButton;
	private TextView contactTitle;
	private CollapsingToolbarLayout collapsingToolbar;
	private List<GroupModel> groupList;
	private boolean isDisabledProfilePicReleaseSettings = false;
	private View workIcon;

	private final ResumePauseHandler.RunIfActive runIfActiveUpdate = new ResumePauseHandler.RunIfActive() {
		@Override
		public void runOnUiThread() {
			reload();

			groupList = groupService.getGroupsByIdentity(identity);
			contactDetailRecyclerView.setAdapter(setupAdapter());
		}
	};
	private final ResumePauseHandler.RunIfActive runIfActiveGroupUpdate = new ResumePauseHandler.RunIfActive() {
		@Override
		public void runOnUiThread() {
			groupList = groupService.getGroupsByIdentity(identity);
			contactDetailRecyclerView.setAdapter(setupAdapter());
		}
	};

	private final ContactSettingsListener contactSettingsListener = new ContactSettingsListener() {
		@Override
		public void onSortingChanged() { }

		@Override
		public void onNameFormatChanged() {
			resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD, runIfActiveUpdate);
		}

		@Override
		public void onAvatarSettingChanged() { }

		@Override
		public void onInactiveContactsSettingChanged() { }

		@Override
		public void onNotificationSettingChanged(String uid) { }
	};

	private final ContactListener contactListener = new ContactListener() {
		@Override
		public void onModified(ContactModel modifiedContactModel) {
		 	RuntimeUtil.runOnUiThread(() -> {
				 updateBlockMenu();
			 });
			resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD, runIfActiveUpdate);
		}

		@Override
		public void onAvatarChanged(ContactModel contactModel) {
			RuntimeUtil.runOnUiThread(() -> updateProfilepicMenu());
			resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD, runIfActiveUpdate);
		}

		@Override
		public void onRemoved(ContactModel removedContactModel) {
			//whaat, finish!
		 	RuntimeUtil.runOnUiThread(() -> finish());
		}

		@Override
		public boolean handle(String identity) {
			return TestUtil.compare(contact.getIdentity(), identity);
		}
	};

	private final GroupListener groupListener = new GroupListener() {
		@Override
		public void onCreate(GroupModel newGroupModel) {
			resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD_GROUP, runIfActiveGroupUpdate);
		}

		@Override
		public void onRename(GroupModel groupModel) {
			resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD_GROUP, runIfActiveGroupUpdate);
		}

		@Override
		public void onUpdatePhoto(GroupModel groupModel) {
			resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD_GROUP, runIfActiveGroupUpdate);
		}

		@Override
		public void onRemove(GroupModel groupModel) {
			resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD_GROUP, runIfActiveGroupUpdate);
		}

		@Override
		public void onNewMember(GroupModel group, String newIdentity) {
			if (newIdentity.equals(identity)) {
				resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD_GROUP, runIfActiveGroupUpdate);
			}
		}

		@Override
		public void onMemberLeave(GroupModel group, String leftIdentity) {
			if (leftIdentity.equals(identity)) {
				resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD_GROUP, runIfActiveGroupUpdate); ;
			}
		}

		@Override
		public void onMemberKicked(GroupModel group, String kickedIdentity) {
			if (kickedIdentity.equals(identity)) {
				resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD_GROUP, runIfActiveGroupUpdate); ;
			}
		}

		@Override
		public void onUpdate(GroupModel groupModel) {
			//ignore
		}

		@Override
		public void onLeave(GroupModel groupModel) {
			resumePauseHandler.runOnActive(RUN_ON_ACTIVE_RELOAD_GROUP, runIfActiveGroupUpdate);
		}
	};

	@Override
	public int getLayoutResource() {
		return R.layout.activity_contact_detail;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.identity = this.getIntent().getStringExtra(ThreemaApplication.INTENT_DATA_CONTACT);

		if (this.identity == null || this.identity.length() == 0) {
			logger.error("no identity", this);
			this.finish();
			return;
		}

		if (this.identity.equals(getMyIdentity())) {
			this.finish();
			return;
		}

		ConfigUtils.configureTransparentStatusBar(this);

		final ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
		}

		this.resumePauseHandler = ResumePauseHandler.getByActivity(this, this);

		try {
			this.contactService = serviceManager.getContactService();
			this.blackListIdentityService = serviceManager.getBlackListService();
			this.profilePicRecipientsService = serviceManager.getProfilePicRecipientsService();
			this.groupService = serviceManager.getGroupService();
			this.messageService = serviceManager.getMessageService();
			this.hiddenChatsListService = serviceManager.getHiddenChatsListService();
			this.licenseService = serviceManager.getLicenseService();
			this.voipStateService = serviceManager.getVoipStateService();
		} catch (Exception e) {
			LogUtil.exception(e, this);
			this.finish();
			return;
		}

		this.collapsingToolbar = findViewById(R.id.collapsing_toolbar);
		this.collapsingToolbar.setTitle(" ");

		if (this.contactService == null) {
			logger.error("no contact service", this);
			finish();
			return;
		}

		this.contact = this.contactService.getByIdentity(this.identity);
		if (this.contact == null) {
			Toast.makeText(this, R.string.contact_not_found, Toast.LENGTH_LONG).show();
			this.finish();
			return;
		}

		this.avatarEditView = findViewById(R.id.avatar_edit_view);
		this.avatarEditView.setHires(true);
		this.avatarEditView.setContactModel(contact);

		this.isReadonly = getIntent().getBooleanExtra(ThreemaApplication.INTENT_DATA_CONTACT_READONLY, false);

		this.contactDetailRecyclerView = findViewById(R.id.contact_group_list);
		if (this.contactDetailRecyclerView == null) {
			logger.error("list not available");
			this.finish();
			return;
		}


		this.contactTitle = findViewById(R.id.contact_title);
		this.workIcon = findViewById(R.id.work_icon);
		ViewUtil.show(workIcon, contactService.showBadge(contact));
		this.workIcon.setContentDescription(getString(ConfigUtils.isWorkBuild() ? R.string.private_contact : R.string.threema_work_contact));

		this.groupList = this.groupService.getGroupsByIdentity(this.identity);

		if (ConfigUtils.isWorkRestricted()) {
			Boolean value = AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__disable_send_profile_picture));
			if (value != null) {
				isDisabledProfilePicReleaseSettings = value;
			}
		}

		this.contactDetailRecyclerView.setLayoutManager(new LinearLayoutManager(this));
		this.contactDetailRecyclerView.setAdapter(setupAdapter());

		if (this.contact.isHidden()) {
			this.reload();
			GenericAlertDialog.newInstance(R.string.menu_add_contact, String.format(getString(R.string.contact_add_confirm), NameUtil.getDisplayNameOrNickname(contact, true)), R.string.yes, R.string.no).show(getSupportFragmentManager(), DIALOG_TAG_ADD_CONTACT);
		} else {
			onCreateLocal();
			this.reload();

			if (savedInstanceState == null) {
				if (!ConfigUtils.isWorkBuild() && contactService.showBadge(contact)) {
					if (!preferenceService.getIsWorkHintTooltipShown()) {
						showWorkTooltip();
					}
				}
			}
		}
	}

	private void onCreateLocal() {
		ListenerManager.contactListeners.add(this.contactListener);
		ListenerManager.contactSettingsListeners.add(this.contactSettingsListener);
		ListenerManager.groupListeners.add(this.groupListener);

		this.floatingActionButton = findViewById(R.id.floating);
		this.floatingActionButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				openContactEditor();
			}
		});

		if (getToolbar().getNavigationIcon() != null) {
			getToolbar().getNavigationIcon().setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
		}
	}

	private void showWorkTooltip() {
		workIcon.postDelayed(() -> {
			int[] location = new int[2];
			workIcon.getLocationOnScreen(location);
			location[0] += workIcon.getWidth() / 2;
			location[1] += workIcon.getHeight();

			final TooltipPopup workTooltipPopup = new TooltipPopup(this, R.string.preferences__tooltip_work_hint_shown, R.layout.popup_tooltip_top_left_work, this, new Intent(this, WorkExplainActivity.class));
			workTooltipPopup.show(this, workIcon, getString(R.string.tooltip_work_hint), TooltipPopup.ALIGN_BELOW_ANCHOR_ARROW_LEFT, location, 0);

			final AppBarLayout appBarLayout = findViewById(R.id.appbar);
			if (appBarLayout != null) {
				appBarLayout.addOnOffsetChangedListener(new AppBarLayout.BaseOnOffsetChangedListener() {
					@Override
					public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
						workTooltipPopup.dismiss(false);
						appBarLayout.removeOnOffsetChangedListener(this);
					}
				});
			}

			new Handler().postDelayed(new Runnable() {
				@Override
				public void run() {
					RuntimeUtil.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							workTooltipPopup.dismiss(false);
						}
					});
				}
			}, 4000);
		}, 1000);
	}

	private ContactDetailAdapter setupAdapter() {
		ContactDetailAdapter groupMembershipAdapter = new ContactDetailAdapter(this, this.groupList, contact);
		groupMembershipAdapter.setOnClickListener(new ContactDetailAdapter.OnClickListener() {
			@Override
			public void onLinkedContactClick(View v) {
				if (ConfigUtils.requestContactPermissions(ContactDetailActivity.this, null, PERMISSION_REQUEST_WRITE_CONTACTS)) {
					linkOrUnlinkContact();
				}
			}

			@Override
			public void onItemClick(View v, GroupModel groupModel) {

				Intent intent = new Intent(ContactDetailActivity.this, GroupDetailActivity.class);
				intent.putExtra(ThreemaApplication.INTENT_DATA_GROUP, groupModel.getId());

				startActivityForResult(intent, ThreemaActivity.ACTIVITY_ID_GROUP_DETAIL);
			}

			@Override
			public void onVerificationInfoClick(View v) {
				Intent intent = new Intent(ContactDetailActivity.this, VerificationLevelActivity.class);
				startActivity(intent);
			}
		});

		return groupMembershipAdapter;
	}

	@Override
	protected void onDestroy() {
		if (floatingActionButton != null) {
			floatingActionButton.hide();
		}

		ListenerManager.contactListeners.remove(this.contactListener);
		ListenerManager.contactSettingsListeners.remove(this.contactSettingsListener);
		ListenerManager.groupListeners.remove(this.groupListener);

		if (this.resumePauseHandler != null) {
			this.resumePauseHandler.onDestroy(this);
		}
		super.onDestroy();
	}

	@Override
	public void onPause() {
		super.onPause();

		if (this.resumePauseHandler != null) {
			this.resumePauseHandler.onPause();
		}
	}

	private void openContactEditor() {
		Uri contactUri = ContactUtil.getLinkedUri(this, contactService, contact);

		if (contactUri != null) {
			Intent intent = new Intent(Intent.ACTION_EDIT);
			intent.setDataAndType(contactUri, ContactsContract.Contacts.CONTENT_ITEM_TYPE);
			intent.putExtra("finishActivityOnSaveCompleted", true);

			// make sure users are coming back to threema and not the external activity
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
			if (intent.resolveActivity(getPackageManager()) != null) {
				startActivity(intent);
			} else {
				Toast.makeText(ContactDetailActivity.this, "No contact editor found on device.", Toast.LENGTH_SHORT).show();
			}
		} else {
			editName();
		}
	}

	private void setScrimColor() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				@ColorInt int color = getResources().getColor(R.color.material_grey_600);
				if (contact != null) {
					final Bitmap bitmap = contactService.getAvatar(contact, false);
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

	private void reload() {
		this.contactTitle.setText(NameUtil.getDisplayNameOrNickname(contact, true));
		setScrimColor();
	}

	private void linkContact() {
		// Creates a new Intent to insert or edit a contact
		Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
		if (intent.resolveActivity(getPackageManager()) != null) {
			startActivityForResult(intent, ThreemaActivity.ACTIVITY_ID_PICK_CONTACT);
		} else {
			Toast.makeText(this, "No contact picker found on device", Toast.LENGTH_SHORT).show();
		}
	}

	private void unlinkContact() {
		GenericAlertDialog dialogFragment = GenericAlertDialog.newInstance(
				R.string.really_unlink_contact_title,
				R.string.really_unlink_contact,
				R.string.ok,
				R.string.cancel);
		dialogFragment.setData(contact);
		dialogFragment.show(getSupportFragmentManager(), DIALOG_TAG_UNLINK_CONTACT);
	}

	private void linkOrUnlinkContact() {
		if (ContactUtil.isLinked(contact)) {
			SelectorDialog.newInstance(
					getString(R.string.synchronize_contact),
					new ArrayList<String>(Arrays.asList(getResources().getStringArray(R.array.linked_contact_unlink_array))),
					getString(R.string.cancel)).show(getSupportFragmentManager(), DIALOG_TAG_LINK_UNLINK_SELECTOR);
		} else {
			linkContact();
		}
	}

	private void reallyUnlinkContact(ContactModel contactModel) {
		if (contactService != null) {
			contactService.unlink(contactModel);
		}
	}

	@Override
	public void onResume() {
		if (this.resumePauseHandler != null) {
			this.resumePauseHandler.onResume();
		}
		super.onResume();
	}

	private void removeContact() {
		GenericAlertDialog dialogFragment = GenericAlertDialog.newInstance(
				R.string.delete_contact_action,
				R.string.really_delete_contact,
				R.string.ok,
				R.string.cancel);
		dialogFragment.setData(contact);
		dialogFragment.show(getSupportFragmentManager(), DIALOG_TAG_DELETE_CONTACT);
	}

	private void removeContactConfirmed(final boolean addToExcludeList, final ContactModel contactModel) {
		new AsyncTask<Void, Void, Boolean>() {
			@Override
			protected void onPreExecute() {
				GenericProgressDialog.newInstance(R.string.deleting_contact, R.string.please_wait).show(getSupportFragmentManager(), DIALOG_TAG_DELETING_CONTACT);
			}


			@Override
			protected Boolean doInBackground(Void... params) {
				if (addToExcludeList) {
					IdListService excludeFromSyncListService = ContactDetailActivity.this
							.serviceManager.getExcludedSyncIdentitiesService();

					if (excludeFromSyncListService != null) {
						excludeFromSyncListService.add(contactModel.getIdentity());
					}
				}
				return contactService.remove(contactModel);
			}

			@Override
			protected void onPostExecute(Boolean success) {
				DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_DELETING_CONTACT, true);
				if (!success) {
					Toast.makeText(ContactDetailActivity.this, "Failed to remove contact", Toast.LENGTH_SHORT).show();
				} else {
					finishAndGoHome();
				}
			}
		}.execute();
	}

	private String getZeroLengthToNull(String v) {
		return v == null || v.length() == 0 ? null : v;
	}

	private void editName() {
		ContactEditDialog contactEditDialog = ContactEditDialog.newInstance(contact);
		contactEditDialog.show(getSupportFragmentManager(), DIALOG_TAG_EDIT);
	}

	@SuppressLint("RestrictedApi")
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		if (isFinishing()) {
			return false;
		}

		getMenuInflater().inflate(R.menu.activity_contact_detail, menu);
		try {
			MenuBuilder menuBuilder = (MenuBuilder) menu;
			menuBuilder.setOptionalIconsVisible(true);
		} catch (Exception ignored) {}

		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		if (isFinishing()) {
			return false;
		}

		// display verification level in action bar
		if (contact != null && contact.getVerificationLevel() != VerificationLevel.FULLY_VERIFIED) {
			MenuItem menuItem = menu.findItem(R.id.action_scan_id);
			menuItem.setVisible(true);
		}
		if (isReadonly) {
			menu.findItem(R.id.action_send_message).setVisible(false);
		}
		this.blockMenuItem = menu.findItem(R.id.action_block_contact);
		updateBlockMenu();

		this.profilePicSendItem = menu.findItem(R.id.action_send_profilepic);
		this.profilePicItem = menu.findItem(R.id.action_add_profilepic_recipient);
		updateProfilepicMenu();

		this.callItem = menu.findItem(R.id.menu_threema_call);

		updateVoipCallMenuItem(null);

		MenuItem galleryMenuItem = menu.findItem(R.id.menu_gallery);
		if (hiddenChatsListService.has(contactService.getUniqueIdString(contact))) {
			galleryMenuItem.setVisible(false);
		}

		return super.onPrepareOptionsMenu(menu);
	}

	@UiThread
	private void updateVoipCallMenuItem(final Boolean newState) {
		if (callItem != null) {
			if (
				ContactUtil.canReceiveVoipMessages(contact, blackListIdentityService)
					&& ConfigUtils.isCallsEnabled(ContactDetailActivity.this, preferenceService, licenseService)) {
				logger.debug("updateVoipMenu newState " + newState);

				callItem.setVisible(newState != null ? newState : voipStateService.getCallState().isIdle());
			} else {
				callItem.setVisible(false);
			}
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_send_message:
				if (identity != null) {
					Intent intent = new Intent(this, ComposeMessageActivity.class);
					intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					intent.putExtra(ThreemaApplication.INTENT_DATA_CONTACT, identity);
					intent.putExtra(ThreemaApplication.INTENT_DATA_EDITFOCUS, Boolean.TRUE);
					startActivity(intent);
					finish();
				}
				break;
			case R.id.action_remove_contact:
				removeContact();
				break;
			case R.id.action_scan_id:
				if (ConfigUtils.requestCameraPermissions(this, null, PERMISSION_REQUEST_CAMERA)) {
					scanQR();
				}
				break;
			case R.id.menu_threema_call:
				VoipUtil.initiateCall(this, contact, false, null);
				break;
			case R.id.action_block_contact:
				if (this.blackListIdentityService != null && this.blackListIdentityService.has(this.contact.getIdentity())) {
					blockContact();
				} else {
					GenericAlertDialog.newInstance(R.string.block_contact, R.string.really_block_contact, R.string.yes, R.string.no).show(getSupportFragmentManager(), DIALOG_TAG_CONFIRM_BLOCK);
				}
				break;
			case R.id.action_share_contact:
				ShareUtil.shareContact(this, contact);
				break;
			case R.id.menu_gallery:
				if (!hiddenChatsListService.has(contactService.getUniqueIdString(contact))) {
					Intent mediaGalleryIntent = new Intent(this, MediaGalleryActivity.class);
					mediaGalleryIntent.putExtra(ThreemaApplication.INTENT_DATA_CONTACT, identity);
					startActivity(mediaGalleryIntent);
				}
				break;
			case R.id.action_add_profilepic_recipient:
				if (!profilePicRecipientsService.has(contact.getIdentity())) {
					profilePicRecipientsService.add(contact.getIdentity());
				} else {
					profilePicRecipientsService.remove(contact.getIdentity());
				}
				updateProfilepicMenu();
				break;
			case R.id.action_send_profilepic:
				sendProfilePic();
				break;
			default:
				finishUp();
		}
		return super.onOptionsItemSelected(item);
	}

	private void sendProfilePic() {
		contact.setProfilePicSentDate(new Date(0));
		contactService.save(contact);

		new AsyncTask<Void, Void, Boolean>() {
			@Override
			protected Boolean doInBackground(Void... params) {
				MessageReceiver messageReceiver = contactService.createReceiver(contact);
				return messageService.sendProfilePicture(new MessageReceiver[]{messageReceiver});
			}

			@Override
			protected void onPostExecute(Boolean aBoolean) {
				if (aBoolean) {
					Toast.makeText(ThreemaApplication.getAppContext(), R.string.profile_picture_sent, Toast.LENGTH_LONG).show();
				}
			}
		}.execute();
	}

	private void blockContact() {
		if (this.blackListIdentityService != null) {
			this.blackListIdentityService.toggle(this, this.contact);
		}
	}

	private void updateBlockMenu() {
		if (this.blockMenuItem != null) {
			if (blackListIdentityService != null && blackListIdentityService.has(contact.getIdentity())) {
				blockMenuItem.setTitle(R.string.unblock_contact);
			} else {
				blockMenuItem.setTitle(R.string.block_contact);
			}
		}
	}

	private void updateProfilepicMenu() {
		if (this.profilePicItem != null && this.profilePicSendItem != null) {

			if (isDisabledProfilePicReleaseSettings) {
				this.profilePicItem.setVisible(false);
				this.profilePicSendItem.setVisible(false);
				return;
			}

			switch (preferenceService.getProfilePicRelease()) {
				case PreferenceService.PROFILEPIC_RELEASE_EVERYONE:
					this.profilePicItem.setVisible(false);
					this.profilePicSendItem.setVisible(ContactUtil.canReceiveProfilePics(contact));
					break;
				case PreferenceService.PROFILEPIC_RELEASE_SOME:
					if (ContactUtil.canReceiveProfilePics(contact)) {
						if (profilePicRecipientsService != null && profilePicRecipientsService.has(contact.getIdentity())) {
							profilePicItem.setTitle(R.string.menu_send_profilpic_off);
							profilePicItem.setIcon(R.drawable.ic_person_remove_outline);
							profilePicSendItem.setVisible(true);
						} else {
							profilePicItem.setTitle(R.string.menu_send_profilpic);
							profilePicItem.setIcon(R.drawable.ic_person_add_outline);
							profilePicSendItem.setVisible(false);
						}
						this.profilePicItem.setVisible(true);
					} else {
						this.profilePicSendItem.setVisible(false);
						this.profilePicItem.setVisible(false);
					}
					break;
				case PreferenceService.PROFILEPIC_RELEASE_NOBODY:
					this.profilePicItem.setVisible(false);
					this.profilePicSendItem.setVisible(false);
					break;
				default:
					break;
			}
		}
	}

	private void scanQR() {
		QRScannerUtil.getInstance().initiateScan(this, false, null);
	}

	private void link(final String lookupKey) {
		if (TestUtil.empty(lookupKey)) {
			return;
		}
		GenericProgressDialog.newInstance(-1, R.string.please_wait).show(getSupportFragmentManager(), "pleaseWait");

		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					contactService.link(contact, lookupKey);
				} catch (final Exception e) {
				 	RuntimeUtil.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							logger.error("Exception", e);
						}
					});
				} finally {
				 	RuntimeUtil.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							DialogUtil.dismissDialog(getSupportFragmentManager(), "pleaseWait", true);
						}
					});
				}
			}
		}).start();
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);

		try {
			Fragment fragment = getSupportFragmentManager().findFragmentByTag(DIALOG_TAG_EDIT);
			if (fragment != null && fragment.isAdded()) {
				fragment.onActivityResult(requestCode, resultCode, intent);
			}
		} catch (Exception e) {
			//
		}

		switch (requestCode) {
			case ACTIVITY_ID_PICK_CONTACT:
				if (resultCode == RESULT_OK) {
					Uri contactUri = intent.getData();
					if (ContactUtil.isLinked(contact)) {
						if (this.contactService != null) {
							this.contactService.unlink(contact);
						}

					}
					if (contactUri != null) {
						Cursor cursor = getContentResolver().query(contactUri, new String[]{
							ContactsContract.Contacts._ID,
							ContactsContract.Contacts.LOOKUP_KEY
						}, null, null, null);

						//get the lookup key
						if (cursor != null) {
							String lookupKey = null;
							int contactId = 0;
							if (cursor.moveToFirst()) {
								lookupKey = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY));
								contactId = cursor.getInt(cursor.getColumnIndex(ContactsContract.Contacts._ID));

							}
							cursor.close();
							if (!TestUtil.empty(lookupKey)) {
								if (contactId != 0) {
									lookupKey += "/" + contactId;
								}
								this.link(lookupKey);
							}
						}
					}
				}
				break;
			case ACTIVITY_ID_GROUP_DETAIL:
				// contacts may have been edited
				this.groupList = this.groupService.getGroupsByIdentity(this.identity);
				contactDetailRecyclerView.setAdapter(setupAdapter());
				break;
			case REQUEST_CODE_QR_SCANNER:
				QRCodeService.QRCodeContentResult qrRes =
						QRScannerUtil.getInstance().parseActivityResult(this, requestCode, resultCode, intent,
								this.serviceManager.getQRCodeService());

				if (qrRes != null) {
					if (qrRes.getExpirationDate() != null && qrRes.getExpirationDate().before(new Date())) {
						SimpleStringAlertDialog.newInstance(R.string.title_adduser, getString(R.string.expired_barcode)).show(getSupportFragmentManager(), "expiredId");
						return;
					}

					if(!TestUtil.compare(identity, qrRes.getIdentity())) {
						SimpleStringAlertDialog.newInstance(
								R.string.scan_id_mismatch_title,
								getString(R.string.scan_id_mismatch_message)).show(getSupportFragmentManager(), "scanId");
						return;
					}
					int contactVerification = this.contactService.updateContactVerification(identity, qrRes.getPublicKey());

					//update the view
					// this.updateVerificationLevelImage(this.verificationLevelImageView);

					int txt;
					switch (contactVerification) {
						case ContactService.ContactVerificationResult_ALREADY_VERIFIED:
							txt = R.string.scan_duplicate;
							break;
						case ContactService.ContactVerificationResult_VERIFIED:
							txt = R.string.scan_successful;
							break;
						default:
							txt = R.string.id_mismatch;
					}
					SimpleStringAlertDialog.newInstance(R.string.scan_id, getString(txt)).show(getSupportFragmentManager(), "scanId");
				}
				break;
			default:
				if (this.avatarEditView != null) {
					this.avatarEditView.onActivityResult(requestCode, resultCode, intent);
				}
				break;
		}
	}

	void deleteContact(ContactModel contactModel) {
		IdListService excludeFromSyncListService = this.serviceManager.getExcludedSyncIdentitiesService();

		//second question, if the contact is a synced contact
		if (ContactUtil.isSynchronized(contactModel) && excludeFromSyncListService != null
				&& !excludeFromSyncListService.has(contactModel.getIdentity())) {

			GenericAlertDialog dialogFragment = GenericAlertDialog.newInstance(
					R.string.delete_contact_action,
					R.string.want_to_add_to_exclude_list,
					R.string.yes,
					R.string.no);
			dialogFragment.setData(contact);
			dialogFragment.show(getSupportFragmentManager(), DIALOG_TAG_EXCLUDE_CONTACT);
		} else {
			removeContactConfirmed(false, contactModel);
		}
	}

	void unhideContact(ContactModel contactModel) {
		contactService.setIsHidden(contactModel.getIdentity(), false);
		onCreateLocal();
		reload();
	}

	@Override
	public void onYes(String tag, Object data) {
		switch (tag) {
			case DIALOG_TAG_UNLINK_CONTACT:
				reallyUnlinkContact((ContactModel) data);
				break;
			case DIALOG_TAG_DELETE_CONTACT:
				deleteContact((ContactModel) data);
				break;
			case DIALOG_TAG_EXCLUDE_CONTACT:
				removeContactConfirmed(true, (ContactModel) data);
				break;
			case DIALOG_TAG_ADD_CONTACT:
				unhideContact(this.contact);
				break;
			case DIALOG_TAG_CONFIRM_BLOCK:
				blockContact();
				break;
			default:
				break;
		}
	}

	@Override
	public void onNo(String tag, Object data) {
		switch (tag) {
			case DIALOG_TAG_EXCLUDE_CONTACT:
				removeContactConfirmed(false, (ContactModel) data);
				break;
			case DIALOG_TAG_ADD_CONTACT:
				finish();
				break;
			default:
				break;
		}
	}

	@Override
	public void onYes(String tag, String text1, String text2, File croppedAvatarFile) {
		String firstName = this.getZeroLengthToNull(text1);
		String lastName = this.getZeroLengthToNull(text2);

		String existingFirstName = this.getZeroLengthToNull(contact.getFirstName());
		String existingLastName = this.getZeroLengthToNull(contact.getLastName());

		if(!TestUtil.compare(firstName, existingFirstName)
				|| !TestUtil.compare(lastName, existingLastName)) {

			//only save contact stuff if the name has changed!
			this.contactService.setName(this.contact, firstName, lastName);
		}
	}

	@Override
	public void onClick(String tag, int which, Object data) {
		switch(which) {
			case 0:
				unlinkContact();
				break;
			case 1:
				linkContact();
				break;
			default:
				break;
		}
	}

	@Override
	public void onCancel(String tag) {}

	@Override
	public void onNo(String tag) {}

	@TargetApi(Build.VERSION_CODES.M)
	@Override
	public void onRequestPermissionsResult(int requestCode,
										   @NonNull String permissions[], @NonNull int[] grantResults) {
		switch (requestCode) {
			case PERMISSION_REQUEST_WRITE_CONTACTS:
				if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					linkOrUnlinkContact();
				} else if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) {
					ConfigUtils.showPermissionRationale(this, findViewById(R.id.main_content), R.string.permission_contacts_required);
				}
				break;
			case PERMISSION_REQUEST_CAMERA:
				if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					scanQR();
				} else if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
					ConfigUtils.showPermissionRationale(this, findViewById(R.id.main_content), R.string.permission_camera_qr_required);
				}
				break;
		}
	}

	@Override
	public void onBackPressed() {
		finishUp();
	}


	private void finishUp() {
		finish();
	}

	private void finishAndGoHome() {
		if (isFinishing() || isDestroyed()) {
			return;
		}
		navigateUpTo(new Intent(this, HomeActivity.class));
		overridePendingTransition(R.anim.fast_fade_in, R.anim.fast_fade_out);
	}
}

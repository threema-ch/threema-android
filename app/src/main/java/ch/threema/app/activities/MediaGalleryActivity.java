/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2021 Threema GmbH
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
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ProgressBar;

import com.google.android.material.snackbar.Snackbar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.adapters.MediaGalleryAdapter;
import ch.threema.app.adapters.MediaGallerySpinnerAdapter;
import ch.threema.app.cache.ThumbnailCache;
import ch.threema.app.dialogs.CancelableHorizontalProgressDialog;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.MessageService;
import ch.threema.app.ui.EmptyView;
import ch.threema.app.utils.AnimationUtil;
import ch.threema.app.utils.AppRestrictionUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.LogUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.data.MessageContentsType;

public class MediaGalleryActivity extends ThreemaToolbarActivity implements AdapterView.OnItemClickListener, ActionBar.OnNavigationListener, GenericAlertDialog.DialogClickListener {
	private static final Logger logger = LoggerFactory.getLogger(MediaGalleryActivity.class);

	private ThumbnailCache<?> thumbnailCache = null;
	private MediaGalleryAdapter mediaGalleryAdapter;
	private MessageReceiver messageReceiver;
	private String actionBarTitle;
	private ActionBar actionBar;
	private SpinnerMessageFilter spinnerMessageFilter;
	private MediaGallerySpinnerAdapter spinnerAdapter;
	private List<AbstractMessageModel> values;
	private GridView gridView;
	private EmptyView emptyView;
	private TypedArray mediaTypeArray;
	private int currentType;
	private ActionMode actionMode = null;
	private AbstractMessageModel initialMessageModel = null;

	public FileService fileService;
	public MessageService messageService;
	public ContactService contactService;
	public GroupService groupService;
	public DistributionListService distributionListService;

	private final int TYPE_ALL = 0;
	private final int TYPE_IMAGE = 1;
	private final int TYPE_VIDEO = 2;
	private final int TYPE_AUDIO = 3;
	private final int TYPE_FILE = 4;

	private static final String DELETE_MESSAGES_CONFIRM_TAG = "reallydelete";
	private static final String DIALOG_TAG_DELETING_MEDIA = "dmm";

	private class SpinnerMessageFilter implements MessageService.MessageFilter {
		private @MessageContentsType int[] filter = null;

		public void setFilterByType(int spinnerMessageType) {
			switch (spinnerMessageType) {
				case TYPE_ALL:
					this.filter = new int[]{MessageContentsType.IMAGE, MessageContentsType.VIDEO, MessageContentsType.AUDIO, MessageContentsType.FILE, MessageContentsType.GIF, MessageContentsType.VOICE_MESSAGE};
					break;
				case TYPE_IMAGE:
					this.filter = new int[]{MessageContentsType.IMAGE};
					break;
				case TYPE_VIDEO:
					this.filter = new int[]{MessageContentsType.VIDEO, MessageContentsType.GIF};
					break;
				case TYPE_AUDIO:
					this.filter = new int[]{MessageContentsType.AUDIO, MessageContentsType.VOICE_MESSAGE};
					break;
				case TYPE_FILE:
					this.filter = new int[]{MessageContentsType.FILE};
					break;
				default:
					break;
			}
		}

		@Override
		public long getPageSize() {
			return 0;
		}

		@Override
		public Integer getPageReferenceId() {
			return null;
		}

		@Override
		public boolean withStatusMessages() {
			return false;
		}

		@Override
		public boolean withUnsaved() {
			return false;
		}

		@Override
		public boolean onlyUnread() {
			return false;
		}

		@Override
		public boolean onlyDownloaded() {
			return true;
		}

		@Override
		public MessageType[] types() { return null; }

		@Override
		@MessageContentsType
		public int[] contentTypes() {
			return this.filter;
		}
	}

	@Override
	public int getLayoutResource() {
		return R.layout.activity_media_gallery;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	protected boolean initActivity(Bundle savedInstanceState) {
		logger.debug("initActivity");
		if (!super.initActivity(savedInstanceState)) {
			return false;
		}

		if (!this.requiredInstances()) {
			this.finish();
			return false;
		}

		currentType = TYPE_ALL;

		this.gridView = findViewById(R.id.gridview);
		this.gridView.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE_MODAL);
		this.gridView.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {
			@Override
			public void onItemCheckedStateChanged(android.view.ActionMode mode, int position, long id, boolean checked) {
				final int count = gridView.getCheckedItemCount();
				if (count > 0) {
					mode.setTitle(Integer.toString(count));
				}
			}

			@Override
			public boolean onCreateActionMode(android.view.ActionMode mode, Menu menu) {
				mode.getMenuInflater().inflate(R.menu.action_media_gallery, menu);
				actionMode = mode;

				ConfigUtils.themeMenu(menu, ConfigUtils.getColorFromAttribute(MediaGalleryActivity.this, R.attr.colorAccent));

				if (AppRestrictionUtil.isShareMediaDisabled(MediaGalleryActivity.this)) {
					menu.findItem(R.id.menu_message_save).setVisible(false);
				}

				return true;
			}

			@Override
			public boolean onPrepareActionMode(android.view.ActionMode mode, Menu menu) {
				mode.setTitle(Integer.toString(gridView.getCheckedItemCount()));
				return false;
			}

			@Override
			public boolean onActionItemClicked(android.view.ActionMode mode, MenuItem item) {
				switch (item.getItemId()) {
					case R.id.menu_message_discard:
						discardMessages();
						return true;
					case R.id.menu_message_save:
						saveMessages();
						return true;
					case R.id.menu_message_select_all:
						selectAllMessages();
						return true;
					default:
						return false;
				}
			}

			@Override
			public void onDestroyActionMode(android.view.ActionMode mode) {
				actionMode = null;
			}
		});
		this.gridView.setOnItemClickListener(this);
		this.gridView.setNumColumns(ConfigUtils.isLandscape(this) ? 5 : 3);

		processIntent(getIntent());

		this.actionBar = getSupportActionBar();
		if (this.actionBar == null) {
			logger.debug("no action bar");
			finish();
			return false;
		}
		this.actionBar.setDisplayHomeAsUpEnabled(true);
		this.actionBar.setDisplayShowTitleEnabled(false);

		// add text view if contact list is empty
		this.mediaTypeArray = getResources().obtainTypedArray(R.array.media_gallery_spinner);
		this.spinnerAdapter = new MediaGallerySpinnerAdapter(
				this.actionBar.getThemedContext(), getResources().getStringArray(R.array.media_gallery_spinner),
				this.actionBarTitle);

		this.actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
		this.actionBar.setListNavigationCallbacks(spinnerAdapter, this);
		this.actionBar.setSelectedNavigationItem(this.currentType);

		this.spinnerMessageFilter = new SpinnerMessageFilter();
		this.spinnerMessageFilter.setFilterByType(this.currentType);
		this.thumbnailCache = new ThumbnailCache<Integer>(null);

		FrameLayout frameLayout = findViewById(R.id.frame_parent);

		this.emptyView = new EmptyView(this);
		this.emptyView.setColorsInt(ConfigUtils.getColorFromAttribute(this, android.R.attr.windowBackground), ConfigUtils.getColorFromAttribute(this, R.attr.textColorPrimary));
		this.emptyView.setup(getString(R.string.no_media_found_generic));

		frameLayout.addView(this.emptyView);
		this.gridView.setEmptyView(this.emptyView);

		if (savedInstanceState == null || mediaGalleryAdapter == null) {
			setupAdapters(this.currentType, true);
		}

		return true;
	}

	@Override
	protected void onDestroy() {
		if (this.thumbnailCache != null) {
			this.thumbnailCache.flush();
		}
		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_media_gallery, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				finish();
				break;
			case R.id.menu_message_select_all:
				selectAllMessages();
				break;
		}
		return true;
	}

	private void processIntent(Intent intent) {
		if (intent.hasExtra(ThreemaApplication.INTENT_DATA_GROUP)) {
			int groupId = intent.getIntExtra(ThreemaApplication.INTENT_DATA_GROUP, 0);
			GroupModel groupModel = this.groupService.getById(groupId);
			messageReceiver = this.groupService.createReceiver(groupModel);
			actionBarTitle = groupModel.getName();
		} else if (intent.hasExtra(ThreemaApplication.INTENT_DATA_DISTRIBUTION_LIST)) {
			DistributionListModel distributionListModel = distributionListService.getById(intent.getIntExtra(ThreemaApplication.INTENT_DATA_DISTRIBUTION_LIST, 0));
			try {
				messageReceiver = distributionListService.createReceiver(distributionListModel);
			} catch (Exception e) {
				logger.error("Exception", e);
			}
			actionBarTitle = distributionListModel.getName();
		} else {
			String identity = intent.getStringExtra(ThreemaApplication.INTENT_DATA_CONTACT);
			if (identity == null) {
				finish();
			}
			ContactModel contactModel = this.contactService.getByIdentity(identity);
			messageReceiver = this.contactService.createReceiver(contactModel);
			actionBarTitle = NameUtil.getDisplayNameOrNickname(contactModel, true);
		}


		String type = IntentDataUtil.getAbstractMessageType(intent);
		int id = IntentDataUtil.getAbstractMessageId(intent);

		if (type != null && id != 0) {
			initialMessageModel = messageService.getMessageModelFromId(id, type);
		}
	}

	private void setupAdapters(int newType, boolean force) {
		if (this.currentType != newType || force) {
			this.values = this.getMessages(this.messageReceiver);
			if (this.values == null || this.values.isEmpty()) {
				if (this.emptyView != null) {
					if (newType == TYPE_ALL) {
						this.emptyView.setup(getString(R.string.no_media_found_generic));
					} else {
						this.emptyView.setup(String.format(getString(R.string.no_media_found), getString(this.mediaTypeArray.getResourceId(newType, -1))));
					}
				}
			}

			this.mediaGalleryAdapter = new MediaGalleryAdapter(
					this,
					values,
					this.fileService,
					this.thumbnailCache
			);

			this.gridView.setAdapter(this.mediaGalleryAdapter);
			if (initialMessageModel != null) {
				this.gridView.post(new Runnable() {
					@Override
					public void run() {
						for(int position = 0; position < values.size(); position++) {
							if (values.get(position).getId() == initialMessageModel.getId()) {
								gridView.setSelection(position);
								break;
							}
						}
						initialMessageModel = null;
					}
				});
			}
		}
		this.currentType = newType;
		resetSpinnerAdapter(newType);
	}

	private void resetSpinnerAdapter(int type) {
		if (this.spinnerAdapter != null && this.mediaTypeArray != null && this.values != null) {
			this.spinnerAdapter.setSubtitle(getString(this.mediaTypeArray.getResourceId(type, -1)) + " (" + this.values.size() + ")");
			this.spinnerAdapter.notifyDataSetChanged();
		}
	}

	private List<AbstractMessageModel> getMessages(MessageReceiver receiver) {
		List<AbstractMessageModel> values = null;
		try {
			values = receiver.loadMessages(this.spinnerMessageFilter);
		} catch (SQLException e) {
			logger.error("Exception", e);
		}
		return values;
	}

	@Override
	public void onItemClick(AdapterView<?> parent, final View view, int position, long id) {
			final AbstractMessageModel m = this.mediaGalleryAdapter.getItem(position);
			ProgressBar progressBar = view.findViewById(R.id.progress_decoding);

			switch (mediaGalleryAdapter.getItemViewType(position)) {
				case MediaGalleryAdapter.TYPE_IMAGE:
					// internal viewer
					showInMediaFragment(m, view);
					break;
				case MediaGalleryAdapter.TYPE_VIDEO:
					showInMediaFragment(m, view);
					break;
				case MediaGalleryAdapter.TYPE_AUDIO:
					showInMediaFragment(m, view);
					break;
				case MediaGalleryAdapter.TYPE_FILE:
					if (m!= null && (FileUtil.isImageFile(m.getFileData()) || FileUtil.isVideoFile(m.getFileData()) || FileUtil.isAudioFile(m.getFileData()))) {
						showInMediaFragment(m, view);
					} else {
						decodeAndShowFile(m, view, progressBar);
					}
					break;
			}
	}

	@Override
	public boolean onNavigationItemSelected(int itemPosition, long itemId) {
		this.spinnerMessageFilter.setFilterByType(itemPosition);
		setupAdapters(itemPosition, false);

		return true;
	}

	private void selectAllMessages() {
		if (gridView != null) {
			if (gridView.getCount() == gridView.getCheckedItemCount()) {
				if (actionMode != null) {
					actionMode.finish();
				}
			} else {
				for (int i = 0; i < gridView.getCount(); i++) {
					if (currentType == TYPE_ALL || mediaGalleryAdapter.getItemViewType(i) == currentType) {
						gridView.setItemChecked(i, true);
					}
				}
				if (actionMode != null) {
					actionMode.invalidate();
				}
			}
		}
	}

	private void discardMessages() {
		List<AbstractMessageModel> selectedMessages = getSelectedMessages();
		GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.really_delete_message_title, String.format(getString(R.string.really_delete_media), selectedMessages.size()), R.string.delete_message, R.string.cancel);
		dialog.setData(selectedMessages);
		dialog.show(getSupportFragmentManager(), DELETE_MESSAGES_CONFIRM_TAG);
	}

	private void saveMessages() {
		fileService.saveMedia(this, gridView, new CopyOnWriteArrayList<>(getSelectedMessages()), true);
		actionMode.finish();
	}

	private List<AbstractMessageModel> getSelectedMessages() {
		List<AbstractMessageModel> selectedMessages = new ArrayList<>();
		SparseBooleanArray checkedItems = gridView.getCheckedItemPositions();

		final int size = checkedItems.size();
		for (int i = 0; i < size; i++) {
			final int index = checkedItems.keyAt(i);

			if (checkedItems.valueAt(i)) {
				selectedMessages.add(mediaGalleryAdapter.getItem(index));
			}
		}
		return selectedMessages;
	}

	@SuppressLint("StaticFieldLeak")
	private void reallyDiscardMessages(final CopyOnWriteArrayList<AbstractMessageModel> selectedMessages) {
		new AsyncTask<Void, Integer, Void>() {
			boolean cancelled = false;

			@Override
			protected void onPreExecute() {
				if (selectedMessages.size() > 10) {
					CancelableHorizontalProgressDialog dialog = CancelableHorizontalProgressDialog.newInstance(R.string.deleting_messages, 0, R.string.cancel, selectedMessages.size());
					dialog.setOnCancelListener(new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							cancelled = true;
						}
					});
					dialog.show(getSupportFragmentManager(), DIALOG_TAG_DELETING_MEDIA);
				}
			}

			@Override
			protected Void doInBackground(Void... params) {
				int i = 0;
				Iterator<AbstractMessageModel> checkedItemsIterator = selectedMessages.iterator();
				while (checkedItemsIterator.hasNext() && !cancelled) {
					publishProgress(i++);
					try {
						final AbstractMessageModel messageModel = checkedItemsIterator.next();

						if (messageModel != null) {
							messageService.remove(messageModel);
						 	RuntimeUtil.runOnUiThread(new Runnable() {
								@Override
								public void run() {
									mediaGalleryAdapter.remove(messageModel);
								}
							});
						}
					} catch (Exception e) {
						logger.error("Exception", e);
					}
				}
				return null;
			}

			@Override
			protected void onPostExecute(Void result) {
				DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_DELETING_MEDIA, true);
				Snackbar.make(gridView, R.string.message_deleted, Snackbar.LENGTH_LONG).show();
				if (actionMode != null) {
					actionMode.finish();
				}
				resetSpinnerAdapter(currentType);
			}

			@Override
			protected void onProgressUpdate(Integer... index) {
				DialogUtil.updateProgress(getSupportFragmentManager(), DIALOG_TAG_DELETING_MEDIA, index[0] + 1);
			}
		}.execute();
	}

	@Override
	public void onYes(String tag, Object data) {
		reallyDiscardMessages(new CopyOnWriteArrayList<>((ArrayList< AbstractMessageModel>) data));
	}

	@Override
	public void onNo(String tag, Object data) {
	}

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		final int topmost;
		if (this.gridView != null) {
			View topChild = this.gridView.getChildAt(0);
			if (topChild != null) {
				if (topChild.getTop() < 0) {
					topmost = this.gridView.getFirstVisiblePosition() + 1;
				} else {
					topmost = this.gridView.getFirstVisiblePosition();
				}
			} else {
				topmost = 0;
			}
		} else {
			topmost = 0;
		}

		super.onConfigurationChanged(newConfig);

		if (this.gridView != null) {
			this.gridView.post(() -> {
				gridView.setNumColumns(ConfigUtils.isLandscape(MediaGalleryActivity.this) ? 5 : 3);
				gridView.setSelection(topmost);
			});
		}
	}

	private void hideProgressBar(final ProgressBar progressBar) {
		if (progressBar != null) {
		 	RuntimeUtil.runOnUiThread(() -> progressBar.setVisibility(View.GONE));
		}
	}

	private void showProgressBar(final ProgressBar progressBar) {
		if (progressBar != null) {
		 	RuntimeUtil.runOnUiThread(() -> progressBar.setVisibility(View.VISIBLE));
		}
	}

	public void decodeAndShowFile(final AbstractMessageModel m, final View v, final ProgressBar progressBar) {
		showProgressBar(progressBar);
		fileService.loadDecryptedMessageFile(m, new FileService.OnDecryptedFileComplete() {
			@Override
			public void complete(File decodedFile) {
				hideProgressBar(progressBar);
				messageService.viewMediaMessage(getApplicationContext(), m, fileService.getShareFileUri(decodedFile, null));
			}

			@Override
			public void error(String message) {
				hideProgressBar(progressBar);
				if (!TestUtil.empty(message)) {
					logger.error(message, MediaGalleryActivity.this);
				}
			}
		});
	}

	public void showInMediaFragment(final AbstractMessageModel m, final View v) {
		Intent intent = new Intent(this, MediaViewerActivity.class);
		IntentDataUtil.append(m, intent);
		intent.putExtra(MediaViewerActivity.EXTRA_ID_IMMEDIATE_PLAY, true);
		intent.putExtra(MediaViewerActivity.EXTRA_ID_REVERSE_ORDER, false);
		AnimationUtil.startActivityForResult(this, v, intent, ACTIVITY_ID_MEDIA_VIEWER);
	}

	@Override
	protected boolean checkInstances() {
		return TestUtil.required(
				this.fileService,
				this.messageService,
				this.groupService,
				this.distributionListService,
				this.contactService
		) && super.checkInstances();
	}

	@Override
	protected void instantiate() {
		super.instantiate();

		ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager != null) {
			try {
				this.fileService = serviceManager.getFileService();
				this.messageService = serviceManager.getMessageService();
				this.groupService = serviceManager.getGroupService();
				this.distributionListService = serviceManager.getDistributionListService();
				this.contactService = serviceManager.getContactService();
			} catch (Exception e) {
				LogUtil.exception(e, this);
			}
		}
	}
}


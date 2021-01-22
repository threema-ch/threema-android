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
import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.BadParcelableException;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.actions.LocationMessageSendAction;
import ch.threema.app.actions.SendAction;
import ch.threema.app.actions.TextMessageSendAction;
import ch.threema.app.adapters.FilterableListAdapter;
import ch.threema.app.dialogs.CancelableHorizontalProgressDialog;
import ch.threema.app.dialogs.ExpandableTextEntryDialog;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.fragments.DistributionListFragment;
import ch.threema.app.fragments.GroupListFragment;
import ch.threema.app.fragments.RecentListFragment;
import ch.threema.app.fragments.RecipientListFragment;
import ch.threema.app.fragments.UserListFragment;
import ch.threema.app.fragments.WorkUserListFragment;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.ConversationService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.UserService;
import ch.threema.app.ui.MediaItem;
import ch.threema.app.ui.SingleToast;
import ch.threema.app.ui.ThreemaSearchView;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ContactLookupUtil;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.MimeUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.NavigationUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.client.file.FileData;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.data.LocationDataModel;
import java8.util.concurrent.CompletableFuture;

import static ch.threema.app.activities.SendMediaActivity.MAX_SELECTABLE_IMAGES;
import static ch.threema.app.ui.MediaItem.TYPE_TEXT;

public class RecipientListBaseActivity extends ThreemaToolbarActivity implements
	CancelableHorizontalProgressDialog.ProgressDialogClickListener,
	ExpandableTextEntryDialog.ExpandableTextEntryDialogClickListener,
	SearchView.OnQueryTextListener {

	private static final Logger logger = LoggerFactory.getLogger(RecipientListBaseActivity.class);

	private final static int FRAGMENT_RECENT = 0;
	private final static int FRAGMENT_USERS = 1;
	private final static int FRAGMENT_GROUPS = 2;
	private final static int FRAGMENT_DISTRIBUTION_LIST = 3;
	private final static int FRAGMENT_WORK_USERS = 4;
	private final static int NUM_FRAGMENTS = 5;

	private static final String DIALOG_TAG_MULTISEND = "multisend";
	private static final String DIALOG_TAG_FILECOPY = "filecopy";

	public static final String INTENT_DATA_MULTISELECT = "ms";

	private static final int REQUEST_READ_EXTERNAL_STORAGE = 1;

	private ViewPager viewPager;
	private UserGroupPagerAdapter userGroupPagerAdapter;
	private MenuItem searchMenuItem;
	private ThreemaSearchView searchView;

	private boolean isForward, hideUi, hideRecents, multiSelect;
	private String captionText;
	private final List<MediaItem> mediaItems = new ArrayList<>();
	private final List<MessageReceiver> recipientMessageReceivers = new ArrayList<>();
	private final List<AbstractMessageModel> originalMessageModels = new ArrayList<>();
	private final List<Integer> tabs = new ArrayList<>(NUM_FRAGMENTS);

	private GroupService groupService;
	private ContactService contactService;
	private ConversationService conversationService;
	private DistributionListService distributionListService;
	private MessageService messageService;
	private FileService fileService;

	private final Runnable copyFilesRunnable = new Runnable() {
		@Override
		public void run() {
			for (int i = 0; i < mediaItems.size(); i++) {
				MediaItem mediaItem = mediaItems.get(i);
				mediaItem.setFilename(FileUtil.getFilenameFromUri(getContentResolver(), mediaItem));

				if (ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(mediaItem.getUri().getScheme())) {
					try {
						File file = fileService.createTempFile("rcpt", null);
						FileUtil.copyFile(mediaItem.getUri(), file, getContentResolver());
						mediaItem.setUri(Uri.fromFile(file));
						mediaItem.setDeleteAfterUse(true);
					} catch (IOException e) {
						logger.error("Unable to copy to tmp dir", e);
					}
				}
			}
		}
	};

	@Override
	public boolean onQueryTextSubmit(String query) {
		// Do something
		return true;
	}

	@Override
	public boolean onQueryTextChange(String newText) {
		int currentItem = viewPager.getCurrentItem();
		Fragment fragment = userGroupPagerAdapter.getRegisteredFragment(currentItem);

		if (fragment != null) {
			FilterableListAdapter listAdapter = ((RecipientListFragment) fragment).getAdapter();
			// adapter can be null if it has not been initialized yet (runs in different thread)
			if (listAdapter == null) return false;
			listAdapter.getFilter().filter(newText);
		}
		return true;
	}

	public int getLayoutResource() {
		return R.layout.activity_recipientlist;
	}

	private boolean validateSendingPermission(MessageReceiver messageReceiver) {
		return messageReceiver != null
				&& messageReceiver.validateSendingPermission(errorResId -> RuntimeUtil.runOnUiThread(() -> SingleToast.getInstance().showLongText(getString(errorResId))));
	}

	private void resetValues() {
		isForward = hideUi = hideRecents = false;
		mediaItems.clear();
		originalMessageModels.clear();
		tabs.clear();
		captionText = null;
	}

	@Override
	protected boolean initActivity(Bundle savedInstanceState) {
		if (!super.initActivity(savedInstanceState)) {
			return false;
		};

		final ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		UserService userService;
		try {
			this.contactService = serviceManager.getContactService();
			this.conversationService = serviceManager.getConversationService();
			this.groupService = serviceManager.getGroupService();
			this.distributionListService = serviceManager.getDistributionListService();
			this.messageService = serviceManager.getMessageService();
			this.fileService = serviceManager.getFileService();
			userService = serviceManager.getUserService();
		} catch (Exception e) {
			logger.error("Exception", e);
			return false;
		}

		if (!userService.hasIdentity()) {
			ConfigUtils.recreateActivity(this);
		}

		onNewIntent(getIntent());

		return true;
	}

	private void setupUI() {
		final TabLayout tabLayout = findViewById(R.id.sliding_tabs);
		final ActionBar actionBar = getSupportActionBar();
		final ProgressBar progressBar = findViewById(R.id.progress_sending);

		viewPager = findViewById(R.id.pager);
		if (viewPager == null || tabLayout == null) {
			finish();
			return;
		}

		tabLayout.clearOnTabSelectedListeners();
		tabLayout.removeAllTabs();

		viewPager.clearOnPageChangeListeners();
		viewPager.setAdapter(null);
		viewPager.removeAllViews();

		if (!hideUi) {
			boolean hasMedia = false;

			for (MediaItem mediaItem: mediaItems) {
				String mimeType = mediaItem.getMimeType();
				if (mimeType != null) {
					if (!hasMedia && !mimeType.startsWith("text/")) {
						hasMedia = true;
					}
				}
			}

			if (hasMedia) {
				if (!ConfigUtils.requestStoragePermissions(this, null, REQUEST_READ_EXTERNAL_STORAGE)) {
					return;
				}
			}

			if (!hideRecents) {
				tabLayout.addTab(tabLayout.newTab()
						.setIcon(R.drawable.ic_history_outline)
						.setContentDescription(R.string.title_tab_recent));
				tabs.add(FRAGMENT_RECENT);
			}

			if (ConfigUtils.isWorkBuild()) {
				tabLayout.addTab(tabLayout.newTab()
					.setIcon(R.drawable.ic_work_outline)
					.setContentDescription(R.string.title_tab_work_users));
				tabs.add(FRAGMENT_WORK_USERS);
			}

			tabLayout.addTab(tabLayout.newTab()
					.setIcon(R.drawable.ic_person_outline)
					.setContentDescription(R.string.title_tab_users));
			tabLayout.addTab(tabLayout.newTab()
					.setIcon(R.drawable.ic_group_outline)
					.setContentDescription(R.string.title_tab_groups));
			tabLayout.addTab(tabLayout.newTab()
					.setIcon(R.drawable.ic_bullhorn_outline)
					.setContentDescription(R.string.title_tab_distribution_list));

			tabs.add(FRAGMENT_USERS);
			tabs.add(FRAGMENT_GROUPS);
			tabs.add(FRAGMENT_DISTRIBUTION_LIST);

			if (progressBar != null) {
				progressBar.setVisibility(View.GONE);
			}

			// keeps inactive tabs from being destroyed causing all kinds of problems with lingering AsyncTasks on the the adapter
			viewPager.setVisibility(View.VISIBLE);
			viewPager.setOffscreenPageLimit(tabLayout.getTabCount() - 1);

			tabLayout.setVisibility(View.VISIBLE);
			tabLayout.setTabGravity(TabLayout.GRAVITY_FILL);

			userGroupPagerAdapter = new UserGroupPagerAdapter(getSupportFragmentManager());
			viewPager.setAdapter(userGroupPagerAdapter);
			viewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));
			tabLayout.addOnTabSelectedListener(new TabLayout.ViewPagerOnTabSelectedListener(viewPager));
			viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
				@Override
				public void onPageSelected(int position) {
					if (searchMenuItem != null) {
						searchMenuItem.collapseActionView();
						if (searchView != null) {
							searchView.setQuery("", false);
						}
					}
					invalidateOptionsMenu();
				}
			});

			if (actionBar != null) {
				actionBar.setDisplayHomeAsUpEnabled(true);
				actionBar.setTitle(R.string.title_choose_recipient);
			}

			if (!hideRecents && !conversationService.hasConversations()) {
				//no conversation? show users tab as default
				this.viewPager.setCurrentItem(tabs.indexOf(FRAGMENT_USERS), true);
			}

			if (searchMenuItem != null) {
				searchMenuItem.setVisible(true);
			}
		} else {
			if (actionBar != null) {
				actionBar.setDisplayHomeAsUpEnabled(false);
				actionBar.setTitle(R.string.please_wait);
			}
			if (tabLayout != null) {
				tabLayout.setVisibility(View.GONE);
			}
			if (progressBar != null) {
				progressBar.setVisibility(View.VISIBLE);
			}
			if (searchMenuItem != null) {
				searchMenuItem.setVisible(false);
			}
		}

		findViewById(R.id.main_content).setVisibility(View.VISIBLE);
	}

	@Override
	protected void onNewIntent(Intent intent) {
		logger.debug("onNewIntent");

		super.onNewIntent(intent);

		resetValues();

		if (intent != null) {
			setIntent(intent);

			try {
				this.hideRecents = intent.getBooleanExtra(ThreemaApplication.INTENT_DATA_HIDE_RECENTS, false);
				this.multiSelect = intent.getBooleanExtra(INTENT_DATA_MULTISELECT, true);
			} catch (BadParcelableException e) {
				logger.error("Exception", e);
			}

			String identity = IntentDataUtil.getIdentity(intent);
			if (!TestUtil.empty(identity)) {
				hideUi = true;
			}

			int groupId = IntentDataUtil.getGroupId(intent);
			if (groupId > 0) {
				hideUi = true;
			}

			String action = intent.getAction();

			if (action != null) {
				// called from other app via regular send intent
				if (action.equals(Intent.ACTION_SEND)) {
					String type = intent.getType();
					Uri uri = null;
					Parcelable parcelable = intent.getParcelableExtra(Intent.EXTRA_STREAM);

					if (parcelable != null) {
						if (!(parcelable instanceof Uri)) {
							parcelable = Uri.parse(parcelable.toString());
						}
						uri = (Uri) parcelable;
					}

					if (type != null && (uri != null || MimeUtil.isTextFile(type))) {
						if (type.equals("message/rfc822")) {
							// email attachments
							//  extract file type from uri path
							String mimeType = FileUtil.getMimeTypeFromUri(this, uri);
							if (!TestUtil.empty(mimeType)) {
								type = mimeType;
							}

							// email body text - can be null
							CharSequence charSequence = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
							if (charSequence != null) {
								String textIntent = charSequence.toString();
								if (!(textIntent.contains("---") && textIntent.contains("WhatsApp"))) {
									// whatsapp forwards media as rfc822 with a footer
									// strip this footer
									mediaItems.add(new MediaItem(uri, TYPE_TEXT, MimeUtil.MIME_TYPE_TEXT, textIntent));
								}
							}
						}
						if (type.equals("text/plain")) {
							String textIntent = getTextFromIntent(intent);

							if (uri != null) {
								// send text as file
								addMediaItem("x-text/plain", uri, null);
								if (textIntent != null) {
									captionText = textIntent;
								}
							} else if (textIntent != null) {
								mediaItems.add(new MediaItem(uri, TYPE_TEXT, MimeUtil.MIME_TYPE_TEXT, textIntent));
							}
						} else {
							if (uri != null) {
								if (ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(uri.getScheme())) {
									// query database for correct mime type as ACTION_SEND may have been called with a generic mime type such as "image/*"
									String[] proj = {
										DocumentsContract.Document.COLUMN_MIME_TYPE
									};

									try (Cursor cursor = getContentResolver().query(uri, proj, null, null, null)) {
										if (cursor != null && cursor.moveToFirst()) {
											String mimeType = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE));
											if (!TestUtil.empty(mimeType)) {
												type = mimeType;
											}
										}
									} catch (Exception e) {
										String filemame = FileUtil.getFilenameFromUri(getContentResolver(), uri);
										if (!TestUtil.empty(filemame)) {
											String mimeType = FileUtil.getMimeTypeFromPath(filemame);
											if (!TestUtil.empty(mimeType)) {
												type = mimeType;
											}
										}
									}
								}

								// if text was shared along with the media item, add that too
								String textIntent = getTextFromIntent(intent);
								addMediaItem(type, uri, textIntent);
							}
						}
					} else {
						// try ClipData
						ClipData clipData = intent.getClipData();
						if (clipData != null && clipData.getItemCount() > 0) {
							for (int i = 0; i < clipData.getItemCount(); i++) {
								Uri uri1 = clipData.getItemAt(i).getUri();
								CharSequence text = clipData.getItemAt(i).getText();

								if (uri1 != null) {
									addMediaItem(type, uri1, null);
								} else if (!TestUtil.empty(text)) {
									mediaItems.add(new MediaItem(uri, TYPE_TEXT, MimeUtil.MIME_TYPE_TEXT, text.toString()));
								}
							}
						}

						if (mediaItems.size() == 0) {
							Toast.makeText(this, getString(R.string.invalid_data), Toast.LENGTH_LONG).show();
							finish();
						}
					}
					this.isForward = intent.getBooleanExtra(ThreemaApplication.INTENT_DATA_IS_FORWARD, false);

					if (!TestUtil.empty(identity)) {
						prepareForwardingOrSharing(new ArrayList<>(Collections.singletonList(contactService.getByIdentity(identity))));
					}

					if (groupId > 0) {
						prepareForwardingOrSharing(new ArrayList<>(Collections.singletonList(groupService.getById(groupId))));
					}
				} else if (action.equals(Intent.ACTION_SENDTO)) {
					// called from contact app or quickcontactbadge
					if (lockAppService != null && lockAppService.isLocked()) {
						finish();
						return;
					}

					// try to extract identity from intent data
					Uri uri = intent.getData();

					// skip user selection if recipient is already known
					if (uri != null && "smsto".equals(uri.getScheme())) {
						mediaItems.add(new MediaItem(uri, TYPE_TEXT, MimeUtil.MIME_TYPE_TEXT, intent.getStringExtra("sms_body")));

						final ContactModel contactModel = ContactLookupUtil.phoneNumberToContact(this, contactService, uri.getSchemeSpecificPart());

						if (contactModel != null) {
							prepareComposeIntent(new ArrayList<>(Collections.singletonList(contactModel)));
							return;
						} else {
							finish();
							return;
						}
					}
				} else if (action.equals(Intent.ACTION_VIEW)) {
					// called from action URL
					if (lockAppService != null && lockAppService.isLocked()) {
						finish();
						return;
					}

					Uri dataUri = intent.getData();

					if (TestUtil.required(dataUri)) {
						String scheme = dataUri.getScheme();
						String host = dataUri.getHost();

						if (scheme != null && host != null) {
							if (
								(ThreemaApplication.uriScheme.equals(scheme) && "compose".equals(host))
								||
								("https".equals(scheme) && (getString(R.string.action_url).equals(host) || getString(R.string.contact_action_url).equals(host)) && "/compose".equals(dataUri.getPath()))
							)
							{
								String text = dataUri.getQueryParameter("text");
								if (!TestUtil.empty(text)) {
									mediaItems.add(new MediaItem(dataUri, TYPE_TEXT, MimeUtil.MIME_TYPE_TEXT, text));
								}

								String targetIdentity;
								String queryParameter = dataUri.getQueryParameter("id");
								if (queryParameter != null) {
									targetIdentity = queryParameter.toUpperCase();
									ContactModel contactModel = contactService.getByIdentity(targetIdentity);

									if (contactModel == null) {
										addNewContact(targetIdentity);
									} else {
										prepareComposeIntent(new ArrayList<>(Collections.singletonList(contactModel)));
									}
								}
							}
						}
					}
				} else if (action.equals(Intent.ACTION_SEND_MULTIPLE) && intent.hasExtra(Intent.EXTRA_STREAM)) {
					// called from other app with multiple media payload
					String type = intent.getType();

					ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
					if (uris != null) {
						for (int i = 0; i < uris.size(); i++) {
							if (i < MAX_SELECTABLE_IMAGES) {
								Uri uri = uris.get(i);
								if (uri != null) {
									String mimeType = FileUtil.getMimeTypeFromUri(this, uri);
									if (mimeType == null) {
										mimeType = type;
									}
									addMediaItem(mimeType, uri, null);
								}
							} else {
								Toast.makeText(getApplicationContext(), getString(R.string.max_selectable_media_exceeded, MAX_SELECTABLE_IMAGES), Toast.LENGTH_LONG).show();
								break;
							}
						}

						if (!TestUtil.empty(identity)) {
							prepareForwardingOrSharing(new ArrayList<>(Collections.singletonList(contactService.getByIdentity(identity))));
						}

						if (groupId > 0) {
							prepareForwardingOrSharing(new ArrayList<>(Collections.singletonList(groupService.getById(groupId))));
						}
					} else {
						finish();
						return;
					}
				} else if (action.equals(ThreemaApplication.INTENT_ACTION_FORWARD)) {
					// internal forward using message id instead of media URI
					isForward = true;
					ArrayList<Integer> messageIds = IntentDataUtil.getAbstractMessageIds(intent);
					String originalMessageType = IntentDataUtil.getAbstractMessageType(intent);

					if (messageIds != null && messageIds.size() > 0) {
						for (int messageId : messageIds) {
							AbstractMessageModel model = messageService.getMessageModelFromId(messageId, originalMessageType);
							if (model != null && model.getType() != MessageType.BALLOT) {
								originalMessageModels.add(model);
							}
						}
					}
				}
			}
		}

		setupUI();
	}

	private String getTextFromIntent(Intent intent) {
		String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);
		String text = intent.getStringExtra(Intent.EXTRA_TEXT);
		String textIntent;
		if (subject != null && subject.length() > 0 && !subject.equals(text)) {
			textIntent = subject;
			if (!TextUtils.isEmpty(text)) {
				textIntent += " - " + text;
			}
		}
		else {
			textIntent = text;
		}
		return textIntent;
	}

	private void addMediaItem(String mimeType, @NonNull Uri uri, @Nullable String caption) {
		if (ContentResolver.SCHEME_FILE.equalsIgnoreCase(uri.getScheme())) {
			String path = uri.getPath();
			File applicationDir = new File(getApplicationInfo().dataDir);

			if (path != null) {
				try {
					String inputPath = new File(path).getCanonicalPath();
					if (inputPath.startsWith(applicationDir.getCanonicalPath())) {
						Toast.makeText(this, "Illegal path", Toast.LENGTH_SHORT).show();
						return;
					}
				} catch (IOException e) {
					logger.error("Exception", e);
					Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
					return;
				}
			}
		}
		mediaItems.add(new MediaItem(uri, mimeType, caption));
	}

	@SuppressLint("StaticFieldLeak")
	private void addNewContact(final String identity) {
		final ContactModel contactModel = contactService.getByIdentity(identity);

		if (contactModel == null) {
			GenericProgressDialog.newInstance(R.string.creating_contact, R.string.please_wait).show(getSupportFragmentManager(), "pro");

			new AsyncTask<Void, Void, Void>() {
				boolean fail = false;
				ContactModel newContactModel = null;

				@Override
				protected Void doInBackground(Void... params) {
					try {
						newContactModel = contactService.createContactByIdentity(identity, false);
					} catch (Exception e) {
						fail = true;
					}
					return null;
				}

				@Override
				protected void onPostExecute(Void result) {
					DialogUtil.dismissDialog(getSupportFragmentManager(), "pro", true);

					if (fail) {
						View rootView = getWindow().getDecorView().findViewById(android.R.id.content);
						Snackbar.make(rootView, R.string.contact_not_found, Snackbar.LENGTH_LONG).show();
					} else {
						prepareComposeIntent(new ArrayList<>(Collections.singletonList(newContactModel)));
					}
				}
			}.execute();
		} else {
			prepareComposeIntent(new ArrayList<>(Collections.singletonList(contactModel)));
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_recipientlist, menu);

		// Associate searchable configuration with the SearchView
		this.searchMenuItem = menu.findItem(R.id.menu_search_messages);
		this.searchView = (ThreemaSearchView) this.searchMenuItem.getActionView();

		if (this.searchView != null) {
			SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
			SearchableInfo mSearchableInfo = searchManager.getSearchableInfo(getComponentName());
			this.searchView.setSearchableInfo(mSearchableInfo);
			this.searchView.setQueryHint(getString(R.string.hint_filter_list));
			this.searchView.setOnQueryTextListener(this);
			if (hideUi) {
				this.searchMenuItem.setVisible(false);
			}
		} else {
			this.searchMenuItem.setVisible(false);
		}

		return true;
	}

	private MessageReceiver getMessageReceiver(Object model) {
		MessageReceiver messageReceiver = null;

		if (model instanceof ContactModel) {
			messageReceiver = contactService.createReceiver((ContactModel) model);
		} else if (model instanceof GroupModel) {
			messageReceiver = groupService.createReceiver((GroupModel) model);
		} else if (model instanceof DistributionListModel) {
			messageReceiver = distributionListService.createReceiver((DistributionListModel) model);
		}
		return messageReceiver;
	}

	private void prepareComposeIntent(ArrayList<Object> models) {
		Intent intent = null;
		MessageReceiver messageReceiver = null;
		ArrayList<MessageReceiver> messageReceivers = new ArrayList<>(models.size());

		for (Object model : models) {
			messageReceiver = getMessageReceiver(model);
			if (validateSendingPermission(messageReceiver)) {
				messageReceivers.add(messageReceiver);
			}
		}

		intent = IntentDataUtil.getComposeIntentForReceivers(this, messageReceivers);

		if (originalMessageModels != null && originalMessageModels.size() > 0) {
			this.forwardMessages(messageReceivers.toArray(new MessageReceiver[0]), intent);
		} else {
			this.sendSharedMedia(messageReceivers.toArray(new MessageReceiver[0]), intent);
		}
	}

	/**
	 * Copy shared files to a private directory as permissions for content URIs may not persist across activities
	 */
	@WorkerThread
	private void copySelectedFiles() {

	}

	private void sendSharedMedia(final MessageReceiver[] messageReceivers, final Intent intent) {
		if (messageReceivers.length == 1 && mediaItems.size() == 1 && TYPE_TEXT == mediaItems.get(0).getType()) {
			intent.putExtra(ThreemaApplication.INTENT_DATA_TEXT, mediaItems.get(0).getCaption());
			startComposeActivity(intent);
		} else if (messageReceivers.length > 1 || mediaItems.size() > 0) {
			messageService.sendMediaAsync(mediaItems, Arrays.asList(messageReceivers));
			startComposeActivity(intent);
		} else {
			startComposeActivity(intent);
		}
	}

	@UiThread
	private void forwardMessages(final MessageReceiver[] messageReceivers, final Intent intent) {
		final int[] errors = {0};
		CancelableHorizontalProgressDialog.newInstance(R.string.sending_messages, 0, 0, originalMessageModels.size()).show(getSupportFragmentManager(), DIALOG_TAG_MULTISEND);
		for (int i = 0; i < originalMessageModels.size(); i++) {
			final AbstractMessageModel messageModel = originalMessageModels.get(i);
			int progress = i;
			fileService.loadDecryptedMessageFile(messageModel, new FileService.OnDecryptedFileComplete() {
				@Override
				public void complete(File decryptedFile) {
					RuntimeUtil.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							DialogUtil.updateProgress(getSupportFragmentManager(), DIALOG_TAG_MULTISEND, progress);
						}
					});
					if (messageModel.isAvailable()) {
						Uri uri = null;
						if (decryptedFile != null) {
							uri = Uri.fromFile(decryptedFile);
						}
						switch (messageModel.getType()) {
							case IMAGE:
								sendForwardedMedia(messageReceivers, uri, captionText, MediaItem.TYPE_IMAGE, null, FileData.RENDERING_MEDIA, null);
								break;
							case VIDEO:
								sendForwardedMedia(messageReceivers, uri, captionText, MediaItem.TYPE_VIDEO, null, FileData.RENDERING_MEDIA, null);
								break;
							case VOICEMESSAGE:
								// voice messages should always be forwarded as files in order not to appear to be recorded by the forwarder
								sendForwardedMedia(messageReceivers, uri, captionText, MediaItem.TYPE_FILE, MimeUtil.MIME_TYPE_AUDIO_AAC, FileData.RENDERING_DEFAULT, null);
								break;
							case FILE:
								int mediaType = MediaItem.TYPE_FILE;
								String mimeType = messageModel.getFileData().getMimeType();
								int renderingType = messageModel.getFileData().getRenderingType();

								if (messageModel.getFileData().getRenderingType() != FileData.RENDERING_DEFAULT) {
									if (MimeUtil.isVideoFile(mimeType)) {
										mediaType = MediaItem.TYPE_VIDEO;
									} else if (MimeUtil.isImageFile(mimeType)) {
										if (MimeUtil.isGifFile(mimeType)) {
											mediaType = MediaItem.TYPE_GIF;
										} else {
											mediaType = MediaItem.TYPE_IMAGE;
										}
									} else {
										// voice messages should always be forwarded as files in order not to appear to be recorded by the forwarder
										renderingType = FileData.RENDERING_DEFAULT;
									}
								}
								sendForwardedMedia(messageReceivers, uri, captionText, mediaType, mimeType, renderingType, messageModel.getFileData().getFileName());
								break;
							case LOCATION:
								sendLocationMessage(messageReceivers, messageModel.getLocationData());
								break;
							case TEXT:
								sendTextMessage(messageReceivers, messageModel.getBody());
								break;
							default:
								// unsupported message type
								break;
						}
					}
				}

				@Override
				public void error(String message) {
					errors[0]++;
				}
			});
		}

		DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_MULTISEND, true);

		if (errors[0] > 0) {
			SingleToast.getInstance().showLongText(getString(R.string.an_error_occurred_during_send));
		}
		startComposeActivity(intent);
	}

	private void startComposeActivityAsync(final Intent intent) {
		if (intent == null) {
			return;
		}

		RuntimeUtil.runOnUiThread(() -> startComposeActivity(intent));
	}

	@UiThread
	private void startComposeActivity(Intent intent) {
		if (isCalledFromExternalApp()) {
			startActivity(intent);
			finishAffinity();
		} else {
			// we have to clear the backstack to prevent users from coming back here with the return key
			Intent upIntent = new Intent(this, HomeActivity.class);
			TaskStackBuilder.create(this)
					.addNextIntent(upIntent)
					.addNextIntent(intent)
					.startActivities();
			finish();
		}
	}

	public void prepareForwardingOrSharing(final ArrayList<Object> models) {
		if (mediaItems.size() > 0 || originalMessageModels.size() > 0) {
			String recipientName = "";

			if (!((mediaItems.size() == 1 && MimeUtil.isTextFile(mediaItems.get(0).getMimeType()))
				|| (originalMessageModels.size() == 1 && originalMessageModels.get(0).getType() == MessageType.TEXT))) {
				for (Object model : models) {
					if (recipientName.length() > 0) {
						recipientName += ", ";
					}

					if (model instanceof ContactModel) {
						recipientName += NameUtil.getDisplayName((ContactModel) model);
					} else if (model instanceof GroupModel) {
						recipientName += NameUtil.getDisplayName((GroupModel) model, this.groupService);
					} else if (model instanceof DistributionListModel) {
						recipientName += NameUtil.getDisplayName((DistributionListModel) model, this.distributionListService);
					}
				}

				if (isForward) {
					// forwarded content of any type
					String presetCaption = null;
					boolean expandable = false;

					if (originalMessageModels.size() == 1) {
						presetCaption = originalMessageModels.get(0).getCaption();
						if (originalMessageModels.get(0).getType() == MessageType.VIDEO ||
							originalMessageModels.get(0).getType() == MessageType.IMAGE ||
							originalMessageModels.get(0).getType() == MessageType.VOICEMESSAGE ||
							originalMessageModels.get(0).getType() == MessageType.FILE) {
							expandable = true;
						}
					}

					ExpandableTextEntryDialog alertDialog = ExpandableTextEntryDialog.newInstance(getString(R.string.really_forward, recipientName), R.string.add_caption_hint, presetCaption, R.string.send, R.string.cancel, expandable);
					alertDialog.setData(models);
					alertDialog.show(getSupportFragmentManager(), null);
				} else {
					// content shared by external apps may be referred to by content URIs. we have to copy these files first in order to be able to access it in another activity
					String finalRecipientName = recipientName;
					GenericProgressDialog.newInstance(R.string.importing_files, R.string.please_wait).show(getSupportFragmentManager(), DIALOG_TAG_FILECOPY);
					try {
						CompletableFuture
							.runAsync(copyFilesRunnable, Executors.newSingleThreadExecutor())
							.thenRunAsync(() -> {
								int numEditableMedia = 0;
								for (MediaItem mediaItem : mediaItems) {
									String mimeType = mediaItem.getMimeType();
									if (MimeUtil.isImageFile(mimeType) || MimeUtil.isVideoFile(mimeType)) {
										numEditableMedia++;
									}
								}

								DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_FILECOPY, true);

								if (numEditableMedia == mediaItems.size()) { // all files are images or videos
									// all files are either images or videos => redirect to SendMediaActivity
									recipientMessageReceivers.clear();
									for (Object model : models) {
										MessageReceiver messageReceiver = getMessageReceiver(model);
										if (validateSendingPermission(messageReceiver)) {
											recipientMessageReceivers.add(messageReceiver);
										}
									}

									if (recipientMessageReceivers.size() > 0) {
										Intent intent = IntentDataUtil.addMessageReceiversToIntent(new Intent(RecipientListBaseActivity.this, SendMediaActivity.class), recipientMessageReceivers.toArray(new MessageReceiver[0]));
										intent.putExtra(SendMediaActivity.EXTRA_MEDIA_ITEMS, (ArrayList<MediaItem>) mediaItems);
										intent.putExtra(ThreemaApplication.INTENT_DATA_TEXT, finalRecipientName);
										startActivityForResult(intent, ThreemaActivity.ACTIVITY_ID_SEND_MEDIA);
									}
								} else {
									// mixed media
									ExpandableTextEntryDialog alertDialog = ExpandableTextEntryDialog.newInstance(getString(R.string.really_send, finalRecipientName), R.string.add_caption_hint, null, R.string.send, R.string.cancel, false);
									alertDialog.setData(models);
									alertDialog.show(getSupportFragmentManager(), null);
								}
							}, ContextCompat.getMainExecutor(getApplicationContext()));
					} catch (Exception e) {
						logger.error("Exception", e);
						finish();
						return;
					}
				}
				return;
			}
		}

		// fallback to starting new chat
		prepareComposeIntent(models);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				if (isCalledFromExternalApp()) {
					finish();
				} else {
					NavigationUtil.navigateUpToHome(this);
				}
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onResume() {
		logger.debug("onResume");
		super.onResume();
	}

	@Override
	protected void onPause() {
		logger.debug("onPause");
		super.onPause();
	}

	@Override
	public void onUserInteraction() {
		logger.debug("onUserInteraction");
		super.onUserInteraction();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode,
									Intent data) {
		switch (requestCode) {
			case ACTIVITY_ID_SEND_MEDIA:
				if (resultCode == RESULT_OK) {
					startComposeActivityAsync(IntentDataUtil.getComposeIntentForReceivers(this, (ArrayList<MessageReceiver>) recipientMessageReceivers));
				} else if (hideUi) {
					finish();
				}
				break;
			default:
				super.onActivityResult(requestCode, resultCode, data);
		}
	}

	public class UserGroupPagerAdapter extends FragmentPagerAdapter {
		// these globals are not persistent across orientation changes (at least in Android <= 4.1)!
		SparseArray<Fragment> registeredFragments = new SparseArray<Fragment>();

		public UserGroupPagerAdapter(FragmentManager fm) {
			super(fm);
		}

		@Override
		public Fragment getItem(int position) {

			Fragment fragment = null;

			switch (tabs.get(position)) {
				case FRAGMENT_USERS:
					fragment = new UserListFragment();
					break;
				case FRAGMENT_GROUPS:
					fragment = new GroupListFragment();
					break;
				case FRAGMENT_RECENT:
					fragment = new RecentListFragment();
					break;
				case FRAGMENT_DISTRIBUTION_LIST:
					fragment = new DistributionListFragment();
					break;
				case FRAGMENT_WORK_USERS:
					fragment = new WorkUserListFragment();
					break;
			}

			if (fragment != null) {
				Bundle args = new Bundle();
				args.putBoolean(RecipientListFragment.ARGUMENT_MULTI_SELECT, multiSelect);
				fragment.setArguments(args);
			}

			return fragment;
		}

		@Override
		public int getCount() {
			return tabs.size();
		}

		@Override
		public CharSequence getPageTitle(int position) {
			switch (tabs.get(position)) {
				case FRAGMENT_USERS:
					return getString(R.string.title_tab_users).toUpperCase();
				case FRAGMENT_GROUPS:
					return getString(R.string.title_tab_groups).toUpperCase();
				case FRAGMENT_RECENT:
					return getString(R.string.title_tab_recent).toUpperCase();
				case FRAGMENT_DISTRIBUTION_LIST:
					return getString(R.string.title_tab_distribution_list).toUpperCase();
				case FRAGMENT_WORK_USERS:
					return getString(R.string.title_tab_work_users).toUpperCase();
			}
			return null;
		}

		@NonNull
		@Override
		public Object instantiateItem(@NonNull ViewGroup container, int position) {
			Fragment fragment = (Fragment) super.instantiateItem(container, position);
			registeredFragments.put(position, fragment);
			return fragment;
		}

		@Override
		public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
			registeredFragments.remove(position);
			super.destroyItem(container, position, object);
		}

		public Fragment getRegisteredFragment(int position) {
			return registeredFragments.get(position);
		}
	}

	public boolean getShowDistributionLists() {
		return tabs.contains(FRAGMENT_RECENT);
	}

	/*
	 * Message action frontends
	 */

	@AnyThread
	private void sendForwardedMedia(final MessageReceiver[] messageReceivers, final Uri uri, final String caption, final int type, @Nullable final String mimeType, @FileData.RenderingType final int renderingType, final String filename) {
		final MediaItem mediaItem = new MediaItem(uri, type);
		if (mimeType != null) {
			mediaItem.setMimeType(mimeType);
		}
		if (renderingType != -1) {
			mediaItem.setRenderingType(renderingType);
		}
		mediaItem.setCaption(caption);
		if (!TestUtil.empty(filename)) {
			mediaItem.setFilename(filename);
		}
		if (renderingType == FileData.RENDERING_MEDIA) {
			if (type == MediaItem.TYPE_VIDEO) {
				// do not re-transcode forwarded videos
				mediaItem.setVideoSize(PreferenceService.VideoSize_ORIGINAL);
			}
			else if (type == MediaItem.TYPE_IMAGE) {
				// do not scale forwarded images
				mediaItem.setImageScale(PreferenceService.ImageScale_ORIGINAL);
			}
		}
		messageService.sendMediaAsync(Collections.singletonList(mediaItem), Arrays.asList(messageReceivers));
	}

	@WorkerThread
	private void sendLocationMessage(final MessageReceiver[] messageReceivers, LocationDataModel locationData) {
		final Location location = new Location("");
		location.setLatitude(locationData.getLatitude());
		location.setLongitude(locationData.getLongitude());
		location.setAccuracy(locationData.getAccuracy());

		final String poiName = locationData.getPoi();

		LocationMessageSendAction.getInstance()
			.sendLocationMessage(messageReceivers, location, poiName, new SendAction.ActionHandler() {
				@Override
				public void onError(final String errorMessage) {
					RuntimeUtil.runOnUiThread(() -> Toast.makeText(RecipientListBaseActivity.this, errorMessage, Toast.LENGTH_SHORT).show());
					if (hideUi) {
						finish();
					}
				}

				@Override
				public void onWarning(String warning, boolean continueAction) {
				}

				@Override
				public void onProgress(int progress, int total) {
				}

				@Override
				public void onCompleted() {
					startComposeActivityAsync(null);
				}
			});
	}

	@WorkerThread
	private void sendTextMessage(final MessageReceiver[] messageReceivers, final String text) {
		logger.debug("sendTextMessage");
		TextMessageSendAction.getInstance()
				.sendTextMessage(messageReceivers, text, new SendAction.ActionHandler() {
					@Override
					public void onError(final String errorMessage) {
						RuntimeUtil.runOnUiThread(() -> Toast.makeText(RecipientListBaseActivity.this, errorMessage, Toast.LENGTH_SHORT).show());
						if (hideUi) {
							finish();
						}
					}

					@Override
					public void onWarning(String warning, boolean continueAction) { }

					@Override
					public void onProgress(int progress, int total) { }

					@Override
					public void onCompleted() { }
				});
	}

	/*
	 * Dialog callbacks
	 */

	@Override
	public void onCancel(String tag, Object object) {
		if (hideUi) {
			finish();
		}
	}

	// return from ExpandableTextEntryDialog
	@Override
	public void onYes(String tag, Object data, String text) {
		this.captionText = text;

		if (data instanceof ArrayList) {
			prepareComposeIntent((ArrayList<Object>)data);
		} else {
			prepareComposeIntent(new ArrayList<>(Arrays.asList(data)));
		}
	}

	@Override
	public void onNo(String tag) {
		if (hideUi) {
			finish();
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode,
	                                       @NonNull String permissions[], @NonNull int[] grantResults) {
		if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
			switch (requestCode) {
				case REQUEST_READ_EXTERNAL_STORAGE:
					setupUI();
					break;
			}
		} else {
			switch (requestCode) {
				case REQUEST_READ_EXTERNAL_STORAGE:
					if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
						Toast.makeText(this, R.string.permission_storage_required, Toast.LENGTH_LONG).show();
					}
					finish();
					break;
			}
		}
	}

	public boolean isCalledFromExternalApp() {
		return false;
	}
}

/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2024 Threema GmbH
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
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.BadParcelableException;
import android.os.BaseBundle;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Pair;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.search.SearchBar;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;

import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

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
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;
import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.actions.LocationMessageSendAction;
import ch.threema.app.actions.SendAction;
import ch.threema.app.actions.TextMessageSendAction;
import ch.threema.app.adapters.FilterableListAdapter;
import ch.threema.app.asynctasks.AddContactRestrictionPolicy;
import ch.threema.app.asynctasks.BasicAddOrUpdateContactBackgroundTask;
import ch.threema.app.asynctasks.ContactResult;
import ch.threema.app.dialogs.CancelableHorizontalProgressDialog;
import ch.threema.app.dialogs.ExpandableTextEntryDialog;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.dialogs.TextWithCheckboxDialog;
import ch.threema.app.dialogs.ThreemaDialogFragment;
import ch.threema.app.fragments.DistributionListFragment;
import ch.threema.app.fragments.GroupListFragment;
import ch.threema.app.fragments.RecentListFragment;
import ch.threema.app.fragments.RecipientListFragment;
import ch.threema.app.fragments.UserListFragment;
import ch.threema.app.fragments.WorkUserListFragment;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.messagereceiver.SendingPermissionValidationResult;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.ConversationService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.UserService;
import ch.threema.app.ui.LongToast;
import ch.threema.app.ui.MediaItem;
import ch.threema.app.ui.SingleToast;
import ch.threema.app.ui.ThreemaSearchView;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ContactLookupUtil;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.GeoLocationUtil;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.LazyProperty;
import ch.threema.app.utils.MimeUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.NavigationUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.ShortcutUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.executor.BackgroundExecutor;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.data.repositories.ContactModelRepository;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.domain.protocol.csp.messages.file.FileData;
import ch.threema.domain.protocol.csp.messages.location.Poi;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.data.LocationDataModel;
import ch.threema.storage.models.data.MessageContentsType;
import java8.util.concurrent.CompletableFuture;

import static ch.threema.app.activities.SendMediaActivity.MAX_EDITABLE_FILES;
import static ch.threema.app.fragments.ComposeMessageFragment.MAX_FORWARDABLE_ITEMS;
import static ch.threema.app.ui.MediaItem.TYPE_IMAGE;
import static ch.threema.app.ui.MediaItem.TYPE_LOCATION;
import static ch.threema.app.ui.MediaItem.TYPE_TEXT;

public class RecipientListBaseActivity extends ThreemaToolbarActivity implements
    CancelableHorizontalProgressDialog.ProgressDialogClickListener,
    ExpandableTextEntryDialog.ExpandableTextEntryDialogClickListener,
    TextWithCheckboxDialog.TextWithCheckboxDialogClickListener,
    SearchView.OnQueryTextListener {

    private static final Logger logger = LoggingUtil.getThreemaLogger("RecipientListBaseActivity");

    private final static int FRAGMENT_RECENT = 0;
    private final static int FRAGMENT_USERS = 1;
    private final static int FRAGMENT_GROUPS = 2;
    private final static int FRAGMENT_DISTRIBUTION_LIST = 3;
    private final static int FRAGMENT_WORK_USERS = 4;
    private final static int NUM_FRAGMENTS = 5;

    private static final String DIALOG_TAG_MULTISEND = "multisend";
    private static final String DIALOG_TAG_FILECOPY = "filecopy";
    private static final String DIALOG_TAG_INTERNAL_MEDIA_FORWARDING = "forwarding_media";

    public static final String INTENT_DATA_MULTISELECT = "ms";
    public static final String INTENT_DATA_MULTISELECT_FOR_COMPOSE = "msi"; // allow multi select for composing a new message (automatically creates a distribution list)

    private static final int REQUEST_READ_EXTERNAL_STORAGE = 1;
    private static final String BUNDLE_QUERY_TEXT = "query";

    private TabLayout tabLayout;
    private UserGroupPagerAdapter userGroupPagerAdapter;
    private MenuItem searchMenuItem;
    private ThreemaSearchView searchView;
    private SearchBar searchBar;

    private boolean hideUi, hideRecents, multiSelect, multiSelectIdentities;
    private String captionText, queryText;
    private final List<MediaItem> mediaItems = new ArrayList<>();
    private final List<MessageReceiver> recipientMessageReceivers = new ArrayList<>();
    private final List<AbstractMessageModel> originalMessageModels = new ArrayList<>();
    private final List<Integer> tabs = new ArrayList<>(NUM_FRAGMENTS);
    private boolean isInternallyForwardingMediaFiles = false;

    private GroupService groupService;
    private ContactService contactService;
    private ConversationService conversationService;
    private DistributionListService distributionListService;
    private MessageService messageService;
    private FileService fileService;
    private UserService userService;
    private APIConnector apiConnector;
    private ContactModelRepository contactModelRepository;

    @NonNull
    private final LazyProperty<BackgroundExecutor> backgroundExecutor = new LazyProperty<>(BackgroundExecutor::new);

    private final Runnable copyExternalFilesRunnable = new Runnable() {
        @Override
        public void run() {
            for (int i = 0; i < mediaItems.size(); i++) {
                MediaItem mediaItem = mediaItems.get(i);

                if (TestUtil.isEmptyOrNull(mediaItem.getFilename())) {
                    mediaItem.setFilename(FileUtil.getFilenameFromUri(getContentResolver(), mediaItem));
                }

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
        int itemCount = userGroupPagerAdapter.getCount();

        // apply filter to all adapters
        for (int currentItem = 0; currentItem < itemCount; currentItem++) {
            Fragment fragment = userGroupPagerAdapter.getRegisteredFragment(currentItem);
            if (fragment != null) {
                FilterableListAdapter listAdapter = ((RecipientListFragment) fragment).getAdapter();
                // adapter can be null if it has not been initialized yet (runs in different thread)
                if (listAdapter != null) {
                    listAdapter.getFilter().filter(newText);
                }
            }
        }
        return true;
    }

    @Override
    public int getLayoutResource() {
        return R.layout.activity_recipientlist;
    }

    @NonNull
    private SendingPermissionValidationResult validateSendingPermission(@Nullable Object recipientModel) {
        if (recipientModel == null) {
            return new SendingPermissionValidationResult.Denied(R.string.invalid_cannot_send);
        }
        @Nullable final MessageReceiver messageReceiver = getMessageReceiver(recipientModel);
        if (messageReceiver != null) {
            return messageReceiver.validateSendingPermission();
        } else {
            return new SendingPermissionValidationResult.Denied(R.string.invalid_cannot_send);
        }
    }

    private void resetValues() {
        hideUi = hideRecents = false;
        mediaItems.clear();
        originalMessageModels.clear();
        tabs.clear();
        captionText = null;
    }

    @Override
    protected boolean initActivity(Bundle savedInstanceState) {
        if (!super.initActivity(savedInstanceState)) {
            return false;
        }

        UserService userService;
        try {
            this.contactService = serviceManager.getContactService();
            this.conversationService = serviceManager.getConversationService();
            this.groupService = serviceManager.getGroupService();
            this.distributionListService = serviceManager.getDistributionListService();
            this.messageService = serviceManager.getMessageService();
            this.fileService = serviceManager.getFileService();
            this.apiConnector = serviceManager.getAPIConnector();
            this.contactModelRepository = serviceManager.getModelRepositories().getContacts();
            userService = serviceManager.getUserService();
        } catch (Exception e) {
            logger.error("Exception", e);
            return false;
        }

        if (!userService.hasIdentity() || (ConfigUtils.isSerialLicensed() && !ConfigUtils.isSerialLicenseValid())) {
            logger.debug("No identity or not licensed");
            finish();
            System.exit(0);
        }

        onNewIntent(getIntent());

        return true;
    }

    private void setupUI() {
        tabLayout = findViewById(R.id.sliding_tabs);
        final ActionBar actionBar = getSupportActionBar();
        final CircularProgressIndicator progressBar = findViewById(R.id.progress_sending);

        ViewPager viewPager = findViewById(R.id.pager);
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

            for (MediaItem mediaItem : mediaItems) {
                String mimeType = mediaItem.getMimeType();
                if (mimeType != null) {
                    if (!hasMedia && !mimeType.startsWith("text/") && mediaItem.getType() != TYPE_LOCATION) {
                        hasMedia = true;
                    }
                }
            }

            if (hasMedia) {
                if (!ConfigUtils.requestReadStoragePermission(this, null, REQUEST_READ_EXTERNAL_STORAGE)) {
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
                    if (searchView != null) {
                        if (searchMenuItem != null) {
                            CharSequence query = searchView.getQuery();
                            if (TestUtil.isBlankOrNull(query)) {
                                invalidateOptionsMenu();
                                if (searchMenuItem.isActionViewExpanded()) {
                                    searchMenuItem.collapseActionView();
                                    onQueryTextChange(null);
                                }
                                searchView.setQuery("", false);
                                queryText = null;
                            } else {
                                searchMenuItem.getActionView().post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (!searchMenuItem.isActionViewExpanded()) {
                                            searchMenuItem.expandActionView();
                                        }
                                        searchView.setQuery(query, true);
                                    }
                                });
                                queryText = query.toString();
                            }
                        }
                    }
                }
            });

            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                actionBar.setTitle(R.string.title_choose_recipient);
                searchBar = (SearchBar) getToolbar();
                searchBar.setNavigationOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (searchView.isIconified()) {
                            finish();
                        } else {
                            searchView.setIconified(true);
                        }
                    }
                });
                searchBar.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        searchView.setIconified(false);
                    }
                });

                ConfigUtils.adjustSearchBarTextViewMargin(this, searchBar);
            }

            if (!hideRecents && !conversationService.hasConversations()) {
                //no conversation? show users tab as default
                viewPager.setCurrentItem(tabs.indexOf(FRAGMENT_USERS), true);
            }

            if (searchMenuItem != null) {
                searchMenuItem.setVisible(true);
            }
        } else {
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(false);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ColorDrawable transparentDrawable = new ColorDrawable(Color.TRANSPARENT);
                    actionBar.setTitle(null);
                    actionBar.setBackgroundDrawable(transparentDrawable);
                    getToolbar().setVisibility(View.GONE);
                    getWindow().setBackgroundDrawable(transparentDrawable);
                    getWindow().setStatusBarColor(Color.TRANSPARENT);
                    setTranslucent(true);
                } else {
                    actionBar.setTitle(R.string.app_name);
                    if (progressBar != null) {
                        progressBar.setVisibility(View.VISIBLE);
                    }
                }
            }

            tabLayout.setVisibility(View.GONE);

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
                this.multiSelectIdentities = intent.getBooleanExtra(INTENT_DATA_MULTISELECT_FOR_COMPOSE, false);
            } catch (BadParcelableException e) {
                logger.error("Exception", e);
            }

            String identity = IntentDataUtil.getIdentity(intent);
            int groupId = IntentDataUtil.getGroupId(intent);
            long distributionListID = IntentDataUtil.getDistributionListId(intent);

            if (TestUtil.isEmptyOrNull(identity) && groupId == -1 && distributionListID == -1) {
                // maybe a shortcut?
                String id = intent.getStringExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID);
                if (!TestUtil.isEmptyOrNull(id)) {
                    BaseBundle bundle = ShortcutUtil.getShareTargetExtrasFromShortcutId(id);
                    if (bundle != null) {
                        if (bundle.containsKey(ThreemaApplication.INTENT_DATA_CONTACT)) {
                            identity = bundle.getString(ThreemaApplication.INTENT_DATA_CONTACT);
                        } else if (bundle.containsKey(ThreemaApplication.INTENT_DATA_GROUP)) {
                            groupId = bundle.getInt(ThreemaApplication.INTENT_DATA_GROUP);
                        } else if (bundle.containsKey(ThreemaApplication.INTENT_DATA_DISTRIBUTION_LIST)) {
                            distributionListID = bundle.getLong(ThreemaApplication.INTENT_DATA_DISTRIBUTION_LIST);
                        }
                    }
                }
            }

            if (groupId > 0 || distributionListID > 0 || !TestUtil.isEmptyOrNull(identity)) {
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

                    if (type != null && (uri != null || MimeUtil.isText(type))) {
                        if (type.equals(MimeUtil.MIME_TYPE_EMAIL)) {
                            // email attachments
                            //  extract file type from uri path
                            String mimeType = FileUtil.getMimeTypeFromUri(this, uri);
                            if (!TestUtil.isEmptyOrNull(mimeType)) {
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
                        if (type.equals(MimeUtil.MIME_TYPE_TEXT)) {
                            String textIntent = getTextFromIntent(intent);
                            if (GeoLocationUtil.isValidGeoUri(uri)) {
                                mediaItems.add(new MediaItem(uri, MediaItem.TYPE_LOCATION, MimeUtil.MIME_TYPE_ANY, textIntent));
                            } else if (uri != null) {
                                // default to sending text as file
                                type = "x-text/plain";

                                String guessedType = getMimeTypeFromContentUri(uri);
                                if (guessedType != null) {
                                    type = guessedType;
                                }

                                addMediaItemSharedFromOtherApp(type, uri, textIntent);
                                if (textIntent != null) {
                                    captionText = textIntent;
                                }
                            } else if (textIntent != null) {
                                mediaItems.add(new MediaItem(null, TYPE_TEXT, MimeUtil.MIME_TYPE_TEXT, textIntent));
                            }
                        } else {
                            if (uri != null) {
                                // guess the correct mime type as ACTION_SEND may have been called with a generic mime type such as "image/*" which should be overridden
                                String guessedType = getMimeTypeFromContentUri(uri);
                                if (guessedType != null) {
                                    if (!guessedType.equals(MimeUtil.MIME_TYPE_DEFAULT) || type.equals("*")) {
                                        type = guessedType;
                                    }
                                }

                                String textIntent = getTextFromIntent(intent);
                                // don't add fixed caption to media item because we want it to be editable when sending a zip file (share chat)
                                if (type.equals(MimeUtil.MIME_TYPE_ZIP) && textIntent != null) {
                                    captionText = textIntent;
                                    mediaItems.add(new MediaItem(uri, MediaItem.TYPE_FILE, MimeUtil.MIME_TYPE_ZIP, textIntent));
                                } else { // if text was shared along with the media item, add that too
                                    addMediaItemSharedFromOtherApp(type, uri, textIntent);
                                }
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
                                    addMediaItemSharedFromOtherApp(type, uri1, null);
                                } else if (!TestUtil.isBlankOrNull(text)) {
                                    mediaItems.add(new MediaItem(uri, TYPE_TEXT, MimeUtil.MIME_TYPE_TEXT, text.toString()));
                                }
                            }
                        }

                        if (mediaItems.size() == 0) {
                            Toast.makeText(this, getString(R.string.invalid_data), Toast.LENGTH_LONG).show();
                            finish();
                        }
                    }

                    if (!TestUtil.isEmptyOrNull(identity)) {
                        prepareForwardingOrSharing(new ArrayList<>(Collections.singletonList(contactService.getByIdentity(identity))));
                    } else if (groupId > 0) {
                        prepareForwardingOrSharing(new ArrayList<>(Collections.singletonList(groupService.getById(groupId))));
                    } else if (distributionListID > 0) {
                        prepareForwardingOrSharing(new ArrayList<>(Collections.singletonList(distributionListService.getById(distributionListID))));
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
                            prepareComposeIntent(new ArrayList<>(Collections.singletonList(contactModel)), false);
                            return;
                        } else {
                            finish();
                            return;
                        }
                    }
                } else if (action.equals(Intent.ACTION_VIEW)) {
                    // called from action URL
                    Uri dataUri = intent.getData();

                    if (TestUtil.required(dataUri)) {
                        String scheme = dataUri.getScheme();
                        String host = dataUri.getHost();

                        if (scheme != null && host != null) {
                            if (
                                (BuildConfig.uriScheme.equals(scheme) && "compose".equals(host))
                                    ||
                                    ("https".equals(scheme) && (BuildConfig.actionUrl.equals(host) || BuildConfig.contactActionUrl.equals(host)) && "/compose".equals(dataUri.getPath()))
                            ) {
                                if (lockAppService != null && lockAppService.isLocked()) {
                                    finish();
                                    return;
                                }

                                String text = dataUri.getQueryParameter("text");
                                if (!TestUtil.isEmptyOrNull(text)) {
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
                                        prepareComposeIntent(new ArrayList<>(Collections.singletonList(contactModel)), false);
                                    }
                                }
                            } else {
                                // redirect to chat
                                MessageReceiver messageReceiver = IntentDataUtil.getMessageReceiverFromExtras(intent.getExtras(), contactService, groupService, distributionListService);
                                if (messageReceiver != null) {
                                    Intent composeIntent = IntentDataUtil.getComposeIntentForReceivers(this, new ArrayList<>(Collections.singletonList(messageReceiver)));
                                    startComposeActivity(composeIntent);
                                }
                                return;
                            }
                        }
                    }
                } else if (action.equals(Intent.ACTION_SEND_MULTIPLE) && intent.hasExtra(Intent.EXTRA_STREAM)) {
                    // called from other app with multiple media payload
                    String type = intent.getType();

                    ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                    if (uris != null) {
                        for (int i = 0; i < uris.size(); i++) {
                            if (i < MAX_FORWARDABLE_ITEMS) {
                                Uri uri = uris.get(i);
                                if (uri != null) {
                                    String mimeType = FileUtil.getMimeTypeFromUri(this, uri);
                                    if (mimeType == null) {
                                        mimeType = type;
                                    }
                                    addMediaItemSharedFromOtherApp(mimeType, uri, null);
                                }
                            } else {
                                Toast.makeText(getApplicationContext(), getString(R.string.max_selectable_media_exceeded, MAX_FORWARDABLE_ITEMS), Toast.LENGTH_LONG).show();
                                break;
                            }
                        }

                        if (!TestUtil.isEmptyOrNull(identity)) {
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

                    isInternallyForwardingMediaFiles = originalMessageModels.stream().allMatch(
                        messageModel ->
                            messageModel.getMessageContentsType() == MessageContentsType.FILE ||
                                messageModel.getMessageContentsType() == MessageContentsType.IMAGE ||
                                messageModel.getMessageContentsType() == MessageContentsType.VIDEO ||
                                messageModel.getMessageContentsType() == MessageContentsType.GIF ||
                                messageModel.getMessageContentsType() == MessageContentsType.AUDIO ||
                                messageModel.getMessageContentsType() == MessageContentsType.VOICE_MESSAGE ||
                                messageModel.getMessageContentsType() == MessageContentsType.CONTACT
                    );
                }
            }
        }

        setupUI();
    }

    @Nullable
    private String getMimeTypeFromContentUri(@NonNull Uri uri) {
        if (ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(uri.getScheme())) {
            // query database for correct mime type as ACTION_SEND may have been called with a generic mime type such as "image/*"
            String[] proj = {
                DocumentsContract.Document.COLUMN_MIME_TYPE
            };

            try (Cursor cursor = getContentResolver().query(uri, proj, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    String mimeType = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE));
                    if (!TestUtil.isEmptyOrNull(mimeType)) {
                        return mimeType;
                    }
                }
            } catch (Exception ignored) {
            }

            String filemame = FileUtil.getFilenameFromUri(getContentResolver(), uri);
            if (!TestUtil.isEmptyOrNull(filemame)) {
                String mimeType = FileUtil.getMimeTypeFromPath(filemame);
                if (!TestUtil.isEmptyOrNull(mimeType)) {
                    return mimeType;
                }
            }
        }
        return null;
    }

    private @Nullable String getTextFromIntent(Intent intent) {
        String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);
        String text = intent.getStringExtra(Intent.EXTRA_TEXT);

        if (TestUtil.isEmptyOrNull(text)) {
            return subject;
        }

        if (TestUtil.isEmptyOrNull(subject) || text.startsWith(subject)) {
            return text;
        }

        return subject + " - " + text;
    }

    private void addMediaItemSharedFromOtherApp(String mimeType, @NonNull Uri uri, @Nullable String caption) {
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
        } else if (ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(uri.getScheme())) {
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception e) {
                logger.info("Unable to take persistable uri permission");
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    uri = FileUtil.getFileUri(uri);
                }
            }
        }
        MediaItem mediaItem = new MediaItem(uri, mimeType, caption);

        // never create a voice message out of a shared audio file - fix default
        if (mediaItem.getType() == MediaItem.TYPE_VOICEMESSAGE) {
            mediaItem.setType(MediaItem.TYPE_FILE);
            mediaItem.setRenderingType(FileData.RENDERING_DEFAULT);
        }

        mediaItems.add(mediaItem);
    }

    @SuppressLint("StaticFieldLeak")
    private void addNewContact(final String identity) {
        final ContactModel contactModel = contactService.getByIdentity(identity);

        if (contactModel == null) {
            GenericProgressDialog.newInstance(R.string.creating_contact, R.string.please_wait).show(getSupportFragmentManager(), "pro");

            backgroundExecutor.get().execute(
                new BasicAddOrUpdateContactBackgroundTask(
                    identity,
                    ContactModel.AcquaintanceLevel.DIRECT,
                    userService.getIdentity(),
                    apiConnector,
                    contactModelRepository,
                    AddContactRestrictionPolicy.CHECK,
                    RecipientListBaseActivity.this,
                    null
                ) {
                    @Override
                    public void onFinished(ContactResult result) {
                        DialogUtil.dismissDialog(getSupportFragmentManager(), "pro", true);

                        ContactModel newContactModel = contactService.getByIdentity(identity);
                        if (newContactModel == null) {
                            View rootView = getWindow().getDecorView().findViewById(android.R.id.content);
                            Snackbar.make(rootView, R.string.contact_not_found, Snackbar.LENGTH_LONG).show();
                        } else {
                            prepareComposeIntent(new ArrayList<>(Collections.singletonList(newContactModel)), false);
                        }
                    }
                }
            );
        } else {
            prepareComposeIntent(new ArrayList<>(Collections.singletonList(contactModel)), false);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.action_compose_message_search, menu);

        this.searchMenuItem = menu.findItem(R.id.menu_action_search);
        this.searchView = (ThreemaSearchView) this.searchMenuItem.getActionView();
        if (ConfigUtils.isLandscape(this)) {
            this.searchView.setMaxWidth(Integer.MAX_VALUE);
        }

        if (this.searchView != null && !hideUi) {
            ConfigUtils.adjustSearchViewPadding(searchView);
            this.searchView.setQueryHint(getString(R.string.title_choose_recipient));
            this.searchView.setOnQueryTextListener(this);
            // Hide the hint of the search bar when the search view is opened to prevent it from
            // appearing on some devices
            this.searchView.setOnSearchClickListener(v -> {
                if (this.searchBar != null) {
                    this.searchBar.setHint("");
                }
            });
            // Show the hint of the search bar again when the search view is closed
            this.searchView.setOnCloseListener(() -> {
                if (this.searchBar != null) {
                    this.searchBar.setHint(R.string.title_choose_recipient);
                }
                return false;
            });
            if (!TestUtil.isEmptyOrNull(queryText)) {
                this.searchMenuItem.expandActionView();
                this.searchView.setIconified(false);
                this.searchView.setQuery(queryText, true);
            }
        } else {
            this.searchMenuItem.setVisible(false);
        }

        return true;
    }

    @Nullable
    private MessageReceiver getMessageReceiver(@Nullable Object model) {
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

    private void prepareComposeIntent(ArrayList<Object> recipients, boolean keepOriginalCaptions) {
        List<Object> validRecipients = filterRecipientModelsBySendingPermission(recipients);

        if (validRecipients.isEmpty()) {
            return;
        }

        ArrayList<MessageReceiver> messageReceivers = validRecipients
            .stream()
            .map(this::getMessageReceiver)
            .collect(Collectors.toCollection(ArrayList<MessageReceiver>::new));

        Intent intent = IntentDataUtil.getComposeIntentForReceivers(this, messageReceivers);

        if (!originalMessageModels.isEmpty()) {
            this.forwardMessages(messageReceivers.toArray(new MessageReceiver[0]), intent, keepOriginalCaptions);
        } else {
            this.sendSharedMedia(messageReceivers.toArray(new MessageReceiver[0]), intent);
        }
    }

    private void sendSharedMedia(final MessageReceiver[] messageReceivers, final Intent intent) {
        if (messageReceivers.length == 1 && mediaItems.size() == 1 && TYPE_TEXT == mediaItems.get(0).getType()) {
            intent.putExtra(ThreemaApplication.INTENT_DATA_TEXT, mediaItems.get(0).getTrimmedCaption());
            startComposeActivity(intent);
        } else if (messageReceivers.length > 1 || mediaItems.size() > 0) {
            messageService.sendMediaSingleThread(mediaItems, Arrays.asList(messageReceivers));
            startComposeActivity(intent);
        } else {
            startComposeActivity(intent);
        }
    }

    void forwardSingleMessage(final MessageReceiver[] messageReceivers, final int i, final Intent intent, final boolean keepOriginalCaptions) {
        final AbstractMessageModel messageModel = originalMessageModels.get(i);
        fileService.loadDecryptedMessageFile(messageModel, new FileService.OnDecryptedFileComplete() {
            @Override
            public void complete(File decryptedFile) {
                RuntimeUtil.runOnUiThread(() -> DialogUtil.updateProgress(getSupportFragmentManager(), DIALOG_TAG_MULTISEND, i));

                if (messageModel.isAvailable()) {
                    Uri uri = null;
                    if (decryptedFile != null) {
                        uri = Uri.fromFile(decryptedFile);
                    }

                    String caption = keepOriginalCaptions ? messageModel.getCaption() : captionText;

                    switch (messageModel.getType()) {
                        case IMAGE:
                            sendForwardedMedia(messageReceivers, uri, caption, TYPE_IMAGE, null, FileData.RENDERING_MEDIA, null, 0L);
                            break;
                        case VIDEO:
                            sendForwardedMedia(messageReceivers, uri, caption, MediaItem.TYPE_VIDEO, null, FileData.RENDERING_MEDIA, null, messageModel.getVideoData().getDuration() * DateUtils.SECOND_IN_MILLIS);
                            break;
                        case VOICEMESSAGE:
                            sendForwardedMedia(messageReceivers, uri, caption, MediaItem.TYPE_VOICEMESSAGE, MimeUtil.MIME_TYPE_AUDIO_AAC, FileData.RENDERING_MEDIA, null, messageModel.getAudioData().getDuration() * DateUtils.SECOND_IN_MILLIS);
                            break;
                        case FILE:
                            int mediaType = MediaItem.TYPE_FILE;
                            String mimeType = messageModel.getFileData().getMimeType();
                            int renderingType = messageModel.getFileData().getRenderingType();

                            if (messageModel.getFileData().getRenderingType() != FileData.RENDERING_DEFAULT) {
                                mediaType = MimeUtil.getMediaTypeFromMimeType(mimeType, uri);
                            }
                            sendForwardedMedia(messageReceivers, uri, caption, mediaType, mimeType, renderingType, messageModel.getFileData().getFileName(), messageModel.getFileData().getDurationMs());
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
                if (i < originalMessageModels.size() - 1) {
                    forwardSingleMessage(messageReceivers, i + 1, intent, keepOriginalCaptions);
                } else {
                    RuntimeUtil.runOnUiThread(() -> DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_MULTISEND, true));
                    startComposeActivity(intent);
                }
            }

            @Override
            public void error(String message) {
                RuntimeUtil.runOnUiThread(() -> {
                    SingleToast.getInstance().showLongText(getString(R.string.an_error_occurred_during_send));
                    DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_MULTISEND, true);
                });
                finish();
            }
        });
    }

    @UiThread
    private void forwardMessages(final MessageReceiver[] messageReceivers, final Intent intent, boolean keepOriginalCaptions) {
        CancelableHorizontalProgressDialog.newInstance(R.string.sending_messages, 0, originalMessageModels.size()).show(getSupportFragmentManager(), DIALOG_TAG_MULTISEND);

        forwardSingleMessage(messageReceivers, 0, intent, keepOriginalCaptions);
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

    @NonNull
    private List<Object> filterRecipientModelsBySendingPermission(@Nullable List<Object> recipientModels) {
        List<Pair<Object, SendingPermissionValidationResult>> recipientModelsWithValidationResults =
            (recipientModels == null ? Collections.emptyList() : recipientModels)
                .stream()
                .map(recipientModel -> Pair.create(recipientModel, validateSendingPermission(recipientModel)))
                .collect(Collectors.toList());

        List<Object> validRecipientModels = recipientModelsWithValidationResults.stream()
            .filter(recipientModelWithValidationResult -> recipientModelWithValidationResult.second.isValid())
            .map(recipientModelWithValidationResult -> recipientModelWithValidationResult.first)
            .collect(Collectors.toList());

        List<SendingPermissionValidationResult.Denied> deniedRecipients = recipientModelsWithValidationResults.stream()
            .filter(recipientModelWithValidationResult -> recipientModelWithValidationResult.second.isDenied())
            .map(recipientModelWithValidationResult -> (SendingPermissionValidationResult.Denied) recipientModelWithValidationResult.second)
            .collect(Collectors.toList());

        if (!deniedRecipients.isEmpty()) {
            // not all valid
            if (validRecipientModels.isEmpty() && deniedRecipients.size() == 1) {
                // all invalid, single selection
                deniedRecipients.stream()
                    .findFirst()
                    .map(SendingPermissionValidationResult.Denied::getErrorResId)
                    .ifPresent(errorResId ->
                        RuntimeUtil.runOnUiThread(() -> SingleToast.getInstance().showLongText(getString(errorResId)))
                    );
            } else if (validRecipientModels.isEmpty()) {
                // all invalid, multi selection
                RuntimeUtil.runOnUiThread(() ->
                    SingleToast.getInstance().showLongText(getString(R.string.invalid_all_cannot_send))
                );
            } else {
                // partial valid
                RuntimeUtil.runOnUiThread(() ->
                    SingleToast.getInstance().showLongText(getString(R.string.invalid_some_cannot_send))
                );
            }
        }
        logger.info("Valid recipient models: {}", validRecipientModels.size());
        return validRecipientModels;
    }

    @SuppressLint("StaticFieldLeak")
    private void forwardImagesOrVideosInternally(final List<Object> recipients, final String combinedRecipientName) {

        if (originalMessageModels.size() > MAX_EDITABLE_FILES) {
            // Fallback to mixed media dialog logic if maximum of editable media files was exceeded
            showMixedMediaForwardDialog(
                recipients,
                combinedRecipientName
            );
            return;
        }

        //noinspection deprecation
        new AsyncTask<Void, Void, Void>() {

            @Override
            protected void onPreExecute() {
                GenericProgressDialog.newInstance(
                    0,
                    R.string.please_wait
                ).show(getSupportFragmentManager(), DIALOG_TAG_INTERNAL_MEDIA_FORWARDING);
            }

            @Override
            protected Void doInBackground(Void... voids) {

                fileService.loadDecryptedMessageFiles(
                    originalMessageModels,
                    new FileService.OnDecryptedFilesComplete() {
                        @Override
                        public void complete(ArrayList<Uri> uris) {

                            ArrayList<MediaItem> mappedMediaItems = new ArrayList<>();

                            for (int i = 0; i < uris.size(); i++) {

                                final AbstractMessageModel originalMessageModel = originalMessageModels.get(i);
                                final Uri fileUri = uris.get(i);

                                if (fileUri == null || originalMessageModel == null || !originalMessageModel.isAvailable()) {
                                    continue;
                                }

                                final String mimeType = MimeUtil.getMimeTypeFromMessageModel(originalMessageModel);
                                final int mediaType = MimeUtil.getMediaTypeFromMimeType(mimeType, fileUri);

                                final MediaItem mediaItem = new MediaItem(
                                    fileUri,
                                    mediaType,
                                    mimeType,
                                    originalMessageModel.getCaption()
                                );

                                final @Nullable String existingFilename = originalMessageModel.getFileData().getFileName();
                                if (!TestUtil.isBlankOrNull(existingFilename)) {
                                    mediaItem.setFilename(existingFilename);
                                }

                                if (originalMessageModel.getMessageContentsType() == MessageContentsType.IMAGE) {
                                    if (originalMessageModel.getFileData().getRenderingType() == FileData.RENDERING_DEFAULT) {
                                        mediaItem.setImageScale(PreferenceService.ImageScale_SEND_AS_FILE);
                                    }
                                } else if (originalMessageModel.getMessageContentsType() == MessageContentsType.VIDEO) {
                                    if (originalMessageModel.getFileData().getRenderingType() == FileData.RENDERING_DEFAULT) {
                                        mediaItem.setVideoSize(PreferenceService.VideoSize_SEND_AS_FILE);
                                    }
                                } else if (originalMessageModel.getMessageContentsType() == MessageContentsType.VOICE_MESSAGE) {
                                    mediaItem.setDurationMs(
                                        originalMessageModel.getFileData().getDurationMs()
                                    );
                                }

                                mappedMediaItems.add(mediaItem);
                            }

                            if (mappedMediaItems.isEmpty()) {
                                return;
                            }

                            Intent intent = IntentDataUtil.addMessageReceiversToIntent(
                                new Intent(RecipientListBaseActivity.this, SendMediaActivity.class),
                                recipientMessageReceivers.toArray(new MessageReceiver[0])
                            );
                            intent.putExtra(SendMediaActivity.EXTRA_MEDIA_ITEMS, mappedMediaItems);
                            intent.putExtra(ThreemaApplication.INTENT_DATA_TEXT, combinedRecipientName);
                            startActivityForResult(intent, ThreemaActivity.ACTIVITY_ID_SEND_MEDIA);
                        }

                        @Override
                        public void error(String message) {
                            RuntimeUtil.runOnUiThread(
                                () -> LongToast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show()
                            );
                        }
                    }
                );

                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                DialogUtil.dismissDialog(
                    getSupportFragmentManager(),
                    DIALOG_TAG_INTERNAL_MEDIA_FORWARDING,
                    true
                );
            }

        }.execute();
    }

    @NonNull
    private String combineRecipientNames(final List<Object> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return "";
        }
        StringBuilder recipientNameBuilder = new StringBuilder();
        for (Object recipientModel : recipients) {
            if (recipientNameBuilder.length() > 0) {
                recipientNameBuilder.append(", ");
            }
            if (recipientModel instanceof ContactModel) {
                recipientNameBuilder.append(NameUtil.getDisplayNameOrNickname((ContactModel) recipientModel, true));
            } else if (recipientModel instanceof GroupModel) {
                recipientNameBuilder.append(NameUtil.getDisplayName((GroupModel) recipientModel, this.groupService));
            } else if (recipientModel instanceof DistributionListModel) {
                recipientNameBuilder.append(NameUtil.getDisplayName((DistributionListModel) recipientModel, this.distributionListService));
            }
        }
        return recipientNameBuilder.toString();
    }

    public void prepareForwardingOrSharing(final @Nullable ArrayList<Object> recipients) {

        List<Object> filteredRecipientModels = filterRecipientModelsBySendingPermission(recipients);

        if (filteredRecipientModels.isEmpty()) {
            return;
        }

        recipientMessageReceivers.clear();
        recipientMessageReceivers.addAll(
            filteredRecipientModels.stream().map(this::getMessageReceiver).collect(Collectors.toList())
        );
        final String combinedRecipientName = combineRecipientNames(filteredRecipientModels);

        if (isInternallyForwardingMediaFiles) {
            forwardImagesOrVideosInternally(filteredRecipientModels, combinedRecipientName);
            return;
        }

        if (!mediaItems.isEmpty() || !originalMessageModels.isEmpty()) {

            // Shared from outside the app
            // Also false if it is a file containing only text
            final boolean isSingleSharedTextMessage = mediaItems.size() == 1
                && MimeUtil.isText(mediaItems.get(0).getMimeType())
                && !MimeUtil.isFileType(mediaItems.get(0).getType());

            // Forwarded from inside Threema
            final boolean isSingleThreemaTextMessage = originalMessageModels.size() == 1
                && originalMessageModels.get(0).getType() == MessageType.TEXT;

            if (!(isSingleSharedTextMessage || isSingleThreemaTextMessage)) {

                if (originalMessageModels.size() > 0) {
                    // forwarded content of any type
                    String presetCaption = null;
                    boolean expandable = false;
                    boolean hasCaptions = false;

                    if (originalMessageModels.size() == 1) {
                        presetCaption = originalMessageModels.get(0).getCaption();
                        if (originalMessageModels.get(0).getType() == MessageType.VIDEO ||
                            originalMessageModels.get(0).getType() == MessageType.IMAGE ||
                            originalMessageModels.get(0).getType() == MessageType.VOICEMESSAGE ||
                            originalMessageModels.get(0).getType() == MessageType.FILE) {
                            expandable = true;
                        }
                    } else {
                        for (AbstractMessageModel messageModel : originalMessageModels) {
                            if (messageModel.getCaption() != null && !TextUtils.isEmpty(messageModel.getCaption())) {
                                hasCaptions = true;
                                break;
                            }
                        }
                    }

                    ThreemaDialogFragment alertDialog;
                    if (!expandable) {
                        alertDialog = TextWithCheckboxDialog.newInstance(
                            getString(R.string.forward_message),
                            getString(R.string.really_forward, combinedRecipientName),
                            hasCaptions ? R.string.forward_captions : 0,
                            R.string.send,
                            R.string.cancel
                        );
                    } else {
                        alertDialog = ExpandableTextEntryDialog.newInstance(
                            getString(R.string.really_forward, combinedRecipientName),
                            R.string.add_caption_hint,
                            presetCaption,
                            R.string.send,
                            R.string.cancel,
                            true
                        );
                    }
                    alertDialog.setData(recipients);
                    alertDialog.show(getSupportFragmentManager(), null);

                } else {
                    // content shared by external apps may be referred to by content URIs which will not survive this activity. so in order to be able to use them later we have to copy these files to a local directory first
                    GenericProgressDialog.newInstance(
                        R.string.importing_files,
                        R.string.please_wait
                    ).show(getSupportFragmentManager(), DIALOG_TAG_FILECOPY);
                    try {
                        CompletableFuture
                            .runAsync(copyExternalFilesRunnable, Executors.newSingleThreadExecutor())
                            .thenRunAsync(() -> {
                                DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_FILECOPY, true);

                                boolean containsGeoUri = false;
                                for (MediaItem mediaItem : mediaItems) {
                                    if (mediaItem != null && mediaItem.getUri() != null && "geo".equals(mediaItem.getUri().getScheme())) {
                                        containsGeoUri = true;
                                        break;
                                    }
                                }

                                if (mediaItems.size() <= MAX_EDITABLE_FILES && !containsGeoUri) { // use send media activity for a reasonable number of files

                                    Intent intent = IntentDataUtil.addMessageReceiversToIntent(new Intent(RecipientListBaseActivity.this, SendMediaActivity.class), recipientMessageReceivers.toArray(new MessageReceiver[0]));
                                    intent.putExtra(SendMediaActivity.EXTRA_MEDIA_ITEMS, (ArrayList<MediaItem>) mediaItems);
                                    intent.putExtra(ThreemaApplication.INTENT_DATA_TEXT, combinedRecipientName);
                                    startActivityForResult(intent, ThreemaActivity.ACTIVITY_ID_SEND_MEDIA);

                                } else {
                                    // mixed media
                                    showMixedMediaForwardDialog(
                                        filteredRecipientModels,
                                        combinedRecipientName
                                    );
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
        prepareComposeIntent(recipients, false);
    }

    private void showMixedMediaForwardDialog(final List<Object> recipients, final String combinedRecipientName) {

        ExpandableTextEntryDialog alertDialog;
        if (hideUi) {
            alertDialog = ExpandableTextEntryDialog.newInstance(
                getString(R.string.app_name),
                getString(R.string.really_send, combinedRecipientName),
                R.string.add_caption_hint,
                captionText,
                R.string.send,
                R.string.cancel,
                false
            );
        } else {
            alertDialog = ExpandableTextEntryDialog.newInstance(
                getString(R.string.really_send, combinedRecipientName),
                R.string.add_caption_hint,
                captionText,
                R.string.send,
                R.string.cancel,
                false
            );
        }
        alertDialog.setData(recipients);
        alertDialog.show(getSupportFragmentManager(), null);
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            queryText = savedInstanceState.getString(BUNDLE_QUERY_TEXT, null);
        }
    }

    @Override
    public void onUserInteraction() {
        logger.debug("onUserInteraction");
        super.onUserInteraction();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
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

    public void onQueryResultChanged(RecipientListFragment recipientListFragment, int count) {
        int tabPosition = userGroupPagerAdapter.registeredFragments.indexOfValue(recipientListFragment);
        TabLayout.Tab tab = tabLayout.getTabAt(tabPosition);

        if (tab != null) {
            if (count > 0) {
                tab.getOrCreateBadge().setNumber(count);
                tab.getBadge().setBackgroundColor(ConfigUtils.getColorFromAttribute(this, R.attr.colorPrimary));
                tab.getBadge().setVisible(true);
            } else {
                if (tab.getBadge() != null) {
                    tab.getBadge().setVisible(false);
                }
            }
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

            boolean allowMultiSelectForCompose = false;

            switch (tabs.get(position)) {
                case FRAGMENT_USERS:
                    fragment = new UserListFragment();
                    allowMultiSelectForCompose = multiSelectIdentities;
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
                    allowMultiSelectForCompose = multiSelectIdentities;
                    break;
            }

            if (fragment != null) {
                Bundle args = new Bundle();
                args.putBoolean(RecipientListFragment.ARGUMENT_MULTI_SELECT, multiSelect);
                args.putBoolean(RecipientListFragment.ARGUMENT_MULTI_SELECT_FOR_COMPOSE, allowMultiSelectForCompose);
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
    private void sendForwardedMedia(final MessageReceiver[] messageReceivers, final Uri uri, final String caption, final int type, @Nullable final String mimeType, @FileData.RenderingType final int renderingType, final String filename, long durationMs) {
        final MediaItem mediaItem = new MediaItem(uri, type);
        if (mimeType != null) {
            mediaItem.setMimeType(mimeType);
        }
        if (renderingType != -1) {
            mediaItem.setRenderingType(renderingType);
        }
        mediaItem.setCaption(caption);
        if (!TestUtil.isEmptyOrNull(filename)) {
            mediaItem.setFilename(filename);
        }
        if (renderingType == FileData.RENDERING_MEDIA) {
            if (type == MediaItem.TYPE_VIDEO) {
                // do not re-transcode forwarded videos
                mediaItem.setVideoSize(PreferenceService.VideoSize_ORIGINAL);
            } else if (type == TYPE_IMAGE) {
                // do not scale forwarded images
                mediaItem.setImageScale(PreferenceService.ImageScale_ORIGINAL);
            } else if (type == MediaItem.TYPE_VOICEMESSAGE) {
                mediaItem.setDurationMs(durationMs);
            }
        }
        messageService.sendMediaSingleThread(Collections.singletonList(mediaItem), Arrays.asList(messageReceivers));
    }

    @WorkerThread
    private void sendLocationMessage(final MessageReceiver[] messageReceivers, LocationDataModel locationData) {
        final Location location = new Location("");
        location.setLatitude(locationData.latitude);
        location.setLongitude(locationData.longitude);
        location.setAccuracy((float) locationData.accuracyOrFallback);
        final @Nullable Poi poi = locationData.poi;
        @Nullable String poiName = null;
        if (poi != null) {
            poiName = poi.getName();
        }

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
                    if (!TestUtil.isEmptyOrNull(errorMessage)) {
                        RuntimeUtil.runOnUiThread(() -> Toast.makeText(RecipientListBaseActivity.this, errorMessage, Toast.LENGTH_SHORT).show());
                    }
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
                }
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
            if (!TestUtil.isEmptyOrNull(text)) {
                for (MediaItem mediaItem : mediaItems) {
                    mediaItem.setCaption(text);
                }
            }
            prepareComposeIntent((ArrayList<Object>) data, false);
        }
    }

    // return from TextWithCheckboxDialog
    @Override
    public void onYes(String tag, Object data, boolean checked) {
        if (data instanceof ArrayList) {
            prepareComposeIntent((ArrayList<Object>) data, checked);
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
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
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

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (searchView != null) {
            CharSequence query = searchView.getQuery();
            outState.putString(BUNDLE_QUERY_TEXT, TextUtils.isEmpty(query) ? null : query.toString());
        }
        super.onSaveInstanceState(outState);
    }
}

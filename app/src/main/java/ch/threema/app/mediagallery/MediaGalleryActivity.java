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

package ch.threema.app.mediagallery;

import static ch.threema.app.utils.RecyclerViewUtil.thumbScrollerPopupStyle;
import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;
import static org.koin.core.parameter.ParametersHolderKt.parametersOf;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.view.ActionMode;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.GridLayoutManager;

import com.google.android.material.chip.Chip;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import org.jetbrains.annotations.Contract;
import org.koin.android.compat.ViewModelCompat;
import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import ch.threema.app.AppConstants;
import ch.threema.app.R;
import ch.threema.app.activities.MediaViewerActivity;
import ch.threema.app.activities.ThreemaActivity;
import ch.threema.app.activities.ThreemaToolbarActivity;
import ch.threema.app.di.DependencyContainer;
import ch.threema.app.dialogs.CancelableHorizontalProgressDialog;
import ch.threema.app.dialogs.ExpandableTextEntryDialog;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.fragments.ComposeMessageFragment;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.FileService;
import ch.threema.app.ui.EmptyRecyclerView;
import ch.threema.app.ui.EmptyView;
import ch.threema.app.ui.InsetSides;
import ch.threema.app.ui.MediaGridItemDecoration;
import ch.threema.app.restrictions.AppRestrictionUtil;
import ch.threema.app.ui.SpacingValues;
import ch.threema.app.ui.ViewExtensionsKt;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.data.MessageContentsType;
import me.zhanghai.android.fastscroll.FastScroller;
import me.zhanghai.android.fastscroll.FastScrollerBuilder;

public class MediaGalleryActivity extends ThreemaToolbarActivity implements
    MediaGalleryAdapter.OnClickItemListener,
    GenericAlertDialog.DialogClickListener,
    ExpandableTextEntryDialog.ExpandableTextEntryDialogClickListener {
    private static final Logger logger = LoggingUtil.getThreemaLogger("MediaGalleryActivity");

    private static final String DIALOG_TAG_DECRYPTING_MESSAGES = "dialog_decrypting_messages";

    private MessageReceiver<?> messageReceiver;
    private MediaGalleryAdapter mediaGalleryAdapter;
    private MediaGalleryViewModel viewModel;
    protected GridLayoutManager gridLayoutManager;
    private EmptyRecyclerView recyclerView;
    private EmptyView emptyView;
    protected FastScroller fastScroller;
    private Chip chipContentTypeImage;
    private Chip chipContentTypeGIF;
    private Chip chipContentTypeVideo;
    private Chip chipContentTypeVoiceMessage;
    private Chip chipContentTypeAudio;
    private Chip chipContentTypeFile;
    private ActionMode actionMode = null;
    private AbstractMessageModel initialMessageModel = null;

    @NonNull
    private final DependencyContainer dependencies = KoinJavaComponent.get(DependencyContainer.class);

    private static final String DELETE_MESSAGES_CONFIRM_TAG = "reallydelete";
    private static final String DIALOG_TAG_DELETING_MEDIA = "dmm";
    private static final int PERMISSION_REQUEST_SAVE_MESSAGE = 88;

    @Override
    public int getLayoutResource() {
        return R.layout.activity_media_gallery;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);
    }

    @Override
    protected void handleDeviceInsets() {
        super.handleDeviceInsets();
        ViewExtensionsKt.applyDeviceInsetsAsPadding(
            findViewById(R.id.content_container),
            InsetSides.horizontal()
        );

        ViewExtensionsKt.applyDeviceInsetsAsPadding(
            findViewById(R.id.item_list),
            InsetSides.bottom(),
            SpacingValues.bottom(R.dimen.grid_unit_x2)
        );
    }

    @Override
    protected boolean initActivity(Bundle savedInstanceState) {
        logger.debug("initActivity");

        if (!super.initActivity(savedInstanceState)) {
            return false;
        }

        final @Nullable String title = processIntent(getIntent());

        viewModel = ViewModelCompat.getViewModel(
            this,
            MediaGalleryViewModel.class,
            null,
            null,
            () -> parametersOf(messageReceiver)
        );

        ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) {
            logger.debug("no action bar");
            finish();
            return false;
        }
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle(title);

        chipContentTypeImage = findViewById(R.id.content_type_image);
        chipContentTypeGIF = findViewById(R.id.content_type_gif);
        chipContentTypeVideo = findViewById(R.id.content_type_video);
        chipContentTypeVoiceMessage = findViewById(R.id.content_type_voice_message);
        chipContentTypeAudio = findViewById(R.id.content_type_audio);
        chipContentTypeFile = findViewById(R.id.content_type_file);

        gridLayoutManager = new GridLayoutManager(this, ConfigUtils.isLandscape(this) ? 5 : 3);
        recyclerView = findViewById(R.id.item_list);

        final int borderSize = (int) ((float) getResources().getDimensionPixelSize(R.dimen.grid_spacing) * 1.5F);
        final int cornerRadius = getResources().getDimensionPixelSize(R.dimen.cardview_border_radius);
        final ViewOutlineProvider roundedCornersOutlineProvider = new ViewOutlineProvider() {
            @Override
            public void getOutline(@NonNull View view, @NonNull Outline outline) {
                outline.setRoundRect(borderSize, 0, view.getWidth() - borderSize, view.getHeight() + cornerRadius, cornerRadius);
            }
        };

        recyclerView.setOutlineProvider(roundedCornersOutlineProvider);
        recyclerView.setClipToOutline(true);
        recyclerView.setLayoutManager(gridLayoutManager);
        recyclerView.addItemDecoration(new MediaGridItemDecoration(getResources().getDimensionPixelSize(R.dimen.grid_spacing)));

        emptyView = new EmptyView(this);
        emptyView.setColorsInt(
            ConfigUtils.getColorFromAttribute(this, android.R.attr.colorBackground),
            ConfigUtils.getColorFromAttribute(this, R.attr.colorOnBackground)
        );
        emptyView.setup(getString(R.string.no_media_found_generic));
        ((ViewGroup) recyclerView.getParent()).addView(emptyView);
        recyclerView.setEmptyView(emptyView);
        emptyView.setLoading(true);
        mediaGalleryAdapter = new MediaGalleryAdapter(this, this, messageReceiver, gridLayoutManager.getSpanCount());
        recyclerView.setAdapter(mediaGalleryAdapter);

        if (fastScroller == null) {
            Drawable thumbDrawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_thumbscroller, getTheme());
            fastScroller = new FastScrollerBuilder(recyclerView)
                .setThumbDrawable(Objects.requireNonNull(thumbDrawable))
                .setTrackDrawable(Objects.requireNonNull(AppCompatResources.getDrawable(this, R.drawable.fastscroll_track_media)))
                .setPopupStyle(thumbScrollerPopupStyle)
                .setPopupTextProvider((view, position) -> {
                    int firstVisible = gridLayoutManager.findFirstCompletelyVisibleItemPosition();
                    if (firstVisible >= 0) {
                        AbstractMessageModel item = mediaGalleryAdapter.getItemAtPosition(firstVisible);
                        if (item != null) {
                            return LocaleUtil.formatDateRelative(item.getCreatedAt().getTime());
                        }
                    }
                    return MediaGalleryActivity.this.getString(R.string.unknown);
                })
                .build();
        }

        setObservers();

        if (initialMessageModel != null) {
            recyclerView.post(() -> {
                for (int position = 0; position < mediaGalleryAdapter.getItemCount(); position++) {
                    AbstractMessageModel messageModel = mediaGalleryAdapter.getItemAtPosition(position);
                    if (messageModel != null && messageModel.getId() == initialMessageModel.getId()) {
                        gridLayoutManager.scrollToPosition(position);
                        break;
                    }
                }
                initialMessageModel = null;
            });
        }
        return true;
    }

    private void setObservers() {

        viewModel.getMessages().observe(this, abstractMessageModels -> {
            emptyView.setLoading(false);
            mediaGalleryAdapter.setItems(abstractMessageModels);
            if (actionMode != null) {
                actionMode.invalidate();
            }
        });

        viewModel.getSelectedContentTypes().observe(this, selectedContentTypes -> {
            setChipCheckedSilently(chipContentTypeImage, MessageContentsType.IMAGE, selectedContentTypes);
            setChipCheckedSilently(chipContentTypeGIF, MessageContentsType.GIF, selectedContentTypes);
            setChipCheckedSilently(chipContentTypeVideo, MessageContentsType.VIDEO, selectedContentTypes);
            setChipCheckedSilently(chipContentTypeVoiceMessage, MessageContentsType.VOICE_MESSAGE, selectedContentTypes);
            setChipCheckedSilently(chipContentTypeAudio, MessageContentsType.AUDIO, selectedContentTypes);
            setChipCheckedSilently(chipContentTypeFile, MessageContentsType.FILE, selectedContentTypes);
        });
    }

    /**
     * We want our ViewModel to hold the source of truth about which chip in the UI is currently checked.
     * Unluckily the Chip-View is very stateful. To prevent a listener-observer-loop we have to temporarily
     * remove the OnCheckedChangeListener before applying new changes to these states from the ViewModel.
     */
    private void setChipCheckedSilently(@NonNull Chip chip, @MessageContentsType int type, @NonNull Set<Integer> selectedContentTypes) {
        chip.setOnCheckedChangeListener(null);
        chip.setChecked(selectedContentTypes.contains(type));
        chip.setOnCheckedChangeListener(onCheckedChangeContentType(type));
    }

    @NonNull
    @Contract(pure = true)
    private CompoundButton.OnCheckedChangeListener onCheckedChangeContentType(@MessageContentsType int contentType) {
        return (buttonView, isChecked) -> {
            viewModel.toggleSelectedContentType(contentType);
        };
    }

    private void showInChat() {
        if (mediaGalleryAdapter.getCheckedItemsCount() != 1) {
            return;
        }
        startActivityForResult(IntentDataUtil.getJumpToMessageIntent(this, mediaGalleryAdapter.getCheckedItemAt(0)), ThreemaActivity.ACTIVITY_ID_COMPOSE_MESSAGE);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return true;
    }

    @Nullable
    private String processIntent(@NonNull Intent intent) {
        String actionBarTitle;

        if (intent.hasExtra(AppConstants.INTENT_DATA_GROUP_DATABASE_ID)) {
            var groupService = dependencies.getGroupService();
            long groupId = intent.getLongExtra(AppConstants.INTENT_DATA_GROUP_DATABASE_ID, 0L);
            GroupModel groupModel = groupService.getById(groupId);
            messageReceiver = groupService.createReceiver(groupModel);
            actionBarTitle = groupModel.getName();
        } else if (intent.hasExtra(AppConstants.INTENT_DATA_DISTRIBUTION_LIST_ID)) {
            var distributionListService = dependencies.getDistributionListService();
            DistributionListModel distributionListModel = distributionListService.getById(intent.getLongExtra(AppConstants.INTENT_DATA_DISTRIBUTION_LIST_ID, 0));
            try {
                messageReceiver = distributionListService.createReceiver(distributionListModel);
            } catch (Exception e) {
                logger.error("Exception", e);
            }
            actionBarTitle = distributionListModel.getName();
        } else {
            String identity = intent.getStringExtra(AppConstants.INTENT_DATA_CONTACT);
            if (identity == null) {
                finish();
            }
            var contactService = dependencies.getContactService();
            ContactModel contactModel = contactService.getByIdentity(identity);
            messageReceiver = contactService.createReceiver(contactModel);
            actionBarTitle = NameUtil.getDisplayNameOrNickname(contactModel, true);
        }


        String type = IntentDataUtil.getAbstractMessageType(intent);
        int id = IntentDataUtil.getAbstractMessageId(intent);

        if (type != null && id != 0) {
            initialMessageModel = dependencies.getMessageService().getMessageModelFromId(id, type);
        }

        return actionBarTitle;
    }

    private void selectAllMessages() {
        if (mediaGalleryAdapter != null) {
            mediaGalleryAdapter.selectAll();
            if (actionMode != null) {
                if (mediaGalleryAdapter.getCheckedItemsCount() == 0) {
                    actionMode.finish();
                } else {
                    actionMode.invalidate();
                }
            }
        }
    }

    private void discardMessages() {
        List<AbstractMessageModel> selectedMessages = mediaGalleryAdapter.getCheckedItems();
        GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.really_delete_message_title, String.format(getString(R.string.really_delete_media), selectedMessages.size()), R.string.delete_message, R.string.cancel);
        dialog.setData(selectedMessages);
        dialog.show(getSupportFragmentManager(), DELETE_MESSAGES_CONFIRM_TAG);
    }

    private void saveMessages() {
        if (ConfigUtils.requestWriteStoragePermissions(this, null, PERMISSION_REQUEST_SAVE_MESSAGE)) {
            dependencies.getFileService().saveMedia(this, recyclerView, new CopyOnWriteArrayList<>(mediaGalleryAdapter.getCheckedItems()), true);
            actionMode.finish();
        }
    }

    @Override
    public void onYes(String tag, Object data, String text) {
        List<Uri> uris = (List<Uri>) data;
        dependencies.getMessageService().shareMediaMessages(this,
            new ArrayList<>(mediaGalleryAdapter.getCheckedItems()),
            new ArrayList<>(uris), text);
    }

    @Override
    public void onNo(String tag) {
        // Nothing to do here
    }

    @SuppressLint("StaticFieldLeak")
    private void reallyDiscardMessages(final CopyOnWriteArrayList<AbstractMessageModel> selectedMessages) {
        new AsyncTask<Void, Integer, List<AbstractMessageModel>>() {
            boolean cancelled = false;

            @Override
            protected void onPreExecute() {
                if (selectedMessages.size() > 10) {
                    CancelableHorizontalProgressDialog dialog = CancelableHorizontalProgressDialog.newInstance(
                        R.string.deleting_messages,
                        R.string.cancel,
                        selectedMessages.size()
                    );
                    dialog.setOnCancelListener((dialog1, which) -> cancelled = true);
                    dialog.show(getSupportFragmentManager(), DIALOG_TAG_DELETING_MEDIA);
                }
            }

            @Override
            protected List<AbstractMessageModel> doInBackground(Void... params) {
                int i = 0;
                List<AbstractMessageModel> deletedMessages = new ArrayList<>();
                Iterator<AbstractMessageModel> checkedItemsIterator = selectedMessages.iterator();
                while (checkedItemsIterator.hasNext() && !cancelled) {
                    publishProgress(i++);
                    try {
                        final AbstractMessageModel messageModel = checkedItemsIterator.next();

                        if (messageModel != null) {
                            deletedMessages.add(messageModel);
                            dependencies.getMessageService().remove(messageModel);
                        }
                    } catch (Exception e) {
                        logger.error("Exception", e);
                    }
                }
                return deletedMessages;
            }

            @Override
            protected void onPostExecute(List<AbstractMessageModel> deletedMessages) {
                mediaGalleryAdapter.removeItems(deletedMessages);
                DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_DELETING_MEDIA, true);
                String text = ConfigUtils.getSafeQuantityString(recyclerView.getContext(), R.plurals.message_deleted, deletedMessages.size(), deletedMessages.size());
                Snackbar.make(recyclerView, text, Snackbar.LENGTH_LONG).show();
                if (actionMode != null) {
                    actionMode.finish();
                }
            }

            @Override
            protected void onProgressUpdate(Integer... index) {
                DialogUtil.updateProgress(getSupportFragmentManager(), DIALOG_TAG_DELETING_MEDIA, index[0] + 1);
            }
        }.execute();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        final int topmost;
        if (gridLayoutManager != null) {
            topmost = gridLayoutManager.findFirstCompletelyVisibleItemPosition();
        } else {
            topmost = 0;
        }

        super.onConfigurationChanged(newConfig);

        if (recyclerView != null) {
            recyclerView.post(() -> {
                if (gridLayoutManager != null) {
                    gridLayoutManager.setSpanCount(ConfigUtils.isLandscape(MediaGalleryActivity.this) ? 5 : 3);
                    gridLayoutManager.scrollToPosition(topmost);
                }
            });
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

    private void hideProgressBar(final CircularProgressIndicator progressBar) {
        if (progressBar != null) {
            RuntimeUtil.runOnUiThread(() -> progressBar.setVisibility(View.GONE));
        }
    }

    private void showProgressBar(final CircularProgressIndicator progressBar) {
        if (progressBar != null) {
            RuntimeUtil.runOnUiThread(() -> progressBar.setVisibility(View.VISIBLE));
        }
    }

    public void decryptAndShow(final AbstractMessageModel m, final View v, final CircularProgressIndicator progressBar) {
        showProgressBar(progressBar);
        var fileService = dependencies.getFileService();
        fileService.loadDecryptedMessageFile(m, new FileService.OnDecryptedFileComplete() {
            @Override
            public void complete(File decodedFile) {
                hideProgressBar(progressBar);
                dependencies.getMessageService().viewMediaMessage(getApplicationContext(), m, fileService.getShareFileUri(decodedFile, null));
            }

            @Override
            public void error(String message) {
                hideProgressBar(progressBar);
                if (!TestUtil.isEmptyOrNull(message)) {
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

        final @Nullable Set<Integer> selectedContentTypes = viewModel.getSelectedContentTypes().getValue();
        final @Nullable @MessageContentsType int[] selectedContentTypesArray;
        if (selectedContentTypes != null) {
            selectedContentTypesArray = selectedContentTypes.stream().mapToInt(Integer::intValue).toArray();
        } else {
            selectedContentTypesArray = null;
        }
        intent.putExtra(MediaViewerActivity.EXTRA_FILTER, selectedContentTypesArray);

        startActivityForResult(intent, ACTIVITY_ID_MEDIA_VIEWER);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == PERMISSION_REQUEST_SAVE_MESSAGE) {
                dependencies.getFileService().saveMedia(this, recyclerView, new CopyOnWriteArrayList<>(mediaGalleryAdapter.getCheckedItems()), true);
            }
        } else {
            if (requestCode == PERMISSION_REQUEST_SAVE_MESSAGE) {
                if (!shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    ConfigUtils.showPermissionRationale(this, recyclerView, R.string.permission_storage_required);
                }
            }
        }
        actionMode.finish();
    }

    @Override
    public void onYes(String tag, Object data) {
        reallyDiscardMessages(new CopyOnWriteArrayList<>((ArrayList<AbstractMessageModel>) data));
    }

    @Override
    public void onClick(@Nullable AbstractMessageModel messageModel, @Nullable View view, int position) {
        if (actionMode != null) {
            logger.info("Message selection toggled");
            mediaGalleryAdapter.toggleChecked(position);
            if (mediaGalleryAdapter.getCheckedItemsCount() > 0) {
                if (actionMode != null) {
                    actionMode.invalidate();
                }
            } else {
                logger.info("Deselected last message");
                actionMode.finish();
            }
        } else {
            if (messageModel != null) {
                if (view != null) {
                    CircularProgressIndicator progressBar = view.findViewById(R.id.progress_decoding);

                    switch (messageModel.getMessageContentsType()) {
                        case MessageContentsType.IMAGE:
                        case MessageContentsType.VIDEO:
                        case MessageContentsType.VOICE_MESSAGE:
                        case MessageContentsType.AUDIO:
                        case MessageContentsType.GIF:
                            showInMediaFragment(messageModel, view);
                            break;
                        case MessageContentsType.FILE:
                            if ((FileUtil.isImageFile(messageModel.getFileData()) || FileUtil.isVideoFile(messageModel.getFileData()) || FileUtil.isAudioFile(messageModel.getFileData()))) {
                                logger.info("Media file clicked, showing");
                                showInMediaFragment(messageModel, view);
                            } else {
                                logger.info("File clicked, opening");
                                decryptAndShow(messageModel, view, progressBar);
                            }
                            break;
                        default:
                            break;
                    }
                }
            }
        }
    }

    @Override
    public boolean onLongClick(@Nullable AbstractMessageModel messageModel, @Nullable View itemView, int position) {
        if (actionMode != null) {
            logger.info("Long pressed message, leaving selection mode");
            actionMode.finish();
        }
        mediaGalleryAdapter.toggleChecked(position);
        if (mediaGalleryAdapter.getCheckedItemsCount() > 0) {
            logger.info("Long pressed message, entering selection mode");
            actionMode = startSupportActionMode(new MediaGalleryAction());
        }
        return true;
    }

    /**
     * Check that no more than {@link ComposeMessageFragment#MAX_FORWARDABLE_ITEMS} are selected,
     * that all media is downloaded, and that sharing media is allowed.
     *
     * @return true if the items can be shared, false otherwise
     */
    private boolean selectedItemsCanBeShared() {
        if (AppRestrictionUtil.isShareMediaDisabled(MediaGalleryActivity.this)) {
            return false;
        }
        if (mediaGalleryAdapter.getCheckedItemsCount() > ComposeMessageFragment.MAX_FORWARDABLE_ITEMS) {
            return false;
        }
        for (AbstractMessageModel message : mediaGalleryAdapter.getCheckedItems()) {
            if (!message.isAvailable()) {
                return false;
            }
        }
        return true;
    }

    public class MediaGalleryAction implements ActionMode.Callback {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.action_media_gallery, menu);
            if (AppRestrictionUtil.isShareMediaDisabled(MediaGalleryActivity.this)) {
                menu.findItem(R.id.menu_message_save).setVisible(false);
            }
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            final int checked = mediaGalleryAdapter.getCheckedItemsCount();
            menu.findItem(R.id.menu_show_in_chat).setVisible(checked == 1);
            menu.findItem(R.id.menu_share).setVisible(selectedItemsCanBeShared());

            if (checked > 0) {
                mode.setTitle(Integer.toString(checked));
                return true;
            }
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            int itemId = item.getItemId();
            if (itemId == R.id.menu_message_discard) {
                logger.info("Discard messages clicked");
                discardMessages();
                return true;
            } else if (itemId == R.id.menu_message_save) {
                logger.info("Save messages clicked");
                saveMessages();
                return true;
            } else if (itemId == R.id.menu_share) {
                logger.info("Share messages clicked");
                shareMessages();
                return true;
            } else if (itemId == R.id.menu_show_in_chat) {
                logger.info("Show in chat clicked");
                showInChat();
                return true;
            } else if (itemId == R.id.menu_select_all) {
                logger.info("Select all clicked");
                selectAllMessages();
                return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mediaGalleryAdapter.clearCheckedItems();
            actionMode = null;
        }

        @SuppressLint("StaticFieldLeak")
        private void shareMessages() {
            //noinspection deprecation
            new AsyncTask<Void, Void, Void>() {
                @Override
                @Deprecated
                protected void onPreExecute() {
                    GenericProgressDialog.newInstance(R.string.decoding_message, R.string.please_wait).show(getSupportFragmentManager(), DIALOG_TAG_DECRYPTING_MESSAGES);
                }

                @Override
                protected Void doInBackground(Void... voids) {
                    dependencies.getFileService().loadDecryptedMessageFiles(mediaGalleryAdapter.getCheckedItems(), new FileService.OnDecryptedFilesComplete() {
                        @Override
                        public void complete(ArrayList<Uri> uris) {
                            shareMediaMessages(uris);
                        }

                        @Override
                        public void error(String message) {
                            RuntimeUtil.runOnUiThread(() -> Toast.makeText(MediaGalleryActivity.this, message, Toast.LENGTH_LONG).show());
                        }
                    });
                    return null;
                }

                @Override
                @Deprecated
                protected void onPostExecute(Void aVoid) {
                    DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_DECRYPTING_MESSAGES, true);
                }
            }.execute();
        }

        private void shareMediaMessages(List<Uri> uris) {
            List<AbstractMessageModel> selectedMessages = mediaGalleryAdapter.getCheckedItems();
            if (uris.size() == 1) {
                ExpandableTextEntryDialog alertDialog = ExpandableTextEntryDialog.newInstance(
                    getString(R.string.share_media),
                    R.string.add_caption_hint, selectedMessages.get(0).getCaption(),
                    R.string.label_continue, R.string.cancel, true);
                alertDialog.setData(uris);
                alertDialog.show(getSupportFragmentManager(), null);
            } else {
                dependencies.getMessageService().shareMediaMessages(MediaGalleryActivity.this,
                    new ArrayList<>(selectedMessages),
                    new ArrayList<>(uris), null);
            }
        }
    }

}

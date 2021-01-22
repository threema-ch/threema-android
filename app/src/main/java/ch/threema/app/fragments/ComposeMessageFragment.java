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

package ch.threema.app.fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.util.DisplayMetrics;
import android.util.Pair;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.Filter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetView;
import com.google.android.material.snackbar.Snackbar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import androidx.annotation.AnyThread;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.SearchView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.MenuItemCompat;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.transition.Slide;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.actions.SendAction;
import ch.threema.app.actions.TextMessageSendAction;
import ch.threema.app.activities.ComposeMessageActivity;
import ch.threema.app.activities.ContactDetailActivity;
import ch.threema.app.activities.ContactNotificationsActivity;
import ch.threema.app.activities.DistributionListAddActivity;
import ch.threema.app.activities.GroupDetailActivity;
import ch.threema.app.activities.GroupNotificationsActivity;
import ch.threema.app.activities.HomeActivity;
import ch.threema.app.activities.MediaGalleryActivity;
import ch.threema.app.activities.RecipientListBaseActivity;
import ch.threema.app.activities.SendMediaActivity;
import ch.threema.app.activities.TextChatBubbleActivity;
import ch.threema.app.activities.ThreemaActivity;
import ch.threema.app.activities.ThreemaToolbarActivity;
import ch.threema.app.activities.WorkExplainActivity;
import ch.threema.app.activities.ballot.BallotOverviewActivity;
import ch.threema.app.adapters.ComposeMessageAdapter;
import ch.threema.app.adapters.decorators.ChatAdapterDecorator;
import ch.threema.app.asynctasks.EmptyChatAsyncTask;
import ch.threema.app.cache.ThumbnailCache;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.dialogs.MessageDetailDialog;
import ch.threema.app.dialogs.SelectorDialog;
import ch.threema.app.emojis.EmojiButton;
import ch.threema.app.emojis.EmojiMarkupUtil;
import ch.threema.app.emojis.EmojiPicker;
import ch.threema.app.emojis.EmojiTextView;
import ch.threema.app.listeners.BallotListener;
import ch.threema.app.listeners.ContactListener;
import ch.threema.app.listeners.ContactTypingListener;
import ch.threema.app.listeners.ConversationListener;
import ch.threema.app.listeners.GroupListener;
import ch.threema.app.listeners.MessageListener;
import ch.threema.app.listeners.MessagePlayerListener;
import ch.threema.app.listeners.QRCodeScanListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.mediaattacher.MediaAttachActivity;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.routines.ReadMessagesRoutine;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.ConversationService;
import ch.threema.app.services.DeadlineListService;
import ch.threema.app.services.DeviceService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.DownloadService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.IdListService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.NotificationService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.RingtoneService;
import ch.threema.app.services.ShortcutService;
import ch.threema.app.services.UserService;
import ch.threema.app.services.WallpaperService;
import ch.threema.app.services.ballot.BallotService;
import ch.threema.app.services.license.LicenseService;
import ch.threema.app.services.messageplayer.MessagePlayerService;
import ch.threema.app.ui.AvatarView;
import ch.threema.app.ui.ContentCommitComposeEditText;
import ch.threema.app.ui.ConversationListView;
import ch.threema.app.ui.DebouncedOnClickListener;
import ch.threema.app.ui.ListViewSwipeListener;
import ch.threema.app.ui.MentionSelectorPopup;
import ch.threema.app.ui.OpenBallotNoticeView;
import ch.threema.app.ui.QRCodePopup;
import ch.threema.app.ui.SendButton;
import ch.threema.app.ui.SingleToast;
import ch.threema.app.ui.TooltipPopup;
import ch.threema.app.ui.TypingIndicatorTextWatcher;
import ch.threema.app.ui.VerificationLevelImageView;
import ch.threema.app.ui.listitemholder.ComposeMessageHolder;
import ch.threema.app.utils.AnimationUtil;
import ch.threema.app.utils.AppRestrictionUtil;
import ch.threema.app.utils.BallotUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ContactUtil;
import ch.threema.app.utils.ConversationUtil;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.EditTextUtil;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.LinkifyUtil;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.app.utils.LogUtil;
import ch.threema.app.utils.MediaPlayerStateWrapper;
import ch.threema.app.utils.MessageUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.NavigationUtil;
import ch.threema.app.utils.QuoteUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.ToolbarUtil;
import ch.threema.app.voicemessage.VoiceRecorderActivity;
import ch.threema.app.voip.listeners.VoipCallEventListener;
import ch.threema.app.voip.managers.VoipListenerManager;
import ch.threema.app.voip.services.VoipStateService;
import ch.threema.app.voip.util.VoipUtil;
import ch.threema.client.IdentityType;
import ch.threema.client.file.FileData;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.DateSeparatorMessageModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.ballot.BallotModel;

import static android.view.WindowManager.LayoutParams.FLAG_SECURE;
import static ch.threema.app.ThreemaApplication.getAppContext;
import static ch.threema.app.services.messageplayer.MessagePlayer.SOURCE_AUDIORECORDER;
import static ch.threema.app.services.messageplayer.MessagePlayer.SOURCE_LIFECYCLE;
import static ch.threema.app.services.messageplayer.MessagePlayer.SOURCE_VOIP;
import static ch.threema.app.utils.LinkifyUtil.DIALOG_TAG_CONFIRM_LINK;

public class ComposeMessageFragment extends Fragment implements
	LifecycleOwner,
	SwipeRefreshLayout.OnRefreshListener,
	GenericAlertDialog.DialogClickListener,
	ChatAdapterDecorator.ActionModeStatus,
	SelectorDialog.SelectorDialogClickListener,
	EmojiPicker.EmojiPickerListener,
	MentionSelectorPopup.MentionSelectorListener,
	OpenBallotNoticeView.VisibilityListener,
	ThreemaToolbarActivity.OnSoftKeyboardChangedListener {

	private static final Logger logger = LoggerFactory.getLogger(ComposeMessageFragment.class);

	private static final String CONFIRM_TAG_DELETE_DISTRIBUTION_LIST = "deleteDistributionList";
	public static final String DIALOG_TAG_CONFIRM_CALL = "dtcc";
	private static final String DIALOG_TAG_CHOOSE_SHORTCUT_TYPE = "st";
	private static final String DIALOG_TAG_EMPTY_CHAT = "ccc";
	private static final String DIALOG_TAG_CONFIRM_BLOCK = "block";
	private static final String DIALOG_TAG_DECRYPTING_MESSAGES = "dcr";
	private static final String DIALOG_TAG_SEARCHING = "src";

	public static final String EXTRA_API_MESSAGE_ID = "apimsgid";
	public static final String EXTRA_SEARCH_QUERY = "searchQuery";

	private static final int PERMISSION_REQUEST_SAVE_MESSAGE = 2;
	private static final int PERMISSION_REQUEST_ATTACH_VOICE_MESSAGE = 7;
	private static final int PERMISSION_REQUEST_ATTACH_CAMERA = 8;
	private static final int PERMISSION_REQUEST_ATTACH_CAMERA_VIDEO = 11;

	private static final int ACTIVITY_ID_VOICE_RECORDER = 9731;

	public static final long VIBRATION_MSEC = 300;
	private static final long MESSAGE_PAGE_SIZE = 100;
	private static final int SCROLLBUTTON_VIEW_TIMEOUT = 3000;
	private static final int SMOOTHSCROLL_THRESHOLD = 10;
	private static final int MAX_SELECTED_ITEMS = 100; // may not be larger than MESSAGE_PAGE_SIZE
	private static final int MAX_FORWARDABLE_ITEMS = 50;
	private static final int CONTEXT_MENU_BOLD = 700;
	private static final int CONTEXT_MENU_ITALIC = 701;
	private static final int CONTEXT_MENU_STRIKETHRU = 702;
	private static final int CONTEXT_MENU_GROUP = 22100;

	private static final String CAMERA_URI = "camera_uri";

	private ContentCommitComposeEditText messageText;
	private SendButton sendButton;
	private ImageButton attachButton, cameraButton;
	private ContactModel contactModel;
	private MessageReceiver messageReceiver;

	private AudioManager audioManager;
	private ConversationListView convListView;
	private ComposeMessageAdapter composeMessageAdapter;
	private View isTypingView;

	private MenuItem mutedMenuItem = null, blockMenuItem = null, deleteDistributionListItem = null, callItem = null, shortCutItem = null, showOpenBallotWindowMenuItem = null, showBallotsMenuItem = null;
	private TextView dateTextView;

	private ActionMode actionMode = null;
	private ActionMode searchActionMode = null;
	private ImageView quickscrollDownView = null, quickscrollUpView = null;
	private FrameLayout dateView = null;
	private FrameLayout bottomPanel = null;
	private String identity;
	private Integer groupId = 0, distributionListId = 0;
	private Uri cameraUri;
	private long intentTimestamp = 0L;
	private int longClickItem = AbsListView.INVALID_POSITION;
	private int listViewTop = 0, lastFirstVisibleItem = -1;
	private Snackbar deleteSnackbar;
	private TypingIndicatorTextWatcher typingIndicatorTextWatcher;
	private Map<String, Integer> identityColors;

	private PreferenceService preferenceService;
	private ContactService contactService;
	private MessageService messageService;
	private NotificationService notificationService;
	private IdListService blackListIdentityService;
	private ConversationService conversationService;
	private DeviceService deviceService;
	private WallpaperService wallpaperService;
	private DeadlineListService mutedChatsListService, mentionOnlyChatsListService, hiddenChatsListService;
	private RingtoneService ringtoneService;
	private UserService userService;
	private FileService fileService;
	private VoipStateService voipStateService;
	private ShortcutService shortcutService;
	private DownloadService downloadService;
	private LicenseService licenseService;

	private boolean listUpdateInProgress = false, isPaused = false;
	private final List<AbstractMessageModel> unreadMessages = new ArrayList<>();
	private final List<AbstractMessageModel> messageValues = new ArrayList<>();
	private final List<AbstractMessageModel> selectedMessages = new ArrayList<>(1);
	private final List<Pair<AbstractMessageModel, Integer>> deleteableMessages = new ArrayList<>(1);

	private EmojiMarkupUtil emojiMarkupUtil;
	private EmojiPicker emojiPicker;
	private EmojiButton emojiButton;
	private SwipeRefreshLayout swipeRefreshLayout;
	private Integer currentPageReferenceId = null;
	private EmojiTextView actionBarTitleTextView;
	private TextView actionBarSubtitleTextView;
	private VerificationLevelImageView actionBarSubtitleImageView;
	private AvatarView actionBarAvatarView;
	private ImageView wallpaperView;
	private ActionBar actionBar;
	private MentionSelectorPopup mentionPopup;
	private TooltipPopup workTooltipPopup;
	private OpenBallotNoticeView openBallotNoticeView;
	private ComposeMessageActivity activity;
	private View fragmentView;
	private CoordinatorLayout coordinatorLayout;
	private BallotService ballotService;
	private LayoutInflater layoutInflater;
	private ListViewSwipeListener listViewSwipeListener;

	private boolean hastNextRecords = true;
	private List<AbstractMessageModel> values;
	private GroupService groupService;
	private boolean isGroupChat = false;
	private GroupModel groupModel;
	private Date listInitializedAt;
	private boolean isDistributionListChat = false;
	private DistributionListService distributionListService;
	private DistributionListModel distributionListModel;
	private MessagePlayerService messagePlayerService;
	private int listInstancePosition = AbsListView.INVALID_POSITION;
	private int listInstanceTop = 0;
	private String listInstanceReceiverId = null;
	private int unreadCount = 0;
	private boolean hasFocus = false;
	private final QuoteInfo quoteInfo = new QuoteInfo();
	private TextView searchCounter;
	private ProgressBar searchProgress;
	private ImageView searchNextButton, searchPreviousButton;

	@SuppressLint("SimpleDateFormat")
	private final SimpleDateFormat dayFormatter = new SimpleDateFormat("yyyyMMdd");
	private ThumbnailCache<?> thumbnailCache = null;

	private static class QuoteInfo {
		private FrameLayout quotePanel;
		private TextView quoteTextView, quoteIdentityView;
		private String quoteText, quoteIdentity;
		private View quoteBar;
		private ImageView quoteThumbnail;
		private ImageView quoteTypeImage;
		private AbstractMessageModel messageModel;
	}

	@Override
	public boolean getActionModeEnabled() {
		return actionMode != null;
	}

	private final MessageService.MessageFilter nextMessageFilter = new MessageService.MessageFilter() {
		@Override
		public long getPageSize() {
			return MESSAGE_PAGE_SIZE;
		}

		@Override
		public Integer getPageReferenceId() {
			return getCurrentPageReferenceId();
		}

		@Override
		public boolean withStatusMessages() {
			return true;
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
			return false;
		}

		@Override
		public MessageType[] types() {
			return null;
		}

		@Override
		public int[] contentTypes() {
			return null;
		}
	};

	// handler to remove dateview button after a certain time
	private final Handler dateViewHandler = new Handler();
	private final Runnable dateViewTask = () -> RuntimeUtil.runOnUiThread(() -> {
		if (dateView != null && dateView.getVisibility() == View.VISIBLE) {
			AnimationUtil.slideOutAnimation(dateView, false, 1f, null);
			AnimationUtil.setFadingVisibility(quickscrollUpView, View.GONE);
			AnimationUtil.setFadingVisibility(quickscrollDownView, View.GONE);
		}
	});

	// Listeners
	private final VoipCallEventListener voipCallEventListener = new VoipCallEventListener() {
		@Override
		public void onRinging(String peerIdentity) {
		}

		@Override
		public void onStarted(String peerIdentity, boolean outgoing) {
			logger.debug("VoipCallEventListener onStarted");
			updateVoipCallMenuItem(false);
			if (messagePlayerService != null) {
				messagePlayerService.pauseAll(SOURCE_VOIP);
			}
		}

		@Override
		public void onFinished(@NonNull String peerIdentity, boolean outgoing, int duration) {
			logger.debug("VoipCallEventListener onFinished");
			updateVoipCallMenuItem(true);
		}

		@Override
		public void onRejected(String peerIdentity, boolean outgoing, byte reason) {
			logger.debug("VoipCallEventListener onRejected");
			updateVoipCallMenuItem(true);
		}

		@Override
		public void onMissed(String peerIdentity, boolean accepted) {
			logger.debug("VoipCallEventListener onMissed");
			updateVoipCallMenuItem(true);
		}

		@Override
		public void onAborted(String peerIdentity) {
			logger.debug("VoipCallEventListener onAborted");
			updateVoipCallMenuItem(true); }
	};

	private final MessageListener messageListener = new MessageListener() {
		@Override
		public void onNew(final AbstractMessageModel newMessage) {
			if (newMessage != null) {
				RuntimeUtil.runOnUiThread(() -> {
					if (newMessage.isOutbox()) {
						if (addMessageToList(newMessage, true)) {
							if (!newMessage.isStatusMessage() && (newMessage.getType() != MessageType.VOIP_STATUS)) {
								playSentSound();
							}
						}
					} else {
						if (addMessageToList(newMessage, false) && !isPaused) {
							if (!newMessage.isStatusMessage() && (newMessage.getType() != MessageType.VOIP_STATUS)) {
								playReceivedSound();
							}
						}
					}
				});
			}
		}

		@Override
		public void onModified(final List<AbstractMessageModel> modifiedMessageModels) {
			//replace model
			synchronized (messageValues) {
				for (final AbstractMessageModel modifiedMessageModel : modifiedMessageModels) {
					if (modifiedMessageModel.getId() != 0) {
						for (int n = 0; n < messageValues.size(); n++) {
							AbstractMessageModel listModel = messageValues.get(n);
							if (listModel != null && listModel.getId() == modifiedMessageModel.getId()) {
								//if the changed message is different to the created
								if (modifiedMessageModel != listModel) {
									//replace item
									messageValues.set(n, modifiedMessageModel);
								}
								break;
							}
						}
					}
				}
			}
			RuntimeUtil.runOnUiThread(() -> {
				if (composeMessageAdapter != null) {
					composeMessageAdapter.notifyDataSetChanged();
				}
			});
		}

		@Override
		public void onRemoved(final AbstractMessageModel removedMessageModel) {
			RuntimeUtil.runOnUiThread(() -> {
				if (TestUtil.required(composeMessageAdapter, removedMessageModel)) {
					composeMessageAdapter.remove(removedMessageModel);
					composeMessageAdapter.notifyDataSetChanged();
				}
			});
		}

		@Override
		public void onProgressChanged(AbstractMessageModel messageModel, int newProgress) {
			//ignore
				RuntimeUtil.runOnUiThread(() -> {
					if (composeMessageAdapter != null) {
						composeMessageAdapter.notifyDataSetChanged();
					}
				});
		}
	};

	private final GroupListener groupListener = new GroupListener() {
		@Override
		public void onCreate(GroupModel newGroupModel) {
			//do nothing
		}

		@Override
		public void onRename(GroupModel groupModel) {
			updateToolBarTitleInUIThread();
		}

		@Override
		public void onUpdatePhoto(GroupModel groupModel) {
			updateToolBarTitleInUIThread();
		}

		@Override
		public void onRemove(GroupModel groupModel) {
			if (isGroupChat && groupId != null && groupId == groupModel.getId()) {
				RuntimeUtil.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if (activity != null) {
							activity.finish();
						}
					}
				});
			}
		}

		@Override
		public void onNewMember(GroupModel group, String newIdentity) {
			updateToolBarTitleInUIThread();
		}

		@Override
		public void onMemberLeave(GroupModel group, String identity) {
			updateToolBarTitleInUIThread();
		}

		@Override
		public void onMemberKicked(GroupModel group, String identity) {
			updateToolBarTitleInUIThread();
		}


		@Override
		public void onUpdate(GroupModel groupModel) {
			updateToolBarTitleInUIThread();
		}

		@Override
		public void onLeave(GroupModel groupModel) {
			if (isGroupChat && groupId != null && groupId == groupModel.getId()) {
				RuntimeUtil.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if (activity != null) {
							activity.finish();
						}
					}
				});
			}
		}
	};

	private final ContactListener contactListener = new ContactListener() {
		@Override
		public void onModified(final ContactModel modifiedContactModel) {
			updateContactModelData(modifiedContactModel);
		}

		@Override
		public void onAvatarChanged(ContactModel contactModel) {
			updateToolBarTitleInUIThread();
		}

		@Override
		public void onRemoved(ContactModel removedContactModel) {
			if (contactModel != null && contactModel.equals(removedContactModel)) {
				// our contact has been removed. finish activity.
				RuntimeUtil.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if (activity != null) {
							activity.finish();
						}
					}
				});
			}
		}

		@Override
		public boolean handle(String handleIdentity) {
			//handle every contact change, affected contact models:
			//1. the contact of the chat
			//2. a contact as group member
			return true;
		}
	};

	private final ContactTypingListener contactTypingListener = new ContactTypingListener() {
		@Override
		public void onContactIsTyping(final ContactModel fromContact, final boolean isTyping) {
			RuntimeUtil.runOnUiThread(() -> {
				if (contactModel != null && fromContact.getIdentity().equals(contactModel.getIdentity())) {
					contactTypingStateChanged(isTyping);
				}
			});
		}
	};

	private final ConversationListener conversationListener = new ConversationListener() {
		@Override
		public void onNew(ConversationModel conversationModel) {}

		@Override
		public void onModified(ConversationModel modifiedConversationModel, Integer oldPosition) {}

		@Override
		public void onRemoved(ConversationModel conversationModel) {
			if (conversationModel != null) {
				boolean itsMyConversation = false;
				if (contactModel != null) {
					itsMyConversation = (conversationModel.getContact() != null
						&& TestUtil.compare(conversationModel.getContact().getIdentity(), contactModel.getIdentity()));
				} else if (distributionListModel!= null) {
					itsMyConversation = conversationModel.getDistributionList() != null
						&& TestUtil.compare(conversationModel.getDistributionList().getId(), distributionListModel.getId());
				} else if (groupModel != null) {
					itsMyConversation = conversationModel.getGroup() != null
						&& TestUtil.compare(conversationModel.getGroup().getId(), groupModel.getId());
				}

				if (itsMyConversation) {
					RuntimeUtil.runOnUiThread(() -> {
						if (getActivity() != null) {
							getActivity().finish();
						}
					});
				}
			}
		}

		@Override
		public void onModifiedAll() {}
	};

	private final MessagePlayerListener messagePlayerListener = new MessagePlayerListener() {
		@Override
		public void onAudioStreamChanged(int newStreamType) { }

		@Override
		public void onAudioPlayEnded(AbstractMessageModel messageModel) {
			// Play next audio message, if any
			RuntimeUtil.runOnUiThread(() -> {
				if (composeMessageAdapter != null) {
					int index = composeMessageAdapter.getNextItem(messageModel, MessageType.VOICEMESSAGE);
					if (index != AbsListView.INVALID_POSITION) {
						View view = composeMessageAdapter.getView(index, null, null);

						ComposeMessageHolder holder = (ComposeMessageHolder) view.getTag();
						if (holder.messagePlayer != null) {
							holder.messagePlayer.open();
							composeMessageAdapter.notifyDataSetChanged();
						}
					}
				}
			});
		}
	};

	private final QRCodeScanListener qrCodeScanListener = new QRCodeScanListener() {
		@Override
		public void onScanCompleted(String scanResult) {
			if (scanResult != null && scanResult.length() > 0) {
				RuntimeUtil.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if (messageText != null) {
							messageText.setText(scanResult);
							messageText.setSelection(messageText.length());
						}
					}
				});
			}
		}
	};

	private final BallotListener ballotListener = new BallotListener() {
		@Override
		public void onClosed(BallotModel ballotModel) { }

		@Override
		public void onModified(BallotModel ballotModel) { }

		@Override
		public void onCreated(BallotModel ballotModel) {
			try {
				BallotUtil.openDefaultActivity(
					getContext(),
					getFragmentManager(),
					ballotService.get(ballotModel.getId()),
					userService.getIdentity());
			} catch (Exception e) {
				logger.error("Exception", e);
			}
		}

		@Override
		public void onRemoved(BallotModel ballotModel) {

		}

		@Override
		public boolean handle(BallotModel ballotModel) {
			return ballotModel != null && userService.getIdentity().equals(ballotModel.getCreatorIdentity());
		}
	};


	@SuppressLint("StaticFieldLeak")
	@Override
	public void onRefresh() {
		logger.debug("onRefresh");

		if (actionMode != null || searchActionMode != null) {
			swipeRefreshLayout.setRefreshing(false);
			return;
		}

		new AsyncTask<Void, Void, Integer>() {
			@Override
			protected Integer doInBackground(Void... params) {
				values = getNextRecords();
				if (values != null) {
					hastNextRecords = values.size() >= nextMessageFilter.getPageSize();
					return insertToList(values, false, true);
				}
				return null;
			}

			@Override
			protected void onPostExecute(Integer numberOfInsertedRecords) {
				composeMessageAdapter.notifyDataSetChanged();
				if (numberOfInsertedRecords != null && numberOfInsertedRecords > 0) {
					convListView.setSelection(convListView.getSelectedItemPosition() + numberOfInsertedRecords + 1);
				}

				// Notify PullToRefreshAttacher that the refresh has activity.finished
				swipeRefreshLayout.setRefreshing(false);
				swipeRefreshLayout.setEnabled(hastNextRecords);
			}
		}.execute();
	}

	@Override
	public void onAttach(@NonNull Activity activity) {
		logger.debug("onAttach");
		super.onAttach(activity);

		setHasOptionsMenu(true);

		this.activity = (ComposeMessageActivity) activity;
		this.audioManager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);

		if (bottomPanel != null) {
			bottomPanel.setVisibility(View.VISIBLE);
		}

		if (this.emojiPicker != null) {
			this.emojiPicker.init(activity);
		}

		// resolution and layout may have changed after being attached to a new activity
		ConfigUtils.getPreferredThumbnailWidth(activity, true);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		logger.debug("onCreate");

		if (getActivity() != null) {
			getActivity().supportPostponeEnterTransition();
		}
		super.onCreate(savedInstanceState);

		setRetainInstance(true);

		ListenerManager.contactTypingListeners.add(this.contactTypingListener);
		ListenerManager.messageListeners.add(this.messageListener, true);
		ListenerManager.groupListeners.add(this.groupListener);
		ListenerManager.contactListeners.add(this.contactListener);
		ListenerManager.conversationListeners.add(this.conversationListener);
		ListenerManager.messagePlayerListener.add(this.messagePlayerListener);
		ListenerManager.qrCodeScanListener.add(this.qrCodeScanListener);
		ListenerManager.ballotListeners.add(this.ballotListener);
		VoipListenerManager.callEventListener.add(this.voipCallEventListener);
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		logger.debug("onCreateView");

		if (!requiredInstances()) {
			activity.finish();
			return this.fragmentView;
		}

		this.layoutInflater = inflater;

		if (this.fragmentView == null) {
			// set font size
			activity.getTheme().applyStyle(preferenceService.getFontStyle(), true);
			this.fragmentView = inflater.inflate(R.layout.fragment_compose_message, container, false);

			ScrollView sv = fragmentView.findViewById(R.id.wallpaper_scroll);
			sv.setEnabled(false);
			sv.setOnTouchListener(null);
			sv.setOnClickListener(null);

			this.convListView = fragmentView.findViewById(R.id.history);
			ViewCompat.setNestedScrollingEnabled(this.convListView, true);
			this.convListView.setDivider(null);
			this.convListView.setClipToPadding(false);

			if (ConfigUtils.isTabletLayout()) {
				this.convListView.setPadding(0, 0, 0, 0);
			}

			this.listViewTop = this.convListView.getPaddingTop();
			this.swipeRefreshLayout = fragmentView.findViewById(R.id.ptr_layout);
			this.swipeRefreshLayout.setOnRefreshListener(this);
			this.swipeRefreshLayout.setColorSchemeResources(R.color.accent_light);
			this.swipeRefreshLayout.setSize(SwipeRefreshLayout.LARGE);
			this.coordinatorLayout = fragmentView.findViewById(R.id.coordinator);
			this.messageText = fragmentView.findViewById(R.id.embedded_text_editor);

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				// do not add on lollipop or lower due to this bug: https://issuetracker.google.com/issues/36937508
				this.messageText.setCustomSelectionActionModeCallback(textSelectionCallback);
			}

			this.sendButton = this.fragmentView.findViewById(R.id.send_button);
			this.attachButton = this.fragmentView.findViewById(R.id.attach_button);
			this.cameraButton = this.fragmentView.findViewById(R.id.camera_button);
			this.cameraButton.setOnClickListener(v -> {
				if (actionMode != null) {
					actionMode.finish();
				}
				closeQuoteMode();
				if (!validateSendingPermission()) {
					return;
				}
				if (ConfigUtils.requestCameraPermissions(activity, this, PERMISSION_REQUEST_ATTACH_CAMERA)) {
					attachCamera();
				}
			});
			updateCameraButton();

			this.emojiButton = this.fragmentView.findViewById(R.id.emoji_button);
			this.emojiButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					logger.info("Emoji button clicked");
					if (activity.isSoftKeyboardOpen()) {
						logger.info("Show emoji picker after keyboard close");
						activity.runOnSoftKeyboardClose(new Runnable() {
							@Override
							public void run() {
								if (emojiPicker != null) {
									emojiPicker.show(activity.loadStoredSoftKeyboardHeight());
								}
							}
						});

						messageText.post(new Runnable() {
							@Override
							public void run() {
								EditTextUtil.hideSoftKeyboard(messageText);
							}
						});
					} else {
						if (emojiPicker != null) {
							if (emojiPicker.isShown()) {
								logger.info("EmojPicker currently shown. Closing.");
								if (ConfigUtils.isLandscape(activity) &&
									!ConfigUtils.isTabletLayout() &&
									preferenceService.isFullscreenIme()) {
									emojiPicker.hide();
								} else {
									activity.openSoftKeyboard(emojiPicker, messageText);
									if (activity.getResources().getConfiguration().keyboard == Configuration.KEYBOARD_QWERTY) {
										emojiPicker.hide();
									}
								}
							} else {
								logger.info("Show emoji picker immediately");
								emojiPicker.show(activity.loadStoredSoftKeyboardHeight());
							}
						}
					}
				}
			});

			this.emojiMarkupUtil = EmojiMarkupUtil.getInstance();
			this.wallpaperView = this.fragmentView.findViewById(R.id.wallpaper_view);
			this.quickscrollUpView = this.fragmentView.findViewById(R.id.quickscroll_top);
			this.quickscrollDownView = this.fragmentView.findViewById(R.id.quickscroll_bottom);
			this.dateView = this.fragmentView.findViewById(R.id.date_separator_container);
			this.dateTextView = this.fragmentView.findViewById(R.id.text_view);

			quoteInfo.quotePanel = this.fragmentView.findViewById(R.id.quote_panel);
			quoteInfo.quoteTextView = this.fragmentView.findViewById(R.id.quote_text_view);
			quoteInfo.quoteIdentityView = this.fragmentView.findViewById(R.id.quote_id_view);
			quoteInfo.quoteBar = this.fragmentView.findViewById(R.id.quote_bar);
			quoteInfo.quoteThumbnail = this.fragmentView.findViewById(R.id.quote_thumbnail);
			quoteInfo.quoteTypeImage = this.fragmentView.findViewById(R.id.quote_type_image);

			ImageView quoteCloseButton = this.fragmentView.findViewById(R.id.quote_panel_close_button);
			quoteCloseButton.setOnClickListener(v -> closeQuoteMode());

			this.bottomPanel = this.fragmentView.findViewById(R.id.bottom_panel);
			this.openBallotNoticeView = this.fragmentView.findViewById(R.id.open_ballots_layout);

			this.getValuesFromBundle(savedInstanceState);
			this.handleIntent(activity.getIntent());
			this.setupListeners();
		}

		if (preferenceService.getEmojiStyle() == PreferenceService.EmojiStyle_ANDROID) {
			// remove emoji button
			this.emojiButton.setVisibility(View.GONE);
			this.messageText.setPadding(getResources().getDimensionPixelSize(R.dimen.no_emoji_button_padding_left), this.messageText.getPaddingTop(), this.messageText.getPaddingRight(), this.messageText.getPaddingBottom());
		} else {
			try {
				this.emojiPicker = (EmojiPicker) ((ViewStub) this.activity.findViewById(R.id.emoji_stub)).inflate();
				this.emojiPicker.init(activity);
				this.emojiButton.attach(this.emojiPicker, preferenceService.isFullscreenIme());
				this.emojiPicker.setEmojiKeyListener(new EmojiPicker.EmojiKeyListener() {
					@Override
					public void onBackspaceClick() {
						messageText.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
					}

					@Override
					public void onEmojiClick(String emojiCodeString) {
						RuntimeUtil.runOnUiThread(() -> messageText.addEmoji(emojiCodeString));
					}
				});

				this.emojiPicker.addEmojiPickerListener(this);
			} catch (Exception e) {
				logger.error("Exception", e);
				activity.finish();
			}
		}

		return this.fragmentView;
	}

	private final android.view.ActionMode.Callback textSelectionCallback = new android.view.ActionMode.Callback() {
		private final Pattern pattern = Pattern.compile("\\B");

		@Override
		public boolean onCreateActionMode(android.view.ActionMode mode, Menu menu) {
			return true;
		}

		@Override
		public boolean onPrepareActionMode(android.view.ActionMode mode, Menu menu) {
			menu.removeGroup(CONTEXT_MENU_GROUP);

			if (messageText != null && messageText.getText() != null) {
				String text = messageText.getText().toString();
				if (text.length() > 1) {
					int start = messageText.getSelectionStart();
					int end = messageText.getSelectionEnd();

					try {
						if ((start <= 0 || pattern.matcher(text.substring(start - 1, start)).find()) &&
							(end >= text.length() || pattern.matcher(text.substring(end, end + 1)).find()) &&
							!text.substring(start, end).contains("\n")) {
							menu.add(CONTEXT_MENU_GROUP, CONTEXT_MENU_BOLD, 200, R.string.bold);
							menu.add(CONTEXT_MENU_GROUP, CONTEXT_MENU_ITALIC, 201, R.string.italic);
							menu.add(CONTEXT_MENU_GROUP, CONTEXT_MENU_STRIKETHRU, 203, R.string.strikethrough);
						}
					} catch (StringIndexOutOfBoundsException e) {
						// do not add menus
					}
				}
			}
			return true;
		}

		@Override
		public boolean onActionItemClicked(android.view.ActionMode mode, MenuItem item) {
			switch (item.getItemId()) {
				case CONTEXT_MENU_BOLD:
					addMarkup("*");
					break;
				case CONTEXT_MENU_ITALIC:
					addMarkup("_");
					break;
				case CONTEXT_MENU_STRIKETHRU:
					addMarkup("~");
					break;
				default:
					return false;
			}
			return true;
		}

		@Override
		public void onDestroyActionMode(android.view.ActionMode mode) {
		}
	};

	private void addMarkup(String string) {
		Editable editable = messageText.getText();

		if (editable.length() > 0) {
			int start = messageText.getSelectionStart();
			int end = messageText.getSelectionEnd();

			editable.insert(end, string);
			editable.insert(start, string);
		}
		messageText.invalidate();
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		logger.debug("onActivityCreated");

		super.onActivityCreated(savedInstanceState);
		/*
		 * This callback tells the fragment when it is fully associated with the new activity instance. This is called after onCreateView(LayoutInflater, ViewGroup, Bundle) and before onViewStateRestored(Bundle).
		 */
		activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

		if (preferenceService.getEmojiStyle() != PreferenceService.EmojiStyle_ANDROID) {
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
				activity.findViewById(R.id.compose_activity_parent).getRootView().getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
					@Override
					public void onGlobalLayout() {
						DisplayMetrics metrics = new DisplayMetrics();
						// get dimensions of usable display space with decorations (status bar / navigation bar) subtracted
						activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
						int usableHeight = metrics.heightPixels;
						int statusBarHeight = ConfigUtils.getStatusBarHeight(getContext());
						int rootViewHeight = activity.findViewById(R.id.compose_activity_parent).getHeight();

						if (rootViewHeight + statusBarHeight == usableHeight) {
							activity.onSoftKeyboardClosed();
						} else {
							activity.onSoftKeyboardOpened(usableHeight - statusBarHeight - rootViewHeight);
						}
					}
				});
			} else {
				try {
					ViewCompat.setOnApplyWindowInsetsListener(activity.getWindow().getDecorView().getRootView(), new OnApplyWindowInsetsListener() {
						@Override
						public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat insets) {

							logger.info("%%% system window top " + insets.getSystemWindowInsetTop() + " bottom " + insets.getSystemWindowInsetBottom());
							logger.info("%%% stable insets top " + insets.getStableInsetTop() + " bottom " + insets.getStableInsetBottom());

							if (insets.getSystemWindowInsetBottom() <= insets.getStableInsetBottom()) {
								activity.onSoftKeyboardClosed();
							} else {
								activity.onSoftKeyboardOpened(insets.getSystemWindowInsetBottom() - insets.getStableInsetBottom());
							}
							return insets;
						}
					});
				} catch (NullPointerException e) {
					logger.error("Exception", e);
				}
			}
			activity.addOnSoftKeyboardChangedListener(this);
		}

		// restore action mode after rotate if the activity was detached
		if (convListView != null && convListView.getCheckedItemCount() > 0 && actionMode != null) {
			actionMode = ((AppCompatActivity) getActivity()).startSupportActionMode(new ComposeMessageAction(this.longClickItem));
		}
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		activity.supportStartPostponedEnterTransition();
	}

	public void onWindowFocusChanged(boolean hasFocus) {
		logger.debug("onWindowFocusChanged " + hasFocus);

		// workaround for proximity wake lock causing calls to onPause/onResume on Samsuck devices:
		// see: http://stackoverflow.com/questions/35318649/android-proximity-sensor-issue-only-in-samsung-devices
		if (hasFocus) {
			if (!this.hasFocus) {
				reallyOnResume();
				this.hasFocus = true;
			}
		} else {
			reallyOnPause();
			this.hasFocus = false;
		}
	}

	@Override
	public void onResume() {
		logger.debug("onResume");
		super.onResume();

		if (!ConfigUtils.isSamsungDevice() || ConfigUtils.isTabletLayout()) {
			reallyOnResume();
		}
	}

	private void reallyOnResume() {
		logger.debug("reallyOnResume");

		//set visible receiver
		if (this.messageReceiver != null) {
			this.notificationService.setVisibleReceiver(this.messageReceiver);

			isPaused = false;
			logger.debug("markAllRead");
			// mark all unread messages
			if (this.unreadMessages.size() > 0) {
				ReadMessagesRoutine r = new ReadMessagesRoutine(this.unreadMessages,
						this.messageService,
						this.notificationService);

				r.addOnFinished(new ReadMessagesRoutine.OnFinished() {
					@Override
					public void finished(boolean success) {
						if (success) {
							unreadMessages.clear();
						}
					}
				});

				new Thread(r).start();
			}

			// update menus
			updateMuteMenu();

			// start media players again
			this.messagePlayerService.resumeAll(getActivity(), this.messageReceiver, SOURCE_LIFECYCLE);

			// restore scroll position after orientation change
			convListView.post(new Runnable() {
				@Override
				public void run() {
					if (listInstancePosition != AbsListView.INVALID_POSITION &&
							messageReceiver != null &&
							messageReceiver.getUniqueIdString().equals(listInstanceReceiverId)) {
						logger.debug("restoring position " + listInstancePosition);
						convListView.setSelectionFromTop(listInstancePosition, listInstanceTop);
					} else {
						if (unreadCount > 0) {
							// jump to first unread message
							int position = convListView.getCount() - unreadCount - 1;
							logger.debug("jump to initial position " + position);
							convListView.setSelection(Math.max(position, 0));
							unreadCount = 0;
						} else {
							logger.debug("reset position");
							convListView.setSelection(Integer.MAX_VALUE);
						}
					}
					// make sure it's not restored twice
					listInstancePosition = AbsListView.INVALID_POSITION;
					listInstanceReceiverId = null;
				}
			});
		}
	}

	@Override
	public void onStart() {
		logger.debug("onStart");

		super.onStart();
	}

	@Override
	public void onPause() {
		logger.debug("onPause");
		if (!ConfigUtils.isSamsungDevice() || ConfigUtils.isTabletLayout()) {
			reallyOnPause();
		}

		super.onPause();
	}

	private void reallyOnPause() {
		logger.debug("reallyOnPause");
		isPaused = true;

		onEmojiPickerClose();

		if (this.notificationService != null) {
			this.notificationService.setVisibleReceiver(null);
		}

		//stop all playing audio messages (incoming call?)
		if (this.messagePlayerService != null) {
			this.messagePlayerService.pauseAll(SOURCE_LIFECYCLE);
		}

		// save unfinished text
		saveMessageDraft();

		if(this.typingIndicatorTextWatcher != null) {
			this.typingIndicatorTextWatcher.stopTyping();
		}

		preserveListInstanceValues();
	}


	@Override
	public void onStop() {
		logger.debug("onStop");

		if(this.typingIndicatorTextWatcher != null) {
			this.typingIndicatorTextWatcher.stopTyping();
		}
		super.onStop();
	}

	@Override
	public void onDetach() {
		logger.debug("onDetach");

		if (this.emojiPicker != null && this.emojiPicker.isShown()) {
			this.emojiPicker.hide();
		}
		dismissMentionPopup();

		this.activity = null;

		super.onDetach();
	}

	@Override
	public void onDestroy() {
		logger.debug("onDestroy");

		try {
			ListenerManager.contactTypingListeners.remove(this.contactTypingListener);
			ListenerManager.groupListeners.remove(this.groupListener);
			ListenerManager.messageListeners.remove(this.messageListener);
			ListenerManager.contactListeners.remove(this.contactListener);
			ListenerManager.conversationListeners.remove(this.conversationListener);
			ListenerManager.messagePlayerListener.remove(this.messagePlayerListener);
			ListenerManager.qrCodeScanListener.remove(this.qrCodeScanListener);
			ListenerManager.ballotListeners.remove(this.ballotListener);
			VoipListenerManager.callEventListener.remove(this.voipCallEventListener);

			dismissTooltipPopup(workTooltipPopup, true);
			workTooltipPopup = null;

			dismissMentionPopup();

			if (this.emojiButton != null) {
				this.emojiButton.detach(this.emojiPicker);
			}

			if (this.emojiPicker != null) {
				this.emojiPicker.removeEmojiPickerListener(this);
			}

			if (!requiredInstances()) {
				super.onDestroy();
				return;
			}

			//release all players!
			if (this.messagePlayerService != null) {
				this.messagePlayerService.release();
			}

			if (this.messageService != null) {
				this.messageService.saveMessageQueue();
			}

			if (this.thumbnailCache != null) {
				this.thumbnailCache.flush();
			}

			if (this.messageText != null) {
				//remove typing change listener
				if(this.typingIndicatorTextWatcher != null) {
					this.messageText.removeTextChangedListener(this.typingIndicatorTextWatcher);
				}
				// http://stackoverflow.com/questions/18348049/android-edittext-memory-leak
				this.messageText.setText(null);
			}

			//remove wallpaper
			this.wallpaperView.setImageBitmap(null);

			// delete pending deleteable messages
			deleteDeleteableMessages();

			if (this.deleteSnackbar != null && this.deleteSnackbar.isShownOrQueued()) {
				this.deleteSnackbar.dismiss();
			}

			removeIsTypingFooter();
			this.isTypingView = null;

			//clear all records to remove all references
			if(this.composeMessageAdapter != null) {
				this.composeMessageAdapter.clear();
			}

		} catch (Exception x) {
			logger.error("Exception", x);
		}

		super.onDestroy();
	}

	private void removeScrollButtons() {
		logger.debug("removeScrollButtons");
		if (dateView != null && dateView.getVisibility() == View.VISIBLE) {
			AnimationUtil.slideOutAnimation(dateView, false, 1f, null);
		}

		if (actionMode != null) {
			actionMode.finish();
		}
	}

	private void setupListeners() {
		// Setting this scroll listener is required to ensure that during ListView scrolling,
		// we don't look for swipes or pulldowns
		this.convListView.setOnScrollListener(new AbsListView.OnScrollListener() {
			@Override
			public void onScrollStateChanged(AbsListView absListView, int scrollState) {
				if (listViewSwipeListener != null) {
					listViewSwipeListener.setEnabled(scrollState != AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL);
				}

				if (!absListView.canScrollList(View.SCROLL_AXIS_VERTICAL)) {
					AnimationUtil.setFadingVisibility(quickscrollDownView, View.GONE);
				}

				if (!absListView.canScrollList(-View.SCROLL_AXIS_VERTICAL)) {
					AnimationUtil.setFadingVisibility(quickscrollUpView, View.GONE);
				}
			}

			@Override
			public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
				if(view != null && view.getChildCount() > 0) {
					View itemView = view.getChildAt(0);

					boolean onTop = firstVisibleItem == 0 && itemView.getTop() == listViewTop;
					swipeRefreshLayout.setEnabled(onTop);

					if (firstVisibleItem != lastFirstVisibleItem) {
						if (lastFirstVisibleItem < firstVisibleItem) {
							// scrolling down
							AnimationUtil.setFadingVisibility(quickscrollUpView, View.GONE);
							if (view.canScrollList(View.SCROLL_AXIS_VERTICAL)) {
								AnimationUtil.setFadingVisibility(quickscrollDownView, View.VISIBLE);
							}
						} else {
							// scrolling up
							AnimationUtil.setFadingVisibility(quickscrollDownView, View.GONE);
							if (view.canScrollList(-View.SCROLL_AXIS_VERTICAL)) {
								AnimationUtil.setFadingVisibility(quickscrollUpView, View.VISIBLE);
							}
						}

						if (dateView.getVisibility() != View.VISIBLE && composeMessageAdapter != null && composeMessageAdapter.getCount() > 0) {
							AnimationUtil.slideInAnimation(dateView, false, 200);
						}

						dateViewHandler.removeCallbacks(dateViewTask);
						dateViewHandler.postDelayed(dateViewTask, SCROLLBUTTON_VIEW_TIMEOUT);

						lastFirstVisibleItem = firstVisibleItem;
						if (composeMessageAdapter != null) {
							AbstractMessageModel abstractMessageModel = composeMessageAdapter.getItem(firstVisibleItem);
							if (abstractMessageModel != null) {
								Date createdAt = abstractMessageModel.getCreatedAt();
								if (createdAt != null) {
									dateView.post(() -> {
										dateTextView.setText(LocaleUtil.formatDateRelative(getActivity(), createdAt.getTime()));
									});
								}
							}
						}
					}
				}
				else {
					swipeRefreshLayout.setEnabled(false);
				}
			}
		});

		listViewSwipeListener = new ListViewSwipeListener(
			this.convListView,
			new ListViewSwipeListener.DismissCallbacks() {
				@Override
				public boolean canSwipe(int position) {
					if (actionMode != null) {
						return false;
					}

					if (messageReceiver == null || !messageReceiver.validateSendingPermission(null)) {
						return false;
					}

					int viewType = composeMessageAdapter.getItemViewType(position);

					if (viewType == ComposeMessageAdapter.TYPE_STATUS ||
						viewType == ComposeMessageAdapter.TYPE_FIRST_UNREAD ||
						viewType == ComposeMessageAdapter.TYPE_DATE_SEPARATOR) {
						return false;
					}

					AbstractMessageModel messageModel = composeMessageAdapter.getItem(position);

					if (messageModel == null) {
						return false;
					}

					return QuoteUtil.isQuoteable(messageModel);
				}

				@Override
				public void onSwiped(int position) {
					AbstractMessageModel abstractMessageModel = composeMessageAdapter.getItem(position);
					if (preferenceService.isInAppVibrate()) {
						if (isAdded() && !isDetached() && activity != null) {
							Vibrator vibrator = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
							if (vibrator != null && vibrator.hasVibrator()) {
								vibrator.vibrate(100);
							}
						}
					}
					if (abstractMessageModel != null) {
						if (isQuotePanelShown() && abstractMessageModel.equals(quoteInfo.messageModel)) {
							closeQuoteMode();
						} else {
							startQuoteMode(abstractMessageModel, new Runnable() {
								@Override
								public void run() {
									RuntimeUtil.runOnUiThread(new Runnable() {
										@Override
										public void run() {
											EditTextUtil.showSoftKeyboard(messageText);
										}
									});
								}
							});
						}
					}
				}
			}
		);

		this.quickscrollDownView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				removeScrollButtons();
				scrollList(Integer.MAX_VALUE);
			}
		});

		this.quickscrollUpView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				removeScrollButtons();
				scrollList(0);
			}
		});

		if (sendButton != null) {
			sendButton.setOnClickListener(new DebouncedOnClickListener(500) {
				@Override
				public void onDebouncedClick(View v) {
					sendMessage();
				}
			});
		}
		if (attachButton != null) {
			attachButton.setOnClickListener(new DebouncedOnClickListener(1000) {
				@Override
				public void onDebouncedClick(View v) {
					if (validateSendingPermission()) {
						if (actionMode != null) {
							actionMode.finish();
						}

						closeQuoteMode();

						Intent intent = new Intent(activity, MediaAttachActivity.class);
						IntentDataUtil.addMessageReceiverToIntent(intent, messageReceiver);
						activity.startActivity(intent);
						activity.overridePendingTransition(R.anim.fast_fade_in, R.anim.fast_fade_out);
					}
				}
			});
		}

		this.messageText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if ((actionId == EditorInfo.IME_ACTION_SEND) ||
						(event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && preferenceService.isEnterToSend())) {
					sendMessage();
					return true;
				}
				return false;
			}
		});
		if (ConfigUtils.isDefaultEmojiStyle()) {
			this.messageText.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (emojiPicker != null) {
						if (emojiPicker.isShown()) {
							if (ConfigUtils.isLandscape(activity) &&
									!ConfigUtils.isTabletLayout() &&
									preferenceService.isFullscreenIme()) {
								emojiPicker.hide();
							} else {
								activity.openSoftKeyboard(emojiPicker, messageText);
							}
						}
					}
				}
			});
		}
		this.messageText.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				ThreemaApplication.activityUserInteract(activity);
				updateSendButton(s);
				if (getActivity() != null && getActivity().getCurrentFocus() == messageText) {
					checkPossibleMention(s, start, before, count);
				}
			}


			@Override
			public void afterTextChanged(Editable s) {
				updateCameraButton();
			}
		});
	}

	private void updateCameraButton() {
		if (cameraButton == null || messageText == null) {
			return;
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
			ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {

			// shouldShowRequestPermissionRationale returns false if
			// a) the user selected "never ask again"; or
			// b) a permission dialog has never been shown
			// we hide the camera button only in case a)
			if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) && preferenceService.getCameraPermissionRequestShown()) {
				cameraButton.setVisibility(View.GONE);
				fixMessageTextPadding(View.GONE);
				return;
			}
		}

		int visibility = messageText.getText() == null ||
						messageText.getText().length() == 0 ?
						View.VISIBLE : View.GONE;

		if (cameraButton.getVisibility() != visibility) {
			Transition transition = new Slide(Gravity.RIGHT);
			transition.setDuration(150);
			transition.setInterpolator(new LinearInterpolator());
			transition.addTarget(R.id.camera_button);

			TransitionManager.beginDelayedTransition((ViewGroup) cameraButton.getParent(), transition);
			cameraButton.setVisibility(visibility);

			fixMessageTextPadding(visibility);
		}
	}

	private void fixMessageTextPadding(int visibility) {
		int marginRight = getResources().getDimensionPixelSize(visibility == View.VISIBLE ? R.dimen.emoji_and_photo_button_width : R.dimen.emoji_button_width);
		messageText.setPadding(messageText.getPaddingLeft(), messageText.getPaddingTop(), marginRight, messageText.getPaddingBottom());
	}

	private void checkPossibleMention(CharSequence s, int start, int before, int count) {
		if (isGroupChat && count == 1 && before != count) {
			if (s.length() > 0 && start < s.length()) {
				if (s.charAt(start) == '@') {
					if (start == 0 || s.charAt(start - 1) == ' ' || s.charAt(start - 1) == '\n') {
						if (s.length() <= start + 1 || s.charAt(start + 1) == ' ' || s.charAt(start + 1) == '\n') {
							dismissTooltipPopup(workTooltipPopup, true);
							workTooltipPopup = null;

							dismissMentionPopup();
							mentionPopup = new MentionSelectorPopup(getActivity(), this, groupService, this.contactService, this.userService, this.preferenceService, groupModel);
							mentionPopup.show(getActivity(), this.messageText, emojiButton.getWidth());;
						}
					}
				}
			}
		}
	}

	private void updateSendButton(CharSequence s) {
		if (isQuotePanelShown()) {
			if (TestUtil.empty(s)) {
				sendButton.setEnabled(false);
			} else {
				sendButton.setSend();
				sendButton.setEnabled(true);
			}
		} else {
			if (TestUtil.empty(s)) {
				sendButton.setRecord();
				sendButton.setEnabled(true);
			} else {
				sendButton.setSend();
				sendButton.setEnabled(true);
			}
		}
		if (emojiButton != null) emojiButton.setVisibility(preferenceService.getEmojiStyle() != PreferenceService.EmojiStyle_ANDROID ? View.VISIBLE : View.GONE);
		if (messageText != null) messageText.setVisibility(View.VISIBLE);
	}

	private void setBackgroundWallpaper() {
		if (isAdded() && this.wallpaperView != null) {
			wallpaperService.setupWallpaperBitmap(this.messageReceiver, this.wallpaperView, ConfigUtils.isLandscape(activity));
		}
	}

	private void resetDefaultValues() {
		this.distributionListId = 0;
		this.groupId = 0;
		this.identity = null;

		this.groupModel = null;
		this.distributionListModel = null;
		this.contactModel = null;

		this.messageReceiver = null;
		this.listInstancePosition = AbsListView.INVALID_POSITION;
		this.listInstanceReceiverId = null;

		if (ConfigUtils.isTabletLayout()) {
			// apply pending deletes upon reentering a chat through onNewIntent() in multi-frame environment
			deleteDeleteableMessages();

			if (this.deleteSnackbar != null && this.deleteSnackbar.isShownOrQueued()) {
				this.deleteSnackbar.dismiss();
			}

			closeQuoteMode();
		}
	}

	private void getValuesFromBundle(Bundle bundle) {
		if (bundle != null) {
			this.groupId = bundle.getInt(ThreemaApplication.INTENT_DATA_GROUP, 0);
			this.distributionListId = bundle.getInt(ThreemaApplication.INTENT_DATA_DISTRIBUTION_LIST, 0);
			this.identity = bundle.getString(ThreemaApplication.INTENT_DATA_CONTACT);
			this.intentTimestamp = bundle.getLong(ThreemaApplication.INTENT_DATA_TIMESTAMP, 0L);
			this.cameraUri = bundle.getParcelable(CAMERA_URI);
		}
	}

	public void onNewIntent(Intent intent) {
		logger.debug("onNewIntent");

		if (!requiredInstances()) {
			return;
		}

		resetDefaultValues();
		handleIntent(intent);

		// initialize various toolbar items
		if (actionMode != null) {
			actionMode.finish();
		}
		if (searchActionMode != null) {
			searchActionMode.finish();
		}
		this.closeQuoteMode();
		this.updateToolbarTitle();
		this.updateMenus();
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private void setupToolbar() {
		View actionBarTitleView = layoutInflater.inflate(R.layout.actionbar_compose_title, null);

		if (actionBarTitleView != null) {
			this.actionBarTitleTextView = actionBarTitleView.findViewById(R.id.title);
			this.actionBarSubtitleImageView = actionBarTitleView.findViewById(R.id.subtitle_image);
			this.actionBarSubtitleTextView = actionBarTitleView.findViewById(R.id.subtitle_text);
			this.actionBarAvatarView = actionBarTitleView.findViewById(R.id.avatar_view);
			final RelativeLayout actionBarTitleContainer = actionBarTitleView.findViewById(R.id.title_container);
			actionBarTitleContainer.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent intent = null;
					if (isGroupChat) {
						if (groupService.isGroupMember(groupModel)) {
							intent = new Intent(activity, GroupDetailActivity.class);
						}
					} else if (isDistributionListChat) {
						intent = new Intent(activity, DistributionListAddActivity.class);
					} else {
						intent = new Intent(activity, ContactDetailActivity.class);
						intent.putExtra(ThreemaApplication.INTENT_DATA_CONTACT_READONLY, true);
					}
					if (intent != null) {
						intent = addExtrasToIntent(intent, messageReceiver);

						activity.startActivityForResult(intent, 0);
					}
				}
			});

			if (contactModel != null) {
				if (contactModel.getType() == IdentityType.WORK) {
					if (!ConfigUtils.isWorkBuild()) {
						if (!preferenceService.getIsWorkHintTooltipShown()) {
							actionBarTitleTextView.postDelayed(() -> {
								if (getActivity() != null && isAdded()) {
									dismissTooltipPopup(workTooltipPopup, true);

									int[] location = new int[2];
									actionBarAvatarView.getLocationOnScreen(location);
									location[0] += actionBarAvatarView.getWidth() / 2;
									location[1] += actionBarAvatarView.getHeight();

									workTooltipPopup = new TooltipPopup(getActivity(), R.string.preferences__tooltip_work_hint_shown, R.layout.popup_tooltip_top_left_work, this, new Intent(getActivity(), WorkExplainActivity.class));
									workTooltipPopup.show(getActivity(), actionBarAvatarView, getString(R.string.tooltip_work_hint), TooltipPopup.ALIGN_BELOW_ANCHOR_ARROW_LEFT, location, 4000);
								}
							}, 1000);
						}
					}
				} else {
					if (!preferenceService.getIsVideoCallTooltipShown()) {
						if (ContactUtil.canReceiveVoipMessages(contactModel, blackListIdentityService)
							&& ConfigUtils.isCallsEnabled(getContext(), preferenceService, licenseService)) {
							View toolbar = ((ThreemaToolbarActivity) getActivity()).getToolbar();

							toolbar.postDelayed(() -> {
								if (getActivity() != null && isAdded()) {
									int[] location = new int[2];
									View itemView = toolbar.findViewById(R.id.menu_threema_call);
									if (itemView != null) {
										itemView.getLocationInWindow(location);
										if (ConfigUtils.isVideoCallsEnabled()) {
											try {
												TapTargetView.showFor(getActivity(),
													TapTarget.forView(itemView, getString(R.string.video_calls_new), getString(R.string.tooltip_video_call))
														.outerCircleColor(ConfigUtils.getAppTheme(getActivity()) == ConfigUtils.THEME_DARK ? R.color.accent_dark : R.color.accent_light)      // Specify a color for the outer circle
														.outerCircleAlpha(0.96f)            // Specify the alpha amount for the outer circle
														.targetCircleColor(android.R.color.white)   // Specify a color for the target circle
														.titleTextSize(24)                  // Specify the size (in sp) of the title text
														.titleTextColor(android.R.color.white)      // Specify the color of the title text
														.descriptionTextSize(18)            // Specify the size (in sp) of the description text
														.descriptionTextColor(android.R.color.white)  // Specify the color of the description text
														.textColor(android.R.color.white)            // Specify a color for both the title and description text
														.textTypeface(Typeface.SANS_SERIF)  // Specify a typeface for the text
														.dimColor(android.R.color.black)            // If set, will dim behind the view with 30% opacity of the given color
														.drawShadow(true)                   // Whether to draw a drop shadow or not
														.cancelable(true)                  // Whether tapping outside the outer circle dismisses the view
														.tintTarget(true)                   // Whether to tint the target view's color
														.transparentTarget(false)           // Specify whether the target is transparent (displays the content underneath)
														.targetRadius(50),                  // Specify the target radius (in dp)
													new TapTargetView.Listener() {          // The listener can listen for regular clicks, long clicks or cancels
														@Override
														public void onTargetClick(TapTargetView view) {
															super.onTargetClick(view);
															String name = NameUtil.getDisplayNameOrNickname(contactModel, false);

															GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.threema_call, String.format(getContext().getString(R.string.voip_call_confirm), name), R.string.ok, R.string.cancel);
															dialog.setTargetFragment(ComposeMessageFragment.this, 0);
															dialog.show(getFragmentManager(), ComposeMessageFragment.DIALOG_TAG_CONFIRM_CALL);
														}
													});
												preferenceService.setVideoCallTooltipShown(true);
											} catch (Exception ignore) {
												// catch null typeface exception on CROSSCALL Action-X3
											}
										}
									}
								}
							}, 1000);
						}
					}
				}
			}
		}

		if (activity == null) {
			activity = (ComposeMessageActivity) getActivity();
		}

		if (activity != null) {
			this.actionBar = activity.getSupportActionBar();
			if (actionBar != null) {
				actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_HOME_AS_UP);
				actionBar.setCustomView(actionBarTitleView);
			}
		}
	}

	private void handleIntent(Intent intent) {
		logger.debug("handleIntent");
		String conversationUid;
		this.isGroupChat = false;
		this.isDistributionListChat = false;
		this.currentPageReferenceId = null;

		//remove old indicator every time!
		//fix ANDR-432
		if (this.typingIndicatorTextWatcher != null) {
			if (this.messageText != null) {
				this.messageText.removeTextChangedListener(this.typingIndicatorTextWatcher);
			}
		}
		if (intent.hasExtra(ThreemaApplication.INTENT_DATA_GROUP) || this.groupId != 0) {
			this.isGroupChat = true;
			if (this.groupId == 0) {
				this.groupId = intent.getIntExtra(ThreemaApplication.INTENT_DATA_GROUP, 0);
			}
			this.groupModel = this.groupService.getById(this.groupId);

			if (this.groupModel == null || this.groupModel.isDeleted()) {
				logger.error(activity.getString(R.string.group_not_found), activity, new Runnable() {
					@Override
					public void run() {
						activity.finish();
					}
				});
				return;
			}
			// we dont want to handle the same intent twice
//			if (!ConfigUtils.isTabletLayout()) {
			intent.removeExtra(ThreemaApplication.INTENT_DATA_GROUP);
//			}
			this.messageReceiver = this.groupService.createReceiver(this.groupModel);
			conversationUid = ConversationUtil.getGroupConversationUid(this.groupId);
		} else if (intent.hasExtra(ThreemaApplication.INTENT_DATA_DISTRIBUTION_LIST) || this.distributionListId != 0) {
			this.isDistributionListChat = true;

			try {
				if (this.distributionListId == 0) {
					this.distributionListId = intent.getIntExtra(ThreemaApplication.INTENT_DATA_DISTRIBUTION_LIST, 0);
				}
				this.distributionListModel = distributionListService.getById(this.distributionListId);

				if (this.distributionListModel == null) {
					logger.error("Invalid distribution list", activity, new Runnable() {
						@Override
						public void run() {
							activity.finish();
						}
					});
					return;
				}

				intent.removeExtra(ThreemaApplication.INTENT_DATA_DISTRIBUTION_LIST);

				this.messageReceiver = distributionListService.createReceiver(this.distributionListModel);
			} catch (Exception e) {
				logger.error("Exception", e);
				return;
			}
			conversationUid = ConversationUtil.getDistributionListConversationUid(this.distributionListId);
		} else {
			if (TestUtil.empty(this.identity)) {
				this.identity = intent.getStringExtra(ThreemaApplication.INTENT_DATA_CONTACT);
			}

			if (this.identity == null) {
				if (intent.getData() != null) {
					if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
						this.identity = ContactUtil.getIdentityFromViewIntent(activity, intent);
					} else {
						Toast.makeText(activity, R.string.permission_contacts_required, Toast.LENGTH_LONG).show();
					}
				}
			}

			// we dont want to handle the same intent twice (but we need it in the other pane of the tablet)
//			if (!ConfigUtils.isTabletLayout()) {
			intent.removeExtra(ThreemaApplication.INTENT_DATA_CONTACT);
//			}

			if (this.identity == null || this.identity.length() == 0 || this.identity.equals(this.userService.getIdentity())) {
				logger.error("no identity found");
				activity.finish();
				return;
			}

			this.contactModel = this.contactService.getByIdentity(this.identity);
			if (this.contactModel == null) {
				Toast.makeText(getContext(), getString(R.string.contact_not_found) + ": " + this.identity, Toast.LENGTH_LONG).show();
				Intent homeIntent = new Intent(activity, HomeActivity.class);
				homeIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivity(homeIntent);
				activity.overridePendingTransition(0, 0);
				activity.finish();
				return;
			}
			this.messageReceiver = this.contactService.createReceiver(this.contactModel);
			this.typingIndicatorTextWatcher = new TypingIndicatorTextWatcher(this.userService, contactModel);
			conversationUid = ConversationUtil.getIdentityConversationUid(this.identity);
		}

		if (this.messageReceiver == null) {
			logger.error("invalid receiver", activity, new Runnable() {
				@Override
				public void run() {
					activity.finish();
				}
			});
			return;
		}

		// set wallpaper based on message receiver
		this.setBackgroundWallpaper();

		this.unreadCount = initConversationList();

		// work around the problem that the same original intent may be sent
		// each time a singleTop activity (like this one) is coming back to front
		// causing - in this case - duplicate delivery of forwarded messages.
		// so we make sure the intent is only handled if it's newer than
		// any previously handled intent
		long newTimestamp = 0L;
		try {
			newTimestamp = intent.getLongExtra(ThreemaApplication.INTENT_DATA_TIMESTAMP, 0L);
			if (newTimestamp != 0L && newTimestamp <= this.intentTimestamp) {
				return;
			}
		} finally {
			this.intentTimestamp = newTimestamp;
		}

		this.messageText.setText("");
		this.messageText.setMessageReceiver(this.messageReceiver);
		this.openBallotNoticeView.setMessageReceiver(this.messageReceiver);
		this.openBallotNoticeView.setVisibilityListener(this);

		// restore draft before setting predefined text
		restoreMessageDraft();

		String defaultText = intent.getStringExtra(ThreemaApplication.INTENT_DATA_TEXT);
		if (!TestUtil.empty(defaultText)) {
			this.messageText.append(defaultText);
		}

		updateSendButton(this.messageText.getText());
		updateCameraButton();

		boolean editFocus = intent.getBooleanExtra(ThreemaApplication.INTENT_DATA_EDITFOCUS, false);
		if (editFocus || this.unreadCount <= 0) {
			messageText.setSelected(true);
			messageText.requestFocus();
		}

		this.notificationService.setVisibleReceiver(this.messageReceiver);

		if (!this.isGroupChat && !this.isDistributionListChat) {
			this.messageText.addTextChangedListener(this.typingIndicatorTextWatcher);
		}

		ListenerManager.chatListener.handle(listener -> listener.onChatOpened(conversationUid));

		if (this.hiddenChatsListService.has(this.messageReceiver.getUniqueIdString())) {
			// hide chat from view and prevent screenshots - may not work on some devices
			try {
				getActivity().getWindow().addFlags(FLAG_SECURE);
			} catch (Exception e) {
				//
			}
		}

		if (intent.hasExtra(EXTRA_API_MESSAGE_ID) && intent.hasExtra(EXTRA_SEARCH_QUERY)) {
			String apiMessageId = intent.getStringExtra(EXTRA_API_MESSAGE_ID);
			String searchQuery = intent.getStringExtra(EXTRA_SEARCH_QUERY);

			AbstractMessageModel targetMessageModel = messageService.getMessageModelByApiMessageId(apiMessageId, messageReceiver.getType());

			if (targetMessageModel != null && !TestUtil.empty(apiMessageId) && !TestUtil.empty(searchQuery)) {
				String identity;
				if (targetMessageModel instanceof GroupMessageModel) {
					identity = targetMessageModel.isOutbox() ? contactService.getMe().getIdentity() : targetMessageModel.getIdentity();
				} else {
					identity = targetMessageModel.getIdentity();
				}

				QuoteUtil.QuoteContent quoteContent = QuoteUtil.QuoteContent.createV2(
					identity,
					searchQuery,
					searchQuery,
					apiMessageId,
					targetMessageModel,
					messageReceiver.getType(),
					null,
					null
				);

				ComposeMessageAdapter.ConversationListFilter filter = (ComposeMessageAdapter.ConversationListFilter) composeMessageAdapter.getQuoteFilter(quoteContent);
				searchV2Quote(apiMessageId, filter);
			} else {
				Toast.makeText(getContext().getApplicationContext(), R.string.message_not_found, Toast.LENGTH_SHORT).show();
			}
		}
	}

	private boolean validateSendingPermission() {
		return this.messageReceiver != null
				&& this.messageReceiver.validateSendingPermission(new MessageReceiver.OnSendingPermissionDenied() {
					@Override
					public void denied(final int errorResId) {
						RuntimeUtil.runOnUiThread(() -> SingleToast.getInstance().showLongText(getString(errorResId)));
					}
				});
	}

	private void deleteSelectedMessages() {
		int deleteableMessagesCount = 0;

		if (selectedMessages != null) {
			// sort highest first for removal
			Collections.sort(selectedMessages, new Comparator<AbstractMessageModel>() {
				@Override
				public int compare(AbstractMessageModel lhs, AbstractMessageModel rhs) {
					return rhs.getId() - lhs.getId();
				}
			});

			synchronized (deleteableMessages) {
				for (AbstractMessageModel messageModel : selectedMessages) {
					if (messageModel != null) {
						// remove from adapter but not from database
						int position = composeMessageAdapter.getPosition(messageModel);

						AbstractMessageModel previousMessage = null;
						if (position > 0) {
							previousMessage = composeMessageAdapter.getItem(position - 1);
						}

						if (previousMessage != null && previousMessage instanceof DateSeparatorMessageModel) {
							AbstractMessageModel nextMessage = null;
							if (position < (composeMessageAdapter.getCount() - 1)) {
								nextMessage = composeMessageAdapter.getItem(position + 1);
							}
							if (nextMessage == null ||
									!dayFormatter.format(messageModel.getCreatedAt()).equals(dayFormatter.format(nextMessage.getCreatedAt()))) {
								deleteableMessages.add(new Pair<>(previousMessage, position - 1));
								composeMessageAdapter.remove(previousMessage);
							}
						}

						deleteableMessages.add(new Pair<>(messageModel, position));
						deleteableMessagesCount++;
						composeMessageAdapter.remove(messageModel);
					}
				}
				composeMessageAdapter.notifyDataSetChanged();

				// sort lowest first for insertion
				Collections.sort(deleteableMessages, new Comparator<Pair<AbstractMessageModel, Integer>>() {
					@Override
					public int compare(Pair<AbstractMessageModel, Integer> lhs, Pair<AbstractMessageModel, Integer> rhs) {
						return lhs.second - rhs.second;
					}
				});
			}
			selectedMessages.clear();

			if (actionMode != null) {
				actionMode.finish();
			}

			try {
				deleteSnackbar = Snackbar.make(coordinatorLayout, deleteableMessagesCount + " " + getString(R.string.message_deleted), 7 * (int) DateUtils.SECOND_IN_MILLIS);
				deleteSnackbar.setAction(R.string.message_delete_undo, v -> RuntimeUtil.runOnUiThread(this::undoDeleteMessages));
				deleteSnackbar.setCallback(new Snackbar.Callback() {
					@Override
					public void onDismissed(Snackbar snackbar, int event) {
						super.onDismissed(snackbar, event);

						if (event != DISMISS_EVENT_ACTION && event != DISMISS_EVENT_CONSECUTIVE && event != DISMISS_EVENT_MANUAL) {
								RuntimeUtil.runOnUiThread(() -> deleteDeleteableMessages());
						}
					}
				});
				deleteSnackbar.show();
			} catch (Exception e) {
				logger.debug("https://issuetracker.google.com/issues/63793040");
				RuntimeUtil.runOnUiThread(this::undoDeleteMessages);
			}

		} else {
			if (actionMode != null) {
				actionMode.finish();
			}
		}
	}

	private void undoDeleteMessages() {
		synchronized (deleteableMessages) {
			for (Pair<AbstractMessageModel, Integer> m : deleteableMessages) {
				composeMessageAdapter.insert(m.first, m.second);
			}
			deleteableMessages.clear();
		}
		composeMessageAdapter.notifyDataSetChanged();
	}

	private synchronized void deleteDeleteableMessages() {
		if (deleteableMessages.size() > 0) {
			synchronized (deleteableMessages) {
				for (Pair<AbstractMessageModel, Integer> m : deleteableMessages) {
					if (m != null) {
						messageService.remove(m.first);
					}
				}
				deleteableMessages.clear();

				if (messageReceiver != null) {
					if (messageReceiver.getMessagesCount() <= 0 && messageReceiver instanceof ContactMessageReceiver) {
						conversationService.clear(messageReceiver);
					} else {
						conversationService.refresh(messageReceiver);
					}
				}
			}
		}
	}

	@UiThread
	private void contactTypingStateChanged(boolean isTyping) {
		RuntimeUtil.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if(isTypingView != null) {
					logger.debug("is typing " + isTyping + " footer view count " + convListView.getFooterViewsCount());
					if (isTyping) {
						//remove if the the another footer element added
						if(convListView.getFooterViewsCount() == 0) {
							isTypingView.setVisibility(View.VISIBLE);
							convListView.addFooterView(isTypingView, null, false);
						}
					} else {
						removeIsTypingFooter();
					}
				}
			}
		});
	}

	private void removeIsTypingFooter() {
		if (isTypingView != null) {
			isTypingView.setVisibility(View.GONE);
			if (convListView != null && convListView.getFooterViewsCount() > 0){
				convListView.removeFooterView(isTypingView);
			}
		}
	}

	@UiThread
	private boolean addMessageToList(AbstractMessageModel message, boolean removeUnreadBar) {
		if (message == null || this.messageReceiver == null) {
			return false;
		}

		//check if the message already added
		if (this.listInitializedAt != null && message.getCreatedAt().before(this.listInitializedAt)) {
			return false;
		}

		if (!this.messageReceiver.isMessageBelongsToMe(message)) {
			//do nothing, not my thread
			return false;
		}

		logger.debug("addMessageToList: started");

		if (removeUnreadBar) {
			this.composeMessageAdapter.removeFirstUnreadPosition();
		}

		// if previous message is from another date, add a date separator
		synchronized (this.messageValues) {
			int size = this.messageValues.size();
			Date date = new Date();
			Date createdAt = size > 0 ? this.messageValues.get(size - 1).getCreatedAt() : new Date(0L);
			if (!dayFormatter.format(createdAt).equals(dayFormatter.format(date))) {
				final DateSeparatorMessageModel dateSeparatorMessageModel = new DateSeparatorMessageModel();
				dateSeparatorMessageModel.setCreatedAt(date);
				this.messageValues.add(size, dateSeparatorMessageModel);
			}
		}

		this.composeMessageAdapter.add(message);

		if (!this.isPaused) {
			new Thread(
					new ReadMessagesRoutine(Arrays.asList(message),
							this.messageService,
							this.notificationService)
			).start();
		} else {
			this.unreadMessages.add(message);
		}

		if (message.isOutbox()) {
			// scroll to bottom on outgoing message
			scrollList(Integer.MAX_VALUE);
		}

		logger.debug("addMessageToList: finished");

		return true;
	}

	@UiThread
	private void scrollList(final int targetPosition) {
		logger.debug("scrollList " + targetPosition);

		if (this.listUpdateInProgress) {
			logger.debug("Update in progress");
			return;
		}
		this.composeMessageAdapter.notifyDataSetChanged();
		this.convListView.post(new Runnable() {
			@Override
			public void run() {
				int topEntry = convListView.getFirstVisiblePosition();

				// update only if really necessary
				if (targetPosition != topEntry) {
					listUpdateInProgress = true;

					int listEntryCount = convListView.getCount();

					if (topEntry > targetPosition) {
						// scroll up
						int startPosition = targetPosition + SMOOTHSCROLL_THRESHOLD;

						if (startPosition < listEntryCount) {
							convListView.setSelection(targetPosition);
						} else {
							convListView.smoothScrollToPosition(targetPosition);
						}
					} else {
						// scroll down
						int startPosition = listEntryCount - SMOOTHSCROLL_THRESHOLD;

						if (listEntryCount - convListView.getLastVisiblePosition() > SMOOTHSCROLL_THRESHOLD && startPosition > 0) {
							convListView.setSelection(targetPosition);
						} else {
							convListView.smoothScrollToPosition(targetPosition);
						}
					}
					listUpdateInProgress = false;
				}
			}
		});
	}

	/**
	 * Loading the next records for the listview
	 */
	private List<AbstractMessageModel> getNextRecords() {
		List<AbstractMessageModel> messageModels = this.messageService.getMessagesForReceiver(this.messageReceiver, this.nextMessageFilter);
		this.valuesLoaded(messageModels);
		return messageModels;
	}

	private List<AbstractMessageModel> getAllRecords() {
		List<AbstractMessageModel> messageModels = this.messageService.getMessagesForReceiver(this.messageReceiver);
		this.valuesLoaded(messageModels);
		return messageModels;
	}

	/**
	 * Appending Records to the list
	 * do not call the notfiy on change on adding to speed up list ctrl
	 */
	/**
	 * Append records to the list, adding date separators if necessary
	 * @param values MessageModels to insert
	 * @param clear Whether previous list entries should be cleared before appending
	 * @param markasread Whether chat should be marked as read
	 * @return Number of items that have been added to the list INCLUDING date separators and other decoration
	 */
	private int insertToList(final List<AbstractMessageModel> values, boolean clear, boolean markasread) {
		this.composeMessageAdapter.setNotifyOnChange(false);

		int insertedSize = 0;
		synchronized (this.messageValues) {
			int initialSize = this.messageValues.size();

			Date date = new Date();
			if (clear) {
				this.messageValues.clear();
			} else {
				// prevent duplicate date separators when adding messages to an existing chat (e.g. after pull-to-refresh)
				if (this.messageValues.size() > 0) {
					if (this.messageValues.get(0) instanceof DateSeparatorMessageModel) {
						this.messageValues.remove(0);
					}
					AbstractMessageModel topmostMessage = this.messageValues.get(0);
					if (topmostMessage != null) {
						Date topmostDate = topmostMessage.getCreatedAt();
						if (topmostDate != null) {
							date = topmostDate;
						}
					}
				}
			}

			for (AbstractMessageModel m : values) {
				Date createdAt = m.getCreatedAt();
				if (createdAt != null) {
					if (!dayFormatter.format(createdAt).equals(dayFormatter.format(date))) {
						if (!this.messageValues.isEmpty()) {
							final DateSeparatorMessageModel dateSeparatorMessageModel = new DateSeparatorMessageModel();
							dateSeparatorMessageModel.setCreatedAt(this.messageValues.get(0).getCreatedAt());
							this.messageValues.add(0, dateSeparatorMessageModel);
						}
						date = createdAt;
					}
				}

				this.messageValues.add(0, m);
			}

			if (!this.messageValues.isEmpty() && !(this.messageValues.get(0) instanceof DateSeparatorMessageModel)) {
				// add topmost date separator
				final DateSeparatorMessageModel dateSeparatorMessageModel = new DateSeparatorMessageModel();
				dateSeparatorMessageModel.setCreatedAt(this.messageValues.get(0).getCreatedAt());
				this.messageValues.add(0, dateSeparatorMessageModel);
			}

			this.listInitializedAt = new Date();

			insertedSize = this.messageValues.size() - initialSize;
		}

		this.composeMessageAdapter.setNotifyOnChange(true);

		if (clear) {
			//invalidate list to rebuild the views
			this.composeMessageAdapter.notifyDataSetInvalidated();
		}

		if (markasread && this.messageReceiver != null) {
			try {
				List<AbstractMessageModel> unreadMessages = this.messageReceiver.getUnreadMessages();
				if (unreadMessages != null && unreadMessages.size() > 0) {
					new Thread(new ReadMessagesRoutine(unreadMessages, this.messageService, this.notificationService)).start();
				}
			} catch (SQLException e) {
				logger.error("Exception", e);
			}
		}
		return insertedSize;
	}

	private void valuesLoaded(List<AbstractMessageModel> values) {
		if(values != null && values.size() > 0) {
			this.currentPageReferenceId = values.get(values.size()-1).getId();
		}
	}
	/**
	 * initialize conversation list and return the unread message count
	 *
	 * @return
	 */
	private int initConversationList() {

		final int unreadCount = (int) this.messageReceiver.getUnreadMessagesCount();

		final List<AbstractMessageModel> values;
		if(unreadCount > MESSAGE_PAGE_SIZE) {
			//do not use next record, create a "custom" selector
			//load ALL unread messages.
			values = this.messageService.getMessagesForReceiver(this.messageReceiver, new MessageService.MessageFilter() {
				@Override
				public long getPageSize() {
					return unreadCount;
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
					return false;
				}

				@Override
				public MessageType[] types() {
					return new MessageType[0];
				}

				@Override
				public int[] contentTypes() {
					return null;
				}
			});

			this.valuesLoaded(values);
		}
		else {
			values = this.getNextRecords();
		}

		if (this.composeMessageAdapter != null) {
			// re-use existing adapter (for example on tablets)
			this.composeMessageAdapter.clear();
			this.composeMessageAdapter.setThumbnailWidth(ConfigUtils.getPreferredThumbnailWidth(getContext(), false));
			this.composeMessageAdapter.setGroupId(groupId);
			this.composeMessageAdapter.setMessageReceiver(this.messageReceiver);
			this.insertToList(values, true, true);
			updateToolbarTitle();
		} else {
			this.thumbnailCache = new ThumbnailCache<Integer>(null);

			this.composeMessageAdapter = new ComposeMessageAdapter(
					this.activity,
					this.messagePlayerService,
					this.messageValues,
					this.userService,
					this.contactService,
					this.fileService,
					this.messageService,
					this.ballotService,
					this.preferenceService,
					this.downloadService,
					this.licenseService,
					this.messageReceiver,
					this.convListView,
					this.thumbnailCache,
					ConfigUtils.getPreferredThumbnailWidth(getContext(), false),
					this
			);

			//adding footer before setting the list adapter (android < 4.4)
			if(null != this.convListView && !this.isGroupChat && !this.isDistributionListChat) {
				//create the istyping instance for later use
				this.isTypingView = layoutInflater.inflate(R.layout.conversation_list_item_typing, null);
				this.convListView.addFooterView(this.isTypingView, null, false);
			}

			this.composeMessageAdapter.setGroupId(groupId);
			this.composeMessageAdapter.setOnClickListener(new ComposeMessageAdapter.OnClickListener() {
				@Override
				public void resend(AbstractMessageModel messageModel) {
					if (messageModel.isOutbox() && messageModel.getState() == MessageState.SENDFAILED && messageReceiver.isMessageBelongsToMe(messageModel)) {
						try {
							messageService.resendMessage(messageModel, messageReceiver, null);
						} catch (Exception e) {
							RuntimeUtil.runOnUiThread(new Runnable() {
								@Override
								public void run() {
									Toast.makeText(getContext(), R.string.original_file_no_longer_avilable, Toast.LENGTH_LONG).show();
								}
							});
						}
					}
				}

				@Override
				public void click(View view, int position, AbstractMessageModel messageModel) {
					if (searchActionMode == null) {
						onListItemClick(view, position, messageModel);
					}
				}

				@Override
				public void longClick(View view, int position, AbstractMessageModel messageModel) {
					if (searchActionMode == null) {
						onListItemLongClick(view, position);
					}
				}

				@Override
				public boolean touch(View view, MotionEvent motionEvent, AbstractMessageModel messageModel) {
					if (listViewSwipeListener != null && searchActionMode == null) {
						return listViewSwipeListener.onTouch(view, motionEvent);
					}
					return false;
				}

				@Override
				public void avatarClick(View view, int position, AbstractMessageModel messageModel) {
					if (messageModel != null && messageModel.getIdentity() != null) {
						ContactModel contactModel = contactService.getByIdentity(messageModel.getIdentity());
						if (contactModel != null) {
							Intent intent;
							if (messageModel instanceof GroupMessageModel || messageModel instanceof DistributionListMessageModel) {
								intent = new Intent(getActivity(), ComposeMessageActivity.class);
								intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
								intent.setData((Uri.parse("foobar://" + SystemClock.elapsedRealtime())));
								IntentDataUtil.append(contactModel, intent);
								getActivity().finish();

							} else {
								intent = new Intent(getActivity(), ContactDetailActivity.class);
								intent.putExtra(ThreemaApplication.INTENT_DATA_CONTACT_READONLY, true);
								IntentDataUtil.append(contactModel, intent);
							}
							AnimationUtil.startActivityForResult(getActivity(), view, intent, 0);
						}
					}
				}

				@SuppressLint("DefaultLocale")
				@Override
				public void onSearchResultsUpdate(int searchResultsIndex, int searchResultsSize) {
					RuntimeUtil.runOnUiThread(() -> {
						if (searchCounter != null) {
							try {
								searchCounter.setText(String.format("%d / %d", searchResultsIndex, searchResultsSize));
							} catch (Exception e) {
								//
							}
						}
					});
				}

				@Override
				public void onSearchInProgress(boolean inProgress) {
					RuntimeUtil.runOnUiThread(() -> {
						if (searchNextButton != null && searchPreviousButton != null) {
							try {
								searchPreviousButton.setVisibility(inProgress ? View.INVISIBLE : View.VISIBLE);
								searchNextButton.setVisibility(inProgress ? View.INVISIBLE : View.VISIBLE);
								searchProgress.setVisibility(inProgress ? View.VISIBLE : View.INVISIBLE);
							} catch (Exception e) {
								//
							}
						}
					});
				}
			});
			this.insertToList(values, false, true);
			this.convListView.setAdapter(this.composeMessageAdapter);
			this.convListView.setItemsCanFocus(false);
			this.convListView.setVisibility(View.VISIBLE);
		}

		setIdentityColors();

		//hack for android < 4.4.... remove footer after adding
		removeIsTypingFooter();

		return unreadCount;
	}

	private void setIdentityColors() {
		logger.debug("setIdentityColors");

		if (this.isGroupChat) {
			Map<String, Integer> colors = this.groupService.getGroupMemberColors(this.groupModel);

			if (ConfigUtils.getAppTheme(activity) == ConfigUtils.THEME_DARK) {
				Map<String, Integer> darkColors = new HashMap<>();
				@ColorInt final int bubbleColorRecv = activity.getResources().getColor(R.color.dark_bubble_recv);

				// lighten up some colors to ensure better visibility if dark theme is enabled
				for (Map.Entry<String, Integer> entry : colors.entrySet()) {
					@ColorInt int newColor;

					try {
						newColor = entry.getValue();

						if (ColorUtils.calculateContrast(newColor, bubbleColorRecv) <= 1.7) {
							float[] hsl = new float[3];
							ColorUtils.colorToHSL(entry.getValue(), hsl);
							if (hsl[2] < 0.7f) {
								hsl[2] = 0.7f; // pull up luminance
							}
							if (hsl[1] > 0.6f) {
								hsl[1] = 0.6f; // tome down saturation
							}
							newColor = ColorUtils.HSLToColor(hsl);
						}
						darkColors.put(entry.getKey(), newColor);
					} catch (Exception e) {
						//
					}
				}
				this.identityColors = darkColors;
			} else {
				this.identityColors = colors;
			}
		} else {
			if (this.identityColors != null) {
				this.identityColors.clear();
			}
		}
		this.composeMessageAdapter.setIdentityColors(this.identityColors);
	}

	private void onListItemClick(View view, int position, AbstractMessageModel messageModel) {
		if (view == null) {
			return;
		}

		if (actionMode != null) {
			if (selectedMessages.contains(messageModel)) {
				// remove from selection
				selectedMessages.remove(messageModel);
				convListView.setItemChecked(position, false);
			} else {
				if (convListView.getCheckedItemCount() < MAX_SELECTED_ITEMS) {
					// add this to selection
					selectedMessages.add(messageModel);
					convListView.setItemChecked(position, true);
				} else {
					convListView.setItemChecked(position, false);
				}
			}

			final int checked = convListView.getCheckedItemCount();
			if (checked > 0) {
				// invalidate menu to update display => onPrepareActionMode()
				actionMode.invalidate();
			} else {
				actionMode.finish();
			}
		} else {
			if (view.isSelected()) {
				view.setSelected(false);
			}
			if (convListView.isItemChecked(position)) {
				convListView.setItemChecked(position, false);
			}
			// check if item is a quote
			if (QuoteUtil.isQuoteV1(messageModel.getBody())) {
				QuoteUtil.QuoteContent quoteContent = QuoteUtil.getQuoteContent(
					messageModel,
					messageReceiver.getType(),
					false,
					thumbnailCache,
					getContext(),
					this.messageService,
					this.userService,
					this.fileService
				);

				if (quoteContent != null) {
					if (searchActionMode != null) {
						searchActionMode.finish();
					}

					ComposeMessageAdapter.ConversationListFilter filter = (ComposeMessageAdapter.ConversationListFilter) composeMessageAdapter.getQuoteFilter(quoteContent);
					// search for quoted text
					filter.filter(quoteContent.quotedText, count -> {
						if (count == 0) {
							SingleToast.getInstance().showShortText(getString(R.string.quote_not_found));
						}
					});
				}
			} else if (messageModel.getQuotedMessageId() != null) {
				QuoteUtil.QuoteContent quoteContent = QuoteUtil.getQuoteContent(
					messageModel,
					messageReceiver.getType(),
					false,
					thumbnailCache,
					getContext(),
					this.messageService,
					this.userService,
					this.fileService
				);
				if (quoteContent != null) {
					if (searchActionMode != null) {
						searchActionMode.finish();
					}

					AbstractMessageModel quotedMessageModel = messageService.getMessageModelByApiMessageId(messageModel.getQuotedMessageId(), messageReceiver.getType());
					if (quotedMessageModel != null) {
						ComposeMessageAdapter.ConversationListFilter filter = (ComposeMessageAdapter.ConversationListFilter) composeMessageAdapter.getQuoteFilter(quoteContent);
						searchV2Quote(quotedMessageModel.getApiMessageId(), filter);
					} else {
						Toast.makeText(getContext().getApplicationContext(), R.string.quoted_message_deleted, Toast.LENGTH_SHORT).show();
					}
				}
			}
		}
	}

	/**
	 * Recursively search for message with provided apiMessageId in chat and gradually load more records to Adapter until matching message is found by provided Filter
	 * TODO: we should provide a static version of this that does not rely on globals
	 * @param apiMessageId to search for
	 * @param filter Filter to use for this search
	 */
	@UiThread
	private void searchV2Quote(final String apiMessageId, final ComposeMessageAdapter.ConversationListFilter filter) {
		filter.filter("#" + apiMessageId, new Filter.FilterListener() {
			@SuppressLint("StaticFieldLeak")
			@Override
			public void onFilterComplete(int count) {
				if (count == 0) {
					new AsyncTask<Void, Void, Integer>() {
						@Override
						protected Integer doInBackground(Void... params) {

							values = getNextRecords();
							if (values != null) {
								int numNewRecords = values.size();
								hastNextRecords = numNewRecords >= nextMessageFilter.getPageSize();
								insertToList(values, false, false);
								return numNewRecords;
							}
							return null;
						}

						@Override
						protected void onPostExecute(Integer result) {
							if (getContext() != null) {
								if (result != null && result > 0) {
									if (getFragmentManager() != null) {
										if (getFragmentManager().findFragmentByTag(DIALOG_TAG_SEARCHING) == null) {
											GenericProgressDialog.newInstance(R.string.searching, R.string.please_wait).show(getFragmentManager(), DIALOG_TAG_SEARCHING);
										}
										searchV2Quote(apiMessageId, filter);
									}
								} else {
									SingleToast.getInstance().showShortText(getString(R.string.quote_not_found));
									swipeRefreshLayout.setEnabled(false);
								}
							}
						}
					}.execute();
				} else {
					DialogUtil.dismissDialog(getFragmentManager(), DIALOG_TAG_SEARCHING, true);
				}
			}
		});
	}

	private boolean onListItemLongClick(View view, final int position) {
		int viewType = composeMessageAdapter.getItemViewType(position);
		if (viewType == ComposeMessageAdapter.TYPE_FIRST_UNREAD  || viewType == ComposeMessageAdapter.TYPE_DATE_SEPARATOR) {
			return false;
		}

		selectedMessages.clear();
		selectedMessages.add(composeMessageAdapter.getItem(position));

		if (actionMode != null) {
			convListView.clearChoices();
			convListView.setItemChecked(position, true);
			actionMode.invalidate();
		} else {
			convListView.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
			convListView.setItemChecked(position, true);
			view.setSelected(true);
			actionMode = activity.startSupportActionMode(new ComposeMessageAction(position));
		}

		// fix linkify on longclick problem
		// see: http://stackoverflow.com/questions/16047215/android-how-to-stop-linkify-on-long-press
		longClickItem = position;

		return true;
	}

	private boolean isMuted() {
		if (messageReceiver != null && mutedChatsListService != null) {
			String uniqueId = messageReceiver.getUniqueIdString();
			return !TestUtil.empty(uniqueId) && mutedChatsListService.has(uniqueId);
		}
		return false;
	}

	private boolean isMentionsOnly() {
		if (messageReceiver != null && mentionOnlyChatsListService != null) {
			String uniqueId = messageReceiver.getUniqueIdString();
			return !TestUtil.empty(uniqueId) && mentionOnlyChatsListService.has(uniqueId);
		}
		return false;
	}

	private boolean isSilent() {
		if (messageReceiver != null && ringtoneService != null) {
			String uniqueId = messageReceiver.getUniqueIdString();
			return !TestUtil.empty(uniqueId) && ringtoneService.hasCustomRingtone(uniqueId) && ringtoneService.isSilent(uniqueId, isGroupChat);
		}
		return false;
	}

	private void playInAppSound(final int resId, final boolean isVibrate) {
		if (this.isMuted() || this.isSilent()) {
			//do not play
			return;
		}

		RuntimeUtil.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				int ringerMode = audioManager.getRingerMode();
				boolean isSilent = (ringerMode == AudioManager.RINGER_MODE_SILENT
					|| ringerMode == AudioManager.RINGER_MODE_VIBRATE);

				if (preferenceService.isInAppSounds() && !isSilent) {
					MediaPlayerStateWrapper mediaPlayer = new MediaPlayerStateWrapper();
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
						mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
							.setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
							.setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
							.build());
						mediaPlayer.setVolume(0.3f, 0.3f);
					} else {
						mediaPlayer.setAudioStreamType(AudioManager.STREAM_NOTIFICATION);
					}
					mediaPlayer.setStateListener(new MediaPlayerStateWrapper.StateListener() {
						@Override
						public void onCompletion(MediaPlayer mp) {
							if (mp.isPlaying()) {
								mp.stop();
							}
							mp.reset();
							mp.release();
						}

						@Override
						public void onPrepared(MediaPlayer mp) {}
					});

					try (AssetFileDescriptor afd = ComposeMessageFragment.this.getResources().openRawResourceFd(resId)) {
						mediaPlayer.setDataSource(afd);
						mediaPlayer.prepare();
						mediaPlayer.start();
					} catch (Exception e) {
						logger.debug("could not play in-app sound.");
						mediaPlayer.release();
					}
				}

				if (preferenceService.isInAppVibrate() && isVibrate) {
					Vibrator vibrator = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
					if (vibrator != null) {
						switch (ringerMode) {
							case AudioManager.RINGER_MODE_VIBRATE:
							case AudioManager.RINGER_MODE_NORMAL:
								vibrator.vibrate(VIBRATION_MSEC);
								break;
							default:
								break;
						}
					}
				}
			}
		});
	}

	private void playSentSound() {
		playInAppSound(R.raw.sent_message, false);
	}

	private void playReceivedSound() {
		playInAppSound(R.raw.received_message, true);
	}

	private void sendTextMessage() {
		if (!this.validateSendingPermission()) {
			return;
		}

		if (!TestUtil.empty(this.messageText.getText())) {
			final CharSequence message;

			if (isQuotePanelShown()) {
				message = QuoteUtil.quote(
						this.messageText.getText().toString(),
						quoteInfo.quoteIdentity,
						quoteInfo.quoteText,
						quoteInfo.messageModel
						);
				closeQuoteMode();
			} else {
				message = this.messageText.getText();
			}

			if (!TestUtil.empty(message)) {
				// block send button to avoid double posting
				this.messageText.setText("");

				if (typingIndicatorTextWatcher != null) {
					messageText.removeTextChangedListener(typingIndicatorTextWatcher);
				}

				if (typingIndicatorTextWatcher != null) {
					messageText.addTextChangedListener(typingIndicatorTextWatcher);
				}

				//send stopped typing message
				if (typingIndicatorTextWatcher != null) {
					typingIndicatorTextWatcher.stopTyping();
				}

				new Thread(new Runnable() {
					@Override
					public void run() {
						TextMessageSendAction.getInstance()
								.sendTextMessage(new MessageReceiver[]{messageReceiver}, message.toString(), new SendAction.ActionHandler() {
									@Override
									public void onError(final String errorMessage) {
											RuntimeUtil.runOnUiThread(() -> {
												Toast.makeText(getActivity(), errorMessage, Toast.LENGTH_LONG).show();
												if (!TestUtil.empty(message)) {
													messageText.setText(message);
													messageText.setSelection(messageText.length());
												}
											});
									}

									@Override
									public void onWarning(String warning, boolean continueAction) {
									}

									@Override
									public void onProgress(final int progress, final int total) {
									}

									@Override
									public void onCompleted() {
										RuntimeUtil.runOnUiThread(new Runnable() {
											@Override
											public void run() {
												scrollList(Integer.MAX_VALUE);
												if (ConfigUtils.isTabletLayout()) {
													// remove draft right now to make sure conversations pane is updated
													ThreemaApplication.putMessageDraft(messageReceiver.getUniqueIdString(), "");
												}
											}
										});
									}
								});
					}
				}).start();
			}
		} else {
			if (ConfigUtils.requestAudioPermissions(getActivity(), this, PERMISSION_REQUEST_ATTACH_VOICE_MESSAGE)) {
				attachVoiceMessage();
			}
		}
	}

	private void attachVoiceMessage() {
		closeQuoteMode();

		// stop all message players
		if (this.messagePlayerService != null) {
			this.messagePlayerService.pauseAll(SOURCE_AUDIORECORDER);
		}

		Intent intent = new Intent(activity, VoiceRecorderActivity.class);
		IntentDataUtil.addMessageReceiverToIntent(intent, messageReceiver);
		activity.startActivityForResult(intent, ACTIVITY_ID_VOICE_RECORDER);
		activity.overridePendingTransition(R.anim.slide_in_left_short, 0);
	}

	private void copySelectedMessagesToClipboard() {
		AbstractMessageModel messageModel = selectedMessages.get(0);

		if (messageModel == null) {
			logger.error("no message model", activity);
			return;
		}

		String body = "";
		for (AbstractMessageModel message : selectedMessages) {
			if (body.length() > 0) {
				body += "\n";
			}

			body += message.getType() == MessageType.TEXT ?
				QuoteUtil.getMessageBody(message, false) :
				message.getCaption();
		}

		try {
			ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
			if (clipboard != null) {
				ClipData clipData = ClipData.newPlainText(null, body);
				if (clipData != null) {
					clipboard.setPrimaryClip(clipData);
					Snackbar.make(coordinatorLayout, R.string.message_copied, Snackbar.LENGTH_SHORT).show();
				}
			}
		} catch (Exception e) {
			// Some Android 4.3 devices raise an IllegalStateException when writing to the clipboard
			// while there is an active clipboard listener
			// see https://code.google.com/p/android/issues/detail?id=58043
			logger.error("Exception", e);
		}
	}

	@SuppressLint("StaticFieldLeak")
	private void shareMessages() {
		if (selectedMessages.size() > 1) {

			new AsyncTask<Void, Void, Void>() {
				@Override
				protected void onPreExecute() {
					GenericProgressDialog.newInstance(R.string.decoding_message, R.string.please_wait).show(getFragmentManager(), DIALOG_TAG_DECRYPTING_MESSAGES);
				}

				@Override
				protected Void doInBackground(Void... voids) {
					fileService.loadDecryptedMessageFiles(selectedMessages, new FileService.OnDecryptedFilesComplete() {
						@Override
						public void complete(ArrayList<Uri> uris) {
							messageService.shareMediaMessages(activity,
								new ArrayList<>(selectedMessages),
								new ArrayList<>(uris));
						}

						@Override
						public void error(String message) {
							RuntimeUtil.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_LONG).show());
						}
					});
					return null;
				}

				@Override
				protected void onPostExecute(Void aVoid) {
					DialogUtil.dismissDialog(getFragmentManager(), DIALOG_TAG_DECRYPTING_MESSAGES, true);
				}
			}.execute();
		} else {
			final AbstractMessageModel messageModel = selectedMessages.get(0);

			if (messageModel != null) {
				fileService.loadDecryptedMessageFile(messageModel, new FileService.OnDecryptedFileComplete() {
					@Override
					public void complete(File decryptedFile) {
						if (decryptedFile != null) {
							String filename = null;
							if (messageModel.getType() == MessageType.FILE) {
								filename = messageModel.getFileData().getFileName();
							}
							messageService.shareMediaMessages(activity,
									new ArrayList<>(Collections.singletonList(messageModel)),
									new ArrayList<>(Collections.singletonList(fileService.getShareFileUri(decryptedFile, filename))));
						} else {
							messageService.shareTextMessage(activity, messageModel);
						}
					}

					@Override
					public void error(final String message) {
						RuntimeUtil.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_LONG).show());
					}
				});
			}
		}
	}

	@Override
	public void onContactSelected(String identity, int length, int insertPosition) {
		Editable editable = this.messageText.getText();

		if (insertPosition >= 0 && insertPosition <= editable.length()) {
			editable.delete(insertPosition, insertPosition + length);
			this.messageText.addMention(identity);
		}
	}

	private void startQuoteMode(AbstractMessageModel messageModel, Runnable onFinishRunnable) {
		if (messageModel == null) {
			messageModel = selectedMessages.get(0);
		}

		String body = QuoteUtil.getMessageBody(messageModel, true);

		if (!TestUtil.empty(body) || ConfigUtils.canCreateV2Quotes()) {
			sendButton.setEnabled(messageText != null && !TestUtil.empty(messageText.getText()));

			quoteInfo.quoteIdentity = messageModel.isOutbox() ? userService.getIdentity() : messageModel.getIdentity();
			quoteInfo.quoteIdentityView.setText(
				NameUtil.getQuoteName(quoteInfo.quoteIdentity, this.contactService, this.userService)
			);

			int color = ConfigUtils.getAccentColor(activity);
			if (!messageModel.isOutbox()) {
				if (isGroupChat) {
					if (identityColors != null && identityColors.containsKey(quoteInfo.quoteIdentity)) {
						color = identityColors.get(quoteInfo.quoteIdentity);
					}
				} else {
					if (contactModel != null) {
						color = contactModel.getColor();
					}
				}
			}
			quoteInfo.quoteBar.setBackgroundColor(color);

			quoteInfo.quoteTextView.setText(emojiMarkupUtil.addTextSpans(activity, body, quoteInfo.quoteTextView, false, false));
			quoteInfo.quoteText = body;
			quoteInfo.messageModel = messageModel;
			quoteInfo.quoteThumbnail.setVisibility(View.GONE);
			quoteInfo.quoteTypeImage.setVisibility(View.GONE);
			if (ConfigUtils.canCreateV2Quotes()) {
				try {
					Bitmap thumbnail = fileService.getMessageThumbnailBitmap(messageModel, thumbnailCache);
					if (thumbnail != null) {
						quoteInfo.quoteThumbnail.setImageBitmap(thumbnail);
						quoteInfo.quoteThumbnail.setVisibility(View.VISIBLE);
					}
				} catch (Exception ignore) {
				}

				MessageUtil.MessageViewElement messageViewElement = MessageUtil.getViewElement(getContext(), messageModel);
				if (messageViewElement.icon != null) {
					quoteInfo.quoteTypeImage.setImageResource(messageViewElement.icon);
					quoteInfo.quoteTypeImage.setVisibility(View.VISIBLE);
				}
			}

			AnimationUtil.expand(quoteInfo.quotePanel, onFinishRunnable);
		}
	}

	private void closeQuoteMode() {
		quoteInfo.quoteIdentityView.setText("");
		quoteInfo.quoteTextView.setText("");
		if (isQuotePanelShown()) {
			AnimationUtil.collapse(quoteInfo.quotePanel, () -> updateSendButton(messageText.getText()));
		}
	}

	private boolean isQuotePanelShown() {
		return quoteInfo.quotePanel != null && quoteInfo.quotePanel.getVisibility() == View.VISIBLE;
	}

	private void startForwardMessage() {
		if (selectedMessages.size() > 0) {
			if (selectedMessages.size() == 1) {
				final AbstractMessageModel messageModel = selectedMessages.get(0);

				if (messageModel.getType() == MessageType.TEXT) {
					// allow editing before sending if it's a single text message
					String body = QuoteUtil.getMessageBody(messageModel, false);
					Intent intent = new Intent(activity, RecipientListBaseActivity.class);
					intent.setType("text/plain");
					intent.setAction(Intent.ACTION_SEND);
					intent.putExtra(Intent.EXTRA_TEXT, body);
					intent.putExtra(ThreemaApplication.INTENT_DATA_IS_FORWARD, true);
					activity.startActivity(intent);
					return;
				}
			}
			FileUtil.forwardMessages(activity, RecipientListBaseActivity.class, selectedMessages);
		}
	}

	private void showMessageLog() {
		AbstractMessageModel messageModel = selectedMessages.get(0);

		if (messageModel == null) {
			return;
		}

		MessageDetailDialog.newInstance(R.string.message_log_title, messageModel.getId(), messageModel.getClass().toString()).
			show(getFragmentManager(), "messageLog");
	}

	private void updateToolbarTitle() {
		if (!TestUtil.required(
				this.actionBar,
				this.actionBarSubtitleImageView,
				this.actionBarSubtitleTextView,
				this.actionBarTitleTextView,
				this.emojiMarkupUtil,
				this.messageReceiver) || !requiredInstances()) {
			return;
		}

		this.actionBarSubtitleTextView.setVisibility(View.GONE);
		this.actionBarSubtitleImageView.setVisibility(View.GONE);

		this.actionBarTitleTextView.setText(this.messageReceiver.getDisplayName());
		this.actionBarTitleTextView.setPaintFlags(this.actionBarTitleTextView.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);

		if (this.isGroupChat) {
			if (!groupService.isGroupMember(this.groupModel)) {
				this.actionBarTitleTextView.setPaintFlags(this.actionBarTitleTextView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
			}
			actionBarSubtitleTextView.setText(groupService.getMembersString(groupModel));
			actionBarSubtitleTextView.setVisibility(View.VISIBLE);
			actionBarAvatarView.setImageBitmap(groupService.getAvatar(groupModel, false));
			actionBarAvatarView.setBadgeVisible(false);
		} else if (this.isDistributionListChat) {
			actionBarSubtitleTextView.setText(this.distributionListService.getMembersString(this.distributionListModel));
			actionBarSubtitleTextView.setVisibility(View.VISIBLE);
			actionBarAvatarView.setImageBitmap(distributionListService.getAvatar(distributionListModel, false));
			actionBarAvatarView.setBadgeVisible(false);
		} else {
			if (contactModel != null) {
				this.actionBarSubtitleImageView.setContactModel(contactModel);
				this.actionBarSubtitleImageView.setVisibility(View.VISIBLE);
				this.actionBarAvatarView.setImageBitmap(contactService.getAvatar(contactModel, false, true));
				this.actionBarAvatarView.setBadgeVisible(contactService.showBadge(contactModel));
			}
		}
		this.actionBarTitleTextView.invalidate();
		this.actionBarSubtitleTextView.invalidate();
		this.actionBarSubtitleImageView.invalidate();
	}

	@Override
	public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
		inflater.inflate(R.menu.fragment_compose_message, menu);
		this.setupToolbar();

		super.onCreateOptionsMenu(menu, inflater);

		ConfigUtils.addIconsToOverflowMenu(getContext(), menu);
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		this.callItem = menu.findItem(R.id.menu_threema_call);
		this.deleteDistributionListItem = menu.findItem(R.id.menu_delete_distribution_list);
		this.shortCutItem = menu.findItem(R.id.menu_shortcut);
		this.mutedMenuItem = menu.findItem(R.id.menu_muted);
		this.blockMenuItem = menu.findItem(R.id.menu_block_contact);
		this.showOpenBallotWindowMenuItem = menu.findItem(R.id.menu_ballot_window_show);
		this.showBallotsMenuItem = menu.findItem(R.id.menu_ballot_show_all);

		// initialize menus
		updateMenus();

		// initialize various toolbar items
		this.updateToolbarTitle();
	}

	@SuppressLint("StaticFieldLeak")
	private void updateMenus() {
		logger.debug("updateMenus");

		if (!TestUtil.required(
				this.callItem,
				this.deleteDistributionListItem,
				this.shortCutItem,
				this.mutedMenuItem,
				this.blockMenuItem,
				this.showOpenBallotWindowMenuItem,
				isAdded()
		)) {
			return;
		}

		this.deleteDistributionListItem.setVisible(this.isDistributionListChat);
		this.shortCutItem.setVisible(ShortcutManagerCompat.isRequestPinShortcutSupported(getAppContext()));
		this.mutedMenuItem.setVisible(!this.isDistributionListChat);
		updateMuteMenu();

		if (contactModel != null) {
			this.blockMenuItem.setVisible(true);
			updateBlockMenu();

			contactTypingStateChanged(contactService.isTyping(contactModel.getIdentity()));
		} else {
			this.blockMenuItem.setVisible(false);
		}

		new AsyncTask<Void, Void, Long>() {
			@Override
			protected Long doInBackground(Void... voids) {
				return ballotService.countBallots(new BallotService.BallotFilter() {
					@Override
					public MessageReceiver getReceiver() { return messageReceiver; }

					@Override
					public BallotModel.State[] getStates() { return new BallotModel.State[]{BallotModel.State.OPEN}; }

					@Override
					public String createdOrNotVotedByIdentity() {
						return userService.getIdentity();
					}

					@Override
					public boolean filter(BallotModel ballotModel) { return true; }
				});
			}

			@Override
			protected void onPostExecute(Long openBallots) {
				showOpenBallotWindowMenuItem.setVisible(openBallots > 0L);

				if (preferenceService.getBallotOverviewHidden()) {
					showOpenBallotWindowMenuItem.setIcon(R.drawable.ic_outline_visibility);
					showOpenBallotWindowMenuItem.setTitle(R.string.ballot_window_show);
				} else {
					showOpenBallotWindowMenuItem.setIcon(R.drawable.ic_outline_visibility_off);
					showOpenBallotWindowMenuItem.setTitle(R.string.ballot_window_hide);
				}
				Context context = getContext();
				if (context != null) {
					ConfigUtils.themeMenuItem(showOpenBallotWindowMenuItem, ConfigUtils.getColorFromAttribute(context, R.attr.textColorSecondary));
				}
			}
		}.execute();

		new AsyncTask<Void, Void, Long>() {
			@Override
			protected Long doInBackground(Void... voids) {
				return ballotService.countBallots(new BallotService.BallotFilter() {
					@Override
					public MessageReceiver getReceiver() { return messageReceiver; }

					@Override
					public BallotModel.State[] getStates() { return new BallotModel.State[]{BallotModel.State.OPEN, BallotModel.State.CLOSED}; }

					@Override
					public boolean filter(BallotModel ballotModel) { return true; }
				});
			}

			@Override
			protected void onPostExecute(Long hasBallots) {
				showBallotsMenuItem.setVisible(hasBallots > 0L);
			}
		}.execute();

		updateVoipCallMenuItem(null);
	}

	private void updateMuteMenu() {
		if (!isAdded() || this.mutedMenuItem == null) {
			// do not update if no longer attached to activity
			return;
		}

		if (isMuted()) {
			this.mutedMenuItem.setIcon(R.drawable.ic_dnd_total_silence_grey600_24dp);
			this.mutedMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		} else if (isMentionsOnly()) {
			this.mutedMenuItem.setIcon(R.drawable.ic_dnd_mention_grey600_24dp);
			this.mutedMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		} else if (isSilent()) {
			this.mutedMenuItem.setIcon(R.drawable.ic_notifications_off_outline);
			this.mutedMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		} else {
			this.mutedMenuItem.setIcon(R.drawable.ic_notifications_active_outline);
			this.mutedMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		}
	}

	private void updateBlockMenu() {
		if (!isAdded()) {
			// do not update if no longer attached to activity
			return;
		}
		if (TestUtil.required(this.blockMenuItem, this.blackListIdentityService, this.contactModel)) {
			boolean state = this.blackListIdentityService.has(this.contactModel.getIdentity());
			this.blockMenuItem.setTitle(state ? getString(R.string.unblock_contact) : getString(R.string.block_contact));
			this.blockMenuItem.setShowAsAction(state ? MenuItem.SHOW_AS_ACTION_ALWAYS : MenuItem.SHOW_AS_ACTION_NEVER);
			this.mutedMenuItem.setShowAsAction(state ? MenuItem.SHOW_AS_ACTION_NEVER : MenuItem.SHOW_AS_ACTION_IF_ROOM);
			this.mutedMenuItem.setVisible(!state);

			this.callItem.setShowAsAction(state ? MenuItem.SHOW_AS_ACTION_NEVER : MenuItem.SHOW_AS_ACTION_ALWAYS);

			updateVoipCallMenuItem(!state);
		}
	}

	@AnyThread
	private void updateVoipCallMenuItem(final Boolean newState) {
		RuntimeUtil.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (callItem != null) {
					if (ContactUtil.canReceiveVoipMessages(contactModel, blackListIdentityService)
							&& ConfigUtils.isCallsEnabled(getContext(), preferenceService, licenseService)) {
						logger.debug("updateVoipMenu newState " + newState);

						callItem.setVisible(newState != null ? newState : voipStateService.getCallState().isIdle());
					} else {
						callItem.setVisible(false);
					}
				}
			}
		});
	}

	private Intent addExtrasToIntent(Intent intent, MessageReceiver receiver) {
		switch (receiver.getType()) {
			case MessageReceiver.Type_GROUP:
				intent.putExtra(ThreemaApplication.INTENT_DATA_GROUP, groupId);
				break;
			case MessageReceiver.Type_DISTRIBUTION_LIST:
				intent.putExtra(ThreemaApplication.INTENT_DATA_DISTRIBUTION_LIST, distributionListModel.getId());
				break;
			case MessageReceiver.Type_CONTACT:
			default:
				intent.putExtra(ThreemaApplication.INTENT_DATA_CONTACT, identity);
		}
		return intent;
	}

	private void attachCamera() {
		Intent previewIntent = IntentDataUtil.addMessageReceiversToIntent(new Intent(activity, SendMediaActivity.class), new MessageReceiver[]{this.messageReceiver});
		if (this.actionBarTitleTextView != null && this.actionBarTitleTextView.getText() != null) {
			previewIntent.putExtra(ThreemaApplication.INTENT_DATA_TEXT, this.actionBarTitleTextView.getText().toString());
		}
		previewIntent.putExtra(ThreemaApplication.INTENT_DATA_PICK_FROM_CAMERA, true);
		AnimationUtil.startActivityForResult(activity, null, previewIntent, ThreemaActivity.ACTIVITY_ID_SEND_MEDIA);
	}

	private void showPermissionRationale(int stringResource) {
		ConfigUtils.showPermissionRationale(getContext(), coordinatorLayout, stringResource);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				NavigationUtil.navigateUpToHome(activity);
				break;
			case R.id.menu_search_messages:
				searchActionMode = activity.startSupportActionMode(new SearchActionMode());
				break;
			case R.id.menu_gallery:
				Intent mediaGalleryIntent = new Intent(activity, MediaGalleryActivity.class);
				activity.startActivity(addExtrasToIntent(mediaGalleryIntent, this.messageReceiver));
				break;
			case R.id.menu_threema_call:
				VoipUtil.initiateCall(activity, contactModel, false, null);
				break;
			case R.id.menu_wallpaper:
				wallpaperService.selectWallpaper(this, this.messageReceiver, new Runnable() {
					@Override
					public void run() {
						RuntimeUtil.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								setBackgroundWallpaper();
							}
						});
					}
				});
				activity.overridePendingTransition(R.anim.fast_fade_in, R.anim.fast_fade_out);
				break;
			case R.id.menu_muted:
				if (!isDistributionListChat) {
					Intent intent;
					int[] location = new int[2];

					if (isGroupChat) {
						intent = new Intent(activity, GroupNotificationsActivity.class);
						intent.putExtra(ThreemaApplication.INTENT_DATA_GROUP, this.groupId);
					} else {
						intent = new Intent(activity, ContactNotificationsActivity.class);
						intent.putExtra(ThreemaApplication.INTENT_DATA_CONTACT, this.identity);
					}
					if (ToolbarUtil.getMenuItemCenterPosition(((ThreemaToolbarActivity)activity).getToolbar(), R.id.menu_muted, location)) {
						intent.putExtra((ThreemaApplication.INTENT_DATA_ANIM_CENTER), location);
					}
					activity.startActivity(intent);
				}
				break;
			case R.id.menu_block_contact:
				if (this.blackListIdentityService.has(contactModel.getIdentity())) {
					this.blackListIdentityService.toggle(activity, contactModel);
					updateBlockMenu();
				} else {
					GenericAlertDialog.newInstance(R.string.block_contact, R.string.really_block_contact, R.string.yes, R.string.no).setTargetFragment(this).show(getFragmentManager(), DIALOG_TAG_CONFIRM_BLOCK);
				}
				break;
			case R.id.menu_delete_distribution_list:
				GenericAlertDialog.newInstance(R.string.really_delete_distribution_list,
						R.string.really_delete_distribution_list_message,
						R.string.ok,
						R.string.cancel)
						.setTargetFragment(this)
						.setData(distributionListModel)
						.show(getFragmentManager(), CONFIRM_TAG_DELETE_DISTRIBUTION_LIST);
				break;
			case R.id.menu_shortcut:
				createShortcut();
				break;
			case R.id.menu_empty_chat:
				GenericAlertDialog.newInstance(R.string.empty_chat_title,
					R.string.empty_chat_confirm,
					R.string.ok,
					R.string.cancel)
					.setTargetFragment(this)
					.show(getFragmentManager(), DIALOG_TAG_EMPTY_CHAT);
				break;
			case R.id.menu_ballot_window_show:
				if (openBallotNoticeView.isShown()) {
					preferenceService.setBallotOverviewHidden(true);
					openBallotNoticeView.hide(true);
				} else {
					preferenceService.setBallotOverviewHidden(false);
					openBallotNoticeView.show(true);
				}
				break;
			case R.id.menu_ballot_show_all:
				Intent intent = new Intent(getContext(), BallotOverviewActivity.class);
				IntentDataUtil.addMessageReceiverToIntent(intent, messageReceiver);
				startActivity(intent);
				break;
		}
		return false;
	}

	private void emptyChat() {
		new EmptyChatAsyncTask(new MessageReceiver[]{messageReceiver}, messageService, getFragmentManager(), false, new Runnable() {
			@Override
			public void run() {
				if (isAdded()) {
						synchronized (messageValues) {
							messageValues.clear();
							composeMessageAdapter.notifyDataSetChanged();
						}

						// empty draft
						ThreemaApplication.putMessageDraft(messageReceiver.getUniqueIdString(), "");
						messageText.setText(null);

						// clear conversations cache
						conversationService.reset();

						ListenerManager.conversationListeners.handle(new ListenerManager.HandleListener<ConversationListener>() {
							@Override
							public void handle(ConversationListener listener) {
								listener.onModifiedAll();
							}
						});
					}
			}
		}).execute();
	}

	private void createShortcut() {
		if (this.isGroupChat) {
			this.shortcutService.createShortcut(groupModel);
		} else if (this.isDistributionListChat) {
			this.shortcutService.createShortcut(distributionListModel);
		} else {
			if (ContactUtil.canReceiveVoipMessages(contactModel, blackListIdentityService)
					&& ConfigUtils.isCallsEnabled(getContext(), preferenceService, licenseService)) {
				ArrayList<String> items = new ArrayList<String>();
				items.add(getString(R.string.prefs_header_chat));
				items.add(getString(R.string.threema_call));
				SelectorDialog selectorDialog = SelectorDialog.newInstance(getString(R.string.shortcut_choice_title), items, getString(R.string.cancel));
				selectorDialog.setTargetFragment(this, 0);
				selectorDialog.show(getFragmentManager(), DIALOG_TAG_CHOOSE_SHORTCUT_TYPE);
			} else {
				this.shortcutService.createShortcut(contactModel, ShortcutService.TYPE_CHAT);
			}
		}
	}

	private void sendMessage() {
		if (typingIndicatorTextWatcher != null) {
			typingIndicatorTextWatcher.killEvents();
		}

		this.sendTextMessage();
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode,
									final Intent intent) {

		if (wallpaperService != null && wallpaperService.handleActivityResult(this, requestCode, resultCode, intent, this.messageReceiver)) {
			setBackgroundWallpaper();
			return;
		}

		if (requestCode == ACTIVITY_ID_VOICE_RECORDER) {
			if (this.messagePlayerService != null) {
				this.messagePlayerService.resumeAll(getActivity(), messageReceiver, SOURCE_AUDIORECORDER);
			}
		}
	}

	private final SearchView.OnQueryTextListener queryTextListener = new SearchView.OnQueryTextListener() {
		// listener for search bar on top
		@Override
		public boolean onQueryTextChange(String newText) {
			composeMessageAdapter.getFilter().filter(newText);
			return true;
		}

		@Override
		public boolean onQueryTextSubmit(String query) {
			composeMessageAdapter.nextMatchPosition();
			return true;
		}
	};

	@Override
	public void onClick(String tag, int which, Object data) {
		if (DIALOG_TAG_CHOOSE_SHORTCUT_TYPE.equals(tag)) {
			this.shortcutService.createShortcut(contactModel, which + 1);
		}
	}

	@Override
	public void onCancel(String tag) {}

	@Override
	public void onNo(String tag) {}

	public class ComposeMessageAction implements ActionMode.Callback {
		private final int position;
		private MenuItem ackItem, decItem, quoteItem, logItem, discardItem, forwardItem, saveItem, copyItem, qrItem, shareItem, showText;

		ComposeMessageAction(int position) {
			this.position = position;
			longClickItem = position;
		}

		private void updateActionMenu() {
			// workaround for support library bug, see https://code.google.com/p/android/issues/detail?id=81192
			MenuItemCompat.setShowAsAction(ackItem, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
			MenuItemCompat.setShowAsAction(decItem, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
			MenuItemCompat.setShowAsAction(quoteItem, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
			MenuItemCompat.setShowAsAction(logItem, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);
			MenuItemCompat.setShowAsAction(discardItem, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);
			MenuItemCompat.setShowAsAction(saveItem, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);
			MenuItemCompat.setShowAsAction(copyItem, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
			MenuItemCompat.setShowAsAction(forwardItem, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
			MenuItemCompat.setShowAsAction(qrItem, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);
			MenuItemCompat.setShowAsAction(shareItem, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);

			decItem.setVisible(false);
			ackItem.setVisible(false);
			quoteItem.setVisible(false);
			qrItem.setVisible(false);
			copyItem.setVisible(false);
			logItem.setVisible(false);
			saveItem.setVisible(false);
			shareItem.setVisible(false);
			showText.setVisible(false);

			if (selectedMessages.size() > 1) {
				boolean isForwardable = selectedMessages.size() <= MAX_FORWARDABLE_ITEMS;
				boolean isMedia = true;
				boolean isTextOnly = true;
				boolean isShareable = true;

				for (AbstractMessageModel message: selectedMessages) {
					if (isForwardable
							&& (
									// if the media is not downloaded
									!message.isAvailable()
									// or the message is status message (unread or status)
									|| message.isStatusMessage()
									// or a ballot
									|| message.getType() == MessageType.BALLOT
									// or a voip status
									|| message.getType() == MessageType.VOIP_STATUS)) {
						isForwardable = false;
					}
					if (isMedia && !message.isAvailable() ||
									(message.getType() != MessageType.IMAGE &&
									message.getType() != MessageType.VOICEMESSAGE &&
									message.getType() != MessageType.VIDEO &&
									message.getType() != MessageType.FILE)) {
						isMedia = false;
					}
					if (isTextOnly && message.getType() != MessageType.TEXT) {
						isTextOnly = false;
					}
					if (isShareable) {
						if (message.getType() != MessageType.IMAGE && message.getType() != MessageType.VIDEO && message.getType() != MessageType.FILE) {
							isShareable = false;
						}
					}
				}
				forwardItem.setVisible(isForwardable);
				saveItem.setVisible(isMedia);
				copyItem.setVisible(isTextOnly);
				shareItem.setVisible(isShareable);
			} else if (selectedMessages.size() == 1) {
				AbstractMessageModel selectedMessage = selectedMessages.get(0);

				if (selectedMessage.isStatusMessage()) {
					forwardItem.setVisible(false);
					copyItem.setVisible(true);
					logItem.setVisible(true);
				} else {
					boolean isValidReceiver = messageReceiver.validateSendingPermission(null);

					quoteItem.setVisible(isValidReceiver && QuoteUtil.isQuoteable(selectedMessage));

					decItem.setVisible(MessageUtil.canSendUserDecline(selectedMessage) && isValidReceiver);
					ackItem.setVisible(MessageUtil.canSendUserAcknowledge(selectedMessage) && isValidReceiver);

					logItem.setVisible(true);

					switch (selectedMessage.getType()) {
						case IMAGE:
							saveItem.setVisible(true);
							forwardItem.setVisible(true);
							shareItem.setVisible(true);
							if (!TestUtil.empty(selectedMessage.getCaption())) {
								copyItem.setVisible(true);
							}
							break;
						case VIDEO:
							saveItem.setVisible(selectedMessage.isAvailable());
							forwardItem.setVisible(selectedMessage.isAvailable());
							shareItem.setVisible(selectedMessage.isAvailable());
							break;
						case VOICEMESSAGE:
							saveItem.setVisible(selectedMessage.isAvailable());
							forwardItem.setVisible(selectedMessage.isAvailable());
							break;
						case FILE:
							if (selectedMessage.getFileData().getRenderingType() == FileData.RENDERING_DEFAULT) {
								MenuItemCompat.setShowAsAction(saveItem, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
								MenuItemCompat.setShowAsAction(forwardItem, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);
							}
							saveItem.setVisible(selectedMessage.isAvailable());
							shareItem.setVisible(selectedMessage.isAvailable());
							forwardItem.setVisible(selectedMessage.isAvailable());
							if (!TestUtil.empty(selectedMessage.getCaption())) {
								copyItem.setVisible(true);
							}
							break;
						case BALLOT:
							saveItem.setVisible(false);
							forwardItem.setVisible(false);
							break;
						case TEXT:
							saveItem.setVisible(false);
							forwardItem.setVisible(true);
							copyItem.setVisible(true);
							qrItem.setVisible(true);
							shareItem.setVisible(true);
							showText.setVisible(true);
							break;
						case VOIP_STATUS:
							saveItem.setVisible(false);
							forwardItem.setVisible(false);
							copyItem.setVisible(false);
							qrItem.setVisible(false);
							shareItem.setVisible(false);
							logItem.setVisible(false);
							break;
						case LOCATION:
							shareItem.setVisible(true);
							break;
						default:
							break;
					}
				}
			}

			if (AppRestrictionUtil.isShareMediaDisabled(getContext())) {
				shareItem.setVisible(false);
				saveItem.setVisible(false);
			}
		}

		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			if (this.position == AbsListView.INVALID_POSITION) {
				return false;
			}

			if (convListView.getCheckedItemCount() < 1) {
				return false;
			}

			MenuInflater inflater = mode.getMenuInflater();
			if (inflater != null) {
				inflater.inflate(R.menu.action_compose_message, menu);
			}

			ConfigUtils.addIconsToOverflowMenu(null, menu);

			decItem = menu.findItem(R.id.menu_message_dec);
			ackItem = menu.findItem(R.id.menu_message_ack);
			logItem = menu.findItem(R.id.menu_message_log);
			discardItem = menu.findItem(R.id.menu_message_discard);
			forwardItem = menu.findItem(R.id.menu_message_forward);
			saveItem = menu.findItem(R.id.menu_message_save);
			copyItem = menu.findItem(R.id.menu_message_copy);
			qrItem = menu.findItem(R.id.menu_message_qrcode);
			shareItem = menu.findItem(R.id.menu_share);
			quoteItem = menu.findItem(R.id.menu_message_quote);
			showText = menu.findItem(R.id.menu_show_text);

			updateActionMenu();

			return true;
		}

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			final int checked = convListView.getCheckedItemCount();

			mode.setTitle(Integer.toString(checked));
			updateActionMenu();

			return true;
		}

		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			if (selectedMessages == null || selectedMessages.size() < 1) {
				mode.finish();
				return true;
			}

			switch (item.getItemId()) {
				case R.id.menu_message_copy:
					copySelectedMessagesToClipboard();
					mode.finish();
					break;
				case R.id.menu_message_discard:
					deleteSelectedMessages();
					break;
				case R.id.menu_message_forward:
					startForwardMessage();
					mode.finish();
					break;
				case R.id.menu_message_ack:
					sendUserAck();
					mode.finish();
					break;
				case R.id.menu_message_dec:
					sendUserDec();
					mode.finish();
					break;
				case R.id.menu_message_save:
					if (ConfigUtils.requestStoragePermissions(activity, ComposeMessageFragment.this, PERMISSION_REQUEST_SAVE_MESSAGE)) {
						fileService.saveMedia(activity, coordinatorLayout, new CopyOnWriteArrayList<>(selectedMessages), false);
					}
					mode.finish();
					break;
				case R.id.menu_message_log:
					showMessageLog();
					mode.finish();
					break;
				case R.id.menu_message_qrcode:
					showAsQrCode(((ThreemaToolbarActivity) activity).getToolbar());
					mode.finish();
					break;
				case R.id.menu_share:
					shareMessages();
					mode.finish();
					break;
				case R.id.menu_message_quote:
					startQuoteMode(null, null);
					mode.finish();
					break;
				case R.id.menu_show_text:
					showTextChatBubble();
					mode.finish();
					break;
				default:
					return false;
			}
			return true;
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {
			actionMode = null;
			longClickItem = AbsListView.INVALID_POSITION;

			// handle done button
			convListView.clearChoices();
			convListView.requestLayout();
			convListView.post(new Runnable() {
				@Override
				public void run() {
					convListView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
				}
			});
		}
	}

	private void showTextChatBubble() {
		AbstractMessageModel messageModel = selectedMessages.get(0);

		Intent intent = new Intent(getContext(), TextChatBubbleActivity.class);
		IntentDataUtil.append(messageModel, intent);
		activity.startActivity(intent);
		activity.overridePendingTransition(R.anim.fast_fade_in, R.anim.fast_fade_out);
	}

	private void showAsQrCode(View v) {
		AbstractMessageModel messageModel = selectedMessages.get(0);

		if (messageModel != null && messageModel.getType() == MessageType.TEXT) {
			new QRCodePopup(getContext(), getActivity().getWindow().getDecorView(), getActivity()).show(v, messageModel.getBody());
		}
	}

	private void sendUserAck() {
		messageService.sendUserAcknowledgement(selectedMessages.get(0));
		Toast.makeText(getActivity(), R.string.message_acknowledged, Toast.LENGTH_SHORT).show();
	}

	/**
	 * Send a Decline Message
	 */
	private void sendUserDec() {
		messageService.sendUserDecline(selectedMessages.get(0));
		Toast.makeText(getActivity(), R.string.message_declined, Toast.LENGTH_SHORT).show();
	}

	public boolean onBackPressed() {
		logger.debug("onBackPressed");
		// dismiss emoji keyboard if it's showing instead of leaving activity
		if (emojiPicker != null && emojiPicker.isShown()) {
			emojiPicker.hide();
			return true;
		} else {
			if (mentionPopup != null && mentionPopup.isShowing()) {
				dismissMentionPopup();
				return true;
			}
			if (searchActionMode != null) {
				searchActionMode.finish();
				return true;
			}
			if (actionMode != null) {
				actionMode.finish();
				return true;
			}
			else if (ConfigUtils.isTabletLayout()) {
				if (actionBar != null) {
					actionBar.setDisplayUseLogoEnabled(true);
					actionBar.setDisplayShowCustomEnabled(false);
				}
			}
			return false;
		}
	}

	private void preserveListInstanceValues() {
		// this instance variable will probably survive an orientation change
		// since setRetainInstance() is set in onCreate()
		// so we don't put it into a bundle in onSaveInstanceState
		listInstancePosition = AbsListView.INVALID_POSITION;

		if (convListView != null && composeMessageAdapter != null) {
			if (convListView.getLastVisiblePosition() != composeMessageAdapter.getCount() - 1) {
				listInstancePosition = convListView.getFirstVisiblePosition();
				View v = convListView.getChildAt(0);
				listInstanceTop = (v == null) ? 0 : (v.getTop() - convListView.getPaddingTop());
				if (messageReceiver != null) {
					listInstanceReceiverId = messageReceiver.getUniqueIdString();
				}
			}
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		logger.debug("onSaveInstanceState");

		// some phones destroy the retained fragment upon going in background so we have to persist some data
		outState.putParcelable(CAMERA_URI, cameraUri);
		outState.putInt(ThreemaApplication.INTENT_DATA_GROUP, this.groupId);
		outState.putInt(ThreemaApplication.INTENT_DATA_DISTRIBUTION_LIST, this.distributionListId);
		outState.putString(ThreemaApplication.INTENT_DATA_CONTACT, this.identity);

		super.onSaveInstanceState(outState);
	}


	private Integer getCurrentPageReferenceId() {
		return this.currentPageReferenceId;
	}

	private void configureSearchWidget(final MenuItem menuItem) {
		// Associate searchable configuration with the SearchView
		SearchManager searchManager =
				(SearchManager) activity.getSystemService(Context.SEARCH_SERVICE);
		SearchView searchView = (SearchView) menuItem.getActionView();
		SearchableInfo mSearchableInfo = searchManager.getSearchableInfo(activity.getComponentName());
		if (searchView != null) {
			searchView.setSearchableInfo(mSearchableInfo);
			searchView.setOnQueryTextListener(queryTextListener);
			searchView.setQueryHint(getString(R.string.hint_search_keyword));
			searchView.setIconified(false);
			searchView.setOnCloseListener(new SearchView.OnCloseListener() {
				@Override
				public boolean onClose() {
					if (searchActionMode != null) {
						searchActionMode.finish();
					}
					return false;
				}
			});

			LinearLayout linearLayoutOfSearchView = (LinearLayout) searchView.getChildAt(0);
			if (linearLayoutOfSearchView != null) {
				linearLayoutOfSearchView.setGravity(Gravity.CENTER_VERTICAL);
				linearLayoutOfSearchView.setPadding(0, 0, 0, 0);

				searchCounter = (TextView) layoutInflater.inflate(R.layout.textview_search_action, null);
				linearLayoutOfSearchView.addView(searchCounter);

				FrameLayout searchPreviousLayout = (FrameLayout) layoutInflater.inflate(R.layout.button_search_action, null);
				searchPreviousButton = searchPreviousLayout.findViewById(R.id.search_button);
				searchPreviousButton.setImageDrawable(ConfigUtils.getThemedDrawable(activity, R.drawable.ic_keyboard_arrow_down_outline));
				searchPreviousButton.setScaleY(-1);
				searchPreviousButton.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						composeMessageAdapter.previousMatchPosition();
					}
				});
				linearLayoutOfSearchView.addView(searchPreviousLayout);

				FrameLayout searchNextLayout = (FrameLayout) layoutInflater.inflate(R.layout.button_search_action, null);
				searchNextButton = searchNextLayout.findViewById(R.id.search_button);
				searchProgress = searchNextLayout.findViewById(R.id.next_progress);
				searchNextButton.setImageDrawable(ConfigUtils.getThemedDrawable(activity, R.drawable.ic_keyboard_arrow_down_outline));
				searchNextButton.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						composeMessageAdapter.nextMatchPosition();
					}
				});
				linearLayoutOfSearchView.addView(searchNextLayout);
			}
		}
	}

	private class SearchActionMode implements ActionMode.Callback {

		@SuppressLint("StaticFieldLeak")
		@Override
		public boolean onCreateActionMode(ActionMode mode, final Menu menu) {
			composeMessageAdapter.clearFilter();

			activity.getMenuInflater().inflate(R.menu.action_compose_message_search, menu);

			final MenuItem item = menu.findItem(R.id.menu_action_search);
			final View actionView = item.getActionView();

			item.setActionView(R.layout.item_progress);
			item.expandActionView();

			if (bottomPanel != null) {
				bottomPanel.setVisibility(View.GONE);
			}

			if (emojiPicker != null && emojiPicker.isShown()) {
				emojiPicker.hide();
			}

			dismissMentionPopup();

			// load all records
			new AsyncTask<Void, Void, Void>() {
				List<AbstractMessageModel> messageModels;

				@Override
				protected Void doInBackground(Void... params) {
					messageModels = getAllRecords();

					return null;
				}

				@Override
				protected void onPostExecute(Void result) {
					if (messageModels != null && isAdded()) {
						hastNextRecords = false;

						item.collapseActionView();
						item.setActionView(actionView);
						configureSearchWidget(menu.findItem(R.id.menu_action_search));

						insertToList(messageModels, true, true);
						convListView.setSelection(Integer.MAX_VALUE);
					}
				}
			}.execute();


			return true;
		}

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			return false;
		}


		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			return false;
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {
			searchCounter = null;
			searchActionMode = null;
			if (composeMessageAdapter != null) {
				composeMessageAdapter.clearFilter();
			}
			if (bottomPanel != null) {
				bottomPanel.setVisibility(View.VISIBLE);
			}
		}
	}

	private void updateToolBarTitleInUIThread() {
			RuntimeUtil.runOnUiThread(this::updateToolbarTitle);
	}

	private void updateContactModelData(final ContactModel contactModel) {
		if(this.composeMessageAdapter != null) {
			RuntimeUtil.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					//update header
					if(contactModel.getIdentity().equals(identity)) {
						updateToolbarTitle();
					}

					composeMessageAdapter.resetCachedContactModelData(contactModel);
				}
			});
		}
	}

	final protected boolean requiredInstances() {
		if(!this.checkInstances()) {
			this.instantiate();
		}
		return this.checkInstances();
	}

	protected boolean checkInstances() {
		return TestUtil.required(
				this.preferenceService,
				this.userService,
				this.contactService,
				this.groupService,
				this.messageService,
				this.fileService,
				this.notificationService,
				this.distributionListService,
				this.messagePlayerService,
				this.blackListIdentityService,
				this.ballotService,
				this.conversationService,
				this.deviceService,
				this.wallpaperService,
				this.mutedChatsListService,
				this.ringtoneService,
				this.voipStateService,
				this.shortcutService
		);
	}

	protected void instantiate() {
		ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager != null) {
			this.preferenceService = serviceManager.getPreferenceService();
			try {
				this.userService = serviceManager.getUserService();
				this.contactService = serviceManager.getContactService();
				this.groupService = serviceManager.getGroupService();
				this.messageService = serviceManager.getMessageService();
				this.fileService = serviceManager.getFileService();
				this.notificationService = serviceManager.getNotificationService();
				this.distributionListService = serviceManager.getDistributionListService();
				this.messagePlayerService = serviceManager.getMessagePlayerService();
				this.blackListIdentityService = serviceManager.getBlackListService();
				this.ballotService = serviceManager.getBallotService();
				this.conversationService = serviceManager.getConversationService();
				this.deviceService =serviceManager.getDeviceService();
				this.wallpaperService = serviceManager.getWallpaperService();
				this.mutedChatsListService = serviceManager.getMutedChatsListService();
				this.mentionOnlyChatsListService = serviceManager.getMentionOnlyChatsListService();
				this.hiddenChatsListService = serviceManager.getHiddenChatsListService();
				this.ringtoneService = serviceManager.getRingtoneService();
				this.voipStateService = serviceManager.getVoipStateService();
				this.shortcutService = serviceManager.getShortcutService();
				this.downloadService = serviceManager.getDownloadService();
				this.licenseService = serviceManager.getLicenseService();
			} catch (Exception e) {
				LogUtil.exception(e, activity);
			}
		}
	}

	// Dialog callbacks
	@Override
	public void onYes(String tag, Object data) {
		switch (tag) {
			case CONFIRM_TAG_DELETE_DISTRIBUTION_LIST:
				final DistributionListModel dmodel = (DistributionListModel) data;
				if (dmodel != null) {
					new Thread(new Runnable() {
						@Override
						public void run() {
							distributionListService.remove(dmodel);

							RuntimeUtil.runOnUiThread(() -> activity.finish());
						}
					}).start();
				}
				break;
			case ThreemaApplication.CONFIRM_TAG_CLOSE_BALLOT:
				BallotUtil.closeBallot((AppCompatActivity) getActivity(), (BallotModel) data, ballotService);
				break;
			case DIALOG_TAG_CONFIRM_CALL:
				VoipUtil.initiateCall((AppCompatActivity) getActivity(), contactModel, false, null);
				break;
			case DIALOG_TAG_EMPTY_CHAT:
				emptyChat();
				break;
			case DIALOG_TAG_CONFIRM_BLOCK:
				blackListIdentityService.toggle(activity, contactModel);
				updateBlockMenu();
				break;
			case DIALOG_TAG_CONFIRM_LINK:
				Uri uri = (Uri) data;
				LinkifyUtil.getInstance().openLink(getContext(), uri);
				break;
			default:
				break;
		}
	}

	@Override
	public void onNo(String tag, Object data) {
		//
	}

	@Override
	public void onEmojiPickerOpen() {
	}

	@Override
	public void onEmojiPickerClose() {
	}

	@Override
	public void onRequestPermissionsResult(int requestCode,
										   @NonNull String permissions[], @NonNull int[] grantResults) {
		if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
			switch (requestCode) {
				case PERMISSION_REQUEST_SAVE_MESSAGE:
					fileService.saveMedia(activity, coordinatorLayout, new CopyOnWriteArrayList<>(selectedMessages), false);
					break;
				case PERMISSION_REQUEST_ATTACH_VOICE_MESSAGE:
					attachVoiceMessage();
					break;
				case PERMISSION_REQUEST_ATTACH_CAMERA:
					updateCameraButton();
					attachCamera();
					break;
			}
		} else {
			switch (requestCode) {
				case PERMISSION_REQUEST_SAVE_MESSAGE:
					if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
						showPermissionRationale(R.string.permission_storage_required);
					}
					break;
				case PERMISSION_REQUEST_ATTACH_VOICE_MESSAGE:
					if (!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
						showPermissionRationale(R.string.permission_record_audio_required);
					}
					break;
				case PERMISSION_REQUEST_ATTACH_CAMERA:
				case PERMISSION_REQUEST_ATTACH_CAMERA_VIDEO:
					preferenceService.setCameraPermissionRequestShown(true);
					if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
						showPermissionRationale(R.string.permission_camera_photo_required);
					}
					updateCameraButton();
					break;
			}
		}
	}

	/* properly dispose of popups */

	private void dismissMentionPopup() {
		if (this.mentionPopup != null) {
			try {
				this.mentionPopup.dismiss();
			} catch( final IllegalArgumentException e){
				// whatever
			} finally{
				this.mentionPopup = null;
			}
		}
	}

	private void dismissTooltipPopup(TooltipPopup tooltipPopup, boolean immediate) {
		try {
			if (tooltipPopup != null) {
				tooltipPopup.dismiss(immediate);
			}
		} catch (final IllegalArgumentException e) {
			// whatever
		}
	}

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		if (this.emojiPicker != null && this.emojiPicker.isShown()) {
			this.emojiPicker.hide();
		}
		EditTextUtil.hideSoftKeyboard(this.messageText);
		dismissMentionPopup();
		dismissTooltipPopup(workTooltipPopup, true);
		workTooltipPopup = null;

		if (ConfigUtils.isTabletLayout()) {
			// make sure layout changes after rotate are reflected in thumbnail size etc.
			saveMessageDraft();
			this.handleIntent(activity.getIntent());
		} else {
			if (isAdded()) {
				// refresh wallpaper to reflect orientation change
				this.wallpaperService.setupWallpaperBitmap(this.messageReceiver, this.wallpaperView, ConfigUtils.isLandscape(activity));
			}
		}
	}

	private void restoreMessageDraft() {
		if (this.messageReceiver != null && this.messageText != null && TestUtil.empty(this.messageText.getText())) {
			String messageDraft = ThreemaApplication.getMessageDraft(this.messageReceiver.getUniqueIdString());

			if (!TextUtils.isEmpty(messageDraft)) {
				this.messageText.setText("");
				this.messageText.append(messageDraft);
			} else {
				this.messageText.setText("");
			}
		}
	}

	private void saveMessageDraft() {
		if (this.messageReceiver != null) {
			String draft = ThreemaApplication.getMessageDraft(this.messageReceiver.getUniqueIdString());
			if (this.messageText.getText() != null) {
				ThreemaApplication.putMessageDraft(this.messageReceiver.getUniqueIdString(), this.messageText.getText().toString());
			}
			if (!TestUtil.empty(this.messageText.getText()) || !TestUtil.empty(draft)) {
				ListenerManager.conversationListeners.handle(new ListenerManager.HandleListener<ConversationListener>() {
					@Override
					public void handle(ConversationListener listener) {
						listener.onModifiedAll();
					}
				});
			}
		}
	}

	@Override
	public void onDismissed() {
		updateMenus();
	}

	@Override
	public void onKeyboardHidden() {
		if (getActivity() != null && isAdded()) {
			dismissMentionPopup();
			dismissTooltipPopup(workTooltipPopup, false);
			workTooltipPopup = null;
		}
	}

	@Override
	public void onKeyboardShown() {
		if (emojiPicker != null && emojiPicker.isShown()) {
			emojiPicker.hide();
		}
	}
}


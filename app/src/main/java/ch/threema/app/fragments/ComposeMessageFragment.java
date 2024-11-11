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

package ch.threema.app.fragments;

import android.Manifest;
import android.animation.Animator;
import android.animation.LayoutTransition;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.util.SparseBooleanArray;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
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
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.Filter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetView;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.badge.BadgeUtils;
import com.google.android.material.badge.ExperimentalBadgeUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.elevation.ElevationOverlayProvider;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputLayout;
import com.google.common.util.concurrent.ListenableFuture;

import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.AnyThread;
import androidx.annotation.ColorInt;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.compose.ui.platform.ComposeView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsAnimationCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
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
import ch.threema.app.activities.GroupNotificationsActivity;
import ch.threema.app.activities.HomeActivity;
import ch.threema.app.activities.ImagePaintActivity;
import ch.threema.app.activities.MediaGalleryActivity;
import ch.threema.app.activities.RecipientListBaseActivity;
import ch.threema.app.activities.SendMediaActivity;
import ch.threema.app.activities.MessageDetailsActivity;
import ch.threema.app.activities.ThreemaActivity;
import ch.threema.app.activities.ThreemaToolbarActivity;
import ch.threema.app.activities.WorkExplainActivity;
import ch.threema.app.activities.ballot.BallotOverviewActivity;
import ch.threema.app.adapters.ComposeMessageAdapter;
import ch.threema.app.adapters.decorators.ChatAdapterDecorator;
import ch.threema.app.asynctasks.EmptyOrDeleteConversationsAsyncTask;
import ch.threema.app.cache.ThumbnailCache;
import ch.threema.app.compose.common.interop.ComposeJavaBridge;
import ch.threema.app.dialogs.ExpandableTextEntryDialog;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.dialogs.MessageDetailDialog;
import ch.threema.app.dialogs.ResendGroupMessageDialog;
import ch.threema.app.dialogs.SelectorDialog;
import ch.threema.app.emojis.EmojiButton;
import ch.threema.app.emojis.EmojiMarkupUtil;
import ch.threema.app.emojis.EmojiPicker;
import ch.threema.app.emojis.EmojiTextView;
import ch.threema.app.glide.AvatarOptions;
import ch.threema.app.grouplinks.IncomingGroupRequestActivity;
import ch.threema.app.grouplinks.OpenGroupRequestNoticeView;
import ch.threema.app.listeners.BallotListener;
import ch.threema.app.listeners.ContactListener;
import ch.threema.app.listeners.ContactTypingListener;
import ch.threema.app.listeners.ConversationListener;
import ch.threema.app.listeners.GroupListener;
import ch.threema.app.listeners.MessageDeletedForAllListener;
import ch.threema.app.listeners.MessageListener;
import ch.threema.app.listeners.MessagePlayerListener;
import ch.threema.app.listeners.QRCodeScanListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.mediaattacher.MediaAttachActivity;
import ch.threema.app.mediaattacher.MediaFilterQuery;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
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
import ch.threema.app.services.notification.NotificationService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.QRCodeServiceImpl;
import ch.threema.app.services.RingtoneService;
import ch.threema.app.services.UserService;
import ch.threema.app.services.VoiceMessagePlayerService;
import ch.threema.app.services.WallpaperService;
import ch.threema.app.services.ballot.BallotService;
import ch.threema.app.services.license.LicenseService;
import ch.threema.app.services.messageplayer.MessagePlayerService;
import ch.threema.app.ui.AckjiPopup;
import ch.threema.app.ui.AvatarView;
import ch.threema.app.ui.ContentCommitComposeEditText;
import ch.threema.app.ui.ConversationListView;
import ch.threema.app.ui.DebouncedOnClickListener;
import ch.threema.app.ui.ListViewTouchSwipeListener;
import ch.threema.app.ui.LockableScrollView;
import ch.threema.app.ui.LongToast;
import ch.threema.app.ui.MediaItem;
import ch.threema.app.ui.OngoingCallNoticeMode;
import ch.threema.app.ui.OngoingCallNoticeView;
import ch.threema.app.ui.OpenBallotNoticeView;
import ch.threema.app.ui.QRCodePopup;
import ch.threema.app.ui.QuotePopup;
import ch.threema.app.ui.ReportSpamView;
import ch.threema.app.ui.RootViewDeferringInsetsCallback;
import ch.threema.app.ui.ScrollButtonManager;
import ch.threema.app.ui.SelectorDialogItem;
import ch.threema.app.ui.SendButton;
import ch.threema.app.ui.SingleToast;
import ch.threema.app.ui.TooltipPopup;
import ch.threema.app.ui.TranslateDeferringInsetsAnimationCallback;
import ch.threema.app.ui.TypingIndicatorTextWatcher;
import ch.threema.app.ui.VerificationLevelImageView;
import ch.threema.app.ui.listitemholder.ComposeMessageHolder;
import ch.threema.app.utils.AnimationUtil;
import ch.threema.app.utils.AppRestrictionUtil;
import ch.threema.app.utils.BallotUtil;
import ch.threema.app.utils.ColorUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ContactUtil;
import ch.threema.app.utils.ConversationUtil;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.EditTextUtil;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.GroupCallUtilKt;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.LinkifyUtil;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.app.utils.LogUtil;
import ch.threema.app.utils.NavigationUtil;
import ch.threema.app.utils.QuoteUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.ShortcutUtil;
import ch.threema.app.utils.SoundUtil;
import ch.threema.app.utils.TapTargetViewUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.ToolbarUtil;
import ch.threema.app.voicemessage.VoiceRecorderActivity;
import ch.threema.app.voip.groupcall.GroupCallDescription;
import ch.threema.app.voip.groupcall.GroupCallManager;
import ch.threema.app.voip.groupcall.GroupCallObserver;
import ch.threema.app.voip.listeners.VoipCallEventListener;
import ch.threema.app.voip.managers.VoipListenerManager;
import ch.threema.app.voip.services.VoipCallService;
import ch.threema.app.voip.services.VoipStateService;
import ch.threema.app.voip.util.VoipUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.app.messagereceiver.SendingPermissionValidationResult;
import ch.threema.domain.models.IdentityType;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.models.VerificationLevel;
import ch.threema.domain.protocol.ThreemaFeature;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.domain.protocol.csp.messages.file.FileData;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.factories.RejectedGroupMessageFactory;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.DateSeparatorMessageModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.FirstUnreadMessageModel;
import ch.threema.app.utils.GroupFeatureSupport;
import ch.threema.app.utils.GroupFeatureAdoptionRate;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.ballot.BallotModel;
import ch.threema.storage.models.data.MessageContentsType;

import static android.view.WindowManager.LayoutParams.FLAG_SECURE;
import static androidx.core.view.WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_STOP;
import static ch.threema.app.ThreemaApplication.getAppContext;
import static ch.threema.app.activities.HomeActivity.THREEMA_CHANNEL_IDENTITY;
import static ch.threema.app.adapters.ComposeMessageAdapter.MIN_CONSTRAINT_LENGTH;
import static ch.threema.app.preference.SettingsAdvancedOptionsFragment.THREEMA_SUPPORT_IDENTITY;
import static ch.threema.app.services.messageplayer.MessagePlayer.SOURCE_AUDIORECORDER;
import static ch.threema.app.services.messageplayer.MessagePlayer.SOURCE_LIFECYCLE;
import static ch.threema.app.services.messageplayer.MessagePlayer.SOURCE_VOIP;
import static ch.threema.app.ui.AckjiPopup.ITEM_ACK;
import static ch.threema.app.ui.AckjiPopup.ITEM_DEC;
import static ch.threema.app.ui.AckjiPopup.ITEM_EDIT;
import static ch.threema.app.ui.AckjiPopup.ITEM_IMAGE_REPLY;
import static ch.threema.app.ui.AckjiPopup.ITEM_INFO;
import static ch.threema.app.ui.AckjiPopup.ITEM_STAR;
import static ch.threema.app.ui.ScrollButtonManager.SCROLLBUTTON_VIEW_TIMEOUT;
import static ch.threema.app.ui.ScrollButtonManager.TYPE_DOWN;
import static ch.threema.app.utils.LinkifyUtil.DIALOG_TAG_CONFIRM_LINK;
import static ch.threema.app.utils.MessageUtil.canDeleteRemotely;
import static ch.threema.app.utils.ShortcutUtil.TYPE_CHAT;
import static ch.threema.storage.models.data.DisplayTag.DISPLAY_TAG_STARRED;

public class ComposeMessageFragment extends Fragment implements
	LifecycleOwner,
	DefaultLifecycleObserver,
	SwipeRefreshLayout.OnRefreshListener,
	GenericAlertDialog.DialogClickListener,
	ChatAdapterDecorator.ActionModeStatus,
	SelectorDialog.SelectorDialogClickListener,
	EmojiPicker.EmojiPickerListener,
	ReportSpamView.OnReportButtonClickListener,
	ThreemaToolbarActivity.OnSoftKeyboardChangedListener,
	ExpandableTextEntryDialog.ExpandableTextEntryDialogClickListener {

	private static final Logger logger = LoggingUtil.getThreemaLogger("ComposeMessageFragment");

	private static final String CONFIRM_TAG_DELETE_DISTRIBUTION_LIST = "deleteDistributionList";
	public static final String DIALOG_TAG_CONFIRM_CALL = "dtcc";
	private static final String DIALOG_TAG_CHOOSE_SHORTCUT_TYPE = "st";
	private static final String DIALOG_TAG_EMPTY_CHAT = "ccc";
	private static final String DIALOG_TAG_CONFIRM_BLOCK = "block";
	private static final String DIALOG_TAG_DECRYPTING_MESSAGES = "dcr";
	private static final String DIALOG_TAG_SEARCHING = "src";
	private static final String DIALOG_TAG_LOADING_MESSAGES = "loadm";
	private static final String DIALOG_TAG_MESSAGE_DETAIL = "messageLog";
	private static final String DIALOG_TAG_CONFIRM_MESSAGE_DELETE = "msgdel";
	private static final String DIALOG_TAG_CONFIRM_RESEND = "confirm_resend";
	private static final String DIALOG_TAG_EDIT_MESSAGES_UNSUPPORTED_WARNING = "editmsg_unsupported";
	private static final String DIALOG_TAG_DELETE_MESSAGES_UNSUPPORTED_WARNING = "deletemsg_unsupported";

	public static final String EXTRA_API_MESSAGE_ID = "apimsgid";
	public static final String EXTRA_SEARCH_QUERY = "searchQuery";
	public static final String EXTRA_OVERRIDE_BACK_TO_HOME_BEHAVIOR = "backOverride";
	public static final String EXTRA_LAST_MEDIA_SEARCH_QUERY = "searchMediaQuery";
	public static final String EXTRA_LAST_MEDIA_TYPE_QUERY = "searchMediaType";

	private static final int PERMISSION_REQUEST_SAVE_MESSAGE = 2;
	private static final int PERMISSION_REQUEST_ATTACH_VOICE_MESSAGE = 7;
	private static final int PERMISSION_REQUEST_ATTACH_CAMERA = 8;
	private static final int PERMISSION_REQUEST_ATTACH_CAMERA_VIDEO = 11;

	private static final int ACTIVITY_ID_VOICE_RECORDER = 9731;

	public static final long VIBRATION_MSEC = 300;
	private static final long MESSAGE_PAGE_SIZE = 100;
	private static final int SMOOTHSCROLL_THRESHOLD = 10;
	private static final int MAX_SELECTED_ITEMS = 100; // may not be larger than MESSAGE_PAGE_SIZE
	public static final int MAX_FORWARDABLE_ITEMS = 50;

	private static final String CAMERA_URI = "camera_uri";
	private static final String BUNDLE_LIST_POSITION = "list_position";
	private static final String BUNDLE_LIST_RECEIVER_ID = "list_receiver_id";
	private static final String BUNDLE_LIST_TOP = "list_top";
	private static final String BUNDLE_LIST_LONG_CLICK_ITEM = "list_long_click_item";

	private ContentCommitComposeEditText messageText;
	private SendButton sendButton;
	private ImageButton attachButton, cameraButton, sendEditMessageButton;
	private ContactModel contactModel;
	private @Nullable MessageReceiver messageReceiver;

	private AudioManager audioManager;
	private ConversationListView convListView;
	private FrameLayout historyParent;
	private @Nullable ComposeMessageAdapter composeMessageAdapter;
	private View isTypingView;

	private MenuItem mutedMenuItem;
	private MenuItem blockMenuItem;
	private MenuItem deleteDistributionListItem;
	private MenuItem callItem;
	private MenuItem showOpenBallotWindowMenuItem;
	private MenuItem showBallotsMenuItem;
	private MenuItem showAllGroupRequestsMenuItem;
	private MenuItem showOpenGroupRequestsMenuItem;
	private MenuItem showEmptyChatMenuItem;
	private TextView dateTextView;
	private TextInputLayout textInputLayout;

	private ActionMode actionMode = null;
	private ActionMode searchActionMode = null;
	private ActionMode editMessageActionMode = null;
	private FrameLayout dateView = null;
	private FrameLayout bottomPanel = null;
	private String identity;
	private Integer groupId = 0;
	private Long distributionListId = 0L;
	private Uri cameraUri;
	private long intentTimestamp = 0L;
	private int longClickItem = AbsListView.INVALID_POSITION;
	private int listViewTop = 0, lastFirstVisibleItem = -1;
	private TypingIndicatorTextWatcher typingIndicatorTextWatcher;
	private Map<String, Integer> identityColors;
	private MediaFilterQuery lastMediaFilter;
	private RootViewDeferringInsetsCallback deferringInsetsListener = null;

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
	private DownloadService downloadService;
	private LicenseService licenseService;

	private ActivityResultLauncher<Intent> wallpaperLauncher;
	private final ActivityResultLauncher<Intent> imageReplyLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
		if (result.getResultCode() == Activity.RESULT_CANCELED) {
			logger.info("Canceled image reply");
			return;
		}

		Intent resultIntent = result.getData();
		if (resultIntent == null) {
			logger.error("Result intent must not be null");
			return;
		}

		@SuppressWarnings("rawtypes")
		MessageReceiver receiver = IntentDataUtil.getMessageReceiverFromIntent(getContext(), resultIntent);
		MediaItem mediaItem = resultIntent.getParcelableExtra(Intent.EXTRA_STREAM);
		if (receiver == null) {
			logger.error("The receiver must not be null");
			return;
		}
		if (mediaItem == null) {
			logger.error("The media item must not be null");
			return;
		}

		messageService.sendMediaAsync(Collections.singletonList(mediaItem), Collections.singletonList(receiver));
	});

	private boolean listUpdateInProgress = false, isPaused = false;
	@NonNull
	private final List<AbstractMessageModel> unreadMessages = new ArrayList<>();
	@NonNull
	private final List<AbstractMessageModel> messageValues = new ArrayList<>();
	@NonNull
	private final List<AbstractMessageModel> selectedMessages = new ArrayList<>(1);

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
	private TooltipPopup workTooltipPopup;
	private AckjiPopup ackjiPopup;
	private QuotePopup quotePopup;
	private OpenBallotNoticeView openBallotNoticeView;
	private OpenGroupRequestNoticeView openGroupRequestNoticeView;
	private ReportSpamView reportSpamView;
	private ComposeMessageActivity activity;
	private View fragmentView;
	private CoordinatorLayout coordinatorLayout;
	private BallotService ballotService;
	private DatabaseServiceNew databaseServiceNew;
	private LayoutInflater layoutInflater;
	private ListViewTouchSwipeListener listViewTouchSwipeListener;

	private GroupService groupService;
	private GroupCallManager groupCallManager;
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
	private String conversationUid = null;
	private int unreadCount = 0, recentlyAddedCount = 0;
	private TextView searchCounter;
	private CircularProgressIndicator searchProgress;
	private ImageView searchNextButton, searchPreviousButton;
	private ViewGroup editMessageBubbleContainer;
	private View dimBackground;
	private ComposeView editMessageBubbleComposeView;

	private OngoingCallNoticeView ongoingCallNotice;
	private GroupCallObserver groupCallObserver;
	private ScrollButtonManager scrollButtonManager;

	@SuppressLint("SimpleDateFormat")
	private final SimpleDateFormat dayFormatter = new SimpleDateFormat("yyyyMMdd");
	private ThumbnailCache<?> thumbnailCache = null;


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

		@Override
		public int[] displayTags() {
			return null;
		}
	};

	// handler to remove dateview button after a certain time
	private final Handler dateViewHandler = new Handler();
	private final Runnable dateViewTask = () -> RuntimeUtil.runOnUiThread(() -> {
		if (dateView != null && dateView.getVisibility() == View.VISIBLE) {
			AnimationUtil.slideOutAnimation(dateView, false, 1f, null);
		}
	});

	// Listeners
	private final VoipCallEventListener voipCallEventListener = new VoipCallEventListener() {
		@Override
		public void onRinging(String peerIdentity) {
		}

		@Override
		public void onStarted(String peerIdentity, boolean outgoing) {
			logger.info("VoipCallEventListener onStarted"); // TODO(ANDR-2441): re-set to debug level
			updateVoipCallMenuItem(false);
			if (messagePlayerService != null) {
				messagePlayerService.pauseAll(SOURCE_VOIP);
			}
		}

		@Override
		public void onFinished(long callId, @NonNull String peerIdentity, boolean outgoing, int duration) {
			logger.info("VoipCallEventListener onFinished"); // TODO(ANDR-2441): re-set to debug level
			updateVoipCallMenuItem(true);
			hideOngoingVoipCallNotice();
		}

		@Override
		public void onRejected(long callId, String peerIdentity, boolean outgoing, byte reason) {
			logger.info("VoipCallEventListener onRejected"); // TODO(ANDR-2441): re-set to debug level
			updateVoipCallMenuItem(true);
			hideOngoingVoipCallNotice();
		}

		@Override
		public void onMissed(long callId, String peerIdentity, boolean accepted, @Nullable Date date) {
			logger.info("VoipCallEventListener onMissed"); // TODO(ANDR-2441): re-set to debug level
			updateVoipCallMenuItem(true);
			hideOngoingVoipCallNotice();
		}

		@Override
		public void onAborted(long callId, String peerIdentity) {
			logger.info("VoipCallEventListener onAborted"); // TODO(ANDR-2441): re-set to debug level
			updateVoipCallMenuItem(true);
			hideOngoingVoipCallNotice();
		}
	};

	private final MessageListener messageListener = new MessageListener() {
		@Override
		public void onNew(final AbstractMessageModel newMessage) {
			if (newMessage != null) {
				RuntimeUtil.runOnUiThread(() -> {
					if (isAdded() && !isDetached() && !isRemoving()) {
						if (newMessage.isOutbox()) {
							if (addMessageToList(newMessage)) {
								if (!newMessage.isStatusMessage() && newMessage.getType() != MessageType.VOIP_STATUS && newMessage.getType() != MessageType.GROUP_CALL_STATUS) {
									playSentSound();

									if (reportSpamView != null && reportSpamView.getVisibility() == View.VISIBLE) {
										reportSpamView.hide();
									}
								}
							}
						} else {
							if (addMessageToList(newMessage) && !isPaused) {
								if (!newMessage.isStatusMessage() && newMessage.getType() != MessageType.VOIP_STATUS && newMessage.getType() != MessageType.GROUP_CALL_STATUS) {
									playReceivedSound();
								}

								if (convListView != null) {
									convListView.post(new Runnable() {
										@Override
										public void run() {
											if (convListView.canScrollList(View.SCROLL_AXIS_VERTICAL)) {
												// list view is not fully scrolled to the bottom
												if (scrollButtonManager != null) {
													scrollButtonManager.showButton(ScrollButtonManager.TYPE_DOWN, recentlyAddedCount);
												}
											}
										}
									});
								}
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
					composeMessageAdapter.notifyItemsChanged(modifiedMessageModels);
				}
			});
		}

		@Override
		public void onRemoved(final AbstractMessageModel removedMessageModel) {
			RuntimeUtil.runOnUiThread(() -> {
				if (TestUtil.required(composeMessageAdapter, removedMessageModel)) {
					composeMessageAdapter.remove(removedMessageModel);
				}
			});
		}

		@Override
		public void onRemoved(List<AbstractMessageModel> removedMessageModels) {
			RuntimeUtil.runOnUiThread(() -> {
				if (TestUtil.required(composeMessageAdapter, removedMessageModels)) {
					for (AbstractMessageModel removedMessageModel : removedMessageModels) {
						composeMessageAdapter.remove(removedMessageModel);
					}
				}
			});
		}

		@Override
		public void onProgressChanged(AbstractMessageModel messageModel, int newProgress) {
			RuntimeUtil.runOnUiThread(() -> {
				if (composeMessageAdapter != null) {
					composeMessageAdapter.notifyItemsChanged(Collections.singletonList(messageModel));
				}
			});
		}

		@Override
		public void onResendDismissed(@NonNull AbstractMessageModel messageModel) {
			// Ignore
		}
	};

	private final MessageDeletedForAllListener messageDeletedForAllListener = new MessageDeletedForAllListener() {
		@Override
		public void onDeletedForAll(@NonNull AbstractMessageModel message) {
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
				RuntimeUtil.runOnUiThread(() -> finishActivity());
			}
		}

		@Override
		public void onNewMember(GroupModel group, String newIdentity, int previousMemberCount) {
			updateToolBarTitleInUIThread();
		}

		@Override
		public void onMemberLeave(GroupModel group, String identity, int previousMemberCount) {
			updateToolBarTitleInUIThread();
		}

		@Override
		public void onMemberKicked(GroupModel group, String identity, int previousMemberCount) {
			updateToolBarTitleInUIThread();

			if (userService.isMe(identity)) {
				updateGroupCallObserverRegistration();
			}
		}


		@Override
		public void onUpdate(GroupModel groupModel) {
			updateToolBarTitleInUIThread();

			updateGroupCallObserverRegistration();
		}

		@Override
		public void onLeave(GroupModel groupModel) {
			if (isGroupChat && groupId != null && groupId == groupModel.getId()) {
				RuntimeUtil.runOnUiThread(() -> finishActivity());
			}
		}

		private void updateGroupCallObserverRegistration() {
			// groupModel may be null if Fragment was re-configured with a new intent
			if (isGroupChat && groupModel != null && groupService.isGroupMember(groupModel)) {
				registerGroupCallObserver();
			} else {
				// Remove ongoing group call notice if not a member of the group anymore
				updateOngoingCallNotice();
				removeGroupCallObserver();
			}
		}
	};

	private final ContactListener contactListener = new ContactListener() {
		@Override
		public void onModified(final @NonNull String identity) {
			if (!identity.equals(ComposeMessageFragment.this.identity)) {
				// Another contact was updated
				return;
			}
			final ContactModel modifiedContactModel = contactService.getByIdentity(identity);
			if (modifiedContactModel != null) {
				RuntimeUtil.runOnUiThread(() -> updateContactModelData(modifiedContactModel));
			}
		}

		@Override
		public void onAvatarChanged(ContactModel contactModel) {
			updateToolBarTitleInUIThread();
		}

		@Override
		public void onRemoved(@NonNull String identity) {
			if (contactModel != null && contactModel.getIdentity().equals(identity)) {
				// our contact has been removed. finish activity.
				RuntimeUtil.runOnUiThread(() -> finishActivity());
			}
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
		public void onAudioPlayEnded(AbstractMessageModel messageModel, ListenableFuture<MediaController> mediaControllerFuture) {
			// Play next audio message, if any
			RuntimeUtil.runOnUiThread(() -> {
				if (composeMessageAdapter != null) {
					int index = composeMessageAdapter.getNextVoiceMessage(messageModel);
					if (index != AbsListView.INVALID_POSITION) {
						View view = composeMessageAdapter.getView(index, null, null);

						ComposeMessageHolder holder = (ComposeMessageHolder) view.getTag();
						if (holder.messagePlayer != null) {
							holder.messagePlayer.open();
							composeMessageAdapter.notifyDataSetChanged();
						}
					} else {
						if (mediaControllerFuture != null) {
							try {
								MediaController mediaController = mediaControllerFuture.get();
								if (mediaController != null) {
									mediaController.stop();
									mediaController.clearMediaItems();
								}
							} catch (Exception e) {
								logger.error("Unable to clear MediaController", e);
							}
						}
					}
				}
			});
		}
	};

	private final QRCodeScanListener qrCodeScanListener = new QRCodeScanListener() {
		@Override
		public void onScanCompleted(final String scanResult) {
			if (scanResult != null && scanResult.length() > 0) {
				if (messageReceiver != null) {
					ThreemaApplication.putMessageDraft(messageReceiver.getUniqueIdString(), scanResult, null);
				}
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
		public void onRemoved(BallotModel ballotModel) { }

		@Override
		public boolean handle(BallotModel ballotModel) {
			return ballotModel != null && userService.getIdentity().equals(ballotModel.getCreatorIdentity());
		}
	};

	private final QuotePopup.QuotePopupListener quotePopupListener = new QuotePopup.QuotePopupListener() {
		@Override
		public void onHeightSet(int height) {
			if (historyParent != null) {
				historyParent.postDelayed(() ->
					historyParent.setPadding(
					historyParent.getPaddingLeft(),
					historyParent.getPaddingTop(),
					historyParent.getPaddingRight(),
					height), 30);
			}
		}

		@Override
		public void onDismiss() {
			if (historyParent != null) {
				historyParent.postDelayed(() ->
					historyParent.setPadding(
                    historyParent.getPaddingLeft(),
                    historyParent.getPaddingTop(),
                    historyParent.getPaddingRight(),
                    0), 70);
			}
		}

		@Override
		public void onPostVisibilityChange() {
			if (messageText != null) {
				updateSendButton(messageText.getText());
				updateCameraButton();
			}
		}
	};

	@SuppressLint("StaticFieldLeak")
	@Override
	public void onRefresh() {
		logger.debug("onRefresh");

		if (actionMode != null || searchActionMode != null || editMessageActionMode != null) {
			swipeRefreshLayout.setRefreshing(false);
			return;
		}

		new AsyncTask<Void, Void, Boolean>() {
			private List<AbstractMessageModel> messageModels;

			@Override
			protected Boolean doInBackground(Void... params) {
				messageModels = getNextRecords();
				if (messageModels != null) {
					return messageModels.size() >= nextMessageFilter.getPageSize();
				}
				return false;
			}

			@Override
			protected void onPostExecute(Boolean hasMoreRecords) {
				if (composeMessageAdapter != null) {
					if (messageModels != null) {
						int numberOfInsertedRecords = insertToList(messageModels, false, true, true);
						if (numberOfInsertedRecords > 0) {
							convListView.setSelection(convListView.getSelectedItemPosition() + numberOfInsertedRecords + 1);
						}
					} else {
						composeMessageAdapter.notifyDataSetChanged();
					}
				}

				if (swipeRefreshLayout != null) {
					swipeRefreshLayout.setRefreshing(false);
					swipeRefreshLayout.setEnabled(hasMoreRecords);
				}
			}
		}.execute();
	}

	@Override
	public void onAttach(@NonNull Activity activity) {
		((FragmentActivity) activity).getLifecycle().addObserver(this);
		logger.debug("onAttach");

		super.onAttach(activity);

		setHasOptionsMenu(true);

		this.activity = (ComposeMessageActivity) activity;
		this.audioManager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);

		if (bottomPanel != null) {
			bottomPanel.setVisibility(View.VISIBLE);
		}

		if (this.emojiPicker != null) {
			this.emojiPicker.init(ThreemaApplication.requireServiceManager().getEmojiService(), true);
		}

		// resolution and layout may have changed after being attached to a new activity
		ConfigUtils.getPreferredThumbnailWidth(activity, true);
		ConfigUtils.getPreferredAudioMessageWidth(activity, true);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		logger.info("onCreate");
		super.onCreate(savedInstanceState);

		ListenerManager.contactTypingListeners.add(this.contactTypingListener);
		ListenerManager.messageListeners.add(this.messageListener, true);
		ListenerManager.messageDeletedForAllListener.add(this.messageDeletedForAllListener);
		ListenerManager.groupListeners.add(this.groupListener);
		ListenerManager.contactListeners.add(this.contactListener);
		ListenerManager.conversationListeners.add(this.conversationListener);
		ListenerManager.messagePlayerListener.add(this.messagePlayerListener);
		ListenerManager.qrCodeScanListener.add(this.qrCodeScanListener);
		ListenerManager.ballotListeners.add(this.ballotListener);
		VoipListenerManager.callEventListener.add(this.voipCallEventListener);

		initializeMedia3Controller();
	}

	@Override
	@ExperimentalBadgeUtils
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		logger.info("onCreateView");

		if (!requiredInstances()) {
			finishActivity();
			return this.fragmentView;
		}

		this.layoutInflater = inflater;

		if (this.fragmentView == null) {
			// set font size
			activity.getTheme().applyStyle(preferenceService.getFontStyle(), true);
			this.fragmentView = inflater.inflate(R.layout.fragment_compose_message, container, false);

			LockableScrollView sv = fragmentView.findViewById(R.id.wallpaper_scroll);
			sv.setEnabled(false);
			sv.setScrollingEnabled(false);
			sv.setOnTouchListener(null);
			sv.setOnClickListener(null);

			this.coordinatorLayout = fragmentView.findViewById(R.id.compose_root);

			this.convListView = fragmentView.findViewById(R.id.history);
			ViewCompat.setNestedScrollingEnabled(this.convListView, true);
			this.convListView.setDivider(null);
			this.convListView.setClipToPadding(false);
			this.convListView.getLayoutTransition().disableTransitionType(LayoutTransition.CHANGING);
			this.convListView.getLayoutTransition().disableTransitionType(LayoutTransition.CHANGE_APPEARING);
			this.convListView.getLayoutTransition().disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);

			if (ConfigUtils.isTabletLayout()) {
				this.convListView.setPadding(0, 0, 0, 0);
			}

			this.historyParent = fragmentView.findViewById(R.id.history_parent);

			this.listViewTop = this.convListView.getPaddingTop();
			this.swipeRefreshLayout = fragmentView.findViewById(R.id.ptr_layout);
			this.swipeRefreshLayout.setOnRefreshListener(this);
			this.swipeRefreshLayout.setColorSchemeResources(R.color.md_theme_light_primary);
			this.swipeRefreshLayout.setSize(SwipeRefreshLayout.LARGE);
			this.messageText = fragmentView.findViewById(R.id.embedded_text_editor);

			this.sendEditMessageButton = this.fragmentView.findViewById(R.id.confirm_edit_button);
			this.sendButton = this.fragmentView.findViewById(R.id.send_button);
			this.attachButton = this.fragmentView.findViewById(R.id.attach_button);
			this.cameraButton = this.fragmentView.findViewById(R.id.camera_button);
			this.cameraButton.setOnClickListener(v -> {
				if (actionMode != null) {
					actionMode.finish();
				}
				dismissQuotePopup();
				if (!validateSendingPermission()) {
					return;
				}
				if (ConfigUtils.requestCameraPermissions(activity, this, PERMISSION_REQUEST_ATTACH_CAMERA)) {
					attachCamera();
				}
			});
			updateCameraButton();

			this.emojiButton = this.fragmentView.findViewById(R.id.emoji_button);
			this.emojiButton.setOnClickListener(v -> showEmojiPicker());

			this.emojiMarkupUtil = EmojiMarkupUtil.getInstance();
			this.wallpaperView = this.fragmentView.findViewById(R.id.wallpaper_view);
			final MaterialButton quickscrollUpView = this.fragmentView.findViewById(R.id.quickscroll_top);
			final MaterialButton quickscrollDownView = this.fragmentView.findViewById(R.id.quickscroll_bottom);
			final FrameLayout quickscrollDownContainer = this.fragmentView.findViewById(R.id.quickscroll_bottom_container);
			this.dateView = this.fragmentView.findViewById(R.id.date_separator_container);
			this.dateTextView = this.fragmentView.findViewById(R.id.text_view);

			this.editMessageBubbleContainer = this.fragmentView.findViewById(R.id.edit_message_bubble_container);
			this.editMessageBubbleComposeView = this.fragmentView.findViewById(R.id.edit_message_bubble_compose_view);
			this.dimBackground = this.fragmentView.findViewById(R.id.dim_background);

			this.bottomPanel = this.fragmentView.findViewById(R.id.bottom_panel);
			this.openBallotNoticeView = this.fragmentView.findViewById(R.id.open_ballots_layout);
			this.openGroupRequestNoticeView = this.fragmentView.findViewById(R.id.open_group_requests_layout);
			this.reportSpamView = this.fragmentView.findViewById(R.id.report_spam_layout);
			this.reportSpamView.setListener(this);

			quickscrollDownContainer.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
	                quickscrollDownContainer.getViewTreeObserver().removeOnGlobalLayoutListener(this);

	                Context context = getContext();
					if (context != null) {
						final BadgeDrawable quickscrollDownBadge = BadgeDrawable.createFromResource(context, R.xml.badge_compose);
						quickscrollDownBadge.setHorizontalOffset(getResources().getDimensionPixelOffset(R.dimen.quickscroll_badge_offset));
						quickscrollDownBadge.setVerticalOffset(getResources().getDimensionPixelOffset(R.dimen.quickscroll_badge_offset));
						BadgeUtils.attachBadgeDrawable(quickscrollDownBadge, quickscrollDownView, quickscrollDownContainer);
						quickscrollDownBadge.setVisible(false);

						scrollButtonManager = new ScrollButtonManager(quickscrollUpView, quickscrollDownContainer, quickscrollDownBadge);
					}
                }
            });

			quickscrollDownView.setOnClickListener(v -> {
				removeDateView();
				if (scrollButtonManager != null) {
					scrollButtonManager.hideAllButtons();
				}
				scrollList(Integer.MAX_VALUE);

			});
			quickscrollUpView.setOnClickListener(v -> {
				removeDateView();
				if (scrollButtonManager != null) {
					scrollButtonManager.hideAllButtons();
				}
				scrollList(0);
			});

			// tint the edittext manually as TextInputLayout does not currently support elevation
			ElevationOverlayProvider elevationOverlayProvider = new ElevationOverlayProvider(activity);
			textInputLayout = fragmentView.findViewById(R.id.textinputlayout_compose);
			textInputLayout.setBoxBackgroundColor(elevationOverlayProvider.compositeOverlayIfNeeded(
				ConfigUtils.getColorFromAttribute(activity, R.attr.colorSurface),
				getResources().getDimension(R.dimen.compose_edittext_elevation)));

			this.getValuesFromBundle(savedInstanceState);
			this.handleIntent(activity.getIntent());
			this.setupListeners();
		}

		if (!ConfigUtils.isDefaultEmojiStyle()) {
			// remove emoji button
			this.emojiButton.setVisibility(View.GONE);
			this.messageText.setPadding(getResources().getDimensionPixelSize(R.dimen.no_emoji_button_padding_left), this.messageText.getPaddingTop(), this.messageText.getPaddingRight(), this.messageText.getPaddingBottom());
		} else {
			try {
				final EmojiPicker.EmojiKeyListener emojiKeyListener = new EmojiPicker.EmojiKeyListener() {
					@Override
					public void onBackspaceClick() {
						messageText.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
					}

					@Override
					public void onEmojiClick(String emojiCodeString) {
						RuntimeUtil.runOnUiThread(() -> messageText.addEmoji(emojiCodeString));
					}

					@Override
					public void onShowPicker() {
						showEmojiPicker();
					}
				};
				this.emojiPicker = (EmojiPicker) ((ViewStub) this.activity.findViewById(R.id.emoji_stub)).inflate();
				this.emojiPicker.init(ThreemaApplication.requireServiceManager().getEmojiService(), true);
				this.emojiButton.attach(this.emojiPicker);
				this.emojiPicker.setEmojiKeyListener(emojiKeyListener);
				this.emojiPicker.addEmojiPickerListener(this);
			} catch (Exception e) {
				logger.error("Exception", e);
				finishActivity();
			}
		}

		return this.fragmentView;
	}

	@AnyThread
	private void initOngoingCallState() {
		ongoingCallNotice = fragmentView.findViewById(R.id.ongoing_call_notice);
		if (ongoingCallNotice != null) {
			if (groupModel != null && groupService.isGroupMember(groupModel)) {
				registerGroupCallObserver();
			} else {
				updateOngoingCallNotice();
			}
		}
	}

	@AnyThread
	private void updateOngoingCallNotice() {
		boolean hasRunningOOCall = VoipCallService.isRunning()
			&& contactModel != null
			&& contactModel.getIdentity().equals(VoipCallService.getOtherPartysIdentity());

		GroupCallDescription chosenCall = getChosenCall();
		boolean hasRunningGroupCall = chosenCall != null;
		boolean hasJoinedGroupCall = hasRunningGroupCall
			&& groupCallManager.isJoinedCall(chosenCall);

		if (hasRunningOOCall && hasJoinedGroupCall) {
			logger.warn("Invalid state: joined 1:1 AND group call, not showing call notice");
			updateVoipCallMenuItem(true);
			hideOngoingCallNotice();
		} else if (hasRunningOOCall) {
			showOngoingVoipCallNotice();
		} else if (hasRunningGroupCall) {
			OngoingCallNoticeMode mode = hasJoinedGroupCall
				? OngoingCallNoticeMode.MODE_GROUP_CALL_JOINED
				: OngoingCallNoticeMode.MODE_GROUP_CALL_RUNNING;
			showOngoingGroupCallNotice(mode, chosenCall);
		} else {
			updateVoipCallMenuItem(true);
			hideOngoingCallNotice();
		}
	}

	@Nullable
	private GroupCallDescription getChosenCall() {
		return ConfigUtils.isGroupCallsEnabled() && groupModel != null && groupCallManager != null
			? groupCallManager.getCurrentChosenCall(groupModel)
			: null;
	}

	@AnyThread
	private void showOngoingVoipCallNotice() {
		logger.info("Show ongoing voip call notice (notice set: {})", ongoingCallNotice != null); // TODO(ANDR-2441): remove eventually
		if (ongoingCallNotice != null) {
			ongoingCallNotice.showVoip();
		}
	}

	@AnyThread
	private void hideOngoingVoipCallNotice() {
		logger.info("Hide ongoing voip call notice (notice set: {})", ongoingCallNotice != null); // TODO(ANDR-2441): remove eventually
		if (ongoingCallNotice != null) {
			ongoingCallNotice.hideVoip();
		}
	}

	@AnyThread
	private void hideOngoingCallNotice() {
		logger.info("Hide ongoing call notice (notice set: {})", ongoingCallNotice != null);  // TODO(ANDR-2441): remove eventually
		if (ongoingCallNotice != null) {
			ongoingCallNotice.hide();
		}
	}

	@AnyThread
	private void registerGroupCallObserver() {
		removeGroupCallObserver();
		if (groupModel != null && groupCallManager != null) {
			groupCallObserver = call -> updateOngoingCallNotice();
			logger.info("Add group call observer for group {}", groupModel.getId());
			groupCallManager.addGroupCallObserver(groupModel, groupCallObserver);
		}
	}

	@AnyThread
	private void showOngoingGroupCallNotice(OngoingCallNoticeMode mode, @NonNull GroupCallDescription call) {
		if (ongoingCallNotice != null) {
			ongoingCallNotice.showGroupCall(call, mode);
			updateVoipCallMenuItem(false);
		}
	}

	private boolean isEmojiPickerShown() {
		return emojiPicker != null && emojiPicker.isShown();
	}

	private void hideEmojiPickerIfShown() {
		if (isEmojiPickerShown()) {
			emojiPicker.hide();
		}
	}

	private void showEmojiPicker() {
		logger.debug("Emoji button clicked");

		if (activity.isSoftKeyboardOpen() && !isEmojiPickerShown()) {
			if (deferringInsetsListener != null) {
				deferringInsetsListener.setDisableAnimation(true);
			}

			logger.debug("Show emoji picker after keyboard close");
			activity.runOnSoftKeyboardClose(() -> {
				if (emojiPicker != null) {
					emojiPicker.show(activity.loadStoredSoftKeyboardHeight());
				}
			});

			messageText.post(() -> EditTextUtil.hideSoftKeyboard(messageText));
		} else {
			if (emojiPicker != null) {
				if (emojiPicker.isShown()) {
					logger.debug("Emoji picker currently shown. Closing.");
					if (ConfigUtils.isLandscape(activity) &&
						!ConfigUtils.isTabletLayout()) {
						emojiPicker.hide();
					} else {
						if (deferringInsetsListener != null) {
							deferringInsetsListener.setDisableAnimation(true);
						}
						activity.openSoftKeyboard(emojiPicker, messageText);
					}
				} else {
					logger.debug("Show emoji picker immediately");
					emojiPicker.show(activity.loadStoredSoftKeyboardHeight());
				}
			}
		}
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		logger.debug("onActivityCreated");

		super.onActivityCreated(savedInstanceState);
		/*
		 * This callback tells the fragment when it is fully associated with the new activity instance. This is called after onCreateView(LayoutInflater, ViewGroup, Bundle) and before onViewStateRestored(Bundle).
		 */
		if (preferenceService == null) {
			return;
		}

		try {
			View rootView = activity.getWindow().getDecorView().getRootView();
			if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
				try {
					ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
					if (decorView.getChildCount() == 1) {
						rootView = decorView.getChildAt(0);
					}
				} catch (Exception e) {
					logger.error("Exception", e);
				}
			}

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
				rootView.setBackgroundColor(ConfigUtils.getColorFromAttribute(requireContext(), android.R.attr.colorBackground));
			}

			deferringInsetsListener = new RootViewDeferringInsetsCallback(emojiPicker, activity);

			ViewCompat.setWindowInsetsAnimationCallback(rootView, deferringInsetsListener);

			ViewCompat.setOnApplyWindowInsetsListener(rootView, deferringInsetsListener);

			ViewCompat.setWindowInsetsAnimationCallback(
				bottomPanel,
				new TranslateDeferringInsetsAnimationCallback(
					bottomPanel,
					WindowInsetsCompat.Type.systemBars(),
					WindowInsetsCompat.Type.ime(),
					WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE
				)
			);
			ViewCompat.setWindowInsetsAnimationCallback(
				convListView,
				new TranslateDeferringInsetsAnimationCallback(
					convListView,
					WindowInsetsCompat.Type.systemBars(),
					WindowInsetsCompat.Type.ime(),
					DISPATCH_MODE_STOP
				)
			);
			if (emojiPicker != null) {
				ViewCompat.setWindowInsetsAnimationCallback(
					emojiPicker,
					new TranslateDeferringInsetsAnimationCallback(
						emojiPicker,
						WindowInsetsCompat.Type.systemBars(),
						WindowInsetsCompat.Type.ime(),
						DISPATCH_MODE_STOP
					)
				);
			}
		} catch (NullPointerException e) {
			logger.error("Exception", e);
		}
		activity.addOnSoftKeyboardChangedListener(this);
	}

	@Override
	public void onResume(@NonNull LifecycleOwner owner) {
		logger.info("onResume"); // TODO(ANDR-2441): Re-set to debug level

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

				r.addOnFinished(success -> {
					if (success) {
						unreadMessages.clear();
					}
				});

				new Thread(r).start();
			}

			// update menus
			updateMuteMenu();
			if (isGroupChat) {
				updateGroupCallMenuItem();
			}

			// start media players again
			this.messagePlayerService.resumeAll(getActivity(), this.messageReceiver, SOURCE_LIFECYCLE);

			// make sure to remark the active chat
			if (ConfigUtils.isTabletLayout()) {
				ListenerManager.chatListener.handle(listener -> listener.onChatOpened(this.conversationUid));
			}

			// restore scroll position after orientation change
			if (getActivity() != null) {
				Intent intent = getActivity().getIntent();
				if (intent != null && !intent.hasExtra(EXTRA_API_MESSAGE_ID) && !intent.hasExtra(EXTRA_SEARCH_QUERY)) {
					convListView.post(() -> {
						if (listInstancePosition != AbsListView.INVALID_POSITION &&
							messageReceiver != null &&
							messageReceiver.getUniqueIdString().equals(listInstanceReceiverId)) {
							logger.debug("restoring position " + listInstancePosition);
							convListView.setSelectionFromTop(listInstancePosition, listInstanceTop);
							if (activity != null && convListView.getCheckedItemCount() > 0 && actionMode == null) {
								SparseBooleanArray itemPositions = convListView.getCheckedItemPositions();
								for (int i = 0; i < itemPositions.size(); i++) {
									selectedMessages.add(composeMessageAdapter.getItem(itemPositions.keyAt(i)));
								}
								actionMode = activity.startSupportActionMode(new ComposeMessageAction(this.longClickItem));
							}
						} else {
							jumpToFirstUnreadMessage();
						}
						// make sure it's not restored twice
						listInstancePosition = AbsListView.INVALID_POSITION;
						listInstanceReceiverId = null;
					});
				}
			}

			// update group requests as they could have been changed when coming back from the overview activity
			if (ConfigUtils.supportsGroupLinks() && groupService.isGroupCreator(this.groupModel)) {
				this.openGroupRequestNoticeView.updateGroupRequests();
			}

			updateOngoingCallNotice();
		}
	}

	@Override
	public void onPause(@NonNull LifecycleOwner owner) {
		logger.info("onPause");
		isPaused = true;

		onEmojiPickerClose();

		if (this.notificationService != null) {
			this.notificationService.setVisibleReceiver(null);
		}

		// save unfinished text
		if (editMessageActionMode == null) {
			saveMessageDraft();
		}

		if(this.typingIndicatorTextWatcher != null) {
			this.typingIndicatorTextWatcher.stopTyping();
		}

		preserveListInstanceValues();
	}

	@Override
	public void onStop() {
		logger.info("onStop");

		if(this.typingIndicatorTextWatcher != null) {
			this.typingIndicatorTextWatcher.stopTyping();
		}

		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
			// close keyboard to prevent layout corruption after unlocking phone
			if (this.messageText != null) {
				EditTextUtil.hideSoftKeyboard(this.messageText);
			}
		}
		super.onStop();
	}

	@Override
	public void onDetach() {
		logger.debug("onDetach");

		hideEmojiPickerIfShown();
		dismissMentionPopup();
		dismissQuotePopup();

		this.activity = null;

		super.onDetach();
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		removeGroupCallObserver();
	}

	@Override
	public void onDestroy() {
		logger.debug("onDestroy");

		try {
			ListenerManager.contactTypingListeners.remove(this.contactTypingListener);
			ListenerManager.groupListeners.remove(this.groupListener);
			ListenerManager.messageListeners.remove(this.messageListener);
			ListenerManager.messageDeletedForAllListener.remove(this.messageDeletedForAllListener);
			ListenerManager.contactListeners.remove(this.contactListener);
			ListenerManager.conversationListeners.remove(this.conversationListener);
			ListenerManager.messagePlayerListener.remove(this.messagePlayerListener);
			ListenerManager.qrCodeScanListener.remove(this.qrCodeScanListener);
			ListenerManager.ballotListeners.remove(this.ballotListener);
			VoipListenerManager.callEventListener.remove(this.voipCallEventListener);

			if (scrollButtonManager != null) {
				scrollButtonManager.hideAllButtons();
			}

			dismissTooltipPopup(workTooltipPopup, true);
			workTooltipPopup = null;

			dismissMentionPopup();
			dismissQuotePopup();

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

			if (this.messagePlayerService != null) {
				this.messagePlayerService.release();
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

			removeIsTypingFooter();
			this.isTypingView = null;

			//clear all records to remove all references
			if(this.composeMessageAdapter != null) {
				this.composeMessageAdapter.clear();
				this.composeMessageAdapter = null;
			}

			releaseMedia3Controller();
		} catch (Exception x) {
			logger.error("Exception", x);
		}

		super.onDestroy();
	}

	private void removeDateView() {
		if (dateView != null && dateView.getVisibility() == View.VISIBLE) {
			AnimationUtil.slideOutAnimation(dateView, false, 1f, null);
		}

		if (actionMode != null) {
			actionMode.finish();
		}
	}

	private void setupListeners() {
		setupConversationListScrollListener();
		setupConversationListSwipeListener();
		setupSendButtonClickListener();
		setupAttachButtonClickListener();
		setupMessageTextListeners();
	}

	private void setupConversationListScrollListener() {
		// Setting this scroll listener is required to ensure that during ListView scrolling,
		// we don't look for swipes or pulldowns
		this.convListView.setOnScrollListener(new AbsListView.OnScrollListener() {
			@Override
			public void onScrollStateChanged(AbsListView absListView, int scrollState) {
				if (listViewTouchSwipeListener != null) {
					listViewTouchSwipeListener.setEnabled(scrollState != AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL);
				}

				if (!absListView.canScrollList(View.SCROLL_AXIS_VERTICAL)) {
					if (scrollButtonManager != null) {
						scrollButtonManager.hideButton(ScrollButtonManager.TYPE_DOWN);
					}
				}

				if (!absListView.canScrollList(-View.SCROLL_AXIS_VERTICAL)) {
					if (scrollButtonManager != null) {
						scrollButtonManager.hideButton(ScrollButtonManager.TYPE_UP);
					}
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
							if (view.canScrollList(View.SCROLL_AXIS_VERTICAL)) {
								if (scrollButtonManager != null) {
									scrollButtonManager.showButton(ScrollButtonManager.TYPE_DOWN, 0);
								}
							} else {
								if (scrollButtonManager != null) {
									scrollButtonManager.hideButton(ScrollButtonManager.TYPE_DOWN);
								}
								recentlyAddedCount = 0;
							}
						} else {
							// scrolling up
							if (view.canScrollList(-View.SCROLL_AXIS_VERTICAL)) {
								if (scrollButtonManager != null) {
									scrollButtonManager.showButton(ScrollButtonManager.TYPE_UP, 0);
								}
							} else {
								if (scrollButtonManager != null) {
									scrollButtonManager.hideButton(ScrollButtonManager.TYPE_UP);
								}
							}
						}

						lastFirstVisibleItem = firstVisibleItem;

						if (dateView.getVisibility() != View.VISIBLE && composeMessageAdapter != null && composeMessageAdapter.getCount() > 0) {
							AnimationUtil.slideInAnimation(dateView, false, 200);
						}

						dateViewHandler.removeCallbacks(dateViewTask);
						dateViewHandler.postDelayed(dateViewTask, SCROLLBUTTON_VIEW_TIMEOUT);

						if (composeMessageAdapter != null) {
							AbstractMessageModel abstractMessageModel = composeMessageAdapter.getItem(firstVisibleItem);
							if (abstractMessageModel != null) {
								final Date createdAt = abstractMessageModel.getCreatedAt();
								if (createdAt != null) {
									final String text = LocaleUtil.formatDateRelative(createdAt.getTime());
									dateTextView.setText(text);
									dateView.post(() -> dateTextView.setText(text));
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
	}

	private void setupConversationListSwipeListener() {
		listViewTouchSwipeListener = new ListViewTouchSwipeListener(
			this.convListView,
			new ListViewTouchSwipeListener.DismissCallbacks() {
				@Override
				public boolean canSwipe(int position) {
					if (actionMode != null) {
						return false;
					}

					if (messageReceiver == null || !messageReceiver.validateSendingPermission().isValid()) {
						return false;
					}

					int viewType = composeMessageAdapter.getItemViewType(position);

					if (viewType == ComposeMessageAdapter.TYPE_STATUS ||
						viewType == ComposeMessageAdapter.TYPE_FIRST_UNREAD ||
						viewType == ComposeMessageAdapter.TYPE_DATE_SEPARATOR  ||
						viewType == ComposeMessageAdapter.TYPE_GROUP_CALL_STATUS) {
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
					if (composeMessageAdapter == null) {
						return;
					}

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
						if (isQuotePopupShown() && abstractMessageModel.equals(quotePopup.getQuoteInfo().getMessageModel())) {
							dismissQuotePopup();
						} else {
							if (searchActionMode != null) {
								searchActionMode.finish();
							}
							showQuotePopup(abstractMessageModel);
						}
					}
				}
			}
		);
	}

	private void setupSendButtonClickListener() {
		if (sendButton != null) {
			sendButton.setOnClickListener(new DebouncedOnClickListener(500) {
				@Override
				public void onDebouncedClick(View v) {
					sendMessage();
				}
			});
		}
	}

	private void setupEditMessageButtonClickListener(AbstractMessageModel messageModel) {
		if (sendEditMessageButton != null) {
			sendEditMessageButton.setOnClickListener(new DebouncedOnClickListener(500) {
				@Override
				public void onDebouncedClick(View v) {
					onSendEditMessage(messageModel, String.valueOf(messageText.getText()));
				}
			});
		}
	}

	private void setupAttachButtonClickListener() {
		if (attachButton != null) {
			attachButton.setOnClickListener(new DebouncedOnClickListener(1000) {
				@Override
				public void onDebouncedClick(View v) {
					if (validateSendingPermission()) {
						if (actionMode != null) {
							actionMode.finish();
						}

						dismissQuotePopup();

						Intent intent = new Intent(activity, MediaAttachActivity.class);
						IntentDataUtil.addMessageReceiverToIntent(intent, messageReceiver);
						if (ComposeMessageFragment.this.lastMediaFilter != null) {
							intent = IntentDataUtil.addLastMediaFilterToIntent(intent, ComposeMessageFragment.this.lastMediaFilter);
						}
						activity.startActivityForResult(intent, ThreemaActivity.ACTIVITY_ID_ATTACH_MEDIA);
						activity.overridePendingTransition(R.anim.fast_fade_in, R.anim.fast_fade_out);
					}
				}
			});
		}
	}

	private void setupEditMessageTextActionListener(AbstractMessageModel messageModel) {
		this.messageText.setOnEditorActionListener(
			setupTextActionListener(() -> onSendEditMessage(messageModel, String.valueOf(messageText.getText())))
		);
	}

	private void setupSendMessageTextActionListener() {
		this.messageText.setOnEditorActionListener(
			setupTextActionListener(this::sendMessage)
		);
	}

	private TextView.OnEditorActionListener setupTextActionListener(Runnable onAction) {
		return (v, actionId, event) -> {
			if ((actionId == EditorInfo.IME_ACTION_SEND) ||
				(event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && preferenceService.isEnterToSend())) {
				onAction.run();
				return true;
			}
			return false;
		};
	}

	private void setupMessageTextListeners() {
		setupSendMessageTextActionListener();

		setupMessageTextClickListener();

		this.messageText.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				ThreemaApplication.activityUserInteract(activity);
				updateSendButton(s);
			}

			@Override
			public void afterTextChanged(Editable s) {
				updateCameraButton();
			}
		});
	}

	/**
	 * Setup listener for text input field.
	 * If default emojis are enabled and the device is in portrait mode,
	 * we will handle activation. Otherwise, leave it to the system
	 */
	private void setupMessageTextClickListener() {
		if (ConfigUtils.isDefaultEmojiStyle() && getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
			this.messageText.setOnClickListener(v -> {
				if (isEmojiPickerShown()) {
					if (ConfigUtils.isLandscape(activity) &&
							!ConfigUtils.isTabletLayout()) {
						emojiPicker.hide();
					} else {
						if (deferringInsetsListener != null) {
							deferringInsetsListener.setDisableAnimation(true);
						}
					}
				}
				activity.openSoftKeyboard(emojiPicker, messageText);
			});
		} else {
			this.messageText.setOnClickListener(null);
		}
	}

	private void updateCameraButton() {
		if (cameraButton == null || attachButton == null || messageText == null || editMessageActionMode != null) {
			return;
		}

		boolean isCameraPermissionGranted = true;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
			ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {

			// shouldShowRequestPermissionRationale returns false if
			// a) the user selected "never ask again"; or
			// b) a permission dialog has never been shown
			// we hide the camera button only in case a)
			if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) && preferenceService.getCameraPermissionRequestShown()) {
				cameraButton.setVisibility(View.GONE);
				isCameraPermissionGranted = false;
			}
		}

		final int attachButtonVisibility = isQuotePopupShown() ?
			View.GONE : View.VISIBLE;

		final int cameraButtonVisibility =
			(messageText.getText() == null ||
				messageText.getText().length() == 0) &&
				!isQuotePopupShown() &&
				isCameraPermissionGranted ?
				View.VISIBLE : View.GONE;

		final boolean attachButtonVisibilityChange = attachButton.getVisibility() != attachButtonVisibility;
		final boolean cameraButtonVisibilityChange = cameraButton.getVisibility() != cameraButtonVisibility;

		if (cameraButtonVisibilityChange) {
			Transition cameraTransition = new Slide(Gravity.RIGHT);
			cameraTransition.setStartDelay(cameraButtonVisibility == View.VISIBLE && attachButtonVisibilityChange ? 100 : 0);
			cameraTransition.setDuration(120);
			cameraTransition.setInterpolator(new LinearInterpolator());
			cameraTransition.addTarget(R.id.camera_button);
			TransitionManager.beginDelayedTransition((ViewGroup) cameraButton.getParent(), cameraTransition);
			cameraButton.setVisibility(cameraButtonVisibility);
		}

		if (attachButtonVisibilityChange) {
			Transition attachTransition = new Slide(Gravity.RIGHT);
			attachTransition.setStartDelay(attachButtonVisibility == View.VISIBLE ? 0 : 100);
			attachTransition.setDuration(120);
			attachTransition.setInterpolator(new LinearInterpolator());
			attachTransition.addTarget(R.id.attach_button);
			TransitionManager.beginDelayedTransition((ViewGroup) attachButton.getParent(), attachTransition);
			attachButton.setVisibility(attachButtonVisibility);
		}

		messageText.postDelayed(() -> fixMessageTextPadding(cameraButtonVisibility, attachButtonVisibility), 50);
	}

	private void fixMessageTextPadding(int cameraButtonVisibility, int attachButtonVisibility) {
		if (isAdded()) {
			int marginRight = ThreemaApplication.getAppContext().getResources().getDimensionPixelSize(R.dimen.emoji_and_photo_button_width);

			if (cameraButtonVisibility != View.VISIBLE) {
				marginRight -= getResources().getDimensionPixelSize(R.dimen.emoji_button_width);
			}

			if (attachButtonVisibility != View.VISIBLE) {
				marginRight -= getResources().getDimensionPixelSize(R.dimen.emoji_button_width);
			}

			marginRight = Math.max(marginRight, getResources().getDimensionPixelSize(R.dimen.no_emoji_button_padding_left));

			messageText.setPadding(messageText.getPaddingLeft(), messageText.getPaddingTop(), marginRight, messageText.getPaddingBottom());
		}
	}

	private void updateSendButton(CharSequence s) {
		if (isQuotePopupShown()) {
			if (TestUtil.isBlankOrNull(s)) {
				sendButton.setEnabled(false);
			} else {
				sendButton.setSend();
				sendButton.setEnabled(true);
			}
		} else {
			if (TestUtil.isBlankOrNull(s)) {
				sendButton.setRecord();
				sendButton.setEnabled(true);
			} else {
				sendButton.setSend();
				sendButton.setEnabled(true);
			}
		}
		if (emojiButton != null) emojiButton.setVisibility(ConfigUtils.isDefaultEmojiStyle() ? View.VISIBLE : View.GONE);
		if (messageText != null) messageText.setVisibility(View.VISIBLE);
	}

	private void updateSendEditMessageButton(String oldMessageText, String newMessageText) {
		if (TestUtil.isBlankOrNull(newMessageText) || newMessageText.equals(oldMessageText)) {
			sendEditMessageButton.setEnabled(false);
			sendEditMessageButton.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.ic_circle_send_disabled));
		} else {
			sendEditMessageButton.setEnabled(true);
			sendEditMessageButton.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.ic_circle_send));
		}
	}

	private void setBackgroundWallpaper() {
		if (isAdded() && this.wallpaperView != null) {
			wallpaperService.setupWallpaperBitmap(this.messageReceiver, this.wallpaperView, ConfigUtils.isLandscape(activity), ConfigUtils.isTheDarkSide(activity));
		}
	}

	private void resetDefaultValues() {
		removeGroupCallObserver();

		this.distributionListId = 0L;
		this.groupId = 0;
		this.identity = null;

		this.groupModel = null;
		this.distributionListModel = null;
		this.contactModel = null;

		this.messageReceiver = null;
		this.listInstancePosition = AbsListView.INVALID_POSITION;
		this.listInstanceReceiverId = null;

		if (ConfigUtils.isTabletLayout()) {
			dismissQuotePopup();
		}

		// remove message detail dialog if still open
		DialogUtil.dismissDialog(getParentFragmentManager(), DIALOG_TAG_MESSAGE_DETAIL, true);
	}

	private void removeGroupCallObserver() {
		if (groupModel != null && groupCallObserver != null && groupCallManager != null) {
			logger.info("Remove group call observer for group {}", groupModel.getId());
			groupCallManager.removeGroupCallObserver(groupModel, groupCallObserver);
			groupCallObserver = null;
		}
	}

	private void getValuesFromBundle(Bundle bundle) {
		if (bundle != null) {
			this.groupId = bundle.getInt(ThreemaApplication.INTENT_DATA_GROUP, 0);
			this.distributionListId = bundle.getLong(ThreemaApplication.INTENT_DATA_DISTRIBUTION_LIST, 0);
			this.identity = bundle.getString(ThreemaApplication.INTENT_DATA_CONTACT);
			this.intentTimestamp = bundle.getLong(ThreemaApplication.INTENT_DATA_TIMESTAMP, 0L);
			this.cameraUri = bundle.getParcelable(CAMERA_URI);
			this.listInstancePosition = bundle.getInt(BUNDLE_LIST_POSITION);
			this.listInstanceReceiverId = bundle.getString(BUNDLE_LIST_RECEIVER_ID);
			this.listInstanceTop = bundle.getInt(BUNDLE_LIST_TOP);
			this.longClickItem = bundle.getInt(BUNDLE_LIST_LONG_CLICK_ITEM);
		}
	}

	public void onNewIntent(Intent intent) {
		logger.debug("onNewIntent");

		if (!requiredInstances()) {
			return;
		}

		if (this.messagePlayerService != null) {
			this.messagePlayerService.stopAll();
			this.messagePlayerService.release();
		}

		MediaController mediaController = getMedia3Controller();
		if (mediaController != null) {
			mediaController.stop();
		}

		resetDefaultValues();

		this.dismissQuotePopup();

		handleIntent(intent);

		// initialize various toolbar items
		if (actionMode != null) {
			actionMode.finish();
		}
		if (searchActionMode != null) {
			searchActionMode.finish();
		}
		if (editMessageActionMode != null) {
			editMessageActionMode.finish();
		}
		this.updateToolbarTitle();
		this.updateMenus();
	}

	private void setupToolbar() {
		View actionBarTitleView = layoutInflater.inflate(R.layout.actionbar_compose_title, null);

		if (actionBarTitleView != null) {
			this.actionBarTitleTextView = actionBarTitleView.findViewById(R.id.title);
			this.actionBarSubtitleImageView = actionBarTitleView.findViewById(R.id.subtitle_image);
			this.actionBarSubtitleTextView = actionBarTitleView.findViewById(R.id.subtitle_text);
			this.actionBarAvatarView = actionBarTitleView.findViewById(R.id.avatar_view);
			final RelativeLayout actionBarTitleContainer = actionBarTitleView.findViewById(R.id.title_container);
			actionBarTitleContainer.setOnClickListener(v -> {
				Intent intent;
				if (isGroupChat) {
					intent = groupService.getGroupDetailIntent(groupModel, activity);
				} else if (isDistributionListChat) {
					intent = new Intent(activity, DistributionListAddActivity.class);
				} else {
					intent = new Intent(activity, ContactDetailActivity.class);
					intent.putExtra(ThreemaApplication.INTENT_DATA_CONTACT_READONLY, true);
				}
				if (intent != null && messageReceiver != null) {
					addExtrasToIntent(intent, messageReceiver);
					activity.startActivity(intent);
				}
			});

			if (contactModel != null) {
				if (contactModel.getIdentityType() == IdentityType.WORK) {
					if (!ConfigUtils.isWorkBuild()) {
						if (!preferenceService.getIsWorkHintTooltipShown()) {
							actionBarTitleTextView.postDelayed(() -> {
								if (getActivity() != null && isAdded()) {
									dismissTooltipPopup(workTooltipPopup, true);

									int[] location = new int[2];
									actionBarAvatarView.getLocationOnScreen(location);
									location[0] += actionBarAvatarView.getWidth() / 2;
									location[1] += actionBarAvatarView.getHeight();

									workTooltipPopup = new TooltipPopup(getActivity(), R.string.preferences__tooltip_work_hint_shown, this, new Intent(getActivity(), WorkExplainActivity.class), R.drawable.ic_badge_work_24dp);
									workTooltipPopup.show(getActivity(), actionBarAvatarView, getString(R.string.tooltip_work_hint), TooltipPopup.ALIGN_BELOW_ANCHOR_ARROW_LEFT, location, 4000);
								}
							}, 1000);
						}
					}
				}
			} else if (groupModel != null) {
				if (ConfigUtils.isGroupCallsEnabled()) {
					showTooltip();
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

	@UiThread
	public void showTooltip() {
		if (activity == null) {
			return;
		}

		if (!preferenceService.getIsGroupCallsTooltipShown()) {
			Toolbar toolbar = activity.getToolbar();
			if (toolbar != null) {
				toolbar.postDelayed(() -> {
					if (activity == null || !activity.hasWindowFocus() || !isGroupChat) {
						return;
					}
					final View itemView = toolbar.findViewById(R.id.menu_threema_call);
					final @ColorInt int textColor = ConfigUtils.getColorFromAttribute(getContext(), R.attr.colorOnPrimary);

					final ViewGroup contentView = activity.findViewById(R.id.main_content);

					try {
						TapTargetViewUtil.showFor(activity,
							TapTarget.forView(itemView, getString(R.string.group_calls_tooltip_title), getString(R.string.group_calls_tooltip_text))
								.outerCircleColorInt(ConfigUtils.getColorFromAttribute(getContext(), R.attr.colorPrimary))      // Specify a color for the outer circle
								.outerCircleAlpha(0.96f)            // Specify the alpha amount for the outer circle
								.targetCircleColor(android.R.color.white)   // Specify a color for the target circle
								.titleTextSize(24)                  // Specify the size (in sp) of the title text
								.titleTextColorInt(textColor)      // Specify the color of the title text
								.descriptionTextSize(18)            // Specify the size (in sp) of the description text
								.descriptionTextColorInt(textColor)  // Specify the color of the description text
								.textColorInt(textColor)            // Specify a color for both the title and description text
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
									itemView.performClick();
								}
							},
							contentView);
						preferenceService.setGroupCallsTooltipShown(true);
					} catch (Exception ignore) {
						// catch null typeface exception on CROSSCALL Action-X3
					}
				}, 2000);
			}
		}
	}

	@UiThread
	private void handleIntent(Intent intent) {
		logger.debug("handleIntent");
		this.isGroupChat = false;
		this.isDistributionListChat = false;
        setCurrentPageReferenceId(null);
		this.reportSpamView.hide();

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
				logger.error(activity.getString(R.string.group_not_found), activity, (Runnable) this::finishActivity);
				return;
			}

			intent.removeExtra(ThreemaApplication.INTENT_DATA_GROUP);
			this.messageReceiver = this.groupService.createReceiver(this.groupModel);
			this.conversationUid = ConversationUtil.getGroupConversationUid(this.groupId);
			if (ConfigUtils.supportsGroupLinks() && groupService.isGroupCreator(this.groupModel)) {
				this.openGroupRequestNoticeView.setGroupIdReference(this.groupModel.getApiGroupId());
				this.openGroupRequestNoticeView.updateGroupRequests();
			}

			this.messageText.enableMentionPopup(
				requireActivity(),
				groupService,
				this.contactService,
				this.userService,
				this.preferenceService,
				groupModel,
				textInputLayout
			);
		} else if (intent.hasExtra(ThreemaApplication.INTENT_DATA_DISTRIBUTION_LIST) || this.distributionListId != 0) {
			this.isDistributionListChat = true;

			try {
				if (this.distributionListId == 0) {
					this.distributionListId = intent.getLongExtra(ThreemaApplication.INTENT_DATA_DISTRIBUTION_LIST, 0);
				}
				this.distributionListModel = distributionListService.getById(this.distributionListId);

				if (this.distributionListModel == null) {
					logger.error("Invalid distribution list", activity, (Runnable) this::finishActivity);
					return;
				}

				intent.removeExtra(ThreemaApplication.INTENT_DATA_DISTRIBUTION_LIST);
				this.messageReceiver = distributionListService.createReceiver(this.distributionListModel);
			} catch (Exception e) {
				logger.error("Exception", e);
				return;
			}
			this.conversationUid = ConversationUtil.getDistributionListConversationUid(this.distributionListId);
		} else {
			if (TestUtil.isEmptyOrNull(this.identity)) {
				this.identity = intent.getStringExtra(ThreemaApplication.INTENT_DATA_CONTACT);
			}

			if (this.identity == null) {
				if (intent.getData() != null) {
					if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
						this.identity = ContactUtil.getIdentityFromViewIntent(activity, intent);
					} else {
						LongToast.makeText(activity, R.string.permission_contacts_required, Toast.LENGTH_LONG).show();
					}
				}
			}

			intent.removeExtra(ThreemaApplication.INTENT_DATA_CONTACT);
			if (this.identity == null || this.identity.length() == 0 || this.identity.equals(this.userService.getIdentity())) {
				logger.error("no identity found");
				finishActivity();
				return;
			}

			this.contactModel = this.contactService.getByIdentity(this.identity);
			if (this.contactModel == null) {
				Toast.makeText(getContext(), getString(R.string.contact_not_found) + ": " + this.identity, Toast.LENGTH_LONG).show();
				Intent homeIntent = new Intent(activity, HomeActivity.class);
				homeIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivity(homeIntent);
				activity.overridePendingTransition(0, 0);
				finishActivity();
				return;
			}
			this.messageReceiver = this.contactService.createReceiver(this.contactModel);
			this.typingIndicatorTextWatcher = new TypingIndicatorTextWatcher(this.contactService, contactModel);
			this.conversationUid = ConversationUtil.getIdentityConversationUid(this.identity);
		}

		initOngoingCallState();

		if (this.messageReceiver == null) {
			logger.error("invalid receiver", activity, (Runnable) this::finishActivity);
			return;
		}

		// hide chat from view and prevent screenshots - may not work on some devices
		if (this.hiddenChatsListService.has(this.messageReceiver.getUniqueIdString())) {
			try {
				activity.getWindow().addFlags(FLAG_SECURE);
			} catch (Exception ignored) { }
		}

		// set wallpaper based on message receiver
		this.setBackgroundWallpaper();

		// report shortcut as used
		if (preferenceService.isDirectShare()) {
			RuntimeUtil.runOnWorkerThread(() -> {
				try {
					ShortcutManagerCompat.reportShortcutUsed(activity, this.messageReceiver.getUniqueIdString());
				} catch (IllegalStateException e) {
					logger.debug("Failed to report shortcut use", e);
				}
			});
		}

		this.initConversationList(intent.hasExtra(EXTRA_API_MESSAGE_ID) && intent.hasExtra(EXTRA_SEARCH_QUERY) ? () -> {
				String apiMessageId = intent.getStringExtra(EXTRA_API_MESSAGE_ID);
				String searchQuery = intent.getStringExtra(EXTRA_SEARCH_QUERY);

				AbstractMessageModel targetMessageModel = messageService.getMessageModelByApiMessageIdAndReceiver(apiMessageId, messageReceiver);

				if (targetMessageModel != null && !TestUtil.isEmptyOrNull(apiMessageId) && !TestUtil.isEmptyOrNull(searchQuery)) {
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
						messageReceiver,
						null,
						null
					);

					if (composeMessageAdapter != null) {
						ComposeMessageAdapter.ConversationListFilter filter = (ComposeMessageAdapter.ConversationListFilter) composeMessageAdapter.getQuoteFilter(quoteContent);
						searchV2Quote(apiMessageId, filter);

						intent.removeExtra(EXTRA_API_MESSAGE_ID);
					}
				} else {
					Toast.makeText(ThreemaApplication.getAppContext(), R.string.message_not_found, Toast.LENGTH_SHORT).show();
				}
		} : () -> {
			if (isPossibleSpamContact(contactModel)) {
				reportSpamView.show(contactModel);
			}
		});

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
		this.openBallotNoticeView.setOnCloseClickedListener(() -> {
			toggleOpenBallotNoticeViewVisibility();
			getActivity().invalidateOptionsMenu();
		});


		// restore draft before setting predefined text
		restoreMessageDraft(false);

		String defaultText = intent.getStringExtra(ThreemaApplication.INTENT_DATA_TEXT);
		if (!TestUtil.isEmptyOrNull(defaultText)) {
			this.messageText.setText(null);
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
	}

	private boolean validateSendingPermission() {
        if (this.messageReceiver == null) {
            return false;
        }
        @NonNull final SendingPermissionValidationResult validationResult = this.messageReceiver.validateSendingPermission();
        if (validationResult.isDenied()) {
            @Nullable Integer errorResId = ((SendingPermissionValidationResult.Denied) validationResult).getErrorResId();
            if (errorResId != null) {
                RuntimeUtil.runOnUiThread(() -> SingleToast.getInstance().showLongText(getString(errorResId)));
            }
            return false;
        }
        return true;
	}

	private void showDeleteMessagesLocallyDialog() {
		if (selectedMessages.isEmpty()) {
			if (actionMode != null) {
				actionMode.finish();
			}
			return;
		}

		List<AbstractMessageModel> deletableMessages = new ArrayList<>(selectedMessages);

		selectedMessages.clear();
		if (actionMode != null) {
			actionMode.finish();
		}

		GenericAlertDialog dialog = GenericAlertDialog.newInstance(
            null,
			ConfigUtils.getSafeQuantityString(requireContext(), R.plurals.delete_messages, deletableMessages.size(), deletableMessages.size()),
			R.string.delete,
			R.string.cancel
        );
		dialog.setCallback((tag, data) -> deleteMessages(deletableMessages));
		dialog.show(getChildFragmentManager(), DIALOG_TAG_CONFIRM_MESSAGE_DELETE);
	}

	private void showDeleteMessagesForAllDialog(@NonNull AbstractMessageModel message) {
		if (actionMode != null) {
			actionMode.finish();
		}

		GenericAlertDialog dialog = GenericAlertDialog.newInstance(
            null,
			ConfigUtils.getSafeQuantityString(requireContext(), R.plurals.delete_messages, 1, 1),
			R.string.delete_for_all,
			R.string.delete_from_this_device,
			R.string.cancel
		);

		dialog.setCallback(new GenericAlertDialog.DialogClickListener() {
			@Override
			public void onYes(String tag, Object data) {
				onConfirmDeleteMessageForAll(message);
			}

			@Override
			public void onNo(String tag, Object data) {
				deleteMessages(List.of(message));
			}
		});

		dialog.show(getChildFragmentManager(), DIALOG_TAG_CONFIRM_MESSAGE_DELETE);
	}

	private void onConfirmDeleteMessageForAll(@NonNull AbstractMessageModel message) {
		if (messageReceiver == null) {
			return;
		}

		if (messageReceiver instanceof ContactMessageReceiver) {
			ContactMessageReceiver receiver = (ContactMessageReceiver) messageReceiver;
			deleteContactMessageForAll(receiver, message);
		} else if (messageReceiver instanceof GroupMessageReceiver && groupModel != null && groupService.isGroupMember(groupModel)) {
			deleteGroupMessageForAll(message, groupModel);
		} else {
			logger.warn("Cannot delete message for receiver of type {}", messageReceiver.getClass().getName());
		}
	}

	private void deleteContactMessageForAll(@NonNull ContactMessageReceiver receiver, @NonNull AbstractMessageModel message) {
		if (ThreemaFeature.canDeleteMessages(receiver.getContact().getFeatureMask())) {
			sendDeleteMessage(message);
		} else {
			logger.warn("Tried to delete a message for a contact that does not support it");
		}
	}

	private void deleteGroupMessageForAll(@NonNull AbstractMessageModel message, @NonNull GroupModel group) {
		GroupFeatureSupport featureSupport = groupService.getFeatureSupport(group, ThreemaFeature.DELETE_MESSAGES);
		if (featureSupport.getAdoptionRate() == GroupFeatureAdoptionRate.ALL) {
			sendDeleteMessage(message);
		} else if (featureSupport.getAdoptionRate()  == GroupFeatureAdoptionRate.PARTIAL) {
			List<ContactModel> membersWithoutFeatureSupport = featureSupport.getContactsWithoutFeatureSupport();
			GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.warning,
				getString(R.string.delete_message_not_supported_for_all_group_members, ContactUtil.joinDisplayNames(getContext(), membersWithoutFeatureSupport)),
				R.string.ok,
				R.string.cancel);
			dialog.setCallback((tag, data) -> sendDeleteMessage(message));
			dialog.show(getChildFragmentManager(), DIALOG_TAG_DELETE_MESSAGES_UNSUPPORTED_WARNING);
		} else {
			logger.warn("Tried to delete a message for a group where none of the members support it");
		}
	}

	private void sendDeleteMessage(@NonNull AbstractMessageModel message) {
		if (messageReceiver == null) {
			return;
		}

		try {
			messageService.sendDeleteMessage(message, messageReceiver);
		} catch (Exception e) {
			logger.error("sendDeleteMessage failed", e);
		}
	}

	/**
	 * Check if the clues indicate that the sender of this chat might be a spammer
	 * @param contactModel Contact model of possible spammer
	 * @return true if the contact could be a spammer, false otherwise
	 */
	private boolean isPossibleSpamContact(@Nullable ContactModel contactModel) {
		if (contactModel == null || composeMessageAdapter == null) {
			return false;
		}

		// No spam reporting in on-prem build
		if (ConfigUtils.isOnPremBuild()) {
			return false;
		}

		// Exclude verified contacts
		if (contactModel.verificationLevel != VerificationLevel.UNVERIFIED) {
			return false;
		}

		// Exclude contacts where the name is set
		if (!TestUtil.isEmptyOrNull(contactModel.getFirstName()) || !TestUtil.isEmptyOrNull(contactModel.getLastName())) {
			return false;
		}

		// Exclude blocked contacts
		if (blackListIdentityService.has(contactModel.getIdentity())) {
			return false;
		}

		// Exclude group contacts
		if (contactModel.getAcquaintanceLevel() == ContactModel.AcquaintanceLevel.GROUP) {
			return false;
		}

		// Exclude official Threema Gateway contacts
		if (THREEMA_CHANNEL_IDENTITY.equals(contactModel.getIdentity()) || THREEMA_SUPPORT_IDENTITY.equals(contactModel.getIdentity())) {
			return false;
		}

		int numMessages = composeMessageAdapter.getCount();
		if (numMessages >= MESSAGE_PAGE_SIZE || numMessages < 2) {
			return false;
		}

		AbstractMessageModel firstMessageModel;
		int positionOfFirstIncomingMessage = 0;
		for (int i = 0; i < numMessages; i++) {
			firstMessageModel = composeMessageAdapter.getItem(i);
			if (firstMessageModel == null) {
				return false;
			}
			if (firstMessageModel.isOutbox()) {
				return false;
			}
			if (contactModel.getIdentity().equals(firstMessageModel.getIdentity())) {
				positionOfFirstIncomingMessage = i;
				break;
			}
		}

		AbstractMessageModel messageModel = composeMessageAdapter.getItem(positionOfFirstIncomingMessage);

		if (messageModel == null) {
			return false;
		}

		Date contactCreated = contactModel.getDateCreated();
		Date firstMessageDate = messageModel.getCreatedAt();

		if (contactCreated == null || firstMessageDate == null) {
			return false;
		}

		if (firstMessageDate.getTime() - contactCreated.getTime() > DateUtils.DAY_IN_MILLIS) {
			return false;
		}

		for (int i = positionOfFirstIncomingMessage; i < numMessages; i++) {
			AbstractMessageModel abstractMessageModel = composeMessageAdapter.getItem(i);
			if (abstractMessageModel == null || abstractMessageModel.isOutbox()) {
				return false;
			}
		}

		return true;
	}

	private synchronized void deleteMessages(@NonNull List<AbstractMessageModel> messages) {
		if (messages.isEmpty()) {
			return;
		}
		for (AbstractMessageModel m : messages) {
			messageService.remove(m);
		}
	}

	@UiThread
	private void contactTypingStateChanged(boolean isTyping) {
		RuntimeUtil.runOnUiThread(() -> {
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
	private boolean addMessageToList(AbstractMessageModel message) {
		if (message == null || this.messageReceiver == null || this.composeMessageAdapter == null) {
			return false;
		}

		if (message.getType() == MessageType.BALLOT && !message.isOutbox()) {
			// If we receive a new ballot message
			openBallotNoticeView.update();
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

		this.composeMessageAdapter.removeFirstUnreadPosition();

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
			this.recentlyAddedCount++;
			new Thread(
					new ReadMessagesRoutine(Collections.singletonList(message),
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

        if (!(message instanceof FirstUnreadMessageModel) && currentPageReferenceId == null) {
            setCurrentPageReferenceId(message.getId());
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

		if (this.composeMessageAdapter == null) {
			return;
		}

		this.convListView.post(() -> {
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
		});
	}

	/**
	 * Loading the next records for the listview
	 */
	@WorkerThread
	private List<AbstractMessageModel> getNextRecords() {
		List<AbstractMessageModel> messageModels = this.messageService.getMessagesForReceiver(this.messageReceiver, this.nextMessageFilter);
		this.valuesLoaded(messageModels);
		return messageModels;
	}

	@WorkerThread
	private List<AbstractMessageModel> getAllRecords() {
		List<AbstractMessageModel> messageModels = this.messageService.getMessagesForReceiver(this.messageReceiver);
		this.valuesLoaded(messageModels);
		return messageModels;
	}

	/**
	 * Append records to the list, adding date separators if necessary
	 * Locks list by calling setNotifyOnChange(false) on the adapter to speed up list ctrl
	 * Don't forget to call notifyDataSetChanged() on the adapter in the UI thread after inserting
	 * @param values MessageModels to insert
	 * @param clear Whether previous list entries should be cleared before appending
	 * @param markasread Whether chat should be marked as read
	 * @return Number of items that have been added to the list INCLUDING date separators and other decoration
	 */
	@UiThread
	private int insertToList(final List<AbstractMessageModel> values, boolean clear, boolean markasread, boolean notify) {
		int insertedSize = 0;

		this.composeMessageAdapter.setNotifyOnChange(false);
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

		if (clear) {
			composeMessageAdapter.setNotifyOnChange(true);
			composeMessageAdapter.notifyDataSetInvalidated();
		} else {
			if (notify) {
				composeMessageAdapter.notifyDataSetChanged();
			} else {
				composeMessageAdapter.setNotifyOnChange(true);
			}
		}

		if (markasread) {
			markAsRead();
		}
		return insertedSize;
	}

	private void valuesLoaded(List<AbstractMessageModel> values) {
		if (values != null && values.size() > 0) {
			AbstractMessageModel topMessageModel = values.get(values.size() - 1);
			// the topmost message may be a unread messages indicator. as it does not have an id, skip it.
			if (topMessageModel instanceof FirstUnreadMessageModel && values.size() > 1) {
				topMessageModel = values.get(values.size() - 2);
			}
            setCurrentPageReferenceId(topMessageModel.getId());
		}
	}

	/**
	 * initialize conversation list and set the unread message count
	 */
	@SuppressLint({"StaticFieldLeak", "WrongThread"})
	@UiThread
	private void initConversationList(@Nullable Runnable runAfter) {
		this.unreadCount = (int) this.messageReceiver.getUnreadMessagesCount();
		if (this.unreadCount > MESSAGE_PAGE_SIZE) {
			new AsyncTask<Void, Void, List<AbstractMessageModel>>() {
				@Override
				protected void onPreExecute() {
					GenericProgressDialog.newInstance(0, R.string.please_wait).show(getParentFragmentManager(), DIALOG_TAG_LOADING_MESSAGES);
				}

				@Override
				protected List<AbstractMessageModel> doInBackground(Void... voids) {
					return messageService.getMessagesForReceiver(messageReceiver, new MessageService.MessageFilter() {
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

						@Override
						public int[] displayTags() {
							return null;
						}
					});
				}

				@Override
				protected void onPostExecute(List<AbstractMessageModel> values) {
					valuesLoaded(values);
					populateList(values);
					DialogUtil.dismissDialog(getParentFragmentManager(), DIALOG_TAG_LOADING_MESSAGES, true);
					if (runAfter != null) {
						runAfter.run();
					}
				}
			}.execute();
		} else {
			populateList(getNextRecords());
			if (runAfter != null) {
				runAfter.run();
			}
		}
	}

	/**
	 * Populate ListView with provided message models
	 */
	@UiThread
	private void populateList(List<AbstractMessageModel> values) {
		if (composeMessageAdapter != null) {
			// re-use existing adapter (for example on tablets)
			composeMessageAdapter.clear();
			composeMessageAdapter.setThumbnailWidth(ConfigUtils.getPreferredThumbnailWidth(getContext(), false));
			composeMessageAdapter.setGroupId(groupId);
			composeMessageAdapter.setMessageReceiver(messageReceiver);
			composeMessageAdapter.setUnreadMessagesCount(unreadCount);
			insertToList(values, true, true, true);
			updateToolbarTitle();
		} else {
			thumbnailCache = new ThumbnailCache<Integer>(null);

			composeMessageAdapter = new ComposeMessageAdapter(
				activity,
				messagePlayerService,
				messageValues,
				userService,
				contactService,
				fileService,
				messageService,
				ballotService,
				preferenceService,
				downloadService,
				licenseService,
				messageReceiver,
				convListView,
				thumbnailCache,
				ConfigUtils.getPreferredThumbnailWidth(getContext(), false),
				ComposeMessageFragment.this,
				unreadCount,
				mediaControllerFuture
			);

			//adding footer before setting the list adapter (android < 4.4)
			if (null != convListView && !isGroupChat && !isDistributionListChat) {
				//create the istyping instance for later use
				isTypingView = layoutInflater.inflate(R.layout.conversation_list_item_typing, null);
				convListView.addFooterView(isTypingView, null, false);
			}

			composeMessageAdapter.setGroupId(groupId);
			composeMessageAdapter.setOnClickListener(new ComposeMessageAdapter.OnClickListener() {
				@Override
				public void click(View view, int position, AbstractMessageModel messageModel) {
					if (actionMode == null && messageModel.isOutbox() && (messageModel.getState() == MessageState.SENDFAILED || messageModel.getState() == MessageState.FS_KEY_MISMATCH) && messageReceiver.isMessageBelongsToMe(messageModel)) {
						final Set<String> finalRecipientIdentities = new HashSet<>();

						Runnable resendMessage = () -> ThreemaApplication.sendMessageExecutorService.execute(() -> {
							try {
								messageService.resendMessage(messageModel, messageReceiver, null, finalRecipientIdentities);
							} catch (Exception e) {
								RuntimeUtil.runOnUiThread(() -> {
									if (isAdded()) {
										Toast.makeText(getContext(), R.string.original_file_no_longer_avilable, Toast.LENGTH_LONG).show();
									}
								});
							}
						});

						if (messageModel instanceof GroupMessageModel) {
							// Check whether sending failed or a fs reject was received
							if (messageModel.getState() == MessageState.SENDFAILED) {
								// If sending failed, we try to resend it to every group member
								finalRecipientIdentities.addAll(Arrays.asList(groupService.getGroupIdentities(groupModel)));
								resendMessage.run();
								return;
							}

							// For group messages we first show a dialog to indicate the affected
							// recipients
							MessageId messageId;
							try {
								messageId = MessageId.fromString(messageModel.getApiMessageId());
							} catch (ThreemaException e) {
								logger.error("Could not get message id from message model", e);
								return;
							}

							RejectedGroupMessageFactory rejectedGroupMessageFactory = databaseServiceNew.getRejectedGroupMessageFactory();
							finalRecipientIdentities.addAll(rejectedGroupMessageFactory.getMessageRejects(messageId, groupModel));

							if (finalRecipientIdentities.isEmpty()) {
								// If there is no rejected recipient, we can just update the message
								// state as the rejected recipient is not longer a group member.
								// Note that this should never happen.
								messageService.updateMessageState(messageModel, MessageState.SENT, null);
								logger.warn("Resend for group members requested, although no member rejected it");
								return;
							}
							ResendGroupMessageDialog.Companion.getInstance(
								finalRecipientIdentities,
								contactService,
								resendMessage::run
							).show(getParentFragmentManager(), DIALOG_TAG_CONFIRM_RESEND);
						} else {
							finalRecipientIdentities.add(messageModel.getIdentity());
							resendMessage.run();
						}
					} else {
						onListItemClick(view, position, messageModel);
					}
				}

				@Override
				public void longClick(View view, int position, AbstractMessageModel messageModel) {
					onListItemLongClick(view, position);
				}

				@Override
				public boolean touch(View view, MotionEvent motionEvent, AbstractMessageModel messageModel) {
					if (actionMode != null) {
						return false;
					}
					if (listViewTouchSwipeListener != null) {
						// performs (long) click manually
						//  to propagate event to click listeners only after checking for swipe
						return listViewTouchSwipeListener.onTouch(view, motionEvent);
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
								requireActivity().finish();

							} else {
								intent = new Intent(getActivity(), ContactDetailActivity.class);
								intent.putExtra(ThreemaApplication.INTENT_DATA_CONTACT_READONLY, true);
								IntentDataUtil.append(contactModel, intent);
							}
							getActivity().startActivity(intent);
						}
					}
				}

				@SuppressLint("DefaultLocale")
				@Override
				public void onSearchResultsUpdate(int searchResultsIndex, int searchResultsSize, final int queryLength) {
					RuntimeUtil.runOnUiThread(() -> {
						if (searchCounter != null) {
							try {
								if (queryLength < MIN_CONSTRAINT_LENGTH && searchResultsSize == 0) {
									searchCounter.setText(getString(R.string.min_n_chars, MIN_CONSTRAINT_LENGTH));
									searchCounter.setVisibility(View.VISIBLE);
									searchPreviousButton.setVisibility(View.INVISIBLE);
									searchNextButton.setVisibility(View.INVISIBLE);
								} else {
									searchCounter.setText(String.format("%d / %d", searchResultsIndex, searchResultsSize));
									searchCounter.setVisibility(View.VISIBLE);
									searchPreviousButton.setVisibility(View.VISIBLE);
									searchNextButton.setVisibility(View.VISIBLE);
								}
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
								searchProgress.setVisibility(inProgress ? View.VISIBLE : View.INVISIBLE);
							} catch (Exception e) {
								//
							}
						}
					});
				}
			});

			insertToList(values, false, !hiddenChatsListService.has(messageReceiver.getUniqueIdString()), false);
			convListView.setAdapter(composeMessageAdapter);
			convListView.setItemsCanFocus(false);
			convListView.setVisibility(View.VISIBLE);
		}

		setIdentityColors();

		removeIsTypingFooter();
	}

	/**
	 * Jump to first unread message keeping in account shift caused by date separators and other decorations
	 * Currently depends on various globals...
	 */
	@UiThread
	private void jumpToFirstUnreadMessage() {
		if (unreadCount > 0) {
			synchronized (this.messageValues) {
				int entryCount = convListView.getCount();
				int position = Math.min(entryCount - unreadCount, this.messageValues.size() - 1);
				while (position >= 0) {
					if (this.messageValues.get(position) instanceof FirstUnreadMessageModel) {
						break;
					}
					position--;

				}
				final int finalUnreadCount = unreadCount;
				if (!isHidden()) {
					unreadCount = 0;
				}

				if (position > 0) {
					final int finalPosition = position;
					logger.debug("jump to initial position " + finalPosition);

					convListView.setSelection(finalPosition);
					convListView.postDelayed(() -> {
						convListView.setSelection(finalPosition);
						if (convListView.canScrollList(View.SCROLL_AXIS_VERTICAL)) {
							if (scrollButtonManager != null) {
								scrollButtonManager.showButton(TYPE_DOWN, finalUnreadCount);
							}
						}
					}, 500);

					return;
				}
			}
		}
		convListView.setSelection(Integer.MAX_VALUE);
	}

	private void setIdentityColors() {
		logger.debug("setIdentityColors");

		if (this.isGroupChat) {
			Map<String, Integer> colorIndices = this.groupService.getGroupMemberIDColorIndices(this.groupModel);
			Map<String, Integer> colors = new HashMap<>();
			boolean darkTheme = ConfigUtils.isTheDarkSide(getContext());
			for (Map.Entry<String, Integer> entry : colorIndices.entrySet()) {
				String memberIdentity = entry.getKey();
				int memberColorIndex = entry.getValue();
				// If the ID color index is -1, the correct index is calculated and stored into the database
				if (memberColorIndex < 0) {
					ContactModel member = contactService.getByIdentity(memberIdentity);
					if (member != null) {
						member.initializeIdColor();
						memberColorIndex = member.getIdColorIndex();
						contactService.save(member);
					}
				}
				colors.put(memberIdentity,
					darkTheme ?
						ColorUtil.getInstance().getIDColorDark(memberColorIndex) :
						ColorUtil.getInstance().getIDColorLight(memberColorIndex));
			}

			this.identityColors = colors;
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
				if (convListView.getCheckedItemCount() < MAX_SELECTED_ITEMS &&
					isItemSelectable(composeMessageAdapter.getItemViewType(position), messageModel)) {
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
					messageReceiver,
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
					messageReceiver,
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

					AbstractMessageModel quotedMessageModel = messageService.getMessageModelByApiMessageIdAndReceiver(messageModel.getQuotedMessageId(), messageReceiver);
					if (quotedMessageModel != null) {
						ComposeMessageAdapter.ConversationListFilter filter = (ComposeMessageAdapter.ConversationListFilter) composeMessageAdapter.getQuoteFilter(quoteContent);
						searchV2Quote(quotedMessageModel.getApiMessageId(), filter);
					} else {
						Toast.makeText(getContext().getApplicationContext(), R.string.quoted_message_deleted, Toast.LENGTH_SHORT).show();
					}
				}
			} else if (messageModel.getType() == MessageType.TEXT && !messageModel.isStatusMessage() && !messageModel.isDeleted()) {
				showTextChatBubble(messageModel);
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
	synchronized private void searchV2Quote(final String apiMessageId, final ComposeMessageAdapter.ConversationListFilter filter) {
		filter.filter("#" + apiMessageId, new Filter.FilterListener() {
			@SuppressLint("StaticFieldLeak")
			@Override
			public void onFilterComplete(int count) {
				if (count == 0) {
					new AsyncTask<Void, Void, Integer>() {
						List<AbstractMessageModel> messageModels;

						@Override
						protected Integer doInBackground(Void... params) {
							messageModels = getNextRecords();
							if (messageModels != null) {
								return messageModels.size();
							}
							return null;
						}

						@Override
						protected void onPostExecute(Integer result) {
							if (getContext() != null) {
								if (result != null && result > 0) {
									insertToList(messageModels, false, false, true);

									if (getFragmentManager() != null) {
										if (getFragmentManager().findFragmentByTag(DIALOG_TAG_SEARCHING) == null) {
											GenericProgressDialog.newInstance(R.string.searching, R.string.please_wait).show(getFragmentManager(), DIALOG_TAG_SEARCHING);
										}
										searchV2Quote(apiMessageId, filter);
									}
								} else {
									SingleToast.getInstance().showShortText(getString(R.string.quote_not_found));
									swipeRefreshLayout.setEnabled(false);
									DialogUtil.dismissDialog(getParentFragmentManager(), DIALOG_TAG_SEARCHING, true);
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

	@UiThread
	private void onListItemLongClick(@NonNull View view, final int position) {
		int viewType = composeMessageAdapter.getItemViewType(position);
		AbstractMessageModel selectedMessage = composeMessageAdapter.getItem(position);

		if (!isItemSelectable(viewType, selectedMessage)) {
			return;
		}

		selectedMessages.clear();
		selectedMessages.add(selectedMessage);

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

		view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);

		// fix linkify on longclick problem
		// see: http://stackoverflow.com/questions/16047215/android-how-to-stop-linkify-on-long-press
		longClickItem = position;

		if (viewType == ComposeMessageAdapter.TYPE_STATUS_DATA_RECV ||
			viewType == ComposeMessageAdapter.TYPE_STATUS_DATA_SEND) {
			// Don't show popup for these view types (but allow them to be selected)
			return;
		}

		showAckjiPopup(view);
	}

	private void showAckjiPopup(View originView) {
		if (ackjiPopup == null) {
			ackjiPopup = new AckjiPopup(getContext(), convListView);
			ackjiPopup.setListener(new AckjiPopup.AckDecPopupListener() {
				@Override
				public void onAckjiClicked(final int clickedItem) {
					if (actionMode == null) {
						return;
					}

					actionMode.finish();

					switch (clickedItem) {
						case ITEM_ACK:
							sendUserAck();
							break;
						case ITEM_DEC:
							sendUserDec();
							break;
						case ITEM_IMAGE_REPLY:
							sendImageReply();
							break;
						case ITEM_INFO:
							showMessageLog(selectedMessages.get(0));
							break;
						case ITEM_EDIT:
							tryEditingSelectedMessage();
							break;
						case ITEM_STAR:
							toggleStar(selectedMessages.get(0));
							break;
					}
				}

				@Override
				public void onOpen() {
				}

				@Override
				public void onClose() {
				}
			});
		}
		ackjiPopup.show(originView.findViewById(R.id.message_block), selectedMessages.get(0));
	}

	/**
	 * Check whether the selected item in the conversation list can be selected
	 * @param viewType View type of the item
	 * @param selectedMessage Message Model of the item
	 * @return true if item is selectable, false otherwise
	 */
	private boolean isItemSelectable(int viewType, @Nullable AbstractMessageModel selectedMessage) {
		if (viewType == ComposeMessageAdapter.TYPE_FIRST_UNREAD  ||
			viewType == ComposeMessageAdapter.TYPE_DATE_SEPARATOR) {
			// Do not allow to select these view types
			return false;
		}

		if (selectedMessage == null) {
			return false;
		}

		if (viewType == ComposeMessageAdapter.TYPE_FILE_VIDEO_SEND && selectedMessage.getState() == MessageState.TRANSCODING) {
			// transcoding messages cannot be selected
			return false;
		}

		return true;
	}

	private boolean isMuted() {
		if (messageReceiver != null && mutedChatsListService != null) {
			String uniqueId = messageReceiver.getUniqueIdString();
			return !TestUtil.isEmptyOrNull(uniqueId) && mutedChatsListService.has(uniqueId);
		}
		return false;
	}

	private boolean isMentionsOnly() {
		if (messageReceiver != null && mentionOnlyChatsListService != null) {
			String uniqueId = messageReceiver.getUniqueIdString();
			return !TestUtil.isEmptyOrNull(uniqueId) && mentionOnlyChatsListService.has(uniqueId);
		}
		return false;
	}

	private boolean isSilent() {
		if (messageReceiver != null && ringtoneService != null) {
			String uniqueId = messageReceiver.getUniqueIdString();
			return !TestUtil.isEmptyOrNull(uniqueId) && ringtoneService.hasCustomRingtone(uniqueId) && ringtoneService.isSilent(uniqueId, isGroupChat);
		}
		return false;
	}

	private void playInAppSound(final int resId, final boolean isVibrate) {
		if (this.isMuted() || this.isSilent()) {
			//do not play
			return;
		}

		RuntimeUtil.runOnUiThread(() -> {
			int ringerMode = audioManager.getRingerMode();

			if (preferenceService.isInAppSounds()) {
				SoundUtil.play(resId);
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
		});
	}

	private void playSentSound() {
		playInAppSound(R.raw.sent_message, false);
	}

	private void playReceivedSound() {
		playInAppSound(R.raw.received_message, true);
	}

	private void onSendButtonClick() {
		if (!this.validateSendingPermission()) {
			return;
		}

		if (!TestUtil.isBlankOrNull(this.messageText.getText())) {
			prepareSendTextMessage();
		} else {
			if (ConfigUtils.requestAudioPermissions(requireActivity(), this, PERMISSION_REQUEST_ATTACH_VOICE_MESSAGE)) {
				attachVoiceMessage();
			}
		}
	}

	private void prepareSendTextMessage() {
		final CharSequence message;

		if (isQuotePopupShown()) {
			QuotePopup.QuoteInfo quoteInfo = quotePopup.getQuoteInfo();
			message = QuoteUtil.quote(
				this.messageText.getText().toString(),
				quoteInfo.getQuoteIdentity(),
				quoteInfo.getQuoteText(),
				quoteInfo.getMessageModel()
			);

			messageText.postDelayed(this::dismissQuotePopup, 500);
		} else {
			message = this.messageText.getText();
		}

		if (!TestUtil.isBlankOrNull(message)) {
			sendTextMessage(message);
		} else {
			logger.warn("Message text is empty");
		}
	}

	private void sendTextMessage(CharSequence message) {
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

		new Thread(() -> TextMessageSendAction.getInstance()
			.sendTextMessage(new MessageReceiver[]{messageReceiver}, message.toString(), new SendAction.ActionHandler() {
				@Override
				public void onError(final String errorMessage) {
					RuntimeUtil.runOnUiThread(() -> {
						LongToast.makeText(getActivity(), errorMessage, Toast.LENGTH_LONG).show();
						if (!TestUtil.isBlankOrNull(message)) {
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
					RuntimeUtil.runOnUiThread(() -> {
						if (ConfigUtils.isTabletLayout()) {
							// remove draft right now to make sure conversations pane is updated
							ThreemaApplication.putMessageDraft(messageReceiver.getUniqueIdString(), "", null);
						}
					});
				}
			})).start();
	}

	private void attachVoiceMessage() {
		dismissQuotePopup();

		// stop all message players
		if (this.messagePlayerService != null) {
			this.messagePlayerService.pauseAll(SOURCE_AUDIORECORDER);
		}

		Intent intent = new Intent(activity, VoiceRecorderActivity.class);
		IntentDataUtil.addMessageReceiverToIntent(intent, messageReceiver);
		activity.startActivityForResult(intent, ACTIVITY_ID_VOICE_RECORDER);
		activity.overridePendingTransition(R.anim.fast_fade_in, R.anim.fast_fade_out);
	}

	private void tryEditingSelectedMessage() {
		if (selectedMessages.size() != 1) {
			logger.error("Cannot edit more than one selected message.");
			return;
		}

		AbstractMessageModel message = selectedMessages.get(0);

		if (messageReceiver instanceof ContactMessageReceiver) {
			ContactMessageReceiver receiver = (ContactMessageReceiver) messageReceiver;
			startEditingContactMessage(receiver, message);
		} else if (messageReceiver instanceof GroupMessageReceiver && groupModel != null) {
			startEditingGroupMessage(groupModel, message);
		} else if (messageReceiver != null) {
			logger.error("Cannot edit message for receiver of type {}", messageReceiver.getClass().getName());
		}
	}

	private void startEditingContactMessage(@NonNull ContactMessageReceiver receiver, @NonNull AbstractMessageModel message) {
		if (ThreemaFeature.canEditMessages(receiver.getContact().getFeatureMask())) {
			startMessageEditor(message);
		} else {
			LongToast.makeText(
				getContext(),
				getResources().getString(R.string.edit_message_not_supported_for_contact),
				Toast.LENGTH_LONG).show();
		}
	}

	private void startEditingGroupMessage(@NonNull GroupModel groupModel, @NonNull AbstractMessageModel message) {
		if (groupService.isNotesGroup(groupModel)) {
			startMessageEditor(message);
			return;
		}

		GroupFeatureSupport featureSupport = groupService.getFeatureSupport(groupModel, ThreemaFeature.EDIT_MESSAGES);
		if (featureSupport.getAdoptionRate() == GroupFeatureAdoptionRate.ALL) {
			startMessageEditor(message);
		} else if (featureSupport.getAdoptionRate() == GroupFeatureAdoptionRate.PARTIAL) {
			List<ContactModel> membersWithoutFeatureSupport = featureSupport.getContactsWithoutFeatureSupport();
			GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.warning,
				getString(R.string.edit_message_not_supported_for_all_group_members, ContactUtil.joinDisplayNames(getContext(), membersWithoutFeatureSupport)),
				R.string.ok,
				R.string.cancel);
			dialog.setCallback((tag, data) -> startMessageEditor(message));
			dialog.show(getChildFragmentManager(), DIALOG_TAG_EDIT_MESSAGES_UNSUPPORTED_WARNING);
		} else {
			LongToast.makeText(
				getContext(),
				getResources().getString(R.string.edit_message_not_supported_for_any_group_members),
				Toast.LENGTH_LONG).show();
		}
	}

	private void startMessageEditor(@NonNull AbstractMessageModel message) {
		if (quotePopup != null && message == quotePopup.getQuoteInfo().getMessageModel()) {
			quotePopup.dismiss();
		}
		editMessageActionMode = activity.startSupportActionMode(new EditMessageActionMode(message));
	}

	private void onSendEditMessage(@NonNull AbstractMessageModel messageModel, @NonNull String newBody) {
		if (editMessageActionMode != null) {
			editMessageActionMode.finish();
		}

		try {
			// when message failed to send edit it locally only
			if (messageModel.getState() == MessageState.SENDFAILED) {
				messageService.saveEditedMessageText(messageModel, newBody, null);
			} else {
				Date editedAt = new Date();
				messageService.sendEditedMessageText(messageModel, newBody, editedAt, Objects.requireNonNull(messageReceiver));
			}
		} catch (Exception e) {
			logger.error("editMessage failed", e);
		}
	}

	private void copySelectedMessagesToClipboard() {
		if (selectedMessages.isEmpty()) {
			logger.error("no selected messages");
			return;
		}

		StringBuilder body = new StringBuilder();
		for (AbstractMessageModel message : selectedMessages) {
			if (body.length() > 0) {
				body.append("\n");
			}

			body.append(message.getType() == MessageType.TEXT ?
				QuoteUtil.getMessageBody(message, false) :
				message.getCaption());
		}

		try {
			ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
			if (clipboard != null) {
				ClipData clipData = ClipData.newPlainText(null, body.toString());
				if (clipData != null) {
					clipboard.setPrimaryClip(clipData);
					Toast.makeText(
						getContext(),
						getResources().getQuantityString(R.plurals.message_copied, selectedMessages.size()),
						Toast.LENGTH_SHORT).show();
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
							shareMediaMessages(uris);
						}

						@Override
						public void error(String message) {
							RuntimeUtil.runOnUiThread(() -> LongToast.makeText(activity, message, Toast.LENGTH_LONG).show());
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
							shareMediaMessages(Collections.singletonList(fileService.getShareFileUri(decryptedFile, filename)));
						} else {
							messageService.shareTextMessage(activity, messageModel);
						}
					}

					@Override
					public void error(final String message) {
						RuntimeUtil.runOnUiThread(() -> LongToast.makeText(activity, message, Toast.LENGTH_LONG).show());
					}
				});
			}
		}
	}

	private void shareMediaMessages(List<Uri> uris) {
		if (selectedMessages.size() == 1) {
			ExpandableTextEntryDialog alertDialog = ExpandableTextEntryDialog.newInstance(
				getString(R.string.share_media),
				R.string.add_caption_hint, selectedMessages.get(0).getCaption(),
				R.string.label_continue, R.string.cancel, true);
			alertDialog.setData(uris);
			alertDialog.setTargetFragment(this, 0);
			alertDialog.show(getParentFragmentManager(), null);
		} else {
			messageService.shareMediaMessages(activity,
				new ArrayList<>(selectedMessages),
				new ArrayList<>(uris), null);
		}
	}

	@Override
	public void onYes(String tag, Object data, String text) {
		List<Uri> uris = (List<Uri>) data;
		messageService.shareMediaMessages(activity,
			new ArrayList<>(selectedMessages),
			new ArrayList<>(uris), text);
	}

	@UiThread
	private void showQuotePopup(@Nullable AbstractMessageModel messageModel) {
		final AbstractMessageModel quotedMessageModel;

		if (messageModel == null) {
			quotedMessageModel = selectedMessages.get(0);
		} else {
			quotedMessageModel = messageModel;
		}

		if (quotedMessageModel == null) {
			return;
		}

		sendButton.setEnabled(messageText != null && !TestUtil.isBlankOrNull(messageText.getText()));

		dismissMentionPopup();
		dismissQuotePopup();

		int color = ConfigUtils.getAccentColor(activity);
		String identity = quotedMessageModel.isOutbox() ? userService.getIdentity() : quotedMessageModel.getIdentity();

		if (!quotedMessageModel.isOutbox()) {
			if (isGroupChat) {
				if (identityColors != null && identityColors.containsKey(identity)) {
					color = identityColors.get(identity);
				}
			} else {
				if (contactModel != null) {
					color = contactModel.getThemedColor(requireContext());
				}
			}
		}

		quotePopup = new QuotePopup(
			activity,
			contactService,
			userService,
			fileService,
			thumbnailCache
		);

		@ColorInt final int sidebarColor = color;

		if (activity.isSoftKeyboardOpen() || isEmojiPickerShown()) {
			messageText.requestFocus();
			quotePopup.show(activity, messageText, textInputLayout, quotedMessageModel, identity, sidebarColor, quotePopupListener);
		} else {
			EditTextUtil.focusWindowAndShowSoftKeyboard(messageText);
			messageText.postDelayed(() -> quotePopup.show(activity, messageText, textInputLayout, quotedMessageModel, identity, sidebarColor, quotePopupListener), 550);
		}
	}

	private void dismissQuotePopup() {
		dismissQuotePopup(null);
	}

	private void dismissQuotePopup(@Nullable Runnable runAfterQuotePopupClosed) {
		if (isQuotePopupShown()) {
			try {
				quotePopup.dismiss();
				quotePopup = null;
			} catch (Exception e) {
				logger.error("Error dismissing quote popup", e);
			}
			updateSendButton(messageText.getText());
			if (runAfterQuotePopupClosed != null) {
				runAfterQuotePopupClosed.run();
			}
		}
	}

	private boolean isQuotePopupShown() {
		return quotePopup != null && quotePopup.isShowing();
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

	private void showMessageLog(@Nullable AbstractMessageModel messageModel) {
		if (messageModel == null) {
			return;
		}
		MessageDetailDialog.newInstance(
            R.string.message_log_title,
            messageModel.getId(),
            messageModel.getClass().toString()
        ).show(getParentFragmentManager(), DIALOG_TAG_MESSAGE_DETAIL);
	}

	/**
	 * Toggles the "starred" flag for the provided message and saves it to the database
	 * @param messageModel AbstractMessageModel of the message
	 */
	private void toggleStar(@Nullable AbstractMessageModel messageModel) {
		if (messageModel != null && messageReceiver != null) {
			messageModel.setDisplayTags(messageModel.getDisplayTags() ^ DISPLAY_TAG_STARRED);
			messageModel.setSaved(true);
			messageReceiver.saveLocalModel(messageModel);
		}
	}

	private void updateToolbarTitle() {
		if (!TestUtil.required(
				this.actionBar,
				this.actionBarSubtitleImageView,
				this.actionBarSubtitleTextView,
				this.actionBarTitleTextView,
				this.emojiMarkupUtil,
				this.messageReceiver,
				isAdded(),
				getActivity() != null) || !requiredInstances()) {
			return;
		}

		this.actionBarSubtitleTextView.setVisibility(View.GONE);
		this.actionBarSubtitleImageView.setVisibility(View.GONE);
		this.actionBarAvatarView.setVisibility(View.VISIBLE);

		this.actionBarTitleTextView.setText(this.messageReceiver.getDisplayName());
		this.actionBarTitleTextView.setPaintFlags(this.actionBarTitleTextView.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);

		if (this.isGroupChat) {
			if (!groupService.isGroupMember(this.groupModel)) {
				this.actionBarTitleTextView.setPaintFlags(this.actionBarTitleTextView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
			}
			actionBarSubtitleTextView.setText(groupService.getMembersString(groupModel));
			actionBarSubtitleTextView.setVisibility(View.VISIBLE);
			if (actionBarAvatarView.getAvatarView().isAttachedToWindow()) {
				groupService.loadAvatarIntoImage(
					groupModel,
					actionBarAvatarView.getAvatarView(),
					AvatarOptions.PRESET_DEFAULT_FALLBACK,
					Glide.with(requireActivity())
				);
			}
			actionBarAvatarView.setBadgeVisible(false);
			setAvatarContentDescription(R.string.prefs_group_notifications);
		} else if (this.isDistributionListChat) {
			actionBarSubtitleTextView.setText(this.distributionListService.getMembersString(this.distributionListModel));
			actionBarSubtitleTextView.setVisibility(View.VISIBLE);
			if (this.distributionListModel.isHidden()) {
				actionBarAvatarView.setVisibility(View.GONE);
				actionBarTitleTextView.setText(getString(R.string.threema_message_to, ""));
			} else {
				if (actionBarAvatarView.getAvatarView().isAttachedToWindow()) {
					distributionListService.loadAvatarIntoImage(
						distributionListModel,
						actionBarAvatarView.getAvatarView(),
						AvatarOptions.PRESET_DEFAULT_AVATAR_NO_CACHE,
						Glide.with(requireActivity())
					);
				}
			}
			actionBarAvatarView.setBadgeVisible(false);
			setAvatarContentDescription(R.string.distribution_list);
		} else {
			if (contactModel != null) {
				this.actionBarSubtitleImageView.setContactModel(contactModel);
				this.actionBarSubtitleImageView.setVisibility(View.VISIBLE);
				if (actionBarAvatarView.getAvatarView().isAttachedToWindow()) {
					contactService.loadAvatarIntoImage(
						contactModel,
						this.actionBarAvatarView.getAvatarView(),
						AvatarOptions.PRESET_RESPECT_SETTINGS,
						Glide.with(requireActivity())
					);
				}
				this.actionBarAvatarView.setBadgeVisible(contactService.showBadge(contactModel));
			}
			setAvatarContentDescription(R.string.prefs_header_chat);
		}
		this.actionBarTitleTextView.invalidate();
		this.actionBarSubtitleTextView.invalidate();
		this.actionBarSubtitleImageView.invalidate();
	}

	private void setAvatarContentDescription(@StringRes int stringRes) {
		try {
			actionBarAvatarView.setContentDescription(getString(stringRes));
		} catch (IllegalStateException e) {
			logger.error("Can't set content description", e);
		}
	}

	@Override
	@SuppressLint("StaticFieldLeak")
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
		this.mutedMenuItem = menu.findItem(R.id.menu_muted);
		this.blockMenuItem = menu.findItem(R.id.menu_block_contact);
		this.showOpenBallotWindowMenuItem = menu.findItem(R.id.menu_ballot_window_show);
		this.showBallotsMenuItem = menu.findItem(R.id.menu_ballot_show_all);
		this.showOpenGroupRequestsMenuItem = menu.findItem(R.id.menu_group_requests_show);
		this.showAllGroupRequestsMenuItem = menu.findItem(R.id.menu_group_request_show_all);
		this.showEmptyChatMenuItem = menu.findItem(R.id.menu_empty_chat);

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
				this.mutedMenuItem,
				this.blockMenuItem,
				this.showOpenBallotWindowMenuItem,
				this.showAllGroupRequestsMenuItem,
				this.showOpenGroupRequestsMenuItem,
				this.showEmptyChatMenuItem,
				isAdded()
		)) {
			return;
		}

		this.deleteDistributionListItem.setVisible(this.isDistributionListChat);
		this.mutedMenuItem.setVisible(!this.isDistributionListChat && !(isGroupChat && groupService.isNotesGroup(groupModel)));
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
					ConfigUtils.tintMenuItem(context, showOpenBallotWindowMenuItem, R.attr.colorOnSurface);
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

		// show/hide open group request chips if there are any
		if (ConfigUtils.supportsGroupLinks() && groupModel != null && !databaseServiceNew.getIncomingGroupJoinRequestModelFactory()
			.getSingleMostRecentOpenRequestsPerUserForGroup(groupModel.getApiGroupId()).isEmpty()) {
			showOpenGroupRequestsMenuItem.setVisible(true);
			showAllGroupRequestsMenuItem.setVisible(true);
			if (preferenceService.getGroupRequestsOverviewHidden()) {
				showOpenGroupRequestsMenuItem.setIcon(R.drawable.ic_outline_visibility);
				showOpenGroupRequestsMenuItem.setTitle(R.string.open_group_requests_show);
			} else {
				showOpenGroupRequestsMenuItem.setIcon(R.drawable.ic_outline_visibility_off);
				showOpenGroupRequestsMenuItem.setTitle(R.string.open_group_requests_hide);
			}
		}
		else {
			showOpenGroupRequestsMenuItem.setVisible(false);
		}

		// link to incoming group requests overview if there are any (includes already answered ones)
		showAllGroupRequestsMenuItem.setVisible(ConfigUtils.supportsGroupLinks() &&
			groupModel != null &&
			!databaseServiceNew.getIncomingGroupJoinRequestModelFactory()
			.getAllRequestsForGroup(groupModel.getApiGroupId()).isEmpty()
		);

		// Show "empty chat" only if chat is not empty
		this.showEmptyChatMenuItem.setVisible(composeMessageAdapter != null && !composeMessageAdapter.isEmpty());

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
	private void updateVoipCallMenuItem(@Nullable final Boolean newState) {
		RuntimeUtil.runOnUiThread(() -> {
			if (isGroupChat) {
				updateGroupCallMenuItem();
			} else if (callItem != null) {
				if (ContactUtil.canReceiveVoipMessages(contactModel, blackListIdentityService) && ConfigUtils.isCallsEnabled()) {
					logger.debug("updateVoipMenu newState " + newState);
					callItem.setIcon(R.drawable.ic_phone_locked_outline);
					callItem.setTitle(R.string.threema_call);
					callItem.setVisible(newState != null ? newState : voipStateService.getCallState().isIdle());
				} else {
					callItem.setVisible(false);
				}
			}
		});
	}

	@UiThread
	private void updateGroupCallMenuItem() {
		if (groupModel == null) {
			logger.warn("Group model is null");
			return;
		}
		if (groupService == null) {
			logger.warn("Group service is null");
			return;
		}

		if (isGroupChat && callItem != null) {
			if (GroupCallUtilKt.qualifiesForGroupCalls(groupService, groupModel)) {
				GroupCallDescription call = groupCallManager.getCurrentChosenCall(groupModel);
				callItem.setIcon(R.drawable.ic_phone_locked_outline);
				callItem.setTitle(R.string.group_call);
				callItem.setVisible(call == null);
			} else {
				callItem.setVisible(false);
			}
		}
	}

	private Intent addExtrasToIntent(Intent intent, @NonNull MessageReceiver receiver) {
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
		activity.startActivityForResult(previewIntent, ThreemaActivity.ACTIVITY_ID_SEND_MEDIA);
	}

	private void showPermissionRationale(int stringResource) {
		ConfigUtils.showPermissionRationale(getContext(), coordinatorLayout, stringResource);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		final int id = item.getItemId();
		if (id == android.R.id.home) {
			if (activity != null && activity.getIntent() != null && activity.getIntent().hasExtra(EXTRA_OVERRIDE_BACK_TO_HOME_BEHAVIOR)) {
				activity.getOnBackPressedDispatcher().onBackPressed();
				return true;
			} else {
		 		NavigationUtil.navigateUpToHome(activity);
			}
		} else if (id == R.id.menu_search_messages) {
			searchActionMode = activity.startSupportActionMode(new SearchActionMode());
		} else if (id == R.id.menu_gallery) {
			Intent mediaGalleryIntent = new Intent(activity, MediaGalleryActivity.class);
			if (this.messageReceiver != null) {
				activity.startActivity(addExtrasToIntent(mediaGalleryIntent, this.messageReceiver));
			}
		} else if (id == R.id.menu_threema_call) {
			initiateCall();
		} else if (id == R.id.menu_wallpaper) {
			wallpaperService.selectWallpaper(this, this.wallpaperLauncher, this.messageReceiver, () -> RuntimeUtil.runOnUiThread(this::setBackgroundWallpaper));
		} else if (id == R.id.menu_muted) {
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
				if (messageReceiver != null) {
					intent.putExtra(ThreemaApplication.INTENT_DATA_TEXT, this.messageReceiver.getDisplayName());
				}
				if (ToolbarUtil.getMenuItemCenterPosition(activity.getToolbar(), R.id.menu_muted, location)) {
					intent.putExtra((ThreemaApplication.INTENT_DATA_ANIM_CENTER), location);
				}
				activity.startActivity(intent);
			}
		} else if (id == R.id.menu_block_contact) {
			if (this.blackListIdentityService.has(contactModel.getIdentity())) {
				this.blackListIdentityService.toggle(activity, contactModel);
				updateBlockMenu();
			} else {
				GenericAlertDialog.newInstance(R.string.block_contact, R.string.really_block_contact, R.string.yes, R.string.no).setTargetFragment(this).show(getFragmentManager(), DIALOG_TAG_CONFIRM_BLOCK);
			}
		} else if (id == R.id.menu_delete_distribution_list) {
			GenericAlertDialog.newInstance(R.string.really_delete_distribution_list,
					R.string.really_delete_distribution_list_message,
					R.string.ok,
					R.string.cancel)
				.setTargetFragment(this)
				.setData(distributionListModel)
				.show(getFragmentManager(), CONFIRM_TAG_DELETE_DISTRIBUTION_LIST);
		} else if (id == R.id.menu_shortcut) {
			createShortcut();
		} else if (id == R.id.menu_empty_chat) {
			GenericAlertDialog.newInstance(R.string.empty_chat_title,
					R.string.empty_chat_confirm,
					R.string.ok,
					R.string.cancel)
				.setTargetFragment(this)
				.show(getFragmentManager(), DIALOG_TAG_EMPTY_CHAT);
		} else if (id == R.id.menu_ballot_window_show) {
			toggleOpenBallotNoticeViewVisibility();
		} else if (id == R.id.menu_ballot_show_all) {
			Intent intent = new Intent(getContext(), BallotOverviewActivity.class);
			IntentDataUtil.addMessageReceiverToIntent(intent, messageReceiver);
			startActivity(intent);
		} else if (id == R.id.menu_group_request_show_all) {
			Intent groupRequestsOverviewIntent = new Intent(getContext(), IncomingGroupRequestActivity.class);
			groupRequestsOverviewIntent.putExtra(ThreemaApplication.INTENT_DATA_GROUP_API, groupModel.getApiGroupId());
			startActivity(groupRequestsOverviewIntent);
		} else if (id == R.id.menu_group_requests_show) {
			if (openGroupRequestNoticeView.isShown()) {
				preferenceService.setGroupRequestsOverviewHidden(true);
				openGroupRequestNoticeView.hide(true);
			} else {
				preferenceService.setGroupRequestsOverviewHidden(false);
				openGroupRequestNoticeView.show(true);
			}
		}
		return false;
	}

	private void toggleOpenBallotNoticeViewVisibility() {
		if (openBallotNoticeView.isShown()) {
			preferenceService.setBallotOverviewHidden(true);
			openBallotNoticeView.hide(true);
		} else {
			preferenceService.setBallotOverviewHidden(false);
			openBallotNoticeView.show(true);
		}
	}

	private void initiateCall() {
		if (isGroupChat) {
			GroupCallUtilKt.initiateCall(activity, groupModel);
		} else {
			VoipUtil.initiateCall(activity, contactModel, false, null);
		}
	}

	private void emptyChat() {
		if (messageReceiver != null) {
			logger.info("Empty chat with receiver {} (type={}).", messageReceiver.getUniqueIdString(), messageReceiver.getType());
		} else {
			logger.warn("Cannot empty chat, messageReceiver is null.");
		}
		new EmptyOrDeleteConversationsAsyncTask(
			EmptyOrDeleteConversationsAsyncTask.Mode.EMPTY,
			new MessageReceiver[]{ messageReceiver },
			conversationService,
			groupService,
			distributionListService,
			getParentFragmentManager(),
			null,
		() -> {
			if (isAdded()) {
				synchronized (messageValues) {
					messageValues.clear();
					composeMessageAdapter.notifyDataSetChanged();
				}

				// empty draft
				ThreemaApplication.putMessageDraft(messageReceiver.getUniqueIdString(), "", null);
				messageText.setText(null);

                setCurrentPageReferenceId(null);
				onRefresh();

				ListenerManager.conversationListeners.handle(listener -> {
					if (!isGroupChat) {
						conversationService.reset();
					}
					listener.onModifiedAll();
				});
			}
		}).execute();
	}

	private void createShortcut() {
		if (!this.isGroupChat &&
			!this.isDistributionListChat &&
			ContactUtil.canReceiveVoipMessages(contactModel, blackListIdentityService) &&
			ConfigUtils.isCallsEnabled()) {
							ArrayList<SelectorDialogItem> items = new ArrayList<>();
				items.add(new SelectorDialogItem(getString(R.string.prefs_header_chat), R.drawable.ic_outline_chat_bubble_outline));
				items.add(new SelectorDialogItem(getString(R.string.threema_call), R.drawable.ic_call_outline));
				SelectorDialog selectorDialog = SelectorDialog.newInstance(getString(R.string.shortcut_choice_title), items, getString(R.string.cancel));
				selectorDialog.setTargetFragment(this, 0);
				selectorDialog.show(getFragmentManager(), DIALOG_TAG_CHOOSE_SHORTCUT_TYPE);
		} else {
			RuntimeUtil.runOnWorkerThread(() -> ShortcutUtil.createPinnedShortcut(messageReceiver, TYPE_CHAT));
		}
	}

	private void sendMessage() {
		if (typingIndicatorTextWatcher != null) {
			typingIndicatorTextWatcher.killEvents();
		}

		this.onSendButtonClick();
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode,
									final Intent intent) {
		if (requestCode == ACTIVITY_ID_VOICE_RECORDER) {
			if (this.messagePlayerService != null) {
				this.messagePlayerService.resumeAll(getActivity(), messageReceiver, SOURCE_AUDIORECORDER);
			}
		}
		if (requestCode == ThreemaActivity.ACTIVITY_ID_ATTACH_MEDIA) {
			restoreMessageDraft(true);
			if (resultCode == Activity.RESULT_OK) {
				this.lastMediaFilter = IntentDataUtil.getLastMediaFilterFromIntent(intent);
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
			final int shortcutType = which + 1;
			RuntimeUtil.runOnWorkerThread(() -> ShortcutUtil.createPinnedShortcut(messageReceiver, shortcutType));
		}
	}

	@Override
	public void onCancel(String tag) {}

	@Override
	public void onNo(String tag) {}

	public class ComposeMessageAction implements ActionMode.Callback {
		private final int position;
		private MenuItem quoteItem, forwardItem, saveItem, copyItem, qrItem, shareItem, showText;

		ComposeMessageAction(int position) {
			this.position = position;
			longClickItem = position;
		}

		private void updateActionMenu(Menu menu) {
			boolean isQuotable = selectedMessages.size() == 1;
			boolean showAsQRCode = selectedMessages.size() == 1;
			boolean showAsText = selectedMessages.size() == 1;
			boolean isForwardable = selectedMessages.size() <= MAX_FORWARDABLE_ITEMS;
			boolean isSaveable = !AppRestrictionUtil.isShareMediaDisabled(getContext());
			boolean isCopyable = true;
			boolean isShareable = !AppRestrictionUtil.isShareMediaDisabled(getContext());
			boolean hasDefaultRendering = false;

			if (selectedMessages.stream().anyMatch(AbstractMessageModel::isDeleted)) {
				onlyShowItems(menu, R.id.menu_message_discard);
				return;
			}

			for (AbstractMessageModel message: selectedMessages) {
				if (message == null) continue;
				isQuotable = isQuotable && isQuotable(message);
				showAsQRCode = showAsQRCode && canShowAsQRCode(message);
				showAsText = showAsText && canShowAsText(message);
				isForwardable = isForwardable && isForwardable(message);
				isSaveable = isSaveable && isSaveable(message);
				isCopyable = isCopyable && isCopyable(message);
				isShareable = isShareable && isShareable(message);
				hasDefaultRendering = hasDefaultRendering || isDefaultRendering(message);
			}

			// Sharing text message is only possible when there is exactly one selected message
			isShareable = isShareable && (selectedMessages.size() == 1 || !containsTextMessage(selectedMessages));

			quoteItem.setVisible(isQuotable);
			qrItem.setVisible(showAsQRCode);
			showText.setVisible(showAsText);
			forwardItem.setVisible(isForwardable);
			saveItem.setVisible(isSaveable);
			copyItem.setVisible(isCopyable);
			shareItem.setVisible(isShareable);

			if (hasDefaultRendering) {
				saveItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
				forwardItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
			} else {
				saveItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
				forwardItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
			}
		}

		private void onlyShowItems(Menu menu, int... ids) {
			for (int i = 0; i < menu.size(); i++) {
				MenuItem item = menu.getItem(i);
				boolean show = Arrays.stream(ids).anyMatch(id -> id == item.getItemId());
				item.setVisible(show);
			}
		}

		private boolean isQuotable(@NonNull AbstractMessageModel message) {
            if (messageReceiver == null) {
                return false;
            }
			boolean isValidReceiver = messageReceiver.validateSendingPermission().isValid();
			return isValidReceiver && QuoteUtil.isQuoteable(message);
		}

		private boolean canShowAsQRCode(@NonNull AbstractMessageModel message) {
			return message.getType() == MessageType.TEXT    // if the message is a text message
				&& !message.isStatusMessage();              // and it is not a status message
		}

		private boolean canShowAsText(@NonNull AbstractMessageModel message) {
			return (message.getType() == MessageType.TEXT || message.getType() == MessageType.FILE ) // if the message is a text or file  message
				&& !message.isStatusMessage();                                                       // and it is not a status message
		}

		private boolean isForwardable(@NonNull AbstractMessageModel message) {
			return message.isAvailable() 	                            // if the media is downloaded
				&& !message.isStatusMessage()                           // and the message is not status message (unread or status)
				&& message.getType() != MessageType.BALLOT              // and not a ballot
				&& message.getType() != MessageType.VOIP_STATUS 		// and not a voip status
				&& message.getType() != MessageType.GROUP_CALL_STATUS; 	// and not a group call status
		}

		private boolean isSaveable(@NonNull AbstractMessageModel message) {
			return message.isAvailable()                            // if the message is available
				&& (message.getType() == MessageType.IMAGE          // and it is an image
				|| message.getType() == MessageType.VOICEMESSAGE    // or voice message
				|| message.getType() == MessageType.VIDEO           // or video
				|| message.getType() == MessageType.FILE);          // or file
		}

		private boolean isShareable(@NonNull AbstractMessageModel message) {
			return message.isAvailable()                    // if the message is available
				&& (message.getType() == MessageType.IMAGE  // and message is an image
				|| message.getType() == MessageType.VIDEO   // or video
				|| message.getType() == MessageType.FILE    // or voice message
				|| message.getType() == MessageType.TEXT);  // or text message
		}

		private boolean isCopyable(@NonNull AbstractMessageModel message) {
			boolean isText = message.getType() == MessageType.TEXT && !message.isStatusMessage();
			boolean isFileWithCaption = message.getType() == MessageType.FILE
				&& !TextUtils.isEmpty(message.getCaption());
			return isText || isFileWithCaption; // is text (not status) or a file with non-empty caption
		}

		private boolean isDefaultRendering(@NonNull AbstractMessageModel message) {
			return message.getType() == MessageType.FILE                                    // if it is a file
				&& message.getFileData().getRenderingType() == FileData.RENDERING_DEFAULT;  // and default rendering is set
		}

		private boolean containsTextMessage(@NonNull List<AbstractMessageModel> messages) {
			for (AbstractMessageModel message : messages) {
				if (message.getType() == MessageType.TEXT) {
					return true;
				}
			}
			return false;
		}

		private boolean isDeletableRemotely(AbstractMessageModel message) {
			// check receiver support
			if (messageReceiver instanceof GroupMessageReceiver) {
				GroupModel group = ((GroupMessageReceiver) messageReceiver).getGroup();
				if (!groupService.isGroupMember(group)) {
					return false;
				} else if (groupService.isNotesGroup(group)) {
					// delete for all is pointless in notes group
					return false;
				} else {
					GroupFeatureSupport featureSupport = groupService.getFeatureSupport(group, ThreemaFeature.DELETE_MESSAGES);
					if (featureSupport.getAdoptionRate() == GroupFeatureAdoptionRate.NONE) {
						// no feature support in group
						return false;
					}
				}
			} else if (messageReceiver instanceof ContactMessageReceiver
				&& !ThreemaFeature.canDeleteMessages(((ContactMessageReceiver) messageReceiver).getContact().getFeatureMask())
			) {
				// no feature support in 1:1 chat
				return false;
			}
			// check message support
			return canDeleteRemotely(message);
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

			forwardItem = menu.findItem(R.id.menu_message_forward);
			saveItem = menu.findItem(R.id.menu_message_save);
			copyItem = menu.findItem(R.id.menu_message_copy);
			qrItem = menu.findItem(R.id.menu_message_qrcode);
			shareItem = menu.findItem(R.id.menu_share);
			quoteItem = menu.findItem(R.id.menu_message_quote);
			showText = menu.findItem(R.id.menu_show_text);

			updateActionMenu(menu);

			return true;
		}

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			final int checked = convListView.getCheckedItemCount();

			mode.setTitle(Integer.toString(checked));
			updateActionMenu(menu);

			return true;
		}

		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			if (selectedMessages.isEmpty()) {
				mode.finish();
				return true;
			}

			final int id = item.getItemId();
			if (id == R.id.menu_message_copy) {
				copySelectedMessagesToClipboard();
				mode.finish();
			} else if (id == R.id.menu_message_discard) {
				if (selectedMessages.size() == 1 && isDeletableRemotely(selectedMessages.get(0))) {
					showDeleteMessagesForAllDialog(selectedMessages.get(0));
				} else {
					showDeleteMessagesLocallyDialog();
				}
			} else if (id == R.id.menu_message_forward) {
				startForwardMessage();
				mode.finish();
			} else if (id == R.id.menu_message_save) {
				if (ConfigUtils.requestWriteStoragePermissions(activity, ComposeMessageFragment.this, PERMISSION_REQUEST_SAVE_MESSAGE)) {
					fileService.saveMedia(activity, coordinatorLayout, new CopyOnWriteArrayList<>(selectedMessages), false);
				}
				mode.finish();
			} else if (id == R.id.menu_message_qrcode) {
				showAsQrCode(activity.getToolbar());
				mode.finish();
			} else if (id == R.id.menu_share) {
				shareMessages();
				mode.finish();
			} else if (id == R.id.menu_message_quote) {
				showQuotePopup(null);
				mode.finish();
			} else if (id == R.id.menu_show_text) {
				showTextChatBubble(selectedMessages.get(0));
				mode.finish();
			} else {
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
			convListView.post(() -> convListView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE));

			if (ackjiPopup != null) {
				ackjiPopup.dismiss();
			}

			// If the action mode has been left without clearing up the selected messages, we need
			// to trigger a refresh so that linkified links work again (selectedMessages will be cleared lazily)
			if (!selectedMessages.isEmpty() && composeMessageAdapter != null) {
				composeMessageAdapter.notifyDataSetChanged();
			}
		}
	}

	private void setMessageTextMaxLength(int max) {
		for (int i = 0; i < messageText.getFilters().length; i++) {
			if (messageText.getFilters()[i] instanceof InputFilter.LengthFilter) {
				messageText.getFilters()[i] = new InputFilter.LengthFilter(max);
				break;
			}
		}
	}

	private class EditMessageActionMode implements ActionMode.Callback {

		private final AbstractMessageModel messageModel;
		private boolean shouldRestoreQuotePanel;

		private final TextWatcher onEditMessageTextChangedListener = new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

			}

			@Override
			public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
				updateSendEditMessageButton(getEditableText(messageModel), charSequence.toString());
			}

			@Override
			public void afterTextChanged(Editable editable) {

			}
		};

		public EditMessageActionMode(AbstractMessageModel messageModel) {
			this.messageModel = messageModel;
		}

		@SuppressLint("ClickableViewAccessibility")
		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			MenuInflater inflater = mode.getMenuInflater();
			inflater.inflate(R.menu.action_edit_message, menu);

			mode.setTitle(R.string.edit_message);

			if (typingIndicatorTextWatcher != null) {
				messageText.removeTextChangedListener(typingIndicatorTextWatcher);
				typingIndicatorTextWatcher.stopTyping();
			}

			saveMessageDraft();

            messageText.setText(getEditableText(messageModel));

			EditTextUtil.focusWindowAndShowSoftKeyboard(messageText);
			messageText.setSelection(String.valueOf(messageText.getText()).length());

			if (actionMode != null) {
				actionMode.finish();
			}

			ComposeJavaBridge.INSTANCE.setEditModeMessageBubble(
                editMessageBubbleComposeView,
                messageModel,
                userService.getIdentity()
            );
			editMessageBubbleContainer.setVisibility(View.VISIBLE);

			if (isQuotePopupShown()) {
				shouldRestoreQuotePanel = true;
				dismissQuotePopup();
			}

			sendButton.setVisibility(View.GONE);
			attachButton.setVisibility(View.GONE);
			cameraButton.setVisibility(View.GONE);
			sendEditMessageButton.setVisibility(View.VISIBLE);

			dimBackground.setAlpha(0f);
			dimBackground.setVisibility(View.VISIBLE);
			dimBackground.animate().alpha(1f).setDuration(300).setListener(new Animator.AnimatorListener() {
				@Override
				public void onAnimationStart(@NonNull Animator animation) {}

				@Override
				public void onAnimationEnd(@NonNull Animator animation) {
					setupEditMessageTextActionListener(messageModel);
					setupEditMessageButtonClickListener(messageModel);
					messageText.addTextChangedListener(onEditMessageTextChangedListener);
					updateSendEditMessageButton(
                        getEditableText(messageModel),
                        String.valueOf(messageText.getText())
                    );
				}

				@Override
				public void onAnimationCancel(@NonNull Animator animation) {
					if (editMessageActionMode != null) {
						editMessageActionMode.finish();
					}
				}

				@Override
				public void onAnimationRepeat(@NonNull Animator animation) {}
			});

			// usually messages are split into multiple messages if they are too long, but this is not possible when editing a message
			setMessageTextMaxLength(ProtocolDefines.MAX_TEXT_MESSAGE_LEN);

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

		@SuppressLint("ClickableViewAccessibility")
		@Override
		public void onDestroyActionMode(ActionMode mode) {
			// restore message draft
			if (messageReceiver != null) {
				String messageDraft = ThreemaApplication.getMessageDraft(messageReceiver.getUniqueIdString());
				messageText.setText(messageDraft);
				messageText.setSelection(String.valueOf(messageText.getText()).length());
			}

			editMessageBubbleContainer.setVisibility(View.GONE);
			editMessageBubbleComposeView.disposeComposition();

			if (shouldRestoreQuotePanel) {
				showQuotePopup(messageModel);
			}

			sendButton.setVisibility(View.VISIBLE);
			attachButton.setVisibility(View.VISIBLE);
			updateCameraButton();
			sendEditMessageButton.setVisibility(View.GONE);

			messageText.removeTextChangedListener(onEditMessageTextChangedListener);

			setupSendMessageTextActionListener();

			dimBackground.animate().alpha(0f).setDuration(300).setListener(new Animator.AnimatorListener() {
				@Override
				public void onAnimationStart(@NonNull Animator animation) {}

				@Override
				public void onAnimationEnd(@NonNull Animator animation) {
					dimBackground.setVisibility(View.GONE);
				}

				@Override
				public void onAnimationCancel(@NonNull Animator animation) {
					dimBackground.setVisibility(View.GONE);
				}

				@Override
				public void onAnimationRepeat(@NonNull Animator animation) {}
			});

			// restore default max length so long messages can be split again
			int defaultEditTextMaxLength = getResources().getInteger(R.integer.message_edittext_max_length);
			setMessageTextMaxLength(defaultEditTextMaxLength);

			if (typingIndicatorTextWatcher != null) {
				messageText.addTextChangedListener(typingIndicatorTextWatcher);
			}

			editMessageActionMode = null;
		}
	}

	private void showTextChatBubble(AbstractMessageModel messageModel) {
		Intent intent = new Intent(getContext(), MessageDetailsActivity.class);
		IntentDataUtil.append(messageModel, intent);
		activity.startActivity(intent);
	}

	private void showAsQrCode(View v) {
		AbstractMessageModel messageModel = selectedMessages.get(0);

		if (messageModel != null && messageModel.getType() == MessageType.TEXT) {
			new QRCodePopup(getContext(), getActivity().getWindow().getDecorView(), getActivity()).show(v, messageModel.getBody(), QRCodeServiceImpl.QR_TYPE_ANY);
		}
	}

	@MainThread
	private void sendUserAck() {
		AbstractMessageModel messageModel = selectedMessages.get(0);

		if (messageModel != null) {
			new Thread(() -> messageService.sendUserAcknowledgement(messageModel, false)).start();
			Toast.makeText(getActivity(), R.string.message_acknowledged, Toast.LENGTH_SHORT).show();
		}
	}

	@MainThread
	private void sendUserDec() {
		AbstractMessageModel messageModel = selectedMessages.get(0);

		if (messageModel != null) {
			new Thread(() -> messageService.sendUserDecline(messageModel, false)).start();
			Toast.makeText(getActivity(), R.string.message_declined, Toast.LENGTH_SHORT).show();
		}
	}

	/**
	 * Start the {@code ImagePaintActivity} to edit the (first) currently selected message.
	 */
	private void sendImageReply() {
		final AbstractMessageModel messageModel = selectedMessages.get(0);
		if (messageModel == null || messageModel.getMessageContentsType() != MessageContentsType.IMAGE) {
			logger.error("Invalid message model: {}", messageModel);
			return;
		}
		fileService.loadDecryptedMessageFile(messageModel, new FileService.OnDecryptedFileComplete() {
			@Override
			public void complete(File decryptedFile) {
				if (messageModel.isAvailable()) {
					Uri uri = null;
					if (decryptedFile != null) {
						uri = Uri.fromFile(decryptedFile);
					}
					if (uri == null) {
						logger.error("Uri is null");
						return;
					}

					Context context = getContext();
					if (context == null) {
						logger.error("Context is null");
						return;
					}

					MediaItem mediaItem = MediaItem.getFromUri(uri, getContext(), false);

					File outputFile;
					try {
						outputFile = fileService.createTempFile(".imageReply", ".png");
					} catch (IOException e) {
						logger.error("Couldn't create temporary file", e);
						return;
					}


					Intent imageReplyIntent = ImagePaintActivity.getImageReplyIntent(context, mediaItem, outputFile, messageReceiver, groupModel);
					IntentDataUtil.addMessageReceiverToIntent(imageReplyIntent, messageReceiver);

					imageReplyLauncher.launch(imageReplyIntent);
				}
			}

			@Override
			public void error(String message) {
				RuntimeUtil.runOnUiThread(() -> SingleToast.getInstance().showLongText(getString(R.string.an_error_occurred_during_send)));
			}
		});
	}

	public boolean onBackPressed() {
		logger.debug("onBackPressed");
		if (isEmojiPickerShown()) {
			// dismiss emoji keyboard if it's showing instead of leaving activity
			emojiPicker.hide();
			return true;
		} else {
			if (messageText != null && messageText.isMentionPopupShowing()) {
				dismissMentionPopup();
				return true;
			}
			dismissQuotePopup();
			if (editMessageActionMode != null) {
				editMessageActionMode.finish();
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
		listInstancePosition = AbsListView.INVALID_POSITION;

		if (!isHidden()) {
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
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		logger.debug("onSaveInstanceState");

		// some phones destroy the retained fragment upon going in background so we have to persist some data
		outState.putParcelable(CAMERA_URI, cameraUri);
		outState.putInt(ThreemaApplication.INTENT_DATA_GROUP, this.groupId);
		outState.putLong(ThreemaApplication.INTENT_DATA_DISTRIBUTION_LIST, this.distributionListId);
		outState.putString(ThreemaApplication.INTENT_DATA_CONTACT, this.identity);
		outState.putInt(BUNDLE_LIST_POSITION, this.listInstancePosition);
		outState.putString(BUNDLE_LIST_RECEIVER_ID, this.listInstanceReceiverId);
		outState.putInt(BUNDLE_LIST_TOP, this.listInstanceTop);
		outState.putInt(BUNDLE_LIST_LONG_CLICK_ITEM, this.longClickItem);

		super.onSaveInstanceState(outState);
	}

    private void setCurrentPageReferenceId(@Nullable Integer currentPageReferenceId) {
        this.currentPageReferenceId = currentPageReferenceId;
    }

    @Nullable
	private Integer getCurrentPageReferenceId() {
		return this.currentPageReferenceId;
	}

	private void configureSearchWidget(final MenuItem menuItem) {
		SearchView searchView = (SearchView) menuItem.getActionView();
		if (searchView != null) {
			searchView.setOnQueryTextListener(queryTextListener);
			searchView.setQueryHint(getString(R.string.hint_search_keyword));
			searchView.setIconified(false);
			searchView.setOnCloseListener(() -> {
				if (searchActionMode != null) {
					searchActionMode.finish();
				}
				return false;
			});

			LinearLayout linearLayoutOfSearchView = (LinearLayout) searchView.getChildAt(0);
			if (linearLayoutOfSearchView != null) {
				linearLayoutOfSearchView.setGravity(Gravity.CENTER_VERTICAL);
				linearLayoutOfSearchView.setPadding(0, 0, 0, 0);

				searchCounter = (TextView) layoutInflater.inflate(R.layout.textview_search_action, null);
				linearLayoutOfSearchView.addView(searchCounter);

				FrameLayout searchPreviousLayout = (FrameLayout) layoutInflater.inflate(R.layout.button_search_action, null);
				searchPreviousButton = searchPreviousLayout.findViewById(R.id.search_button);
				searchPreviousButton.setScaleY(-1);
				searchPreviousButton.setOnClickListener(v -> composeMessageAdapter.previousMatchPosition());
				linearLayoutOfSearchView.addView(searchPreviousLayout);

				FrameLayout searchNextLayout = (FrameLayout) layoutInflater.inflate(R.layout.button_search_action, null);
				searchNextButton = searchNextLayout.findViewById(R.id.search_button);
				searchProgress = searchNextLayout.findViewById(R.id.next_progress);
				searchNextButton.setOnClickListener(v -> composeMessageAdapter.nextMatchPosition());
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

			hideEmojiPickerIfShown();
			dismissMentionPopup();
			dismissQuotePopup();

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
						item.collapseActionView();
						item.setActionView(actionView);
						configureSearchWidget(menu.findItem(R.id.menu_action_search));

						insertToList(messageModels, true, true, true);
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

	@UiThread
	private void updateContactModelData(final ContactModel contactModel) {
		// Sanity check
		if (!contactModel.getIdentity().equals(identity)) {
			logger.warn("updateContactModelData was called for mismatching identity");
			return;
		}

		if (this.contactModel != contactModel) {
			// Update the contact model (and the receiver) to have the current setting for
			// sending messages (forward security). This needs to be done if the contact model
			// cache has been reset and therefore a new contact model object has been created.
			this.contactModel = contactModel;
			messageReceiver = this.contactService.createReceiver(this.contactModel);
		}

		// Update header containing contact information
		updateToolbarTitle();

		// Reset cache
		if (composeMessageAdapter != null) {
			composeMessageAdapter.resetCachedContactModelData(contactModel);
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
				this.voipStateService
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
				this.groupCallManager = serviceManager.getGroupCallManager();
				this.messageService = serviceManager.getMessageService();
				this.fileService = serviceManager.getFileService();
				this.notificationService = serviceManager.getNotificationService();
				this.distributionListService = serviceManager.getDistributionListService();
				this.messagePlayerService = serviceManager.getMessagePlayerService();
				this.blackListIdentityService = serviceManager.getBlackListService();
				this.ballotService = serviceManager.getBallotService();
				this.databaseServiceNew = serviceManager.getDatabaseServiceNew();
				this.conversationService = serviceManager.getConversationService();
				this.deviceService =serviceManager.getDeviceService();
				this.wallpaperService = serviceManager.getWallpaperService();
				this.wallpaperLauncher = wallpaperService.getWallpaperActivityResultLauncher(this, this::setBackgroundWallpaper, () -> this.messageReceiver);
				this.mutedChatsListService = serviceManager.getMutedChatsListService();
				this.mentionOnlyChatsListService = serviceManager.getMentionOnlyChatsListService();
				this.hiddenChatsListService = serviceManager.getHiddenChatsListService();
				this.ringtoneService = serviceManager.getRingtoneService();
				this.voipStateService = serviceManager.getVoipStateService();
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
					new Thread(() -> {
						distributionListService.remove(dmodel);
						RuntimeUtil.runOnUiThread(this::finishActivity);
					}).start();
				}
				break;
			case ThreemaApplication.CONFIRM_TAG_CLOSE_BALLOT:
				BallotUtil.closeBallot((AppCompatActivity) requireActivity(), (BallotModel) data, ballotService);
				break;
			case DIALOG_TAG_CONFIRM_CALL:
				VoipUtil.initiateCall((AppCompatActivity) requireActivity(), contactModel, false, null);
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
				LinkifyUtil.getInstance().openLink(uri, this, null);
				break;
			default:
				break;
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode,
	                                       @NonNull String[] permissions, @NonNull int[] grantResults) {
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
		if (messageText != null) {
			try {
				messageText.dismissMentionPopup();
			} catch (Exception e) {
				logger.error("Error dismissing mention popup", e);
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

	public void markAsRead() {
		if (messageReceiver != null) {
			try {
				List<AbstractMessageModel> unreadMessages = messageReceiver.getUnreadMessages();
				if (unreadMessages != null && unreadMessages.size() > 0) {
					new Thread(new ReadMessagesRoutine(unreadMessages, this.messageService, this.notificationService)).start();
					notificationService.cancel(messageReceiver);
				}
			} catch (SQLException e) {
				logger.error("Exception", e);
			}
		}
	}

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		hideEmojiPickerIfShown();
		EditTextUtil.hideSoftKeyboard(this.messageText);
		dismissQuotePopup();
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
				this.wallpaperService.setupWallpaperBitmap(this.messageReceiver, this.wallpaperView, ConfigUtils.isLandscape(activity), ConfigUtils.isTheDarkSide(activity));
			}
		}

		setupMessageTextClickListener();
	}

	private void restoreMessageDraft(boolean force) {
		if (this.messageReceiver != null && this.messageText != null && (force || TestUtil.isBlankOrNull(this.messageText.getText()))) {
			String messageDraft = ThreemaApplication.getMessageDraft(this.messageReceiver.getUniqueIdString());

			if (!TextUtils.isEmpty(messageDraft)) {
				this.messageText.setText("");
				this.messageText.append(messageDraft);
				String apiMessageId = ThreemaApplication.getQuoteDraft(this.messageReceiver.getUniqueIdString());
				if (apiMessageId != null) {
					AbstractMessageModel quotedMessageModel = messageService.getMessageModelByApiMessageIdAndReceiver(apiMessageId, messageReceiver);
					if (quotedMessageModel != null && QuoteUtil.isQuoteable(quotedMessageModel)) {
						showQuotePopup(quotedMessageModel);
					}
				}
				// If the draft is just "@", then dismiss the mention popup when restoring the draft
				if ("@".equals(messageDraft)) {
					dismissMentionPopup();
				}
			} else {
				this.messageText.setText("");
			}
		}
	}

	private void saveMessageDraft() {
		if (this.messageReceiver != null) {
			String draft = ThreemaApplication.getMessageDraft(this.messageReceiver.getUniqueIdString());
			if (this.messageText.getText() != null) {
				ThreemaApplication.putMessageDraft(this.messageReceiver.getUniqueIdString(),
					this.messageText.getText().toString(),
					isQuotePopupShown() ? quotePopup.getQuoteInfo().getMessageModel() : null);
			}
			if (!TestUtil.isBlankOrNull(this.messageText.getText()) || !TestUtil.isEmptyOrNull(draft)) {
				ListenerManager.conversationListeners.handle(ConversationListener::onModifiedAll);
			}
		}
	}

	@Override
	public void onKeyboardHidden() {
		if (getActivity() != null && isAdded()) {
			dismissMentionPopup();
			dismissTooltipPopup(workTooltipPopup, false);
			workTooltipPopup = null;

			if (emojiPicker != null) {
				emojiPicker.onKeyboardHidden();
			}
		}
	}

	@Override
	public void onKeyboardShown() {
		if (emojiPicker != null) {
			if (isEmojiPickerShown()) {
				emojiPicker.onKeyboardShown();
			}
			if (isResumed() &&
				!emojiPicker.isShown() &&
				searchActionMode == null &&
				messageText != null &&
				!messageText.hasFocus()) {
				// In some cases when the activity is launched where the previous activity finished with
				// an open keyboard, the messageText does not have focus even if the keyboard is shown
				// Only request focus if the emoji picker is hidden and the search bar is not shown,
				// otherwise the keyboard is needed to search emojis or the chat.
				messageText.requestFocus();
			}
		}
	}

	@Override
	public void onReportSpamClicked(@NonNull final ContactModel spammerContactModel, boolean block) {
		contactService.reportSpam(
			spammerContactModel,
			unused -> {
				if (isAdded()) {
					LongToast.makeText(getContext(), R.string.spam_successfully_reported, Toast.LENGTH_LONG).show();
				}

				final String spammerIdentity = spammerContactModel.getIdentity();
				if (block) {
					blackListIdentityService.add(spammerIdentity);
					ThreemaApplication.requireServiceManager().getExcludedSyncIdentitiesService().add(spammerIdentity);

					if (messageReceiver != null) {
						new EmptyOrDeleteConversationsAsyncTask(
							EmptyOrDeleteConversationsAsyncTask.Mode.DELETE,
							new MessageReceiver[]{messageReceiver},
							conversationService,
							groupService,
							distributionListService,
							null,
							null,
							() -> {
								ListenerManager.conversationListeners.handle(ConversationListener::onModifiedAll);
								ListenerManager.contactListeners.handle(listener -> listener.onModified(spammerIdentity));
								if (isAdded()) {
									finishActivity();
								}
							}).execute();
					}
				} else {
					reportSpamView.hide();
					ListenerManager.contactListeners.handle(listener -> listener.onModified(spammerIdentity));
				}
			},
			message -> {
				if (isAdded()) {
					LongToast.makeText(getContext(), requireContext().getString(R.string.spam_error_reporting, message), Toast.LENGTH_LONG).show();
				}
			}
		);
	}

	private void finishActivity() {
		if (activity != null) {
			activity.finish();
		}
	}

	/*--------------------------------------------------------------------------------------------*/

	private ListenableFuture<MediaController> mediaControllerFuture;

	private void initializeMedia3Controller() {
		SessionToken sessionToken = new SessionToken(getAppContext(), new ComponentName(getAppContext(), VoiceMessagePlayerService.class));

		mediaControllerFuture = new MediaController.Builder(getAppContext(), sessionToken).buildAsync();
	}

	@Nullable
	private MediaController getMedia3Controller() {
		if (mediaControllerFuture.isDone()) {
			try {
				return mediaControllerFuture.get();
			} catch (ExecutionException e) {
				logger.error("Media Controller exception", e);
			} catch (InterruptedException e) {
				logger.error("Media Controller interrupted exception", e);
				Thread.currentThread().interrupt();
			}
		}
		return null;
	}

	private void releaseMedia3Controller() {
		MediaController mediaController = getMedia3Controller();
		if (mediaController != null) {
			mediaController.stop();
			mediaController.release();
		}

		if (mediaControllerFuture != null) {
			MediaController.releaseFuture(mediaControllerFuture);
		}

		try {
			if (!getAppContext().stopService(new Intent(getAppContext(), VoiceMessagePlayerService.class))) {
				logger.debug("VoiceMessagePlayer already stopped.");
			}
		} catch (Exception e) {
			logger.error("Unable to stop VoiceMessagePlayer", e);
		}
	}

    /**
     * Get the currently stored text for the given message model. For a text message, this is just
     * the message's text. In case of a file message, the caption is returned. For other message
     * types, null is returned.
     */
    @Nullable
    private String getEditableText(@Nullable AbstractMessageModel messageModel) {
        if (messageModel == null) {
            return null;
        }
        if (messageModel.getType() == MessageType.TEXT) {
            return messageModel.getBody();
        } else if (messageModel.getType() == MessageType.FILE) {
            return messageModel.getCaption();
        }
        return null;
    }
}


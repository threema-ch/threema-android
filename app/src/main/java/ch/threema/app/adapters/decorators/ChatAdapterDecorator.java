/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2022 Threema GmbH
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

package ch.threema.app.adapters.decorators;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.fragment.app.Fragment;

import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

import ch.threema.app.R;
import ch.threema.app.cache.ThumbnailCache;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.DownloadService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.UserService;
import ch.threema.app.services.ballot.BallotService;
import ch.threema.app.services.license.LicenseService;
import ch.threema.app.services.messageplayer.MessagePlayerService;
import ch.threema.app.ui.listitemholder.AbstractListItemHolder;
import ch.threema.app.ui.listitemholder.ComposeMessageHolder;
import ch.threema.app.utils.MessageUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.StateBitmapUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.TextUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.MessageType;

abstract public class ChatAdapterDecorator extends AdapterDecorator {
	private static final Logger logger = LoggingUtil.getThreemaLogger("ChatAdapterDecorator");

	public interface OnClickElement {
		void onClick(AbstractMessageModel messageModel);
	}

	public interface OnLongClickElement {
		void onLongClick(AbstractMessageModel messageModel);
	}

	public interface OnTouchElement {
		boolean onTouch(MotionEvent motionEvent, AbstractMessageModel messageModel);
	}

	public interface ActionModeStatus {
		boolean getActionModeEnabled();
	}

	private final AbstractMessageModel messageModel;
	protected final Helper helper;
	private final StateBitmapUtil stateBitmapUtil;

	protected OnClickElement onClickElement = null;
	private OnLongClickElement onLongClickElement = null;
	private OnTouchElement onTouchElement = null;
	protected ActionModeStatus actionModeStatus = null;

	private CharSequence datePrefix = "";
	protected String dateContentDescriptionPrefix = "";

	private int groupId = 0;
	protected Map<String, Integer> identityColors = null;
	protected String filterString;

	// whether this message should be displayed as a continuation of a previous message by the same sender
	private boolean isGroupedMessage = true;

	public static class ContactCache {
		public String identity;
		public String displayName;
		public Bitmap avatar;
		public ContactModel contactModel;
	}

	public static class Helper {
		private final String myIdentity;
		private final MessageService messageService;
		private final UserService userService;
		private final ContactService contactService;
		private final FileService fileService;
		private final MessagePlayerService messagePlayerService;
		private final BallotService ballotService;
		private final ThumbnailCache thumbnailCache;
		private final PreferenceService preferenceService;
		private final DownloadService downloadService;
		private final LicenseService licenseService;
		private MessageReceiver messageReceiver;
		private int thumbnailWidth;
		private final Fragment fragment;
		protected int regularColor;
		private final Map<String, ContactCache> contacts = new HashMap<>();
		private final Drawable stopwatchIcon;
		private final int maxBubbleTextLength;
		private final int maxQuoteTextLength;

		public Helper(
			String myIdentity,
			MessageService messageService,
			UserService userService,
			ContactService contactService,
			FileService fileService,
			MessagePlayerService messagePlayerService,
			BallotService ballotService,
			ThumbnailCache thumbnailCache,
			PreferenceService preferenceService,
			DownloadService downloadService,
			LicenseService licenseService,
			MessageReceiver messageReceiver,
			int thumbnailWidth,
			Fragment fragment,
			int regularColor,
			Drawable stopwatchIcon,
			int maxBubbleTextLength,
			int maxQuoteTextLength) {
			this.myIdentity = myIdentity;
			this.messageService = messageService;
			this.userService = userService;
			this.contactService = contactService;
			this.fileService = fileService;
			this.messagePlayerService = messagePlayerService;
			this.ballotService = ballotService;
			this.thumbnailCache = thumbnailCache;
			this.preferenceService = preferenceService;
			this.downloadService = downloadService;
			this.licenseService = licenseService;
			this.messageReceiver = messageReceiver;
			this.thumbnailWidth = thumbnailWidth;
			this.fragment = fragment;
			this.regularColor = regularColor;
			this.stopwatchIcon = stopwatchIcon;
			this.maxBubbleTextLength = maxBubbleTextLength;
			this.maxQuoteTextLength = maxQuoteTextLength;
		}

		public Fragment getFragment() {
			return fragment;
		}

		public int getThumbnailWidth() {
			return thumbnailWidth;
		}

		public ThumbnailCache getThumbnailCache() {
			return thumbnailCache;
		}

		public MessagePlayerService getMessagePlayerService() {
			return messagePlayerService;
		}

		public FileService getFileService() {
			return fileService;
		}

		public UserService getUserService() {
			return userService;
		}

		public ContactService getContactService() {
			return contactService;
		}

		public MessageService getMessageService() {
			return messageService;
		}

		public PreferenceService getPreferenceService() {
			return preferenceService;
		}

		public DownloadService getDownloadService() {
			return downloadService;
		}

		public LicenseService getLicenseService() {
			return licenseService;
		}

		public String getMyIdentity() {
			return myIdentity;
		}

		public BallotService getBallotService() {
			return ballotService;
		}

		public Map<String, ContactCache> getContactCache() {
			return contacts;
		}

		public MessageReceiver getMessageReceiver() {
			return messageReceiver;
		}

		public void setThumbnailWidth(int preferredThumbnailWidth) {
			thumbnailWidth = preferredThumbnailWidth;
		}

		public Drawable getStopwatchIcon() {
			return stopwatchIcon;
		}

		public int getMaxBubbleTextLength() {
			return maxBubbleTextLength;
		}

		public int getMaxQuoteTextLength() {
			return maxQuoteTextLength;
		}

		public void setMessageReceiver(MessageReceiver messageReceiver) {
			this.messageReceiver = messageReceiver;
		}
	}

	public ChatAdapterDecorator(Context context,
	                            AbstractMessageModel messageModel,
	                            Helper helper) {
		super(context);
		this.messageModel = messageModel;
		this.helper = helper;
		stateBitmapUtil = StateBitmapUtil.getInstance();
		try {
			actionModeStatus = (ActionModeStatus) helper.getFragment();
		} catch (ClassCastException e) {
			throw new ClassCastException(context.toString()
				+ " must implement ActionModeStatus");
		}
	}

	public void setGroupMessage(int groupId, Map<String, Integer> identityColors) {
		this.groupId = groupId;
		this.identityColors = identityColors;
	}

	public void setOnClickElement(OnClickElement onClickElement) {
		this.onClickElement = onClickElement;
	}

	public void setOnLongClickElement(OnLongClickElement onClickElement) {
		onLongClickElement = onClickElement;
	}

	public void setOnTouchElement(OnTouchElement onTouchElement) {
		this.onTouchElement = onTouchElement;
	}

	final public void setFilter(String filterString) {
		this.filterString = filterString;
	}

	@Override
	final protected void configure(final AbstractListItemHolder h, int position) {
		if (!(h instanceof ComposeMessageHolder) || h.position != position) {
			return;
		}

		boolean isUserMessage = !getMessageModel().isStatusMessage()
			&& getMessageModel().getType() != MessageType.STATUS
			&& getMessageModel().getType() != MessageType.GROUP_CALL_STATUS;

		String identity = (
			messageModel.isOutbox() ?
				helper.getMyIdentity() :
				messageModel.getIdentity());

		final ComposeMessageHolder holder = (ComposeMessageHolder) h;

		//configure the chat message
		configureChatMessage(holder, position);

		if (isUserMessage) {
			if (!messageModel.isOutbox() && groupId > 0) {

				ContactCache c = helper.getContactCache().get(identity);
				if (c == null) {
					ContactModel contactModel = helper.getContactService().getByIdentity(messageModel.getIdentity());
					c = new ContactCache();
					c.displayName = NameUtil.getDisplayNameOrNickname(contactModel, true);
					c.avatar = helper.getContactService().getAvatar(contactModel, false);

					c.contactModel = contactModel;
					helper.getContactCache().put(identity, c);
				}

				if (holder.senderView != null) {
					if (isGroupedMessage) {
						holder.senderView.setVisibility(View.VISIBLE);
						holder.senderName.setText(c.displayName);

						if (identityColors != null && identityColors.containsKey(identity)) {
							holder.senderName.setTextColor(identityColors.get(identity));
						} else {
							holder.senderName.setTextColor(helper.regularColor);
						}
					} else {
						// hide sender name in grouped messages
						holder.senderView.setVisibility(View.GONE);
					}
				}

				if (holder.avatarView != null) {
					if (isGroupedMessage) {
						holder.avatarView.setImageBitmap(c.avatar);
						holder.avatarView.setVisibility(View.VISIBLE);
						if (c.contactModel != null) {
							holder.avatarView.setBadgeVisible(helper.getContactService().showBadge(c.contactModel));
						}
					} else {
						// hide avatar in grouped messages
						holder.avatarView.setVisibility(View.INVISIBLE);
					}
				}
			} else {
				if (holder.avatarView != null) {
					holder.avatarView.setVisibility(View.GONE);
				}
				if (holder.senderView != null) {
					holder.senderView.setVisibility(View.GONE);
				}
			}

			CharSequence s = MessageUtil.getDisplayDate(getContext(), messageModel, true);
			if (s == null) {
				s = "";
			}

			CharSequence contentDescription;

			if (!TestUtil.empty(datePrefix)) {
				contentDescription = dateContentDescriptionPrefix + ". "
						+ getContext().getString(R.string.state_dialog_modified) + ": "
						+ s;
				if (messageModel.isOutbox()) {
					s = TextUtils.concat(datePrefix, " | " + s);
				} else {
					s = TextUtils.concat(s + " | ", datePrefix);
				}
			} else {
				contentDescription = s;
			}
			if (holder.dateView != null) {
				holder.dateView.setText(s, TextView.BufferType.SPANNABLE);
				holder.dateView.setContentDescription(contentDescription);
			}

			stateBitmapUtil.setStateDrawable(messageModel, holder.deliveredIndicator, true);
			stateBitmapUtil.setGroupAckCount(messageModel, holder);
		}
	}

	public Spannable highlightMatches(CharSequence fullText, String filterText) {
		return TextUtil.highlightMatches(getContext(), fullText, filterText, true, false);
	}

	CharSequence formatTextString(@Nullable String string, String filterString) {
		return formatTextString(string, filterString, -1);
	}

	CharSequence formatTextString(@Nullable String string, String filterString, int maxLength) {
		if (TextUtils.isEmpty(string)) {
			return "";
		}

		if (maxLength > 0 && string.length() > maxLength) {
			return highlightMatches(string.substring(0, maxLength - 1), filterString);
		}
		return highlightMatches(string, filterString);
	}

	abstract protected void configureChatMessage(final ComposeMessageHolder holder, final int position);

	protected void setDatePrefix(String prefix, float textSize) {
		if (!TestUtil.empty(prefix) && textSize > 0) {
			Drawable icon = helper.getStopwatchIcon();
			icon.setBounds(0, 0, (int) (textSize * 0.8), (int) (textSize * 0.8));

			SpannableStringBuilder spannableString = new SpannableStringBuilder("  " + prefix);
			spannableString.setSpan(new ImageSpan(icon, ImageSpan.ALIGN_BASELINE),
				0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

			datePrefix = spannableString;
		} else {
			datePrefix = prefix;
		}
	}

	protected MessageService getMessageService() {
		return helper.getMessageService();
	}

	protected MessagePlayerService getMessagePlayerService() {
		return helper.getMessagePlayerService();
	}

	protected FileService getFileService() {
		return helper.getFileService();
	}

	protected int getThumbnailWidth() {
		return helper.getThumbnailWidth();
	}

	protected ThumbnailCache getThumbnailCache() {
		return helper.getThumbnailCache();
	}

	protected AbstractMessageModel getMessageModel() {
		return messageModel;
	}

	protected PreferenceService getPreferenceService() {
		return helper.getPreferenceService();
	}

	protected LicenseService getLicenseService() {
		return helper.getLicenseService();
	}

	protected UserService getUserService() {
		return helper.getUserService();
	}

	protected void setOnClickListener(final View.OnClickListener onViewClickListener, View view) {
		if (view != null) {
			view.setOnClickListener(v -> {
				if (onViewClickListener != null && !actionModeStatus.getActionModeEnabled()) {
					// do not propagate click if actionMode (selection mode) is enabled in parent
					onViewClickListener.onClick(v);
				}
				if (onClickElement != null) {
					//propagate event to parents
					onClickElement.onClick(getMessageModel());
				}
			});

//			propagate long click listener
			view.setOnLongClickListener(v -> {
				if (onLongClickElement != null) {
					onLongClickElement.onLongClick(getMessageModel());
				}
				return false;
			});

//			propagate touch listener
			view.setOnTouchListener(new View.OnTouchListener() {
				@Override
				public boolean onTouch(View arg0, MotionEvent event) {
					if (onTouchElement != null) {
						return onTouchElement.onTouch(event, getMessageModel());
					}
					return false;
				}
			});
		}
	}

	void setDefaultBackground(ComposeMessageHolder holder) {
		if (holder.messageBlockView.getBackground() == null) {
			@DrawableRes int drawableRes;

			if (getMessageModel().isOutbox() && !(getMessageModel() instanceof DistributionListMessageModel)) {
				// outgoing
				drawableRes = R.drawable.bubble_send_selector;
			} else {
				// incoming
				drawableRes = R.drawable.bubble_recv_selector;
			}
			holder.messageBlockView.setBackground(AppCompatResources.getDrawable(getContext(), drawableRes));

			logger.debug("*** setDefaultBackground");
		}
	}

	/**
	 * Set whether this message should be displayed as the continuation of a previous message by the same sender
	 * @param grouped If this is a grouped message, following another message by the same sender
	 */
	public void setGroupedMessage(boolean grouped) {
		isGroupedMessage = grouped;
	}

	/**
	 * Setup "Tap to resend" UI
	 * @param holder ComposeMessageHolder
	 */
	protected void setupResendStatus(ComposeMessageHolder holder) {
		if (holder.tapToResend != null) {
			if (getMessageModel() != null &&
				getMessageModel().isOutbox() &&
				(getMessageModel().getState() == MessageState.FS_KEY_MISMATCH ||
				getMessageModel().getState() == MessageState.SENDFAILED)) {
				holder.tapToResend.setVisibility(View.VISIBLE);
				holder.dateView.setVisibility(View.GONE);
			} else {
				holder.tapToResend.setVisibility(View.GONE);
				holder.dateView.setVisibility(View.VISIBLE);
			}
		}
	}
}

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

package ch.threema.app.adapters.decorators;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.AttrRes;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
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
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.MessageType;

abstract public class ChatAdapterDecorator extends AdapterDecorator {
	private static final Logger logger = LoggerFactory.getLogger(ChatAdapterDecorator.class);

	public interface OnClickRetry {
		void onClick(AbstractMessageModel messageModel);
	}

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

	protected OnClickRetry onClickRetry = null;
	protected OnClickElement onClickElement = null;
	private OnLongClickElement onLongClickElement = null;
	private OnTouchElement onTouchElement = null;
	protected ActionModeStatus actionModeStatus = null;

	private CharSequence datePrefix = "";
	protected String dateContentDescriptionPreifx = "";

	private int groupId = 0;
	protected Map<String, Integer> identityColors = null;
	protected String filterString;

	public class ContactCache {
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
		private final Map<String, ContactCache> contacts = new HashMap<String, ContactCache>();
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
			return this.fragment;
		}

		public int getThumbnailWidth() {
			return this.thumbnailWidth;
		}

		public ThumbnailCache getThumbnailCache() {
			return this.thumbnailCache;
		}

		public MessagePlayerService getMessagePlayerService() {
			return this.messagePlayerService;
		}

		public FileService getFileService() {
			return this.fileService;
		}

		public UserService getUserService() {
			return this.userService;
		}

		public ContactService getContactService() {
			return this.contactService;
		}

		public MessageService getMessageService() {
			return this.messageService;
		}

		public PreferenceService getPreferenceService() {
			return this.preferenceService;
		}

		public DownloadService getDownloadService() {
			return this.downloadService;
		}

		public LicenseService getLicenseService() {
			return this.licenseService;
		}

		public String getMyIdentity() {
			return myIdentity;
		}

		public BallotService getBallotService() {
			return this.ballotService;
		}

		public Map<String, ContactCache> getContactCache() {
			return this.contacts;
		}

		public MessageReceiver getMessageReceiver() {
			return this.messageReceiver;
		}

		public void setThumbnailWidth(int preferredThumbnailWidth) {
			this.thumbnailWidth = preferredThumbnailWidth;
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
		this.stateBitmapUtil = StateBitmapUtil.getInstance();
		try {
			this.actionModeStatus = (ActionModeStatus) helper.getFragment();
		} catch (ClassCastException e) {
			throw new ClassCastException(context.toString()
				+ " must implement ActionModeStatus");
		}
	}

	public void setGroupMessage(int groupId, Map<String, Integer> identityColors) {
		this.groupId = groupId;
		this.identityColors = identityColors;
	}

	public ChatAdapterDecorator setOnClickRetry(OnClickRetry onClickRetry) {
		this.onClickRetry = onClickRetry;
		return this;
	}

	public ChatAdapterDecorator setOnClickElement(OnClickElement onClickElement) {
		this.onClickElement = onClickElement;
		return this;
	}

	public ChatAdapterDecorator setOnLongClickElement(OnLongClickElement onClickElement) {
		this.onLongClickElement = onClickElement;
		return this;
	}

	public ChatAdapterDecorator setOnTouchElement(OnTouchElement onTouchElement) {
		this.onTouchElement = onTouchElement;
		return this;
	}

	final public ChatAdapterDecorator setFilter(String filterString) {
		this.filterString = filterString;
		return this;
	}

	@Override
	final protected void configure(final AbstractListItemHolder h, int position) {
		if (h == null || !(h instanceof ComposeMessageHolder) || h.position != position) {
			return;
		}

		boolean isUserMessage = !this.getMessageModel().isStatusMessage()
			&& this.getMessageModel().getType() != MessageType.STATUS;

		String identity = (
			messageModel.isOutbox() ?
				this.helper.getMyIdentity() :
				messageModel.getIdentity());

		final ComposeMessageHolder holder = (ComposeMessageHolder) h;

		//configure the chat message
		this.configureChatMessage(holder, position);

		if (isUserMessage) {
			if (!messageModel.isOutbox() && groupId > 0) {
				ContactCache c = null;

				c = this.helper.getContactCache().get(identity);
				ContactModel contactModel = null;
				if (c == null) {
					contactModel = this.helper.getContactService().getByIdentity(messageModel.getIdentity());
					c = new ContactCache();
					c.displayName = NameUtil.getDisplayNameOrNickname(contactModel, true);
					c.avatar = this.helper.getContactService().getAvatar(contactModel, false);

					c.contactModel = contactModel;
					this.helper.getContactCache().put(identity, c);
				}

				if (holder.senderView != null) {
					holder.senderView.setVisibility(View.VISIBLE);
					holder.senderName.setText(c.displayName);

					if (this.identityColors != null && this.identityColors.containsKey(identity)) {
						holder.senderName.setTextColor(this.identityColors.get(identity));
					} else {
						holder.senderName.setTextColor(this.helper.regularColor);
					}
				}

				if (holder.avatarView != null) {
					holder.avatarView.setImageBitmap(c.avatar);
					holder.avatarView.setVisibility(View.VISIBLE);
					if (c.contactModel != null) {
						holder.avatarView.setBadgeVisible(this.helper.getContactService().showBadge(c.contactModel));
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

			CharSequence s = MessageUtil.getDisplayDate(this.getContext(), messageModel, true);
			if (s == null) {
				s = "";
			}

			CharSequence contentDescription;

			if (!TestUtil.empty(this.datePrefix)) {
				contentDescription = this.dateContentDescriptionPreifx + ". "
						+ getContext().getString(R.string.state_dialog_modified) + ": "
						+ s;
				if (messageModel.isOutbox()) {
					s = TextUtils.concat(this.datePrefix, " | " + s);
				} else {
					s = TextUtils.concat(s + " | ", this.datePrefix);
				}
			} else {
				contentDescription = s;
			}
			if (holder.dateView != null) {
				holder.dateView.setText(s, TextView.BufferType.SPANNABLE);
				holder.dateView.setContentDescription(contentDescription);
			}

			stateBitmapUtil.setStateDrawable(messageModel, holder.deliveredIndicator, true);
		}
	}

	public Spannable highlightMatches(CharSequence fullText, String filterText) {
		return TextUtil.highlightMatches(this.getContext(), fullText, filterText, true, false);
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

	protected ChatAdapterDecorator setDatePrefix(String prefix, float textSize) {
		if (!TestUtil.empty(prefix) && textSize > 0) {
			Drawable icon = this.helper.getStopwatchIcon();
			icon.setBounds(0, 0, (int) (textSize * 0.8), (int) (textSize * 0.8));

			SpannableStringBuilder spannableString = new SpannableStringBuilder("  " + prefix);
			spannableString.setSpan(new ImageSpan(icon, ImageSpan.ALIGN_BASELINE),
				0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

			this.datePrefix = spannableString;
		} else {
			this.datePrefix = prefix;
		}
		return this;
	}

	protected MessageService getMessageService() {
		return this.helper.getMessageService();
	}

	protected MessagePlayerService getMessagePlayerService() {
		return this.helper.getMessagePlayerService();
	}

	protected FileService getFileService() {
		return this.helper.getFileService();
	}

	protected int getThumbnailWidth() {
		return this.helper.getThumbnailWidth();
	}

	protected ThumbnailCache getThumbnailCache() {
		return this.helper.getThumbnailCache();
	}

	protected AbstractMessageModel getMessageModel() {
		return this.messageModel;
	}

	protected PreferenceService getPreferenceService() {
		return this.helper.getPreferenceService();
	}

	protected LicenseService getLicenseService() {
		return this.helper.getLicenseService();
	}

	protected UserService getUserService() {
		return this.helper.getUserService();
	}

	protected void setOnClickListener(final View.OnClickListener onViewClickListener, View view) {
		if (view != null) {
			view.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					if (onViewClickListener != null && !actionModeStatus.getActionModeEnabled()) {
						// do not propagate click if actionMode (selection mode) is enabled in parent
						onViewClickListener.onClick(view);
					}
					if (onClickElement != null) {
						//propagate event to parents
						onClickElement.onClick(getMessageModel());
					}
				}
			});

//			propagate long click listener
			view.setOnLongClickListener(new View.OnLongClickListener() {
				@Override
				public boolean onLongClick(View v) {
					if (onLongClickElement != null) {
						onLongClickElement.onLongClick(getMessageModel());
					}
					return false;
				}
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
			@AttrRes int attr;

			if (this.getMessageModel().isOutbox() && !(this.getMessageModel() instanceof DistributionListMessageModel)) {
				// outgoing
				attr = R.attr.chat_bubble_send;
			} else {
				// incoming
				attr = R.attr.chat_bubble_recv;
			}

			TypedArray typedArray;
			typedArray = getContext().getTheme().obtainStyledAttributes(new int[] { attr });

			Drawable drawable = typedArray.getDrawable(0);

			typedArray.recycle();
			holder.messageBlockView.setBackground(drawable);

			logger.debug("*** setDefaultBackground");
		}
	}
}

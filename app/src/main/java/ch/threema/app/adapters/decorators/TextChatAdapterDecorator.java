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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.text.method.LinkMovementMethod;
import android.view.View;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.annotation.Nullable;
import ch.threema.app.activities.TextChatBubbleActivity;
import ch.threema.app.emojis.EmojiConversationTextView;
import ch.threema.app.fragments.ComposeMessageFragment;
import ch.threema.app.ui.listitemholder.ComposeMessageHolder;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.LinkifyUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.QuoteUtil;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupMessageModel;

public class TextChatAdapterDecorator extends ChatAdapterDecorator {
	private static final Logger logger = LoggerFactory.getLogger(ChatAdapterDecorator.class);

	private final int quoteType;

	public TextChatAdapterDecorator(Context context, AbstractMessageModel messageModel, Helper helper) {
		super(context, messageModel, helper);

		this.quoteType = QuoteUtil.getQuoteType(messageModel);
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	protected void configureChatMessage(final ComposeMessageHolder holder, final int position) {
		if(holder.bodyTextView != null) {
			holder.bodyTextView.setMovementMethod(LinkMovementMethod.getInstance());
			String messageText = this.getMessageModel().getBody();

			this.setOnClickListener(view -> {
				// no action on onClick
			}, holder.messageBlockView);

			if (quoteType != QuoteUtil.QUOTE_TYPE_NONE) {
				QuoteUtil.QuoteContent quoteContent = configureQuote(holder, this.getMessageModel());
				if (quoteContent != null) {
					messageText = quoteContent.bodyText;
				}
			} else {
				holder.bodyTextView.setText(formatTextString(messageText, this.filterString, helper.getMaxBubbleTextLength() + 8));
			}

			if (holder.readOnContainer != null) {
				if (messageText != null && messageText.length() > helper.getMaxBubbleTextLength()) {
					holder.readOnContainer.setVisibility(View.VISIBLE);
					holder.readOnButton.setOnClickListener(view -> {
						Intent intent = new Intent(helper.getFragment().getContext(), TextChatBubbleActivity.class);
						IntentDataUtil.append(this.getMessageModel(), intent);
						helper.getFragment().startActivity(intent);
					});
				} else {
					holder.readOnContainer.setVisibility(View.GONE);
					holder.readOnButton.setOnClickListener(null);
				}
			}

			LinkifyUtil.getInstance().linkify(
				(ComposeMessageFragment) helper.getFragment(),
				holder.bodyTextView,
				this.getMessageModel(),
				messageText.length() < 80,
				actionModeStatus.getActionModeEnabled(),
				onClickElement);
		}
	}

	@Nullable
	private QuoteUtil.QuoteContent configureQuote(final ComposeMessageHolder holder, final AbstractMessageModel messageModel) {
		QuoteUtil.QuoteContent content = QuoteUtil.getQuoteContent(
			messageModel,
			this.helper.getMessageReceiver().getType(),
			false,
			this.helper.getThumbnailCache(),
			this.getContext(),
			this.helper.getMessageService(),
			this.helper.getUserService(),
			this.helper.getFileService()
		);

		if (content != null) {
			if (holder.secondaryTextView instanceof EmojiConversationTextView) {
				holder.secondaryTextView.setText(formatTextString(content.quotedText, this.filterString, helper.getMaxQuoteTextLength() + 8));
				((EmojiConversationTextView) holder.secondaryTextView).setFade(content.quotedText.length() > helper.getMaxQuoteTextLength());
			}

			ContactModel contactModel = this.helper.getContactService().getByIdentity(content.identity);
			if (contactModel != null) {
				if (holder.tertiaryTextView != null) {
					holder.tertiaryTextView.setText(NameUtil.getQuoteName(contactModel, this.getUserService()));
					holder.tertiaryTextView.setVisibility(View.VISIBLE);
				}

				int barColor = ConfigUtils.getAccentColor(getContext());

				if (!helper.getMyIdentity().equals(content.identity)) {
					if (getMessageModel() instanceof GroupMessageModel) {
						if (this.identityColors != null && this.identityColors.containsKey(content.identity)) {
							barColor = this.identityColors.get(content.identity);
						}
					} else {
						barColor = contactModel.getColor();
					}
				}
				if (holder.quoteBar != null) {
					holder.quoteBar.setBackgroundColor(barColor);
					holder.quoteBar.setVisibility(View.VISIBLE);
				}
			} else {
				if (holder.tertiaryTextView != null) {
					holder.tertiaryTextView.setVisibility(View.GONE);
				}
				holder.quoteBar.setVisibility(View.GONE);
			}

			if (content.bodyText != null) {
				holder.bodyTextView.setText(formatTextString(content.bodyText, this.filterString, helper.getMaxBubbleTextLength() + 8));
				holder.bodyTextView.setVisibility(View.VISIBLE);
			} else {
				holder.bodyTextView.setText("");
				holder.bodyTextView.setVisibility(View.GONE);
			}

			if (holder.quoteThumbnail != null) {
				if (content.thumbnail != null) {
					holder.quoteThumbnail.setImageBitmap(content.thumbnail);
					holder.quoteThumbnail.setVisibility(View.VISIBLE);
				} else {
					holder.quoteThumbnail.setVisibility(View.GONE);
				}
			}

			if (holder.quoteTypeImage != null) {
				if (content.icon != null) {
					holder.quoteTypeImage.setImageResource(content.icon);
					holder.quoteTypeImage.setVisibility(View.VISIBLE);
				} else {
					holder.quoteTypeImage.setVisibility(View.GONE);
				}
			}
		}
		return content;
	}
}

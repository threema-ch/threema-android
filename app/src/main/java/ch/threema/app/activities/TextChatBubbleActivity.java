/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2021 Threema GmbH
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

import android.content.Intent;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.annotation.ColorInt;
import androidx.annotation.LayoutRes;
import androidx.appcompat.widget.Toolbar;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.emojis.EmojiConversationTextView;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.LockAppService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.LinkifyUtil;
import ch.threema.app.utils.MessageUtil;
import ch.threema.app.utils.QuoteUtil;
import ch.threema.app.utils.StateBitmapUtil;
import ch.threema.base.ThreemaException;
import ch.threema.storage.models.AbstractMessageModel;

public class TextChatBubbleActivity extends ThreemaActivity implements GenericAlertDialog.DialogClickListener {
	private static final Logger logger = LoggerFactory.getLogger(TextChatBubbleActivity.class);

	private static final int CONTEXT_MENU_FORWARD = 600;
	private static final int CONTEXT_MENU_GROUP = 22200;

	private EmojiConversationTextView textView;

	private final ActionMode.Callback textSelectionCallback = new ActionMode.Callback() {
		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			return true;
		}

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			menu.removeGroup(CONTEXT_MENU_GROUP);
			menu.add(CONTEXT_MENU_GROUP, CONTEXT_MENU_FORWARD, 200, R.string.forward_text);
			return true;
		}

		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			switch (item.getItemId()) {
				case CONTEXT_MENU_FORWARD:
					forwardText();
					break;
				default:
					return false;
			}
			return true;
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {
			// we ignore this
		}

		private void forwardText() {
			EmojiConversationTextView textView = findViewById(R.id.text_view);
			CharSequence text = textView.getText();

			if (text.length() > 0) {
				int start = textView.getSelectionStart();
				int end = textView.getSelectionEnd();

				String body = text.subSequence(start, end).toString();
				Intent intent = new Intent(TextChatBubbleActivity.this, RecipientListBaseActivity.class);
				intent.setType("text/plain");
				intent.setAction(Intent.ACTION_SEND);
				intent.putExtra(Intent.EXTRA_TEXT, body);
				intent.putExtra(ThreemaApplication.INTENT_DATA_IS_FORWARD, true);
				startActivity(intent);
				finish();
			}
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		logger.debug("onCreate");

		MessageService messageService;
		PreferenceService preferenceService;
		LockAppService lockAppService;
		MessageReceiver<? extends AbstractMessageModel> messageReceiver;
		@LayoutRes int footerLayout;
		@ColorInt int color;
		String title;

		ConfigUtils.configureActivityTheme(this);

		super.onCreate(savedInstanceState);

		try {
			ServiceManager serviceManager = ThreemaApplication.getServiceManager();
			messageService = serviceManager.getMessageService();
			preferenceService = serviceManager.getPreferenceService();
			lockAppService = serviceManager.getLockAppService();
		} catch (Exception e) {
			finish();
			return;
		}

		// set font size according to user preferences
		getTheme().applyStyle(ThreemaApplication.getServiceManager().getPreferenceService().getFontStyle(), true);
		// hide contents in app switcher and inhibit screenshots
		ConfigUtils.setScreenshotsAllowed(this, preferenceService, lockAppService);
		ConfigUtils.setLocaleOverride(this, preferenceService);

		setContentView(R.layout.activity_text_chat_bubble);

		AbstractMessageModel messageModel = IntentDataUtil.getAbstractMessageModel(getIntent(), messageService);
		try {
			messageReceiver = messageService.getMessageReceiver(messageModel);
		} catch (ThreemaException e) {
			logger.error("Exception", e);
			finish();
			return;
		}

		if (messageModel.isOutbox()) {
			// send
			if (ConfigUtils.getAppTheme(this) == ConfigUtils.THEME_DARK) {
				color = getResources().getColor(R.color.dark_bubble_send);
			} else {
				color = getResources().getColor(R.color.light_bubble_send);
			}
			title = getString(R.string.threema_message_to, messageReceiver.getDisplayName());
			footerLayout = R.layout.conversation_bubble_footer_send;
		} else {
			// recv
			if (ConfigUtils.getAppTheme(this) == ConfigUtils.THEME_DARK) {
				color = getResources().getColor(R.color.dark_bubble_recv);
			} else {
				color = getResources().getColor(R.color.light_bubble_recv);
			}
			title = getString(R.string.threema_message_from, messageReceiver.getDisplayName());
			footerLayout = R.layout.conversation_bubble_footer_recv;
		}

		Toolbar toolbar = findViewById(R.id.toolbar);
		toolbar.setNavigationOnClickListener(view -> finish());
		toolbar.setOnMenuItemClickListener(item -> {
			if (item.isChecked()) {
				item.setChecked(false);
				textView.setIgnoreMarkup(true);
				setText(messageModel);
			} else {
				item.setChecked(true);
				textView.setIgnoreMarkup(false);
				setText(messageModel);
			}
			return true;
		});
		toolbar.setTitle(title);

		ConfigUtils.addIconsToOverflowMenu(this, toolbar.getMenu());

		// TODO: replace with "toolbarNavigationButtonStyle" attribute in theme as soon as all Toolbars have been switched to Material Components
		toolbar.getNavigationIcon().setColorFilter(getResources().getColor(
			ConfigUtils.getAppTheme(this) == ConfigUtils.THEME_DARK ?
				R.color.dark_text_color_primary :
				R.color.text_color_secondary),
			PorterDuff.Mode.SRC_IN);

		MaterialCardView cardView = findViewById(R.id.card_view);
		cardView.setCardBackgroundColor(color);

		View footerView = LayoutInflater.from(this).inflate(footerLayout, null);
		((ViewGroup) findViewById(R.id.footer)).addView(footerView);

		textView = findViewById(R.id.text_view);
		setText(messageModel);

		// display date
		CharSequence s = MessageUtil.getDisplayDate(this, messageModel, true);
		((TextView) footerView.findViewById(R.id.date_view)).setText(s != null ? s : "");

		// display message status
		StateBitmapUtil.getInstance().setStateDrawable(messageModel, findViewById(R.id.delivered_indicator), true);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			// do not add on lollipop or lower due to this bug: https://issuetracker.google.com/issues/36937508
			textView.setCustomSelectionActionModeCallback(textSelectionCallback);
		}

		findViewById(R.id.back_button).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				finish();
			}
		});
	}

	private void setText(AbstractMessageModel messageModel) {
		textView.setText(QuoteUtil.getMessageBody(messageModel, false));
		LinkifyUtil.getInstance().linkify(null, this, textView, messageModel, messageModel.getBody().length() < 80, false, null);
	}

	@Override
	public void onYes(String tag, Object data) {
		if (LinkifyUtil.DIALOG_TAG_CONFIRM_LINK.equals(tag)) {
			LinkifyUtil.getInstance().openLink(this, (Uri) data);
		}
	}

	@Override
	public void onNo(String tag, Object data) {
		//
	}
}

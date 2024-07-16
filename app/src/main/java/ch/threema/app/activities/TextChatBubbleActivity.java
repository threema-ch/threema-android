/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2024 Threema GmbH
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
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.google.android.material.appbar.MaterialToolbar;

import org.slf4j.Logger;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.emojis.EmojiConversationTextView;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.MessageService;
import ch.threema.app.ui.MessageBubbleView;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.LinkifyUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.AbstractMessageModel;

public class TextChatBubbleActivity extends ThreemaToolbarActivity implements GenericAlertDialog.DialogClickListener {
	private static final Logger logger = LoggingUtil.getThreemaLogger("TextChatBubbleActivity");

	private static final int CONTEXT_MENU_FORWARD = 600;
	private static final int CONTEXT_MENU_GROUP = 22200;
	private static final float TEXT_SIZE_INCREMENT_DP = 2;

	private int defaultTextSizeDp;
	private MaterialToolbar toolbar;
	private MessageBubbleView messageBubbleView;

	private final ActionMode.Callback textSelectionCallback = new ActionMode.Callback() {
		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			return true;
		}

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			menu.removeGroup(CONTEXT_MENU_GROUP);
			try {
				menu.add(CONTEXT_MENU_GROUP, CONTEXT_MENU_FORWARD, 200, R.string.forward_text);
			} catch (Exception e) {
				// some MIUI devices crash when attempting to add a context menu
				logger.error("Error adding context menu (Xiaomi?)", e);
			}
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
	protected boolean initActivity(Bundle savedInstanceState) {
		getTheme().applyStyle(ThreemaApplication.getServiceManager().getPreferenceService().getFontStyle(), true);

		if (!super.initActivity(savedInstanceState)) {
			return false;
		}

		MessageService messageService;
		MessageReceiver<? extends AbstractMessageModel> messageReceiver;

		String title;

		try {
			messageService = serviceManager.getMessageService();
		} catch (Exception e) {
			finish();
			return false;
		}

		AbstractMessageModel messageModel = IntentDataUtil.getAbstractMessageModel(getIntent(), messageService);
		try {
			messageReceiver = messageService.getMessageReceiver(messageModel);
		} catch (ThreemaException e) {
			logger.error("Exception", e);
			finish();
			return false;
		}

		if (messageModel.isOutbox()) {
			// send
			title = getString(R.string.threema_message_to, messageReceiver.getDisplayName());
		} else {
			// recv
			title = getString(R.string.threema_message_from, messageReceiver.getDisplayName());
		}

		messageBubbleView = findViewById(R.id.message_bubble);
		messageBubbleView.show(messageModel);
		messageBubbleView.linkifyText(this, messageModel, false);

		toolbar = findViewById(R.id.material_toolbar);
		toolbar.setNavigationOnClickListener(view -> finish());
		toolbar.setOnMenuItemClickListener(item -> {
			if (item.getItemId() == R.id.enable_formatting) {
				if (item.isChecked()) {
					item.setChecked(false);
					messageBubbleView.linkifyText(this, messageModel, true);
				} else {
					item.setChecked(true);
					messageBubbleView.linkifyText(this, messageModel, false);
				}
			} else if (item.getItemId() == R.id.zoom_in) {
				messageBubbleView.increaseTextSizeByDp((int) TEXT_SIZE_INCREMENT_DP);
				updateMenus();
			} else if (item.getItemId() == R.id.zoom_out) {
				messageBubbleView.increaseTextSizeByDp((int) -TEXT_SIZE_INCREMENT_DP);
				updateMenus();
			}
			return true;
		});
		toolbar.setTitle(title);

		ConfigUtils.addIconsToOverflowMenu(this, toolbar.getMenu());

		defaultTextSizeDp = messageBubbleView.getTextSizeDp();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			// do not add on lollipop or lower due to this bug: https://issuetracker.google.com/issues/36937508
			messageBubbleView.setCustomSelectionActionModeCallback(textSelectionCallback);
		}

		findViewById(R.id.back_button).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				finish();
			}
		});

		return true;
	}

	@Override
	public int getLayoutResource() {
		return R.layout.activity_text_chat_bubble;
	}

	private void updateMenus() {
		if (messageBubbleView != null && toolbar != null) {
			Menu menu = toolbar.getMenu();
			if (menu != null) {
				menu.findItem(R.id.zoom_in).setVisible(messageBubbleView.getTextSizeDp() < (defaultTextSizeDp * 4));
				menu.findItem(R.id.zoom_out).setVisible(messageBubbleView.getTextSizeDp() > (defaultTextSizeDp / 2));
			}
		}
	}

	@Override
	public void onYes(String tag, Object data) {
		if (LinkifyUtil.DIALOG_TAG_CONFIRM_LINK.equals(tag)) {
			LinkifyUtil.getInstance().openLink((Uri) data, null, this);
		}
	}

	@Override
	public void onNo(String tag, Object data) {
		//
	}
}

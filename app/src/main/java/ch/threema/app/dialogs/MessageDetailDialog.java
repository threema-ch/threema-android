/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2021 Threema GmbH
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

package ch.threema.app.dialogs;

import android.app.Activity;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Date;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialog;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.services.MessageService;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.TextUtil;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.MessageType;

public class MessageDetailDialog extends ThreemaDialogFragment {
	private AlertDialog alertDialog;
	private Activity activity;

	public static MessageDetailDialog newInstance(@StringRes int title, int messageId, String type) {
		MessageDetailDialog dialog = new MessageDetailDialog();
		Bundle args = new Bundle();
		args.putInt("title", title);
		args.putInt("messageId", messageId);
		args.putString("messageType", type);

		dialog.setArguments(args);
		return dialog;
	}

	@Override
	public void onAttach(@NonNull Activity activity) {
		super.onAttach(activity);

		this.activity = activity;
	}

	@NonNull
	@Override
	public AppCompatDialog onCreateDialog(Bundle savedInstanceState) {
		MessageService messageService = null;
		try {
			messageService = ThreemaApplication.getServiceManager().getMessageService();
		} catch (Exception e) {
			//
		}

		if (messageService != null) {
			@StringRes int title = getArguments().getInt("title");
			int messageId = getArguments().getInt("messageId");
			String messageType = getArguments().getString("messageType");

			AbstractMessageModel messageModel = messageService.getMessageModelFromId(messageId, messageType);

			final View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_message_detail, null);
			final TextView createdText = dialogView.findViewById(R.id.created_text);
			final TextView createdDate = dialogView.findViewById(R.id.created_date);
			final TextView postedText = dialogView.findViewById(R.id.posted_text);
			final TextView postedDate = dialogView.findViewById(R.id.posted_date);
			final TextView modifiedText = dialogView.findViewById(R.id.modified_text);
			final TextView modifiedDate = dialogView.findViewById(R.id.modified_date);
			final TextView messageIdText = dialogView.findViewById(R.id.messageid_text);
			final TextView messageIdDate = dialogView.findViewById(R.id.messageid_date);
			final TextView mimeTypeText = dialogView.findViewById(R.id.filetype_text);
			final TextView mimeTypeMime = dialogView.findViewById(R.id.filetype_mime);
			final TextView fileSizeText = dialogView.findViewById(R.id.filesize_text);
			final TextView fileSizeData = dialogView.findViewById(R.id.filesize_data);

			MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity(), getTheme());
			builder.setView(dialogView);

			if (title != -1) {
				builder.setTitle(title);
			}

			builder.setPositiveButton(getString(R.string.ok), null);

			@StringRes int stateResource = getStateTextRes(messageModel);
			MessageState messageState = messageModel.getState();

			boolean showPostedAt = (messageState != null &&
				messageState != MessageState.SENDING &&
				messageState != MessageState.SENDFAILED &&
				messageState != MessageState.PENDING) ||
				messageModel.getType() == MessageType.BALLOT;

			if (messageModel.isStatusMessage()) {
				createdDate.setText(LocaleUtil.formatTimeStampStringAbsolute(getContext(), messageModel.getCreatedAt().getTime()));
			} else {
				if (messageModel.isOutbox()) {
					// outgoing msgs
					if (messageModel.getCreatedAt() != null) {
						createdDate.setText(LocaleUtil.formatTimeStampStringAbsolute(getContext(), messageModel.getCreatedAt().getTime()));
					} else {
						createdText.setVisibility(View.GONE);
						createdDate.setVisibility(View.GONE);
					}

					if (showPostedAt && messageModel.getPostedAt() != null) {
						postedDate.setText(LocaleUtil.formatTimeStampStringAbsolute(getContext(), messageModel.getPostedAt().getTime()));
						postedText.setVisibility(View.VISIBLE);
						postedDate.setVisibility(View.VISIBLE);
					}

					if (messageState != MessageState.SENT && !(messageModel.getType() == MessageType.BALLOT && messageModel instanceof GroupMessageModel)) {
						Date modifiedAt = messageModel.getModifiedAt();
						modifiedText.setText(TextUtil.capitalize(getString(stateResource)));
						modifiedDate.setText(modifiedAt != null ? LocaleUtil.formatTimeStampStringAbsolute(getContext(), messageModel.getModifiedAt().getTime()) : "");
						modifiedText.setVisibility(View.VISIBLE);
						modifiedDate.setVisibility(View.VISIBLE);
					}
				} else {
					// incoming msgs
					if (messageModel.getPostedAt() != null) {
						createdText.setText(R.string.state_dialog_posted);
						createdDate.setText(LocaleUtil.formatTimeStampStringAbsolute(getContext(), messageModel.getPostedAt().getTime()));
					} else {
						createdText.setVisibility(View.GONE);
						createdDate.setVisibility(View.GONE);
					}
					if (messageModel.getCreatedAt() != null) {
						postedText.setText(R.string.state_dialog_received);
						postedDate.setText(LocaleUtil.formatTimeStampStringAbsolute(getContext(), messageModel.getCreatedAt().getTime()));
						postedText.setVisibility(View.VISIBLE);
						postedDate.setVisibility(View.VISIBLE);
					}
					if (messageModel.getModifiedAt() != null && messageState != MessageState.READ) {
						modifiedText.setText(TextUtil.capitalize(getString(R.string.state_read)));
						modifiedDate.setText(LocaleUtil.formatTimeStampStringAbsolute(getContext(), messageModel.getModifiedAt().getTime()));
						modifiedText.setVisibility(View.VISIBLE);
						modifiedDate.setVisibility(View.VISIBLE);
					}
				}

				if (messageModel.getType() == MessageType.FILE && messageModel.getFileData() != null) {
					if (!TestUtil.empty(messageModel.getFileData().getMimeType())) {
						mimeTypeMime.setText(messageModel.getFileData().getMimeType());
						mimeTypeMime.setVisibility(View.VISIBLE);
						mimeTypeText.setVisibility(View.VISIBLE);
					}

					if (messageModel.getFileData().getFileSize() > 0) {
						fileSizeData.setText(Formatter.formatShortFileSize(getContext(), messageModel.getFileData().getFileSize()));
						fileSizeData.setVisibility(View.VISIBLE);
						fileSizeText.setVisibility(View.VISIBLE);
					}
				}

				if (!TestUtil.empty(messageModel.getApiMessageId())) {
					messageIdDate.setText(messageModel.getApiMessageId());
					messageIdDate.setVisibility(View.VISIBLE);
					messageIdText.setVisibility(View.VISIBLE);
				}
			}

			alertDialog = builder.create();
			return alertDialog;
		}
		return null;
	}

	private @StringRes int getStateTextRes(AbstractMessageModel messageModel) {
		int stateResource = 0;
		if (messageModel.getState() != null) {
			switch (messageModel.getState()) {
				case READ:
					stateResource = R.string.state_read;
					break;
				case USERACK:
					stateResource = R.string.state_ack;
					break;
				case USERDEC:
					stateResource = R.string.state_dec;
					break;
				case DELIVERED:
					stateResource = R.string.state_delivered;
					break;
				case SENT:
					stateResource = R.string.state_sent;
					break;
				case SENDING:
					stateResource = R.string.state_sending;
					break;
				case SENDFAILED:
					stateResource = R.string.state_failed;
					break;
				case PENDING:
					stateResource = R.string.state_pending;
					break;
				case TRANSCODING:
					stateResource = R.string.state_transcoding;
					break;
			}
		} else {
			stateResource = R.string.state_sent;
		}
		return stateResource;
	}

}

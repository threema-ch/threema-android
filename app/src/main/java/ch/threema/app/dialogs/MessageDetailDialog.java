/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2024 Threema GmbH
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
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatDialog;
import androidx.core.app.ActivityCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipDrawable;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.divider.MaterialDivider;

import java.util.Date;
import java.util.List;
import java.util.Map;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.ContactDetailActivity;
import ch.threema.app.activities.ThreemaActivity;
import ch.threema.app.listeners.MessageListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.MessageService;
import ch.threema.app.stores.IdentityStore;
import ch.threema.app.utils.AvatarConverterUtil;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.TextUtil;
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityMode;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.MessageType;

public class MessageDetailDialog extends ThreemaDialogFragment implements View.OnClickListener {
	private Activity activity;
	private View dialogView;
	private ContactService contactService = null;
	private IdentityStore identityStore = null;
	private AbstractMessageModel messageModel = null;
	private final MessageListener messageListener = new MessageListener() {
		@Override
		public void onNew(AbstractMessageModel newMessage) {}

		@Override
		public void onModified(List<AbstractMessageModel> modifiedMessageModels) {
			if (messageModel != null) {
				for (AbstractMessageModel modifiedMessageModel : modifiedMessageModels) {
					if (modifiedMessageModel.getId() == messageModel.getId()) {
						RuntimeUtil.runOnUiThread(() -> updateAckDisplay(modifiedMessageModel));
						break;
					}
				}
			}
		}

		@Override
		public void onRemoved(AbstractMessageModel removedMessageModel) {}

		@Override
		public void onRemoved(List<AbstractMessageModel> removedMessageModels) {}

		@Override
		public void onProgressChanged(AbstractMessageModel messageModel, int newProgress) {}

		@Override
		public void onResendDismissed(@NonNull AbstractMessageModel messageModel) {
			// Ignore
		}
	};

	public static MessageDetailDialog newInstance(@StringRes int title, int messageId, String type, @Nullable ForwardSecurityMode forwardSecurityMode) {
		MessageDetailDialog dialog = new MessageDetailDialog();
		Bundle args = new Bundle();
		args.putInt("title", title);
		args.putInt("messageId", messageId);
		args.putString("messageType", type);
		if (forwardSecurityMode != null) {
			args.putInt("forwardSecurityMode", forwardSecurityMode.getValue());
		}

		dialog.setArguments(args);
		return dialog;
	}

	@Override
	public void onAttach(@NonNull Activity activity) {
		super.onAttach(activity);

		this.activity = activity;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		ListenerManager.messageListeners.add(this.messageListener);
	}

	@Override
	public void onDestroy() {
		ListenerManager.messageListeners.remove(this.messageListener);

		super.onDestroy();
	}

	@NonNull
	@Override
	public AppCompatDialog onCreateDialog(Bundle savedInstanceState) {
		MessageService messageService = null;
		try {
			messageService = ThreemaApplication.getServiceManager().getMessageService();
			identityStore = ThreemaApplication.getServiceManager().getIdentityStore();
			contactService = ThreemaApplication.getServiceManager().getContactService();
		} catch (Exception e) {
			//
		}

		if (messageService != null && contactService != null) {
			@StringRes int title = getArguments().getInt("title");
			int messageId = getArguments().getInt("messageId");
			String messageType = getArguments().getString("messageType");
			ForwardSecurityMode forwardSecurityMode = ForwardSecurityMode.getByValue(getArguments().getInt("forwardSecurityMode"));
			String forwardSecurityModeStr = getString(R.string.forward_security_mode_none);
			if (forwardSecurityMode != null) {
				switch (forwardSecurityMode) {
					case TWODH:
						forwardSecurityModeStr = getString(R.string.forward_security_mode_2dh);
						break;
					case FOURDH:
						forwardSecurityModeStr = getString(R.string.forward_security_mode_4dh);
						break;
					default:
						break;
				}
			}

			messageModel = messageService.getMessageModelFromId(messageId, messageType);

			dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_message_detail, null);
			final TextView createdText = dialogView.findViewById(R.id.created_text);
			final TextView createdDate = dialogView.findViewById(R.id.created_date);
			final TextView postedText = dialogView.findViewById(R.id.posted_text);
			final TextView postedDate = dialogView.findViewById(R.id.posted_date);
			final TextView deliveredText = dialogView.findViewById(R.id.delivered_text);
			final TextView deliveredDate = dialogView.findViewById(R.id.delivered_date);
			final TextView readText = dialogView.findViewById(R.id.read_text);
			final TextView readDate = dialogView.findViewById(R.id.read_date);
			final TextView modifiedText = dialogView.findViewById(R.id.modified_text);
			final TextView modifiedDate = dialogView.findViewById(R.id.modified_date);
			final TextView messageIdText = dialogView.findViewById(R.id.messageid_text);
			final TextView messageIdDate = dialogView.findViewById(R.id.messageid_date);
			final TextView mimeTypeText = dialogView.findViewById(R.id.filetype_text);
			final TextView mimeTypeMime = dialogView.findViewById(R.id.filetype_mime);
			final TextView fileSizeText = dialogView.findViewById(R.id.filesize_text);
			final TextView fileSizeData = dialogView.findViewById(R.id.filesize_data);
			final TextView forwardSecurityModeText = dialogView.findViewById(R.id.forward_security_mode_text);
			final TextView forwardSecurityModeData = dialogView.findViewById(R.id.forward_security_mode_data);

			MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity(), getTheme());
			builder.setView(dialogView);

			if (title != -1) {
				builder.setTitle(title);
			}

			builder.setPositiveButton(getString(R.string.ok), null);

			@StringRes int stateResource = getStateTextRes(messageModel);
			MessageState messageState = messageModel.getState();

			if (messageModel.isStatusMessage()) {
				createdDate.setText(LocaleUtil.formatTimeStampStringAbsolute(getContext(), messageModel.getCreatedAt().getTime()));
			} else if (messageModel.getType() == MessageType.GROUP_CALL_STATUS) {
				Date deliveredAt = messageModel.getCreatedAt();
				Date postedAt = messageModel.getPostedAt();

				if (messageModel.isOutbox()) {
					if (postedAt != null) {
						postedDate.setText(LocaleUtil.formatTimeStampStringAbsolute(getContext(), postedAt.getTime()));
						postedText.setVisibility(View.VISIBLE);
						postedDate.setVisibility(View.VISIBLE);
					}
				} else {
					if (postedAt != null) {
						postedText.setText(R.string.state_dialog_posted);
						postedDate.setText(LocaleUtil.formatTimeStampStringAbsolute(getContext(), postedAt.getTime()));
						postedText.setVisibility(View.VISIBLE);
						postedDate.setVisibility(View.VISIBLE);
					}

					if (deliveredAt != null) {
						deliveredText.setText(R.string.state_dialog_received);
						deliveredDate.setText(LocaleUtil.formatTimeStampStringAbsolute(getContext(), deliveredAt.getTime()));
						deliveredText.setVisibility(View.VISIBLE);
						deliveredDate.setVisibility(View.VISIBLE);
					}
				}
				createdText.setVisibility(View.GONE);
				createdDate.setVisibility(View.GONE);
			} else {
				if (messageModel.isOutbox()) {
					// outgoing msgs
					if (messageModel.getCreatedAt() != null) {
						createdDate.setText(LocaleUtil.formatTimeStampStringAbsolute(getContext(), messageModel.getCreatedAt().getTime()));
					} else {
						createdText.setVisibility(View.GONE);
						createdDate.setVisibility(View.GONE);
					}

					boolean showPostedAt = (messageState != null &&
						messageState != MessageState.SENDING &&
						messageState != MessageState.SENDFAILED &&
						messageState != MessageState.FS_KEY_MISMATCH &&
						messageState != MessageState.PENDING) ||
						messageModel.getType() == MessageType.BALLOT;

					if (showPostedAt && messageModel.getPostedAt() != null) {
						postedDate.setText(LocaleUtil.formatTimeStampStringAbsolute(getContext(), messageModel.getPostedAt().getTime()));
						postedText.setVisibility(View.VISIBLE);
						postedDate.setVisibility(View.VISIBLE);
					}

					if (messageState != MessageState.SENT && !(messageModel.getType() == MessageType.BALLOT && messageModel instanceof GroupMessageModel)) {
						Date deliveredAt = messageModel.getDeliveredAt();
						Date readAt = messageModel.getReadAt();
						Date modifiedAt = messageModel.getModifiedAt();

						if (deliveredAt != null) {
							deliveredText.setText(TextUtil.capitalize(getString(R.string.state_delivered)));
							deliveredDate.setText(LocaleUtil.formatTimeStampStringAbsolute(getContext(), deliveredAt.getTime()));
							deliveredText.setVisibility(View.VISIBLE);
							deliveredDate.setVisibility(View.VISIBLE);
						}

						if (readAt != null) {
							readText.setText(TextUtil.capitalize(getString(R.string.state_read)));
							readDate.setText(LocaleUtil.formatTimeStampStringAbsolute(getContext(), readAt.getTime()));
							readText.setVisibility(View.VISIBLE);
							readDate.setVisibility(View.VISIBLE);
						}

						if (modifiedAt != null &&
							!(messageState == MessageState.READ && modifiedAt.equals(readAt)) &&
							!(messageState == MessageState.DELIVERED && modifiedAt.equals(deliveredAt))) {
							modifiedText.setText(TextUtil.capitalize(getString(stateResource)));
							modifiedDate.setText(LocaleUtil.formatTimeStampStringAbsolute(getContext(), modifiedAt.getTime()));
							modifiedText.setVisibility(View.VISIBLE);
							modifiedDate.setVisibility(View.VISIBLE);
						}
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
						deliveredText.setText(TextUtil.capitalize(getString(R.string.state_read)));
						deliveredDate.setText(LocaleUtil.formatTimeStampStringAbsolute(getContext(), messageModel.getModifiedAt().getTime()));
						deliveredText.setVisibility(View.VISIBLE);
						deliveredDate.setVisibility(View.VISIBLE);
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

				if (ConfigUtils.isForwardSecurityEnabled()) {
					if (messageModel instanceof GroupMessageModel || messageModel instanceof DistributionListMessageModel) {
						forwardSecurityModeData.setVisibility(View.GONE);
						forwardSecurityModeText.setVisibility(View.GONE);
					} else {
						forwardSecurityModeData.setText(forwardSecurityModeStr);
						forwardSecurityModeData.setVisibility(View.VISIBLE);
						forwardSecurityModeText.setVisibility(View.VISIBLE);
					}
				}

				updateAckDisplay(messageModel);
			}

			return builder.create();
		}
		return null;
	}

	private synchronized void updateAckDisplay(AbstractMessageModel messageModel) {
		if (!ConfigUtils.isGroupAckEnabled()) {
			return;
		}

		if (dialogView == null) {
			return;
		}

		if (!isAdded()) {
			return;
		}

		if (messageModel == null) {
			return;
		}

		final MaterialDivider groupAckDivider = dialogView.findViewById(R.id.groupack_divider);
		final MaterialCardView ackCard = dialogView.findViewById(R.id.ack_card);
		final ImageView ackIcon = dialogView.findViewById(R.id.ack_icon);
		final ChipGroup ackData = dialogView.findViewById(R.id.ack_data);
		final MaterialButton ackCountView = dialogView.findViewById(R.id.ack_count);
		final MaterialCardView decCard = dialogView.findViewById(R.id.dec_card);
		final ImageView decIcon = dialogView.findViewById(R.id.dec_icon);
		final ChipGroup decData = dialogView.findViewById(R.id.dec_data);
		final MaterialButton decCountView = dialogView.findViewById(R.id.dec_count);

		if (messageModel instanceof GroupMessageModel) {
			Map<String, Object> messageStates = ((GroupMessageModel) messageModel).getGroupMessageStates();
			if (messageStates != null && messageStates.size() > 0) {
				int ackCount = 0, decCount = 0;
				ackData.removeAllViews();
				decData.removeAllViews();

				for (Map.Entry<String, Object> entry : messageStates.entrySet()) {
					ContactModel contactModel = contactService.getByIdentity(entry.getKey());
					if (contactModel == null) {
						continue;
					}

					// an ack or dec state implies "read"
					if (MessageState.USERACK.toString().equals(entry.getValue())) {
						appendChip(ackData, contactModel);
						ackCount++;
					} else if (MessageState.USERDEC.toString().equals(entry.getValue())) {
						appendChip(decData, contactModel);
						decCount++;
					}

					if (ackCount > 0) {
						ackCard.setVisibility(View.VISIBLE);
						ackCountView.setText(String.valueOf(ackCount));
						if (messageModel.getState() == MessageState.USERACK) {
							ackIcon.setImageResource(R.drawable.ic_thumb_up_filled);
						}
					} else {
						ackCard.setVisibility(View.GONE);
					}

					if (decCount > 0) {
						decCard.setVisibility(View.VISIBLE);
						decCountView.setText(String.valueOf(decCount));
						if (messageModel.getState() == MessageState.USERDEC) {
							decIcon.setImageResource(R.drawable.ic_thumb_down_filled);
						}
					} else {
						decCard.setVisibility(View.GONE);
					}

					if (decCount > 0 || ackCount > 0) {
						groupAckDivider.setVisibility(View.VISIBLE);
					}
				}
			}
		}
	}

	private void appendChip(ChipGroup chipGroup, ContactModel contactModel) {
		Chip chip = new Chip(getContext());
		ChipDrawable chipDrawable = ChipDrawable.createFromAttributes(getContext(),
			null,
			0,
			R.style.Threema_Chip_MessageDetails);
		chip.setChipDrawable(chipDrawable);
		chip.setEnsureMinTouchTargetSize(false);
		chip.setTag(contactModel.getIdentity());
		chip.setOnClickListener(this);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			chip.setTextAppearance(R.style.Threema_TextAppearance_Chip_ChatNotice);
		} else {
			chip.setTextSize(14);
		}

		new AsyncTask<Void, Void, Bitmap>() {
			@Override
			protected Bitmap doInBackground(Void... params) {
				Bitmap bitmap = contactService.getAvatar(contactModel, false);
				if (bitmap != null) {
					return BitmapUtil.replaceTransparency(bitmap, Color.WHITE);
				}
				return null;
			}

			@Override
			protected void onPostExecute(Bitmap avatar) {
				if (avatar != null) {
					chip.setChipIcon(AvatarConverterUtil.convertToRound(getResources(), avatar));
				} else {
					chip.setChipIconResource(R.drawable.ic_contact);
				}
			}
		}.execute();

		chip.setText(NameUtil.getShortName(contactModel));
		chipGroup.addView(chip);
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
				case CONSUMED:
					stateResource = R.string.listened_to;
					break;
				case FS_KEY_MISMATCH:
					stateResource = R.string.fs_key_mismatch;
					break;
			}
		} else {
			stateResource = R.string.state_sent;
		}
		return stateResource;
	}

	@Override
	public void onClick(View v) {
		if (v instanceof Chip) {
			String identity = (String) v.getTag();
			if (identity != null && !identity.equals(identityStore.getIdentity())) {
				Intent intent = new Intent(getContext(), ContactDetailActivity.class);
				intent.putExtra(ThreemaApplication.INTENT_DATA_CONTACT, identity);
				intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				ActivityCompat.startActivityForResult(getActivity(), intent, ThreemaActivity.ACTIVITY_ID_CONTACT_DETAIL, null);
			}
		}
	}
}

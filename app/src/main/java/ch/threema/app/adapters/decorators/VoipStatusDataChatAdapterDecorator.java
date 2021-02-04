/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2021 Threema GmbH
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
import android.graphics.PorterDuff;
import android.view.View;

import ch.threema.app.R;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.fragments.ComposeMessageFragment;
import ch.threema.app.ui.SingleToast;
import ch.threema.app.ui.listitemholder.ComposeMessageHolder;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.MessageUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.StringConversionUtil;
import ch.threema.app.utils.ViewUtil;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.data.status.VoipStatusDataModel;

public class VoipStatusDataChatAdapterDecorator extends ChatAdapterDecorator {

	public VoipStatusDataChatAdapterDecorator(Context context, AbstractMessageModel messageModel, Helper helper) {
		super(context, messageModel, helper);
	}

	@Override
	protected void configureChatMessage(final ComposeMessageHolder holder, final int position) {
		if (holder.controller != null) {
			holder.controller.setClickable(false);
			holder.controller.setImageResource(R.drawable.ic_phone_locked_outline);
		}

		if(holder.bodyTextView != null) {
			MessageUtil.MessageViewElement viewElement = MessageUtil.getViewElement(this.getContext(), this.getMessageModel());

			if (viewElement != null) {
				if (viewElement.placeholder != null) {
					holder.bodyTextView.setText(viewElement.placeholder);
				}

				VoipStatusDataModel status = this.getMessageModel().getVoipStatusData();
				if (status != null && status.getStatus() == VoipStatusDataModel.FINISHED) {
					// Show duration
					if (holder.dateView != null) {
						this.setDatePrefix(StringConversionUtil.secondsToString(
							status.getDuration(),
							false
						), holder.dateView.getTextSize());
					}
				}

				// Set and tint the phone image
				if(ViewUtil.showAndSet(holder.attachmentImage, viewElement.icon)) {
					if (viewElement.color != null) {
						holder.attachmentImage.setColorFilter(
								getContext().getResources().getColor(viewElement.color),
								PorterDuff.Mode.SRC_IN);
					}
				}
			}
		}

		this.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				// load the the contact
				if (ConfigUtils.isCallsEnabled(getContext(), getPreferenceService(), getLicenseService())) {
					ContactModel contactModel = helper.getContactService().getByIdentity(getMessageModel().getIdentity());
					if (contactModel != null) {
						String name = NameUtil.getDisplayNameOrNickname(contactModel, false);

						GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.threema_call, String.format(getContext().getString(R.string.voip_call_confirm), name), R.string.ok, R.string.cancel);
						dialog.setTargetFragment(helper.getFragment(), 0);
						dialog.show(helper.getFragment().getFragmentManager(), ComposeMessageFragment.DIALOG_TAG_CONFIRM_CALL);
					}
				} else {
					SingleToast.getInstance().showLongText(getContext().getString(R.string.voip_disabled));
				}
			}
		}, holder.messageBlockView);
	}
}

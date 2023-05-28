/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2023 Threema GmbH
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

package ch.threema.app.grouplinks;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialog;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.dialogs.ThreemaDialogFragment;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.group.IncomingGroupJoinRequestService;
import ch.threema.app.utils.NameUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.models.group.IncomingGroupJoinRequestModel;
import java8.util.Optional;

public class IncomingGroupJoinRequestDialog extends ThreemaDialogFragment {
	private static final Logger logger = LoggingUtil.getThreemaLogger("IncomingGroupJoinRequestDialog");

	private static final String EXTRA_REQUEST_ID = "requestId";

	private IncomingGroupJoinRequestService incomingGroupJoinRequestService;

	private IncomingGroupJoinRequestDialog.IncomingGroupJoinRequestDialogClickListener callback;
	private AlertDialog alertDialog;
	private IncomingGroupJoinRequestModel groupJoinRequest;

	public static IncomingGroupJoinRequestDialog newInstance(int requestId) {
		IncomingGroupJoinRequestDialog dialog = new IncomingGroupJoinRequestDialog();
		Bundle args = new Bundle();
		args.putInt(EXTRA_REQUEST_ID, requestId);

		dialog.setArguments(args);
		return dialog;
	}

	@NonNull
	@Override
	public AppCompatDialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		if (savedInstanceState != null && alertDialog != null) {
			return alertDialog;
		}

		int requestId = 0;
		if (getArguments() != null) {
			requestId = getArguments().getInt(EXTRA_REQUEST_ID, 0);
			if (requestId == 0) {
				logger.error("Exception: no group join request id received");
				return returnAnErrorOccuredDialog("No group request id received");
			}
		}

		ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager == null) {
			return returnAnErrorOccuredDialog("Required services not available to handle request");
		}

		DatabaseServiceNew databaseService;
		ContactService contactService;
		try {
			databaseService = serviceManager.getDatabaseServiceNew();
			this.incomingGroupJoinRequestService = serviceManager.getIncomingGroupJoinRequestService();
			contactService = serviceManager.getContactService();
		} catch (Exception e) {
			logger.error("Exception, services not available", e);
			return returnAnErrorOccuredDialog("Required services not available to handle request");
		}

		Optional<IncomingGroupJoinRequestModel> groupRequestResult = databaseService.getIncomingGroupJoinRequestModelFactory().getById(requestId);

		if (groupRequestResult.isEmpty()) {
			logger.error("Exception: groupRequestModel not found for id {}", requestId);
			return returnAnErrorOccuredDialog("Request not found in the database");
		}
		this.groupJoinRequest = groupRequestResult.get();

		final View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_incoming_group_join_request, null);
		final TextView messageView = dialogView.findViewById(R.id.message);

		if (groupJoinRequest.getMessage().isEmpty()) {
			messageView.setText(R.string.incoming_group_request_no_message);
		}
		else {
			messageView.setText(groupJoinRequest.getMessage());
		}

		MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity());
		builder
			.setView(dialogView)
			.setTitle(String.format(getString(R.string.group_request_incoming_dialog_title), NameUtil.getDisplayName(
				contactService.getByIdentity(
					groupJoinRequest.getRequestingIdentity()
				)
			)));

		// only offer the button options if the request is open and was not answered previously
		if (groupJoinRequest.getResponseStatus() == IncomingGroupJoinRequestModel.ResponseStatus.OPEN) {
			builder.setNegativeButton(getString(R.string.reject), (dialog, which) -> reject());
			builder.setPositiveButton(getString(R.string.accept), (dialog, whichButton) -> accept());
		}

		this.alertDialog = builder.create();
		return alertDialog;
	}

	public IncomingGroupJoinRequestDialog setCallback(IncomingGroupJoinRequestDialog.IncomingGroupJoinRequestDialogClickListener callback) {
		this.callback = callback;
		return this;
	}

	private void accept() {
		try {
			incomingGroupJoinRequestService.accept(groupJoinRequest);
			if (callback != null) {
				callback.onAccept(groupJoinRequest.getMessage());
			}
		} catch (Exception e) {
			logger.error("Exception, could not accept group request", e);
		}
		dismiss();
	}

	private void reject() {
		try {
			incomingGroupJoinRequestService.reject(groupJoinRequest);
			if (callback != null) {
				callback.onReject();
			}
		} catch (ThreemaException e) {
			logger.error("Exception, could not reject group request", e);
		}
		dismiss();
	}

	private AppCompatDialog returnAnErrorOccuredDialog(String errorMessage) {
		return new MaterialAlertDialogBuilder(requireActivity())
			.setTitle(R.string.error)
			.setMessage(String.format(getString(R.string.an_error_occurred_more), errorMessage))
			.setPositiveButton(R.string.ok, null)
			.create();
	}

	public interface IncomingGroupJoinRequestDialogClickListener {
		void onAccept(String message);
		void onReject();
	}
}

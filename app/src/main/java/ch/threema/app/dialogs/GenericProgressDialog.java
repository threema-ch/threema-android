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

package ch.threema.app.dialogs;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialog;
import androidx.fragment.app.FragmentManager;
import ch.threema.app.R;

public class GenericProgressDialog extends ThreemaDialogFragment {
	private AlertDialog alertDialog;
	private Activity activity;
	private TextView messageTextView;

	public static GenericProgressDialog newInstance(@StringRes int title, @StringRes int message) {
		GenericProgressDialog dialog = new GenericProgressDialog();
		Bundle args = new Bundle();
		args.putInt("title", title);
		args.putInt("message", message);

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
		int title = getArguments().getInt("title");
		int message = getArguments().getInt("message");

		final View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_progress_generic, null);

		MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity(), 0).setCancelable(false);
		builder.setView(dialogView);

		if (title != -1) {
			builder.setTitle(title);
		}

		messageTextView = dialogView.findViewById(R.id.text);
		messageTextView.setText(message);

		setCancelable(false);

		alertDialog = builder.create();
		return alertDialog;
	}

	/**
	 * Updates message of progress bar. Do not call this directly, use {@link ch.threema.app.utils.DialogUtil#updateMessage(FragmentManager, String, String)} instead!
	 * @param message
	 */
	@UiThread
	public void setMessage(String message) {
		if (alertDialog != null && messageTextView != null) {
			messageTextView.setText(message);
		}
	}
}

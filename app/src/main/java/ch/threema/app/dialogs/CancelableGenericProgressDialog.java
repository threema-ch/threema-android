/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2024 Threema GmbH
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
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialog;
import ch.threema.app.R;

public class CancelableGenericProgressDialog extends ThreemaDialogFragment {
	private AlertDialog alertDialog;
	private Activity activity;
	private CancelableGenericProgressDialog.ProgressDialogClickListener callback;

	public static CancelableGenericProgressDialog newInstance(@StringRes int title, @StringRes int message, @StringRes int button) {
		CancelableGenericProgressDialog dialog = new CancelableGenericProgressDialog();
		Bundle args = new Bundle();
		args.putInt("title", title);
		args.putInt("message", message);
		args.putInt("button", button);

		dialog.setArguments(args);
		return dialog;
	}

	public interface ProgressDialogClickListener {
		void onProgressbarCanceled(String tag);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (callback == null) {
			try {
				callback = (CancelableGenericProgressDialog.ProgressDialogClickListener) getTargetFragment();
			} catch (ClassCastException e) {
				//
			}
		}

		// called from an activity rather than a fragment
		if (callback == null) {
			if ((activity instanceof CancelableGenericProgressDialog.ProgressDialogClickListener)) {
				callback = (CancelableGenericProgressDialog.ProgressDialogClickListener) activity;
			}
		}
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
		int button = getArguments().getInt("button");

		final String tag = this.getTag();

		final View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_progress_generic, null);
		TextView textView = dialogView.findViewById(R.id.text);

		MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity(), getTheme()).setCancelable(false);
		builder.setView(dialogView);

		if (title != -1) {
			builder.setTitle(title);
		}

		if (message != 0) {
			textView.setText(message);
		}

		builder.setPositiveButton(getString(button), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						callback.onProgressbarCanceled(tag);
					}
				}
		);

		alertDialog = builder.create();

		setCancelable(false);

		return alertDialog;
	}
}

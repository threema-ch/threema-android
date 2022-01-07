/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2022 Threema GmbH
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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatDialog;
import androidx.appcompat.widget.AppCompatCheckBox;
import ch.threema.app.R;

/**
 *  A dialog with a title and a checkbox
 */
public class TextWithCheckboxDialog extends ThreemaDialogFragment {
	private TextWithCheckboxDialogClickListener callback;
	private Activity activity;

	public interface TextWithCheckboxDialogClickListener {
		void onYes(String tag, Object data, boolean checked);
	}

	public static TextWithCheckboxDialog newInstance(String message, @StringRes int checkboxLabel, @StringRes int positive, @StringRes int negative) {
		TextWithCheckboxDialog dialog = new TextWithCheckboxDialog();
		Bundle args = new Bundle();
		args.putString("message", message);
		args.putInt("checkboxLabel", checkboxLabel);
		args.putInt("positive", positive);
		args.putInt("negative", negative);

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

		if (callback == null) {
			try {
				callback = (TextWithCheckboxDialogClickListener) getTargetFragment();
			} catch (ClassCastException e) {
				//
			}

			// called from an activity rather than a fragment
			if (callback == null) {
				if (activity instanceof TextWithCheckboxDialogClickListener) {
					callback = (TextWithCheckboxDialogClickListener) activity;
				}
			}
		}
	}

	@NonNull
	@Override
	public AppCompatDialog onCreateDialog(Bundle savedInstanceState) {
		String message = getArguments().getString("message");
		@StringRes int checkboxLabel = getArguments().getInt("checkboxLabel");
		@StringRes int positive = getArguments().getInt("positive");
		@StringRes int negative = getArguments().getInt("negative");

		final View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_text_with_checkbox, null);
		final AppCompatCheckBox checkbox = dialogView.findViewById(R.id.checkbox);

		final String tag = this.getTag();

		MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity(), getTheme())
			.setTitle(message)
			.setView(dialogView)
			.setCancelable(false)
			.setNegativeButton(negative, null)
			.setPositiveButton(positive, (dialog, which) -> callback.onYes(tag, object, checkbox.isChecked()));

		checkbox.setChecked(false);
		if (checkboxLabel != 0) {
			checkbox.setText(checkboxLabel);
		} else {
			checkbox.setVisibility(View.GONE);
		}

		setCancelable(false);

		return builder.create();
	}
}

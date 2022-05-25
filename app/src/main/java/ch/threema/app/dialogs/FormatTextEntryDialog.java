/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022 Threema GmbH
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

import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialog;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.emojis.EmojiEditText;
import ch.threema.app.utils.DialogUtil;

public class FormatTextEntryDialog extends ThreemaDialogFragment {

	public static final String ARG_TITLE = "title";
	public static final String ARG_MESSAGE = "message";
	public static final String ARG_POSITIVE = "positive";
	public static final String ARG_NEGATIVE = "negative";
	public static final String ARG_TEXT = "text";
	public static final String ARG_MAX_LINES = "maxLines";

	private final FormatTextEntryDialogClickListener callback;
	private AlertDialog alertDialog;

	public static FormatTextEntryDialog newInstance(@StringRes int title, @StringRes int message,
	                                                @StringRes int positive, @StringRes int negative,
	                                                String text, int maxLines, FormatTextEntryDialogClickListener callback) {
		FormatTextEntryDialog dialog = new FormatTextEntryDialog(callback);
		Bundle args = new Bundle();
		args.putInt(ARG_TITLE, title);
		args.putInt(ARG_MESSAGE, message);
		args.putInt(ARG_POSITIVE, positive);
		args.putInt(ARG_NEGATIVE, negative);
		args.putString(ARG_TEXT, text);
		args.putInt(ARG_MAX_LINES, maxLines);

		dialog.setArguments(args);
		return dialog;
	}

	FormatTextEntryDialog(FormatTextEntryDialogClickListener callback) {
		this.callback = callback;
	}

	public interface FormatTextEntryDialogClickListener {
		void onYes(String text);

		void onNo();
	}

	@NonNull
	@Override
	public AppCompatDialog onCreateDialog(Bundle savedInstanceState) {
		Bundle args = getArguments();
		if (args == null) {
			throw new IllegalArgumentException("Arguments must not be null");
		}

		int title = args.getInt(ARG_TITLE);
		int message = args.getInt(ARG_MESSAGE);
		int positive = args.getInt(ARG_POSITIVE);
		int negative = args.getInt(ARG_NEGATIVE);
		String text = args.getString(ARG_TEXT, "");
		int maxLines = args.getInt(ARG_MAX_LINES, 0);

		final View dialogView = requireActivity().getLayoutInflater().inflate(R.layout.dialog_format_text_entry, null);

		final TextInputLayout editTextLayout = dialogView.findViewById(R.id.format_text_input_layout);
		final EmojiEditText editText = dialogView.findViewById(R.id.format_edit_text);

		editText.setText(text);

		if (text != null && text.length() > 0) {
			editText.setSelection(text.length());
		}

		if (maxLines > 1) {
			editText.setSingleLine(false);
			editText.setMaxLines(maxLines);
		}

		editText.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				ThreemaApplication.activityUserInteract(requireActivity());
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});

		if (message != 0) {
			editTextLayout.setHint(getString(message));
		}

		MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity());
		builder.setView(dialogView);

		if (title != 0) {
			builder.setTitle(title);
		}

		builder.setPositiveButton(getString(positive), (dialog, whichButton) -> {
				Editable editable = editText.getText();
				if (editable == null) {
					callback.onYes("");
				} else {
					callback.onYes(editable.toString());
				}
			}
		);
		builder.setNegativeButton(getString(negative), (dialog, whichButton) -> callback.onNo()
		);

		alertDialog = builder.create();

		return alertDialog;
	}

	@Override
	public void onStart() {
		super.onStart();

		ColorStateList colorStateList = DialogUtil.getButtonColorStateList(requireActivity());

		alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(colorStateList);
		alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(colorStateList);

		Button neutral = alertDialog.getButton(DialogInterface.BUTTON_NEUTRAL);
		if (neutral != null) {
			neutral.setTextColor(colorStateList);
		}
	}
}

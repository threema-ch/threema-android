/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2022 Threema GmbH
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
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialog;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.ui.ComposeEditText;
import ch.threema.app.utils.AnimationUtil;
import ch.threema.app.utils.TestUtil;

public class ExpandableTextEntryDialog extends ThreemaDialogFragment {
	private ExpandableTextEntryDialogClickListener callback;
	private Activity activity;
	private AlertDialog alertDialog;

	public static ExpandableTextEntryDialog newInstance(String title, int hint, int positive, int negative, boolean expandable) {
		ExpandableTextEntryDialog dialog = new ExpandableTextEntryDialog();
		Bundle args = new Bundle();
		args.putString("title", title);
		args.putInt("message", hint);
		args.putInt("positive", positive);
		args.putInt("negative", negative);
		args.putBoolean("expandable", expandable);

		dialog.setArguments(args);
		return dialog;
	}

	public static ExpandableTextEntryDialog newInstance(String title, int hint, String preset, int positive, int negative, boolean expandable) {
		ExpandableTextEntryDialog dialog = new ExpandableTextEntryDialog();
		Bundle args = new Bundle();
		args.putString("title", title);
		args.putString("preset", preset);
		args.putInt("message", hint);
		args.putInt("positive", positive);
		args.putInt("negative", negative);
		args.putBoolean("expandable", expandable);

		dialog.setArguments(args);
		return dialog;
	}

	public interface ExpandableTextEntryDialogClickListener {
		void onYes(String tag, Object data, String text);
		void onNo(String tag);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (callback == null) {
			try {
				callback = (ExpandableTextEntryDialogClickListener) getTargetFragment();
			} catch (ClassCastException e) {
				//
			}

			// called from an activity rather than a fragment
			if (callback == null) {
				if (activity instanceof ExpandableTextEntryDialogClickListener) {
					callback = (ExpandableTextEntryDialogClickListener) activity;
				}
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
		String title = getArguments().getString("title");
		String preset = getArguments().getString("preset", null);
		int message = getArguments().getInt("message");
		int positive = getArguments().getInt("positive");
		int negative = getArguments().getInt("negative");
		boolean expandable = getArguments().getBoolean("expandable");

		final String tag = this.getTag();

		final View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_text_entry_expandable, null);

		final ComposeEditText editText = dialogView.findViewById(R.id.caption_edittext);
		final TextInputLayout editTextContainer = dialogView.findViewById(R.id.edittext_container);
		final TextView addCaptionText = dialogView.findViewById(R.id.add_caption_text);
		final ImageView expandButton = dialogView.findViewById(R.id.expand_button);
		final LinearLayout addCaptionLayout = dialogView.findViewById(R.id.add_caption_intro);

		addCaptionLayout.setClickable(true);
		addCaptionLayout.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				toggleLayout(expandButton, editTextContainer);
			}
		});

		editText.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				ThreemaApplication.activityUserInteract(activity);
			}

			@Override
			public void afterTextChanged(Editable s) {}
		});

		MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity,  getTheme());
		builder.setView(dialogView);

		if (!TestUtil.empty(title)) {
			builder.setTitle(title);
		}

		if (message != 0) {
			addCaptionText.setText(message);
		}

		if (!TestUtil.empty(preset)) {
			editText.setText(preset);
			if (expandable) {
				toggleLayout(expandButton, editTextContainer);
			}
		}

		if (!expandable) {
			addCaptionLayout.setVisibility(View.GONE);
		}

		builder.setPositiveButton(getString(positive), new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int whichButton) {
								callback.onYes(tag, object, editText.getText().toString());
							}
				}
		);
		builder.setNegativeButton(getString(negative), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
								callback.onNo(tag);
							}
						}
				);
		alertDialog = builder.create();
		setCancelable(false);

		return alertDialog;
	}

	private void toggleLayout(ImageView button, View v) {
		InputMethodManager imm = (InputMethodManager)v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
		EditText editText = v.findViewById(R.id.caption_edittext);

		if(v.isShown()){
			AnimationUtil.slideUp(activity, v);
			v.setVisibility(View.GONE);
			button.setRotation(0);
			if (imm != null && editText != null) {
				imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
			}
		}
		else{
			v.setVisibility(View.VISIBLE);
			AnimationUtil.slideDown(activity, v, () -> {
				if (editText != null) {
					editText.requestFocus();
					if (imm != null) {
						imm.showSoftInput(editText, 0);
					}
				}
			});
			button.setRotation(90);
		}
	}
}

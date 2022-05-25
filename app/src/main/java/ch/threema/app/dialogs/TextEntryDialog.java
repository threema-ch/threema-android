/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2022 Threema GmbH
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
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.telephony.PhoneNumberFormattingTextWatcher;
import android.text.Editable;
import android.text.InputFilter;
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
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.LocaleService;
import ch.threema.app.utils.DialogUtil;
import ch.threema.domain.protocol.csp.ProtocolDefines;

public class TextEntryDialog extends ThreemaDialogFragment {

	public static final String ARG_TITLE = "title";
	public static final String ARG_MESSAGE = "message";
	public static final String ARG_POSITIVE = "positive";
	public static final String ARG_NEUTRAL = "neutral";
	public static final String ARG_NEGATIVE = "negative";
	public static final String ARG_TEXT = "text";
	public static final String ARG_INPUT_TYPE = "inputType";
	public static final String ARG_INPUT_FILTER_TYPE = "inputFilterType";
	public static final String ARG_MAX_LINES = "maxLines";
	public static final String ARG_MAX_LENGTH = "maxLength";
	public static final String ARG_MIN_LENGTH = "minLength";
	public static final String ARG_ENABLE_FORMATTING = "enableFormatting";

	private TextEntryDialogClickListener callback;
	private Activity activity;
	private AlertDialog alertDialog;
	private LocaleService localeService;
	private int inputFilterType, minLength = 0;

	public static int INPUT_FILTER_TYPE_NONE = 0;
	public static int INPUT_FILTER_TYPE_IDENTITY = 1;
	public static int INPUT_FILTER_TYPE_PHONE = 2;

	public static TextEntryDialog newInstance(@StringRes int title, @StringRes int message,
	                                          @StringRes int positive, @StringRes int neutral, @StringRes int negative,
	                                          String text, int inputType, int inputFilterType) {
		TextEntryDialog dialog = new TextEntryDialog();
		Bundle args = new Bundle();
		args.putInt(ARG_TITLE, title);
		args.putInt(ARG_MESSAGE, message);
		args.putInt(ARG_POSITIVE, positive);
		args.putInt(ARG_NEUTRAL, neutral);
		args.putInt(ARG_NEGATIVE, negative);
		args.putString(ARG_TEXT, text);
		args.putInt(ARG_INPUT_TYPE, inputType);
		args.putInt(ARG_INPUT_FILTER_TYPE, inputFilterType);

		dialog.setArguments(args);
		return dialog;
	}

	public static TextEntryDialog newInstance(@StringRes int title, @StringRes int message,
	                                          @StringRes int positive, @StringRes int negative,
	                                          String text, int inputType, int inputFilterType,
	                                          int maxLines) {
		TextEntryDialog dialog = new TextEntryDialog();
		Bundle args = new Bundle();
		args.putInt(ARG_TITLE, title);
		args.putInt(ARG_MESSAGE, message);
		args.putInt(ARG_POSITIVE, positive);
		args.putInt(ARG_NEGATIVE, negative);
		args.putString(ARG_TEXT, text);
		args.putInt(ARG_INPUT_TYPE, inputType);
		args.putInt(ARG_INPUT_FILTER_TYPE, inputFilterType);
		args.putInt(ARG_MAX_LINES, maxLines);

		dialog.setArguments(args);
		return dialog;
	}

	public static TextEntryDialog newInstance(@StringRes int title, @StringRes int message,
	                                          @StringRes int positive, @StringRes int neutral, @StringRes int negative,
	                                          String text, int inputType, int inputFilterType, int maxLength) {
		TextEntryDialog dialog = new TextEntryDialog();
		Bundle args = new Bundle();
		args.putInt(ARG_TITLE, title);
		args.putInt(ARG_MESSAGE, message);
		args.putInt(ARG_POSITIVE, positive);
		args.putInt(ARG_NEUTRAL, neutral);
		args.putInt(ARG_NEGATIVE, negative);
		args.putString(ARG_TEXT, text);
		args.putInt(ARG_INPUT_TYPE, inputType);
		args.putInt(ARG_INPUT_FILTER_TYPE, inputFilterType);
		args.putInt(ARG_MAX_LENGTH, maxLength);

		dialog.setArguments(args);
		return dialog;
	}

	public static TextEntryDialog newInstance(@StringRes int title, @StringRes int message,
	                                          @StringRes int positive, @StringRes int negative,
	                                          String text, int inputType, int inputFilterType) {
		TextEntryDialog dialog = new TextEntryDialog();
		Bundle args = new Bundle();
		args.putInt(ARG_TITLE, title);
		args.putInt(ARG_MESSAGE, message);
		args.putInt(ARG_POSITIVE, positive);
		args.putInt(ARG_NEGATIVE, negative);
		args.putString(ARG_TEXT, text);
		args.putInt(ARG_INPUT_TYPE, inputType);
		args.putInt(ARG_INPUT_FILTER_TYPE, inputFilterType);

		dialog.setArguments(args);
		return dialog;
	}

	public static TextEntryDialog newInstance(@StringRes int title, @StringRes int message,
	                                          @StringRes int positive, @StringRes int negative,
	                                          int maxLines, int maxLength) {
		TextEntryDialog dialog = new TextEntryDialog();
		Bundle args = new Bundle();
		args.putInt(ARG_TITLE, title);
		args.putInt(ARG_MESSAGE, message);
		args.putInt(ARG_POSITIVE, positive);
		args.putInt(ARG_NEGATIVE, negative);
		args.putInt(ARG_MAX_LINES, maxLines);
		args.putInt(ARG_MAX_LENGTH, maxLength);

		dialog.setArguments(args);
		return dialog;
	}

	public static TextEntryDialog newInstance(@StringRes int title, @StringRes int message,
	                                          @StringRes int positive, @StringRes int negative,
	                                          int maxLines, int maxLength, int minLength) {
		TextEntryDialog dialog = new TextEntryDialog();
		Bundle args = new Bundle();
		args.putInt(ARG_TITLE, title);
		args.putInt(ARG_MESSAGE, message);
		args.putInt(ARG_POSITIVE, positive);
		args.putInt(ARG_NEGATIVE, negative);
		args.putInt(ARG_MAX_LINES, maxLines);
		args.putInt(ARG_MAX_LENGTH, maxLength);
		args.putInt(ARG_MIN_LENGTH, minLength);

		dialog.setArguments(args);
		return dialog;
	}

	public static TextEntryDialog newInstance(@StringRes int title, @StringRes int message,
	                                          @StringRes int positive, @StringRes int negative,
	                                          String text, int inputType, int inputFilterType,
	                                          int maxLines, boolean enableFormatting) {
		TextEntryDialog dialog = new TextEntryDialog();
		Bundle args = new Bundle();
		args.putInt(ARG_TITLE, title);
		args.putInt(ARG_MESSAGE, message);
		args.putInt(ARG_POSITIVE, positive);
		args.putInt(ARG_NEGATIVE, negative);
		args.putString(ARG_TEXT, text);
		args.putInt(ARG_INPUT_TYPE, inputType);
		args.putInt(ARG_INPUT_FILTER_TYPE, inputFilterType);
		args.putInt(ARG_MAX_LINES, maxLines);
		args.putBoolean(ARG_ENABLE_FORMATTING, enableFormatting);

		dialog.setArguments(args);
		return dialog;
	}

	public interface TextEntryDialogClickListener {
		void onYes(String tag, String text);
		void onNo(String tag);
		void onNeutral(String tag);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (callback == null) {
			try {
				callback = (TextEntryDialogClickListener) getTargetFragment();
			} catch (ClassCastException e) {
				//
			}

			// called from an activity rather than a fragment
			if (callback == null) {
				if (activity instanceof TextEntryDialogClickListener) {
					callback = (TextEntryDialogClickListener) activity;
				}
			}
		}
		ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager != null) {
			localeService = ThreemaApplication.getServiceManager().getLocaleService();
		}
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		this.activity = activity;
	}

	@NonNull
	@Override
	public AppCompatDialog onCreateDialog(Bundle savedInstanceState) {
		int title = getArguments().getInt(ARG_TITLE);
		int message = getArguments().getInt(ARG_MESSAGE);
		int positive = getArguments().getInt(ARG_POSITIVE);
		int neutral = getArguments().getInt(ARG_NEUTRAL);
		int negative = getArguments().getInt(ARG_NEGATIVE);
		String text = getArguments().getString(ARG_TEXT, "");
		int inputType = getArguments().getInt(ARG_INPUT_TYPE);
		inputFilterType = getArguments().getInt(ARG_INPUT_FILTER_TYPE, 0);
		int maxLength = getArguments().getInt(ARG_MAX_LENGTH, 0);
		int maxLines = getArguments().getInt(ARG_MAX_LINES, 0);
		minLength = getArguments().getInt(ARG_MIN_LENGTH, 0);

		final String tag = this.getTag();

		final View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_text_entry, null);

		final TextInputLayout editTextLayout;
		final EmojiEditText editText;

		if (getArguments().getBoolean(ARG_ENABLE_FORMATTING, false)) {
			editTextLayout = dialogView.findViewById(R.id.format_text_input_layout);
			editText = dialogView.findViewById(R.id.format_edit_text);
		} else {
			editTextLayout = dialogView.findViewById(R.id.text_input_layout);
			editText = dialogView.findViewById(R.id.edit_text);
		}
		editTextLayout.setVisibility(View.VISIBLE);

		editText.setText(text);
		if (text != null && text.length() > 0) {
			editText.setSelection(text.length());
		}

		if (inputType != 0) {
			editText.setInputType(inputType);
		}

		if (maxLength > 0) {
			editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
		}

		if (maxLines > 1) {
			editText.setSingleLine(false);
			editText.setMaxLines(maxLines);
		}

		editText.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				ThreemaApplication.activityUserInteract(activity);
			}

			@Override
			public void afterTextChanged(Editable s) {
				if (minLength > 0) {
					alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(s != null && s.length() >= minLength);
				}
			}
		});

		if (inputFilterType == INPUT_FILTER_TYPE_IDENTITY) {
			editText.setFilters(new InputFilter[]{new InputFilter.AllCaps(), new InputFilter.LengthFilter(ProtocolDefines.IDENTITY_LEN)});
		} else if (inputFilterType == INPUT_FILTER_TYPE_PHONE && localeService != null) {
			editText.addTextChangedListener(new PhoneNumberFormattingTextWatcher(localeService.getCountryIsoCode()));
			editText.addTextChangedListener(new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {}

				@Override
				public void afterTextChanged(Editable s) {
					alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(localeService.validatePhoneNumber(s.toString()));
				}
			});
		}

		if (message != 0) {
			editTextLayout.setHint(getString(message));
		}

		MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity());
		builder.setView(dialogView);

		if (title != 0) {
			builder.setTitle(title);
		}

		builder.setPositiveButton(getString(positive), new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int whichButton) {
								callback.onYes(tag, editText.getText().toString());
							}
				}
		);
		builder.setNegativeButton(getString(negative), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
								callback.onNo(tag);
							}
						}
				);
		if (neutral != 0) {
			builder.setNeutralButton(getString(neutral),
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int whichButton) {
							callback.onNeutral(tag);
						}
					}
			);
		}

		alertDialog = builder.create();

		return alertDialog;
	}

	@Override
	public void onStart() {
		super.onStart();

		ColorStateList colorStateList = DialogUtil.getButtonColorStateList(activity);

		alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(colorStateList);
		alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(colorStateList);

		if (inputFilterType == INPUT_FILTER_TYPE_PHONE || minLength > 0) {
			alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
		}

		Button neutral = alertDialog.getButton(DialogInterface.BUTTON_NEUTRAL);
		if (neutral != null) {
			neutral.setTextColor(colorStateList);
		}
	}
}

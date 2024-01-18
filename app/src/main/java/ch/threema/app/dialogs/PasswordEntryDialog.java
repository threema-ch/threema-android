/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2024 Threema GmbH
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
import android.text.Editable;
import android.text.Html;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialog;
import androidx.core.text.util.LinkifyCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import ch.threema.app.R;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.LocaleUtil;

public class PasswordEntryDialog extends ThreemaDialogFragment implements GenericAlertDialog.DialogClickListener {
	private static final String DIALOG_TAG_CONFIRM_CHECKBOX = "dtcc";

	protected PasswordEntryDialogClickListener callback;
	protected Activity activity;
	protected AlertDialog alertDialog;
	protected boolean isLinkify = false;
	protected boolean isLengthCheck = true;
	protected int minLength, maxLength;
	protected MaterialSwitch checkBox;
	public enum ForgotHintType {
		NONE,
		SAFE,
		PIN_PASSPHRASE
	}

	public static PasswordEntryDialog newInstance(@StringRes int title, @StringRes int message,
	                                              @StringRes int hint,
	                                              @StringRes int positive, @StringRes int negative,
	                                              int minLength, int maxLength,
	                                              int confirmHint, int inputType, int checkboxText,
	                                              ForgotHintType showForgotPwHint) {
		PasswordEntryDialog dialog = new PasswordEntryDialog();
		Bundle args = new Bundle();
		args.putInt("title", title);
		args.putInt("message", message);
		args.putInt("hint", hint);
		args.putInt("positive", positive);
		args.putInt("negative", negative);
		args.putInt("minLength", minLength);
		args.putInt("maxLength", maxLength);
		args.putInt("confirmHint", confirmHint);
		args.putInt("inputType", inputType);
		args.putInt("checkboxText", checkboxText);
		args.putSerializable("showForgotPwHint", showForgotPwHint);

		dialog.setArguments(args);
		return dialog;
	}

	public static PasswordEntryDialog newInstance(@StringRes int title, @StringRes int message,
	                                              @StringRes int hint,
	                                              @StringRes int positive, @StringRes int negative,
	                                              int minLength, int maxLength, int confirmHint,
	                                              int inputType, int checkboxText, int checkboxConfirmText) {
		PasswordEntryDialog dialog = new PasswordEntryDialog();
		Bundle args = new Bundle();
		args.putInt("title", title);
		args.putInt("message", message);
		args.putInt("hint", hint);
		args.putInt("positive", positive);
		args.putInt("negative", negative);
		args.putInt("minLength", minLength);
		args.putInt("maxLength", maxLength);
		args.putInt("confirmHint", confirmHint);
		args.putInt("inputType", inputType);
		args.putInt("checkboxText", checkboxText);
		args.putInt("checkboxConfirmText", checkboxConfirmText);

		dialog.setArguments(args);
		return dialog;
	}

	@Override
	public void onYes(String tag, Object data) { }

	@Override
	public void onNo(String tag, Object data) {
		checkBox.setChecked(false);
	}


	public interface PasswordEntryDialogClickListener {
		void onYes(String tag, String text, boolean isChecked, Object data);
		void onNo(String tag);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		try {
			callback = (PasswordEntryDialogClickListener) getTargetFragment();
		} catch (ClassCastException e) {
			//
		}

		// called from an activity rather than a fragment
		if (callback == null) {
			if (!(activity instanceof PasswordEntryDialogClickListener)) {
				throw new ClassCastException("Calling fragment must implement TextEntryDialogClickListener interface");
			}
			callback = (PasswordEntryDialogClickListener) activity;
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
		if (savedInstanceState != null && alertDialog != null) {
			return alertDialog;
		}

		final int title = getArguments().getInt("title");
		int message = getArguments().getInt("message");
		int hint = getArguments().getInt("hint");
		int positive = getArguments().getInt("positive");
		int negative = getArguments().getInt("negative");
		int inputType = getArguments().getInt("inputType", 0);
		minLength = getArguments().getInt("minLength", 0);
		maxLength = getArguments().getInt("maxLength", 0);
		final int confirmHint = getArguments().getInt("confirmHint", 0);
		final int checkboxText = getArguments().getInt("checkboxText", 0);
		final int checkboxConfirmText = getArguments().getInt("checkboxConfirmText", 0);
		final ForgotHintType showForgotPwHint = (ForgotHintType) getArguments().getSerializable("showForgotPwHint");

		final String tag = this.getTag();

		// InputType defaults
		final int inputTypePasswordHidden = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_PASSWORD;

		final View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_password_entry, null);
		final TextView messageTextView = dialogView.findViewById(R.id.message_text);
		final TextView forgotPwTextView = dialogView.findViewById(R.id.forgot_password);
		final TextInputEditText editText1 = dialogView.findViewById(R.id.password1);
		final TextInputEditText editText2 = dialogView.findViewById(R.id.password2);
		final TextInputLayout editText1Layout = dialogView.findViewById(R.id.password1layout);
		final TextInputLayout editText2Layout = dialogView.findViewById(R.id.password2layout);
		checkBox = dialogView.findViewById(R.id.check_box);

		editText1.addTextChangedListener(new PasswordWatcher(editText1, editText2));
		editText2.addTextChangedListener(new PasswordWatcher(editText1, editText2));

		if (maxLength > 0) {
			editText1.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
			editText2.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
		}

		if (message != 0) {
			String messageString = getString(message);

			if (messageString.contains("https://")) {
				final SpannableString s = new SpannableString(messageString);
				LinkifyCompat.addLinks(s, Linkify.WEB_URLS);

				messageTextView.setText(s);
				isLinkify = true;
			} else {
				messageTextView.setText(messageString);
			}
		}

		if (inputType != 0) {
			editText1.setInputType(inputType);
			editText2.setInputType(inputType);
		}

		if (hint != 0) {
			editText1Layout.setHint(getString(hint));
			editText2Layout.setHint(getString(hint));
		}

		if (checkboxText != 0) {
			checkBox.setVisibility(View.VISIBLE);
			checkBox.setText(checkboxText);

			if (checkboxConfirmText != 0) {
				checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
					if (isChecked) {
						DialogUtil.dismissDialog(getFragmentManager(), DIALOG_TAG_CONFIRM_CHECKBOX, true);
						GenericAlertDialog genericAlertDialog = GenericAlertDialog.newInstance(title, checkboxConfirmText, R.string.ok, R.string.cancel);
						genericAlertDialog.setTargetFragment(this, 0);
						genericAlertDialog.show(getFragmentManager(), DIALOG_TAG_CONFIRM_CHECKBOX);
					}
				});
			}
		}

		if (confirmHint == 0) {
			editText1.setInputType(inputTypePasswordHidden);
			editText2.setVisibility(View.GONE);
			editText2Layout.setVisibility(View.GONE);
			isLengthCheck = false;
		} else {
			editText2Layout.setHint(getString(confirmHint));
			editText1Layout.setHelperTextEnabled(true);
			editText1Layout.setHelperText(String.format(activity.getString(R.string.password_too_short), minLength));
		}

		if (showForgotPwHint != null) {
			switch (showForgotPwHint) {
				case SAFE:
					String safeFaqUrl = String.format(getString(R.string.threema_safe_password_faq), LocaleUtil.getAppLanguage());
					forgotPwTextView.setText(Html.fromHtml(String.format(getString(R.string.forgot_your_password), safeFaqUrl)));
					forgotPwTextView.setMovementMethod(LinkMovementMethod.getInstance());
					forgotPwTextView.setVisibility(View.VISIBLE);
					break;
				case PIN_PASSPHRASE:
					String pinFaqUrl = String.format(getString(R.string.threema_passwords_faq), LocaleUtil.getAppLanguage());
					forgotPwTextView.setText(Html.fromHtml(String.format(getString(R.string.forgot_your_password), pinFaqUrl)));
					forgotPwTextView.setMovementMethod(LinkMovementMethod.getInstance());
					forgotPwTextView.setVisibility(View.VISIBLE);
					break;
				case NONE:
					break;
			}
		}

		MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity(), getTheme());

		if (title != 0) {
			builder.setTitle(title);
		}

		builder.setView(dialogView);

		builder.setPositiveButton(getString(positive), (dialog, whichButton) -> {
			if (checkboxText != 0) {
				callback.onYes(tag, editText1.getText().toString(), checkBox.isChecked(), object);
			} else {
				callback.onYes(tag, editText1.getText().toString(), false, object);
			}
		});
		builder.setNegativeButton(getString(negative), (dialog, whichButton) -> callback.onNo(tag));

		builder.setBackgroundInsetTop(getResources().getDimensionPixelSize(R.dimen.dialog_inset_top_bottom));
		builder.setBackgroundInsetBottom(getResources().getDimensionPixelSize(R.dimen.dialog_inset_top_bottom));

		builder.setBackgroundInsetTop(getResources().getDimensionPixelSize(R.dimen.dialog_inset_top_bottom));
		builder.setBackgroundInsetBottom(getResources().getDimensionPixelSize(R.dimen.dialog_inset_top_bottom));

		alertDialog = builder.create();
		alertDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
		return alertDialog;
	}

	@Override
	public void onCancel(@NonNull DialogInterface dialogInterface) {
		callback.onNo(this.getTag());
	}

	public class PasswordWatcher implements TextWatcher {
		private final EditText password1;
		private final EditText password2;

		public PasswordWatcher(final EditText password1, final EditText password2) {
			this.password1 = password1;
			this.password2 = password2;
		}

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {
		}

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {
		}

		@Override
		public void afterTextChanged(Editable s) {
			String password1Text = password1.getText().toString();
			String password2Text = password2.getText().toString();

			if (isLengthCheck) {
				alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(getPasswordOK(password1Text, password2Text));
			} else {
				alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(password1Text.length() > 0);
			}
		}
	}

	private boolean getPasswordOK(String password1Text, String password2Text) {
		boolean lengthOk = password1Text.length() >= minLength;
		if (maxLength > 0) {
			lengthOk = lengthOk && password1Text.length() <= maxLength;
		}
		boolean passwordsMatch = password1Text.equals(password2Text);

		return (lengthOk && passwordsMatch);
	}

	@Override
	public void onStart() {
		super.onStart();

		if (isLinkify) {
			View textView = alertDialog.findViewById(R.id.message_text);

			if (textView instanceof TextView) {
				((TextView) textView).setMovementMethod(LinkMovementMethod.getInstance());
			}
		}

		final TextInputEditText editText1 = alertDialog.findViewById(R.id.password1);

		if (isLengthCheck) {
			final TextInputEditText editText2 = alertDialog.findViewById(R.id.password2);

			if (editText1 != null && editText2 != null) {
				if (editText1.getText() != null && editText2.getText() != null) {
					alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(getPasswordOK(editText1.getText().toString(), editText2.getText().toString()));
				}
			}
		} else {
			if (editText1 != null && editText1.getText() != null) {
				alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(editText1.getText().length() > 0);
			}
		}

		ColorStateList colorStateList = DialogUtil.getButtonColorStateList(activity);

		alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(colorStateList);
		alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(colorStateList);
	}
}

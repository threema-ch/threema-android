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
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialog;
import androidx.core.text.util.LinkifyCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.slf4j.Logger;

import androidx.fragment.app.Fragment;
import ch.threema.app.R;
import ch.threema.app.ui.SimpleTextWatcher;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.LocaleUtil;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class PasswordEntryDialog extends ThreemaDialogFragment implements GenericAlertDialog.DialogClickListener {
    private static final Logger logger = getThreemaLogger("PasswordEntryDialog");
    private static final String DIALOG_TAG_CONFIRM_CHECKBOX = "dtcc";

    protected @Nullable PasswordEntryDialogClickListener callback;
    protected Activity activity;
    protected AlertDialog alertDialog;
    protected boolean isLinkify = false;
    protected boolean requiresPasswordConfirmation = true;
    protected int minLength, maxLength;
    protected MaterialSwitch checkBox;

    public enum ForgotHintType {
        NONE,
        SAFE,
        PIN_PASSPHRASE
    }

    @NonNull
    public static PasswordEntryDialog newInstance(
        @StringRes int title,
        @StringRes int message,
        @StringRes int hint,
        @StringRes int positive,
        @StringRes int negative,
        int minLength,
        int maxLength,
        int confirmHint,
        int inputType,
        int checkboxText,
        ForgotHintType showForgotPwHint
    ) {
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

    @NonNull
    public static PasswordEntryDialog newInstance(
        @StringRes int title,
        @StringRes int message,
        @StringRes int hint,
        @StringRes int positive,
        @StringRes int negative,
        int minLength,
        int maxLength,
        int confirmHint,
        int inputType,
        int checkboxText,
        int checkboxConfirmText
    ) {
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
    public void onYes(@Nullable String tag, @Nullable Object data) {
    }

    @Override
    public void onNo(@Nullable String tag, @Nullable Object data) {
        checkBox.setChecked(false);
    }

    public interface PasswordEntryDialogClickListener {
        void onYes(@Nullable String tag, @NonNull String text, boolean isChecked, @Nullable Object data);

        default void onNo(@Nullable String tag) {
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);

        if (callback != null) {
            return;
        }

        // Check if the target fragment implements our callback
        final @Nullable Fragment targetFragment = getTargetFragment();
        if (targetFragment instanceof PasswordEntryDialogClickListener) {
            callback = (PasswordEntryDialogClickListener) targetFragment;
            return;
        }

        // Check if the activity implements our callback
        if (activity instanceof PasswordEntryDialogClickListener) {
            callback = (PasswordEntryDialogClickListener) activity;
        }
    }

    public void setCallback(@Nullable PasswordEntryDialogClickListener passwordEntryDialogClickListener) {
        this.callback = passwordEntryDialogClickListener;
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

        var passwordWatcher = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(@NonNull Editable editable) {
                updateViews();
            }
        };
        editText1.addTextChangedListener(passwordWatcher);
        editText2.addTextChangedListener(passwordWatcher);

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
            requiresPasswordConfirmation = false;
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

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity());

        if (title != 0) {
            builder.setTitle(title);
        }

        builder.setView(dialogView);

        builder.setPositiveButton(
            getString(positive),
            (dialog, whichButton) -> {
                if (callback == null) {
                    return;
                }
                final @Nullable Editable editable = editText1.getText();
                if (editable == null) {
                    return;
                }
                if (checkboxText != 0) {
                    callback.onYes(tag, editable.toString(), checkBox.isChecked(), object);
                } else {
                    callback.onYes(tag, editable.toString(), false, object);
                }
            }
        );
        builder.setNegativeButton(
            getString(negative),
            (dialog, whichButton) -> {
                if (callback != null) {
                    callback.onNo(tag);
                }
            }
        );

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
        if (callback != null) {
            callback.onNo(this.getTag());
        }
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

        updateViews();

        ColorStateList colorStateList = DialogUtil.getButtonColorStateList(activity);

        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(colorStateList);
        alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(colorStateList);
    }

    private void updateViews() {
        var okButton = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        final TextInputEditText editText1 = alertDialog.findViewById(R.id.password1);
        final TextInputLayout editText1Layout = alertDialog.findViewById(R.id.password1layout);
        if (editText1Layout == null || editText1 == null || editText1.getText() == null) {
            return;
        }
        var password1 = editText1.getText().toString();

        if (requiresPasswordConfirmation) {
            final TextInputEditText editText2 = alertDialog.findViewById(R.id.password2);
            final TextInputLayout editText2Layout = alertDialog.findViewById(R.id.password2layout);
            if (editText2Layout == null || editText2 == null || editText2.getText() == null) {
                return;
            }
            var password2 = editText2.getText().toString();

            if (password1.length() < minLength) {
                editText1Layout.setError(null);
                editText2Layout.setError(null);
                okButton.setEnabled(false);
                return;
            }
            if (!password1.equals(password2)) {
                editText1Layout.setError(null);
                editText2Layout.setError(
                    password2.length() >= password1.length()
                        ? getString(R.string.passwords_dont_match)
                        : null
                );
                okButton.setEnabled(false);
                return;
            }

            editText1Layout.setError(null);
            editText2Layout.setError(null);
            okButton.setEnabled(true);
        } else {
            okButton.setEnabled(!password1.isEmpty());
        }
    }
}

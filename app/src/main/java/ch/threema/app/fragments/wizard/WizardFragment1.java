/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2025 Threema GmbH
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

package ch.threema.app.fragments.wizard;

import static ch.threema.app.threemasafe.ThreemaSafeServiceImpl.MAX_PW_LENGTH;
import static ch.threema.app.threemasafe.ThreemaSafeServiceImpl.MIN_PW_LENGTH;
import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

import android.os.Bundle;
import android.text.Editable;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputLayout;

import org.slf4j.Logger;

import java.util.Objects;

import androidx.annotation.NonNull;
import ch.threema.app.R;
import ch.threema.app.activities.wizard.WizardBaseActivity;
import ch.threema.app.activities.wizard.components.WizardButtonXml;
import ch.threema.app.threemasafe.ThreemaSafeAdvancedDialog;
import ch.threema.app.threemasafe.ThreemaSafeServerInfo;
import ch.threema.app.restrictions.AppRestrictionUtil;
import ch.threema.app.ui.SimpleTextWatcher;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.EditTextUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;

public class WizardFragment1 extends WizardFragment implements ThreemaSafeAdvancedDialog.WizardDialogCallback {
    private static final Logger logger = LoggingUtil.getThreemaLogger("WizardFragment1");
    public static final int PAGE_ID = 1;

    private static final String DIALOG_TAG_ADVANCED = "adv";

    private EditText password1, password2;
    private TextInputLayout password1layout, password2layout;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View rootView = Objects.requireNonNull(super.onCreateView(inflater, container, savedInstanceState));

        TextView title = rootView.findViewById(R.id.wizard_title);
        title.setText(R.string.threema_safe);

        // inflate content layout
        contentViewStub.setLayoutResource(R.layout.fragment_wizard1);
        contentViewStub.inflate();

        WizardFragment4.SettingsInterface callback = (WizardFragment4.SettingsInterface) requireActivity();

        this.password1 = rootView.findViewById(R.id.safe_password1);
        this.password2 = rootView.findViewById(R.id.safe_password2);
        this.password1layout = rootView.findViewById(R.id.password1layout);
        this.password2layout = rootView.findViewById(R.id.password2layout);

        if (!TestUtil.isEmptyOrNull(callback.getSafePassword())) {
            this.password1.setText(callback.getSafePassword());
            this.password2.setText(callback.getSafePassword());
        }

        this.password1.addTextChangedListener(new PasswordWatcher());
        this.password2.addTextChangedListener(new PasswordWatcher());
        this.password2.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                if (password1.getText() != null && password2.getText() != null) {
                    if (getPasswordOK(password1.getText().toString(), password2.getText().toString())) {
                        if (getActivity() != null && isAdded()) {
                            ((WizardBaseActivity) getActivity()).nextPage();
                        }
                        return true;
                    }
                }
            }
            return false;
        });

        final @NonNull WizardButtonXml advancedOptionsButtonCompose = rootView.findViewById(R.id.advanced_options_compose);
        if (ConfigUtils.isWorkRestricted() && (callback.getSafeForcePasswordEntry() || callback.getSafeSkipBackupPasswordEntry())) {
            advancedOptionsButtonCompose.setVisibility(View.GONE);
        } else {
            advancedOptionsButtonCompose.setVisibility(View.VISIBLE);
            advancedOptionsButtonCompose.setOnClickListener(v -> {
                ThreemaSafeAdvancedDialog dialog = ThreemaSafeAdvancedDialog.newInstance(callback.getSafeServerInfo(), false);
                dialog.setTargetFragment(this, 0);
                dialog.show(getFragmentManager(), DIALOG_TAG_ADVANCED);
            });
        }

        if (ConfigUtils.isWorkRestricted()) {
            // administrator forced use of threema safe. do not allow user to override advanced settings
            if (callback.getSafeForcePasswordEntry()) {
                TextView explainText = rootView.findViewById(R.id.safe_enable_explain);
                explainText.setText(R.string.safe_configure_choose_password_force);
            }

            // threema safe password entry disabled completely
            if (callback.getSafeSkipBackupPasswordEntry()) {
                this.password1layout.setVisibility(View.GONE);
                this.password2layout.setVisibility(View.GONE);
                rootView.findViewById(R.id.safe_enable_explain).setVisibility(View.GONE);
                rootView.findViewById(R.id.disabled_by_policy).setVisibility(View.VISIBLE);
            }
        }

        return rootView;
    }

    @Override
    protected int getAdditionalInfoText() {
        return R.string.safe_enable_explain;
    }

    @Override
    public void onYes(String tag, ThreemaSafeServerInfo serverInfo) {
        ((WizardFragment1.OnSettingsChangedListener) requireActivity()).onSafeServerInfoSet(serverInfo);
    }

    @Override
    public void onNo(String tag) {
        // no action
    }

    private class PasswordWatcher extends SimpleTextWatcher {
        private PasswordWatcher() {
        }

        @Override
        public void afterTextChanged(@NonNull Editable editable) {
            boolean passwordOk = getPasswordOK(password1.getText().toString(), password2.getText().toString());

            OnSettingsChangedListener listener = (WizardFragment1.OnSettingsChangedListener) getActivity();
            if (listener != null) {
                if (passwordOk) {
                    listener.onSafePasswordSet(editable.toString());
                } else {
                    listener.onSafePasswordSet(null);
                }
            }
        }
    }

    public static boolean getPasswordLengthOK(String text, int minLength) {
        return text != null && text.length() >= minLength && text.length() <= MAX_PW_LENGTH;
    }

    private boolean getPasswordOK(String password1Text, String password2Text) {
        boolean lengthOk = getPasswordLengthOK(password1Text, AppRestrictionUtil.isSafePasswordPatternSet(getContext()) ? 1 : MIN_PW_LENGTH);
        boolean passwordsMatch = password1Text != null && password1Text.equals(password2Text);

        if (!lengthOk && password1Text != null && !password1Text.isEmpty()) {
            this.password1layout.setError(getString(R.string.password_too_short_generic));
            this.password2layout.setError(null);
        } else {
            this.password1layout.setError(null);
            if (!TestUtil.isBlankOrNull(this.password2.getText())) {
                this.password2layout.setError(passwordsMatch ? null : getString(R.string.passwords_dont_match));
            } else {
                this.password2layout.setError(null);
            }
        }

        return (lengthOk && passwordsMatch);
    }

    public interface OnSettingsChangedListener {
        void onSafePasswordSet(String password);

        void onSafeServerInfoSet(ThreemaSafeServerInfo serverInfo);
    }

    @Override
    public void onResume() {
        super.onResume();
        initValues();
        if (this.password1 != null) {
            this.password1.requestFocus();
            EditTextUtil.showSoftKeyboard(this.password1);
        }
    }

    @Override
    public void onPause() {
        if (this.password1 != null) {
            this.password1.clearFocus();
            EditTextUtil.hideSoftKeyboard(this.password1);
        }
        super.onPause();
    }

    private void initValues() {
        if (isResumed()) {
            WizardFragment4.SettingsInterface callback = (WizardFragment4.SettingsInterface) requireActivity();
            if (callback.isSafeEnabled()) {
                password1.setText(callback.getSafePassword());
                password2.setText(callback.getSafePassword());
            }
        }
    }
}

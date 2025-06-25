/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2025 Threema GmbH
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

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import org.slf4j.Logger;

import java.util.Objects;

import ch.threema.app.R;
import ch.threema.app.activities.wizard.WizardBaseActivity;
import ch.threema.app.ui.SimpleTextWatcher;
import ch.threema.app.utils.EditTextUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.csp.ProtocolDefines;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class WizardFragment2 extends WizardFragment {
    private static final Logger logger = LoggingUtil.getThreemaLogger("WizardFragment2");
    private EditText nicknameText;
    public static final int PAGE_ID = 2;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = Objects.requireNonNull(super.onCreateView(inflater, container, savedInstanceState));

        WizardFragment4.SettingsInterface callback = (WizardFragment4.SettingsInterface) requireActivity();

        TextView title = rootView.findViewById(R.id.wizard_title);
        title.setText(R.string.new_wizard_choose_nickname);

        // inflate content layout
        contentViewStub.setLayoutResource(R.layout.fragment_wizard2);
        contentViewStub.inflate();

        nicknameText = rootView.findViewById(R.id.wizard_edit1);
        if (callback.isReadOnlyProfile()) {
            nicknameText.setEnabled(false);
            rootView.findViewById(R.id.disabled_by_policy).setVisibility(View.VISIBLE);
        } else {
            nicknameText.addTextChangedListener(new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    Activity activity = requireActivity();
                    if (activity.getCurrentFocus() == nicknameText) {
                        ((OnSettingsChangedListener) activity).onNicknameSet(s.toString());
                    }
                }
            });
            // TODO(ANDR-3180): Consolidate nickname length
            nicknameText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(ProtocolDefines.PUSH_FROM_LEN)});
        }
        this.nicknameText.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                if (getActivity() != null && isAdded()) {
                    ((WizardBaseActivity) getActivity()).nextPage();
                }
                return true;
            }
            return false;
        });

        return rootView;
    }

    @Override
    protected int getAdditionalInfoText() {
        return R.string.new_wizard_info_nickname;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);
    }

    public interface OnSettingsChangedListener {
        void onNicknameSet(String nickname);
    }

    @Override
    public void onResume() {
        super.onResume();
        new Handler(Looper.getMainLooper()).postDelayed(() -> RuntimeUtil.runOnUiThread(() -> {
            initValues();
            if (nicknameText != null) {
                nicknameText.requestFocus();
                EditTextUtil.showSoftKeyboard(nicknameText);
            }
        }), 50);
    }

    @Override
    public void onPause() {
        if (this.nicknameText != null) {
            this.nicknameText.clearFocus();
            EditTextUtil.hideSoftKeyboard(this.nicknameText);
        }
        super.onPause();
    }

    private void initValues() {
        if (isResumed()) {
            WizardFragment4.SettingsInterface callback = (WizardFragment4.SettingsInterface) requireActivity();
            String nickname = callback.getNickname();
            // If the nickname is longer than allowed, we increase the maximum length of the
            // nickname edit text because in this case the nickname comes from an external MDM as
            // there are no other possibilities to add such a long nickname.
            // Note: This is necessary to prevent a crash when calling 'setSelection'.
            // TODO(ANDR-3180): Consolidate nickname length
            if (nickname != null && nickname.length() > ProtocolDefines.PUSH_FROM_LEN) {
                nicknameText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(nickname.length())});
            }
            nicknameText.setText(nickname);
            if (!TestUtil.isEmptyOrNull(nickname)) {
                nicknameText.setSelection(nickname.length());
            }
        }
    }
}

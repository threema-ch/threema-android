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

package ch.threema.app.threemasafe;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.wizard.components.WizardButtonXml;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.dialogs.ThreemaDialogFragment;
import ch.threema.app.utils.AnimationUtil;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.EditTextUtil;
import ch.threema.base.ThreemaException;

public class ThreemaSafeAdvancedDialog extends ThreemaDialogFragment implements View.OnClickListener {

    private static final String ARG_SERVER_URL = "sU";
    private static final String ARG_PLAIN_STYLE = "pS";
    private static final String ARG_SERVER_USERNAME = "Un";
    private static final String ARG_SERVER_PASSWORD = "Sp";
    private static final String DIALOG_TAG_PROGRESS = "pr";

    private WizardDialogCallback callback;
    private Activity activity;
    private ThreemaSafeService threemaSafeService;
    private AlertDialog alertDialog;

    private ThreemaSafeServerInfo serverInfo;

    private @Nullable Button positiveButtonXml;
    private @Nullable WizardButtonXml positiveButtonCompose;
    private EditText serverUrlEditText, usernameEditText, passwordEditText;
    private LinearLayout serverContainer;
    private MaterialSwitch defaultServerSwitch;

    @NonNull
    public static ThreemaSafeAdvancedDialog newInstance(@NonNull ThreemaSafeServerInfo serverInfo, boolean plainStyle) {
        ThreemaSafeAdvancedDialog dialog = new ThreemaSafeAdvancedDialog();
        Bundle args = new Bundle();
        args.putString(ARG_SERVER_URL, serverInfo.getCustomServerName());
        args.putString(ARG_SERVER_USERNAME, serverInfo.getServerUsername());
        args.putString(ARG_SERVER_PASSWORD, serverInfo.getServerPassword());
        args.putBoolean(ARG_PLAIN_STYLE, plainStyle);
        dialog.setArguments(args);

        return dialog;
    }

    public interface WizardDialogCallback {
        void onYes(String tag, ThreemaSafeServerInfo serverInfo);

        void onNo(String tag);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            callback = (WizardDialogCallback) getTargetFragment();
        } catch (ClassCastException e) {
            //
        }

        // called from an activity rather than a fragment
        if (callback == null) {
            if (!(activity instanceof WizardDialogCallback)) {
                throw new ClassCastException("Calling fragment must implement WizardDialogCallback interface");
            }
            callback = (WizardDialogCallback) activity;
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
        serverInfo = new ThreemaSafeServerInfo();
        serverInfo.setCustomServerName(getArguments().getString(ARG_SERVER_URL));
        serverInfo.setServerUsername(getArguments().getString(ARG_SERVER_USERNAME));
        serverInfo.setServerPassword(getArguments().getString(ARG_SERVER_PASSWORD));
        boolean plainStyle = getArguments().getBoolean(ARG_PLAIN_STYLE);

        final View dialogView = activity.getLayoutInflater().inflate(plainStyle ? R.layout.dialog_safe_advanced : R.layout.dialog_wizard_safe_advanced, null);

        if (!plainStyle) {
            dialogView.findViewById(R.id.cancel_compose).setOnClickListener(v -> onCancel(null));
            positiveButtonCompose = dialogView.findViewById(R.id.ok_compose);
            positiveButtonCompose.setOnClickListener(v -> customOnClickedYes());
        }

        serverUrlEditText = dialogView.findViewById(R.id.safe_edit_server);
        serverContainer = dialogView.findViewById(R.id.safe_server_container);
        usernameEditText = dialogView.findViewById(R.id.safe_edit_username);
        passwordEditText = dialogView.findViewById(R.id.safe_edit_server_password);
        defaultServerSwitch = dialogView.findViewById(R.id.safe_switch_server);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity(), plainStyle ? getTheme() : R.style.Threema_Dialog_Wizard);
        builder.setView(dialogView);

        try {
            threemaSafeService = ThreemaApplication.getServiceManager().getThreemaSafeService();
        } catch (Exception e) {
            //
        }

        defaultServerSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateUI();
        });

        if (serverInfo.isDefaultServer()) {
            serverContainer.setVisibility(View.GONE);
        }

        serverUrlEditText.setText(serverInfo.isDefaultServer() ? "" : serverInfo.getCustomServerName());
        usernameEditText.setText(serverInfo.getServerUsername());
        passwordEditText.setText(serverInfo.getServerPassword());
        defaultServerSwitch.setChecked(serverInfo.isDefaultServer());

        updateUI();

        serverUrlEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePositiveButtonEnabledState();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        if (plainStyle) {
            builder.setTitle(R.string.safe_configure_choose_server);
            builder.setPositiveButton(getString(R.string.ok), null);
            builder.setNegativeButton(getString(R.string.cancel), null);
        } else {
            updatePositiveButtonEnabledState();
        }

        setCancelable(false);

        alertDialog = builder.create();

        return alertDialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        final @Nullable Button dialogPositiveButtonXml = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (dialogPositiveButtonXml != null) {
            this.positiveButtonXml = dialogPositiveButtonXml;
            this.positiveButtonXml.setOnClickListener(this);
            alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> onCancel(null));
            updatePositiveButtonEnabledState();
        } else {
            final @Nullable WizardButtonXml dialogPositiveButtonCompose = alertDialog.findViewById(R.id.ok_compose);
            final @Nullable WizardButtonXml dialogNegativeButtonCompose = alertDialog.findViewById(R.id.cancel_compose);
            if (dialogPositiveButtonCompose != null) {
                this.positiveButtonCompose = dialogPositiveButtonCompose;
                positiveButtonCompose.setOnClickListener(v -> customOnClickedYes());
            }
            if (dialogNegativeButtonCompose != null) {
                dialogNegativeButtonCompose.setOnClickListener(v -> onCancel(null));
            }
        }
    }

    private void updateUI() {
        updatePositiveButtonEnabledState();

        if (defaultServerSwitch.isChecked()) {
            if (serverContainer.getVisibility() == View.VISIBLE) {
                AnimationUtil.fadeViewVisibility(serverContainer, View.GONE);
            }
        } else {
            if (serverContainer.getVisibility() != View.VISIBLE) {
                AnimationUtil.fadeViewVisibility(serverContainer, View.VISIBLE);
            }
        }
    }

    private void updatePositiveButtonEnabledState() {
        if (serverUrlEditText != null && defaultServerSwitch != null) {
            final boolean isPositiveButtonEnabled = defaultServerSwitch.isChecked()
                || (serverUrlEditText.getText() != null && serverUrlEditText.getText().length() >= 9);
            if (positiveButtonXml != null) {
                positiveButtonXml.setEnabled(isPositiveButtonEnabled);
            } else if (positiveButtonCompose != null) {
                setPositiveButtonEnabled(isPositiveButtonEnabled);
            }
        }
    }

    private void setPositiveButtonEnabled(final boolean isEnabled) {
        if (positiveButtonCompose != null) {
            positiveButtonCompose.setButtonEnabled(isEnabled);
        }
    }

    @SuppressLint("StaticFieldLeak")
    private void testServer() {
        new AsyncTask<Void, Void, String>() {
            @Override
            protected void onPreExecute() {
                GenericProgressDialog.newInstance(R.string.safe_test_server, R.string.please_wait).show(getFragmentManager(), DIALOG_TAG_PROGRESS);
            }

            @Override
            protected String doInBackground(Void... voids) {
                try {
                    threemaSafeService.testServer(serverInfo);
                    return null;
                } catch (ThreemaException e) {
                    return e.getMessage();
                }
            }

            @Override
            protected void onPostExecute(String failureMessage) {
                DialogUtil.dismissDialog(getFragmentManager(), DIALOG_TAG_PROGRESS, true);

                if (failureMessage != null) {
                    Toast.makeText(getActivity(), getString(R.string.test_unsuccessful) + ": " + failureMessage, Toast.LENGTH_LONG).show();
                } else {
                    onYes();
                }

                updateUI();
            }
        }.execute();
    }

    private void onYes() {
        dismiss();
        callback.onYes(getTag(), serverInfo);
    }

    @Override
    public void onCancel(DialogInterface dialogInterface) {
        dismiss();
        callback.onNo(this.getTag());
    }

    @Override
    public void onClick(View v) {
        customOnClickedYes();
    }

    private void customOnClickedYes() {
        if (!defaultServerSwitch.isChecked()) {
            EditTextUtil.hideSoftKeyboard(serverUrlEditText);
            serverInfo.setCustomServerName(serverUrlEditText.getText().toString());
            serverInfo.setServerUsername(usernameEditText.getText().toString());
            serverInfo.setServerPassword(passwordEditText.getText().toString());
            testServer();
        } else {
            serverInfo = new ThreemaSafeServerInfo();
            onYes();
        }
    }
}

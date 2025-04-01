/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2024 Threema GmbH
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
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import org.slf4j.Logger;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.dialogs.SimpleStringAlertDialog;
import ch.threema.app.listeners.ThreemaSafeListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.ui.SilentSwitchCompat;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;

import static ch.threema.app.threemasafe.ThreemaSafeConfigureActivity.EXTRA_CHANGE_PASSWORD;

public class BackupThreemaSafeFragment extends Fragment implements GenericAlertDialog.DialogClickListener {
    private static final Logger logger = LoggingUtil.getThreemaLogger("BackupThreemaSafeFragment");

    private static final int REQUEST_CODE_SAFE_CONFIGURE = 22;
    private static final int REQUEST_CODE_SAFE_CHANGE_PASSWORD = 23;
    private static final String DIALOG_TAG_DELETING = "dts";
    private static final String DIALOG_TAG_DEACTIVATE_CONFIRM = "dcf";

    private View fragmentView;
    private PreferenceService preferenceService;
    private ThreemaSafeService threemaSafeService;
    private ExtendedFloatingActionButton floatingActionButton;
    private Button changePasswordButton;
    private SilentSwitchCompat enableSwitch;
    private View configLayout, explainLayout;

    private final ThreemaSafeListener threemaSafeListener = new ThreemaSafeListener() {
        @Override
        public void onBackupStatusChanged() {
            RuntimeUtil.runOnUiThread(() -> updateUI());
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);

        try {
            ServiceManager serviceManager = ThreemaApplication.getServiceManager();
            this.preferenceService = serviceManager.getPreferenceService();
            this.threemaSafeService = serviceManager.getThreemaSafeService();
        } catch (Exception e) {
            logger.error("Exception", e);
            getActivity().finish();
        }

        ListenerManager.threemaSafeListeners.add(this.threemaSafeListener);
    }

    @Override
    public void onDestroy() {
        ListenerManager.threemaSafeListeners.remove(this.threemaSafeListener);

        super.onDestroy();
    }

    @Override
    public void onDestroyView() {
        this.configLayout = null;
        this.explainLayout = null;
        this.floatingActionButton.setOnClickListener(null);
        this.floatingActionButton = null;
        this.changePasswordButton.setOnClickListener(null);
        this.changePasswordButton = null;
        this.enableSwitch.setOnCheckedChangeListener(null);
        this.enableSwitch = null;

        this.fragmentView = null;

        super.onDestroyView();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (this.fragmentView == null) {
            this.fragmentView = inflater.inflate(R.layout.fragment_backup_threema_safe, container, false);

            configLayout = fragmentView.findViewById(R.id.config_layout);
            explainLayout = fragmentView.findViewById(R.id.explain_layout);

            floatingActionButton = fragmentView.findViewById(R.id.floating);
            floatingActionButton.setOnClickListener(v -> {
                if (preferenceService.getThreemaSafeEnabled()) {
                    threemaSafeService.uploadNow(true);
                    threemaSafeService.reschedulePeriodicUpload();
                }
            });

            changePasswordButton = fragmentView.findViewById(R.id.threema_safe_change_password);
            changePasswordButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (preferenceService.getThreemaSafeEnabled()) {
                        threemaSafeService.unschedulePeriodicUpload();
                        Intent intent = new Intent(getActivity(), ThreemaSafeConfigureActivity.class);
                        intent.putExtra(EXTRA_CHANGE_PASSWORD, true);
                        startActivityForResult(intent, REQUEST_CODE_SAFE_CHANGE_PASSWORD);
                    }
                }
            });

            enableSwitch = fragmentView.findViewById(R.id.switch_button);
            enableSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (buttonView.isShown()) {
                        logger.debug("*** onCheckedChanged buttonView {} isChecked {}", buttonView.isChecked(), isChecked);
                        if (isChecked) {
                            startActivityForResult(new Intent(getActivity(), ThreemaSafeConfigureActivity.class), REQUEST_CODE_SAFE_CONFIGURE);
                        } else {
                            GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.safe_deactivate, R.string.safe_deactivate_explain, R.string.ok, R.string.cancel);
                            dialog.setTargetFragment(BackupThreemaSafeFragment.this, 0);
                            dialog.show(getFragmentManager(), DIALOG_TAG_DEACTIVATE_CONFIRM);
                        }
                    }
                }
            });

            fragmentView.findViewById(R.id.info).setOnClickListener(v -> onInfoButtonClicked(v));
            updateUI();
        }

        // adjust height of switch layout on top
        if (this.fragmentView != null) {
            FrameLayout switchLayout = this.fragmentView.findViewById(R.id.switch_frame);
            if (switchLayout != null) {
                ViewGroup.LayoutParams lp = switchLayout.getLayoutParams();
                lp.height = getResources().getDimensionPixelSize(R.dimen.web_sessions_switch_frame_height);
                switchLayout.setLayoutParams(lp);
                ((TextView) switchLayout.findViewById(R.id.switch_text)).setHeight(lp.height);
            }
        }

        return fragmentView;
    }

    private String getShortServerName() {
        if (getContext() != null) {
            if (preferenceService.getThreemaSafeServerInfo().isDefaultServer()) {
                return getString(R.string.safe_use_default_server);
            } else {
                return preferenceService.getThreemaSafeServerInfo().getHostName();
            }
        }
        return getString(R.string.error);
    }

    @UiThread
    private void updateUI() {
        if (preferenceService.getThreemaSafeEnabled()) {
            // Threema safe is already configured
            ((TextView) fragmentView.findViewById(R.id.server_text)).setText(getShortServerName());
            ((TextView) fragmentView.findViewById(R.id.server_size)).setText(Formatter.formatFileSize(getActivity(), preferenceService.getThreemaSafeServerMaxUploadSize()));
            ((TextView) fragmentView.findViewById(R.id.server_retention)).setText(String.format(getString(R.string.number_of_days), preferenceService.getThreemaSafeServerRetention()));

            TextView backupResult = fragmentView.findViewById(R.id.backup_result);
            if (preferenceService.getThreemaSafeBackupDate() != null) {
                ((TextView) fragmentView.findViewById(R.id.backup_date)).setText(LocaleUtil.formatTimeStampString(getActivity(), preferenceService.getThreemaSafeBackupDate().getTime(), true));
                ((TextView) fragmentView.findViewById(R.id.backup_size)).setText(Formatter.formatFileSize(getActivity(), preferenceService.getThreemaSafeBackupSize()));
                backupResult.setText(getResources().getStringArray(R.array.threema_safe_error)[preferenceService.getThreemaSafeErrorCode()]);
                if (preferenceService.getThreemaSafeErrorCode() == ThreemaSafeService.ERROR_CODE_OK) {
                    backupResult.setTextColor(getResources().getColor(R.color.material_green));
                } else {
                    backupResult.setTextColor(getResources().getColor(R.color.material_red));
                }
                changePasswordButton.setVisibility(View.VISIBLE);
            } else {
                ((TextView) fragmentView.findViewById(R.id.backup_date)).setText("-");
                ((TextView) fragmentView.findViewById(R.id.backup_size)).setText("-");
                if (preferenceService.getThreemaSafeErrorCode() != ThreemaSafeService.ERROR_CODE_OK) {
                    backupResult.setText(getResources().getStringArray(R.array.threema_safe_error)[preferenceService.getThreemaSafeErrorCode()]);
                    backupResult.setTextColor(getResources().getColor(R.color.material_red));
                } else {
                    backupResult.setText("-");
                    backupResult.setTextColor(ConfigUtils.getColorFromAttribute(getContext(), R.attr.colorOnBackground));
                }
                changePasswordButton.setVisibility(View.INVISIBLE);
            }

            configLayout.setVisibility(View.VISIBLE);
            explainLayout.setVisibility(View.GONE);
            enableSwitch.setCheckedSilent(true);
            floatingActionButton.setVisibility(View.VISIBLE);
        } else {
            configLayout.setVisibility(View.GONE);
            explainLayout.setVisibility(View.VISIBLE);
            enableSwitch.setCheckedSilent(false);
            floatingActionButton.setVisibility(View.GONE);
        }

        if (ConfigUtils.isWorkRestricted()) {
            if (ThreemaSafeMDMConfig.getInstance().isBackupForced()) {
                enableSwitch.setEnabled(false);
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    private void disableSafe() {
        new AsyncTask<Void, Void, String>() {
            @Override
            protected void onPreExecute() {
                GenericProgressDialog.newInstance(R.string.safe_deleting, R.string.please_wait).show(getFragmentManager(), DIALOG_TAG_DELETING);
            }

            @Override
            protected String doInBackground(Void... voids) {
                try {
                    threemaSafeService.deleteBackup();
                } catch (ThreemaException e) {
                    return e.getMessage();
                }
                return null;
            }

            @Override
            protected void onPostExecute(String string) {
                String message;
                DialogUtil.dismissDialog(getFragmentManager(), DIALOG_TAG_DELETING, true);
                threemaSafeService.setEnabled(false);
                if (string != null) {
                    message = String.format(getString(R.string.safe_delete_error), string);
                } else {
                    message = getString(R.string.safe_delete_success);
                }
                Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
                updateUI();
            }
        }.execute();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        updateUI();
    }

    @Override
    public void onYes(String tag, Object data) {
        disableSafe();
    }

    @Override
    public void onNo(String tag, Object data) {
        enableSwitch.setCheckedSilent(preferenceService.getThreemaSafeEnabled());
    }

    @UiThread
    private void onInfoButtonClicked(View v) {
        SimpleStringAlertDialog.newInstance(R.string.threema_safe, R.string.safe_enable_explain).show(getFragmentManager(), "tse");
    }
}

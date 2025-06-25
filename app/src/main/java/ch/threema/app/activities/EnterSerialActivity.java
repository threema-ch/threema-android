/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

package ch.threema.app.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.method.LinkMovementMethod;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.text.HtmlCompat;
import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.wizard.components.WizardButtonXml;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.restrictions.AppRestrictionService;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.services.license.LicenseService;
import ch.threema.app.services.license.LicenseServiceUser;
import ch.threema.app.services.license.SerialCredentials;
import ch.threema.app.services.license.UserCredentials;
import ch.threema.app.restrictions.AppRestrictionUtil;
import ch.threema.app.ui.InsetSides;
import ch.threema.app.ui.SimpleTextWatcher;
import ch.threema.app.ui.SpacingValues;
import ch.threema.app.ui.ViewExtensionsKt;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.EditTextUtil;
import ch.threema.app.utils.LazyProperty;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.executor.BackgroundExecutor;
import ch.threema.app.utils.executor.BackgroundTask;
import ch.threema.base.utils.LoggingUtil;

import static ch.threema.app.startup.AppStartupUtilKt.finishAndRestartLaterIfNotReady;
import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

// this should NOT extend ThreemaToolbarActivity
public class EnterSerialActivity extends ThreemaActivity {
    private static final Logger logger = LoggingUtil.getThreemaLogger("EnterSerialActivity");

    private static final String BUNDLE_PASSWORD = "bupw";
    private static final String BUNDLE_LICENSE_KEY = "bulk";
    private static final String BUNDLE_SERVER = "busv";
    private static final String DIALOG_TAG_CHECKING = "check";
    private TextView stateTextView = null;
    private EditText licenseKeyOrUsernameText, passwordText, serverText;
    private MaterialButton unlockButton;
    private WizardButtonXml loginButtonCompose;
    private LicenseService licenseService;
    private PreferenceService preferenceService;

    private final LazyProperty<BackgroundExecutor> backgroundExecutor = new LazyProperty<>(BackgroundExecutor::new);

    // We need to use getResources().getIdentifier(...) because of flavor specific layout files for this fragment
    @SuppressLint("DiscouragedApi")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);
        if (finishAndRestartLaterIfNotReady(this)) {
            return;
        }

        if (!ConfigUtils.isSerialLicensed()) {
            finish();
            return;
        }

        setContentView(R.layout.activity_enter_serial);

        ServiceManager serviceManager = ThreemaApplication.requireServiceManager();
        licenseService = serviceManager.getLicenseService();
        preferenceService = serviceManager.getPreferenceService();

        checkForValidCredentialsInBackground();

        ViewExtensionsKt.applyDeviceInsetsAsPadding(
            findViewById(R.id.layout_parent_top),
            InsetSides.vertical(),
            SpacingValues.symmetric(R.dimen.wizard_contents_padding, R.dimen.wizard_contents_padding_horizontal)
        );

        stateTextView = findViewById(R.id.unlock_state);
        licenseKeyOrUsernameText = findViewById(R.id.license_key);
        passwordText = findViewById(getResources().getIdentifier("password", "id", getPackageName()));
        serverText = findViewById(getResources().getIdentifier("server", "id", getPackageName()));

        TextView enterKeyExplainText = findViewById(R.id.layout_top);
        enterKeyExplainText.setText(HtmlCompat.fromHtml(getString(R.string.flavored__enter_serial_body), HtmlCompat.FROM_HTML_MODE_COMPACT));
        enterKeyExplainText.setClickable(true);
        enterKeyExplainText.setMovementMethod(LinkMovementMethod.getInstance());

        if (!ConfigUtils.isWorkBuild() && !ConfigUtils.isOnPremBuild()) {
            setupForShopBuild();
        } else {
            setupForWorkBuild();
        }

        handleUrlIntent(getIntent());
    }

    private void checkForValidCredentialsInBackground() {
        // In case there are credentials, we can validate them and skip this activity so that the
        // user does not have to enter them again.
        if (licenseService.hasCredentials()) {
            backgroundExecutor.get().execute(new BackgroundTask<Boolean>() {
                @Override
                public void runBefore() {
                    // Nothing to do
                }

                @Override
                public Boolean runInBackground() {
                    return licenseService.validate(false) == null;
                }

                @Override
                public void runAfter(Boolean result) {
                    if (Boolean.TRUE.equals(result)) {
                        logger.info("Credentials are available and valid");
                        ConfigUtils.recreateActivity(EnterSerialActivity.this);
                    }
                }
            });
        }
    }

    private void setupForShopBuild() {
        licenseKeyOrUsernameText.addTextChangedListener(new PasswordWatcher());
        licenseKeyOrUsernameText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        licenseKeyOrUsernameText.setFilters(new InputFilter[]{new InputFilter.AllCaps(), new InputFilter.LengthFilter(11)});
        licenseKeyOrUsernameText.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                if (licenseKeyOrUsernameText.getText().length() == 11) {
                    doUnlock();
                }
                return true;
            }
            return false;
        });
        unlockButton = findViewById(R.id.unlock_button);
        unlockButton.setOnClickListener(v -> doUnlock());

        this.setLoginButtonEnabled(false);
    }

    // We need to use getResources().getIdentifier(...) because of flavor specific layout files for this fragment
    @SuppressLint("DiscouragedApi")
    private void setupForWorkBuild() {
        licenseKeyOrUsernameText.addTextChangedListener(new TextChangeWatcher());
        passwordText.addTextChangedListener(new TextChangeWatcher());

        loginButtonCompose = findViewById(getResources().getIdentifier("unlock_button_work_compose", "id", getPackageName()));
        loginButtonCompose.setOnClickListener(v -> doUnlock());

        TextView lostCredentialsHelp = findViewById(getResources().getIdentifier("work_lost_credential_help", "id", getPackageName()));
        lostCredentialsHelp.setText(getString(R.string.work_lost_credentials_help));

        // Always enable for work build
        setLoginButtonEnabled(true);
    }

    private void handleUrlIntent(@Nullable Intent intent) {
        String scheme = null;
        Uri data = null;
        if (intent != null) {
            data = intent.getData();
            if (data != null) {
                scheme = data.getScheme();
            }
        }

        if (!ConfigUtils.isSerialLicenseValid()) {
            if (scheme != null) {
                if (scheme.startsWith(BuildConfig.uriScheme)) {
                    parseUrlAndCheck(data);
                } else if (scheme.startsWith("https")) {
                    String path = data.getPath();

                    if (path != null && path.length() > 1) {
                        path = path.substring(1);
                        if (path.startsWith("license")) {
                            parseUrlAndCheck(data);
                        }
                    }
                }
            }

            if (ConfigUtils.isWorkRestricted()) {
                String username = AppRestrictionUtil.getStringRestriction(getString(R.string.restriction__license_username));
                String password = AppRestrictionUtil.getStringRestriction(getString(R.string.restriction__license_password));
                String server = AppRestrictionUtil.getStringRestriction(getString(R.string.restriction__onprem_server));

                if (!TestUtil.isEmptyOrNull(username) && !TestUtil.isEmptyOrNull(password)) {
                    check(new UserCredentials(username, password), server);
                }
            }
        } else {
            // We get here if called from url intent and we're already licensed
            if (scheme != null) {
                Toast.makeText(this, R.string.already_licensed, Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void setLoginButtonEnabled(final boolean isEnabled) {
        if (!ConfigUtils.isWorkBuild() && !ConfigUtils.isOnPremBuild()) {
            if (this.unlockButton != null) {
                unlockButton.setClickable(isEnabled);
                unlockButton.setEnabled(isEnabled);
            }
        } else if (this.loginButtonCompose != null) {
            loginButtonCompose.setButtonEnabled(isEnabled);
        }
    }

    private void parseUrlAndCheck(@NonNull Uri data) {
        String query = data.getQuery();
        if (!TestUtil.isEmptyOrNull(query)) {
            if (licenseService instanceof LicenseServiceUser) {
                parseWorkLicense(data);
            } else {
                parseConsumerLicense(data);
            }
        }
    }

    private void parseConsumerLicense(@NonNull Uri data) {
        final String key = data.getQueryParameter("key");
        if (!TestUtil.isEmptyOrNull(key)) {
            check(new SerialCredentials(key), null);
        }
    }

    private void parseWorkLicense(@NonNull Uri data) {
        final String username = data.getQueryParameter("username");
        final String password = data.getQueryParameter("password");
        final String server = data.getQueryParameter("server");

        if (ConfigUtils.isOnPremBuild()) {
            if (!TestUtil.isEmptyOrNull(username) && !TestUtil.isEmptyOrNull(password) && !TestUtil.isEmptyOrNull(server)) {
                check(new UserCredentials(username, password), server);
            } else {
                licenseKeyOrUsernameText.setText(username);
                passwordText.setText(password);
                serverText.setText(server);
            }
        } else {
            if (!TestUtil.isEmptyOrNull(username) && !TestUtil.isEmptyOrNull(password)) {
                check(new UserCredentials(username, password), null);
            } else {
                licenseKeyOrUsernameText.setText(username);
                passwordText.setText(password);
            }
        }
    }

    private void doUnlock() {
        // hide keyboard to make error message visible on low resolution displays
        EditTextUtil.hideSoftKeyboard(this.licenseKeyOrUsernameText);

        this.setLoginButtonEnabled(false);

        if (ConfigUtils.isOnPremBuild()) {
            if (!TestUtil.isEmptyOrNull(this.licenseKeyOrUsernameText.getText().toString()) && !TestUtil.isEmptyOrNull(this.passwordText.getText().toString()) && !TestUtil.isEmptyOrNull(this.serverText.getText().toString())) {
                this.check(new UserCredentials(this.licenseKeyOrUsernameText.getText().toString(), this.passwordText.getText().toString()), this.serverText.getText().toString());
            } else {
                this.setLoginButtonEnabled(true);
                this.stateTextView.setText(getString(R.string.invalid_input));
            }
        } else if (ConfigUtils.isWorkBuild()) {
            if (!TestUtil.isEmptyOrNull(this.licenseKeyOrUsernameText.getText().toString()) && !TestUtil.isEmptyOrNull(this.passwordText.getText().toString())) {
                this.check(new UserCredentials(this.licenseKeyOrUsernameText.getText().toString(), this.passwordText.getText().toString()), null);
            } else {
                this.setLoginButtonEnabled(true);
                this.stateTextView.setText(getString(R.string.invalid_input));
            }
        } else {
            this.check(new SerialCredentials(this.licenseKeyOrUsernameText.getText().toString()), null);
        }
    }

    private class PasswordWatcher extends SimpleTextWatcher {
        @Override
        public void afterTextChanged(Editable s) {
            String initial = s.toString();
            String processed = initial.replaceAll("[^a-zA-Z0-9]", "");
            processed = processed.replaceAll("([a-zA-Z0-9]{5})(?=[a-zA-Z0-9])", "$1-");

            if (!initial.equals(processed)) {
                s.replace(0, initial.length(), processed);
            }

            //enable login only if the length of the key is 11 chars
            setLoginButtonEnabled(s.length() == 11);
        }
    }

    public class TextChangeWatcher extends SimpleTextWatcher {
        @Override
        public void afterTextChanged(Editable s) {
            if (stateTextView != null) {
                stateTextView.setText("");
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        if (licenseKeyOrUsernameText != null && !TestUtil.isBlankOrNull(licenseKeyOrUsernameText.getText())) {
            outState.putString(BUNDLE_LICENSE_KEY, licenseKeyOrUsernameText.getText().toString());
        }

        if (passwordText != null && !TestUtil.isBlankOrNull(passwordText.getText())) {
            outState.putString(BUNDLE_PASSWORD, passwordText.getText().toString());
        }

        if (serverText != null && !TestUtil.isBlankOrNull(serverText.getText())) {
            outState.putString(BUNDLE_SERVER, serverText.getText().toString());
        }
    }

    @SuppressLint("StaticFieldLeak")
    private void check(final LicenseService.Credentials credentials, String onPremServer) {
        if (ConfigUtils.isOnPremBuild()) {
            if (onPremServer != null) {
                if (!onPremServer.startsWith("https://")) {
                    onPremServer = "https://" + onPremServer;
                }

                if (!onPremServer.endsWith(".oppf")) {
                    // Automatically expand hostnames to default provisioning URL
                    onPremServer += "/prov/config.oppf";
                }
            }
            preferenceService.setOnPremServer(onPremServer);
            preferenceService.setLicenseUsername(((UserCredentials) credentials).username);
            preferenceService.setLicensePassword(((UserCredentials) credentials).password);
        }

        new AsyncTask<Void, Void, String>() {
            @Override
            protected void onPreExecute() {
                GenericProgressDialog.newInstance(R.string.flavored__checking_serial, R.string.please_wait).show(getSupportFragmentManager(), DIALOG_TAG_CHECKING);
            }

            @Override
            protected String doInBackground(Void... voids) {
                String error = getString(R.string.error);
                try {
                    error = licenseService.validate(credentials);
                    if (error == null) {
                        // validated
                        if (ConfigUtils.isWorkBuild()) {
                            AppRestrictionService.getInstance()
                                .fetchAndStoreWorkMDMSettings(
                                    ThreemaApplication.getServiceManager().getAPIConnector(),
                                    (UserCredentials) credentials
                                );
                        }
                    }
                } catch (Exception e) {
                    logger.error("Exception", e);
                }
                return error;
            }

            @Override
            protected void onPostExecute(String error) {
                setLoginButtonEnabled(true);
                DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_CHECKING, true);
                if (error == null) {
                    ConfigUtils.recreateActivity(EnterSerialActivity.this);
                } else {
                    changeState(error);
                }
            }
        }.execute();
    }

    private void changeState(String state) {
        this.stateTextView.setText(state);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        // We override this method to avoid restarting the entire
        // activity when the keyboard is opened or orientation changes
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        handleUrlIntent(intent);
    }
}

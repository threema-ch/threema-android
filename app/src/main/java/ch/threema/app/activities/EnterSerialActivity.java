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
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.text.HtmlCompat;
import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.activities.wizard.components.WizardButtonXml;
import ch.threema.app.di.DependencyContainer;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.restrictions.AppRestrictionService;
import ch.threema.app.services.license.LicenseService;
import ch.threema.app.services.license.LicenseServiceUser;
import ch.threema.domain.models.SerialCredentials;
import ch.threema.domain.models.UserCredentials;
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
import ch.threema.domain.models.LicenseCredentials;

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

    @NonNull
    private final DependencyContainer dependencies = KoinJavaComponent.get(DependencyContainer.class);

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

        // Workaround to fix the prefix in the TextInputLayout not being aligned correctly when the phones font size changes
        // Open Issue: https://github.com/material-components/material-components-android/issues/773
        final @Nullable TextInputLayout serverContainer = findViewById(getResources().getIdentifier("server_container", "id", getPackageName()));
        if (serverContainer != null) {
            serverContainer.getPrefixTextView().setLayoutParams(
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
            );
            serverContainer.getPrefixTextView().setGravity(Gravity.CENTER);
        }

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
        if (dependencies.getLicenseService().hasCredentials()) {
            backgroundExecutor.get().execute(new BackgroundTask<Boolean>() {
                @Override
                public void runBefore() {
                    // Nothing to do
                }

                @Override
                public Boolean runInBackground() {
                    return dependencies.getLicenseService().validate(false) == null;
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

    @SuppressLint("DiscouragedApi")
    private void setupForWorkBuild() {
        licenseKeyOrUsernameText.addTextChangedListener(new TextChangeWatcher());
        passwordText.addTextChangedListener(new TextChangeWatcher());

        // We need to use getResources().getIdentifier(...) because of flavor specific layout files for this fragment
        loginButtonCompose = findViewById(getResources().getIdentifier("unlock_button_work_compose", "id", getPackageName()));
        loginButtonCompose.setOnClickListener(v -> doUnlock());

        // We need to use getResources().getIdentifier(...) because of flavor specific layout files for this fragment
        TextView lostCredentialsHelp = findViewById(getResources().getIdentifier("work_lost_credential_help", "id", getPackageName()));
        lostCredentialsHelp.setText(getString(R.string.work_lost_credentials_help));

        // Always enable for work build
        setLoginButtonEnabled(true);

        // Disable the edit texts based on whether there are unchangeable preset values that must be
        // used.
        String configuredUsername = getConfiguredUsername();
        if (configuredUsername != null) {
            licenseKeyOrUsernameText.setText(configuredUsername);
            licenseKeyOrUsernameText.setEnabled(false);
        }

        String configuredPassword = getConfiguredPassword();
        if (configuredPassword != null) {
            passwordText.setEnabled(false);
        }

        if (ConfigUtils.isOnPremBuild()) {
            String configuredServerUrl = getConfiguredOnPremServerUrl();
            if (configuredServerUrl != null) {
                serverText.setText(getBaseUrl(configuredServerUrl));
                serverText.setEnabled(false);
            }

            if (ConfigUtils.isWhitelabelOnPremBuild(this) && configuredServerUrl == null) {
                // In case of a whitelabel build without pre configured server url, we do not want
                // the server url to be edited even if there is no configured server url. App setup
                // won't be possible.
                serverText.setEnabled(false);
                setLoginButtonEnabled(false);
                onMissingPresetUrl();
            }
        }
    }

    private void handleUrlIntent(@Nullable Intent intent) {
        // In case the activation link is not available or in the wrong format, it will be null.
        Uri activationLink = getActivationLink(intent);

        if (ConfigUtils.isSerialLicenseValid()) {
            if (activationLink != null) {
                // We inform the user only if there was an active attempt to re-license the app.
                Toast.makeText(this, R.string.already_licensed, Toast.LENGTH_LONG).show();
            }
            finish();
            return;
        }

        if (activationLink != null) {
            // In case there is an activation link that could be checked, we parse it, combine it
            // with mdm values (if work) and check the resulting credentials.
            checkActivationLinkAndMdm(activationLink);
        } else if (ConfigUtils.isWorkRestricted()) {
            // Otherwise we just check whether we have all the information solely from mdm config.
            String username = getConfiguredUsername();
            String password = getConfiguredPassword();

            if (ConfigUtils.isOnPremBuild()) {
                String server = getConfiguredOnPremServerUrl();

                if (!TestUtil.isEmptyOrNull(username) && !TestUtil.isEmptyOrNull(password) && !TestUtil.isEmptyOrNull(server)) {
                    check(new UserCredentials(username, password), server);
                }
            } else {
                if (!TestUtil.isEmptyOrNull(username) && !TestUtil.isEmptyOrNull(password)) {
                    check(new UserCredentials(username, password), null);
                }
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

    private void checkActivationLinkAndMdm(@NonNull Uri activationLink) {
        String query = activationLink.getQuery();
        if (query != null && !query.isEmpty()) {
            if (dependencies.getLicenseService() instanceof LicenseServiceUser) {
                checkWorkActivationLinkAndMdm(activationLink);
            } else {
                checkPrivateActivationLink(activationLink);
            }
        }
    }

    private void checkPrivateActivationLink(@NonNull Uri data) {
        final String key = data.getQueryParameter("key");
        if (!TestUtil.isEmptyOrNull(key)) {
            check(new SerialCredentials(key), null);
        }
    }

    private void checkWorkActivationLinkAndMdm(@NonNull Uri data) {
        final String intentUsername = getIntentUsername(data);
        final String intentPassword = getIntentPassword(data);
        final String intentServerUrl = getIntentServerUrl(data);

        final String configuredUsername = getConfiguredUsername();
        final String configuredPassword = getConfiguredPassword();
        final String configuredServerUrl = getConfiguredOnPremServerUrl();

        final String effectiveUsername = configuredUsername != null ? configuredUsername : intentUsername;
        final String effectivePassword = configuredPassword != null ? configuredPassword : intentPassword;
        final String effectiveServerUrl;

        if (ConfigUtils.isWhitelabelOnPremBuild(this)) {
            // Assert that we have a server url on the whitelabel build. If we don't, we abort
            // parsing the uri.
            if (configuredServerUrl == null) {
                return;
            }

            // Check that the intent server url matches the configured server url
            if (intentServerUrl != null && !intentServerUrl.isBlank()) {
                if (!configuredServerUrl.equals(intentServerUrl)) {
                    onIntentServerUrlMismatch();
                    return;
                }
            }

            // The effective server url is always the configured server url on whitelabel builds.
            effectiveServerUrl = configuredServerUrl;
        } else if (ConfigUtils.isOnPremBuild()) {
            // Check that both server urls equal if they are both defined
            if (configuredServerUrl != null && intentServerUrl != null && !configuredServerUrl.equals(intentServerUrl)) {
                onIntentServerUrlMismatch();
                return;
            }

            // If there is a configured server url, use it. Otherwise try the intent server url
            effectiveServerUrl = configuredServerUrl != null ? configuredServerUrl : intentServerUrl;
        } else {
            // On non-onprem builds we never use a server url
            effectiveServerUrl = null;
        }

        // Pre-fill the available credentials into the edit texts.
        licenseKeyOrUsernameText.setText(effectiveUsername);
        // We must not display the password that has been set by mdm. However, we can show
        // the password provided by the activation link.
        if (configuredPassword == null) {
            passwordText.setText(intentPassword);
        }

        // Check the credentials if available
        if (ConfigUtils.isOnPremBuild()) {
            // Also show the server url if available
            serverText.setText(effectiveServerUrl != null ? getBaseUrl(effectiveServerUrl) : null);

            // Check license if credentials and server url are available
            if (!TestUtil.isEmptyOrNull(effectiveUsername) && !TestUtil.isEmptyOrNull(effectivePassword) && !TestUtil.isEmptyOrNull(effectiveServerUrl)) {
                check(new UserCredentials(effectiveUsername, effectivePassword), effectiveServerUrl);
            }
        } else {
            // Check license if credentials are available
            if (!TestUtil.isEmptyOrNull(effectiveUsername) && !TestUtil.isEmptyOrNull(effectivePassword)) {
                check(new UserCredentials(effectiveUsername, effectivePassword), null);
            }
        }
    }

    private void doUnlock() {
        // hide keyboard to make error message visible on low resolution displays
        EditTextUtil.hideSoftKeyboard(this.licenseKeyOrUsernameText);

        this.setLoginButtonEnabled(false);

        if (ConfigUtils.isOnPremBuild()) {
            String configuredUsername = getConfiguredUsername();
            String configuredPassword = getConfiguredPassword();
            String configuredServerUrl = getConfiguredOnPremServerUrl();

            String effectiveUsername = configuredUsername != null ? configuredUsername : licenseKeyOrUsernameText.getText().toString();
            String effectivePassword = configuredPassword != null ? configuredPassword : passwordText.getText().toString();
            String effectiveServerUrl = configuredServerUrl != null ? configuredServerUrl : serverText.getText().toString();

            if (!TestUtil.isEmptyOrNull(effectiveUsername) && !TestUtil.isEmptyOrNull(effectivePassword) && !TestUtil.isEmptyOrNull(effectiveServerUrl)) {
                this.check(new UserCredentials(effectiveUsername, effectivePassword), effectiveServerUrl);
            } else {
                this.setLoginButtonEnabled(true);
                this.stateTextView.setText(getString(R.string.invalid_input));
            }
        } else if (ConfigUtils.isWorkBuild()) {
            String configuredUsername = getConfiguredUsername();
            String configuredPassword = getConfiguredPassword();

            String effectiveUsername = configuredUsername != null ? configuredUsername : licenseKeyOrUsernameText.getText().toString();
            String effectivePassword = configuredPassword != null ? configuredPassword : passwordText.getText().toString();

            if (!TestUtil.isEmptyOrNull(effectiveUsername) && !TestUtil.isEmptyOrNull(effectivePassword)) {
                this.check(new UserCredentials(effectiveUsername, effectivePassword), null);
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
        public void afterTextChanged(@NonNull Editable editable) {
            String initial = editable.toString();
            String processed = initial.replaceAll("[^a-zA-Z0-9]", "");
            processed = processed.replaceAll("([a-zA-Z0-9]{5})(?=[a-zA-Z0-9])", "$1-");

            if (!initial.equals(processed)) {
                editable.replace(0, initial.length(), processed);
            }

            //enable login only if the length of the key is 11 chars
            setLoginButtonEnabled(editable.length() == 11);
        }
    }

    public class TextChangeWatcher extends SimpleTextWatcher {
        @Override
        public void afterTextChanged(@NonNull Editable editable) {
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
    private void check(final LicenseCredentials credentials, String onPremServer) {
        if (ConfigUtils.isOnPremBuild()) {
            if (onPremServer != null) {
                onPremServer = getUrlToOppf(onPremServer);
            }
            var preferenceService = dependencies.getPreferenceService();
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
                    LicenseService licenseService = dependencies.getLicenseService();
                    error = licenseService.validate(credentials);
                    if (error == null) {
                        // validated
                        if (ConfigUtils.isWorkBuild()) {
                            AppRestrictionService.getInstance()
                                .fetchAndStoreWorkMDMSettings(
                                    dependencies.getApiConnector(),
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

    /**
     * Shows an error to the user that the provided server url in the activation link does not match
     * the requirements, i.e., either the server url set by mdm (onprem) or the pre-configured
     * server url (whitelabel onprem).
     */
    private void onIntentServerUrlMismatch() {
        logger.error("The intent's server url does not match the requirements");
        changeState(getString(R.string.error_preset_onprem_url_mismatch_intent));
    }

    /**
     * Shows an error to the user that there is no pre-configured server url. This can happen when
     * a whitelabel build does not have the preconfigured url set.
     */
    private void onMissingPresetUrl() {
        logger.error("The preset server url is missing for a whitelabel build");
        changeState("No preset OPPF Url");
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

    @Nullable
    private String getPresetOnPremServerUrlIfWhiteLabeled() {
        //noinspection ConstantValue
        return ConfigUtils.isWhitelabelOnPremBuild(this) && BuildConfig.PRESET_OPPF_URL != null
            ? BuildConfig.PRESET_OPPF_URL
            : null;
    }

    @NonNull
    private String getUrlToOppf(@NonNull String url) {
        if (!url.startsWith("https://")) {
            url = "https://" + url;
        }

        if (!url.endsWith(".oppf")) {
            // Automatically expand hostnames to default provisioning URL
            url += "/prov/config.oppf";
        }

        return url;
    }

    @NonNull
    private String getBaseUrl(@NonNull String url) {
        return url
            .replace("https://", "")
            .replace("/prov/config.oppf", "");
    }

    @Nullable
    private Uri getActivationLink(@Nullable Intent intent) {
        if (intent == null) {
            return null;
        }

        Uri data = intent.getData();
        String scheme = data != null ? data.getScheme() : null;
        if (scheme == null) {
            return null;
        }

        if (scheme.startsWith(BuildConfig.uriScheme)) {
            return data;
        } else if (scheme.startsWith("https")) {
            String path = data.getPath();
            if (path == null || path.isEmpty()) {
                return null;
            }

            return path.startsWith("/license") ? data : null;
        } else {
            return null;
        }
    }

    /**
     * Get the username from the uri. Returns null if no username is present.
     */
    @Nullable
    private String getIntentUsername(@NonNull Uri uri) {
        return uri.getQueryParameter("username");
    }

    /**
     * Get the password from the uri. Returns null if no password is present.
     */
    @Nullable
    private String getIntentPassword(@NonNull Uri uri) {
        return uri.getQueryParameter("password");
    }

    /**
     * Get the server url and make it point directly to the oppf if possible. Returns null if no
     * server url is set.
     */
    @Nullable
    private String getIntentServerUrl(@NonNull Uri uri) {
        final String serverUrl = uri.getQueryParameter("server");
        return serverUrl != null ? getUrlToOppf(serverUrl) : null;
    }

    /**
     * Get the username configured via mdm. If no username is configured via mdm, null is returned.
     */
    @Nullable
    private String getConfiguredUsername() {
        return AppRestrictionUtil.getStringRestriction(getString(R.string.restriction__license_username));
    }

    /**
     * Get the password configured via mdm. If no password is configured via mdm, null is returned.
     */
    @Nullable
    private String getConfiguredPassword() {
        return AppRestrictionUtil.getStringRestriction(getString(R.string.restriction__license_password));
    }

    /**
     * Get the configure onprem server url and make it point to the oppf file if possible. On
     * whitelabel builds this is the pre-configured url, on normal onprem builds it is the mdm
     * defined server url or null. On non-onprem builds, null is returned.
     */
    @Nullable
    private String getConfiguredOnPremServerUrl() {
        if (!ConfigUtils.isOnPremBuild()) {
            return null;
        }

        final String serverUrl = ConfigUtils.isWhitelabelOnPremBuild(this)
            ? getPresetOnPremServerUrlIfWhiteLabeled()
            : AppRestrictionUtil.getStringRestriction(getString(R.string.restriction__onprem_server));

        return serverUrl != null ? getUrlToOppf(serverUrl) : null;
    }

    @NonNull
    public static Intent createIntent(@NonNull Context context) {
        return new Intent(context, EnterSerialActivity.class);
    }
}

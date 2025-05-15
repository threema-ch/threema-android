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

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Window;
import android.widget.Toast;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import org.slf4j.Logger;

import java.io.IOException;
import java.util.Date;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;
import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.asynctasks.AddContactRestrictionPolicy;
import ch.threema.app.asynctasks.AlreadyVerified;
import ch.threema.app.asynctasks.BasicAddOrUpdateContactBackgroundTask;
import ch.threema.app.asynctasks.ContactResult;
import ch.threema.app.asynctasks.ContactCreated;
import ch.threema.app.asynctasks.ContactExists;
import ch.threema.app.asynctasks.ContactModified;
import ch.threema.app.asynctasks.Failed;
import ch.threema.app.asynctasks.PolicyViolation;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.dialogs.NewContactDialog;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.LockAppService;
import ch.threema.app.services.QRCodeService;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.QRScannerUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.executor.BackgroundExecutor;
import ch.threema.app.webclient.services.WebSessionQRCodeParser;
import ch.threema.app.webclient.services.WebSessionQRCodeParserImpl;
import ch.threema.base.utils.Base64;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.data.repositories.ContactModelRepository;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.storage.models.ContactModel;

import static ch.threema.app.services.QRCodeServiceImpl.QR_TYPE_ID;
import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;
import static ch.threema.domain.protocol.csp.ProtocolDefines.IDENTITY_LEN;

public class AddContactActivity extends ThreemaActivity implements GenericAlertDialog.DialogClickListener, NewContactDialog.NewContactDialogClickListener {
    private static final String DIALOG_TAG_ADD_PROGRESS = "ap";
    private static final String DIALOG_TAG_ADD_ERROR = "ae";
    private static final String DIALOG_TAG_ADD_BY_ID = "abi";
    public static final String EXTRA_ADD_BY_ID = "add_by_id";
    public static final String EXTRA_ADD_BY_QR = "add_by_qr";
    public static final String EXTRA_QR_RESULT = "qr_result";

    private static final Logger logger = LoggingUtil.getThreemaLogger("AddContactActivity");

    private static final int PERMISSION_REQUEST_CAMERA = 1;

    private QRCodeService qrCodeService;
    private LockAppService lockAppService;
    private ContactModelRepository contactModelRepository;
    private APIConnector apiConnector;
    private final BackgroundExecutor backgroundExecutor = new BackgroundExecutor();

    public void onCreate(Bundle savedInstanceState) {
        logScreenVisibility(this, logger);
        ServiceManager serviceManager = ThreemaApplication.getServiceManager();

        if (serviceManager == null) {
            finish();
            return;
        }

        try {
            this.qrCodeService = serviceManager.getQRCodeService();
            this.lockAppService = serviceManager.getLockAppService();
            this.contactModelRepository = serviceManager.getModelRepositories().getContacts();
            this.apiConnector = serviceManager.getAPIConnector();
        } catch (Exception e) {
            logger.error("Could not instantiate services", e);
            finish();
            return;
        }

        super.onCreate(savedInstanceState);

        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);

        onNewIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null) {
            String action = intent.getAction();

            if (action != null) {
                if (action.equals(Intent.ACTION_VIEW)) {
                    Uri dataUri = intent.getData();

                    if (TestUtil.required(dataUri)) {
                        String scheme = dataUri.getScheme();
                        String host = dataUri.getHost();

                        if (scheme != null && host != null) {
                            if (
                                (BuildConfig.uriScheme.equals(scheme) && "add".equals(host))
                                    ||
                                    ("https".equals(scheme) && BuildConfig.actionUrl.equals(host) && "/add".equals(dataUri.getPath()))
                            ) {

                                String id = dataUri.getQueryParameter("id");

                                if (TestUtil.required(id)) {
                                    addContactByIdentity(id);
                                }
                            }
                        }
                    }
                }
            }
            if (intent.getStringExtra(EXTRA_QR_RESULT) != null) {
                parseQrResult(intent.getStringExtra(EXTRA_QR_RESULT));
            }

            if (intent.getBooleanExtra(EXTRA_ADD_BY_QR, false)) {
                scanQR();
            }

            if (intent.getBooleanExtra(EXTRA_ADD_BY_ID, false)) {
                requestID();
            }
        }
    }

    private void parseQrResult(String payload) {
        // first: try to parse as contact result (contact scan)
        QRCodeService.QRCodeContentResult contactQRCode = this.qrCodeService.getResult(payload);

        if (contactQRCode != null) {
            addContactByQRResult(contactQRCode);
            return;
        }

        // second: try uri scheme
        String scannedIdentity = null;
        Uri uri = Uri.parse(payload);
        if (uri != null) {
            String scheme = uri.getScheme();
            if (BuildConfig.uriScheme.equals(scheme) && "add".equals(uri.getAuthority())) {
                scannedIdentity = uri.getQueryParameter("id");
            } else if ("https".equals(scheme) && BuildConfig.contactActionUrl.equals(uri.getHost())) {
                scannedIdentity = uri.getLastPathSegment();
            }

            if (scannedIdentity != null && scannedIdentity.length() == IDENTITY_LEN) {
                addContactByIdentity(scannedIdentity);
            }
        }
    }

    private void addContactByIdentity(final String identity) {
        if (identity == null) {
            logger.error("Identity is null");
            finish();
            return;
        }

        addContactByIdentity(identity, null);
    }

    /**
     * start a web client session (payload must be validated before
     * the method is called)
     * <p>
     * fix #ANDR-570
     *
     * @param payload a valid payload
     */
    private void startWebClientByQRResult(final byte[] payload) {
        if (payload != null) {
            // start web client session screen with payload data and finish my screen
            Intent webClientIntent = new Intent(this, ch.threema.app.webclient.activities.SessionsActivity.class);
            IntentDataUtil.append(payload, webClientIntent);
            this.finish();
            startActivity(webClientIntent);
        }
    }

    @SuppressLint("StaticFieldLeak")
    private void addContactByQRResult(final QRCodeService.QRCodeContentResult qrResult) {
        logger.info("Adding contact from QR code");
        if (qrResult.getExpirationDate() != null
            && qrResult.getExpirationDate().before(new Date())) {
            GenericAlertDialog.newInstance(R.string.title_adduser, getString(R.string.expired_barcode), R.string.ok, 0).show(getSupportFragmentManager(), "ex");
            return;
        }

        addContactByIdentity(qrResult.getIdentity(), qrResult.getPublicKey());
    }

    private void addContactByIdentity(@NonNull String identity, @Nullable byte[] publicKey) {
        if (lockAppService.isLocked()) {
            finish();
            return;
        }

        backgroundExecutor.execute(new BasicAddOrUpdateContactBackgroundTask(
            identity,
            ContactModel.AcquaintanceLevel.DIRECT,
            getMyIdentity(),
            apiConnector,
            contactModelRepository,
            AddContactRestrictionPolicy.CHECK,
            this,
            publicKey
        ) {
            @Override
            public void onBefore() {
                GenericProgressDialog.newInstance(R.string.creating_contact, R.string.please_wait).show(getSupportFragmentManager(), DIALOG_TAG_ADD_PROGRESS);
            }

            @Override
            public void onFinished(@NonNull ContactResult result) {
                if (isDestroyed()) {
                    return;
                }

                DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_ADD_PROGRESS, true);

                if (result instanceof ContactCreated) {
                    showContactAndFinish(identity, R.string.creating_contact_successful);
                } else if (result instanceof ContactModified) {
                    if (((ContactModified) result).getAcquaintanceLevelChanged()) {
                        showContactAndFinish(identity, R.string.creating_contact_successful);
                    } else {
                        showContactAndFinish(identity, R.string.scan_successful);
                    }
                } else if (result instanceof AlreadyVerified) {
                    showContactAndFinish(identity, R.string.scan_duplicate);
                } else if (result instanceof ContactExists) {
                    showContactAndFinish(identity, R.string.identity_already_exists);
                } else if (result instanceof PolicyViolation) {
                    Toast.makeText(AddContactActivity.this, R.string.disabled_by_policy_short, Toast.LENGTH_SHORT).show();
                    finish();
                } else if (result instanceof Failed) {
                    GenericAlertDialog.newInstance(
                        ConfigUtils.isOnPremBuild() ?
                            R.string.invalid_onprem_id_title :
                            R.string.title_adduser,
                        ((Failed) result).getMessage(),
                        R.string.close,
                        0
                    ).show(getSupportFragmentManager(), DIALOG_TAG_ADD_ERROR);
                }
            }
        });
    }

    private void showContactDetail(String id) {
        Intent intent = new Intent(this, ContactDetailActivity.class);
        intent.putExtra(ThreemaApplication.INTENT_DATA_CONTACT, id);
        this.startActivity(intent);
        finish();
    }

    private void showContactAndFinish(@NonNull String identity, @StringRes int stringRes) {
        Toast.makeText(this.getApplicationContext(), stringRes, Toast.LENGTH_SHORT).show();
        showContactDetail(identity);
        finish();
    }

    private void scanQR() {
        if (ConfigUtils.requestCameraPermissions(this, null, PERMISSION_REQUEST_CAMERA)) {
            if (ConfigUtils.supportsGroupLinks()) {
                QRScannerUtil.getInstance().initiateGeneralThreemaQrScanner(this, getString(R.string.qr_scanner_id_hint));
            } else {
                QRScannerUtil.getInstance().initiateScan(this, getString(R.string.qr_scanner_id_hint), QR_TYPE_ID);
            }
        }
    }

    private void requestID() {
        DialogFragment dialogFragment = NewContactDialog.newInstance(
            R.string.menu_add_contact,
            R.string.enter_id_hint,
            R.string.ok,
            R.string.cancel);
        dialogFragment.show(getSupportFragmentManager(), DIALOG_TAG_ADD_BY_ID);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (resultCode == RESULT_OK) {
            String payload = QRScannerUtil.getInstance().parseActivityResult(this, requestCode, resultCode, intent);

            if (!TestUtil.isEmptyOrNull(payload)) {

                // first: try to parse as content result (contact scan)
                QRCodeService.QRCodeContentResult contactQRCode = this.qrCodeService.getResult(payload);

                if (contactQRCode != null) {
                    // ok, try to add contact
                    if (contactQRCode.getExpirationDate() != null
                        && contactQRCode.getExpirationDate().before(new Date())) {
                        GenericAlertDialog.newInstance(R.string.title_adduser, getString(R.string.expired_barcode), R.string.ok, 0).show(getSupportFragmentManager(), "ex");
                    } else {
                        addContactByQRResult(contactQRCode);
                    }

                    // return, qr code valid and exit method
                    DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_ADD_BY_ID, true);
                    return;
                }

                // second: try uri scheme
                String scannedIdentity = null;
                Uri uri = Uri.parse(payload);
                if (uri != null) {
                    String scheme = uri.getScheme();
                    if (BuildConfig.uriScheme.equals(scheme) && "add".equals(uri.getAuthority())) {
                        scannedIdentity = uri.getQueryParameter("id");
                    } else if ("https".equals(scheme) && BuildConfig.contactActionUrl.equals(uri.getHost())) {
                        scannedIdentity = uri.getLastPathSegment();
                    }

                    if (scannedIdentity != null && scannedIdentity.length() == IDENTITY_LEN) {
                        addContactByIdentity(scannedIdentity);
                        return;
                    }
                }

                // third: try to parse as web client qr
                try {
                    byte[] base64Payload = Base64.decode(payload);
                    if (base64Payload != null) {
                        final WebSessionQRCodeParser webClientQRCodeParser = new WebSessionQRCodeParserImpl();
                        webClientQRCodeParser.parse(base64Payload); // throws if QR is not valid
                        // it was a valid web client qr code, exit method
                        startWebClientByQRResult(base64Payload);
                        return;
                    }
                } catch (IOException | WebSessionQRCodeParser.InvalidQrCodeException x) {
                    // not a valid base64 or web client payload
                    // ignore and continue
                }
            }
            Toast.makeText(this, R.string.invalid_barcode, Toast.LENGTH_SHORT).show();
        }

        finish();
    }

    @Override
    public void onYes(String tag, Object data) {
        finish();
    }

    @Override
    public void onNo(String tag, Object data) {
        finish();
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_CAMERA:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    scanQR();
                } else if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    ConfigUtils.showPermissionRationale(this, getWindow().getDecorView().findViewById(android.R.id.content), R.string.permission_camera_qr_required, new BaseTransientBottomBar.BaseCallback<Snackbar>() {
                        @Override
                        public void onDismissed(Snackbar transientBottomBar, int event) {
                            super.onDismissed(transientBottomBar, event);
                            finish();
                        }
                    });
                } else {
                    finish();
                }
                break;
            default:
                break;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onContactEnter(String tag, String text) {
        if (TestUtil.required(text)) {
            addContactByIdentity(text);
        }
    }

    @Override
    public void onCancel(String tag) {
        finish();
    }

    @Override
    public void onScanButtonClick(String tag) {
        logger.info("Scan button clicked");
        scanQR();
    }
}

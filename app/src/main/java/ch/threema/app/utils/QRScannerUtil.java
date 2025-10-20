/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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

package ch.threema.app.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.slf4j.Logger;

import ch.threema.app.AppConstants;
import ch.threema.app.R;
import ch.threema.app.camera.QRScannerActivity;
import ch.threema.app.dialogs.SimpleStringAlertDialog;
import ch.threema.app.services.QRCodeService;
import ch.threema.base.utils.LoggingUtil;

public class QRScannerUtil {
    private static final Logger logger = LoggingUtil.getThreemaLogger("QRScannerUtil");

    public static final int REQUEST_CODE_QR_SCANNER = 26657;
    // Singleton stuff
    private static QRScannerUtil sInstance = null;

    public static synchronized QRScannerUtil getInstance() {
        if (sInstance == null) {
            sInstance = new QRScannerUtil();
        }
        return sInstance;
    }

    public void initiateScan(@NonNull AppCompatActivity activity, String hint) {
        logger.info("initiateScan");

        Intent intent = getInitiateScanIntent(activity, hint);
        activity.startActivityForResult(intent, REQUEST_CODE_QR_SCANNER);
    }

    @NonNull
    private static Intent getInitiateScanIntent(@NonNull Context context, @Nullable String hint) {
        Intent intent = new Intent(context, QRScannerActivity.class);
        if (!TestUtil.isEmptyOrNull(hint)) {
            intent.putExtra(QRScannerActivity.KEY_HINT_TEXT, hint);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        return intent;
    }

    private void invalidCodeDialog(@NonNull AppCompatActivity activity) {
        SimpleStringAlertDialog.newInstance(R.string.scan_id, R.string.invalid_threema_qr_code).show(activity.getSupportFragmentManager(), "");
    }

    public String parseActivityResult(AppCompatActivity activity, int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQUEST_CODE_QR_SCANNER) {
            if (activity != null) {
                if (resultCode == Activity.RESULT_OK) {
                    return intent.getStringExtra(AppConstants.INTENT_DATA_QRCODE);
                }
            }
        }
        return null;
    }

    public QRCodeService.QRCodeContentResult parseActivityResult(
        AppCompatActivity activity,
        int requestCode,
        int resultCode,
        Intent intent,
        QRCodeService qrCodeService
    ) {
        if (activity != null) {
            if (qrCodeService != null) {
                String scanResult = parseActivityResult(activity, requestCode, resultCode, intent);
                if (scanResult != null) {
                    QRCodeService.QRCodeContentResult qrRes = qrCodeService.getResult(scanResult);
                    if (qrRes != null) {
                        return qrRes;
                    }
                    invalidCodeDialog(activity);
                }
            }
        }
        return null;
    }
}

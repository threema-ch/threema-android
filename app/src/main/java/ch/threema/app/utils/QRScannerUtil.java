/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2021 Threema GmbH
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
import android.content.Intent;
import android.content.pm.ActivityInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.appcompat.app.AppCompatActivity;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.dialogs.SimpleStringAlertDialog;
import ch.threema.app.qrscanner.activity.CaptureActivity;
import ch.threema.app.services.QRCodeService;

public class QRScannerUtil {
	private static final Logger logger = LoggerFactory.getLogger(QRScannerUtil.class);

	private static boolean scanAnyCode;
	public static final int REQUEST_CODE_QR_SCANNER = 26657;
	// Singleton stuff
	private static QRScannerUtil sInstance = null;

	public static synchronized QRScannerUtil getInstance() {
		if (sInstance == null) {
			sInstance = new QRScannerUtil();
		}
		return sInstance;
	}

	public void initiateScan(AppCompatActivity activity, boolean anyCode, String hint) {
		logger.info("initiateScan");
		scanAnyCode = anyCode;
		Intent intent = new Intent(activity, CaptureActivity.class);
		if (!TestUtil.empty(hint)) {
			intent.putExtra("PROMPT_MESSAGE", hint);
		}
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
		// lock orientation before launching scanner
		ConfigUtils.setRequestedOrientation(activity, activity.getResources().getConfiguration().orientation);
		activity.startActivityForResult(intent, REQUEST_CODE_QR_SCANNER);
	}

	private void invalidCodeDialog(AppCompatActivity activity) {
		SimpleStringAlertDialog.newInstance(R.string.scan_id, R.string.invalid_barcode).show(activity.getSupportFragmentManager(), "");
	}

	public String parseActivityResult(AppCompatActivity activity, int requestCode, int resultCode, Intent intent) {
		if (requestCode == REQUEST_CODE_QR_SCANNER) {
			if (activity != null) {
				ConfigUtils.setRequestedOrientation(activity, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
				if (resultCode == Activity.RESULT_OK) {
					if (scanAnyCode || intent.getBooleanExtra(ThreemaApplication.INTENT_DATA_QRCODE_TYPE_OK, false)) {
						return intent.getStringExtra(ThreemaApplication.INTENT_DATA_QRCODE);
					} else {
						invalidCodeDialog(activity);
					}
				}
			}
		}
		return null;
	}

	public QRCodeService.QRCodeContentResult parseActivityResult(AppCompatActivity activity, int requestCode, int resultCode, Intent intent, QRCodeService qrCodeService) {
		ConfigUtils.setRequestedOrientation(activity, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
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
		return null;
	}
}

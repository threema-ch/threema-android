/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2024 Threema GmbH
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.slf4j.Logger;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.camera.QRScannerActivity;
import ch.threema.app.dialogs.SimpleStringAlertDialog;
import ch.threema.app.qrscanner.activity.BaseQrScannerActivity;
import ch.threema.app.services.QRCodeService;
import ch.threema.app.services.QRCodeServiceImpl;
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

	public void initiateScan(@NonNull AppCompatActivity activity, String hint, @QRCodeServiceImpl.QRCodeColor int qrType) {
		logger.info("initiateScan");

		Intent intent = getInitiateScanIntent(activity, hint, qrType);
		activity.startActivityForResult(intent, REQUEST_CODE_QR_SCANNER);
	}

	private static Intent getInitiateScanIntent(
		@NonNull Context context,
		@Nullable String hint,
		@QRCodeServiceImpl.QRCodeColor int qrType
	) {
		Intent intent = new Intent(context, QRScannerActivity.class);
		if (!TestUtil.isEmptyOrNull(hint)) {
			intent.putExtra(QRScannerActivity.KEY_HINT_TEXT, hint);
		}
		intent.putExtra(QRScannerActivity.KEY_QR_TYPE, qrType);

		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
		return intent;
	}

	public void initiateGeneralThreemaQrScanner(Activity activity, String hint) {
		Intent intent = new Intent(activity, BaseQrScannerActivity.class);
		if (!TestUtil.isEmptyOrNull(hint)) {
			intent.putExtra(QRScannerActivity.KEY_HINT_TEXT, hint);
		}
		if (activity != null) {
			activity.startActivity(intent);
		}
	}

	private void invalidCodeDialog(AppCompatActivity activity) {
		SimpleStringAlertDialog.newInstance(R.string.scan_id, R.string.invalid_barcode).show(activity.getSupportFragmentManager(), "");
	}

	public String parseActivityResult(AppCompatActivity activity, int requestCode, int resultCode, Intent intent) {
		if (requestCode == REQUEST_CODE_QR_SCANNER) {
			if (activity != null) {
				if (resultCode == Activity.RESULT_OK) {
					return intent.getStringExtra(ThreemaApplication.INTENT_DATA_QRCODE);
				}
			}
		}
		return null;
	}

	public QRCodeService.QRCodeContentResult parseActivityResult(AppCompatActivity activity, int requestCode, int resultCode, Intent intent, QRCodeService qrCodeService) {
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

	public interface ScanResultCallback {
		void onScanResult(@Nullable String payload);
	}

	static public class QrCodeScanner {
		private final @NonNull Context context;
		private final @NonNull ActivityResultLauncher<Intent> launcher;

		private QrCodeScanner(@NonNull Context context, @NonNull ActivityResultLauncher<Intent> launcher) {
			this.context = context;
			this.launcher = launcher;
		}

		public void scan(@Nullable String hint, @QRCodeServiceImpl.QRCodeColor int type) {
			launcher.launch(getInitiateScanIntent(context, hint, type));
		}
	}

	public static QrCodeScanner prepareScanner(
		@NonNull AppCompatActivity activity,
		@NonNull ScanResultCallback callback
	) {
		ActivityResultLauncher<Intent> launcher = activity.registerForActivityResult(
			new ActivityResultContracts.StartActivityForResult(),
			result -> {
				boolean success = result.getResultCode() == Activity.RESULT_OK;
				Intent data = result.getData();
				String payload = success && data != null
					? data.getStringExtra(ThreemaApplication.INTENT_DATA_QRCODE)
					: null;
				callback.onScanResult(payload);
			}
		);
		return new QrCodeScanner(activity, launcher);
	}
}
